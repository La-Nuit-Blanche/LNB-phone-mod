package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.api.PhoneNotifications
import fr.bde_teknantes.lnb.phone.feed.Feed
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.network.PacketDistributor
import java.util.UUID

/**
 * What Feed's server side needs from the running server, so that its logic can be tested without one
 * ([MinecraftFeedHost] is the real one). Everything but [execute] is called on the server thread only.
 */
interface FeedHost {
    /** Runs [task] on the server thread: at once if already on it, otherwise as soon as the server can. */
    fun execute(task: () -> Unit)

    fun onlinePlayers(): Collection<UUID>

    fun isOnline(player: UUID): Boolean

    /** Sends [payload] to [player] if they are connected. */
    fun send(player: UUID, payload: CustomPacketPayload)

    /** Sends [player] their command tree again, so that `/feed announcer` appears or disappears. */
    fun refreshCommands(player: UUID)

    /** A monotonic clock in milliseconds, for the per-player limits. */
    fun millis(): Long
}

/** Where Feed's notifications go: the phone's [PhoneNotifications], or a recorder in tests. */
interface FeedNotificationSink {
    fun send(players: Collection<UUID>, title: Component, text: Component, target: String)

    /** Every player of the world but [except] (an announcement's author): passed to the phone and nowhere else. */
    fun sendToEveryone(title: Component, text: Component, target: String, except: Collection<UUID>)

    fun clearTargetForEveryone(target: String)
}

class MinecraftFeedHost(private val server: MinecraftServer) : FeedHost {
    override fun execute(task: () -> Unit) {
        if (server.isSameThread) return task()
        // A stopped server runs execute()'s task in the caller's thread instead of queueing it: drop it then, as the
        // phone does, since Feed has nothing to do once the server stops.
        if (!server.isRunning) return
        server.execute { if (server.isSameThread) task() }
    }

    override fun onlinePlayers(): Collection<UUID> = server.playerList.players.map { it.uuid }

    override fun isOnline(player: UUID) = server.playerList.getPlayer(player) != null

    override fun send(player: UUID, payload: CustomPacketPayload) {
        val target = server.playerList.getPlayer(player) ?: return
        PacketDistributor.sendToPlayer(target, payload)
    }

    override fun refreshCommands(player: UUID) {
        val target = server.playerList.getPlayer(player) ?: return
        server.commands.sendCommands(target)
    }

    override fun millis(): Long = System.nanoTime() / 1_000_000
}

object PhoneFeedNotifications : FeedNotificationSink {
    override fun send(players: Collection<UUID>, title: Component, text: Component, target: String) =
        PhoneNotifications.send(players, Feed.APP_ID, title, text, target)

    override fun sendToEveryone(title: Component, text: Component, target: String, except: Collection<UUID>) =
        PhoneNotifications.sendToEveryone(Feed.APP_ID, title, text, target, except)

    override fun clearTargetForEveryone(target: String) = PhoneNotifications.clearTargetForEveryone(Feed.APP_ID, target)
}

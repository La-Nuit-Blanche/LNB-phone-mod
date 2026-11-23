package fr.bde_teknantes.lnb.phone.feed.server

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID

/** The server, standing still: tasks run at once, as when already on the server thread. */
class FakeHost : FeedHost {
    val online = LinkedHashSet<UUID>()
    val sent = ArrayList<Pair<UUID, CustomPacketPayload>>()
    val commandRefreshes = ArrayList<UUID>()
    var time = 0L

    override fun execute(task: () -> Unit) = task()
    override fun onlinePlayers(): Collection<UUID> = online.toList()
    override fun isOnline(player: UUID) = player in online

    override fun send(player: UUID, payload: CustomPacketPayload) {
        if (player in online) sent += player to payload
    }

    override fun refreshCommands(player: UUID) {
        commandRefreshes += player
    }

    override fun millis() = time

    fun sentTo(player: UUID): List<CustomPacketPayload> = sent.filter { it.first == player }.map { it.second }

    inline fun <reified T : CustomPacketPayload> received(player: UUID): List<T> = sentTo(player).filterIsInstance<T>()
}

class RecordingSink : FeedNotificationSink {
    data class Sent(val players: Collection<UUID>?, val except: Collection<UUID>, val title: Component, val text: Component, val target: String)

    val sent = ArrayList<Sent>()
    val cleared = ArrayList<String>()

    override fun send(players: Collection<UUID>, title: Component, text: Component, target: String) {
        sent += Sent(players.toList(), emptyList(), title, text, target)
    }

    override fun sendToEveryone(title: Component, text: Component, target: String, except: Collection<UUID>) {
        sent += Sent(null, except.toList(), title, text, target)
    }

    override fun clearTargetForEveryone(target: String) {
        cleared += target
    }
}

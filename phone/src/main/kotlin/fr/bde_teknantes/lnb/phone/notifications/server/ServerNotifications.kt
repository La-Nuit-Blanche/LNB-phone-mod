package fr.bde_teknantes.lnb.phone.notifications.server

import fr.bde_teknantes.lnb.phone.notifications.NotificationCodecs
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import io.netty.buffer.Unpooled
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.connection.ConnectionType
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.nio.file.Files
import java.util.UUID

/**
 * Connects [NotificationService] to the running server: its events, its payload handlers and a [NotificationHost] over
 * the [MinecraftServer]. Server thread only; [fr.bde_teknantes.lnb.phone.api.PhoneNotifications] hops here from other
 * threads. Common code: it runs on the dedicated server and on the integrated one.
 */
internal object ServerNotifications {
    val service = NotificationService()

    /** How often clients that fell out of step get their list again: every 5 seconds. */
    private const val RESYNC_TICKS = 100
    private var ticks = 0

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
    }

    /** The host for [server], checking that the caller is on its thread. */
    fun host(server: MinecraftServer): NotificationHost {
        check(server.isSameThread) {
            "Phone notifications are stored on the server thread, not on ${Thread.currentThread().name}"
        }
        return MinecraftHost(server)
    }

    private fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        service.login(host(player.server), player.uuid)
    }

    private fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        service.logout(event.entity.uuid)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        if (++ticks % RESYNC_TICKS == 0) service.resync(host(event.server))
    }

    // Handlers run on the server thread (NeoForge's default for payloads). The payloads name no player: each acts on
    // its sender's notifications only, so a client can't touch anyone else's.

    fun handleClearApp(payload: ClearAppNotificationsPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        service.clientClearedApp(host(player.server), player.uuid, payload.appId, payload.upToId)
    }

    fun handleDismiss(payload: DismissNotificationPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        service.clientDismissed(host(player.server), player.uuid, payload.id)
    }

    private class MinecraftHost(private val server: MinecraftServer) : NotificationHost {
        override val data: NotificationSavedData? get() = NotificationSavedData.getOrNull(server)

        override fun isOnline(player: UUID) = server.playerList.getPlayer(player) != null

        override fun send(player: UUID, payload: CustomPacketPayload) {
            server.playerList.getPlayer(player)?.let { PacketDistributor.sendToPlayer(it, payload) }
        }

        /** The players with a save file in this world (`playerdata/<uuid>.dat`), and those connected. */
        override fun knownPlayers(): Collection<UUID> {
            val known = server.playerList.players.mapTo(HashSet()) { it.uuid }
            val dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
            if (Files.isDirectory(dir)) {
                Files.newDirectoryStream(dir, "*.dat").use { files ->
                    for (file in files) {
                        runCatching { UUID.fromString(file.fileName.toString().removeSuffix(".dat")) }.getOrNull()?.let(known::add)
                    }
                }
            }
            return known
        }

        override fun encodedSize(notification: PhoneNotification): Int {
            val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE)
            return try {
                NotificationCodecs.STREAM_CODEC.encode(buf, notification)
                buf.readableBytes()
            } catch (e: Exception) {
                -1
            } finally {
                buf.release()
            }
        }

        override fun now() = System.currentTimeMillis()
    }
}

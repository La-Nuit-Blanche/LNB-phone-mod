package fr.bde_teknantes.lnb.phone.notifications.server

import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

/**
 * The server side of notifications: changes the saved store and keeps connected clients in step. Server thread only;
 * [fr.bde_teknantes.lnb.phone.api.PhoneNotifications] hops here from other threads. Common code: it runs on the
 * dedicated server and on the integrated one.
 */
internal object ServerNotifications {
    // Clients clear an app's notifications when the player opens it: a few times a minute in normal play. Anything far
    // beyond is a modified client, and is ignored rather than allowed to keep the server busy.
    private val clientRequests = RateLimiter<UUID>(burst = 10, perSecond = 2.0)

    fun register() {
        NeoForge.EVENT_BUS.addListener(::onLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onLoggedOut)
    }

    /** Stores a notification for [player] and, if they are connected, sends it to them. Arguments already limited. */
    fun send(server: MinecraftServer, player: UUID, appId: ResourceLocation, title: Component, text: Component, target: String?) {
        checkThread(server)
        val data = NotificationSavedData.get(server)
        val online = server.playerList.getPlayer(player)
        val notification = data.store.add(player, appId, title, text, target, System.currentTimeMillis(), delivered = online != null)
        data.setDirty()
        if (online != null) PacketDistributor.sendToPlayer(online, NotificationAddedPayload(notification))
    }

    /** Removes [player]'s notifications from [appId] (only those leading to [target], if given), on their client too. */
    fun clear(server: MinecraftServer, player: UUID, appId: ResourceLocation, target: String?) {
        checkThread(server)
        val data = NotificationSavedData.get(server)
        val removed = if (target == null) data.store.clearApp(player, appId) else data.store.clearTarget(player, appId, target)
        if (removed.isEmpty()) return
        data.setDirty()
        server.playerList.getPlayer(player)?.let { PacketDistributor.sendToPlayer(it, NotificationsClearedPayload(appId, target)) }
    }

    private fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val data = NotificationSavedData.get(player.server)
        // Sent with their delivered flags as they were, so the client can tell what came in while the player was away.
        PacketDistributor.sendToPlayer(player, NotificationsSyncPayload(data.store.of(player.uuid)))
        if (data.store.markDelivered(player.uuid)) data.setDirty()
    }

    private fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        clientRequests.forget(event.entity.uuid)
    }

    // Handlers run on the server thread (NeoForge's default for payloads). The payloads name no player: each acts on
    // its sender's notifications only, so a client can't touch anyone else's.

    fun handleClearApp(payload: ClearAppNotificationsPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!clientRequests.tryAcquire(player.uuid)) return
        val data = NotificationSavedData.get(player.server)
        if (data.store.clearApp(player.uuid, payload.appId, payload.upToId).isNotEmpty()) data.setDirty()
    }

    fun handleDismiss(payload: DismissNotificationPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!clientRequests.tryAcquire(player.uuid)) return
        val data = NotificationSavedData.get(player.server)
        if (data.store.remove(player.uuid, payload.id) != null) data.setDirty()
    }

    private fun checkThread(server: MinecraftServer) {
        check(server.isSameThread) {
            "Phone notifications are stored on the server thread, not on ${Thread.currentThread().name}"
        }
    }
}

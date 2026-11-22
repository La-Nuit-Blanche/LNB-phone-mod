package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.apps.NavigationListener
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.PacketDistributor

/**
 * [ClientNotificationSync] over the game's connection and [LnbPhoneClient.notifications]: what the payload handlers
 * and the navigator call. Client thread only (payload handlers run there).
 */
internal object ClientNotificationHandler : NavigationListener {
    private val sync = ClientNotificationSync(LnbPhoneClient.notifications, ::sendToServer)

    fun handleSync(payload: NotificationsSyncPayload) = sync.handleSync(payload)

    fun handleAdded(payload: NotificationAddedPayload) = sync.handleAdded(payload)

    fun handleCleared(payload: NotificationsClearedPayload) = sync.handleCleared(payload)

    /** See [ClientNotificationSync.onAppOpened]. */
    override fun onAppOpened(app: PhoneApp) = sync.onAppOpened(app)

    /** See [ClientNotificationSync.dismiss]. */
    fun dismiss(notification: PhoneNotification) = sync.dismiss(notification)

    private fun sendToServer(payload: CustomPacketPayload) {
        // Nothing to tell when the connection is already gone: the server forgets nothing it was told before.
        if (Minecraft.getInstance().connection != null) PacketDistributor.sendToServer(payload)
    }
}

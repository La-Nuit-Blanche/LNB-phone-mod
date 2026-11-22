package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.apps.NavigationListener
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Connects [ClientNotifications] to the server and to the navigator: payloads in, "read" and "dismissed" out, and the
 * rule that opening an app clears its notifications (6.1). Client thread only (payload handlers run there).
 */
internal object ClientNotificationHandler : NavigationListener {
    private val store get() = LnbPhoneClient.notifications

    fun handleSync(payload: NotificationsSyncPayload) = store.sync(payload.notifications, payload.login)

    fun handleAdded(payload: NotificationAddedPayload) = store.arrived(payload.notification)

    fun handleCleared(payload: NotificationsClearedPayload) = store.clearedByServer(payload.appId, payload.target)

    /** Opening an app, by any route (home icon, banner, peek), reads its notifications, here and on the server. */
    override fun onAppOpened(app: PhoneApp) {
        val upTo = store.read(app.id) ?: return
        sendToServer(ClearAppNotificationsPayload(app.id, upTo))
    }

    /** [notification] was read in place, its target on screen ([Alert.keep] false): removed here and on the server. */
    fun dismiss(notification: PhoneNotification) {
        if (store.dismiss(notification.id)) sendToServer(DismissNotificationPayload(notification.id))
    }

    private fun sendToServer(payload: CustomPacketPayload) {
        // Nothing to tell when the connection is already gone: the server forgets nothing it was told before.
        if (Minecraft.getInstance().connection != null) PacketDistributor.sendToServer(payload)
    }
}

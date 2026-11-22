package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.apps.NavigationListener
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Keeps [store] in step with the server, as logic over a [sendToServer] function so it can be tested without a
 * connection ([ClientNotificationHandler] gives the real one): payloads in, "read" and "dismissed" out, and the rule
 * that opening an app clears its notifications (6.1). Client thread only.
 */
internal class ClientNotificationSync(
    private val store: ClientNotifications,
    private val sendToServer: (CustomPacketPayload) -> Unit,
) : NavigationListener {
    fun handleSync(payload: NotificationsSyncPayload) = store.sync(payload.notifications, payload.login)

    fun handleAdded(payload: NotificationAddedPayload) = store.arrived(payload.notification)

    fun handleCleared(payload: NotificationsClearedPayload) = store.clearedByServer(payload.appId, payload.target)

    /**
     * Opening an app, by any route (home icon, banner, peek), reads its notifications, here and on the server: up to
     * the newest this client knew of, so one still on its way survives.
     */
    override fun onAppOpened(app: PhoneApp) {
        val upTo = store.read(app.id) ?: return
        sendToServer(ClearAppNotificationsPayload(app.id, upTo))
    }

    /** [notification] was read in place, its target on screen ([Alert.keep] false): removed here and on the server. */
    fun dismiss(notification: PhoneNotification) {
        if (store.dismiss(notification.id)) sendToServer(DismissNotificationPayload(notification.id))
    }
}

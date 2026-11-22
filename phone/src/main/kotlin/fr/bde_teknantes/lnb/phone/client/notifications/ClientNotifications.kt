package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.notifications.NotificationList
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The client's copy of the player's notifications, kept in step with the server by [ClientNotificationHandler], and
 * what the phone reads to draw badges (6.1), the LED (14) and the inventory button's "new notifications" state (11).
 * Plain logic: the network and the navigator are wired to it elsewhere.
 *
 * Every notification here is unread (opening an app removes its notifications), so an app's badge is its count.
 * Notifications of apps this client doesn't have ([isInstalled] false) are kept, as the server keeps them, but count
 * nowhere: no icon could show them and no app could clear them, so they would light the LED for ever.
 *
 * Client thread only.
 */
internal class ClientNotifications(private val isInstalled: (ResourceLocation) -> Boolean) {
    /** Hears changes, to start a banner or redraw a badge. Callbacks run on the client thread. */
    interface Listener {
        /**
         * [notification] just arrived while the player is connected: the moment to decide on a banner, a peek and the
         * chime ([NotificationAlerts]). Not called for the notifications of the login sync. Called after the store
         * has it, before [onChanged].
         */
        fun onArrived(notification: PhoneNotification) {}

        /** The notifications changed in any way (arrival, sync, clearing, reset): counts may differ. */
        fun onChanged() {}
    }

    private val list = NotificationList()
    private val listeners = CopyOnWriteArrayList<Listener>()

    /**
     * The notifications of the last login sync that had arrived while the player was away (never delivered before),
     * oldest first. They show as badges only; this tells the phone there was news while away, should it want to say
     * so. Not updated by later changes.
     */
    var missedWhileAway: List<PhoneNotification> = emptyList()
        private set

    /** Every notification, oldest first, including those of apps not installed. */
    val all: List<PhoneNotification> get() = list.all

    operator fun get(id: Long): PhoneNotification? = list[id]

    /** [appId]'s badge: how many unread notifications it has. 0 for an app not installed. */
    fun unreadCount(appId: ResourceLocation): Int = if (isInstalled(appId)) list.count(appId) else 0

    /** Unread notifications of installed apps, all together: the inventory button's badge. */
    val totalUnread: Int get() = list.all.count { isInstalled(it.appId) }

    /** Whether any installed app has a badge: the LED blinks and the inventory button lights up. */
    val hasUnread: Boolean get() = list.all.any { isInstalled(it.appId) }

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /**
     * Everything the player has, replacing what was here: at login ([login] true, which also sets [missedWhileAway]),
     * or to put this client back in step with the server.
     */
    fun sync(notifications: List<PhoneNotification>, login: Boolean = true) {
        list.replaceAll(notifications)
        if (login) missedWhileAway = list.all.filter { !it.delivered }
        notifyListeners { it.onChanged() }
    }

    /** A notification sent while the player is connected. */
    fun arrived(notification: PhoneNotification) {
        val known = list[notification.id] != null
        val dropped = list.add(notification)
        // Already known (a repeat) or older than everything kept: not news.
        if (!known && dropped.none { it.id == notification.id }) notifyListeners { it.onArrived(notification) }
        notifyListeners { it.onChanged() }
    }

    /** The app's server side cleared [appId]'s notifications, or only those leading to [target]. */
    fun clearedByServer(appId: ResourceLocation, target: String?) {
        val removed = if (target == null) list.removeApp(appId) else list.removeTarget(appId, target)
        if (removed.isNotEmpty()) notifyListeners { it.onChanged() }
    }

    /**
     * The player opened [appId]: its notifications are read and removed. Returns the id of the newest one removed,
     * which the server needs to remove the same ones, or null if the app had none.
     */
    fun read(appId: ResourceLocation): Long? {
        val upTo = list.newestId(appId) ?: return null
        list.removeApp(appId, upTo)
        notifyListeners { it.onChanged() }
        return upTo
    }

    /** Notification [id] was seen in place (its target was on screen): removed. Returns whether it was there. */
    fun dismiss(id: Long): Boolean {
        if (list.remove(id) == null) return false
        notifyListeners { it.onChanged() }
        return true
    }

    /** Forgets everything: the player disconnected. */
    fun reset() {
        list.clear()
        missedWhileAway = emptyList()
        notifyListeners { it.onChanged() }
    }

    // One listener that throws must not keep the others from hearing, nor break the packet handler that called us.
    private inline fun notifyListeners(event: (Listener) -> Unit) {
        for (listener in listeners) {
            try {
                event(listener)
            } catch (e: Exception) {
                LnbPhone.LOGGER.error("Phone notification listener {} failed", listener.javaClass.name, e)
            }
        }
    }
}

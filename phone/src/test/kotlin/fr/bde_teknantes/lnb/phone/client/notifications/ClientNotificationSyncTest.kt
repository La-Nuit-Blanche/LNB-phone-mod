package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.network.ClearAppNotificationsPayload
import fr.bde_teknantes.lnb.phone.notifications.network.DismissNotificationPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationAddedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsClearedPayload
import fr.bde_teknantes.lnb.phone.notifications.network.NotificationsSyncPayload
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The client side of the protocol: what comes in from the server, and what goes back when the player reads. */
class ClientNotificationSyncTest {
    private val sent = ArrayList<CustomPacketPayload>()
    private val store = ClientNotifications { it != appId("gone") }
    private val sync = ClientNotificationSync(store) { sent += it }

    private fun n(id: Long, app: String = "feed", target: String? = null, delivered: Boolean = true) =
        PhoneNotification(id, appId(app), Component.empty(), Component.empty(), target, 0, delivered)

    @Test
    fun `payloads from the server change the store`() {
        sync.handleSync(NotificationsSyncPayload(listOf(n(1, delivered = false), n(2, target = "t"))))
        assertEquals(listOf(1L), store.missedWhileAway.map { it.id })
        sync.handleAdded(NotificationAddedPayload(n(3, target = "t")))
        assertEquals(3, store.unreadCount(appId("feed")))
        sync.handleCleared(NotificationsClearedPayload(appId("feed"), "t"))
        assertEquals(listOf(1L), store.all.map { it.id })
        sync.handleCleared(NotificationsClearedPayload(appId("feed"), null))
        assertEquals(emptyList<PhoneNotification>(), store.all)
        assertEquals(emptyList<Any>(), sent)
    }

    @Test
    fun `a resync replaces the list but keeps what was missed while away`() {
        sync.handleSync(NotificationsSyncPayload(listOf(n(1, delivered = false))))
        sync.handleSync(NotificationsSyncPayload(listOf(n(1), n(2)), login = false))
        assertEquals(listOf(1L, 2L), store.all.map { it.id })
        assertEquals(listOf(1L), store.missedWhileAway.map { it.id })
    }

    @Test
    fun `opening an app reads its notifications up to the newest known, and tells the server`() {
        val registry = AppRegistry()
        registry.register(testApp("feed"), "test")
        registry.register(testApp("mail"), "test")
        val navigator = PhoneNavigator(registry)
        navigator.addListener(sync)
        sync.handleSync(NotificationsSyncPayload(listOf(n(1), n(4), n(5, app = "mail"))))

        navigator.openApp(appId("feed"))
        assertEquals(listOf<Any>(ClearAppNotificationsPayload(appId("feed"), 4)), sent)
        assertEquals(listOf(5L), store.all.map { it.id })
        // An app without notifications sends nothing.
        navigator.openApp(appId("feed"))
        assertEquals(1, sent.size)
    }

    @Test
    fun `a dismissed notification is removed and reported once`() {
        sync.handleSync(NotificationsSyncPayload(listOf(n(1), n(2))))
        sync.dismiss(n(2))
        sync.dismiss(n(2))
        assertEquals(listOf<Any>(DismissNotificationPayload(2)), sent)
        assertEquals(listOf(1L), store.all.map { it.id })
    }
}

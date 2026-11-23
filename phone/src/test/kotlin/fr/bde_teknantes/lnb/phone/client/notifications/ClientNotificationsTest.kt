package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.apps.appId
import fr.bde_teknantes.lnb.phone.notifications.NotificationList
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The client's mirror of the player's notifications: badges, the LED's state, and what counts as news. */
class ClientNotificationsTest {
    private val feed = appId("feed")
    private val mail = appId("mail")
    private val gone = appId("gone")
    private val store = ClientNotifications { it != gone }
    private val events = mutableListOf<String>()

    init {
        store.addListener(object : ClientNotifications.Listener {
            override fun onArrived(notification: PhoneNotification) {
                events += "arrived:${notification.id}"
            }

            override fun onChanged() {
                events += "changed"
            }
        })
    }

    private fun n(id: Long, app: ResourceLocation = feed, target: String? = null, delivered: Boolean = true) =
        PhoneNotification(id, app, Component.literal("t"), Component.literal("x"), target, 0, delivered)

    // ---- Login sync ----

    @Test
    fun `the login sync replaces everything and announces no arrival`() {
        store.arrived(n(1))
        events.clear()
        store.sync(listOf(n(5), n(6, mail)))
        assertEquals(listOf("changed"), events)
        assertEquals(listOf(5L, 6L), store.all.map { it.id })
        assertNull(store[1])
    }

    @Test
    fun `notifications that came while away are told apart, oldest first, and stay badges`() {
        store.sync(listOf(n(3, delivered = false), n(1, delivered = true), n(2, mail, delivered = false)))
        assertEquals(listOf(2L, 3L), store.missedWhileAway.map { it.id })
        assertEquals(2, store.unreadCount(feed))
        assertEquals(1, store.unreadCount(mail))
        assertFalse(events.any { it.startsWith("arrived") })
    }

    @Test
    fun `missed-while-away is a snapshot of the sync`() {
        store.sync(listOf(n(1, delivered = false)))
        store.read(feed)
        assertEquals(listOf(1L), store.missedWhileAway.map { it.id })
        store.sync(emptyList())
        assertEquals(emptyList<PhoneNotification>(), store.missedWhileAway)
    }

    @Test
    fun `a sync over the capacity keeps the newest`() {
        store.sync((1L..60L).map { n(it) })
        assertEquals((11L..60L).toList(), store.all.map { it.id })
    }

    // ---- Live arrivals ----

    @Test
    fun `an arrival is announced once, before the change`() {
        store.arrived(n(1))
        assertEquals(listOf("arrived:1", "changed"), events)
        events.clear()
        store.arrived(n(1))
        assertEquals(listOf("changed"), events)
        assertEquals(1, store.unreadCount(feed))
    }

    @Test
    fun `an arrival already known from the sync is not news`() {
        store.sync(listOf(n(4)))
        events.clear()
        store.arrived(n(4))
        assertEquals(listOf("changed"), events)
    }

    @Test
    fun `an arrival older than everything in a full list is dropped silently`() {
        store.sync((11L..60L).map { n(it) })
        events.clear()
        store.arrived(n(5))
        assertEquals(listOf("changed"), events)
        assertNull(store[5])
        assertEquals(NotificationList.CAPACITY, store.all.size)
    }

    @Test
    fun `an arrival in a full list pushes out the oldest`() {
        store.sync((1L..50L).map { n(it) })
        store.arrived(n(51))
        assertNull(store[1])
        assertEquals(50, store.unreadCount(feed))
    }

    @Test
    fun `an arrival for an app not installed is still announced, but counts nowhere`() {
        // The decision (NotificationAlerts) then says nothing shows: the store only reports.
        store.arrived(n(1, gone))
        assertEquals(listOf("arrived:1", "changed"), events)
        assertEquals(0, store.unreadCount(gone))
        assertEquals(0, store.totalUnread)
        assertFalse(store.hasUnread)
        assertEquals(1, store.all.size)
    }

    // ---- Counts ----

    @Test
    fun `badges count per installed app, the total adds them up`() {
        store.sync(listOf(n(1, feed), n(2, feed), n(3, mail), n(4, gone), n(5, gone)))
        assertEquals(2, store.unreadCount(feed))
        assertEquals(1, store.unreadCount(mail))
        assertEquals(0, store.unreadCount(gone))
        assertEquals(0, store.unreadCount(appId("unknown")))
        assertEquals(3, store.totalUnread)
        assertTrue(store.hasUnread)
    }

    @Test
    fun `only notifications of apps not installed leave the LED off`() {
        store.sync(listOf(n(1, gone)))
        assertFalse(store.hasUnread)
        assertEquals(0, store.totalUnread)
    }

    @Test
    fun `installing an app later makes its kept notifications count`() {
        var installed = false
        val late = ClientNotifications { installed }
        late.sync(listOf(n(1, gone)))
        assertEquals(0, late.unreadCount(gone))
        installed = true
        assertEquals(1, late.unreadCount(gone))
    }

    // ---- Reading, dismissing, clearing ----

    @Test
    fun `reading an app removes all of its notifications and returns the newest id`() {
        store.sync(listOf(n(1, feed), n(2, mail), n(3, feed, "thread:1")))
        events.clear()
        assertEquals(3L, store.read(feed))
        assertEquals(listOf("changed"), events)
        assertEquals(0, store.unreadCount(feed))
        assertEquals(1, store.unreadCount(mail))
    }

    @Test
    fun `reading an app without notifications says so and changes nothing`() {
        store.sync(listOf(n(1, mail)))
        events.clear()
        assertNull(store.read(feed))
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `dismissing removes one notification, once`() {
        store.sync(listOf(n(1), n(2)))
        events.clear()
        assertTrue(store.dismiss(1))
        assertFalse(store.dismiss(1))
        assertFalse(store.dismiss(99))
        assertEquals(listOf("changed"), events)
        assertEquals(1, store.unreadCount(feed))
    }

    @Test
    fun `the server clearing an app or a target`() {
        store.sync(listOf(n(1, feed, "thread:1"), n(2, feed, "thread:2"), n(3, feed, null), n(4, mail, "thread:1")))
        events.clear()
        store.clearedByServer(feed, "thread:1")
        assertEquals(listOf(2L, 3L, 4L), store.all.map { it.id })
        store.clearedByServer(feed, null)
        assertEquals(listOf(4L), store.all.map { it.id })
        assertEquals(listOf("changed", "changed"), events)
        events.clear()
        store.clearedByServer(feed, null)
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `disconnecting forgets everything`() {
        store.sync(listOf(n(1, delivered = false)))
        events.clear()
        store.reset()
        assertEquals(emptyList<PhoneNotification>(), store.all)
        assertEquals(emptyList<PhoneNotification>(), store.missedWhileAway)
        assertFalse(store.hasUnread)
        assertEquals(listOf("changed"), events)
    }

    // ---- Listeners ----

    @Test
    fun `a listener that throws doesn't keep the others from hearing`() {
        val quiet = ClientNotifications { true }
        val heard = mutableListOf<String>()
        quiet.addListener(object : ClientNotifications.Listener {
            override fun onArrived(notification: PhoneNotification) = throw IllegalStateException("boom")
            override fun onChanged() = throw IllegalStateException("boom")
        })
        quiet.addListener(object : ClientNotifications.Listener {
            override fun onArrived(notification: PhoneNotification) {
                heard += "arrived"
            }

            override fun onChanged() {
                heard += "changed"
            }
        })
        quiet.arrived(n(1))
        assertEquals(listOf("arrived", "changed"), heard)
        assertEquals(1, quiet.all.size)
    }

    @Test
    fun `a listener may remove itself while being called`() {
        val once = object : ClientNotifications.Listener {
            var calls = 0
            override fun onChanged() {
                calls++
                store.removeListener(this)
            }
        }
        store.addListener(once)
        store.arrived(n(1))
        store.arrived(n(2))
        assertEquals(1, once.calls)
    }

    @Test
    fun `listeners read the new state`() {
        val seen = mutableListOf<Int>()
        store.addListener(object : ClientNotifications.Listener {
            override fun onArrived(notification: PhoneNotification) {
                seen += store.unreadCount(feed)
            }
        })
        store.arrived(n(1))
        assertEquals(listOf(1), seen)
    }

    // ---- With the server's store: decision 2 ----

    @Test
    fun `a notification still in flight when the app is opened survives on both sides`() {
        val player = UUID(0, 1)
        val server = NotificationStore()
        // Notification 1 reaches the client.
        store.arrived(server.add(player, feed, Component.literal("t"), Component.literal("1"), null, 0, true))
        // The server sends 2; before it arrives, the player opens Feed.
        val inFlight = server.add(player, feed, Component.literal("t"), Component.literal("2"), null, 0, true)
        val upTo = store.read(feed)!!
        // The server gets the request, then the client gets 2.
        server.clearApp(player, feed, upTo)
        store.arrived(inFlight)
        assertEquals(listOf(inFlight), server.of(player))
        assertEquals(listOf(inFlight), store.all)
        assertEquals(1, store.unreadCount(feed))
    }

    @Test
    fun `server and client apply the same changes to the same lists`() {
        val player = UUID(0, 1)
        val server = NotificationStore()
        fun send(app: ResourceLocation, target: String?) =
            store.arrived(server.add(player, app, Component.literal("t"), Component.literal("x"), target, 0, true))

        repeat(30) { send(feed, "thread:${it % 3}") }
        repeat(30) { send(mail, null) }
        server.clearTarget(player, feed, "thread:1")
        store.clearedByServer(feed, "thread:1")
        val upTo = store.read(mail)!!
        server.clearApp(player, mail, upTo)
        val dismissed = store.all.first().id
        store.dismiss(dismissed)
        server.remove(player, dismissed)
        assertEquals(server.of(player), store.all)
    }
}

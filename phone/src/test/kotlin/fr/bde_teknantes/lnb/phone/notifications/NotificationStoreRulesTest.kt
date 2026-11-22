package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.notifications.server.NotificationStore
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The server's store of every player's notifications, apart from saving (see [NotificationStoreSaveTest]). */
class NotificationStoreRulesTest {
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val mail = ResourceLocation.fromNamespaceAndPath("test", "mail")
    private val alice = UUID(0, 1)
    private val bob = UUID(0, 2)

    private fun NotificationStore.add(player: UUID, app: ResourceLocation = feed, target: String? = null, delivered: Boolean = false) =
        add(player, app, Component.literal("t"), Component.literal("x"), target, 1000L, delivered)

    private fun NotificationStore.ids(player: UUID) = of(player).map { it.id }

    @Test
    fun `ids start at 1 and grow by one for the whole server`() {
        val store = NotificationStore()
        assertEquals(1L, store.nextId)
        assertEquals(1L, store.add(alice).id)
        assertEquals(2L, store.add(bob).id)
        assertEquals(3L, store.add(alice).id)
        assertEquals(4L, store.nextId)
    }

    @Test
    fun `stores every field as given`() {
        val store = NotificationStore()
        val title = Component.literal("title")
        val text = Component.translatable("k", "arg")
        val n = store.add(alice, mail, title, text, "thread:3", 123456789L, delivered = true)
        assertEquals(PhoneNotification(1, mail, title, text, "thread:3", 123456789L, true), n)
        assertEquals(listOf(n), store.of(alice))
    }

    @Test
    fun `a full player loses their oldest, other players keep theirs`() {
        val store = NotificationStore()
        repeat(NotificationList.CAPACITY) { store.add(alice) }
        val bobs = store.add(bob)
        val newest = store.add(alice)
        assertEquals(NotificationList.CAPACITY, store.of(alice).size)
        assertEquals((2L..NotificationList.CAPACITY.toLong()).toList() + newest.id, store.ids(alice))
        assertEquals(listOf(bobs), store.of(bob))
    }

    @Test
    fun `ids of dropped notifications are never given again`() {
        val store = NotificationStore(capacity = 1)
        val first = store.add(alice)
        store.add(alice)
        store.clearApp(alice, feed)
        assertTrue(store.add(alice).id > first.id + 1)
    }

    @Test
    fun `a player without notifications has an empty list and no entry`() {
        val store = NotificationStore()
        assertEquals(emptyList<PhoneNotification>(), store.of(alice))
        store.add(alice)
        store.clearApp(alice, feed)
        assertEquals(emptySet<UUID>(), store.playersWithNotifications)
    }

    @Test
    fun `clearing an app touches one player and one app`() {
        val store = NotificationStore()
        store.add(alice, feed)
        store.add(alice, mail)
        store.add(bob, feed)
        assertEquals(1, store.clearApp(alice, feed).size)
        assertEquals(listOf(mail), store.of(alice).map { it.appId })
        assertEquals(listOf(feed), store.of(bob).map { it.appId })
    }

    @Test
    fun `clearing up to an id keeps what came after it`() {
        val store = NotificationStore()
        val a = store.add(alice)
        val b = store.add(alice)
        val c = store.add(alice)
        assertEquals(listOf(a, b), store.clearApp(alice, feed, upToId = b.id))
        assertEquals(listOf(c), store.of(alice))
    }

    @Test
    fun `clearing up to another player's id uses the number only`() {
        // Ids are global: Alice's request carrying the id Bob's notification got still clears Alice's older ones.
        val store = NotificationStore()
        val mine = store.add(alice)
        val bobs = store.add(bob)
        val later = store.add(alice)
        assertEquals(listOf(mine), store.clearApp(alice, feed, upToId = bobs.id))
        assertEquals(listOf(later), store.of(alice))
        assertEquals(listOf(bobs), store.of(bob))
    }

    @Test
    fun `clearing what doesn't exist changes nothing`() {
        val store = NotificationStore()
        store.add(alice, feed)
        assertEquals(emptyList<PhoneNotification>(), store.clearApp(alice, mail))
        assertEquals(emptyList<PhoneNotification>(), store.clearApp(bob, feed))
        assertEquals(emptyList<PhoneNotification>(), store.clearTarget(bob, feed, "x"))
        assertEquals(emptyList<PhoneNotification>(), store.clearApp(alice, feed, upToId = 0))
        assertEquals(1, store.of(alice).size)
    }

    @Test
    fun `clearing a target leaves the app's other notifications`() {
        val store = NotificationStore()
        val t1 = store.add(alice, feed, "thread:1")
        store.add(alice, feed, "thread:2")
        store.add(alice, feed, null)
        store.add(alice, mail, "thread:1")
        store.add(bob, feed, "thread:1")
        assertEquals(listOf(t1), store.clearTarget(alice, feed, "thread:1"))
        assertEquals(3, store.of(alice).size)
        assertEquals(1, store.of(bob).size)
    }

    @Test
    fun `a player removes only their own notification by id`() {
        val store = NotificationStore()
        val mine = store.add(alice)
        val bobs = store.add(bob)
        assertNull(store.remove(alice, bobs.id))
        assertEquals(listOf(bobs), store.of(bob))
        assertEquals(mine, store.remove(alice, mine.id))
        assertNull(store.remove(alice, mine.id))
        assertNull(store.remove(UUID(9, 9), mine.id))
    }

    @Test
    fun `marking delivered reports a change only once, and for one player`() {
        val store = NotificationStore()
        store.add(alice, delivered = false)
        store.add(alice, delivered = true)
        store.add(bob, delivered = false)
        assertTrue(store.markDelivered(alice))
        assertTrue(store.of(alice).all { it.delivered })
        assertFalse(store.markDelivered(alice))
        assertFalse(store.of(bob).single().delivered)
        assertFalse(store.markDelivered(UUID(9, 9)))
    }

    @Test
    fun `marking delivered keeps ids and order`() {
        val store = NotificationStore()
        repeat(5) { store.add(alice, delivered = it % 2 == 0) }
        val before = store.ids(alice)
        store.markDelivered(alice)
        assertEquals(before, store.ids(alice))
    }

    @Test
    fun `a notification sent after delivery waits for the next login`() {
        // The login marks what the client was sent; a later one sent while away stays undelivered.
        val store = NotificationStore()
        store.add(alice, delivered = false)
        store.markDelivered(alice)
        val away = store.add(alice, delivered = false)
        assertEquals(listOf(away), store.of(alice).filter { !it.delivered })
    }
}

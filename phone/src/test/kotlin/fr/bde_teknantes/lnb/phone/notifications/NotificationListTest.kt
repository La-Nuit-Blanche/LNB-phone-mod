package fr.bde_teknantes.lnb.phone.notifications

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The capped list both sides keep, whose rules must give the same result on the server and on the client. */
class NotificationListTest {
    private val feed = ResourceLocation.fromNamespaceAndPath("test", "feed")
    private val mail = ResourceLocation.fromNamespaceAndPath("test", "mail")

    private fun n(id: Long, app: ResourceLocation = feed, target: String? = null, text: String = "n$id", delivered: Boolean = true) =
        PhoneNotification(id, app, Component.literal("t"), Component.literal(text), target, id * 10, delivered)

    private fun NotificationList.ids() = all.map { it.id }

    @Test
    fun `capacity must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { NotificationList(0) }
        assertThrows(IllegalArgumentException::class.java) { NotificationList(-1) }
        assertEquals(50, NotificationList().capacity)
    }

    @Test
    fun `fills up to the capacity without dropping`() {
        val list = NotificationList(3)
        for (id in 1L..3L) assertEquals(emptyList<PhoneNotification>(), list.add(n(id)))
        assertEquals(listOf(1L, 2L, 3L), list.ids())
    }

    @Test
    fun `the 51st notification drops the oldest, and only it`() {
        val list = NotificationList()
        for (id in 1L..50L) list.add(n(id))
        assertEquals(listOf(n(1)), list.add(n(51)))
        assertEquals((2L..51L).toList(), list.ids())
    }

    @Test
    fun `the oldest is dropped whatever its app`() {
        val list = NotificationList(2)
        list.add(n(1, mail))
        list.add(n(2, feed))
        assertEquals(listOf(1L), list.add(n(3, feed)).map { it.id })
        assertEquals(0, list.count(mail))
    }

    @Test
    fun `keeps id order whatever the arrival order`() {
        val list = NotificationList()
        for (id in listOf(5L, 1L, 9L, 3L, 7L)) list.add(n(id))
        assertEquals(listOf(1L, 3L, 5L, 7L, 9L), list.ids())
        assertEquals(9L, list.newest?.id)
    }

    @Test
    fun `an older notification arriving at a full list is the one dropped`() {
        val list = NotificationList(3)
        for (id in listOf(5L, 6L, 7L)) list.add(n(id))
        assertEquals(listOf(n(2)), list.add(n(2)))
        assertEquals(listOf(5L, 6L, 7L), list.ids())
    }

    @Test
    fun `one arriving between kept ids at a full list drops the oldest`() {
        val list = NotificationList(3)
        for (id in listOf(5L, 7L, 9L)) list.add(n(id))
        assertEquals(listOf(n(5)), list.add(n(8)))
        assertEquals(listOf(7L, 8L, 9L), list.ids())
    }

    @Test
    fun `the same id twice is stored once, the second copy replacing the first`() {
        val list = NotificationList()
        list.add(n(1, delivered = false))
        assertEquals(emptyList<PhoneNotification>(), list.add(n(1, delivered = true)))
        assertEquals(1, list.size)
        assertTrue(list[1]!!.delivered)
    }

    @Test
    fun `a repeat at a full list drops nothing`() {
        val list = NotificationList(2)
        list.add(n(1))
        list.add(n(2))
        assertEquals(emptyList<PhoneNotification>(), list.add(n(1)))
        assertEquals(listOf(1L, 2L), list.ids())
    }

    @Test
    fun `replacing everything sorts, removes repeats, and keeps the newest`() {
        val list = NotificationList(3)
        list.add(n(100))
        val dropped = list.replaceAll(listOf(n(4), n(2), n(4, text = "second copy"), n(1), n(3)))
        assertEquals(listOf(n(1)), dropped)
        assertEquals(listOf(2L, 3L, 4L), list.ids())
        // The first copy of a repeated id wins.
        assertEquals("n4", list[4]!!.text.string)
        assertNull(list[100])
    }

    @Test
    fun `replacing with nothing empties`() {
        val list = NotificationList()
        list.add(n(1))
        list.replaceAll(emptyList())
        assertTrue(list.isEmpty())
        assertNull(list.newest)
    }

    @Test
    fun `counts and lists per app`() {
        val list = NotificationList()
        list.add(n(1, feed))
        list.add(n(2, mail))
        list.add(n(3, feed))
        assertEquals(2, list.count(feed))
        assertEquals(1, list.count(mail))
        assertEquals(0, list.count(ResourceLocation.fromNamespaceAndPath("test", "none")))
        assertEquals(listOf(1L, 3L), list.of(feed).map { it.id })
        assertEquals(3L, list.newestId(feed))
        assertEquals(2L, list.newestId(mail))
        assertNull(list.newestId(ResourceLocation.fromNamespaceAndPath("test", "none")))
    }

    @Test
    fun `removing an app removes all of it, or up to an id included`() {
        val list = NotificationList()
        for (id in 1L..6L) list.add(n(id, if (id % 2 == 0L) mail else feed))
        assertEquals(listOf(1L, 3L), list.removeApp(feed, upToId = 3).map { it.id })
        assertEquals(listOf(2L, 4L, 5L, 6L), list.ids())
        assertEquals(listOf(2L, 4L, 6L), list.removeApp(mail).map { it.id })
        assertEquals(listOf(5L), list.ids())
        assertEquals(emptyList<PhoneNotification>(), list.removeApp(mail))
    }

    @Test
    fun `removing up to an id older than all removes nothing`() {
        val list = NotificationList()
        list.add(n(5))
        assertEquals(emptyList<PhoneNotification>(), list.removeApp(feed, upToId = 4))
        assertEquals(emptyList<PhoneNotification>(), list.removeApp(feed, upToId = Long.MIN_VALUE))
        assertEquals(1, list.size)
    }

    @Test
    fun `removing a target removes only that app's notifications leading there`() {
        val list = NotificationList()
        list.add(n(1, feed, "thread:1"))
        list.add(n(2, feed, "thread:2"))
        list.add(n(3, mail, "thread:1"))
        list.add(n(4, feed, null))
        list.add(n(5, feed, "thread:1"))
        assertEquals(listOf(1L, 5L), list.removeTarget(feed, "thread:1").map { it.id })
        assertEquals(listOf(2L, 3L, 4L), list.ids())
        // A null target is never matched by a target string, not even an empty one.
        assertEquals(emptyList<PhoneNotification>(), list.removeTarget(feed, ""))
    }

    @Test
    fun `removing by id`() {
        val list = NotificationList()
        list.add(n(1))
        list.add(n(2))
        assertEquals(n(1), list.remove(1))
        assertNull(list.remove(1))
        assertNull(list.remove(99))
        assertEquals(listOf(2L), list.ids())
    }

    @Test
    fun `update changes every notification but never an id`() {
        val list = NotificationList()
        list.add(n(1, delivered = false))
        list.add(n(2, delivered = false))
        list.update { it.copy(delivered = true) }
        assertTrue(list.all.all { it.delivered })
        assertThrows(IllegalArgumentException::class.java) { list.update { it.copy(id = it.id + 100) } }
    }

    @Test
    fun `all is a copy`() {
        val list = NotificationList()
        list.add(n(1))
        val snapshot = list.all
        list.add(n(2))
        list.clear()
        assertEquals(listOf(1L), snapshot.map { it.id })
    }
}

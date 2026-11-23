package fr.bde_teknantes.lnb.phone.feed.server.backend.memory

import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.server.MutableClock
import fr.bde_teknantes.lnb.phone.feed.server.Players
import fr.bde_teknantes.lnb.phone.feed.server.Players.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.server.Players.KAEL
import fr.bde_teknantes.lnb.phone.feed.server.Players.LUNE
import fr.bde_teknantes.lnb.phone.feed.server.Players.MIKA
import fr.bde_teknantes.lnb.phone.feed.server.Players.VISITOR
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** The memory backend against the contract's vectors (`docs/feed-mod.md` 13.2 to 13.6). */
class MemoryBackendTest {
    private val clock = MutableClock()
    private val events = RecordingListener()
    private val backend = MemoryBackend(clock, Players.directory(), guests = listOf("Visitor42"), rateLimits = false).apply {
        start(events)
        setAnnouncer(KAEL, true, null).ok()
        events.clear()
    }
    private var keys = 0L

    private fun key() = UUID(42, keys++)

    private fun <T> CompletableFuture<BackendResult<T>>.ok(): T {
        val result = join()
        assertTrue(result is BackendResult.Ok, "expected Ok, got $result")
        return (result as BackendResult.Ok).body
    }

    private fun <T> CompletableFuture<BackendResult<T>>.refused(status: Int, problem: String?): BackendResult.Refused {
        val result = join()
        assertTrue(result is BackendResult.Refused && result.status == status && result.problem == problem, "expected $status $problem, got $result")
        return result as BackendResult.Refused
    }

    private fun post(player: UUID, feed: FeedKind = FeedKind.GLOBAL, text: String = "Salut", anonymous: Boolean = false): PostView {
        clock.advanceSeconds(1)
        return backend.createPost(key(), player, feed, text, anonymous).ok().post
    }

    private fun reply(player: UUID, thread: UUID, replyTo: UUID? = null, anonymous: Boolean = false): PostView {
        clock.advanceSeconds(1)
        return backend.createReply(key(), player, thread, replyTo, "Moi !", anonymous).ok().post
    }

    private fun view(id: UUID, viewer: UUID): PostView = backend.views(listOf(viewer), listOf(id)).ok().getValue(viewer).single()

    // ---- 13.2 Idempotency ----

    @Test
    fun `idempotency, vector 13_2`() {
        val k = key()
        val first = backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "Salut", false).ok()
        assertFalse(first.replayed)
        val again = backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "Salut", false).ok()
        assertTrue(again.replayed)
        assertEquals(first.post.id, again.post.id)
        assertEquals(1, backend.page(CAMILLE, FeedKind.GLOBAL, null, 20).ok().posts.size)

        backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "Salut !", false).refused(409, "idempotencyMismatch")
        backend.createPost(k, MIKA, FeedKind.GLOBAL, "Salut", false).refused(409, "idempotencyMismatch")
        assertTrue(backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "  Salut  ", false).ok().replayed)

        backend.setWriting(false)
        assertEquals(BackendResult.Disabled("feed.writing"), backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "Salut", false).join())
        backend.setWriting(true)
        assertTrue(backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "Salut", false).ok().replayed)

        backend.delete(CAMILLE, first.post.id).ok()
        backend.createPost(k, CAMILLE, FeedKind.GLOBAL, "Salut", false).refused(404, "notFound")
    }

    // ---- 13.3 Anonymity, by viewer ----

    @Test
    fun `an anonymous Global post, by viewer, vector 13_3`() {
        val post = post(CAMILLE, anonymous = true)
        fun check(viewer: UUID, author: Boolean, mine: Boolean, canDelete: Boolean) {
            val seen = view(post.id, viewer)
            assertEquals(author, seen.author != null, "author for $viewer")
            assertEquals(mine, seen.mine, "mine for $viewer")
            assertEquals(canDelete, seen.canDelete, "canDelete for $viewer")
            assertFalse(seen.canPin)
            assertTrue(seen.anonymous)
        }
        check(CAMILLE, author = true, mine = true, canDelete = true)
        check(LUNE, author = true, mine = false, canDelete = true)
        check(KAEL, author = false, mine = false, canDelete = false)
        check(MIKA, author = false, mine = false, canDelete = false)
        check(VISITOR, author = false, mine = false, canDelete = false)
        assertTrue(post.mine)
    }

    @Test
    fun `replies to an anonymous post and their notifications, vector 13_3`() {
        val post = post(CAMILLE, anonymous = true)
        val mikaReply = reply(MIKA, post.id)
        assertNull(mikaReply.replyTo)
        val toCamille = backend.pendingNotifications().ok().items.single { it.post.id == mikaReply.id }
        assertEquals(QueuedNotification.Kind.REPLY, toCamille.kind)
        assertEquals(listOf(CAMILLE), toCamille.players)
        assertEquals("MikaPvP", toCamille.post.author?.username)

        val luneReply = reply(LUNE, post.id, replyTo = mikaReply.id, anonymous = true)
        val forKael = view(luneReply.id, KAEL)
        assertNull(forKael.author)
        assertEquals(ReplyRef(mikaReply.id, false, "MikaPvP"), forKael.replyTo)
        val toMika = backend.pendingNotifications().ok().items.single { it.post.id == luneReply.id }
        assertEquals(listOf(MIKA), toMika.players)
        assertNull(toMika.post.author)

        // A reply to an anonymous reply names nobody, for moderators too.
        val camilleReply = reply(CAMILLE, post.id, replyTo = luneReply.id)
        assertEquals(ReplyRef(luneReply.id, true, null), view(camilleReply.id, LUNE).replyTo)
    }

    @Test
    fun `an anonymous announcement, by viewer, and its notification, vector 13_3`() {
        val post = post(KAEL, feed = FeedKind.ANNOUNCEMENTS, anonymous = true)
        fun check(viewer: UUID, author: Boolean, mine: Boolean, canDelete: Boolean, canPin: Boolean) {
            val seen = view(post.id, viewer)
            assertEquals(author, seen.author != null, "author for $viewer")
            assertEquals(mine, seen.mine, "mine for $viewer")
            assertEquals(canDelete, seen.canDelete, "canDelete for $viewer")
            assertEquals(canPin, seen.canPin, "canPin for $viewer")
        }
        check(KAEL, author = true, mine = true, canDelete = true, canPin = true)
        check(LUNE, author = true, mine = false, canDelete = true, canPin = true)
        check(CAMILLE, author = false, mine = false, canDelete = false, canPin = false)
        check(VISITOR, author = false, mine = false, canDelete = false, canPin = false)

        val notification = backend.pendingNotifications().ok().items.single()
        assertEquals(QueuedNotification.Kind.ANNOUNCEMENT, notification.kind)
        assertNull(notification.players)
        assertEquals(listOf(KAEL), notification.except)
        assertNull(notification.post.author)
        assertEquals(listOf(post.id), events.posts)
    }

    @Test
    fun `a reply to oneself notifies nobody, and only announcers post announcements`() {
        val post = post(CAMILLE)
        reply(CAMILLE, post.id)
        assertTrue(backend.pendingNotifications().ok().items.isEmpty())
        backend.createPost(key(), CAMILLE, FeedKind.ANNOUNCEMENTS, "Hello", false).refused(403, "notAllowed")
        backend.createPost(key(), VISITOR, FeedKind.GLOBAL, "Hello", false).refused(404, "notLinked")
        backend.createPost(key(), CAMILLE, FeedKind.GLOBAL, " \n ", false).refused(400, "emptyText")
        backend.createPost(key(), CAMILLE, FeedKind.GLOBAL, "a".repeat(281), false).refused(400, "tooLong")
    }

    // ---- 13.4 Pagination ----

    @Test
    fun `keyset pages, vector 13_4`() {
        val g = (1..25).map { post(MIKA, text = "G$it").id }
        val first = backend.page(CAMILLE, FeedKind.GLOBAL, null, 20).ok()
        assertEquals(g.reversed().take(20), first.posts.map { it.id })
        val c1 = first.next!!
        assertTrue(c1.length <= 128)

        post(MIKA, text = "G26")
        backend.delete(MIKA, g[2]).ok()
        val second = backend.page(CAMILLE, FeedKind.GLOBAL, c1, 20).ok()
        assertEquals(listOf(g[4], g[3], g[1], g[0]), second.posts.map { it.id })
        assertNull(second.next)
        assertTrue(second.pinned.isEmpty())
    }

    @Test
    fun `the pinned group comes first and isn't repeated, vector 13_4`() {
        val a = (1..5).map { post(LUNE, feed = FeedKind.ANNOUNCEMENTS, text = "A$it").id }
        backend.pin(LUNE, a[1], true, false).ok()
        clock.advanceSeconds(1)
        backend.pin(LUNE, a[3], true, false).ok()
        val page = backend.page(CAMILLE, FeedKind.ANNOUNCEMENTS, null, 20).ok()
        assertEquals(listOf(a[3], a[1]), page.pinned.map { it.id })
        assertEquals(listOf(a[4], a[2], a[0]), page.posts.map { it.id })
        assertTrue(backend.page(CAMILLE, FeedKind.GLOBAL, null, 20).ok().pinned.isEmpty())
    }

    @Test
    fun `a malformed cursor or limit is a plain 400`() {
        backend.page(CAMILLE, FeedKind.GLOBAL, "nope", 20).refused(400, null)
        backend.page(CAMILLE, FeedKind.GLOBAL, null, 0).refused(400, null)
        backend.page(CAMILLE, FeedKind.GLOBAL, null, 51).refused(400, null)
    }

    // ---- 13.5 Pins ----

    @Test
    fun `three pins at most, vector 13_5`() {
        val (a, b, c, d) = (1..4).map { post(LUNE, feed = FeedKind.ANNOUNCEMENTS, text = "P$it").id }
        val dReply = reply(MIKA, d)
        for (id in listOf(a, b, c)) {
            clock.advanceSeconds(1)
            backend.pin(LUNE, id, true, false).ok()
        }
        events.clear()

        assertEquals(a, backend.pin(LUNE, d, true, false).refused(409, "pinLimit").oldestPin)
        assertTrue(events.pins.isEmpty())

        val replaced = backend.pin(LUNE, d, true, true).ok()
        assertEquals(listOf(d, c, b), replaced.pinned.map { it.id })
        assertEquals(a, replaced.unpinned)
        assertEquals(listOf(listOf(d, c, b)), events.pins.map { group -> group.map { it.id } })

        val again = backend.pin(LUNE, d, true, false).ok()
        assertEquals(listOf(d, c, b), again.pinned.map { it.id })
        assertNull(again.unpinned)
        assertEquals(1, events.pins.size)

        assertEquals(listOf(d, b), backend.pin(KAEL, c, false, false).ok().pinned.map { it.id })
        backend.pin(MIKA, b, true, false).refused(403, "notAllowed")
        backend.pin(LUNE, post(MIKA).id, true, false).refused(409, "notPinnable")
        backend.pin(LUNE, dReply.id, true, false).refused(409, "notPinnable")

        // Unpinning what isn't pinned changes nothing and answers the group, a Global post and a reply included.
        val pinsBefore = events.pins.size
        val global = post(MIKA)
        for (id in listOf(global.id, dReply.id, a)) {
            val group = backend.pin(LUNE, id, false, false).ok()
            assertEquals(listOf(d, b), group.pinned.map { it.id })
            assertNull(group.unpinned)
        }
        assertEquals(pinsBefore, events.pins.size, "no pins event for an unpin that changes nothing")
        backend.pin(MIKA, global.id, false, false).refused(403, "notAllowed")

        events.clear()
        backend.delete(LUNE, d).ok()
        assertEquals(listOf(listOf(d, dReply.id)), events.deleted.map { items -> items.map { it.id } })
        assertEquals(listOf(listOf(b)), events.pins.map { group -> group.map { it.id } })
        assertEquals(listOf("deleted", "pins"), events.order)
    }

    // ---- 13.6 Unread and markers ----

    @Test
    fun `tab dots and unseen replies, vector 13_6`() {
        val g1 = post(MIKA, text = "G1")
        assertTrue(backend.player(CAMILLE).ok().unread.global)
        backend.markFeedSeen(CAMILLE, FeedKind.GLOBAL, g1.createdAt).ok()
        assertFalse(backend.player(CAMILLE).ok().unread.global)
        val g2 = post(CAMILLE, text = "G2")
        assertFalse(backend.player(CAMILLE).ok().unread.global)
        post(MIKA, text = "G3")
        assertTrue(backend.player(CAMILLE).ok().unread.global)

        val answer = reply(MIKA, g2.id)
        assertEquals(1, view(g2.id, CAMILLE).unseenReplies)
        assertTrue(view(answer.id, CAMILLE).answersMe)
        assertFalse(view(answer.id, LUNE).answersMe)
        assertEquals(0, view(g2.id, MIKA).unseenReplies)

        assertEquals(1, backend.thread(CAMILLE, g2.id, null, 50).ok().post.unseenReplies)
        backend.markThreadSeen(CAMILLE, g2.id).ok()
        assertEquals(0, view(g2.id, CAMILLE).unseenReplies)
    }

    @Test
    fun `a guest's state and marks`() {
        val state = backend.player(VISITOR).ok()
        assertFalse(state.linked)
        assertNull(state.me)
        assertFalse(state.rights.write)
        assertFalse(state.unread.global)
        backend.markFeedSeen(VISITOR, FeedKind.GLOBAL, Instant.EPOCH).ok()
        val lune = backend.player(LUNE).ok()
        assertTrue(lune.rights.moderate && lune.rights.announce && lune.rights.write)
        val kael = backend.player(KAEL).ok()
        assertTrue(kael.rights.announce && !kael.rights.moderate)
    }

    // ---- Limits and switches ----

    @Test
    fun `a fourth post in a minute is refused with the seconds to wait`() {
        val limited = MemoryBackend(clock, Players.directory())
        repeat(3) { limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "Post $it", false).ok() }
        clock.advanceSeconds(20)
        assertEquals(40, limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "Post 4", false).refused(429, "rateLimited").retryAfterSeconds)
        clock.advanceSeconds(41)
        limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "Post 4", false).ok()
    }

    @Test
    fun `the dev switches answer unavailable or disabled, and tell the listener`() {
        backend.setOffline(true)
        assertTrue(backend.player(CAMILLE).join() is BackendResult.Unavailable)
        backend.setOffline(false)
        backend.setDisabled(true)
        assertEquals(BackendResult.Disabled("feed"), backend.page(CAMILLE, FeedKind.GLOBAL, null, 20).join())
        backend.setDisabled(false)
        assertEquals(listOf("disconnected", "connected", "disconnected(disabled)", "connected"), events.order)
    }

    @Test
    fun `the announcer command's rules`() {
        backend.setAnnouncer(CAMILLE, true, Issuer(MIKA, "MikaPvP")).refused(403, "issuerNotAllowed")
        val lune = Issuer(LUNE, "Admin_Lune")
        assertTrue(backend.setAnnouncer(CAMILLE, true, lune).ok().announcer)
        assertEquals(listOf(listOf(CAMILLE)), events.rights)
        backend.setAnnouncer(CAMILLE, true, lune).ok()
        assertEquals(1, events.rights.size)
        assertTrue(backend.setAnnouncer(LUNE, true, null).ok().viaRole)
        backend.setAnnouncer(LUNE, false, null).refused(409, "impliedByRole")
        backend.setAnnouncer(VISITOR, true, null).refused(404, "notLinked")
        assertEquals(setOf("Cam_Roux", "Kael_Builder"), backend.announcers().ok().flatMap { it.accounts }.map { it.username }.toSet())
    }

    @Test
    fun `the sample seed matches the art direction`() {
        val sample = MemoryBackend(MutableClock(SampleContent.FIXED_NOW), Players.directory(), rateLimits = false)
        val ids = SampleContent.seed(sample, SampleContent.FIXED_NOW)
        val announcements = sample.page(SampleContent.CAMILLE, FeedKind.ANNOUNCEMENTS, null, 20).ok()
        assertEquals(listOf(ids["5"], ids["9"]), announcements.pinned.map { it.id })
        assertEquals(listOf(ids["10"], ids["6"], ids["7"]), announcements.posts.map { it.id })
        val global = sample.page(SampleContent.CAMILLE, FeedKind.GLOBAL, null, 20).ok()
        assertEquals(listOf("1", "2", "8", "3", "4").map { ids[it] }, global.posts.map { it.id })
        val first = global.posts.first()
        assertEquals(12, first.likes)
        assertTrue(first.liked)
        assertEquals(3, first.replies)
        assertEquals(2, global.posts.single { it.id == ids["8"] }.unseenReplies)
        assertNull(global.posts.single { it.id == ids["2"] }.author)
        assertEquals("Alexandre M.", first.author?.realName)
        val thread = sample.thread(SampleContent.CAMILLE, ids["1"], null, 50).ok()
        assertEquals(listOf("R1", "R2", "R3").map { ids[it] }, thread.replies.map { it.id })
        assertEquals(ReplyRef(ids["R2"], true, null), thread.replies.last().replyTo)
    }

    private class RecordingListener : FeedEventListener {
        val order = ArrayList<String>()
        val posts = ArrayList<UUID>()
        val deleted = ArrayList<List<RemovedItem>>()
        val counts = ArrayList<List<CountUpdate>>()
        val pins = ArrayList<List<Pin>>()
        val rights = ArrayList<List<UUID>>()

        fun clear() {
            order.clear(); posts.clear(); deleted.clear(); counts.clear(); pins.clear(); rights.clear()
        }

        override fun onConnected() { order += "connected" }
        override fun onDisconnected(disabled: Boolean) { order += if (disabled) "disconnected(disabled)" else "disconnected" }
        override fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant) { order += "post"; posts += id }
        override fun onDeleted(items: List<RemovedItem>) { order += "deleted"; deleted += items }
        override fun onCounts(items: List<CountUpdate>) { order += "counts"; counts += items }
        override fun onPins(pinned: List<Pin>) { order += "pins"; pins += pinned }
        override fun onRights(players: List<UUID>) { order += "rights"; rights += players }
        override fun onNotification() { order += "notification" }
    }
}

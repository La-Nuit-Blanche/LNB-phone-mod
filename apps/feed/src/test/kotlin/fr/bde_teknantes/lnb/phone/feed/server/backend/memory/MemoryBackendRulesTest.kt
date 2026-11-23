package fr.bde_teknantes.lnb.phone.feed.server.backend.memory

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.server.MutableClock
import fr.bde_teknantes.lnb.phone.feed.server.Players
import fr.bde_teknantes.lnb.phone.feed.server.Players.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.server.Players.KAEL
import fr.bde_teknantes.lnb.phone.feed.server.Players.LUNE
import fr.bde_teknantes.lnb.phone.feed.server.Players.MIKA
import fr.bde_teknantes.lnb.phone.feed.server.Players.VISITOR
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * The memory backend beyond the vectors `MemoryBackendTest` covers (`docs/feed-mod.md` 4, 6, 7 and 13): the text
 * vectors as the site answers them, keys checked before rights and rates, keyset pages across equal times, the
 * rate-limit windows at their edges, and "seen" capped at now.
 */
class MemoryBackendRulesTest {
    private val clock = MutableClock()
    private val backend = MemoryBackend(clock, Players.directory(), guests = listOf("Visitor42"), rateLimits = false).apply {
        setAnnouncer(KAEL, true, null)
    }
    private var keys = 0L

    private fun key() = UUID(43, keys++)

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

    private fun post(backend: MemoryBackend, player: UUID, feed: FeedKind = FeedKind.GLOBAL, text: String = "Salut") =
        backend.createPost(key(), player, feed, text, false).ok().post

    private fun view(id: UUID, viewer: UUID): PostView = backend.views(listOf(viewer), listOf(id)).ok().getValue(viewer).single()

    // ---- 13.1 as the site answers ----

    @Test
    fun `the text vectors of 13_1, as stored and answered`() {
        val accepted = listOf(
            "  Salut  " to "Salut", "été" to "été", "👍🏽" to "👍🏽", "🇫🇷" to "🇫🇷", "a".repeat(280) to "a".repeat(280),
            "a".repeat(279) + "é" to "a".repeat(279) + "é", "a\r\nb" to "a\nb", "a\rb" to "a\nb", "a\tb" to "a b", "§cRouge" to "cRouge",
            "a\u0000b\u0085c" to "abc", "a\n\n\n\nb" to "a\n\nb", "　bonjour " to "bonjour", "﻿hi" to "﻿hi", "a​b" to "a​b",
        )
        for ((raw, stored) in accepted) assertEquals(stored, post(backend, CAMILLE, text = raw).text)
        backend.createPost(key(), CAMILLE, FeedKind.GLOBAL, "a".repeat(281), false).refused(400, "tooLong")
        backend.createPost(key(), CAMILLE, FeedKind.GLOBAL, "   \n   ", false).refused(400, "emptyText")
        backend.createPost(key(), CAMILLE, FeedKind.GLOBAL, "", false).refused(400, "emptyText")
        // Past the 4,000 raw characters the site takes: a plain 400, before anything else.
        backend.createPost(key(), CAMILLE, FeedKind.GLOBAL, " ".repeat(4001), false).refused(400, null)
        val thread = post(backend, MIKA)
        backend.createReply(key(), CAMILLE, thread.id, null, "a".repeat(281), false).refused(400, "tooLong")
        assertEquals("a\nb", backend.createReply(key(), CAMILLE, thread.id, null, "a\r\nb", false).ok().post.text)
    }

    // ---- 6: keys before everything else ----

    @Test
    fun `a replay works whatever changed since, rights and rate included`() {
        val limited = MemoryBackend(clock, Players.directory())
        limited.setAnnouncer(KAEL, true, null)
        val k = key()
        val first = limited.createPost(k, KAEL, FeedKind.ANNOUNCEMENTS, "Événement", false).ok()
        limited.createPost(key(), KAEL, FeedKind.GLOBAL, "deux", false).ok()
        limited.createPost(key(), KAEL, FeedKind.GLOBAL, "trois", false).ok()
        limited.createPost(key(), KAEL, FeedKind.GLOBAL, "quatre", false).refused(429, "rateLimited")
        limited.setAnnouncer(KAEL, false, null)
        val again = limited.createPost(k, KAEL, FeedKind.ANNOUNCEMENTS, "Événement", false).ok()
        assertTrue(again.replayed)
        assertEquals(first.post.id, again.post.id)
        assertFalse(again.post.canPin, "the view is for now: Kael no longer announces")
        // The same key for a reply is another request.
        limited.createReply(k, KAEL, first.post.id, null, "Événement", false).refused(409, "idempotencyMismatch")
        // So is the same text made anonymous, or sent to the other feed.
        limited.createPost(k, KAEL, FeedKind.ANNOUNCEMENTS, "Événement", true).refused(409, "idempotencyMismatch")
        limited.createPost(k, KAEL, FeedKind.GLOBAL, "Événement", false).refused(409, "idempotencyMismatch")
    }

    @Test
    fun `a reply's key is bound to its thread and its target, and a deleted reply answers notFound`() {
        val thread = post(backend, MIKA)
        val other = post(backend, MIKA, text = "autre")
        val target = backend.createReply(key(), LUNE, thread.id, null, "cible", false).ok().post
        val k = key()
        val reply = backend.createReply(k, CAMILLE, thread.id, target.id, "Moi !", false).ok()
        assertTrue(backend.createReply(k, CAMILLE, thread.id, target.id, " Moi ! ", false).ok().replayed)
        backend.createReply(k, CAMILLE, other.id, target.id, "Moi !", false).refused(409, "idempotencyMismatch")
        backend.createReply(k, CAMILLE, thread.id, null, "Moi !", false).refused(409, "idempotencyMismatch")
        backend.delete(CAMILLE, reply.post.id).ok()
        backend.createReply(k, CAMILLE, thread.id, target.id, "Moi !", false).refused(404, "notFound")
    }

    // ---- 4.6, 4.7, 4.9: what one may act on ----

    @Test
    fun `replies need a live main post, and a target in the same thread`() {
        val thread = post(backend, MIKA)
        val other = post(backend, MIKA, text = "autre")
        val inOther = backend.createReply(key(), LUNE, other.id, null, "ailleurs", false).ok().post
        backend.createReply(key(), CAMILLE, inOther.id, null, "x", false).refused(404, "notFound")
        backend.createReply(key(), CAMILLE, thread.id, inOther.id, "x", false).refused(404, "notFound")
        backend.createReply(key(), CAMILLE, UUID(0, 0), null, "x", false).refused(404, "notFound")
        backend.thread(CAMILLE, inOther.id, null, 50).refused(404, "notFound")
        // Any writer may reply in Announcements' threads.
        val announcement = post(backend, KAEL, feed = FeedKind.ANNOUNCEMENTS)
        assertEquals(FeedKind.ANNOUNCEMENTS, backend.createReply(key(), CAMILLE, announcement.id, null, "Super", false).ok().post.feed)
    }

    @Test
    fun `deleting is for the author and moderators, again harmlessly, and takes the thread with its notifications`() {
        val thread = post(backend, KAEL, feed = FeedKind.ANNOUNCEMENTS)
        val reply = backend.createReply(key(), MIKA, thread.id, null, "Moi !", false).ok().post
        backend.createReply(key(), KAEL, thread.id, reply.id, "Oui", false).ok()
        assertEquals(3, backend.pendingNotifications().ok().items.size, "the announcement, Kael's and Mika's replies")

        backend.delete(CAMILLE, thread.id).refused(403, "notAllowed")
        backend.delete(VISITOR, thread.id).refused(404, "notLinked")
        backend.delete(CAMILLE, UUID(0, 1)).refused(404, "notFound")
        backend.delete(LUNE, thread.id).ok()
        assertTrue(backend.pendingNotifications().ok().items.isEmpty(), "a post deleted before delivery takes its rows with it")
        backend.delete(LUNE, thread.id).ok()
        backend.delete(KAEL, thread.id).ok()
        backend.delete(CAMILLE, thread.id).refused(404, "notFound")
        backend.thread(CAMILLE, thread.id, null, 50).refused(404, "notFound")
        backend.like(CAMILLE, reply.id, true).refused(404, "notFound")
        backend.markThreadSeen(CAMILLE, thread.id).refused(404, "notFound")
    }

    @Test
    fun `writing off stops posts, replies and likes, and leaves deleting and pinning`() {
        val announcement = post(backend, LUNE, feed = FeedKind.ANNOUNCEMENTS)
        backend.setWriting(false)
        assertEquals(BackendResult.Disabled("feed.writing"), backend.like(CAMILLE, announcement.id, true).join())
        assertEquals(BackendResult.Disabled("feed.writing"), backend.createReply(key(), CAMILLE, announcement.id, null, "x", false).join())
        assertFalse(backend.player(CAMILLE).ok().writing)
        backend.pin(LUNE, announcement.id, true, false).ok()
        backend.delete(LUNE, announcement.id).ok()
        assertTrue(backend.page(CAMILLE, FeedKind.GLOBAL, null, 20).join() is BackendResult.Ok, "reads work")
    }

    @Test
    fun `a guest reads everything, every viewer field false`() {
        val thread = post(backend, MIKA)
        val reply = backend.createReply(key(), CAMILLE, thread.id, null, "Moi !", false).ok().post
        backend.like(CAMILLE, thread.id, true).ok()
        for (seen in listOf(view(thread.id, VISITOR), view(reply.id, VISITOR))) {
            assertFalse(seen.liked || seen.mine || seen.canDelete || seen.canPin || seen.answersMe)
            assertEquals(0, seen.unseenReplies)
        }
        backend.like(VISITOR, thread.id, true).refused(404, "notLinked")
        backend.pin(VISITOR, thread.id, true, false).refused(404, "notLinked")
        backend.createReply(key(), VISITOR, thread.id, null, "x", false).refused(404, "notLinked")
        backend.markThreadSeen(VISITOR, thread.id).ok()
        // A UUID the server has never seen is a guest too.
        assertFalse(backend.player(UUID(0, 7)).ok().linked)
    }

    @Test
    fun `liking twice changes nothing, and unliking what isn't liked neither`() {
        val thread = post(backend, MIKA)
        assertEquals(1, backend.like(CAMILLE, thread.id, true).ok().likes)
        assertEquals(1, backend.like(CAMILLE, thread.id, true).ok().likes)
        assertEquals(0, backend.like(CAMILLE, thread.id, false).ok().likes)
        val state = backend.like(CAMILLE, thread.id, false).ok()
        assertEquals(0, state.likes)
        assertFalse(state.liked)
    }

    // ---- 4.2 and 4.3: keyset pages ----

    @Test
    fun `pages split posts of the same instant by ID, each shown once`() {
        val at = Instant.parse("2026-11-23T20:00:00Z")
        val sample = MemoryBackend(MutableClock(at), Players.directory(), rateLimits = false)
        sample.addSampleAuthor(MIKA, "MikaPvP", "Mika L.")
        val same = (1..7).map { sample.insert(MIKA, FeedKind.GLOBAL, "P$it", at) }
        val older = sample.insert(MIKA, FeedKind.GLOBAL, "old", at.minusMillis(1))
        val newer = sample.insert(MIKA, FeedKind.GLOBAL, "new", at.plusMillis(1))

        val seen = ArrayList<UUID>()
        var cursor: String? = null
        do {
            val page = sample.page(CAMILLE, FeedKind.GLOBAL, cursor, 2).ok()
            seen += page.posts.map { it.id }
            cursor = page.next
        } while (cursor != null)
        assertEquals(listOf(newer) + same.sortedDescending() + older, seen)

        val replies = (1..5).map { sample.insert(MIKA, FeedKind.GLOBAL, "R$it", at, threadId = older) }
        val inThread = ArrayList<UUID>()
        var after: String? = null
        do {
            val page = sample.thread(CAMILLE, older, after, 2).ok()
            inThread += page.replies.map { it.id }
            after = page.next
        } while (after != null)
        assertEquals(replies.sorted(), inThread)
    }

    @Test
    fun `a page that ends exactly on the last post says so`() {
        repeat(20) { post(backend, MIKA, text = "G$it") }
        assertNull(backend.page(CAMILLE, FeedKind.GLOBAL, null, 20).ok().next)
        assertEquals(19, backend.page(CAMILLE, FeedKind.GLOBAL, null, 19).ok().posts.size)
        backend.thread(CAMILLE, UUID(0, 3), "nope", 50).refused(400, null)
        backend.thread(CAMILLE, UUID(0, 3), null, 51).refused(400, null)
    }

    // ---- 7: the rate windows at their edges ----

    @Test
    fun `fifteen posts an hour, counted over the hour that slides`() {
        val limited = MemoryBackend(clock, Players.directory())
        val start = clock.now
        for (minute in 0 until 5) {
            clock.now = start.plus(Duration.ofMinutes(minute.toLong()))
            repeat(3) { post(limited, CAMILLE, text = "m$minute.$it") }
        }
        clock.now = start.plus(Duration.ofMinutes(59)).plusSeconds(59)
        assertEquals(1, limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "16", false).refused(429, "rateLimited").retryAfterSeconds)
        // Both feeds count together.
        limited.createPost(key(), LUNE, FeedKind.ANNOUNCEMENTS, "x", false).ok()
        limited.createPost(key(), CAMILLE, FeedKind.ANNOUNCEMENTS, "16", false).refused(403, "notAllowed")
        clock.now = start.plus(Duration.ofHours(1))
        post(limited, CAMILLE, text = "16")
        // The next ones come free as the first minute's posts leave the hour.
        limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "17", false).ok()
        limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "18", false).ok()
        assertEquals(60, limited.createPost(key(), CAMILLE, FeedKind.GLOBAL, "19", false).refused(429, "rateLimited").retryAfterSeconds)
    }

    @Test
    fun `replies 6 a minute and 60 an hour, likes 60 a minute, each counted apart`() {
        val limited = MemoryBackend(clock, Players.directory())
        val thread = post(limited, MIKA)
        val start = clock.now
        for (minute in 0 until 10) {
            clock.now = start.plus(Duration.ofMinutes(minute.toLong()))
            repeat(6) { limited.createReply(key(), CAMILLE, thread.id, null, "r$minute.$it", false).ok() }
            if (minute == 0) {
                assertEquals(60, limited.createReply(key(), CAMILLE, thread.id, null, "x", false).refused(429, "rateLimited").retryAfterSeconds)
            }
        }
        clock.now = start.plus(Duration.ofMinutes(30))
        val hour = limited.createReply(key(), CAMILLE, thread.id, null, "61", false).refused(429, "rateLimited")
        assertEquals(30 * 60, hour.retryAfterSeconds)
        // Posts have their own allowance.
        post(limited, CAMILLE, text = "a post")

        repeat(60) { limited.like(MIKA, thread.id, it % 2 == 0).ok() }
        clock.now = clock.now.plusSeconds(59)
        assertEquals(1, limited.like(MIKA, thread.id, true).refused(429, "rateLimited").retryAfterSeconds)
        clock.now = clock.now.plusSeconds(1)
        limited.like(MIKA, thread.id, true).ok()
    }

    // ---- 4.10: seen, capped at now ----

    @Test
    fun `a seen mark later than now is kept as now, so newer posts still light the dot`() {
        val fixed = Instant.parse("2026-11-23T20:00:00Z")
        val still = MemoryBackend(Clock.fixed(fixed, ZoneOffset.UTC), Players.directory(), rateLimits = false)
        val clockOf = MutableClock(fixed)
        val moving = MemoryBackend(clockOf, Players.directory(), rateLimits = false)
        moving.markFeedSeen(CAMILLE, FeedKind.GLOBAL, fixed.plus(Duration.ofDays(1))).ok()
        clockOf.advanceSeconds(1)
        post(moving, MIKA)
        assertTrue(moving.player(CAMILLE).ok().unread.global)

        // With a fixed clock the posts' times run ahead of it: "now" is never before what was created.
        val first = post(still, MIKA)
        still.markFeedSeen(CAMILLE, FeedKind.GLOBAL, first.createdAt).ok()
        assertFalse(still.player(CAMILLE).ok().unread.global)
        post(still, MIKA)
        assertTrue(still.player(CAMILLE).ok().unread.global)
    }

    @Test
    fun `the later of the stored and the new mark is kept`() {
        val g1 = post(backend, MIKA)
        clock.advanceSeconds(1)
        val g2 = post(backend, MIKA)
        backend.markFeedSeen(CAMILLE, FeedKind.GLOBAL, g2.createdAt).ok()
        backend.markFeedSeen(CAMILLE, FeedKind.GLOBAL, g1.createdAt).ok()
        assertFalse(backend.player(CAMILLE).ok().unread.global)
    }

    // ---- 3: markers ----

    @Test
    fun `unseen replies count on the viewer's own posts only, never their own replies`() {
        val thread = post(backend, MIKA)
        val camille = backend.createReply(key(), CAMILLE, thread.id, null, "Moi", false).ok().post
        clock.advanceSeconds(1)
        backend.createReply(key(), LUNE, thread.id, camille.id, "Toi ?", false).ok()
        backend.createReply(key(), CAMILLE, thread.id, camille.id, "Moi-même", false).ok()
        assertEquals(1, view(camille.id, CAMILLE).unseenReplies)
        assertEquals(0, view(thread.id, CAMILLE).unseenReplies, "always 0 on someone else's post")
        assertEquals(1, view(thread.id, MIKA).unseenReplies, "Camille's reply answers Mika; the replies to Camille's reply don't")
        assertEquals(0, view(camille.id, LUNE).unseenReplies)
    }
}

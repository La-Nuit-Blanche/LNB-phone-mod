package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Payloads a broken or hostile server may send, answers matched to the wrong request, events for posts the client
 * doesn't hold, and the delete's outcomes (`docs/feed-app.md` 5.3, 8).
 */
class FeedSessionPayloadsTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val global get() = session.feed(FeedKind.GLOBAL)
    private val announcements get() = session.feed(FeedKind.ANNOUNCEMENTS)

    // ---- Answers to the wrong request ----

    @Test
    fun `a result answering a load's request ID leaves the load waiting for its page`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        val load = r.last<LoadFeedPayload>()
        session.onResult(ResultPayload(load.requestId, Outcome.OK, likes = 3, liked = true))
        assertTrue(global.loadingFirst)
        val a = post()
        session.onPage(PagePayload(load.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(a), null))
        assertEquals(listOf(a.id), global.posts)
    }

    @Test
    fun `a page or a thread answering a write's request ID is dropped, and the write still waits`() {
        r.state()
        val p = post(likes = 1)
        r.firstPage(FeedKind.GLOBAL, listOf(p))
        session.toggleLike(p.id)
        val like = r.last<LikePayload>()
        session.onPage(PagePayload(like.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(post()), null))
        session.onThread(ThreadPayload(like.requestId, Outcome.OK, p, emptyList(), null))
        assertEquals(listOf(p.id), global.posts)
        assertNull(session.threadOrNull(p.id))
        session.onResult(ResultPayload(like.requestId, Outcome.OK, likes = 2, liked = true))
        assertEquals(2, session.post(p.id)!!.likes)
    }

    @Test
    fun `a thread page answering a feed's load, and a feed page answering a thread's, are dropped`() {
        val main = post()
        session.loadIfNeeded(FeedKind.GLOBAL)
        val feedLoad = r.last<LoadFeedPayload>()
        session.loadThreadIfNeeded(main.id)
        val threadLoad = r.last<LoadThreadPayload>()
        session.onThread(ThreadPayload(feedLoad.requestId, Outcome.OK, main, emptyList(), null))
        session.onPage(PagePayload(threadLoad.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(main), null))
        assertFalse(global.loaded)
        assertFalse(session.thread(main.id).loaded)
        assertTrue(global.loadingFirst)
        assertTrue(session.thread(main.id).loadingFirst)
    }

    @Test
    fun `an answer delivered twice is applied once`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        val load = r.last<LoadFeedPayload>()
        val a = post(minutesAgo = 2)
        session.onPage(PagePayload(load.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(a), null))
        session.onPage(PagePayload(load.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(post(minutesAgo = 1)), null))
        assertEquals(listOf(a.id), global.posts)
        assertTrue(global.waiting.isEmpty())
    }

    @Test
    fun `answers with IDs never asked, and answers from before logging out, are dropped`() {
        session.onResult(ResultPayload(77, Outcome.OK, post = post(mine = true)))
        session.onThread(ThreadPayload(78, Outcome.OK, post(), emptyList(), null))
        assertNull(session.compose.takeOutcome())
        session.loadIfNeeded(FeedKind.ANNOUNCEMENTS)
        val load = r.last<LoadFeedPayload>()
        session.clear()
        session.onPage(PagePayload(load.requestId, Outcome.OK, FeedKind.ANNOUNCEMENTS, emptyList(), listOf(post(FeedKind.ANNOUNCEMENTS)), null))
        assertFalse(announcements.loaded)
        // After logging out, request IDs go on rising: an old answer can't match a new request.
        session.loadIfNeeded(FeedKind.ANNOUNCEMENTS)
        assertTrue(r.last<LoadFeedPayload>().requestId > load.requestId)
    }

    // ---- Pages that don't belong ----

    @Test
    fun `a page keeps only main posts of its own feed, once each, never pinned in Global`() {
        val g = post()
        val pinnedG = post(pinnedAt = NOW)
        val other = post(FeedKind.ANNOUNCEMENTS)
        val reply = replyIn(g)
        r.firstPage(FeedKind.GLOBAL, listOf(g, other, reply, g, pinnedG), pinned = listOf(post(pinnedAt = NOW)))
        assertEquals(listOf(g.id, pinnedG.id), global.posts)
        assertTrue(global.pinned.isEmpty())
        assertNull(session.post(pinnedG.id)!!.pinnedAt)
        assertNull(session.post(other.id))
        assertNull(session.post(reply.id))
    }

    @Test
    fun `the pinned group of a page keeps announcements only`() {
        val a = post(FeedKind.ANNOUNCEMENTS)
        val pinnedA = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 50, pinnedAt = NOW)
        val pinnedG = post(FeedKind.GLOBAL, pinnedAt = NOW)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a), pinned = listOf(pinnedA, pinnedG, replyIn(a).copy(pinnedAt = NOW)))
        assertEquals(listOf(pinnedA.id), announcements.pinned)
        assertEquals(listOf(a.id), announcements.posts)
    }

    @Test
    fun `a page answered for one feed but labelled the other fills the feed asked, with that feed's posts only`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        val a = post(FeedKind.ANNOUNCEMENTS)
        val g = post(FeedKind.GLOBAL)
        session.onPage(PagePayload(r.last<LoadFeedPayload>().requestId, Outcome.OK, FeedKind.ANNOUNCEMENTS, emptyList(), listOf(a, g), null))
        assertEquals(listOf(g.id), global.posts)
        assertFalse(announcements.loaded)
    }

    @Test
    fun `a thread whose main post isn't the one asked, or is a reply, shows the error block`() {
        val main = post()
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, post(), emptyList(), null))
        assertFalse(session.thread(main.id).loaded)
        assertEquals(Outcome.UNAVAILABLE, session.thread(main.id).firstError)
        session.retryThread(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, replyIn(post()).copy(id = main.id), emptyList(), null))
        assertEquals(Outcome.UNAVAILABLE, session.thread(main.id).firstError)
        session.retryThread(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, null, emptyList(), null))
        assertEquals(Outcome.UNAVAILABLE, session.thread(main.id).firstError)
    }

    @Test
    fun `replies of another thread, in a page or live, never join this one`() {
        val main = post()
        val stranger = replyIn(post())
        val ok = replyIn(main, minutesAgo = 5)
        r.loadThread(main, listOf(stranger, ok))
        assertEquals(listOf(ok.id), session.thread(main.id).replies)
        session.loadMoreReplies(main.id) // no more pages: nothing sent
        session.onLive(listOf(replyIn(post())))
        assertEquals(listOf(ok.id), session.thread(main.id).replies)
    }

    @Test
    fun `a later page of replies drops strangers and repeats`() {
        val main = post()
        val first = replyIn(main, minutesAgo = 9)
        r.loadThread(main, listOf(first), next = "t1")
        session.loadMoreReplies(main.id)
        val second = replyIn(main, minutesAgo = 8)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, null, listOf(first, replyIn(post()), second), null))
        assertEquals(listOf(first.id, second.id), session.thread(main.id).replies)
        assertFalse(session.thread(main.id).hasMore)
    }

    // ---- Events about what isn't held ----

    @Test
    fun `counts, removals and live replies for posts not held change nothing and keep nothing`() {
        val a = post(likes = 1)
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        val unknown = FeedFixtures.id()
        session.onCounts(listOf(CountUpdate(unknown, 5, 5)))
        session.onRemoved(listOf(RemovedItem(unknown, FeedKind.GLOBAL, null), RemovedItem(FeedFixtures.id(), FeedKind.ANNOUNCEMENTS, unknown)))
        val orphan = replyIn(post(), answersMe = true)
        session.onLive(listOf(orphan))
        assertEquals(listOf(a.id), global.posts)
        assertNull(session.post(unknown))
        assertNull(session.post(orphan.id))
        assertEquals(1, session.post(a.id)!!.likes)
    }

    @Test
    fun `a live post of a feed not loaded isn't kept, but lights that tab's dot`() {
        r.state()
        session.show(FeedKind.GLOBAL)
        val a = post(FeedKind.ANNOUNCEMENTS)
        session.onLive(listOf(a))
        assertNull(session.post(a.id))
        assertTrue(session.isUnread(FeedKind.ANNOUNCEMENTS))
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a))
        assertEquals(listOf(a.id), announcements.posts)
    }

    @Test
    fun `a live post that says it is pinned waits as any other, unpinned`() {
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)))
        val sneaky = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, pinnedAt = NOW)
        session.onLive(listOf(sneaky))
        assertTrue(announcements.pinned.isEmpty())
        assertEquals(listOf(sneaky.id), announcements.waiting)
        assertNull(session.post(sneaky.id)!!.pinnedAt)
    }

    @Test
    fun `at most 200 posts wait behind the pill, the newest kept`() {
        r.firstPage(FeedKind.GLOBAL, emptyList())
        val fresh = (1..201).map { post(minutesAgo = 0).copy(createdAt = NOW.plusSeconds(it.toLong())) }
        fresh.chunked(20).forEach { session.onLive(it) }
        assertEquals(FeedSession.MAX_FEED, global.waiting.size)
        assertEquals(fresh.last().id, global.waiting.first())
        assertFalse(global.waiting.contains(fresh.first().id))
    }

    @Test
    fun `a removed reply leaves its thread, and the thread of a removed main post is marked deleted`() {
        val main = post()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        val a = replyIn(main, minutesAgo = 3)
        val b = replyIn(main, minutesAgo = 2)
        r.loadThread(main, listOf(a, b))
        session.onRemoved(listOf(RemovedItem(a.id, FeedKind.GLOBAL, main.id)))
        assertEquals(listOf(b.id), session.thread(main.id).replies)
        assertFalse(session.thread(main.id).deleted)
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null), RemovedItem(b.id, FeedKind.GLOBAL, main.id)))
        assertTrue(session.thread(main.id).deleted)
        assertNull(session.post(b.id))
        // A late live copy of a reply that went doesn't come back.
        session.onLive(listOf(b))
        assertNull(session.post(b.id))
    }

    // ---- Deleting (art direction 9.9) ----

    @Test
    fun `a delete waits for the answer, then the post goes with its thread, and says so`() {
        r.state()
        val mine = post(mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(mine))
        assertTrue(session.delete(mine.id))
        assertTrue(session.isDeleting(mine.id))
        assertFalse(session.delete(mine.id))
        assertEquals(1, r.all<DeletePayload>().size)
        assertEquals(listOf(mine.id), global.posts)
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        assertFalse(session.isDeleting(mine.id))
        assertTrue(global.posts.isEmpty())
        assertEquals(FeedNotice.Deleted(mine.id, reply = false), session.takeNotice())
    }

    @Test
    fun `deleting a reply says a reply was deleted`() {
        r.state()
        val main = post()
        val mine = replyIn(main, mine = true)
        r.loadThread(main, listOf(mine))
        session.delete(mine.id)
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        assertEquals(FeedNotice.Deleted(mine.id, reply = true), session.takeNotice())
        assertTrue(session.thread(main.id).replies.isEmpty())
    }

    @Test
    fun `deleting a reply says a reply was deleted even when the removal arrives first`() {
        r.state()
        val main = post()
        val mine = replyIn(main, mine = true)
        r.loadThread(main, listOf(mine))
        session.delete(mine.id)
        session.onRemoved(listOf(RemovedItem(mine.id, FeedKind.GLOBAL, main.id)))
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        assertEquals(FeedNotice.Deleted(mine.id, reply = true), session.takeNotice())
    }

    @Test
    fun `a delete answered NOT_FOUND drops the post without a word, other refusals keep it with one`() {
        r.state()
        val gone = post(mine = true)
        val kept = post(mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(gone, kept))
        session.delete(gone.id)
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.NOT_FOUND))
        assertEquals(listOf(kept.id), global.posts)
        assertNull(session.takeNotice())
        val cases = listOf(
            ResultPayload(0, Outcome.UNAVAILABLE) to FeedNotice.ActionFailed,
            ResultPayload(0, Outcome.DISABLED) to FeedNotice.ActionFailed,
            ResultPayload(0, Outcome.NOT_ALLOWED) to FeedNotice.ActionFailed,
            ResultPayload(0, Outcome.NOT_LINKED) to FeedNotice.Refused(WriteBlock.GUEST),
            ResultPayload(0, Outcome.RATE_LIMITED, retryAfter = 2) to FeedNotice.TooFast(2),
        )
        for ((answer, notice) in cases) {
            assertTrue(session.delete(kept.id))
            session.onResult(answer.copy(requestId = r.last<DeletePayload>().requestId))
            assertEquals(notice, session.takeNotice(), "$answer")
            assertEquals(listOf(kept.id), global.posts)
            assertFalse(session.isDeleting(kept.id))
        }
    }

    @Test
    fun `only what the viewer can delete is sent`() {
        r.state()
        val theirs = post()
        r.firstPage(FeedKind.GLOBAL, listOf(theirs))
        assertFalse(session.delete(theirs.id))
        assertFalse(session.delete(FeedFixtures.id()))
        assertTrue(r.all<DeletePayload>().isEmpty())
    }

    @Test
    fun `a pin event or a page during a delete doesn't bring the deleted post back`() {
        r.state()
        val mine = post(FeedKind.ANNOUNCEMENTS, mine = true, canPin = true)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(mine))
        session.delete(mine.id)
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        session.onPins(listOf(Pin(mine.id, NOW)))
        assertTrue(announcements.pinned.isEmpty())
        session.onReset()
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(mine))
        assertTrue(announcements.posts.isEmpty())
    }
}

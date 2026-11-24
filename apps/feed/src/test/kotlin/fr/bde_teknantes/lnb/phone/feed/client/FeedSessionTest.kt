package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.GUEST
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedSessionTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session

    @Test
    fun `a first page fills the feed, a later one appends, pinned posts stay apart`() {
        val pinned = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 3000, pinnedAt = NOW)
        val a = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)
        val b = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 20)
        r.page(FeedKind.ANNOUNCEMENTS, listOf(a, b), pinned = listOf(pinned), next = "c1")
        val feed = session.feed(FeedKind.ANNOUNCEMENTS)
        assertEquals(listOf(pinned.id), feed.pinned)
        assertEquals(listOf(a.id, b.id), feed.posts)
        assertTrue(feed.hasMore)

        val c = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 30)
        session.loadMore(FeedKind.ANNOUNCEMENTS)
        val more = r.last<LoadFeedPayload>()
        assertEquals("c1", more.before)
        // A post already held, repeated by the next page, isn't doubled.
        session.onPage(PagePayload(more.requestId, Outcome.OK, FeedKind.ANNOUNCEMENTS, emptyList(), listOf(b, c), null))
        assertEquals(listOf(a.id, b.id, c.id), feed.posts)
        assertFalse(feed.hasMore)
    }

    @Test
    fun `a page nobody asked for is dropped`() {
        session.onPage(PagePayload(42, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(post()), null))
        assertFalse(session.feed(FeedKind.GLOBAL).loaded)
    }

    @Test
    fun `a first page that fails shows the block, and only Retry loads again`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        val request = r.last<LoadFeedPayload>()
        session.onPage(PagePayload.failed(request.requestId, Outcome.DISABLED, FeedKind.GLOBAL))
        val feed = session.feed(FeedKind.GLOBAL)
        assertEquals(Outcome.DISABLED, feed.firstError)
        val loads = r.all<LoadFeedPayload>().size
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads, r.all<LoadFeedPayload>().size)

        // Retry asks whether Feed is back first; once it is, the page loads (skeletons, then the list).
        session.retryFirst(FeedKind.GLOBAL)
        assertTrue(feed.retrying)
        assertEquals(OpenPayload, r.sent.last())
        r.state()
        assertFalse(feed.retrying)
        assertTrue(feed.loadingFirst)
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
    }

    @Test
    fun `Retry while Feed is still off brings the switched-off block back`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        session.onPage(PagePayload.failed(r.last<LoadFeedPayload>().requestId, Outcome.UNAVAILABLE, FeedKind.GLOBAL))
        session.retryFirst(FeedKind.GLOBAL)
        r.state(available = Availability.DISABLED)
        val feed = session.feed(FeedKind.GLOBAL)
        assertFalse(feed.retrying)
        assertEquals(Outcome.DISABLED, feed.firstError)
    }

    @Test
    fun `a read refused as too frequent is asked again a little later, not shown as a failure`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        session.onPage(PagePayload.failed(r.last<LoadFeedPayload>().requestId, Outcome.RATE_LIMITED, FeedKind.GLOBAL))
        val feed = session.feed(FeedKind.GLOBAL)
        assertNull(feed.firstError)
        val loads = r.all<LoadFeedPayload>().size
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads, r.all<LoadFeedPayload>().size)
        repeat(FeedSession.READ_RETRY_TICKS) { session.tick() }
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
    }

    @Test
    fun `a live post waits for its place to be in view, and lights the dot of a tab not shown`() {
        r.state()
        r.page(FeedKind.GLOBAL, listOf(post(minutesAgo = 10)))
        session.show(FeedKind.ANNOUNCEMENTS)
        val fresh = post(minutesAgo = 0)
        session.onLive(listOf(fresh))
        val feed = session.feed(FeedKind.GLOBAL)
        assertEquals(listOf(fresh.id), feed.waiting)
        assertTrue(session.isUnread(FeedKind.GLOBAL))

        assertEquals(listOf(fresh.id), session.releaseWaiting(FeedKind.GLOBAL))
        assertEquals(fresh.id, feed.posts.first())
        assertTrue(feed.waiting.isEmpty())
        assertTrue(session.takeHighlight(fresh.id))
        assertFalse(session.takeHighlight(fresh.id))
    }

    @Test
    fun `a live post on the tab shown, or the player's own, lights no dot`() {
        r.page(FeedKind.GLOBAL, emptyList())
        session.show(FeedKind.GLOBAL)
        session.onLive(listOf(post()))
        assertFalse(session.isUnread(FeedKind.GLOBAL))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.onLive(listOf(post(mine = true)))
        assertFalse(session.isUnread(FeedKind.GLOBAL))
    }

    @Test
    fun `the waiting posts are counted newest first, and the same post never twice`() {
        r.page(FeedKind.GLOBAL, emptyList())
        val older = post(minutesAgo = 2)
        val newer = post(minutesAgo = 1)
        session.onLive(listOf(newer))
        session.onLive(listOf(older, newer))
        assertEquals(listOf(newer.id, older.id), session.feed(FeedKind.GLOBAL).waiting)
    }

    @Test
    fun `an own post answered before its live copy is shown once, at the top, revealed`() = ownPost(liveFirst = false)

    @Test
    fun `an own post whose live copy came first is shown once, at the top, revealed`() = ownPost(liveFirst = true)

    private fun ownPost(liveFirst: Boolean) {
        r.state()
        val other = post(minutesAgo = 5)
        r.page(FeedKind.GLOBAL, listOf(other))
        session.show(FeedKind.GLOBAL)
        session.compose.text.text = "Qui vient au Nether ?"
        assertTrue(session.sendPost())
        val request = r.last<CreatePostPayload>()
        assertEquals("Qui vient au Nether ?", request.text)
        val mine = post(minutesAgo = 0, mine = true, text = request.text)
        val live = { session.onLive(listOf(mine)) }
        if (liveFirst) live()
        session.onResult(ResultPayload(request.requestId, Outcome.OK, post = mine))
        if (!liveFirst) live()
        val feed = session.feed(FeedKind.GLOBAL)
        assertEquals(listOf(mine.id, other.id), feed.posts)
        assertTrue(feed.waiting.isEmpty())
        assertEquals(mine.id, session.takeReveal(FeedKind.GLOBAL))
        assertTrue(session.takeHighlight(mine.id))
        assertEquals("", session.compose.text.text)
        assertInstanceOf(DraftOutcome.Sent::class.java, session.compose.takeOutcome())
    }

    @Test
    fun `a draft that fails keeps its text and its key, and a changed draft gets a new key`() {
        r.state()
        session.compose.text.text = "Bonjour"
        session.sendPost()
        val first = r.last<CreatePostPayload>()
        assertTrue(session.compose.sending)
        session.onResult(ResultPayload(first.requestId, Outcome.UNAVAILABLE))
        assertFalse(session.compose.sending)
        assertEquals(DraftOutcome.SendFailed, session.compose.takeOutcome())
        assertEquals("Bonjour", session.compose.text.text)

        session.sendPost()
        val retry = r.last<CreatePostPayload>()
        assertEquals(first.key, retry.key)
        session.onResult(ResultPayload(retry.requestId, Outcome.RATE_LIMITED, retryAfter = 7))
        assertEquals(DraftOutcome.TooFast(7), session.compose.takeOutcome())

        session.compose.anonymous = true
        session.sendPost()
        assertNotEquals(first.key, r.last<CreatePostPayload>().key)
    }

    @Test
    fun `a draft over the limit or blank isn't sent`() {
        r.state()
        session.compose.text.text = "   "
        assertFalse(session.sendPost())
        session.compose.text.text = "a".repeat(281)
        assertFalse(session.sendPost())
        assertTrue(r.all<CreatePostPayload>().isEmpty())
    }

    @Test
    fun `a like shows at once and is undone when refused`() {
        r.state()
        val p = post(likes = 8)
        r.page(FeedKind.GLOBAL, listOf(p))
        assertEquals(LikeAttempt.SENT, session.toggleLike(p.id))
        assertTrue(session.post(p.id)!!.liked)
        assertEquals(9, session.post(p.id)!!.likes)
        val like = r.last<LikePayload>()
        assertTrue(like.liked)
        session.onResult(ResultPayload(like.requestId, Outcome.UNAVAILABLE))
        assertFalse(session.post(p.id)!!.liked)
        assertEquals(8, session.post(p.id)!!.likes)
        assertEquals(FeedNotice.ActionFailed, session.takeNotice())
    }

    @Test
    fun `quick like then unlike, only the last answer decides`() {
        r.state()
        val p = post(likes = 8)
        r.page(FeedKind.GLOBAL, listOf(p))
        session.toggleLike(p.id)
        val like = r.last<LikePayload>()
        session.toggleLike(p.id)
        val unlike = r.last<LikePayload>()
        assertFalse(unlike.liked)
        assertEquals(8, session.post(p.id)!!.likes)
        // The like went through, then the unlike failed: back to the liked state the server holds.
        session.onResult(ResultPayload(like.requestId, Outcome.OK, likes = 9, liked = true))
        assertFalse(session.post(p.id)!!.liked)
        session.onResult(ResultPayload(unlike.requestId, Outcome.UNAVAILABLE))
        assertTrue(session.post(p.id)!!.liked)
        assertEquals(9, session.post(p.id)!!.likes)
    }

    @Test
    fun `a guest's heart sends nothing`() {
        r.state(GUEST)
        val p = post()
        r.page(FeedKind.GLOBAL, listOf(p))
        assertEquals(WriteBlock.GUEST, session.writeBlock())
        assertEquals(LikeAttempt.BLOCKED, session.toggleLike(p.id))
        assertTrue(r.all<LikePayload>().isEmpty())
        assertFalse(session.canCompose(FeedKind.GLOBAL))
    }

    @Test
    fun `who may compose where`() {
        r.state()
        assertTrue(session.canCompose(FeedKind.GLOBAL))
        assertFalse(session.canCompose(FeedKind.ANNOUNCEMENTS))
        r.state(ANNOUNCER)
        assertTrue(session.canCompose(FeedKind.ANNOUNCEMENTS))
        r.state(ANNOUNCER.copy(writing = false))
        assertEquals(WriteBlock.WRITING_OFF, session.writeBlock())
        assertFalse(session.canCompose(FeedKind.ANNOUNCEMENTS))
        r.state(ANNOUNCER.copy(banned = true))
        assertEquals(WriteBlock.BANNED, session.writeBlock())
    }

    @Test
    fun `counts and pins apply once, everywhere, and an unpinned post returns to its date`() {
        val a = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)
        val b = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 20)
        val c = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 30, pinnedAt = NOW)
        r.page(FeedKind.ANNOUNCEMENTS, listOf(a, b), pinned = listOf(c))
        session.onCounts(listOf(CountUpdate(a.id, 5, 2)))
        assertEquals(5, session.post(a.id)!!.likes)
        assertEquals(2, session.post(a.id)!!.replies)

        session.onPins(listOf(Pin(b.id, NOW.plusSeconds(1))))
        val feed = session.feed(FeedKind.ANNOUNCEMENTS)
        assertEquals(listOf(b.id), feed.pinned)
        assertEquals(listOf(a.id, c.id), feed.posts)
        assertNull(session.post(c.id)!!.pinnedAt)
    }

    @Test
    fun `a pin nobody held marks Announcements stale, to be reloaded`() {
        r.page(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS)))
        session.onPins(listOf(Pin(FeedFixtures.id(), NOW)))
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).stale)
    }

    @Test
    fun `removed posts leave the feeds and never come back late`() {
        val p = post()
        r.page(FeedKind.GLOBAL, listOf(p))
        session.onRemoved(listOf(RemovedItem(p.id, FeedKind.GLOBAL, null)))
        assertTrue(session.feed(FeedKind.GLOBAL).posts.isEmpty())
        assertNull(session.post(p.id))
        session.onLive(listOf(p))
        assertTrue(session.feed(FeedKind.GLOBAL).waiting.isEmpty())
    }

    @Test
    fun `a removed main post marks its thread deleted`() {
        val main = post()
        r.page(FeedKind.GLOBAL, listOf(main))
        session.loadThreadIfNeeded(main.id)
        val request = r.last<LoadThreadPayload>()
        session.onThread(ThreadPayload(request.requestId, Outcome.OK, main, listOf(reply(main)), null))
        assertTrue(session.thread(main.id).loaded)
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        assertTrue(session.thread(main.id).deleted)
    }

    @Test
    fun `a live reply raises the marker on the player's post only, and not while the thread is read`() {
        r.state()
        val mine = post(mine = true)
        val theirs = post()
        r.page(FeedKind.GLOBAL, listOf(mine, theirs))
        val answer = reply(mine, answersMe = true)
        session.onLive(listOf(answer))
        assertEquals(1, session.post(mine.id)!!.unseenReplies)
        // Delivered twice, counted once.
        session.onLive(listOf(answer))
        assertEquals(1, session.post(mine.id)!!.unseenReplies)
        session.onLive(listOf(reply(theirs, answersMe = false)))
        assertEquals(0, session.post(theirs.id)!!.unseenReplies)
        // The player's own reply raises nothing.
        session.onLive(listOf(reply(mine, mine = true, answersMe = false)))
        assertEquals(1, session.post(mine.id)!!.unseenReplies)

        // Reading the thread: marked seen at once, markers cleared.
        session.loadThreadIfNeeded(mine.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, session.post(mine.id), emptyList(), null))
        session.showThread(mine.id)
        assertEquals(mine.id, r.last<SeenThreadPayload>().threadId)
        assertEquals(0, session.post(mine.id)!!.unseenReplies)
        val seen = r.all<SeenThreadPayload>().size
        session.onLive(listOf(reply(mine, answersMe = true)))
        assertEquals(0, session.post(mine.id)!!.unseenReplies)
        // Marked seen again, 2 seconds after the last mark.
        repeat(FeedSession.SEEN_EVERY_TICKS) { session.tick() }
        assertEquals(seen + 1, r.all<SeenThreadPayload>().size)
    }

    @Test
    fun `reset merges, newer posts wait, posts gone meanwhile go, the rest updates in place`() {
        val kept = post(minutesAgo = 10, likes = 1)
        val deleted = post(minutesAgo = 20)
        val old = post(minutesAgo = 30)
        r.page(FeedKind.GLOBAL, listOf(kept, deleted, old))
        session.show(FeedKind.GLOBAL)
        session.onReset()
        val feed = session.feed(FeedKind.GLOBAL)
        assertTrue(feed.stale)
        val newer = post(minutesAgo = 1)
        r.page(FeedKind.GLOBAL, listOf(newer, kept.copy(likes = 4), old))
        assertFalse(feed.stale)
        assertEquals(listOf(kept.id, old.id), feed.posts)
        assertEquals(listOf(newer.id), feed.waiting)
        assertEquals(4, session.post(kept.id)!!.likes)
        assertNull(session.post(deleted.id))
    }

    @Test
    fun `reset keeps the older pages a merge doesn't cover`() {
        val first = (1..3).map { post(minutesAgo = it.toLong()) }
        r.page(FeedKind.GLOBAL, first, next = "c1")
        session.loadMore(FeedKind.GLOBAL)
        val older = post(minutesAgo = 100)
        session.onPage(PagePayload(r.last<LoadFeedPayload>().requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(older), "c2"))
        session.onReset()
        r.page(FeedKind.GLOBAL, first, next = "c1b")
        val feed = session.feed(FeedKind.GLOBAL)
        assertEquals(first.map { it.id } + older.id, feed.posts)
        session.loadMore(FeedKind.GLOBAL)
        assertEquals("c2", r.last<LoadFeedPayload>().before)
    }

    @Test
    fun `a list not looked at keeps its newest 200 posts and loads the rest again`() {
        val posts = (1..230).map { post(minutesAgo = it.toLong()) }
        r.page(FeedKind.GLOBAL, posts.take(50), next = "c1")
        var before = "c1"
        for (page in 1..4) {
            session.loadMore(FeedKind.GLOBAL)
            val request = r.last<LoadFeedPayload>()
            assertEquals(before, request.before)
            before = "c${page + 1}"
            session.onPage(PagePayload(request.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), posts.drop(50 * page).take(50), before))
        }
        val feed = session.feed(FeedKind.GLOBAL)
        session.show(FeedKind.GLOBAL)
        assertEquals(230, feed.posts.size)
        session.show(FeedKind.ANNOUNCEMENTS)
        assertEquals(FeedSession.MAX_FEED, feed.posts.size)
        assertNull(session.post(posts.last().id))
        assertTrue(feed.hasMore)
        // The page the oldest kept post came with is asked again; what is held is skipped.
        session.loadMore(FeedKind.GLOBAL)
        assertEquals("c3", r.last<LoadFeedPayload>().before)
    }

    @Test
    fun `the connection lost over a loaded list is said once, retried every 15 s, and cleared when back`() {
        r.state()
        r.page(FeedKind.GLOBAL, listOf(post()))
        session.show(FeedKind.GLOBAL)
        r.state(available = Availability.UNAVAILABLE)
        assertEquals(FeedNotice.ConnectionLost, session.takeNotice())
        val opens = r.sent.count { it == OpenPayload }
        repeat(FeedSession.AUTO_RETRY_TICKS) { session.tick() }
        assertEquals(opens + 1, r.sent.count { it == OpenPayload })
        // An automatic retry that fails says nothing; Retry pressed that fails says it again.
        r.state(available = Availability.UNAVAILABLE)
        assertNull(session.takeNotice())
        session.retryConnection()
        r.state(available = Availability.UNAVAILABLE)
        assertEquals(FeedNotice.ConnectionLost, session.takeNotice())
        r.state()
        assertEquals(FeedNotice.ConnectionBack, session.takeNotice())
    }

    @Test
    fun `nothing loaded, an unavailable state says nothing, the block will`() {
        session.open()
        r.state(available = Availability.UNAVAILABLE)
        assertNull(session.takeNotice())
        assertEquals(FeedAvailability.UNAVAILABLE, session.availability)
    }

    @Test
    fun `viewing a tab clears its dot and tells the newest post shown, when it changes`() {
        r.state(FeedFixtures.PLAYER.copy(unread = Unread(announcements = false, global = true)))
        assertTrue(session.isUnread(FeedKind.GLOBAL))
        val p = post(minutesAgo = 3)
        r.page(FeedKind.GLOBAL, listOf(p))
        session.show(FeedKind.GLOBAL)
        session.markViewed(FeedKind.GLOBAL)
        assertFalse(session.isUnread(FeedKind.GLOBAL))
        assertEquals(p.createdAt, r.last<SeenFeedPayload>().upTo)
        repeat(100) {
            session.tick()
            session.markViewed(FeedKind.GLOBAL)
        }
        assertEquals(1, r.all<SeenFeedPayload>().size)
    }

    @Test
    fun `a notification's announcement is brought out of the waiting posts and highlighted`() {
        r.page(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)))
        val fresh = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0)
        session.onLive(listOf(fresh))
        session.land(fresh.id)
        assertEquals(fresh.id, session.takeLanding(FeedKind.ANNOUNCEMENTS))
        assertEquals(fresh.id, session.feed(FeedKind.ANNOUNCEMENTS).posts.first())
        assertTrue(session.wantsHighlight(fresh.id))
        assertNull(session.takeLanding(FeedKind.ANNOUNCEMENTS))
    }

    @Test
    fun `clear forgets everything, and late answers are dropped`() {
        r.state()
        session.loadIfNeeded(FeedKind.GLOBAL)
        val request = r.last<LoadFeedPayload>()
        session.compose.text.text = "brouillon"
        session.clear()
        session.onPage(PagePayload(request.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(post()), null))
        assertFalse(session.feed(FeedKind.GLOBAL).loaded)
        assertNull(session.state)
        assertEquals("", session.compose.text.text)
    }
}

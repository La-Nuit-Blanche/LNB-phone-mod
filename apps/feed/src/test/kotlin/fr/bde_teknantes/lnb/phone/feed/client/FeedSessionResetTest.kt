package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The merge after `reset` (`docs/feed-app.md` 8): the first page reloaded over what is held, so the list catches up
 * without jumping. Pages here are cut small on purpose, as the site's keyset cursors cut them.
 */
class FeedSessionResetTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val global get() = session.feed(FeedKind.GLOBAL)
    private val announcements get() = session.feed(FeedKind.ANNOUNCEMENTS)

    @Test
    fun `reset marks every loaded feed and thread stale, and only those`() {
        val main = post()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, emptyList())
        session.onReset()
        assertTrue(global.stale)
        assertTrue(session.thread(main.id).stale)
        // Never loaded: nothing to merge, it loads as usual when shown.
        assertFalse(announcements.stale)
    }

    @Test
    fun `a page that is the whole feed drops every held post it lacks, older pages included`() {
        val a = post(minutesAgo = 1)
        val b = post(minutesAgo = 2)
        val c = post(minutesAgo = 3)
        r.firstPage(FeedKind.GLOBAL, listOf(a, b), next = "c1")
        r.nextPage(FeedKind.GLOBAL, listOf(c))
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        assertEquals(listOf(a.id), global.posts)
        assertFalse(global.hasMore)
        assertNull(session.post(b.id))
        assertNull(session.post(c.id))
    }

    @Test
    fun `a held post the page skipped within its span went meanwhile, and never comes back late`() {
        val a = post(minutesAgo = 1)
        val b = post(minutesAgo = 2)
        val c = post(minutesAgo = 3)
        r.firstPage(FeedKind.GLOBAL, listOf(a, b, c), next = "c1")
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, listOf(a, c), next = "c1b")
        assertEquals(listOf(a.id, c.id), global.posts)
        // A `live` or a later page still carrying it doesn't bring it back.
        session.onLive(listOf(b))
        assertTrue(global.waiting.isEmpty())
        r.nextPage(FeedKind.GLOBAL, listOf(b))
        assertEquals(listOf(a.id, c.id), global.posts)
    }

    @Test
    fun `a held post at the page's oldest time, cut onto the next page, is not taken for deleted`() {
        // Four posts of the same millisecond, as the memory backend's fixed clock makes them.
        val same = (1..4).map { post(minutesAgo = 5) }
        r.firstPage(FeedKind.GLOBAL, same.take(2), next = "c1")
        r.nextPage(FeedKind.GLOBAL, same.drop(2))
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, same.take(2), next = "c1b")
        // The other two are on the next page: still held, or at least brought back by that page.
        if (global.posts.size < 4) r.nextPage(FeedKind.GLOBAL, same.drop(2))
        assertEquals(same.map { it.id }, global.posts)
    }

    @Test
    fun `waiting posts deleted meanwhile leave the pill's count`() {
        val a = post(minutesAgo = 10)
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        val fresh = post(minutesAgo = 1)
        session.onLive(listOf(fresh))
        assertEquals(listOf(fresh.id), global.waiting)
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        assertTrue(global.waiting.isEmpty())
        assertNull(session.post(fresh.id))
    }

    @Test
    fun `posts newer than the newest held arrive as live ones do, and light the dot of a tab not shown`() {
        r.state()
        val a = post(minutesAgo = 10)
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.onReset()
        val theirs = post(minutesAgo = 2)
        val mine = post(minutesAgo = 1, mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(mine, theirs, a))
        assertEquals(listOf(a.id), global.posts)
        assertEquals(listOf(mine.id, theirs.id), global.waiting)
        assertTrue(session.isUnread(FeedKind.GLOBAL))
    }

    @Test
    fun `a reset page with only the player's own new post lights no dot`() {
        r.state()
        val a = post(minutesAgo = 10)
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, listOf(post(minutesAgo = 1, mine = true), a))
        assertFalse(session.isUnread(FeedKind.GLOBAL))
    }

    @Test
    fun `a post missed while away but older than the newest held goes in at its date, not behind the pill`() {
        val a = post(minutesAgo = 1)
        val c = post(minutesAgo = 30)
        r.firstPage(FeedKind.GLOBAL, listOf(a, c))
        session.onReset()
        val b = post(minutesAgo = 10)
        r.firstPage(FeedKind.GLOBAL, listOf(a, b, c))
        assertEquals(listOf(a.id, b.id, c.id), global.posts)
        assertTrue(global.waiting.isEmpty())
    }

    @Test
    fun `held posts are updated in place, the order kept`() {
        val a = post(minutesAgo = 1, likes = 1)
        val b = post(minutesAgo = 2, replies = 0)
        r.firstPage(FeedKind.GLOBAL, listOf(a, b))
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, listOf(a.copy(likes = 7, liked = true), b.copy(replies = 3, unseenReplies = 2)))
        assertEquals(listOf(a.id, b.id), global.posts)
        assertEquals(7, session.post(a.id)!!.likes)
        assertTrue(session.post(a.id)!!.liked)
        assertEquals(2, session.post(b.id)!!.unseenReplies)
    }

    @Test
    fun `the cursor follows the page when the held list ends within it, and the held one otherwise`() {
        val first = (1..3).map { post(minutesAgo = it.toLong()) }
        r.firstPage(FeedKind.GLOBAL, first, next = "c1")
        session.onReset()
        // The page reaches as far as what is held: its own cursor goes on from there.
        r.firstPage(FeedKind.GLOBAL, first, next = "c1b")
        r.sendsLoad(FeedKind.GLOBAL) { session.loadMore(FeedKind.GLOBAL) }.also { assertEquals("c1b", it.before) }
    }

    @Test
    fun `a whole feed held, then more posts than a page while away - nothing older is lost`() {
        val held = (10..14).map { post(minutesAgo = it.toLong()) }
        r.firstPage(FeedKind.GLOBAL, held)
        assertFalse(global.hasMore)
        session.onReset()
        val fresh = (1..3).map { post(minutesAgo = it.toLong()) }
        // The page now has 3 new posts and the first 2 held, and a cursor.
        r.firstPage(FeedKind.GLOBAL, fresh + held.take(2), next = "c1")
        assertEquals(held.map { it.id }, global.posts)
        assertEquals(fresh.map { it.id }, global.waiting)
        // What is held already reaches the end of the feed.
        assertFalse(global.hasMore)
    }

    @Test
    fun `the reset page's pinned group replaces the held one, a post unpinned meanwhile back at its date`() {
        val p = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 15, pinnedAt = NOW)
        val a = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)
        val b = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 20)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a, b), pinned = listOf(p))
        session.onReset()
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a, p.copy(pinnedAt = null), b))
        assertTrue(announcements.pinned.isEmpty())
        assertEquals(listOf(a.id, p.id, b.id), announcements.posts)
        assertNull(session.post(p.id)!!.pinnedAt)
    }

    @Test
    fun `a post pinned meanwhile moves into the group and isn't taken for deleted`() {
        val a = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)
        val b = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 20)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a, b))
        session.onReset()
        // `posts` never repeats a pinned post (feed-mod.md 4.3).
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a), pinned = listOf(b.copy(pinnedAt = NOW)))
        assertEquals(listOf(b.id), announcements.pinned)
        assertEquals(listOf(a.id), announcements.posts)
        assertEquals(NOW, session.post(b.id)!!.pinnedAt)
    }

    @Test
    fun `a reset landing while the first page loads leaves one copy of each post`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        val request = r.last<LoadFeedPayload>()
        session.onReset()
        val a = post()
        session.onPage(PagePayload(request.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(a, a), null))
        assertTrue(global.loaded)
        assertEquals(listOf(a.id), global.posts)
    }

    @Test
    fun `a first page asked before a reset is shown but stays stale, and loads again`() {
        val before = r.sendsLoad(FeedKind.GLOBAL) { session.loadIfNeeded(FeedKind.GLOBAL) }
        session.onReset()
        val old = post(minutesAgo = 5)
        session.onPage(PagePayload(before.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(old), null))
        assertEquals(listOf(old.id), global.posts)
        assertTrue(global.stale)
        // Loaded again, after the reset: now current.
        val fresh = post(minutesAgo = 1)
        r.firstPage(FeedKind.GLOBAL, listOf(fresh, old))
        assertFalse(global.stale)
        assertEquals(listOf(fresh.id), global.waiting)
    }

    @Test
    fun `a thread asked before a reset stays stale`() {
        val main = post()
        session.loadThreadIfNeeded(main.id)
        val before = r.last<LoadThreadPayload>()
        session.onReset()
        session.onThread(ThreadPayload(before.requestId, Outcome.OK, main, emptyList(), null))
        assertTrue(session.thread(main.id).loaded)
        assertTrue(session.thread(main.id).stale)
    }

    @Test
    fun `a later page in flight across a reset is appended, whichever answer comes first`() {
        for (moreFirst in listOf(true, false)) {
            val rec = FeedFixtures.Recorder()
            val a = post(minutesAgo = 1)
            val b = post(minutesAgo = 2)
            val c = post(minutesAgo = 3)
            rec.firstPage(FeedKind.GLOBAL, listOf(a, b), next = "c1")
            val more = rec.sendsLoad(FeedKind.GLOBAL) { rec.session.loadMore(FeedKind.GLOBAL) }
            rec.session.onReset()
            val reload = rec.sendsLoad(FeedKind.GLOBAL) { rec.session.loadIfNeeded(FeedKind.GLOBAL) }
            val answerMore = { rec.session.onPage(PagePayload(more.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(c), null)) }
            val answerReload = { rec.session.onPage(PagePayload(reload.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(a, b), "c1b")) }
            if (moreFirst) answerMore() else answerReload()
            if (moreFirst) answerReload() else answerMore()
            val feed = rec.session.feed(FeedKind.GLOBAL)
            assertEquals(listOf(a.id, b.id, c.id), feed.posts, "more first: $moreFirst")
            assertFalse(feed.hasMore, "more first: $moreFirst")
            assertFalse(feed.stale)
        }
    }

    @Test
    fun `a reload that fails keeps the list readable, says the connection went, and tries again later`() {
        val a = post()
        r.firstPage(FeedKind.GLOBAL, listOf(a))
        session.onReset()
        val reload = r.sendsLoad(FeedKind.GLOBAL) { session.loadIfNeeded(FeedKind.GLOBAL) }
        session.onPage(PagePayload.failed(reload.requestId, Outcome.UNAVAILABLE, FeedKind.GLOBAL))
        assertEquals(listOf(a.id), global.posts)
        assertNull(global.firstError)
        assertEquals(FeedNotice.ConnectionLost, session.takeNotice())
        val loads = r.all<LoadFeedPayload>().size
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads, r.all<LoadFeedPayload>().size)
        repeat(FeedSession.AUTO_RETRY_TICKS) { session.tick() }
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
    }

    @Test
    fun `a reload failing again says nothing more, and Feed back says so once`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        session.show(FeedKind.GLOBAL)
        session.onReset()
        repeat(3) {
            val reload = r.sendsLoad(FeedKind.GLOBAL) { session.loadIfNeeded(FeedKind.GLOBAL) }
            session.onPage(PagePayload.failed(reload.requestId, Outcome.UNAVAILABLE, FeedKind.GLOBAL))
            if (it == 0) assertEquals(FeedNotice.ConnectionLost, session.takeNotice()) else assertNull(session.takeNotice())
            repeat(FeedSession.AUTO_RETRY_TICKS) { session.tick() }
        }
        // The 15-second open went out meanwhile; its answer brings Feed back.
        assertTrue(r.sent.count { it == fr.bde_teknantes.lnb.phone.feed.network.OpenPayload } >= 2)
        r.state()
        assertEquals(FeedNotice.ConnectionBack, session.takeNotice())
    }

    @Test
    fun `a reload refused as too frequent tries again after 2 seconds, without a snackbar`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        session.onReset()
        val reload = r.sendsLoad(FeedKind.GLOBAL) { session.loadIfNeeded(FeedKind.GLOBAL) }
        session.onPage(PagePayload.failed(reload.requestId, Outcome.RATE_LIMITED, FeedKind.GLOBAL))
        assertNull(session.takeNotice())
        repeat(FeedSession.READ_RETRY_TICKS - 1) { session.tick() }
        val loads = r.all<LoadFeedPayload>().size
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads, r.all<LoadFeedPayload>().size)
        session.tick()
        session.loadIfNeeded(FeedKind.GLOBAL)
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
    }

    // ---- Threads ----

    @Test
    fun `a thread reloaded after a reset drops replies gone, keeps the order, and adds new ones at their date`() {
        val main = post()
        val r1 = replyIn(main, minutesAgo = 30)
        val r2 = replyIn(main, minutesAgo = 20)
        val r3 = replyIn(main, minutesAgo = 10)
        r.loadThread(main, listOf(r1, r2, r3))
        session.onReset()
        val missed = replyIn(main, minutesAgo = 15)
        val r4 = replyIn(main, minutesAgo = 1)
        r.loadThread(main, listOf(r1, missed, r3, r4))
        assertEquals(listOf(r1.id, missed.id, r3.id, r4.id), session.thread(main.id).replies)
        assertNull(session.post(r2.id))
    }

    @Test
    fun `a thread reloaded keeps the later replies its first page doesn't cover`() {
        val main = post()
        val replies = (1..4).map { replyIn(main, minutesAgo = 50L - it) }
        r.loadThread(main, replies.take(2), next = "t1")
        session.loadMoreReplies(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, null, replies.drop(2), null))
        session.onReset()
        r.loadThread(main, replies.take(2), next = "t1b")
        assertEquals(replies.map { it.id }, session.thread(main.id).replies)
        // Replies made meanwhile come after those: the thread goes on from the page, skipping what it holds.
        assertTrue(session.thread(main.id).hasMore)
        val made = replyIn(main, minutesAgo = 1)
        session.loadMoreReplies(main.id)
        val more = r.last<LoadThreadPayload>()
        assertEquals("t1b", more.after)
        session.onThread(ThreadPayload(more.requestId, Outcome.OK, null, replies.drop(2) + made, null))
        assertEquals(replies.map { it.id } + made.id, session.thread(main.id).replies)
        assertFalse(session.thread(main.id).hasMore)
    }

    @Test
    fun `a whole thread held, longer than a page, gets the replies made meanwhile`() {
        val main = post()
        val replies = (1..4).map { replyIn(main, minutesAgo = 50L - it) }
        r.loadThread(main, replies)
        assertFalse(session.thread(main.id).hasMore)
        session.onReset()
        // Two replies made while away; the reloaded page holds only the first two.
        r.loadThread(main, replies.take(2), next = "t1")
        val made = (1..2).map { replyIn(main, minutesAgo = 3L - it) }
        session.loadMoreReplies(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, null, replies.drop(2) + made, null))
        assertEquals((replies + made).map { it.id }, session.thread(main.id).replies)
    }

    @Test
    fun `more than a page posted while away - its posts wait behind the pill, then start the list again, nothing between lost`() {
        val held = (20..24).map { post(minutesAgo = it.toLong()) }
        r.firstPage(FeedKind.GLOBAL, held, next = "c1")
        session.onReset()
        // A page of posts all newer than what is held, and more behind it.
        val fresh = (1..3).map { post(minutesAgo = it.toLong()) }
        r.firstPage(FeedKind.GLOBAL, fresh, next = "c9")
        // The reader keeps what they read; the new posts wait behind the pill, with the dot, as live posts do.
        assertEquals(held.map { it.id }, global.posts)
        assertEquals(fresh.map { it.id }, global.waiting)
        assertTrue(session.isUnread(FeedKind.GLOBAL))
        // Let in (the pill, or their place scrolled into view): the list starts again from them, highlighted.
        session.releaseWaiting(FeedKind.GLOBAL)
        assertEquals(fresh.map { it.id }, global.posts)
        assertTrue(global.waiting.isEmpty())
        assertTrue(fresh.all { session.wantsHighlight(it.id) })
        // The posts between come with the next page, and the old ones after them, in order.
        val between = (10..11).map { post(minutesAgo = it.toLong()) }
        session.loadMore(FeedKind.GLOBAL)
        val more = r.last<LoadFeedPayload>()
        assertEquals("c9", more.before)
        session.onPage(PagePayload(more.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), between + held, null))
        assertEquals((fresh + between + held).map { it.id }, global.posts)
    }

    @Test
    fun `a held reply at the page's newest time, cut onto the next page, is not taken for deleted`() {
        val main = post()
        val same = (1..4).map { replyIn(main, minutesAgo = 3) }
        r.loadThread(main, same.take(2), next = "t1")
        session.loadMoreReplies(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, null, same.drop(2), null))
        session.onReset()
        r.loadThread(main, same.take(2), next = "t1b")
        assertEquals(same.map { it.id }, session.thread(main.id).replies)
    }

    @Test
    fun `a thread whose main post went meanwhile answers NOT_FOUND on reload and is left`() {
        val main = post()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, emptyList())
        session.onReset()
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.NOT_FOUND))
        assertTrue(session.thread(main.id).deleted)
        assertTrue(global.posts.isEmpty())
        // No reload of a thread known to be deleted.
        val loads = r.all<LoadThreadPayload>().size
        session.loadThreadIfNeeded(main.id)
        assertEquals(loads, r.all<LoadThreadPayload>().size)
        assertNotNull(session.threadOrNull(main.id))
    }
}

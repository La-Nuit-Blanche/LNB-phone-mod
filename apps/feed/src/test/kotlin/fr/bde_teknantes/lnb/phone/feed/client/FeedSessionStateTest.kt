package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.GUEST
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.PLAYER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The player's state and availability, the tab dots and `seen_feed`, the unseen-replies markers and `seen_thread`
 * (`docs/feed-app.md` 8, `feed-mod.md` 3), the paging failures, the bounds, and clearing at logout.
 */
class FeedSessionStateTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val global get() = session.feed(FeedKind.GLOBAL)

    private fun opens() = r.sent.count { it == OpenPayload }

    // ---- Availability ----

    @Test
    fun `open asks for the state, and the first answer sets availability, rights and the player`() {
        assertEquals(FeedAvailability.UNKNOWN, session.availability)
        session.open()
        assertEquals(FeedAvailability.LOADING, session.availability)
        r.state(ANNOUNCER)
        assertEquals(FeedAvailability.OK, session.availability)
        assertTrue(session.canCompose(FeedKind.ANNOUNCEMENTS))
        assertNull(session.writeBlock())
    }

    @Test
    fun `nothing may be written before the state is known`() {
        assertNull(session.writeBlock())
        assertFalse(session.canCompose(FeedKind.GLOBAL))
        assertFalse(session.canCompose(FeedKind.ANNOUNCEMENTS))
    }

    @Test
    fun `a state without the player's state keeps the last one known`() {
        r.state(ANNOUNCER)
        session.onState(StatePayload(Availability.UNAVAILABLE, null))
        assertEquals(ANNOUNCER, session.state)
        assertEquals(FeedAvailability.UNAVAILABLE, session.availability)
    }

    @Test
    fun `the block's Retry gives up waiting after 15 seconds without an answer`() {
        session.loadIfNeeded(FeedKind.GLOBAL)
        session.onPage(PagePayload.failed(r.last<LoadFeedPayload>().requestId, Outcome.UNAVAILABLE, FeedKind.GLOBAL))
        session.retryFirst(FeedKind.GLOBAL)
        assertTrue(global.retrying)
        // A second click while it waits sends nothing more.
        val o = opens()
        session.retryFirst(FeedKind.GLOBAL)
        assertEquals(o, opens())
        repeat(FeedSession.RETRY_TIMEOUT_TICKS - 1) { session.tick() }
        assertTrue(global.retrying)
        session.tick()
        assertFalse(global.retrying)
        assertEquals(Outcome.UNAVAILABLE, global.firstError)
    }

    @Test
    fun `Feed back loads the tab whose Retry was clicked, and lifts the other tab's block to load when shown`() {
        for (kind in FeedKind.entries) {
            session.loadIfNeeded(kind)
            session.onPage(PagePayload.failed(r.last<LoadFeedPayload>().requestId, Outcome.DISABLED, kind))
        }
        session.retryFirst(FeedKind.GLOBAL)
        r.state()
        assertTrue(global.loadingFirst)
        val announcements = session.feed(FeedKind.ANNOUNCEMENTS)
        assertNull(announcements.firstError)
        assertFalse(announcements.loadingFirst)
        session.loadIfNeeded(FeedKind.ANNOUNCEMENTS)
        assertTrue(announcements.loadingFirst)
    }

    @Test
    fun `while the connection is lost, open goes again every 15 seconds only while a list is shown`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        session.show(FeedKind.GLOBAL)
        r.state(available = Availability.DISABLED)
        assertEquals(FeedNotice.ConnectionLost, session.takeNotice())
        val o = opens()
        repeat(FeedSession.AUTO_RETRY_TICKS) { session.tick() }
        assertEquals(o + 1, opens())
        // Feed put away (no screen shown): no more retries.
        session.show(null)
        repeat(FeedSession.AUTO_RETRY_TICKS * 3) { session.tick() }
        assertEquals(o + 1, opens())
    }

    @Test
    fun `the reset after a reconnection clears the snackbar like an OK state`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        r.state(available = Availability.UNAVAILABLE)
        session.takeNotice()
        session.onReset()
        assertEquals(FeedNotice.ConnectionBack, session.takeNotice())
    }

    @Test
    fun `Retry while the connection is lost sends open at once and puts off the automatic one`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        session.show(FeedKind.GLOBAL)
        r.state(available = Availability.UNAVAILABLE)
        repeat(100) { session.tick() }
        val o = opens()
        session.retryConnection()
        assertEquals(o + 1, opens())
        repeat(FeedSession.AUTO_RETRY_TICKS - 1) { session.tick() }
        assertEquals(o + 1, opens())
        session.tick()
        assertEquals(o + 2, opens())
    }

    // ---- Paging failures ----

    @Test
    fun `a later page that fails turns into the retry row, and Retry asks the same page again`() {
        r.firstPage(FeedKind.GLOBAL, listOf(post()), next = "c1")
        val more = r.sendsLoad(FeedKind.GLOBAL) { session.loadMore(FeedKind.GLOBAL) }
        session.onPage(PagePayload.failed(more.requestId, Outcome.UNAVAILABLE, FeedKind.GLOBAL))
        assertTrue(global.moreFailed)
        assertFalse(global.loadingMore)
        // The spinner row no longer asks by itself.
        val loads = r.all<LoadFeedPayload>().size
        session.loadMore(FeedKind.GLOBAL)
        assertEquals(loads, r.all<LoadFeedPayload>().size)
        val again = r.sendsLoad(FeedKind.GLOBAL) { session.retryMore(FeedKind.GLOBAL) }
        assertEquals("c1", again.before)
        assertFalse(global.moreFailed)
        assertTrue(global.loadingMore)
    }

    @Test
    fun `a later page refused as too frequent keeps the spinner row and asks again after 2 seconds`() {
        r.firstPage(FeedKind.GLOBAL, listOf(post()), next = "c1")
        val more = r.sendsLoad(FeedKind.GLOBAL) { session.loadMore(FeedKind.GLOBAL) }
        session.onPage(PagePayload.failed(more.requestId, Outcome.RATE_LIMITED, FeedKind.GLOBAL))
        assertFalse(global.moreFailed)
        val loads = r.all<LoadFeedPayload>().size
        repeat(FeedSession.READ_RETRY_TICKS - 1) {
            session.tick()
            session.loadMore(FeedKind.GLOBAL)
        }
        assertEquals(loads, r.all<LoadFeedPayload>().size)
        session.tick()
        assertEquals("c1", r.sendsLoad(FeedKind.GLOBAL) { session.loadMore(FeedKind.GLOBAL) }.before)
    }

    @Test
    fun `later replies that fail turn into the retry row, and a thread's first page refused as too frequent waits`() {
        val main = post()
        r.loadThread(main, listOf(replyIn(main)), next = "t1")
        session.loadMoreReplies(main.id)
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.UNAVAILABLE))
        assertTrue(session.thread(main.id).moreFailed)
        session.retryReplies(main.id)
        assertEquals("t1", r.last<LoadThreadPayload>().after)

        val other = post()
        session.loadThreadIfNeeded(other.id)
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.RATE_LIMITED))
        assertNull(session.thread(other.id).firstError)
        val loads = r.all<LoadThreadPayload>().size
        session.loadThreadIfNeeded(other.id)
        assertEquals(loads, r.all<LoadThreadPayload>().size)
        repeat(FeedSession.READ_RETRY_TICKS) { session.tick() }
        session.loadThreadIfNeeded(other.id)
        assertEquals(loads + 1, r.all<LoadThreadPayload>().size)
    }

    @Test
    fun `a thread that can't load shows its block, switched off with its own outcome`() {
        val main = post()
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.DISABLED))
        assertEquals(Outcome.DISABLED, session.thread(main.id).firstError)
        val loads = r.all<LoadThreadPayload>().size
        session.loadThreadIfNeeded(main.id)
        assertEquals(loads, r.all<LoadThreadPayload>().size)
    }

    // ---- Tab dots (art direction 9.6) ----

    @Test
    fun `the state's dots light the tabs not viewed, the tab viewed never`() {
        session.show(FeedKind.ANNOUNCEMENTS)
        r.state(PLAYER.copy(unread = Unread(announcements = true, global = true)))
        assertFalse(session.isUnread(FeedKind.ANNOUNCEMENTS))
        assertTrue(session.isUnread(FeedKind.GLOBAL))
        // A later state without the dots puts them out.
        r.state(PLAYER)
        assertFalse(session.isUnread(FeedKind.GLOBAL))
    }

    @Test
    fun `a new announcement while on Global lights the Annonces dot, viewing Annonces puts it out`() {
        r.state()
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)))
        session.show(FeedKind.GLOBAL)
        session.onLive(listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0)))
        assertTrue(session.isUnread(FeedKind.ANNOUNCEMENTS))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.markViewed(FeedKind.ANNOUNCEMENTS)
        assertFalse(session.isUnread(FeedKind.ANNOUNCEMENTS))
    }

    @Test
    fun `a live reply lights no dot`() {
        r.state()
        val main = post()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.onLive(listOf(replyIn(main)))
        assertFalse(session.isUnread(FeedKind.GLOBAL))
    }

    @Test
    fun `a live post while Feed shows nothing lights its tab's dot`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post(minutesAgo = 5)))
        session.show(FeedKind.GLOBAL)
        session.show(null)
        session.onLive(listOf(post(minutesAgo = 0)))
        assertTrue(session.isUnread(FeedKind.GLOBAL))
    }

    // ---- seen_feed ----

    @Test
    fun `seen_feed tells the newest post shown, pinned included, waiting posts not`() {
        r.state()
        val pinned = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 1, pinnedAt = NOW)
        val a = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 5)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a), pinned = listOf(pinned))
        session.onLive(listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0)))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.markViewed(FeedKind.ANNOUNCEMENTS)
        val seen = r.last<SeenFeedPayload>()
        assertEquals(FeedKind.ANNOUNCEMENTS, seen.feed)
        assertEquals(pinned.createdAt, seen.upTo)
    }

    @Test
    fun `seen_feed goes again when a newer post is shown, no sooner than 2 seconds after the last`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post(minutesAgo = 5)))
        session.show(FeedKind.GLOBAL)
        session.markViewed(FeedKind.GLOBAL)
        assertEquals(1, r.all<SeenFeedPayload>().size)
        val fresh = post(minutesAgo = 1)
        session.onLive(listOf(fresh))
        session.releaseWaiting(FeedKind.GLOBAL)
        repeat(FeedSession.SEEN_EVERY_TICKS - 1) {
            session.tick()
            session.markViewed(FeedKind.GLOBAL)
        }
        assertEquals(1, r.all<SeenFeedPayload>().size)
        session.tick()
        session.markViewed(FeedKind.GLOBAL)
        assertEquals(2, r.all<SeenFeedPayload>().size)
        assertEquals(fresh.createdAt, r.last<SeenFeedPayload>().upTo)
    }

    @Test
    fun `a guest, and a feed not loaded or empty, send no seen_feed - the dot still goes`() {
        r.state(GUEST.copy(unread = Unread(announcements = false, global = true)))
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        session.markViewed(FeedKind.GLOBAL)
        assertFalse(session.isUnread(FeedKind.GLOBAL))
        r.state()
        session.markViewed(FeedKind.ANNOUNCEMENTS)
        r.firstPage(FeedKind.ANNOUNCEMENTS, emptyList())
        session.markViewed(FeedKind.ANNOUNCEMENTS)
        assertTrue(r.all<SeenFeedPayload>().isEmpty())
    }

    @Test
    fun `a tab viewed briefly right after the other is still marked seen`() {
        r.state()
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS)))
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        session.show(FeedKind.ANNOUNCEMENTS)
        session.markViewed(FeedKind.ANNOUNCEMENTS)
        repeat(5) { session.tick() }
        session.show(FeedKind.GLOBAL)
        repeat(20) {
            session.tick()
            session.markViewed(FeedKind.GLOBAL)
        }
        session.show(null)
        assertTrue(r.all<SeenFeedPayload>().any { it.feed == FeedKind.GLOBAL })
    }

    // ---- Unseen-replies markers (feed-mod.md 3, docs/feed-app.md 8) ----

    @Test
    fun `a reply to the player's reply in someone else's thread marks that reply only`() {
        r.state()
        val main = post()
        val mine = replyIn(main, minutesAgo = 5, mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, listOf(mine))
        session.onLive(listOf(replyIn(main, to = mine, answersMe = true)))
        assertEquals(1, session.post(mine.id)!!.unseenReplies)
        assertEquals(0, session.post(main.id)!!.unseenReplies)
    }

    @Test
    fun `a reply to the player's reply in the player's own thread marks both, once each`() {
        r.state()
        val main = post(mine = true)
        val mine = replyIn(main, minutesAgo = 5, mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, listOf(mine))
        session.onLive(listOf(replyIn(main, to = mine, answersMe = true)))
        assertEquals(1, session.post(mine.id)!!.unseenReplies)
        assertEquals(1, session.post(main.id)!!.unseenReplies)
    }

    @Test
    fun `a reply to someone else's reply in the player's thread raises nothing`() {
        r.state()
        val main = post(mine = true)
        val theirs = replyIn(main, minutesAgo = 5)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, listOf(theirs))
        session.onLive(listOf(replyIn(main, to = theirs, answersMe = false)))
        assertEquals(0, session.post(main.id)!!.unseenReplies)
        assertEquals(0, session.post(theirs.id)!!.unseenReplies)
    }

    @Test
    fun `the player's own reply never raises a marker, even said to answer them`() {
        r.state()
        val main = post(mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        session.onLive(listOf(replyIn(main, mine = true, answersMe = true)))
        assertEquals(0, session.post(main.id)!!.unseenReplies)
    }

    @Test
    fun `a broken answersMe on someone else's post raises nothing`() {
        r.state()
        val main = post()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        session.onLive(listOf(replyIn(main, answersMe = true)))
        assertEquals(0, session.post(main.id)!!.unseenReplies)
    }

    @Test
    fun `opening the thread clears the markers of the main post and of its replies, and sends seen_thread once`() {
        r.state()
        val main = post(mine = true, unseenReplies = 2)
        val mine = replyIn(main, mine = true).copy(unseenReplies = 1)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        session.showThread(main.id)
        // Not loaded yet: marked seen once its first page is shown.
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, listOf(mine), null))
        assertEquals(listOf(SeenThreadPayload(main.id)), r.all<SeenThreadPayload>())
        assertEquals(0, session.post(main.id)!!.unseenReplies)
        assertEquals(0, session.post(mine.id)!!.unseenReplies)
        session.showThread(main.id)
        assertEquals(1, r.all<SeenThreadPayload>().size)
        // Left and opened again: seen again, 2 seconds after the last mark at the soonest.
        session.showThread(null)
        session.showThread(main.id)
        assertEquals(1, r.all<SeenThreadPayload>().size)
        repeat(FeedSession.SEEN_EVERY_TICKS) { session.tick() }
        assertEquals(2, r.all<SeenThreadPayload>().size)
    }

    @Test
    fun `replies arriving while the thread is read are marked seen together, one mark every 2 seconds at most`() {
        r.state()
        val main = post(mine = true)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, emptyList())
        session.showThread(main.id)
        assertEquals(1, r.all<SeenThreadPayload>().size)
        repeat(5) { session.onLive(listOf(replyIn(main, answersMe = true))) }
        assertEquals(1, r.all<SeenThreadPayload>().size)
        repeat(FeedSession.SEEN_EVERY_TICKS) { session.tick() }
        assertEquals(2, r.all<SeenThreadPayload>().size)
        repeat(3 * FeedSession.SEEN_EVERY_TICKS) { session.tick() }
        assertEquals(2, r.all<SeenThreadPayload>().size)
        assertEquals(0, session.post(main.id)!!.unseenReplies)
    }

    @Test
    fun `a seen mark that waits for its turn goes once the player left the tab`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post(minutesAgo = 5)))
        session.show(FeedKind.GLOBAL)
        session.markViewed(FeedKind.GLOBAL)
        val fresh = post(minutesAgo = 1)
        session.onLive(listOf(fresh))
        session.releaseWaiting(FeedKind.GLOBAL)
        session.markViewed(FeedKind.GLOBAL)
        session.show(FeedKind.ANNOUNCEMENTS)
        repeat(FeedSession.SEEN_EVERY_TICKS) { session.tick() }
        assertEquals(fresh.createdAt, r.last<SeenFeedPayload>().upTo)
    }

    @Test
    fun `a guest's thread clears its markers but tells the site nothing`() {
        r.state(GUEST)
        val main = post(unseenReplies = 1)
        r.loadThread(main, emptyList())
        session.showThread(main.id)
        assertEquals(0, session.post(main.id)!!.unseenReplies)
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
    }

    // ---- Bounds ----

    @Test
    fun `the 16 most recent threads are kept, the one on screen whatever its age`() {
        val first = post()
        r.loadThread(first, emptyList())
        session.showThread(first.id)
        val others = (1..16).map { post() }
        others.forEach { session.thread(it.id) }
        assertNotNull(session.threadOrNull(first.id))
        assertNull(session.threadOrNull(others.first().id))
        assertEquals(16, (others.drop(1).map { it.id } + first.id).count { session.threadOrNull(it) != null })
    }

    @Test
    fun `a thread dropped from the 16 takes its replies with it, but not posts its feed shows`() {
        val main = post()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        val reply = replyIn(main)
        r.loadThread(main, listOf(reply))
        repeat(16) { session.thread(post().id) }
        assertNull(session.threadOrNull(main.id))
        assertNull(session.post(reply.id))
        assertNotNull(session.post(main.id))
    }

    @Test
    fun `a list shown is never trimmed, the one left is, and its first page reloads it from the start`() {
        val posts = (1..205).map { post(minutesAgo = it.toLong()) }
        session.show(FeedKind.GLOBAL)
        // All from live posts and a first page: none came with a cursor.
        r.firstPage(FeedKind.GLOBAL, posts.take(5))
        val live = (1..200).map { post(minutesAgo = 0).copy(createdAt = NOW.plusSeconds(it.toLong())) }
        live.chunked(20).forEach { session.onLive(it) }
        session.releaseWaiting(FeedKind.GLOBAL)
        assertEquals(205, global.posts.size)
        session.show(FeedKind.ANNOUNCEMENTS)
        assertEquals(FeedSession.MAX_FEED, global.posts.size)
        assertTrue(global.hasMore)
        // The oldest kept came live: the pages are asked again from the first.
        assertNull(r.sendsLoad(FeedKind.GLOBAL) { session.loadMore(FeedKind.GLOBAL) }.before)
    }

    @Test
    fun `a page in flight when the list is trimmed leaves no hole`() {
        val posts = (1..260).map { post(minutesAgo = it.toLong()) }
        session.show(FeedKind.GLOBAL)
        r.firstPage(FeedKind.GLOBAL, posts.take(50), next = "c1")
        for (page in 1..4) r.nextPage(FeedKind.GLOBAL, posts.drop(50 * page).take(50), next = "c${page + 1}")
        val inFlight = r.sendsLoad(FeedKind.GLOBAL) { session.loadMore(FeedKind.GLOBAL) }
        session.show(FeedKind.ANNOUNCEMENTS)
        session.onPage(PagePayload(inFlight.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), posts.drop(250), null))
        val held = global.posts
        assertEquals(posts.take(held.size).map { it.id }, held)
    }

    // ---- Logging out ----

    @Test
    fun `clearing forgets everything the next server must not see`() {
        r.state(ANNOUNCER.copy(unread = Unread(announcements = true, global = true)))
        val main = post(likes = 1)
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        global.scroll.offset = 120f
        r.loadThread(main, emptyList())
        session.onLive(listOf(post(minutesAgo = 0)))
        session.toggleLike(main.id)
        session.land(main.id)
        session.show(FeedKind.GLOBAL)
        session.showThread(main.id)
        val revision = session.revision
        session.clear()
        assertTrue(session.revision > revision)
        assertEquals(FeedAvailability.UNKNOWN, session.availability)
        assertNull(session.state)
        assertNull(session.post(main.id))
        assertFalse(global.loaded)
        assertTrue(global.posts.isEmpty() && global.waiting.isEmpty())
        assertEquals(0f, global.scroll.offset)
        assertNull(session.threadOrNull(main.id))
        assertFalse(session.isUnread(FeedKind.GLOBAL) || session.isUnread(FeedKind.ANNOUNCEMENTS))
        assertNull(session.viewing)
        assertNull(session.viewingThread)
        assertNull(session.takeNotice())
        assertFalse(session.hasLoadedContent)
        // Loaded again on the next server, the landing asked on the last one is gone.
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS)))
        assertNull(session.takeLanding(FeedKind.ANNOUNCEMENTS))
        // The post deleted on the old server isn't remembered as gone on the new one.
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        assertEquals(listOf(main.id), global.posts)
        assertFalse(session.post(main.id)!!.liked)
    }

    @Test
    fun `two notices in one tick are both told, in order`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        r.state(available = Availability.UNAVAILABLE)
        r.state()
        assertEquals(FeedNotice.ConnectionLost, session.takeNotice())
        assertEquals(FeedNotice.ConnectionBack, session.takeNotice())
        assertNull(session.takeNotice())
    }

    @Test
    fun `a notice untold 5 seconds while a screen could tell it is dropped, and all go when Feed closes`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post()))
        r.state(available = Availability.UNAVAILABLE)
        // No screen shown (the phone put away): it waits, however long.
        repeat(FeedSession.NOTICE_MAX_AGE_TICKS + 1) { session.tick() }
        assertEquals(FeedNotice.ConnectionLost, session.takeNotice())
        r.state()
        assertEquals(FeedNotice.ConnectionBack, session.takeNotice())
        // The feed shown and not telling it: 5 seconds, then it means nothing any more.
        r.state(available = Availability.UNAVAILABLE)
        session.show(FeedKind.GLOBAL)
        repeat(FeedSession.NOTICE_MAX_AGE_TICKS + 1) { session.tick() }
        assertNull(session.takeNotice())
        val owner = Any()
        session.appOpened(owner)
        r.state()
        session.appClosed(owner)
        assertNull(session.takeNotice())
    }

    @Test
    fun `a landing on a list about to reload waits for the reload to bring the post`() {
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10)))
        session.onReset()
        val fresh = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0)
        session.land(fresh.id)
        assertNull(session.takeLanding(FeedKind.ANNOUNCEMENTS))
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(fresh) + session.feed(FeedKind.ANNOUNCEMENTS).posts.mapNotNull(session::post))
        assertEquals(fresh.id, session.takeLanding(FeedKind.ANNOUNCEMENTS))
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).posts.contains(fresh.id))
        assertTrue(session.wantsHighlight(fresh.id))
    }

    @Test
    fun `an answer lost on the way counts as unavailable after 30 seconds`() {
        r.state()
        session.loadIfNeeded(FeedKind.GLOBAL)
        repeat(FeedSession.ANSWER_TIMEOUT_TICKS - 1) { session.tick() }
        assertTrue(global.loadingFirst)
        session.tick()
        assertFalse(global.loadingFirst)
        assertEquals(Outcome.UNAVAILABLE, global.firstError)
        // A write too: the heart goes back.
        val p = post(likes = 3)
        session.retryFirst(FeedKind.GLOBAL)
        r.state()
        session.onPage(PagePayload(r.last<LoadFeedPayload>().requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), listOf(p), null))
        session.toggleLike(p.id)
        repeat(FeedSession.ANSWER_TIMEOUT_TICKS) { session.tick() }
        assertFalse(session.post(p.id)!!.liked)
        assertEquals(3, session.post(p.id)!!.likes)
    }
}

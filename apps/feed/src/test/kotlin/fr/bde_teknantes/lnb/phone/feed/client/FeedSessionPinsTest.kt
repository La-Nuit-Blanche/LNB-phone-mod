package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** The pinned group (art direction 9.4): pins shown at once, the answers, the `pins` events, and broken ones. */
class FeedSessionPinsTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val feed get() = session.feed(FeedKind.ANNOUNCEMENTS)

    private fun announcement(minutesAgo: Long, pinnedAt: java.time.Instant? = null) =
        post(FeedKind.ANNOUNCEMENTS, minutesAgo = minutesAgo, canPin = true, pinnedAt = pinnedAt)

    /** Announcements loaded for an announcer: [posts], and [pinned] (pinned one minute apart, the first most recent). */
    private fun loaded(posts: List<PostView>, pinned: List<PostView> = emptyList(), next: String? = null) {
        r.state(ANNOUNCER)
        r.firstPage(FeedKind.ANNOUNCEMENTS, posts, pinned = pinned, next = next)
    }

    private fun pinnedAt(i: Int) = NOW.minusSeconds(60L * (i + 1))

    private fun threePinned() = (0..2).map { announcement(minutesAgo = 1000L + it, pinnedAt = pinnedAt(it)) }

    @Test
    fun `a pin shows at once, the post highlighted in the group, and the answer settles it`() {
        val a = announcement(10)
        val b = announcement(20)
        loaded(listOf(a, b))
        assertEquals(PinAttempt.SENT, session.togglePin(b.id, replace = false))
        val sent = r.last<PinPayload>()
        assertTrue(sent.pinned)
        assertFalse(sent.replace)
        assertEquals(listOf(b.id), feed.pinned)
        assertEquals(listOf(a.id), feed.posts)
        assertTrue(session.wantsHighlight(b.id))
        val serverTime = NOW.plusSeconds(2)
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, pinned = listOf(Pin(b.id, serverTime))))
        assertEquals(serverTime, session.post(b.id)!!.pinnedAt)
        assertEquals(FeedNotice.PinDone(true), session.takeNotice())
    }

    @Test
    fun `an unpin shows at once, the post back at its date, and says so`() {
        val a = announcement(10)
        val c = announcement(30)
        val p = announcement(20, pinnedAt = NOW)
        loaded(listOf(a, c), pinned = listOf(p))
        assertEquals(PinAttempt.SENT, session.togglePin(p.id, replace = false))
        assertFalse(r.last<PinPayload>().pinned)
        assertTrue(feed.pinned.isEmpty())
        assertEquals(listOf(a.id, p.id, c.id), feed.posts)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.OK, pinned = emptyList()))
        assertEquals(FeedNotice.PinDone(false), session.takeNotice())
        assertEquals(listOf(a.id, p.id, c.id), feed.posts)
    }

    @Test
    fun `with three pinned a pin asks first, and the replace sends the oldest back to its date`() {
        val pins = threePinned()
        val a = announcement(10)
        loaded(listOf(a), pinned = pins)
        val sent = r.all<PinPayload>().size
        assertEquals(PinAttempt.NEEDS_REPLACE, session.togglePin(a.id, replace = false))
        assertEquals(sent, r.all<PinPayload>().size)
        assertEquals(PinAttempt.SENT, session.togglePin(a.id, replace = true))
        assertTrue(r.last<PinPayload>().replace)
        assertEquals(listOf(a.id, pins[0].id, pins[1].id), feed.pinned)
        // The oldest pin returns to its place in the list (it is older than `a`).
        assertEquals(listOf(pins[2].id), feed.posts)
    }

    @Test
    fun `a replacing pin shows first even when the client's clock is behind the site's`() {
        // Pinned on the site a minute ahead of this client's clock.
        val pins = (0..2).map { announcement(minutesAgo = 1000L + it, pinnedAt = NOW.plusSeconds(60L - it)) }
        val a = announcement(10)
        loaded(listOf(a), pinned = pins)
        session.togglePin(a.id, replace = true)
        assertEquals(a.id, feed.pinned.first())
    }

    @ParameterizedTest
    @EnumSource(Outcome::class, names = ["UNAVAILABLE", "DISABLED", "NOT_ALLOWED", "NOT_PINNABLE", "INVALID"])
    fun `a pin refused puts the button back and says the action failed`(outcome: Outcome) {
        val a = announcement(10)
        val p = announcement(20, pinnedAt = NOW)
        loaded(listOf(a), pinned = listOf(p))
        session.togglePin(a.id, replace = false)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, outcome))
        assertEquals(listOf(p.id), feed.pinned)
        assertEquals(listOf(a.id), feed.posts)
        assertNull(session.post(a.id)!!.pinnedAt)
        assertEquals(FeedNotice.ActionFailed, session.takeNotice())
    }

    @Test
    fun `an unpin refused puts the post back in the group`() {
        val p = announcement(20, pinnedAt = NOW)
        loaded(listOf(announcement(10)), pinned = listOf(p))
        session.togglePin(p.id, replace = false)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.UNAVAILABLE))
        assertEquals(listOf(p.id), feed.pinned)
        assertEquals(NOW, session.post(p.id)!!.pinnedAt)
    }

    @Test
    fun `refusals for who the player is, and for going too fast, say so`() {
        val cases = listOf(
            ResultPayload(0, Outcome.NOT_LINKED) to FeedNotice.Refused(WriteBlock.GUEST),
            ResultPayload(0, Outcome.BANNED) to FeedNotice.Refused(WriteBlock.BANNED),
            ResultPayload(0, Outcome.RATE_LIMITED, retryAfter = 4) to FeedNotice.TooFast(4),
            // The server's own limits carry no retryAfter: 1 second.
            ResultPayload(0, Outcome.RATE_LIMITED) to FeedNotice.TooFast(1),
        )
        for ((answer, notice) in cases) {
            val rec = FeedFixtures.Recorder()
            rec.state(ANNOUNCER)
            val a = announcement(10)
            rec.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a))
            rec.session.togglePin(a.id, replace = false)
            rec.session.onResult(answer.copy(requestId = rec.last<PinPayload>().requestId))
            assertEquals(notice, rec.session.takeNotice(), "$answer")
            assertTrue(rec.session.feed(FeedKind.ANNOUNCEMENTS).pinned.isEmpty())
        }
    }

    @Test
    fun `two announcers at once - PIN_LIMIT from the server asks for the dialog`() {
        val pins = (0..1).map { announcement(minutesAgo = 1000L + it, pinnedAt = pinnedAt(it)) }
        val a = announcement(10)
        loaded(listOf(a), pinned = pins)
        session.togglePin(a.id, replace = false)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.PIN_LIMIT, oldestPin = pins[1].id))
        assertEquals(FeedNotice.PinLimit(a.id), session.takeNotice())
        assertEquals(pins.map { it.id }, feed.pinned)
    }

    @Test
    fun `a pin answered NOT_FOUND drops the post`() {
        val a = announcement(10)
        loaded(listOf(a))
        session.togglePin(a.id, replace = false)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.NOT_FOUND))
        assertNull(session.post(a.id))
        assertFalse(feed.posts.contains(a.id) || feed.pinned.contains(a.id))
    }

    @Test
    fun `a pins event while a pin waits survives that pin's failure`() {
        val a = announcement(10)
        val c = announcement(20)
        val p = announcement(30, pinnedAt = pinnedAt(1))
        loaded(listOf(a, c), pinned = listOf(p))
        session.togglePin(a.id, replace = false)
        val request = r.last<PinPayload>().requestId
        // Another announcer pinned c meanwhile.
        session.onPins(listOf(Pin(c.id, NOW), Pin(p.id, pinnedAt(1))))
        session.onResult(ResultPayload(request, Outcome.UNAVAILABLE))
        assertEquals(listOf(c.id, p.id), feed.pinned)
        assertEquals(listOf(a.id), feed.posts)
    }

    @Test
    fun `nothing to pin - a post waiting for its pin, a reply, a Global post, a post not pinnable, a post not held`() {
        val a = announcement(10)
        val plain = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 20, canPin = false)
        val g = post(FeedKind.GLOBAL, canPin = true)
        loaded(listOf(a, plain))
        r.firstPage(FeedKind.GLOBAL, listOf(g))
        val reply = replyIn(a).copy(canPin = true)
        r.loadThread(a, listOf(reply))
        assertEquals(PinAttempt.SENT, session.togglePin(a.id, replace = false))
        val sent = r.all<PinPayload>().size
        assertEquals(PinAttempt.IGNORED, session.togglePin(a.id, replace = false))
        assertEquals(PinAttempt.IGNORED, session.togglePin(plain.id, replace = false))
        assertEquals(PinAttempt.IGNORED, session.togglePin(reply.id, replace = false))
        assertEquals(PinAttempt.IGNORED, session.togglePin(FeedFixtures.id(), replace = false))
        assertEquals(sent, r.all<PinPayload>().size)
    }

    @Test
    fun `a Global post is never pinned, whatever its view says`() {
        val g = post(FeedKind.GLOBAL, canPin = true)
        r.state(ANNOUNCER)
        r.firstPage(FeedKind.GLOBAL, listOf(g))
        assertEquals(PinAttempt.IGNORED, session.togglePin(g.id, replace = false))
        assertTrue(r.all<PinPayload>().isEmpty())
    }

    @Test
    fun `pinning follows the view, writing switched off included, and a view that doesn't allow it sends nothing`() {
        // Writing off: the site still pins (art direction 9.11), so the announcer's pin goes.
        val off = FeedFixtures.Recorder()
        off.state(ANNOUNCER.copy(writing = false))
        val a = announcement(10)
        off.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a))
        assertEquals(PinAttempt.SENT, off.session.togglePin(a.id, replace = false))
        assertEquals(1, off.all<PinPayload>().size)
        // A guest's view never allows pinning.
        val guest = FeedFixtures.Recorder()
        guest.state(FeedFixtures.GUEST)
        val b = announcement(10).copy(canPin = false)
        guest.firstPage(FeedKind.ANNOUNCEMENTS, listOf(b))
        assertEquals(PinAttempt.IGNORED, guest.session.togglePin(b.id, replace = false))
        assertTrue(guest.all<PinPayload>().isEmpty())
    }

    @Test
    fun `a pin whose post is deleted meanwhile is forgotten, its answer changes nothing`() {
        val a = announcement(10)
        loaded(listOf(a))
        session.togglePin(a.id, replace = false)
        session.onRemoved(listOf(RemovedItem(a.id, FeedKind.ANNOUNCEMENTS, null)))
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.UNAVAILABLE))
        assertTrue(feed.pinned.isEmpty())
        assertNull(session.post(a.id))
    }

    // ---- The `pins` event ----

    @Test
    fun `pins orders the group by pin time, whatever order it lists them in, and drops repeats`() {
        val posts = (1..3).map { announcement(10L * it) }
        loaded(posts)
        session.onPins(listOf(Pin(posts[2].id, pinnedAt(2)), Pin(posts[0].id, pinnedAt(0)), Pin(posts[0].id, pinnedAt(0)), Pin(posts[1].id, pinnedAt(1))))
        assertEquals(posts.map { it.id }, feed.pinned)
        assertTrue(feed.posts.isEmpty())
    }

    @Test
    fun `a Global post or a reply named in pins is never pinned, and Global is untouched`() {
        val a = announcement(10)
        loaded(listOf(a))
        val g = post(FeedKind.GLOBAL)
        r.firstPage(FeedKind.GLOBAL, listOf(g))
        val reply = replyIn(a)
        r.loadThread(a, listOf(reply))
        session.onPins(listOf(Pin(g.id, NOW), Pin(reply.id, NOW)))
        assertTrue(feed.pinned.isEmpty())
        assertEquals(listOf(a.id), feed.posts)
        assertEquals(listOf(g.id), session.feed(FeedKind.GLOBAL).posts)
        assertNull(session.post(g.id)!!.pinnedAt)
        assertNull(session.post(reply.id)!!.pinnedAt)
    }

    @Test
    fun `a post pinned that the client doesn't hold reloads Announcements, keeping the group it can show`() {
        val a = announcement(10)
        val b = announcement(20)
        loaded(listOf(a, b))
        session.onPins(listOf(Pin(FeedFixtures.id(), NOW), Pin(b.id, pinnedAt(0))))
        assertEquals(listOf(b.id), feed.pinned)
        assertTrue(feed.stale)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(a), pinned = listOf(announcement(500, pinnedAt = NOW), b.copy(pinnedAt = pinnedAt(0))))
        assertEquals(2, feed.pinned.size)
        assertFalse(feed.stale)
    }

    @Test
    fun `a pins event before Announcements is loaded changes nothing that will show`() {
        session.onPins(listOf(Pin(FeedFixtures.id(), NOW)))
        assertFalse(feed.loaded)
        assertFalse(feed.stale)
    }

    @Test
    fun `an unpinned post older than the pages loaded leaves the list and comes back with its page`() {
        val old = announcement(5000, pinnedAt = NOW)
        val a = announcement(10)
        val b = announcement(20)
        val c = announcement(30)
        // Pages remain: the unpinned post's place is past what is loaded.
        loaded(listOf(a, b), pinned = listOf(old), next = "c1")
        session.onPins(emptyList())
        assertTrue(feed.pinned.isEmpty())
        assertEquals(listOf(a.id, b.id), feed.posts)
        assertNull(session.post(old.id)!!.pinnedAt)
        r.nextPage(FeedKind.ANNOUNCEMENTS, listOf(c, old.copy(pinnedAt = null)))
        assertEquals(listOf(a.id, b.id, c.id, old.id), feed.posts)
    }

    @Test
    fun `an unpinned post goes at the end, at its date, when everything is loaded`() {
        val old = announcement(5000, pinnedAt = NOW)
        val a = announcement(10)
        val b = announcement(20)
        loaded(listOf(a, b), pinned = listOf(old))
        session.onPins(emptyList())
        assertEquals(listOf(a.id, b.id, old.id), feed.posts)
    }

    @Test
    fun `a pinned post that is deleted leaves the group`() {
        val p = announcement(20, pinnedAt = NOW)
        loaded(listOf(announcement(10)), pinned = listOf(p))
        session.onRemoved(listOf(RemovedItem(p.id, FeedKind.ANNOUNCEMENTS, null)))
        assertTrue(feed.pinned.isEmpty())
    }

    @Test
    fun `a waiting announcement pinned by someone joins the group and leaves the pill's count`() {
        val a = announcement(10)
        loaded(listOf(a))
        val fresh = announcement(0)
        session.onLive(listOf(fresh))
        assertEquals(listOf(fresh.id), feed.waiting)
        session.onPins(listOf(Pin(fresh.id, NOW)))
        assertEquals(listOf(fresh.id), feed.pinned)
        assertTrue(feed.waiting.isEmpty())
    }
}

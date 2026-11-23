package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A visit of Feed: open while one of its root screens is on the phone, ended by that screen closing, or leaving the
 * phone without a word (it threw), or by the player leaving the server; and one `open` per visit.
 */
class AppVisitTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    @Test
    fun `Feed is open while its root screen is on the phone, the phone put away included, and not once it closed`() {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        assertTrue(session.isAppOpen)
        t.closePhone()
        assertTrue(session.isAppOpen)
        t.openPhone()
        t.close()
        assertFalse(session.isAppOpen)
    }

    @Test
    fun `an owner that left the phone without closing ends the visit with no more ticks, nothing shown and the notices gone`() {
        var alive = true
        session.appOpened(Any()) { alive }
        r.state()
        r.page(FeedKind.GLOBAL, listOf(post(text = "x")))
        session.show(FeedKind.GLOBAL)
        r.state(available = Availability.UNAVAILABLE)
        assertTrue(session.isAppOpen)
        // It threw: its onClosed never comes.
        alive = false
        assertFalse(session.isAppOpen)
        assertNull(session.viewing)
        assertNull(session.takeNotice())
        // No `open` every 15 seconds for a closed Feed: the client only ticks an open one.
        val opens = r.all<OpenPayload>().size
        repeat(400) { if (session.isAppOpen) session.tick() }
        assertEquals(opens, r.all<OpenPayload>().size)
    }

    @Test
    fun `a root made for a stack that never opened doesn't count as open, and clearing forgets every owner`() {
        session.appOpened(Any()) { false }
        assertFalse(session.isAppOpen)
        session.appOpened(Any())
        assertTrue(session.isAppOpen)
        session.clear()
        assertFalse(session.isAppOpen)
    }

    @Test
    fun `open goes once per visit, and again for the next one`() {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        assertEquals(1, r.all<OpenPayload>().size)
        // Compose and back: the feed shows again, no second `open`.
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(post(text = "x")))
        t.frame()
        t.click(220f, 334f)
        t.tick(4)
        t.click(22f, 22f)
        t.tick(4)
        assertEquals(1, r.all<OpenPayload>().size)
        t.close()
        tester = PhoneTester(feedApp(session, FeedKind.GLOBAL))
        assertEquals(2, r.all<OpenPayload>().size)
    }
}

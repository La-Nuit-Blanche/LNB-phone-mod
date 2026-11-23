package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/** Pinning and deleting from the feed (art direction 9.4, 9.9), and the session's parts of them. */
class FeedActionsTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun open(tab: FeedKind, posts: List<PostView>, pinned: List<PostView> = emptyList()): PhoneTester {
        val t = PhoneTester { FeedScreen(session, strings, tab, fromHome = true, zone = ZoneOffset.UTC) }.also { tester = it }
        r.state(ANNOUNCER)
        val request = r.all<LoadFeedPayload>().last { it.feed == tab }
        session.onPage(PagePayload(request.requestId, Outcome.OK, tab, pinned, posts, null))
        t.frame()
        return t
    }

    private fun PhoneTester.clickText(text: String) {
        val drawn = texts.first { it.text == text }
        click(drawn.x + 10f, drawn.y + 6f)
    }

    @Test
    fun `a fourth pin asks first, then replaces with the confirm button loading until the answer`() {
        val pins = (1..3).map { post(FeedKind.ANNOUNCEMENTS, minutesAgo = 3000L + it, author = LUNE, text = "p$it", pinnedAt = NOW.minusSeconds(it * 60L), canPin = true) }
        val fresh = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 10, author = LUNE, text = "neuf", canPin = true)
        val t = open(FeedKind.ANNOUNCEMENTS, listOf(fresh), pins)
        (t.screen as FeedScreen).pinClicked(session.post(fresh.id)!!)
        t.frame()
        assertTrue(t.showsStart("dialog.pin_limit"))
        assertTrue(r.all<PinPayload>().isEmpty())
        t.confirmDialog()
        val sent = r.last<PinPayload>()
        assertTrue(sent.pinned)
        assertTrue(sent.replace)
        assertTrue(session.isPinning(fresh.id))
        // Shown at once: the new pin first in the group.
        assertEquals(fresh.id, session.feed(FeedKind.ANNOUNCEMENTS).pinned.first())
        t.tick()
        assertTrue((t.screen as FeedScreen).isDialogOpen)
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, pinned = listOf(Pin(fresh.id, NOW)) + pins.take(2).map { Pin(it.id, it.pinnedAt!!) }))
        t.tick()
        assertFalse((t.screen as FeedScreen).isDialogOpen)
        assertTrue(t.shows("pin.done"))
    }

    @Test
    fun `an unpin answered as refused puts the pin back and says so`() {
        val pinned = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 100, author = LUNE, text = "epingle", pinnedAt = NOW, canPin = true)
        val t = open(FeedKind.ANNOUNCEMENTS, emptyList(), listOf(pinned))
        (t.screen as FeedScreen).pinClicked(session.post(pinned.id)!!)
        val sent = r.last<PinPayload>()
        assertFalse(sent.pinned)
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).pinned.isEmpty())
        session.onResult(ResultPayload(sent.requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertEquals(listOf(pinned.id), session.feed(FeedKind.ANNOUNCEMENTS).pinned)
        assertTrue(t.showsStart("snackbar.action"))
    }

    @Test
    fun `deleting a post folds its cell away and says Post deleted, and cancelling changes nothing`() {
        val own = post(text = "mien", mine = true, canDelete = true)
        val other = post(text = "autre", minutesAgo = 5)
        val t = open(FeedKind.GLOBAL, listOf(own, other))
        // The more button of the first card: 28 dp at (220, 5).
        t.click(234f, 44f + 19f)
        t.clickText("menu.delete")
        assertTrue(t.showsStart("dialog.delete_post"))
        t.cancelDialog()
        t.tick(3)
        assertTrue(r.all<DeletePayload>().isEmpty())
        assertTrue(t.shows("mien"))

        t.click(234f, 44f + 19f)
        t.clickText("menu.delete")
        t.confirmDialog()
        val sent = r.last<DeletePayload>()
        session.onResult(ResultPayload(sent.requestId, Outcome.OK))
        t.frame()
        // Folding: still drawn, then shorter after a tick.
        assertTrue(t.shows("mien"))
        val before = t.texts.first { it.text == "autre" }.y
        t.tick()
        assertTrue(t.texts.first { it.text == "autre" }.y < before)
        assertTrue(t.shows("delete.post_done"))
        t.tick(4)
        t.frame()
        assertFalse(t.shows("mien"))
        assertTrue(t.shows("autre"))
    }

    @Test
    fun `scrolled, a post whose removed comes before the answer still folds away`() {
        val first = post(text = "premier", minutesAgo = 1)
        val own = post(text = "mien", mine = true, canDelete = true, minutesAgo = 2)
        val rest = (3..8).map { post(text = "p$it", minutesAgo = it.toLong()) }
        val t = open(FeedKind.GLOBAL, listOf(first, own) + rest)
        t.scroll(128f, 200f, -2f)
        t.frame()
        val more = t.texts.first { it.text == "mien" }.y - 46f + 19f
        t.click(234f, more)
        t.clickText("menu.delete")
        t.confirmDialog()
        session.onRemoved(listOf(fr.bde_teknantes.lnb.phone.feed.model.RemovedItem(own.id, FeedKind.GLOBAL, null)))
        t.frame()
        assertTrue(t.shows("mien"))
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        t.tick()
        assertTrue(t.shows("mien"))
        t.tick(5)
        assertFalse(t.shows("mien"))
    }

    @Test
    fun `a delete refused closes the dialog and the post stays`() {
        val own = post(text = "mien", mine = true, canDelete = true)
        val t = open(FeedKind.GLOBAL, listOf(own))
        t.click(234f, 44f + 19f)
        t.clickText("menu.delete")
        t.confirmDialog()
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.UNAVAILABLE))
        t.tick(4)
        assertFalse((t.screen as FeedScreen).isDialogOpen)
        assertTrue(t.showsStart("snackbar.action"))
        assertTrue(t.shows("mien"))
    }

    @Test
    fun `a click on a card opens its thread, its reply button with the composer expanded`() {
        val p = post(text = "fil")
        val t = open(FeedKind.GLOBAL, listOf(p))
        t.click(150f, 44f + 50f)
        t.tick(4)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        t.click(22f, 22f)
        t.tick(4)
        // The reply button: x 50, on the action row (y 44 + 68).
        t.click(60f, 44f + 68f + 14f)
        t.tick(4)
        assertTrue((t.screen as ThreadScreen).composer.expanded)
        assertTrue(t.screen!!.hasTextFocus)
    }
}

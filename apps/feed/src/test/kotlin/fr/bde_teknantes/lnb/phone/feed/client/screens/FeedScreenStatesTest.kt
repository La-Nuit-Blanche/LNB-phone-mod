package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.PLAYER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.firstPage
import fr.bde_teknantes.lnb.phone.feed.client.nextPage
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.DrawnShape
import fr.bde_teknantes.lnb.phone.testing.PhoneSound
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * The root screen's states of art direction 9.3 to 9.6, 9.11 and 9.12 that show without pixels: what is drawn where,
 * what a click sends, which snackbar and sound follow. Strings show as their ID with the arguments after it; the test
 * font makes every character half its size wide (6.5 dp in body text, 6 dp in captions), so a one-line post with a
 * handle is 101 dp tall, 119 dp pinned.
 */
class FeedScreenStatesTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }
    private var tester: PhoneTester? = null

    private fun open(tab: FeedKind = FeedKind.ANNOUNCEMENTS): PhoneTester =
        PhoneTester { FeedScreen(session, strings, tab, fromHome = true, zone = ZoneOffset.UTC) }.also { tester = it }

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    /** Answers the last load of [feed] the screen sent. */
    private fun answer(feed: FeedKind, posts: List<PostView>, pinned: List<PostView> = emptyList(), next: String? = null) {
        val request = r.all<LoadFeedPayload>().last { it.feed == feed }
        session.onPage(PagePayload(request.requestId, Outcome.OK, feed, pinned, posts, next))
    }

    private fun failLast(feed: FeedKind, outcome: Outcome) =
        session.onPage(PagePayload.failed(r.all<LoadFeedPayload>().last { it.feed == feed }.requestId, outcome, feed))

    /** One-line posts named `p1`, `p2`... newest first, ten minutes apart. */
    private fun posts(count: Int, feed: FeedKind = FeedKind.GLOBAL, prefix: String = "p") =
        (1..count).map { post(feed, minutesAgo = it * 10L, author = LUNE, text = "$prefix$it") }

    /** Tall pinned announcements (6 lines of text: 209 dp each). */
    private fun tallPins(count: Int) = (0 until count).map {
        post(FeedKind.ANNOUNCEMENTS, minutesAgo = 3000L + it, author = LUNE, text = "x ".repeat(80).trim(), pinnedAt = NOW.minusSeconds(it.toLong()))
    }

    private fun PhoneTester.tabDots() = shapes.count { it.kind == DrawnShape.Kind.CIRCLE && it.width == 6f && it.y < 44f }

    private fun PhoneTester.fabs() = shapes.count { it.width == 48f && it.height == 48f }

    /** The new-posts pill drawn: 28 dp tall, in the accent, at y 52. */
    private fun PhoneTester.pill() = shapes.firstOrNull { it.kind == DrawnShape.Kind.ROUND_RECT && it.height == 28f && it.y == 52f && it.color == Colors.ACCENT }

    /** Where the right edge of the first relative time drawn is (6 dp per caption character). */
    private fun PhoneTester.timeRight() = texts.first { it.text.startsWith("time.") }.let { it.x + it.text.length * 6f }

    /** The hover disc of an action button with its left edge at [x]. */
    private fun PhoneTester.discAt(x: Float) = shapes.any { it.kind == DrawnShape.Kind.CIRCLE && it.width == 28f && it.x == x }

    private fun PhoneTester.yOf(text: String) = texts.first { it.text == text }.y

    /** Client ticks as the game plays them: the screen's, then the session's while Feed is open (FeedClient). */
    private fun PhoneTester.clientTicks(times: Int) = repeat(times) {
        tick()
        if (session.isAppOpen) session.tick()
    }

    // ---- Tab dots (9.6) ----

    @Test
    fun `a tab's dot shows while it isn't the active tab, and goes when it is viewed`() {
        val t = open()
        r.state(PLAYER.copy(unread = Unread(announcements = false, global = true)))
        answer(FeedKind.ANNOUNCEMENTS, posts(1, FeedKind.ANNOUNCEMENTS))
        t.frame()
        assertEquals(1, t.tabDots())
        t.click(192f, 22f)
        answer(FeedKind.GLOBAL, posts(1))
        t.tick()
        assertEquals(0, t.tabDots())
        // A new announcement while on Global: the Annonces dot.
        session.onLive(listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, author = LUNE, text = "new")))
        t.frame()
        assertEquals(1, t.tabDots())
        t.click(64f, 22f)
        t.tick()
        assertEquals(0, t.tabDots())
    }

    // ---- Who may write (9.2, 9.3, 9.11) ----

    @Test
    fun `a player sees the more button where they can delete, an announcer the pin button`() {
        val t = open()
        r.state(ANNOUNCER)
        answer(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 5, author = LUNE, text = "gg", canDelete = true, canPin = true)))
        t.frame()
        // The time ends before the more button.
        assertEquals(214f, t.timeRight())
        t.moveTo(228f, 44f + 68f + 14f)
        assertTrue(t.discAt(214f))
        assertEquals(1, t.fabs())
    }

    @Test
    fun `guests, banned players and writing off - no compose button, no more or pin button, a heart that only says why`() {
        val cases = listOf(
            FeedFixtures.GUEST to "guest.snackbar",
            ANNOUNCER.copy(banned = true) to "snackbar.banned",
            ANNOUNCER.copy(writing = false) to "snackbar.writing_off",
        )
        for ((state, words) in cases) {
            tester?.close()
            val rec = FeedFixtures.Recorder()
            val t = PhoneTester { FeedScreen(rec.session, strings, FeedKind.ANNOUNCEMENTS, fromHome = true, zone = ZoneOffset.UTC) }
            tester = t
            rec.state(state)
            val p = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 5, author = LUNE, text = "gg", likes = 8, canDelete = true, canPin = true)
            rec.session.onPage(PagePayload(rec.last<LoadFeedPayload>().requestId, Outcome.OK, FeedKind.ANNOUNCEMENTS, emptyList(), listOf(p), null))
            t.frame()
            assertEquals(0, t.fabs(), words)
            assertEquals(244f, t.timeRight(), words)
            t.moveTo(228f, 44f + 68f + 14f)
            assertFalse(t.discAt(214f), words)
            // The heart is drawn and hovers like anyone's.
            t.moveTo(128f, 44f + 68f + 14f)
            assertTrue(t.discAt(114f), words)
            t.clearSounds()
            t.click(128f, 44f + 68f + 14f)
            assertTrue(rec.all<LikePayload>().isEmpty(), words)
            assertTrue(t.shows(words), words)
            assertTrue(t.shows("8"), words)
            // A plain snackbar: the error sound once, the screen's own (an error snackbar would add its own).
            assertEquals(listOf(PhoneSound.ERROR), t.sounds, words)
            // Global has no compose button either.
            t.click(192f, 22f)
            rec.session.onPage(PagePayload(rec.all<LoadFeedPayload>().last().requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), emptyList(), null))
            t.frame()
            assertEquals(0, t.fabs(), words)
            assertTrue(t.shows("empty.global.text_guest"), words)
            assertFalse(t.texts.any { it.text.startsWith("empty.global.b") }, words)
        }
    }

    @Test
    @Disabled(
        "Defect (a broken server only): the card's pin button follows `canPin` alone, so a Global post whose view says " +
            "canPin shows it, and FeedSession.togglePin would send it. Art direction 9.3: Global posts never show it.",
    )
    fun `a Global post never shows the pin button, whatever its view says`() {
        val t = open(FeedKind.GLOBAL)
        r.state(ANNOUNCER)
        answer(FeedKind.GLOBAL, listOf(post(minutesAgo = 5, author = LUNE, text = "gg", canPin = true)))
        t.frame()
        t.moveTo(228f, 44f + 68f + 14f)
        assertFalse(t.discAt(214f))
    }

    @Test
    fun `an empty Annonces says so, with the compose button for announcers only`() {
        val t = open()
        r.state()
        answer(FeedKind.ANNOUNCEMENTS, emptyList())
        t.frame()
        assertTrue(t.shows("empty.announcements.title"))
        assertTrue(t.shows("empty.announcements.text"))
        assertEquals(0, t.fabs())
        r.state(ANNOUNCER)
        t.frame()
        assertEquals(1, t.fabs())
    }

    // ---- Likes (9.3, F-6) ----

    @Test
    fun `a like refused goes back, with Action impossible and the error sound`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, listOf(post(minutesAgo = 5, text = "gg", likes = 8)))
        t.frame()
        t.clearSounds()
        t.click(128f, 44f + 68f + 14f)
        assertTrue(t.shows("9"))
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertTrue(t.shows("8"))
        assertTrue(t.shows("snackbar.action_failed"))
        assertEquals(listOf(PhoneSound.LIKE, PhoneSound.ERROR), t.sounds)
    }

    @Test
    fun `an unlike taps, and too fast or banned answers say so`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, listOf(post(minutesAgo = 5, text = "gg", likes = 9, liked = true)))
        t.frame()
        t.clearSounds()
        t.click(128f, 44f + 68f + 14f)
        assertFalse(r.last<LikePayload>().liked)
        assertTrue(t.shows("8"))
        assertEquals(listOf(PhoneSound.TAP), t.sounds)
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.RATE_LIMITED, retryAfter = 3))
        t.tick()
        assertTrue(t.shows("snackbar.too_fast 3"))
        t.click(128f, 44f + 68f + 14f)
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.BANNED))
        t.tick()
        assertTrue(t.shows("snackbar.banned"))
    }

    @Test
    fun `counts change in place, 0 shows no number`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        val p = post(minutesAgo = 5, text = "gg", likes = 1, replies = 2)
        answer(FeedKind.GLOBAL, listOf(p))
        t.frame()
        assertTrue(t.shows("1") && t.shows("2"))
        session.onCounts(listOf(CountUpdate(p.id, likes = 0, replies = 1500)))
        t.frame()
        assertFalse(t.shows("1"))
        assertTrue(t.shows("count.thousands 1 5"))
    }

    // ---- Paging (9.5, 9.12) ----

    @Test
    fun `the spinner row loads the next page, a failure turns it into Retry, then the end row`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, posts(2), next = "c1")
        t.frame()
        assertEquals("c1", r.last<LoadFeedPayload>().before)
        failLast(FeedKind.GLOBAL, Outcome.UNAVAILABLE)
        t.frame()
        assertTrue(t.shows("error.retry"))
        val loads = r.all<LoadFeedPayload>().size
        val retry = t.texts.first { it.text == "error.retry" }
        t.click(retry.x + 5f, retry.y + 6f)
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
        assertEquals("c1", r.last<LoadFeedPayload>().before)
        assertFalse(t.shows("error.retry"))
        t.tick(5)
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
        answer(FeedKind.GLOBAL, posts(1, prefix = "older"))
        t.frame()
        assertTrue(t.shows("older1"))
        assertTrue(t.shows("feed.end"))
    }

    @Test
    fun `a next page refused as too frequent keeps the spinner and asks again 2 seconds later`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, posts(2), next = "c1")
        t.frame()
        failLast(FeedKind.GLOBAL, Outcome.RATE_LIMITED)
        val loads = r.all<LoadFeedPayload>().size
        t.clientTicks(39)
        assertEquals(loads, r.all<LoadFeedPayload>().size)
        assertFalse(t.shows("error.retry"))
        t.clientTicks(1)
        t.frame()
        assertEquals(loads + 1, r.all<LoadFeedPayload>().size)
    }

    @Test
    fun `each tab shows its own block, switched off with its own words`() {
        val t = open()
        failLast(FeedKind.ANNOUNCEMENTS, Outcome.DISABLED)
        t.frame()
        assertTrue(t.shows("feed.disabled.title"))
        assertTrue(t.shows("feed.disabled.text"))
        t.click(192f, 22f)
        assertFalse(t.shows("feed.disabled.title"))
        failLast(FeedKind.GLOBAL, Outcome.UNAVAILABLE)
        t.frame()
        assertTrue(t.shows("feed.unavailable.title"))
        assertTrue(t.shows("feed.unavailable.text"))
    }

    // ---- New posts and the pill (9.5) ----

    @Test
    fun `scrolled down in Global, the pill has the arrow, counts in place, and scrolling up brings the posts in`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, posts(10))
        t.frame()
        session.feed(FeedKind.GLOBAL).scroll.offset = 300f
        t.frame()
        session.onLive(listOf(post(minutesAgo = 0, author = LUNE, text = "n1"), post(minutesAgo = 0, author = LUNE, text = "n2")))
        t.frame()
        t.tick(4)
        assertTrue(t.shows("feed.new_posts.other 2"))
        // `feed.new_posts.other 2` (132 dp), padding 12 each side, the arrow and its gap (20 dp).
        assertEquals(176f, t.pill()?.width)
        session.onLive(listOf(post(minutesAgo = 0, author = LUNE, text = "n3")))
        t.frame()
        assertTrue(t.shows("feed.new_posts.other 3"))
        // Scrolling by hand to the top: they go in without a click.
        t.scroll(128f, 200f, 20f)
        t.frame()
        assertTrue(session.feed(FeedKind.GLOBAL).waiting.isEmpty())
        assertTrue(t.shows("n1") && t.shows("n3"))
    }

    @Test
    fun `one waiting post is counted as one`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, posts(10))
        t.frame()
        session.feed(FeedKind.GLOBAL).scroll.offset = 300f
        session.onLive(listOf(post(minutesAgo = 0, text = "n1")))
        t.frame()
        t.tick(4)
        assertTrue(t.shows("feed.new_posts.one"))
    }

    @Test
    fun `in Annonces under a short pinned group a new announcement goes in at once, highlighted`() {
        val t = open()
        r.state()
        val pin = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 3000, author = LUNE, text = "pin", pinnedAt = NOW)
        answer(FeedKind.ANNOUNCEMENTS, posts(3, FeedKind.ANNOUNCEMENTS), pinned = listOf(pin))
        t.frame()
        session.onLive(listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, author = LUNE, text = "new")))
        t.frame()
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).waiting.isEmpty())
        assertNull(t.pill())
        // Right under the 119 dp pinned cell, with the highlight.
        assertTrue(t.shapes.any { it.y == 44f + 119f && it.width == 256f && it.color == Colors.ACCENT_SOFT })
    }

    @Test
    fun `in Annonces a place only partly visible at the bottom waits without the arrow, and goes in once scrolled into view`() {
        val t = open()
        r.state()
        // Two one-line pins: the place is 238..339, past the 326 dp list by 13 dp.
        val pins = (0..1).map { post(FeedKind.ANNOUNCEMENTS, minutesAgo = 3000L + it, author = LUNE, text = "pin$it", pinnedAt = NOW.minusSeconds(it.toLong())) }
        answer(FeedKind.ANNOUNCEMENTS, posts(6, FeedKind.ANNOUNCEMENTS), pinned = pins)
        t.frame()
        val fresh = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, author = LUNE, text = "new")
        session.onLive(listOf(fresh))
        t.frame()
        t.tick(4)
        // `feed.new_announcements.one` (156 dp) and padding, no arrow.
        assertEquals(180f, t.pill()?.width)
        assertFalse(t.screen!!.showsTarget("post:${fresh.id}"))
        t.scroll(128f, 200f, -1f)
        t.frame()
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).waiting.isEmpty())
        assertTrue(t.screen!!.showsTarget("post:${fresh.id}"))
    }

    @Test
    fun `in Annonces scrolled past the pinned group the pill has the arrow, and a click scrolls to the new post`() {
        val t = open()
        r.state()
        answer(FeedKind.ANNOUNCEMENTS, posts(10, FeedKind.ANNOUNCEMENTS), pinned = tallPins(3))
        t.frame()
        val list = session.feed(FeedKind.ANNOUNCEMENTS)
        list.scroll.offset = 800f
        t.frame()
        val fresh = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, author = LUNE, text = "new")
        session.onLive(listOf(fresh))
        t.frame()
        t.tick(4)
        assertEquals(200f, t.pill()?.width)
        t.click(128f, 66f)
        t.tick(10)
        assertTrue(list.waiting.isEmpty())
        // Under the 627 dp pinned group, at the top of the view.
        assertEquals(627f, list.scroll.offset, 0.5f)
        assertTrue(t.screen!!.showsTarget("post:${fresh.id}"))
    }

    // ---- showsTarget and landing (6.4, 9.10) ----

    @Test
    fun `showsTarget is true only for an announcement fully on screen in Annonces`() {
        val t = open()
        r.state()
        val shown = posts(6, FeedKind.ANNOUNCEMENTS)
        answer(FeedKind.ANNOUNCEMENTS, shown)
        t.frame()
        val screen = t.screen!!
        assertTrue(screen.showsTarget("post:${shown[0].id}"))
        // 303..404: cut at the bottom.
        assertFalse(screen.showsTarget("post:${shown[3].id}"))
        assertFalse(screen.showsTarget(FeedTarget.Reply(FeedKind.ANNOUNCEMENTS, shown[0].id, shown[0].id).toString()))
        assertFalse(screen.showsTarget("post:${FeedFixtures.id()}"))
        assertFalse(screen.showsTarget("post:nope"))
        assertFalse(screen.showsTarget(""))
        session.feed(FeedKind.ANNOUNCEMENTS).scroll.offset = 150f
        t.frame()
        // Scrolled past the top of the first.
        assertFalse(screen.showsTarget("post:${shown[0].id}"))
        assertTrue(screen.showsTarget("post:${shown[2].id}"))
        t.click(192f, 22f)
        assertFalse(screen.showsTarget("post:${shown[2].id}"))
    }

    @Test
    fun `a notification's announcement under a tall pinned group is scrolled into view and highlighted`() {
        val target = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 5, author = LUNE, text = "cible")
        session.land(target.id)
        val t = open()
        r.state()
        answer(FeedKind.ANNOUNCEMENTS, listOf(target) + posts(3, FeedKind.ANNOUNCEMENTS), pinned = tallPins(3))
        t.frame()
        t.frame()
        // 627..728 in a 326 dp list: the bottom of the view on its bottom.
        assertEquals(728f - 326f, session.feed(FeedKind.ANNOUNCEMENTS).scroll.offset)
        assertTrue(t.screen!!.showsTarget("post:${target.id}"))
        assertTrue(t.shapes.any { it.width == 256f && it.color == Colors.ACCENT_SOFT })
    }

    @Test
    fun `a notification's announcement still waiting is brought in and shown`() {
        r.state()
        r.firstPage(FeedKind.ANNOUNCEMENTS, posts(3, FeedKind.ANNOUNCEMENTS), pinned = tallPins(3))
        val target = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, author = LUNE, text = "cible")
        session.onLive(listOf(target))
        session.land(target.id)
        val t = open()
        t.frame()
        t.frame()
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).waiting.isEmpty())
        assertTrue(t.screen!!.showsTarget("post:${target.id}"))
    }

    // ---- Connection lost (9.12) ----

    @Test
    fun `Feed back takes away its Connexion perdue only, not a snackbar that replaced it`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, posts(2))
        t.frame()
        r.state(available = Availability.UNAVAILABLE)
        t.tick(4)
        assertTrue(t.texts.any { it.text.startsWith("feed.conn") })
        // A like refused meanwhile: its snackbar replaces Connexion perdue, and stays when Feed is back.
        session.toggleLike(session.feed(FeedKind.GLOBAL).posts.first())
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.UNAVAILABLE))
        t.tick(2)
        assertTrue(t.shows("snackbar.action_failed"))
        r.state()
        t.tick(2)
        assertTrue(t.shows("snackbar.action_failed"))
    }

    @Test
    fun `the session's clock runs while Feed is open, from its root screen's creation until it closes`() {
        val t = open()
        assertTrue(session.isAppOpen)
        t.closePhone()
        // Put away, Feed is still open on the phone: its timers go on.
        assertTrue(session.isAppOpen)
        t.close()
        assertFalse(session.isAppOpen)
    }

    @Test
    fun `Connexion perdue's Retry sends open at once, and Feed tries alone every 15 seconds`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, posts(2))
        t.frame()
        r.state(available = Availability.UNAVAILABLE)
        t.tick(4)
        val action = t.texts.first { it.text == "error.retry" }
        val opens = r.sent.count { it == OpenPayload }
        t.click(action.x + 5f, action.y + 6f)
        assertEquals(opens + 1, r.sent.count { it == OpenPayload })
        t.clientTicks(300)
        assertEquals(opens + 2, r.sent.count { it == OpenPayload })
        // Everything loaded stays readable.
        assertTrue(t.shows("p1"))
    }

    // ---- The view keeps still (9.12, art direction 9.5) ----

    private val shown = posts(10, FeedKind.ANNOUNCEMENTS)

    /** Annonces with p1 to p10, scrolled 300 dp (p3 cut at the top); returns where p5's text is drawn. */
    private fun scrolledAnnonces(t: PhoneTester): Float {
        r.state(ANNOUNCER)
        answer(FeedKind.ANNOUNCEMENTS, shown)
        t.frame()
        session.feed(FeedKind.ANNOUNCEMENTS).scroll.offset = 300f
        t.frame()
        return t.yOf("p5")
    }

    @Test
    fun `a post pinned from below joins the group on top without moving what the player reads`() {
        val t = open()
        val y = scrolledAnnonces(t)
        // 18 dp taller in the group.
        session.onPins(listOf(Pin(shown[8].id, NOW)))
        t.frame()
        assertEquals(y, t.yOf("p5"))
    }

    @Test
    fun `a post missed while away, merged above the view after a reset, doesn't move what the player reads`() {
        val t = open()
        val y = scrolledAnnonces(t)
        session.onReset()
        t.tick()
        val missed = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 25, author = LUNE, text = "missed")
        answer(FeedKind.ANNOUNCEMENTS, listOf(shown[0], shown[1], missed) + shown.drop(2))
        t.frame()
        assertNotNull(session.post(missed.id))
        assertEquals(y, t.yOf("p5"))
    }

    @Test
    @Disabled(
        "Defect: FeedTabView.syncRows removes the cards that left the list before it takes the row to keep in place, " +
            "so the list has already closed up when the anchor is measured: a post deleted above the view makes " +
            "what the player reads jump up by that card's height (101 dp here).",
    )
    fun `a post deleted above the view doesn't move what the player reads`() {
        val t = open()
        val y = scrolledAnnonces(t)
        session.onRemoved(listOf(RemovedItem(shown[0].id, FeedKind.ANNOUNCEMENTS, null)))
        t.frame()
        assertEquals(y, t.yOf("p5"))
    }

    @Test
    @Disabled(
        "Defect: FeedSession.show(null), called when the screen is hidden (a thread pushed over it, or the phone put " +
            "away), trims the list to 200 posts. A player reading post 221 who puts the phone away comes back to a list " +
            "that no longer holds it, the view jumped to the end of what is left.",
    )
    fun `putting the phone away deep in a long list keeps the player's place`() {
        r.state()
        val all = posts(230)
        r.firstPage(FeedKind.GLOBAL, all.take(50), next = "c1")
        for (page in 1..4) r.nextPage(FeedKind.GLOBAL, all.drop(50 * page).take(50), next = "c${page + 1}")
        val t = open(FeedKind.GLOBAL)
        session.feed(FeedKind.GLOBAL).scroll.offset = 220 * 101f
        t.frame()
        assertTrue(t.shows("p221"))
        t.closePhone()
        t.openPhone()
        t.frame()
        assertTrue(t.shows("p221"))
    }
}

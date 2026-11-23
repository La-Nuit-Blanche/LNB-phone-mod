package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.PLAYER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/**
 * Compose (art direction 9.8, 9.11, flows F-11 to F-14), opened from the feed's buttons, and every answer
 * `docs/feed-app.md` 5.3 gives a post. Publier is at x 156..244, y 6..38; the text area at y 96; the anonymous row
 * 12 dp under the area (196 dp for a player, 152 for an announcer); the destination at y 316.
 */
class ComposeFlowTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null
    private val draft get() = session.compose

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    /** Feed on [tab] for [state], loaded with [posts] (and [pinned]); nothing opened yet. */
    private fun feed(
        tab: FeedKind,
        state: PlayerState = PLAYER,
        posts: List<PostView> = listOf(post(tab, text = "ancien", author = LUNE)),
        pinned: List<PostView> = emptyList(),
    ): PhoneTester {
        val t = PhoneTester(feedApp(session, tab)).also { tester = it }
        r.state(state)
        r.answerFeed(tab, posts, pinned)
        t.frame()
        return t
    }

    /** The compose button at (196, 310), 48 dp. */
    private fun PhoneTester.clickFab() {
        click(220f, 334f)
        tick(4)
        assertInstanceOf(ComposeScreen::class.java, screen)
    }

    private fun PhoneTester.clickPublish() = click(200f, 22f)

    private fun answer(outcome: Outcome, post: PostView? = null, retryAfter: Int? = null) =
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, outcome, post = post, retryAfter = retryAfter))

    private fun published(feed: FeedKind, text: String, anonymous: Boolean = false) =
        post(feed, minutesAgo = 0, author = CAMILLE, text = text, mine = true, anonymous = anonymous)

    // ---- Who sees what ----

    @Test
    fun `an announcer opening Compose from Global finds Global chosen, and the anonymous row under the shorter area`() {
        val t = feed(FeedKind.GLOBAL, ANNOUNCER)
        t.clickFab()
        assertEquals(FeedKind.GLOBAL, draft.destination)
        assertTrue(t.showsStart("tab.ann"))
        assertTrue(t.showsStart("tab.glo"))
        // The anonymous row, 12 dp under the 152 dp area.
        val row = t.texts.first { it.text == "compose.anonymous" }
        assertEquals(96f + 152f + 12f + 4f, row.y)
        t.type("hello")
        t.clickPublish()
        assertEquals(FeedKind.GLOBAL, r.last<CreatePostPayload>().feed)
    }

    @Test
    fun `a player never chooses a destination, whatever opened Compose, and posts on Global`() {
        val t = feed(FeedKind.GLOBAL)
        // Asked for Announcements (no button offers it to a player): Global still.
        (t.screen as FeedScreen).compose(FeedKind.ANNOUNCEMENTS)
        t.tick(4)
        assertEquals(FeedKind.GLOBAL, draft.destination)
        assertFalse(t.showsStart("tab."))
        val row = t.texts.first { it.text == "compose.anonymous" }
        assertEquals(96f + 196f + 12f + 4f, row.y)
        t.type("bonjour")
        t.clickPublish()
        assertEquals(FeedKind.GLOBAL, r.last<CreatePostPayload>().feed)
    }

    @Test
    fun `a player posts anonymously on Global`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.click(226f, 96f + 196f + 12f + 22f)
        assertTrue(draft.anonymous)
        t.click(128f, 150f)
        t.type("secret")
        t.clickPublish()
        assertTrue(r.last<CreatePostPayload>().anonymous)
    }

    @Test
    fun `the author row shows the username alone when there is no real name`() {
        val t = feed(FeedKind.GLOBAL, PLAYER.copy(me = FeedFixtures.STEVE))
        t.clickFab()
        val name = t.texts.first { it.text == "SteveBuilds" }
        // Centred on the face: line box y 59.
        assertEquals(52f + 7f, name.y)
        assertFalse(t.texts.any { it.text.startsWith("@") })
    }

    // ---- Counting ----

    @Test
    fun `Publier takes 1 to 280 characters, not 281 nor 280 and a space, and Ctrl+Enter the same`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        draft.text.text = "a".repeat(281)
        t.frame()
        assertTrue(t.shows("-1"))
        t.clickPublish()
        t.click(128f, 150f)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        draft.text.text = " " + "a".repeat(280)
        t.frame()
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertTrue(r.all<CreatePostPayload>().isEmpty())

        draft.text.text = "a"
        t.frame()
        assertTrue(t.shows("1/280"))
        draft.text.text = "a".repeat(280)
        t.frame()
        assertTrue(t.shows("280/280"))
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertEquals(280, r.last<CreatePostPayload>().text.length)
    }

    @Test
    fun `Enter makes a new line and doesn't publish`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("un")
        t.key(GLFW.GLFW_KEY_ENTER)
        t.type("deux")
        assertTrue(r.all<CreatePostPayload>().isEmpty())
        assertEquals("un\ndeux", draft.text.text)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertEquals("un\ndeux", r.last<CreatePostPayload>().text)
    }

    // ---- Sending ----

    @Test
    fun `while sending, the close button, the toggle and the destination do nothing, and a second click sends nothing`() {
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER)
        t.clickFab()
        t.type("annonce")
        t.clickPublish()
        assertTrue(draft.sending)
        t.clickPublish()
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        t.click(22f, 22f)
        t.tick(4)
        t.click(226f, 96f + 152f + 12f + 22f)
        t.click(186f, 332f)
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        assertEquals(1, r.all<CreatePostPayload>().size)
        assertFalse(draft.anonymous)
        assertEquals(FeedKind.ANNOUNCEMENTS, draft.destination)
    }

    @Test
    fun `an announcer posting from Global to Announcements comes back on Announcements, which loads with the post`() {
        val t = feed(FeedKind.GLOBAL, ANNOUNCER)
        t.clickFab()
        t.click(70f, 332f)
        assertEquals(FeedKind.ANNOUNCEMENTS, draft.destination)
        t.click(128f, 150f)
        t.type("annonce")
        t.clickPublish()
        assertEquals(FeedKind.ANNOUNCEMENTS, r.last<CreatePostPayload>().feed)
        val mine = published(FeedKind.ANNOUNCEMENTS, "annonce")
        answer(Outcome.OK, mine)
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertEquals(FeedKind.ANNOUNCEMENTS, (t.screen as FeedScreen).tab)
        assertTrue(t.showsStart("compose.posted"))
        // Announcements wasn't loaded: it loads, and the post is in it.
        assertTrue(r.all<LoadFeedPayload>().any { it.feed == FeedKind.ANNOUNCEMENTS })
        r.answerFeed(FeedKind.ANNOUNCEMENTS, listOf(mine))
        t.frame()
        assertTrue(t.shows("annonce"))
    }

    @Test
    fun `an announcement published under three pins is scrolled into view, under the pinned group, highlighted`() {
        val pins = (1..3).map { post(FeedKind.ANNOUNCEMENTS, minutesAgo = 100L + it, author = LUNE, text = "p$it", pinnedAt = NOW.minusSeconds(it * 60L)) }
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, posts = listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 50, author = LUNE, text = "vieille")), pinned = pins)
        t.clickFab()
        t.type("neuve")
        t.clickPublish()
        answer(Outcome.OK, published(FeedKind.ANNOUNCEMENTS, "neuve"))
        t.tick(5)
        t.frame()
        val text = t.texts.first { it.text == "neuve" }
        // Fully in the list's area (y 44..370), under the three pins, and highlighted.
        assertTrue(text.y - 46f >= 44f && text.y - 46f + 101f <= 370f, "the post at ${text.y}")
        assertTrue(t.texts.first { it.text == "p3" }.y < text.y)
        assertTrue(t.highlighted("neuve", textOffset = 46f))
    }

    @Test
    fun `a post published while Global is scrolled down goes back to its top with the post first`() {
        val many = (1..10).map { post(minutesAgo = 10L + it, text = "p$it") }
        val t = feed(FeedKind.GLOBAL, posts = many)
        t.scroll(128f, 200f, -20f)
        t.tick(10)
        assertTrue(session.feed(FeedKind.GLOBAL).scroll.offset > 0f)
        t.clickFab()
        t.type("dernier")
        t.clickPublish()
        answer(Outcome.OK, published(FeedKind.GLOBAL, "dernier"))
        t.tick(5)
        t.frame()
        assertEquals(44f + 46f, t.texts.first { it.text == "dernier" }.y)
    }

    // ---- Every answer (feed-app.md 5.3) ----

    @Test
    fun `banned says so without Retry, keeps the draft, and the state that follows takes the compose button away`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("non")
        t.clickPublish()
        answer(Outcome.BANNED)
        t.tick()
        assertTrue(t.showsStart("snackbar.banned"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        assertEquals("non", draft.text.text)
        r.state(PLAYER.copy(banned = true))
        t.click(22f, 22f)
        t.tick(4)
        t.frame()
        assertFalse(session.canCompose(FeedKind.GLOBAL))
        t.clearSounds()
        t.click(220f, 334f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
    }

    @Test
    fun `not linked says LNB account needed, without Retry`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("qui")
        t.clickPublish()
        answer(Outcome.NOT_LINKED)
        t.tick()
        assertTrue(t.showsStart("guest.snackbar"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        assertEquals("qui", draft.text.text)
    }

    @Test
    fun `writing switched off says Writing is paused once the state says so, without Retry`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("pause")
        t.clickPublish()
        answer(Outcome.DISABLED)
        t.tick()
        assertTrue(draft.sending)
        r.state(PLAYER.copy(writing = false))
        t.tick()
        assertFalse(draft.sending)
        assertTrue(t.showsStart("snackbar.writing_off"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        assertEquals("pause", draft.text.text)
    }

    @Test
    fun `writing switched off while Compose is open refuses Publier with its words, sending nothing`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("pause")
        r.state(PLAYER.copy(writing = false))
        t.tick()
        t.clickPublish()
        assertTrue(r.all<CreatePostPayload>().isEmpty())
        assertTrue(t.showsStart("snackbar.writing_off"))
        assertEquals("pause", draft.text.text)
    }

    @Test
    fun `an invalid answer, or one lost on the way, is a failure to send whose Retry publishes again`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("encore")
        t.clickPublish()
        val first = r.last<CreatePostPayload>()
        answer(Outcome.INVALID)
        t.tick()
        assertTrue(t.showsStart("snackbar.send"))
        val retry = t.texts.last { it.text.startsWith("error.") }
        t.click(retry.x + 4f, retry.y + 6f)
        assertEquals(first.key, r.last<CreatePostPayload>().key)
        assertEquals(2, r.all<CreatePostPayload>().size)
        // No answer at all: after 30 seconds, the same failure.
        t.play(session, 601)
        assertFalse(draft.sending)
        assertTrue(t.showsStart("snackbar.send"))
    }

    @Test
    fun `too fast keeps Compose and its draft, and Publier sends again with the same key`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("vite")
        t.clickPublish()
        val first = r.last<CreatePostPayload>()
        answer(Outcome.RATE_LIMITED)
        t.tick()
        // The server's own limit gives no retryAfter: 1 second.
        assertTrue(t.shows("snackbar.too_fast 1"))
        t.clickPublish()
        assertEquals(first.key, r.last<CreatePostPayload>().key)
    }

    // ---- Cancel and the draft ----

    @Test
    fun `cancel keeps the text and the toggle, and the published post clears both`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("plus tard")
        t.click(226f, 96f + 196f + 12f + 22f)
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        t.clickFab()
        assertTrue(t.shows("plus tard"))
        assertTrue(draft.anonymous)
        // The anonymous look of the author row, kept too.
        assertTrue(t.shows("compose.anon_sub"))
        t.clickPublish()
        answer(Outcome.OK, published(FeedKind.GLOBAL, "plus tard", anonymous = true))
        // The compose button is lifted while `Publié` shows: once it went.
        t.advance(4000)
        t.clickFab()
        assertTrue(t.shows("compose.placeholder"))
        assertFalse(draft.anonymous)
        assertTrue(t.shows("Camille R."))
    }

    @Test
    fun `an announcer's cancelled draft keeps the destination chosen when Compose opens again`() {
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER)
        t.clickFab()
        t.type("pour global")
        t.click(186f, 332f)
        assertEquals(FeedKind.GLOBAL, draft.destination)
        t.click(22f, 22f)
        t.tick(4)
        t.clickFab()
        assertEquals(FeedKind.GLOBAL, draft.destination)
    }

    @Test
    fun `an announcer who loses the right while Compose is open falls back to Global, the text and the toggle kept`() {
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER)
        t.clickFab()
        t.type("annonce")
        // The anonymous row under the announcer's 152 dp area.
        t.click(128f, 96f + 152f + 12f + 22f)
        assertTrue(draft.anonymous)
        assertEquals(FeedKind.ANNOUNCEMENTS, draft.destination)
        r.state(PLAYER)
        t.tick()
        // The destination control went, the destination is Global, the area has the player's 196 dp.
        assertFalse(t.showsStart("tab."))
        assertEquals(FeedKind.GLOBAL, draft.destination)
        assertFalse((t.screen as ComposeScreen).announcer)
        assertEquals(196f, (t.screen as ComposeScreen).area.height)
        assertEquals("annonce", draft.text.text)
        assertTrue(draft.anonymous)
        t.click(128f, 150f)
        t.clickPublish()
        val sent = r.last<CreatePostPayload>()
        assertEquals(FeedKind.GLOBAL, sent.feed)
        assertTrue(sent.anonymous)
        // Compose opened again as a player: no destination, Global.
        answer(Outcome.UNAVAILABLE)
        t.tick()
        t.click(22f, 22f)
        t.tick(4)
        (t.screen as FeedScreen).compose(FeedKind.ANNOUNCEMENTS)
        t.tick(4)
        assertFalse(t.showsStart("tab."))
        assertEquals(FeedKind.GLOBAL, draft.destination)
    }

    @Test
    fun `a post answered after the player left Feed doesn't close the next Compose opened`() {
        val t = feed(FeedKind.GLOBAL)
        t.clickFab()
        t.type("envoye")
        t.clickPublish()
        // The player goes home while it is sent: the app's screens close.
        t.close()
        answer(Outcome.OK, published(FeedKind.GLOBAL, "envoye"))
        val again = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        again.frame()
        again.click(220f, 334f)
        again.tick(5)
        assertInstanceOf(ComposeScreen::class.java, again.screen)
    }

    @Test
    fun `the compose button goes for a guest, a banned player and writing off, and comes back with the right`() {
        val t = feed(FeedKind.GLOBAL)
        for (blocked in listOf(FeedFixtures.GUEST, PLAYER.copy(banned = true), PLAYER.copy(writing = false))) {
            r.state(blocked)
            t.tick()
            t.click(220f, 334f)
            t.tick(4)
            assertInstanceOf(FeedScreen::class.java, t.screen, "$blocked")
        }
        r.state(PLAYER)
        t.tick()
        t.clickFab()
    }
}

package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneSound
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import java.time.ZoneOffset

/** Compose (art direction 9.8), opened from the feed as the compose button does, played without the game. */
class ComposeScreenTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    /** The feed on [tab], loaded with one post, then Compose from its button. */
    private fun compose(tab: FeedKind = FeedKind.GLOBAL, state: fr.bde_teknantes.lnb.phone.feed.model.PlayerState = FeedFixtures.PLAYER): PhoneTester {
        val t = PhoneTester { FeedScreen(session, strings, tab, fromHome = true, zone = ZoneOffset.UTC) }.also { tester = it }
        r.state(state)
        val request = r.all<LoadFeedPayload>().last { it.feed == tab }
        session.onPage(PagePayload(request.requestId, Outcome.OK, tab, emptyList(), listOf(post(tab, text = "ancien")), null))
        t.frame()
        (t.screen as FeedScreen).compose(tab)
        t.tick(4)
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        return t
    }

    @Test
    fun `a player's Compose has their name, the area focused, Publier off until there is text, no destination`() {
        val t = compose()
        assertTrue(t.shows("Camille R."))
        assertTrue(t.shows("@Cam_Roux"))
        assertTrue(t.shows("compose.placeholder"))
        assertTrue(t.shows("0/280"))
        assertTrue(t.shows("compose.anonymous"))
        assertFalse(t.showsStart("tab.ann"))
        assertTrue(t.screen!!.hasTextFocus)
        t.clearSounds()
        t.click(200f, 22f)
        assertEquals(listOf(PhoneSound.ERROR), t.sounds)
        // Spaces alone can't be sent either.
        t.type("   ")
        t.click(200f, 22f)
        assertTrue(r.all<CreatePostPayload>().isEmpty())
    }

    @Test
    fun `over 280 characters Publier is off, and the anonymous toggle changes the author row`() {
        val t = compose()
        session.compose.text.text = "a".repeat(285)
        t.frame()
        assertTrue(t.shows("-5"))
        t.click(200f, 22f)
        assertTrue(r.all<CreatePostPayload>().isEmpty())
        // The anonymous row: 12 dp under the 196 dp area.
        t.click(128f, 96f + 196f + 12f + 22f)
        assertTrue(session.compose.anonymous)
        t.frame()
        assertTrue(t.shows("post.anonymous"))
        assertTrue(t.shows("compose.anon_sub"))
        assertFalse(t.shows("Camille R."))
    }

    @Test
    fun `Ctrl+Enter publishes trimmed, and the feed shows the post first with Posted`() {
        val t = compose()
        t.type(" salut ")
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        val sent = r.last<CreatePostPayload>()
        assertEquals("salut", sent.text)
        assertEquals(FeedKind.GLOBAL, sent.feed)
        assertTrue(session.compose.sending)
        val published = post(FeedKind.GLOBAL, minutesAgo = 0, author = FeedFixtures.CAMILLE, text = "salut", mine = true)
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = published))
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("compose.posted"))
        assertTrue(t.shows("salut"))
        assertEquals(published.id, session.feed(FeedKind.GLOBAL).posts.first())
        assertTrue(PhoneSound.SENT in t.sounds)
        assertEquals("", session.compose.text.text)
    }

    @Test
    fun `a failure keeps Compose and its draft, and Retry publishes with the same key`() {
        val t = compose()
        t.type("bonjour")
        t.click(200f, 22f)
        val first = r.last<CreatePostPayload>()
        session.onResult(ResultPayload(first.requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        assertTrue(t.showsStart("snackbar.send"))
        assertEquals("bonjour", session.compose.text.text)
        t.click(220f, 342f)
        assertEquals(first.key, r.last<CreatePostPayload>().key)
    }

    @Test
    fun `a post refused as not allowed says Action impossible without Retry, and keeps the draft`() {
        val t = compose()
        t.type("non")
        t.click(200f, 22f)
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.NOT_ALLOWED))
        t.tick()
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        assertTrue(t.showsStart("snackbar.action"))
        assertFalse(t.showsStart("error.retry"))
        assertEquals("non", session.compose.text.text)
    }

    @Test
    fun `too fast says how long, without Retry`() {
        val t = compose()
        t.type("vite")
        t.click(200f, 22f)
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.RATE_LIMITED, retryAfter = 4))
        t.tick()
        assertTrue(t.shows("snackbar.too_fast 4"))
        assertFalse(t.shows("error.retry"))
    }

    @Test
    fun `the close button goes back and keeps the draft`() {
        val t = compose()
        t.type("plus tard")
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        (t.screen as FeedScreen).compose(FeedKind.GLOBAL)
        t.tick(4)
        assertTrue(t.shows("plus tard"))
    }

    @Test
    fun `an announcer chooses the destination, preselected, and the anonymous toggle stays`() {
        val t = compose(FeedKind.ANNOUNCEMENTS, ANNOUNCER)
        assertTrue(t.showsStart("tab.ann"))
        assertEquals(FeedKind.ANNOUNCEMENTS, session.compose.destination)
        // The anonymous row under the 152 dp area, then Global in the segmented control at y 316.
        t.click(128f, 96f + 152f + 12f + 22f)
        t.click(186f, 332f)
        assertEquals(FeedKind.GLOBAL, session.compose.destination)
        assertTrue(session.compose.anonymous)
        t.click(70f, 332f)
        t.click(128f, 150f)
        t.type("annonce")
        t.click(200f, 22f)
        val sent = r.last<CreatePostPayload>()
        assertEquals(FeedKind.ANNOUNCEMENTS, sent.feed)
        assertTrue(sent.anonymous)
    }
}

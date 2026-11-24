package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import java.time.ZoneOffset

/**
 * Where the keyboard goes while writing: a click on a disabled send button leaves it in the text, so Backspace trims
 * the text instead of closing the screen; a send that failed gives it back to the text.
 */
class WritingFocusTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun compose(): PhoneTester {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(post(text = "ancien")))
        t.frame()
        t.click(220f, 334f)
        t.tick(4)
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        return t
    }

    @Test
    fun `over 280, a click on the disabled Publier then Backspace trims the text and Compose stays`() {
        val t = compose()
        t.type("a".repeat(281))
        t.click(200f, 22f)
        assertTrue(t.screen!!.hasTextFocus)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        t.tick(4)
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        assertEquals("a".repeat(280), session.compose.text.text)
    }

    @Test
    fun `a failed post gives the keyboard back to the text`() {
        val t = compose()
        t.type("bonjour")
        t.click(200f, 22f)
        // The player clicks elsewhere while it is sent.
        t.click(128f, 60f)
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertTrue(t.screen!!.hasTextFocus)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        assertEquals("bonjou", session.compose.text.text)
    }

    @Test
    fun `a failed reply gives the keyboard back to the composer's text, and a disabled Reply keeps it there`() {
        val main = post(text = "fil")
        r.state()
        val t = PhoneTester { ThreadScreen(session, IDS, main.id, expand = true, zone = ZoneOffset.UTC) }.also { tester = it }
        r.answerThread(main, emptyList())
        t.frame()
        // Empty: Répondre is disabled, and a click on it leaves the text area focused.
        t.click(200f, 342f)
        assertTrue(t.screen!!.hasTextFocus)
        t.type("salut")
        t.click(200f, 342f)
        t.click(128f, 100f)
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertTrue(t.screen!!.hasTextFocus)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        assertEquals("salu", session.replyDraft(main.id).text.text)
    }
}

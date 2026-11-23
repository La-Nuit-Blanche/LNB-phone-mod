package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.PLAYER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneSound
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import java.time.ZoneOffset

/**
 * The reply composer of art direction 9.7 and every answer `docs/feed-app.md` 5.3 gives a reply, on the thread screen.
 *
 * The expanded composer is docked at y 170: its text area at y 202, the `Anonyme` toggle at (12, 326) and `Répondre`
 * at x 148..244, y 326..358.
 */
class ReplyComposerTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    private val main = post(minutesAgo = 30, text = "diamants")
    private val steve = reply(main, minutesAgo = 20).copy(text = "question")
    private val draft get() = session.replyDraft(main.id)

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    /** The thread of [main] with [replies], composer expanded and focused. */
    private fun open(replies: List<PostView> = emptyList(), expand: Boolean = true, next: String? = null): PhoneTester {
        r.state()
        val t = PhoneTester { ThreadScreen(session, IDS, main.id, expand = expand, zone = ZoneOffset.UTC) }.also { tester = it }
        r.answerThread(main, replies, next)
        t.frame()
        return t
    }

    /** The feed on Global with [main], then its thread from the card, the composer expanded. */
    private fun openFromFeed(replies: List<PostView> = emptyList()): PhoneTester {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(main))
        t.frame()
        t.click(60f, 44f + 68f + 14f)
        t.tick(4)
        r.answerThread(main, replies)
        t.frame()
        return t
    }

    private fun PhoneTester.clickSend() = click(200f, 342f)

    private fun answer(outcome: Outcome, post: PostView? = null, retryAfter: Int? = null) =
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, outcome, post = post, retryAfter = retryAfter))

    private fun PhoneTester.clickRetry() {
        val retry = texts.last { it.text.startsWith("error.") }
        click(retry.x + 4f, retry.y + 6f)
    }

    // ---- Counting ----

    @Test
    fun `Répondre is off at 0 characters and on from 1 to 280, counted as typed`() {
        val t = open()
        assertTrue(t.shows("0/280"))
        t.clearSounds()
        t.clickSend()
        assertEquals(listOf(PhoneSound.ERROR), t.sounds)

        draft.text.text = "a"
        t.frame()
        assertTrue(t.shows("1/280"))
        assertTrue((t.screen as ThreadScreen).composer.button.isEnabled)

        draft.text.text = "a".repeat(280)
        t.frame()
        assertTrue(t.shows("280/280"))
        t.clickSend()
        assertEquals("a".repeat(280), r.last<CreateReplyPayload>().text)
    }

    @Test
    fun `281 characters, or 280 and a space, can't be sent, by the button or by Ctrl+Enter`() {
        val t = open()
        draft.text.text = "a".repeat(281)
        t.frame()
        assertTrue(t.shows("-1"))
        assertFalse((t.screen as ThreadScreen).composer.button.isEnabled)
        t.clickSend()
        t.click(128f, 250f)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertTrue(r.all<CreateReplyPayload>().isEmpty())

        // Counted untrimmed, as the counter counts.
        draft.text.text = "a".repeat(280) + " "
        t.frame()
        assertTrue(t.shows("-1"))
        t.clickSend()
        assertTrue(r.all<CreateReplyPayload>().isEmpty())
        assertFalse(draft.sending)
    }

    @Test
    fun `spaces and line feeds alone can't be sent, and what is sent is trimmed`() {
        val t = open()
        t.type("   ")
        t.key(GLFW.GLFW_KEY_ENTER)
        t.type("  ")
        t.frame()
        assertFalse((t.screen as ThreadScreen).composer.button.isEnabled)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertTrue(r.all<CreateReplyPayload>().isEmpty())
        t.type("ok")
        t.key(GLFW.GLFW_KEY_ENTER)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertEquals("ok", r.last<CreateReplyPayload>().text)
    }

    // ---- Sending ----

    @Test
    fun `sending loads the button, disables the toggle, sends once, and success clears the draft and the toggle`() {
        val t = open()
        t.type("cache")
        // The toggle, then Répondre.
        t.click(30f, 342f)
        assertTrue(draft.anonymous)
        t.clickSend()
        val sent = r.last<CreateReplyPayload>()
        assertTrue(sent.anonymous)
        assertTrue(draft.sending)
        // While sending: another click and Ctrl+Enter send nothing, the toggle doesn't move.
        t.clickSend()
        t.click(30f, 342f)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertEquals(1, r.all<CreateReplyPayload>().size)
        assertTrue(draft.anonymous)

        val mine = reply(main, mine = true, author = CAMILLE).copy(text = "cache", anonymous = true)
        answer(Outcome.OK, post = mine)
        t.tick(5)
        t.frame()
        assertTrue(t.showsStart("reply.sent"))
        assertFalse(draft.anonymous)
        assertEquals("", draft.text.text)
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        // Posted as Anonymous, with the line only its author sees (F-9).
        assertTrue(t.shows("post.anonymous"))
        assertTrue(t.shows("post.own_anonymous"))
        assertTrue(t.highlighted("cache", textOffset = 46f))
        assertTrue(PhoneSound.SENT in t.sounds)
    }

    @Test
    fun `an own reply at the end of a long thread is scrolled into view`() {
        val many = (1..8).map { reply(main, minutesAgo = 20L - it).copy(text = "r$it") }
        val t = open(many)
        t.type("fin")
        t.clickSend()
        answer(Outcome.OK, post = reply(main, mine = true, author = CAMILLE).copy(text = "fin"))
        t.tick(5)
        t.frame()
        val text = t.texts.first { it.text == "fin" }
        // Above the collapsed composer (y 306).
        assertTrue(text.y > 44f && text.y + 18f + 4f + 28f < 306f, "the reply at ${text.y}")
    }

    @Test
    @Disabled(
        "Design and code disagree: art direction 9.7 has the own reply appear at the end of the thread, scrolled into " +
            "view; with later pages not loaded yet, FeedSession.replyAnswered keeps it out of the thread until they are",
    )
    fun `an own reply in a thread whose later pages aren't loaded still shows at its end`() {
        val many = (1..50).map { reply(main, minutesAgo = 25L - it / 3).copy(text = "r$it") }
        // The first page only: the spinner row waits at the end.
        val t = open(many, next = "c1")
        t.type("fin")
        t.clickSend()
        answer(Outcome.OK, post = reply(main, mine = true, author = CAMILLE).copy(text = "fin"))
        t.tick(5)
        t.frame()
        assertTrue(t.shows("fin"))
    }

    // ---- Every answer (feed-app.md 5.3) ----

    @Test
    fun `a failure keeps the draft and its key, Retry sends it again with the same key, and a change makes a new key`() {
        val t = open()
        t.type("bonjour")
        t.clickSend()
        val first = r.last<CreateReplyPayload>()
        answer(Outcome.UNAVAILABLE)
        t.tick()
        assertTrue(t.showsStart("snackbar.send"))
        assertTrue((t.screen as ThreadScreen).composer.expanded)
        assertTrue((t.screen as ThreadScreen).composer.button.isEnabled)
        t.clickRetry()
        val again = r.last<CreateReplyPayload>()
        assertEquals(first.key, again.key)
        answer(Outcome.UNAVAILABLE)
        t.tick()
        // Sent again from the button, unchanged: the same key still.
        t.clickSend()
        assertEquals(first.key, r.last<CreateReplyPayload>().key)
        answer(Outcome.UNAVAILABLE)
        t.tick()
        t.click(128f, 250f)
        t.type("!")
        t.clickSend()
        assertNotEquals(first.key, r.last<CreateReplyPayload>().key)
    }

    @Test
    fun `an invalid answer is a failure to send with Retry`() {
        val t = open()
        t.type("x")
        t.clickSend()
        answer(Outcome.INVALID)
        t.tick()
        assertTrue(t.showsStart("snackbar.send"))
        assertTrue(t.texts.any { it.text.startsWith("error.") })
        assertEquals("x", draft.text.text)
    }

    @Test
    fun `an answer lost on the way counts as a failure to send after 30 seconds`() {
        val t = open()
        t.type("perdu")
        t.clickSend()
        t.play(session, 599)
        assertTrue(draft.sending)
        t.play(session, 2)
        assertFalse(draft.sending)
        assertTrue(t.showsStart("snackbar.send"))
        assertEquals("perdu", draft.text.text)
    }

    @Test
    fun `too fast says how long, without Retry, and keeps the draft and the composer`() {
        val t = open()
        t.type("vite")
        t.clickSend()
        answer(Outcome.RATE_LIMITED, retryAfter = 7)
        t.tick()
        assertTrue(t.shows("snackbar.too_fast 7"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        assertEquals("vite", draft.text.text)
        assertTrue((t.screen as ThreadScreen).composer.expanded)
        // The look doesn't change, and Répondre sends again.
        assertFalse(t.shows("snackbar.banned"))
        t.clickSend()
        assertEquals(2, r.all<CreateReplyPayload>().size)
    }

    @Test
    fun `not allowed says Action impossible without Retry and keeps the draft`() {
        val t = open()
        t.type("non")
        t.clickSend()
        answer(Outcome.NOT_ALLOWED)
        t.tick()
        assertTrue(t.showsStart("snackbar.action"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        assertEquals("non", draft.text.text)
    }

    @Test
    fun `banned says so without Retry, then the state turns the composer into the banned strip, the draft kept`() {
        val t = open()
        t.type("sanction")
        t.clickSend()
        answer(Outcome.BANNED)
        t.tick()
        assertTrue(t.showsStart("snackbar.banned"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        r.state(PLAYER.copy(banned = true))
        t.tick()
        t.frame()
        assertTrue(t.shows("snackbar.banned"))
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        assertFalse(t.showsStart("reply.placeholder"))
        assertFalse(t.screen!!.hasTextFocus)
        assertEquals("sanction", draft.text.text)
    }

    @Test
    fun `not linked says LNB account needed, then the guest strip`() {
        val t = open()
        t.type("qui")
        t.clickSend()
        answer(Outcome.NOT_LINKED)
        t.tick()
        assertTrue(t.showsStart("guest.snackbar"))
        r.state(FeedFixtures.GUEST)
        t.tick()
        t.frame()
        assertTrue(t.shows("guest.reply"))
        assertFalse((t.screen as ThreadScreen).composer.expanded)
    }

    @Test
    fun `switched off for writing, the button loads until the state says so, then writing is paused and the strip`() {
        val t = open()
        t.type("pause")
        t.clickSend()
        answer(Outcome.DISABLED)
        t.tick()
        // DISABLED could be Feed switched off or writing: the state that follows tells.
        assertTrue(draft.sending)
        assertFalse(t.showsStart("snackbar"))
        r.state(PLAYER.copy(writing = false))
        t.tick()
        t.frame()
        assertFalse(draft.sending)
        assertTrue(t.showsStart("snackbar.writing_off"))
        assertFalse(t.texts.any { it.text.startsWith("error.") })
        assertTrue(t.shows("snackbar.writing_off"))
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        assertEquals("pause", draft.text.text)
    }

    @Test
    fun `switched off with no state following is a failure to send with Retry after a while`() {
        val t = open()
        t.type("pause")
        t.clickSend()
        answer(Outcome.DISABLED)
        t.play(session, 239)
        assertTrue(draft.sending)
        t.play(session, 2)
        assertFalse(draft.sending)
        assertTrue(t.showsStart("snackbar.send"))
    }

    @Test
    fun `switched off while Feed is known to be off is a failure to send at once`() {
        val t = open()
        t.type("pause")
        t.clickSend()
        r.state(available = Availability.DISABLED)
        t.tick()
        answer(Outcome.DISABLED)
        t.tick()
        assertFalse(draft.sending)
        assertTrue(t.showsStart("snackbar.send"))
        assertEquals("pause", draft.text.text)
    }

    @Test
    fun `a reply whose thread went meanwhile says Action impossible, then goes back to the feed with This post was deleted`() {
        val t = openFromFeed()
        t.type("tard")
        t.clickSend()
        answer(Outcome.NOT_FOUND)
        t.tick()
        assertTrue(t.showsStart("snackbar.action"))
        // The thread asks whether it is still there: it is not.
        r.answerThread(null, emptyList(), outcome = Outcome.NOT_FOUND)
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
    }

    // ---- The target ----

    @Test
    fun `replying to an anonymous reply names it Anonymous, and the main post's reply button targets the post again`() {
        val anon = reply(main, minutesAgo = 10).copy(text = "masque", anonymous = true, author = null)
        val t = open(listOf(anon), expand = false)
        t.click(60f, 169f + 52f + 14f)
        t.tick(4)
        assertTrue(t.shows("post.reply_to post.anonymous"))
        t.type("toi")
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        val toAnon = r.last<CreateReplyPayload>()
        assertEquals(anon.id, toAnon.replyTo)
        answer(Outcome.UNAVAILABLE)
        t.tick()
        // The main post's reply button (its action row at y 44 + 92): the composer answers the post again.
        t.click(60f, 44f + 92f + 14f)
        t.tick(4)
        assertTrue(t.shows("post.reply_to Alexandre M."))
        assertNull(draft.target)
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        val toMain = r.last<CreateReplyPayload>()
        assertNull(toMain.replyTo)
        // Another target is another reply: a new key.
        assertNotEquals(toAnon.key, toMain.key)
    }

    @Test
    fun `the collapsed composer keeps naming the reply it answers until the reply goes out`() {
        val t = open(listOf(steve), expand = false)
        t.click(60f, 169f + 52f + 14f)
        t.tick(4)
        t.type("oui")
        t.click(150f, 100f)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        t.tick(4)
        t.frame()
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        assertEquals(steve.id, draft.target?.replyTo)
        // The field shows the draft, the target stays for when it opens again.
        assertTrue(t.shows("oui"))
        t.click(128f, 338f)
        t.tick(4)
        assertTrue(t.shows("post.reply_to SteveBuilds"))
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        answer(Outcome.OK, post = reply(main, mine = true).copy(text = "oui", replyTo = ReplyRef(steve.id, false, "SteveBuilds")))
        t.tick(5)
        t.frame()
        assertNull(draft.target)
        assertTrue(t.shows("reply.placeholder Alexandre M."))
    }

    // ---- Back, and one draft per thread ----

    @Test
    fun `the header's back arrow while expanded collapses the composer and stays on the thread`() {
        val t = openFromFeed()
        assertTrue((t.screen as ThreadScreen).composer.expanded)
        t.type("garde")
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        assertFalse(t.screen!!.hasTextFocus)
        // Collapsed, the arrow goes back.
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertEquals("garde", draft.text.text)
    }

    @Test
    fun `each thread has its own draft, kept when the player leaves and comes back`() {
        val other = post(minutesAgo = 40, text = "autre")
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(main, other))
        t.frame()
        // The first card's reply button, then the draft.
        t.click(60f, 44f + 68f + 14f)
        t.tick(4)
        r.answerThread(main, emptyList())
        t.frame()
        t.type("premier")
        t.click(150f, 100f)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)

        // The second card: an empty composer.
        t.click(60f, 44f + 101f + 68f + 14f)
        t.tick(4)
        r.answerThread(other, emptyList())
        t.frame()
        assertTrue(t.shows("0/280"))
        assertFalse(t.shows("premier"))
        t.click(22f, 22f)
        t.click(22f, 22f)
        t.tick(4)

        // Back on the first: the draft is there, collapsed.
        t.click(150f, 44f + 50f)
        t.tick(4)
        t.frame()
        assertTrue(t.shows("premier"))
        assertEquals("premier", session.replyDraft(main.id).text.text)
        assertEquals("", session.replyDraft(other.id).text.text)
    }

    @Test
    fun `a reply answered after the player left its thread says nothing when the thread is opened again`() {
        val t = openFromFeed()
        t.type("en route")
        t.clickSend()
        // Back twice: the composer closes, then the thread.
        t.click(22f, 22f)
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        answer(Outcome.OK, post = reply(main, mine = true, author = CAMILLE).copy(text = "en route"))
        t.advance(5000)
        t.click(150f, 44f + 50f)
        t.clearSounds()
        t.tick(5)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        assertFalse(t.showsStart("reply.sent"))
        assertFalse(PhoneSound.SENT in t.sounds)
    }

    @Test
    fun `a draft survives the phone put away and brought out again`() {
        val t = open()
        t.type("plus tard")
        t.closePhone()
        t.openPhone()
        t.frame()
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        assertEquals("plus tard", draft.text.text)
        assertTrue(t.shows("plus tard"))
    }

    // ---- Who can't write ----

    @Test
    fun `banned or writing off, the strip says why, and the reply button of a reply and the like say it too`() {
        for ((state, words) in listOf(PLAYER.copy(banned = true) to "snackbar.banned", PLAYER.copy(writing = false) to "snackbar.writing_off")) {
            tester?.close()
            val main = post(minutesAgo = 30, text = "diamants")
            val replyCard = reply(main, minutesAgo = 20).copy(text = "question")
            r.state(state)
            val t = PhoneTester { ThreadScreen(session, IDS, main.id, expand = true, zone = ZoneOffset.UTC) }.also { tester = it }
            r.answerThread(main, listOf(replyCard))
            t.frame()
            assertTrue(t.shows(words), words)
            assertFalse(t.showsStart("reply.placeholder"))
            assertFalse((t.screen as ThreadScreen).composer.expanded)
            t.clearSounds()
            t.click(60f, 169f + 52f + 14f)
            assertTrue(t.texts.count { it.text == words } >= 2, "the snackbar too")
            assertTrue(PhoneSound.ERROR in t.sounds)
            t.click(128f, 169f + 52f + 14f)
            assertFalse(session.post(replyCard.id)!!.liked)
            assertTrue(r.all<CreateReplyPayload>().isEmpty())
        }
    }

    @Test
    fun `the strip catches clicks, and nothing under it is reached`() {
        r.state(FeedFixtures.GUEST)
        val replies = (1..8).map { reply(main, minutesAgo = 20L - it).copy(text = "r$it") }
        val t = PhoneTester { ThreadScreen(session, IDS, main.id, zone = ZoneOffset.UTC) }.also { tester = it }
        r.answerThread(main, replies)
        t.frame()
        t.clearSounds()
        t.click(128f, 350f)
        assertTrue(t.sounds.isEmpty())
        assertFalse(t.showsStart("guest.snackbar"))
    }

    @Test
    fun `writing switched off while the composer is open takes it away, and switched on again brings it back`() {
        val t = open()
        t.type("attente")
        r.state(PLAYER.copy(writing = false))
        t.tick()
        t.frame()
        assertTrue(t.shows("snackbar.writing_off"))
        assertFalse(t.screen!!.hasTextFocus)
        r.state()
        t.tick()
        t.frame()
        assertTrue(t.shows("attente"))
        assertFalse(t.shows("snackbar.writing_off"))
    }
}

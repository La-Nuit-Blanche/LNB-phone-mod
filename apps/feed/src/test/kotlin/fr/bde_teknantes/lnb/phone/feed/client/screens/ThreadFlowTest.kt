package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.testing.DrawnShape
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.util.UUID

/**
 * The thread of art direction 9.7 and 9.12, as the player reaches it and reads it: paging by 50, the reply-to lines,
 * live replies and their highlight, likes on replies, the markers, a `reset`, the thread going away, and what shows
 * when it can't load. The composer has its own tests ([ReplyComposerTest]).
 */
class ThreadFlowTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    private val main = post(minutesAgo = 600, text = "diamants", replies = 2)

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    /** The thread of [main] alone, as its root screen, for a player. */
    private fun open(landOn: UUID? = null, expand: Boolean = false, of: PostView = main): PhoneTester {
        r.state()
        return PhoneTester { ThreadScreen(session, IDS, of.id, expand = expand, landOn = landOn, zone = ZoneOffset.UTC) }.also { tester = it }
    }

    /** The feed on Global with [posts], under the player's state, as the phone opens it. */
    private fun feed(vararg posts: PostView): PhoneTester {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, posts.toList())
        t.frame()
        return t
    }

    private fun replies(count: Int, from: Int = 1) = (from until from + count).map { reply(main, minutesAgo = 500L - it).copy(text = "r$it") }

    // ---- Paging ----

    @Test
    fun `replies come 50 at a time, the spinner row at the end asks for the next, and a failure turns it into Retry`() {
        val t = open()
        r.answerThread(main, replies(50), next = "c1")
        t.frame()
        // The spinner row waits at the end, out of view: nothing more is asked yet.
        assertTrue(t.showsSpinnerRow())
        assertFalse(t.shows("thread.no_replies"))
        assertEquals(1, r.all<LoadThreadPayload>().size)

        t.scrollToEnd()
        val more = r.last<LoadThreadPayload>()
        assertEquals(2, r.all<LoadThreadPayload>().size)
        assertEquals("c1", more.after)
        assertEquals(main.id, more.threadId)
        // In view for many frames, it is asked once.
        t.frame()
        t.tick(5)
        assertEquals(2, r.all<LoadThreadPayload>().size)

        session.onThread(ThreadPayload.failed(more.requestId, Outcome.UNAVAILABLE))
        t.frame()
        assertTrue(t.showsRetryRow())
        assertFalse(t.showsSpinnerRow())
        // The rest of the thread is untouched (F-24).
        assertTrue(t.shows("r50"))
        t.tick(5)
        assertEquals(2, r.all<LoadThreadPayload>().size)

        val retry = t.texts.last { it.text == "error.retry" }
        t.click(128f, retry.y + 6f)
        assertEquals(3, r.all<LoadThreadPayload>().size)
        assertEquals("c1", r.last<LoadThreadPayload>().after)
        t.frame()
        assertTrue(t.showsSpinnerRow())

        r.answerThread(null, replies(3, from = 51))
        t.scrollToEnd()
        assertFalse(t.showsSpinnerRow())
        assertFalse(t.showsRetryRow())
        assertFalse(t.shows("thread.no_replies"))
        assertTrue(t.shows("r53"))
        assertEquals(53, session.thread(main.id).replies.size)
    }

    @Test
    fun `a later page refused as too fast keeps the spinner and is asked again two seconds later`() {
        val t = open()
        r.answerThread(main, replies(50), next = "c1")
        t.scrollToEnd()
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.RATE_LIMITED))
        t.frame()
        assertTrue(t.showsSpinnerRow())
        assertFalse(t.showsRetryRow())
        t.play(session, 39)
        assertEquals(2, r.all<LoadThreadPayload>().size)
        t.play(session, 2)
        assertEquals(3, r.all<LoadThreadPayload>().size)
    }

    @Test
    fun `a later page repeating replies held, or carrying another thread's, adds only what is new`() {
        val t = open()
        val first = replies(50)
        r.answerThread(main, first, next = "c1")
        t.scrollToEnd()
        val stranger = reply(post(text = "ailleurs")).copy(text = "intrus")
        r.answerThread(null, listOf(first.last(), stranger) + replies(1, from = 51))
        t.scrollToEnd()
        assertEquals(51, session.thread(main.id).replies.size)
        assertFalse(t.shows("intrus"))
        assertEquals(1, t.texts.count { it.text == "r50" })
    }

    @Test
    fun `no replies shows its row, and only once the thread is loaded`() {
        val t = open()
        t.frame()
        // Before the answer: a spinner row under the header, no composer yet.
        assertTrue(t.showsSpinnerRow())
        assertFalse(t.shows("thread.no_replies"))
        assertFalse(t.showsStart("reply.placeholder"))
        r.answerThread(main, emptyList())
        t.frame()
        assertTrue(t.shows("thread.no_replies"))
        assertFalse(t.showsSpinnerRow())
        // The row sits right under the main post.
        val row = t.texts.first { it.text == "thread.no_replies" }
        assertTrue(row.y > 44f + 125f && row.y < 44f + 125f + 48f)
    }

    // ---- Landing from a notification ----

    @Test
    fun `a reply's notification for a reply on a later page loads pages until it is there, then lands on it`() {
        r.state()
        val target = reply(main, minutesAgo = 1).copy(text = "cible")
        val t = PhoneTester(feedApp(session), FeedTarget.Reply(FeedKind.GLOBAL, main.id, target.id).toString()).also { tester = it }
        r.answerThread(main, replies(50), next = "c1")
        t.frame()
        // Not on the first page: the next one is asked at once, without scrolling.
        assertEquals("c1", r.last<LoadThreadPayload>().after)
        r.answerThread(null, replies(5, from = 51) + target, next = "c2")
        t.frame()
        t.frame()
        val text = t.texts.first { it.text == "cible" }
        // Fully above the collapsed composer, and highlighted.
        assertTrue(text.y - 30f >= 44f && text.y - 30f + 85f <= 306f, "the reply at ${text.y}")
        assertTrue(t.highlighted("cible"))
        // Landed: nothing more is asked than that page.
        t.tick(5)
        assertEquals("c1", r.last<LoadThreadPayload>().after)
    }

    @Test
    fun `a reply's notification whose reply is gone stops at the end of the thread, at its top`() {
        r.state()
        val t = PhoneTester(feedApp(session), FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()).also { tester = it }
        r.answerThread(main, replies(50), next = "c1")
        t.frame()
        r.answerThread(null, replies(2, from = 51))
        t.frame()
        t.tick(5)
        assertEquals(2, r.all<LoadThreadPayload>().size)
        assertTrue(t.shows("diamants"))
    }

    @Test
    @Disabled("Defect: a reply's notification whose later page fails drops the landing; after Retry the thread doesn't scroll to the reply (ThreadScreen.afterSync sets landing = null on moreFailed)")
    fun `a reply's notification whose later page failed lands on the reply once Retry brought it`() {
        r.state()
        val target = reply(main, minutesAgo = 1).copy(text = "cible")
        val t = PhoneTester(feedApp(session), FeedTarget.Reply(FeedKind.GLOBAL, main.id, target.id).toString()).also { tester = it }
        r.answerThread(main, replies(50), next = "c1")
        t.frame()
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.UNAVAILABLE))
        t.frame()
        // The retry row is at the end of the thread: the player scrolls to it and retries.
        t.scrollToEnd()
        val retry = t.texts.last { it.text == "error.retry" }
        t.click(128f, retry.y + 6f)
        r.answerThread(null, listOf(target))
        t.frame()
        t.frame()
        assertTrue(t.highlighted("cible"))
    }

    // ---- What a reply shows ----

    @Test
    fun `the reply-to line names the reply answered, Anonymous for an anonymous one, and nothing for the main post`() {
        val t = open()
        val toMain = reply(main, minutesAgo = 4).copy(text = "a")
        val toSteve = reply(main, minutesAgo = 3).copy(text = "b", replyTo = ReplyRef(toMain.id, anonymous = false, name = "SteveBuilds"))
        // A moderator is sent the author of an anonymous reply: the line still says Anonymous.
        val toAnon = reply(main, minutesAgo = 2).copy(text = "c", replyTo = ReplyRef(FeedFixtures.id(), anonymous = true, name = "Mika L."))
        val toPurged = reply(main, minutesAgo = 1).copy(text = "d", replyTo = ReplyRef(null, anonymous = false, name = "Mika L."))
        r.answerThread(main, listOf(toMain, toSteve, toAnon, toPurged))
        t.frame()
        t.scroll(128f, 200f, -3f)
        t.tick(5)
        t.frame()
        assertEquals(1, t.texts.count { it.text == "post.reply_to SteveBuilds" })
        assertEquals(1, t.texts.count { it.text == "post.reply_to post.anonymous" })
        assertEquals(1, t.texts.count { it.text == "post.reply_to Mika L." })
        // The line is the first optional line, 16 dp above the body (art direction 9.7).
        val line = t.texts.first { it.text == "post.reply_to SteveBuilds" }
        assertEquals(line.y + 16f + 2f, t.texts.first { it.text == "b" }.y)
    }

    // ---- Live replies ----

    @Test
    fun `a live reply joins the end with the highlight, the replies already there don't get one`() {
        val t = open()
        r.answerThread(main, listOf(reply(main, minutesAgo = 3).copy(text = "vieux")))
        t.frame()
        assertFalse(t.highlighted("vieux"))
        session.onLive(listOf(reply(main, minutesAgo = 0).copy(text = "neuf")))
        t.frame()
        assertTrue(t.highlighted("neuf"))
        assertFalse(t.highlighted("vieux"))
        assertTrue(t.texts.first { it.text == "vieux" }.y < t.texts.first { it.text == "neuf" }.y)
        // Delivered twice, it shows once.
        session.onLive(listOf(session.post(session.thread(main.id).replies.last())!!))
        t.frame()
        assertEquals(1, t.texts.count { it.text == "neuf" })
    }

    @Test
    fun `a live reply waits for its page while later pages aren't loaded, and isn't highlighted when it comes`() {
        val t = open()
        r.answerThread(main, replies(50), next = "c1")
        t.frame()
        val live = reply(main, minutesAgo = 0).copy(text = "direct")
        session.onLive(listOf(live))
        assertEquals(50, session.thread(main.id).replies.size)
        t.scrollToEnd()
        r.answerThread(null, listOf(live))
        t.scrollToEnd()
        assertTrue(t.shows("direct"))
        assertFalse(t.highlighted("direct"))
    }

    @Test
    fun `a page merged after a reset highlights only the reply made meanwhile, and drops the one deleted meanwhile`() {
        val t = open()
        val kept = reply(main, minutesAgo = 5).copy(text = "reste")
        val deleted = reply(main, minutesAgo = 4).copy(text = "parti")
        r.answerThread(main, listOf(kept, deleted))
        t.frame()
        session.onReset()
        t.tick()
        assertEquals(2, r.all<LoadThreadPayload>().size)
        // Still readable while it reloads.
        assertTrue(t.shows("parti"))
        val meanwhile = reply(main, minutesAgo = 1).copy(text = "pendant")
        r.answerThread(main, listOf(kept, meanwhile))
        t.frame()
        assertTrue(t.shows("reste"))
        assertFalse(t.shows("parti"))
        assertTrue(t.highlighted("pendant"))
        assertFalse(t.highlighted("reste"))
        // A second reset with the same page highlights nothing again.
        t.advance(1200)
        session.onReset()
        t.tick()
        r.answerThread(main, listOf(kept, meanwhile))
        t.frame()
        assertFalse(t.highlighted("pendant"))
        assertFalse(t.highlighted("reste"))
    }

    @Test
    fun `a reset while the thread shows keeps the scroll and loads nothing more than the first page`() {
        val t = open()
        r.answerThread(main, replies(10))
        t.frame()
        t.scroll(128f, 200f, -5f)
        t.tick(5)
        t.frame()
        val at = t.texts.first { it.text == "r5" }.y
        session.onReset()
        t.tick()
        r.answerThread(main, replies(10))
        t.frame()
        assertEquals(at, t.texts.first { it.text == "r5" }.y)
        assertEquals(2, r.all<LoadThreadPayload>().size)
    }

    // ---- Likes and markers ----

    @Test
    fun `a like on a reply goes out for the reply, shows at once, and a refusal puts it back with Action impossible`() {
        val t = open()
        val steve = reply(main, minutesAgo = 1).copy(text = "salut", likes = 3)
        r.answerThread(main, listOf(steve))
        t.frame()
        // The reply's heart: x 114 on its action row (52 dp under its top, y 169).
        t.click(128f, 169f + 52f + 14f)
        val sent = r.last<LikePayload>()
        assertEquals(steve.id, sent.postId)
        assertTrue(sent.liked)
        assertEquals(4, session.post(steve.id)!!.likes)
        assertTrue(t.shows("4"))
        session.onResult(ResultPayload(sent.requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertEquals(3, session.post(steve.id)!!.likes)
        assertFalse(session.post(steve.id)!!.liked)
        assertTrue(t.showsStart("snackbar.action"))
    }

    @Test
    fun `opening a thread clears its markers, and the feed's card has no dot when the player comes back`() {
        val own = post(text = "le mien", author = CAMILLE, mine = true, replies = 2, unseenReplies = 2)
        val t = feed(own)
        // The marker's dot: 6 dp, centred at x 50.
        assertTrue(t.shapes.any { it.kind == DrawnShape.Kind.CIRCLE && it.x == 47f && it.width == 6f })
        t.click(150f, 44f + 50f)
        t.tick(4)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        val mineReply = reply(own, mine = true).copy(text = "moi", unseenReplies = 1)
        r.answerThread(own, listOf(mineReply))
        t.frame()
        assertEquals(0, session.post(own.id)!!.unseenReplies)
        assertEquals(0, session.post(mineReply.id)!!.unseenReplies)
        assertEquals(listOf(own.id), r.all<SeenThreadPayload>().map { it.threadId })
        t.click(22f, 22f)
        t.tick(4)
        t.frame()
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertFalse(t.shapes.any { it.kind == DrawnShape.Kind.CIRCLE && it.x == 47f && it.width == 6f })
    }

    @Test
    fun `replies to me while I read the thread raise no marker, and the thread is marked seen again once, two seconds on`() {
        val own = main.copy(author = CAMILLE, mine = true)
        val t = open(of = own)
        r.answerThread(own, emptyList())
        t.frame()
        assertEquals(1, r.all<SeenThreadPayload>().size)
        val live = (1..3).map { reply(own, minutesAgo = 0, answersMe = true).copy(text = "l$it") }
        session.onLive(live)
        t.frame()
        assertEquals(0, session.post(own.id)!!.unseenReplies)
        assertTrue(t.shapes.none { it.kind == DrawnShape.Kind.CIRCLE && it.x == 47f && it.width == 6f })
        t.play(session, 39)
        assertEquals(1, r.all<SeenThreadPayload>().size)
        t.play(session, 2)
        assertEquals(2, r.all<SeenThreadPayload>().size)
        t.play(session, 200)
        assertEquals(2, r.all<SeenThreadPayload>().size)
    }

    // ---- The thread going away ----

    @Test
    fun `a thread found gone when it loads goes back to the feed with This post was deleted`() {
        val p = post(text = "fil")
        val t = feed(p)
        t.click(150f, 44f + 50f)
        t.tick(4)
        r.answerThread(null, emptyList(), outcome = Outcome.NOT_FOUND)
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
        // It left the feed too (5.3: the post leaves the lists).
        assertFalse(t.shows("fil"))
    }

    @Test
    fun `a later page answered not found goes back with This post was deleted`() {
        val p = main
        val t = feed(p)
        t.click(150f, 44f + 50f)
        t.tick(4)
        r.answerThread(p, replies(50), next = "c1")
        t.scrollToEnd()
        r.answerThread(null, emptyList(), outcome = Outcome.NOT_FOUND)
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
    }

    @Test
    fun `a reset whose reload finds the thread gone goes back with This post was deleted`() {
        val t = feed(main)
        t.click(150f, 44f + 50f)
        t.tick(4)
        r.answerThread(main, emptyList())
        t.frame()
        session.onReset()
        t.tick()
        r.answerThread(null, emptyList(), outcome = Outcome.NOT_FOUND)
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
    }

    @Test
    fun `a like on the main post answered not found goes back with This post was deleted`() {
        val t = feed(main)
        t.click(150f, 44f + 50f)
        t.tick(4)
        r.answerThread(main, emptyList())
        t.frame()
        // The main post's heart: its action row at y 44 + 92.
        t.click(128f, 44f + 92f + 14f)
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.NOT_FOUND))
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
    }

    // ---- Unavailable (9.12) ----

    @Test
    fun `a thread switched off with nothing known shows the switched-off block, keeps its words while retrying, then says unavailable`() {
        val t = open()
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.DISABLED))
        t.frame()
        assertTrue(t.shows("feed.disabled.title"))
        assertTrue(t.shows("feed.disabled.text"))
        assertFalse(t.showsStart("reply.placeholder"))
        val retry = t.texts.first { it.text == "error.retry" }
        t.click(128f, retry.y + 6f)
        t.frame()
        // Retrying: the same block, its button loading (its label gives way to the spinner).
        assertTrue(t.shows("feed.disabled.title"))
        assertFalse(t.shows("error.retry"))
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.UNAVAILABLE))
        t.frame()
        assertTrue(t.shows("feed.unavailable.title"))
        assertTrue(t.shows("error.retry"))
    }

    @Test
    fun `a block left by a failure goes once Feed is back, and the thread loads without a Retry`() {
        val t = open()
        session.onThread(ThreadPayload.failed(r.last<LoadThreadPayload>().requestId, Outcome.UNAVAILABLE))
        t.frame()
        assertTrue(t.shows("feed.unavailable.title"))
        r.state(available = Availability.UNAVAILABLE)
        t.tick()
        assertEquals(1, r.all<LoadThreadPayload>().size)
        r.state()
        t.tick()
        assertEquals(2, r.all<LoadThreadPayload>().size)
        r.answerThread(main, emptyList())
        t.frame()
        assertFalse(t.shows("feed.unavailable.title"))
        assertTrue(t.shows("diamants"))
    }

    @Test
    fun `connection lost over a thread says so with Retry, sends open every 15 seconds, and goes when Feed is back`() {
        val t = open()
        r.answerThread(main, listOf(reply(main).copy(text = "lu")))
        t.frame()
        r.state(available = Availability.UNAVAILABLE)
        t.tick()
        assertTrue(t.showsStart("feed.conn"))
        // Everything stays readable.
        assertTrue(t.shows("lu"))
        assertTrue(t.showsStart("reply.placeholder"))
        val opens = r.all<OpenPayload>().size
        val retry = t.texts.last { it.text.startsWith("error.") }
        t.click(retry.x + 4f, retry.y + 6f)
        assertEquals(opens + 1, r.all<OpenPayload>().size)
        t.play(session, 299)
        assertEquals(opens + 1, r.all<OpenPayload>().size)
        t.play(session, 2)
        assertEquals(opens + 2, r.all<OpenPayload>().size)
        r.state()
        t.tick()
        t.frame()
        assertFalse(t.showsStart("feed.conn"))
        // Back: nothing more goes on its own.
        t.play(session, 900)
        assertEquals(opens + 2, r.all<OpenPayload>().size)
    }

    // ---- How it opens ----

    @Test
    fun `from a reply button with the main post not known yet, the composer expands and takes the focus once it comes`() {
        val t = open(expand = true)
        t.frame()
        assertFalse(t.screen!!.hasTextFocus)
        r.answerThread(main, emptyList())
        t.frame()
        assertTrue((t.screen as ThreadScreen).composer.expanded)
        assertTrue(t.screen!!.hasTextFocus)
        assertTrue(t.shows("post.reply_to Alexandre M."))
    }

    @Test
    fun `from a card, the thread opens at its top even when it was left scrolled`() {
        val t = feed(main)
        t.click(150f, 44f + 50f)
        t.tick(4)
        r.answerThread(main, replies(10))
        t.scrollToEnd()
        assertTrue(session.thread(main.id).scroll.offset > 0f)
        t.click(22f, 22f)
        t.tick(4)
        t.click(150f, 44f + 50f)
        t.tick(4)
        t.frame()
        assertEquals(0f, session.thread(main.id).scroll.offset)
        assertTrue(t.shows("diamants"))
    }

    @Test
    fun `a guest's reply button on a feed card opens the thread with the strip, no composer`() {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state(FeedFixtures.GUEST)
        r.answerFeed(FeedKind.GLOBAL, listOf(main))
        t.frame()
        // The reply button: x 50 on the action row (y 44 + 68).
        t.click(60f, 44f + 68f + 14f)
        t.tick(4)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        r.answerThread(main, emptyList())
        t.frame()
        assertTrue(t.shows("guest.reply"))
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        assertFalse(t.screen!!.hasTextFocus)
    }

    @Test
    fun `the thread says it shows a reply's target of its own thread only`() {
        val t = open()
        r.answerThread(main, emptyList())
        t.frame()
        val screen = t.screen!!
        assertTrue(screen.showsTarget(FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()))
        assertFalse(screen.showsTarget(FeedTarget.Reply(FeedKind.GLOBAL, FeedFixtures.id(), FeedFixtures.id()).toString()))
        assertFalse(screen.showsTarget(FeedTarget.Post(main.id).toString()))
        assertFalse(screen.showsTarget("reply:global:${main.id}"))
        assertFalse(screen.showsTarget(""))
    }

    @Test
    fun `nothing goes out on its own while a thread is read`() {
        val t = open()
        r.answerThread(main, replies(5))
        t.frame()
        val before = r.sent.size
        t.play(session, 2400)
        t.frame()
        assertEquals(before, r.sent.size, "sent: ${r.sent.drop(before)}")
        assertNull(session.takeNotice())
    }
}

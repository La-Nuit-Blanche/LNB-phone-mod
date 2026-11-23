package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Where a Feed notification lands (`docs/feed-app.md` 6.2, art direction 9.10): [FeedTargetScreens] for each target,
 * the stack it builds, back from it, and what each screen says to `showsTarget`.
 */
class FeedTargetScreensTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    @Test
    fun `an announcement's target opens Announcements alone, at its top, and lands on the announcement`() {
        val target = post(FeedKind.ANNOUNCEMENTS, author = LUNE, text = "annonce")
        // Left scrolled down earlier: a notification starts at the top.
        session.feed(FeedKind.ANNOUNCEMENTS).scroll.offset = 300f
        val screens = FeedTargetScreens.screens(session, IDS, FeedTarget.Post(target.id).toString(), ZoneOffset.UTC)
        assertEquals(1, screens.size)
        assertEquals(FeedKind.ANNOUNCEMENTS, (screens.single() as FeedScreen).tab)
        assertEquals(0f, session.feed(FeedKind.ANNOUNCEMENTS).scroll.offset)

        r.state()
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL), FeedTarget.Post(target.id).toString()).also { tester = it }
        assertEquals(1, t.screens.size)
        assertEquals(FeedKind.ANNOUNCEMENTS, (t.screen as FeedScreen).tab)
        r.answerFeed(FeedKind.ANNOUNCEMENTS, listOf(target))
        t.frame()
        t.frame()
        assertTrue(t.highlighted("annonce", textOffset = 46f))
        assertTrue(t.screen!!.showsTarget(FeedTarget.Post(target.id).toString()))
    }

    @Test
    fun `a reply's target in Announcements opens the thread over Announcements, and back shows that tab`() {
        r.state()
        val main = post(FeedKind.ANNOUNCEMENTS, author = LUNE, text = "annonce")
        val answer = reply(main).copy(text = "reponse")
        val target = FeedTarget.Reply(FeedKind.ANNOUNCEMENTS, main.id, answer.id).toString()
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL), target).also { tester = it }
        assertEquals(2, t.screens.size)
        assertEquals(FeedKind.ANNOUNCEMENTS, (t.screens[0] as FeedScreen).tab)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        assertEquals(main.id, (t.screen as ThreadScreen).threadId)
        // The feed under it isn't loaded before it shows.
        assertTrue(r.all<LoadFeedPayload>().isEmpty())
        r.answerThread(main, listOf(answer))
        t.frame()
        assertTrue(t.highlighted("reponse"))
        assertTrue(t.screen!!.showsTarget(target))

        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertEquals(FeedKind.ANNOUNCEMENTS, (t.screen as FeedScreen).tab)
        assertEquals(FeedKind.ANNOUNCEMENTS, r.last<LoadFeedPayload>().feed)
        // One `open` for the visit, from the thread, which showed first: the feed under it sends none.
        assertEquals(1, r.all<OpenPayload>().size)
        // The feed's own landing rules don't apply to a reply.
        assertFalse(t.screen!!.showsTarget(target))
    }

    @Test
    fun `targets Feed doesn't know open it on its first screen`() {
        val uuid = FeedFixtures.id()
        val unknown = listOf(
            "",
            "post:",
            "post:nope",
            "post:$uuid:extra",
            "reply:global:$uuid",
            "reply:events:$uuid:$uuid",
            "reply:global:$uuid:nope",
            "thread:$uuid",
            "x".repeat(200),
        )
        for (target in unknown) {
            assertTrue(FeedTargetScreens.screens(session, IDS, target, ZoneOffset.UTC).isEmpty(), target)
            tester?.close()
            val t = PhoneTester(feedApp(session), target).also { tester = it }
            assertEquals(1, t.screens.size, target)
            assertEquals(FeedKind.ANNOUNCEMENTS, (t.screen as FeedScreen).tab, target)
        }
        assertTrue(r.all<LoadThreadPayload>().isEmpty())
    }

    @Test
    fun `each screen says it shows a target only for its own kind`() {
        r.state()
        val main = post(text = "fil")
        val replyTarget = FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.answerFeed(FeedKind.GLOBAL, listOf(main))
        t.frame()
        // The feed never says so for a reply, nor for a Global post's id given as an announcement.
        assertFalse(t.screen!!.showsTarget(replyTarget))
        assertFalse(t.screen!!.showsTarget(FeedTarget.Post(main.id).toString()))
        (t.screen as FeedScreen).compose(FeedKind.GLOBAL)
        t.tick(4)
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        assertFalse(t.screen!!.showsTarget(replyTarget))
        assertFalse(t.screen!!.showsTarget(FeedTarget.Post(main.id).toString()))
    }

    @Test
    fun `a notification opened while its thread was already held lands on the reply without loading it again`() {
        r.state()
        val main = post(text = "fil")
        val replies = (1..8).map { reply(main, minutesAgo = 10L - it).copy(text = "r$it") }
        r.loadThreadAndAnswer(main, replies)
        val target = FeedTarget.Reply(FeedKind.GLOBAL, main.id, replies.last().id).toString()
        val t = PhoneTester(feedApp(session), target).also { tester = it }
        t.frame()
        t.frame()
        assertEquals(1, r.all<LoadThreadPayload>().size)
        val text = t.texts.first { it.text == "r8" }
        assertTrue(text.y - 30f >= 44f && text.y - 30f + 85f <= 306f, "the reply at ${text.y}")
        assertTrue(t.highlighted("r8"))
    }

    private fun FeedFixtures.Recorder.loadThreadAndAnswer(main: fr.bde_teknantes.lnb.phone.feed.model.PostView, replies: List<fr.bde_teknantes.lnb.phone.feed.model.PostView>) {
        session.loadThreadIfNeeded(main.id)
        answerThread(main, replies)
    }
}

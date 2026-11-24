package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A draft's outcome is told once, to the screen waiting for it: a Compose or a thread opened again while the draft is
 * still on its way is waiting, and tells it when it comes.
 */
class DraftOutcomeTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    @Test
    fun `a Compose opened again while the post is on its way tells its answer`() {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(post(text = "ancien")))
        t.frame()
        t.click(220f, 334f)
        t.tick(4)
        t.type("envoi")
        t.click(200f, 22f)
        // Home and back before the answer: a new Compose, sending still.
        t.close()
        val again = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        again.frame()
        again.click(220f, 334f)
        again.tick(4)
        assertInstanceOf(ComposeScreen::class.java, again.screen)
        assertTrue(session.compose.sending)
        session.onResult(
            ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.OK, post = post(minutesAgo = 0, author = CAMILLE, text = "envoi", mine = true)),
        )
        again.tick(5)
        assertInstanceOf(FeedScreen::class.java, again.screen)
        assertTrue(again.texts.any { it.text.startsWith("compose.posted") })
    }

    @Test
    fun `a thread opened again while the reply is on its way tells its answer`() {
        val main = post(text = "fil")
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(main))
        t.frame()
        // The card's reply button: the thread, its composer open.
        t.click(60f, 44f + 68f + 14f)
        t.tick(4)
        r.answerThread(main, emptyList())
        t.frame()
        t.type("route")
        t.click(200f, 342f)
        // Back to the feed, then the thread again from its card, before the answer.
        t.click(22f, 22f)
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        t.click(150f, 44f + 50f)
        t.tick(4)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.OK, post = reply(main, mine = true).copy(text = "route")))
        t.tick(3)
        assertTrue(t.texts.any { it.text.startsWith("reply.sent") })
    }
}

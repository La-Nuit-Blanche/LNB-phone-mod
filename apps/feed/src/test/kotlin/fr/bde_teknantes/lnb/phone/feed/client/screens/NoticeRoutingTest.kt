package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedNotice
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Which screen tells the session's notices: Compose only the connection's, the others waiting for the feed; a thread
 * that leaves leaves them for the feed; the feed tells them once a snackbar carried to it is gone; and `Post supprimé`
 * is told once.
 */
class NoticeRoutingTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun PhoneTester.showsStart(start: String) = texts.any { it.text.startsWith(start) }

    @Test
    fun `a pin refused for the limit while Compose is open asks over the feed, not over Compose`() {
        val t = PhoneTester(feedApp(session, FeedKind.ANNOUNCEMENTS)).also { tester = it }
        r.state(ANNOUNCER)
        val a = post(FeedKind.ANNOUNCEMENTS, text = "annonce", author = LUNE, canPin = true)
        r.answerFeed(FeedKind.ANNOUNCEMENTS, listOf(a))
        t.frame()
        (t.screen as FeedScreen).pinClicked(session.post(a.id)!!)
        t.click(220f, 334f)
        t.tick(4)
        assertInstanceOf(ComposeScreen::class.java, t.screen)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.PIN_LIMIT))
        t.tick(2)
        assertFalse((t.screen as ComposeScreen).isDialogOpen)
        assertFalse(t.showsStart("dialog.pin_limit"))
        // Back on the feed (within the notice's 5 seconds): there.
        t.click(22f, 22f)
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue((t.screen as FeedScreen).isDialogOpen)
    }

    @Test
    fun `Compose tells the connection lost, and leaves a refused like for the feed`() {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(post(text = "gg", likes = 3)))
        t.frame()
        t.click(128f, 44f + 68f + 14f)
        t.click(220f, 334f)
        t.tick(4)
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.UNAVAILABLE))
        r.state(available = Availability.UNAVAILABLE)
        t.tick(2)
        assertTrue(t.showsStart("feed.conn"))
        assertFalse(t.showsStart("snackbar.action"))
        assertTrue(session.takeNotice { it == FeedNotice.ActionFailed } != null)
    }

    @Test
    fun `after a publish, the feed tells what waited once Published is gone`() {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, listOf(post(text = "gg", likes = 3)))
        t.frame()
        t.click(128f, 44f + 68f + 14f)
        t.click(220f, 334f)
        t.tick(4)
        t.type("salut")
        t.click(200f, 22f)
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.UNAVAILABLE))
        session.onResult(
            ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.OK, post = post(minutesAgo = 0, author = CAMILLE, text = "salut", mine = true)),
        )
        t.tick(6)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.showsStart("compose.posted"))
        assertFalse(t.showsStart("snackbar.action"))
        // `Publié` held 3 seconds and faded (a frame each tick, as in the game): then the refused like.
        repeat(80) { t.tick() }
        assertTrue(t.showsStart("snackbar.action"))
    }

    @Test
    fun `a thread that leaves leaves the connection lost for the feed, told after This post was deleted`() {
        val main = post(text = "fil")
        r.state()
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL), FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString())
            .also { tester = it }
        r.answerThread(main, emptyList())
        t.frame()
        // In one tick: the connection drops, and the post is deleted by someone else.
        r.state(available = Availability.UNAVAILABLE)
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.showsStart("thread.deleted"))
        assertFalse(t.showsStart("feed.conn"))
        repeat(80) { t.tick() }
        assertTrue(t.showsStart("feed.conn"))
        assertTrue(session.connectionSnackbar?.isShowing == true)
    }

    @Test
    fun `the main post deleted from its thread with removed first says Post deleted once`() {
        val main = post(text = "fil", mine = true, canDelete = true, minutesAgo = 30)
        r.state()
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL), FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString())
            .also { tester = it }
        r.answerThread(main, emptyList())
        t.frame()
        (t.screen as ThreadScreen).more(main, (t.screen as ThreadScreen).composer)
        t.frame()
        t.clickStart("menu.delete")
        t.confirmDialog()
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        t.tick(4)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.showsStart("delete.post_done"))
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        assertNull(session.takeNotice { it is FeedNotice.Deleted })
    }

    @Test
    fun `takeNotice with a filter takes the oldest accepted and leaves the others in order`() {
        r.state()
        r.page(FeedKind.GLOBAL, listOf(post(text = "x")))
        r.state(available = Availability.UNAVAILABLE)
        r.state()
        // Connexion perdue, then back: two notices.
        assertTrue(session.takeNotice { it == FeedNotice.ConnectionBack } == FeedNotice.ConnectionBack)
        assertTrue(session.takeNotice() == FeedNotice.ConnectionLost)
        assertNull(session.takeNotice())
    }
}

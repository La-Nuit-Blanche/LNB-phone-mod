package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.server.PlayerLimits
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.util.UUID

/**
 * What the client sends while the player moves through threads (`docs/feed-app.md` 4.3, 8): nothing on a timer,
 * `seen_thread` when the thread shown changes or replies come to the player while they read, and never a request the
 * server's own limits would refuse in normal use. The server's [PlayerLimits] referees every payload at the game time
 * it went out.
 */
class ThreadTrafficTest {
    private val r = FeedFixtures.Recorder()
    private val session: FeedSession = r.session
    private var tester: PhoneTester? = null

    // The game's time, and the server's limits judging each payload when it goes.
    private var ticks = 0L
    private val referee = PlayerLimits { ticks * 50L }
    private val player = UUID.randomUUID()
    private var judged = 0
    private val refused = ArrayList<String>()

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun kindOf(payload: CustomPacketPayload): PlayerLimits.Kind? = when (payload) {
        is OpenPayload, is LoadFeedPayload, is LoadThreadPayload -> PlayerLimits.Kind.READ
        is CreatePostPayload, is CreateReplyPayload, is LikePayload, is PinPayload, is DeletePayload -> PlayerLimits.Kind.WRITE
        is SeenFeedPayload, is SeenThreadPayload -> PlayerLimits.Kind.SEEN
        else -> null
    }

    /** Has the server judge what went out since last time, at this time; the site answers at once. */
    private fun judge() {
        while (judged < r.sent.size) {
            val payload = r.sent[judged++]
            val kind = kindOf(payload) ?: continue
            if (referee.tryStart(player, kind)) referee.finish(player) else refused += "$payload at tick $ticks"
        }
    }

    /** [n] game ticks: the session's, the phone's, then the server's judgement. */
    private fun PhoneTester.step(n: Int = 1) = repeat(n) {
        // What the test's own actions sent went at the tick before this one.
        judge()
        ticks++
        session.tick()
        tick()
        judge()
    }

    private fun feed(posts: List<PostView>): PhoneTester {
        val t = PhoneTester(feedApp(session, FeedKind.GLOBAL)).also { tester = it }
        r.state()
        r.answerFeed(FeedKind.GLOBAL, posts)
        t.step()
        return t
    }

    /** Opens the thread of [post] from the feed shown, and answers its load if one went. */
    private fun PhoneTester.openThread(post: PostView, replies: List<PostView> = emptyList()) {
        val loads = r.all<LoadThreadPayload>().size
        (screen as FeedScreen).openThread(post, compose = false)
        if (r.all<LoadThreadPayload>().size > loads) r.answerThread(post, replies)
        step(4)
        assertInstanceOf(ThreadScreen::class.java, screen)
    }

    private fun PhoneTester.back() {
        click(22f, 22f)
        step(4)
        assertInstanceOf(FeedScreen::class.java, screen)
    }

    @Test
    fun `opening and closing the same thread quickly loads it once, and marks it seen within the server's allowance`() {
        val p = post(text = "fil")
        val t = feed(listOf(p))
        repeat(30) {
            t.openThread(p)
            t.back()
        }
        // 30 visits in 12 seconds.
        assertEquals(1, r.all<LoadThreadPayload>().size)
        val seen = r.all<SeenThreadPayload>().size
        assertTrue(seen in 1..7, "seen_thread sent $seen times")
        assertEquals(emptyList<String>(), refused)
    }

    @Test
    fun `opening many threads in a row stays within the server's limits`() {
        val posts = (1..20).map { post(text = "p$it", minutesAgo = it.toLong()) }
        val t = feed(posts)
        for (p in posts) {
            t.openThread(p, listOf(reply(p).copy(text = "r")))
            t.back()
        }
        assertEquals(20, r.all<LoadThreadPayload>().size)
        // Each thread is marked seen once, the marks spaced as the server allows.
        t.step(40 * 20)
        assertEquals(20, r.all<SeenThreadPayload>().size)
        assertEquals(posts.map { it.id }.toSet(), r.all<SeenThreadPayload>().map { it.threadId }.toSet())
        assertEquals(emptyList<String>(), refused)
    }

    @Test
    fun `a minute reading a thread that gets a reply to the player every second stays within the allowance`() {
        val own = post(text = "le mien", author = CAMILLE, mine = true)
        val t = feed(listOf(own))
        t.openThread(own)
        repeat(60) {
            session.onLive(listOf(reply(own, answersMe = true).copy(text = "r$it")))
            t.step(20)
        }
        val seen = r.all<SeenThreadPayload>().size
        // About one every 2 seconds, never more.
        assertTrue(seen in 25..32, "seen_thread sent $seen times")
        assertEquals(emptyList<String>(), refused)
    }

    @Test
    fun `a state that comes again, or replies to someone else, mark nothing more`() {
        val p = post(text = "fil")
        val t = feed(listOf(p))
        t.openThread(p)
        t.step(100)
        assertEquals(1, r.all<SeenThreadPayload>().size)
        repeat(5) {
            r.state()
            session.onLive(listOf(reply(p).copy(text = "x$it")))
            t.step(50)
        }
        assertEquals(1, r.all<SeenThreadPayload>().size)
    }

    @Test
    fun `nothing goes out on a timer while the player sits on a thread or on the feed`() {
        val p = post(text = "fil")
        val t = feed(listOf(p))
        t.openThread(p, listOf(reply(p).copy(text = "r")))
        t.step(100)
        val before = r.sent.size
        t.step(20 * 120)
        t.back()
        t.step(20 * 120)
        assertEquals(before, r.sent.size, "sent: ${r.sent.drop(before)}")
    }

    @Test
    fun `a seen mark sent as the allowance refills is still allowed when the earlier ones reached the server a tick late`() {
        r.state()
        val threads = (1..4).map { post(text = "t$it") }
        for (p in threads) {
            session.loadThreadIfNeeded(p.id)
            r.answerThread(p, emptyList())
        }
        // Four threads shown in a row: three marks go at once, the fourth waits for the allowance.
        for (p in threads) session.showThread(p.id)
        assertEquals(3, r.all<SeenThreadPayload>().size)
        // The three heard 50 ms late, as the network may.
        ticks = 1
        judge()
        while (r.all<SeenThreadPayload>().size < 4) {
            session.tick()
            ticks++
        }
        // The fourth heard as it was sent.
        ticks--
        judge()
        assertEquals(emptyList<String>(), refused)
    }

    @Test
    fun `the thread shown is never let go, however many threads the session takes on meanwhile`() {
        val main = post(text = "fil")
        r.state()
        val t = PhoneTester { ThreadScreen(session, IDS, main.id, zone = ZoneOffset.UTC) }.also { tester = it }
        r.answerThread(main, emptyList())
        t.step()
        val held = session.thread(main.id)
        val others = (1..(FeedSession.MAX_THREADS + 4)).map { FeedFixtures.id() }
        for (id in others) session.thread(id)
        assertSame(held, session.threadOrNull(main.id))
        // The eldest others went, the newest stay.
        assertEquals(null, session.threadOrNull(others.first()))
        assertTrue(session.threadOrNull(others.last()) != null)
        // The screen still follows the session: a live reply shows.
        session.onLive(listOf(reply(main).copy(text = "toujours")))
        t.step()
        t.frame()
        assertTrue(t.shows("toujours"))
    }

    @Test
    fun `a thread let go while the player was elsewhere is loaded again when opened`() {
        val posts = (1..(FeedSession.MAX_THREADS + 2)).map { post(text = "p$it", minutesAgo = it.toLong()) }
        val t = feed(posts)
        for (p in posts) {
            t.openThread(p)
            t.back()
        }
        assertEquals(posts.size, r.all<LoadThreadPayload>().size)
        t.openThread(posts.first())
        assertEquals(posts.size + 1, r.all<LoadThreadPayload>().size)
        t.back()
        assertEquals(emptyList<String>(), refused)
    }
}

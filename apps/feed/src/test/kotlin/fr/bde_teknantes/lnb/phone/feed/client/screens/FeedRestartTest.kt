package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * More than a page posted while the list was stale (`docs/feed-app.md` 8): a reader scrolled down keeps their place,
 * the new posts wait behind the pill, and the pill brings them in, highlighted, the list starting again from them.
 */
class FeedRestartTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    @Test
    fun `a reader scrolled down keeps their place, the pill counts the new posts, and its click starts the list from them`() {
        val t = PhoneTester { FeedScreen(session, IDS, FeedKind.GLOBAL, fromHome = true, zone = ZoneOffset.UTC) }.also { tester = it }
        r.state()
        val held = (20..31).map { post(minutesAgo = it.toLong(), text = "old$it") }
        r.answerFeed(FeedKind.GLOBAL, held, next = "c1")
        t.frame()
        t.scroll(128f, 200f, -5f)
        t.frame()
        val offset = session.feed(FeedKind.GLOBAL).scroll.offset
        val top = t.texts.first { it.text.startsWith("old") && it.y > 44f }.text
        assertTrue(offset > 0f)
        session.onReset()
        // The tab shown asks its first page again.
        t.tick()
        val fresh = (1..3).map { post(minutesAgo = it.toLong(), text = "new$it") }
        val request = r.all<LoadFeedPayload>().last()
        session.onPage(PagePayload(request.requestId, Outcome.OK, FeedKind.GLOBAL, emptyList(), fresh, "c9"))
        t.frame()
        t.tick(4)
        // Nothing moved under the reader; the pill says how many wait.
        assertEquals(offset, session.feed(FeedKind.GLOBAL).scroll.offset)
        assertEquals(top, t.texts.first { it.text.startsWith("old") && it.y > 44f }.text)
        assertTrue(t.texts.any { it.text == "feed.new_posts.other 3" })
        t.click(128f, 66f)
        repeat(10) { t.tick() }
        assertEquals(fresh.map { it.id }, session.feed(FeedKind.GLOBAL).posts)
        // A post with a handle: its text 46 dp under the top of its cell.
        assertTrue(t.highlighted("new1", textOffset = 46f))
    }
}

package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.GUEST
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.testing.PhoneSound
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Feed's root screen played without the game, from a session filled by hand. Strings show as their ID, arguments
 * after it; text measures 6.5 dp per character in body text, so posts here are short (one line).
 */
class FeedScreenTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }
    private var tester: PhoneTester? = null

    private fun open(tab: FeedKind = FeedKind.ANNOUNCEMENTS): PhoneTester =
        PhoneTester { FeedScreen(session, strings, tab, fromHome = true, zone = ZoneOffset.UTC) }.also { tester = it }

    /** Whether a line starting with [start] was drawn: the test font's wide letters cut the longer IDs. */
    private fun PhoneTester.showsStart(start: String) = texts.any { it.text.startsWith(start) }

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun answer(feed: FeedKind, posts: List<PostView>, pinned: List<PostView> = emptyList(), next: String? = null) {
        val request = r.all<LoadFeedPayload>().last { it.feed == feed }
        session.onPage(PagePayload(request.requestId, Outcome.OK, feed, pinned, posts, next))
    }

    private fun announcements(count: Int, minutesAgo: Long = 60) =
        (0 until count).map { post(FeedKind.ANNOUNCEMENTS, minutesAgo = minutesAgo + it, author = LUNE, text = "gg") }

    @Test
    fun `opening sends open once and loads Annonces, skeletons meanwhile`() {
        val t = open()
        assertEquals(1, r.sent.count { it == OpenPayload })
        assertEquals(FeedKind.ANNOUNCEMENTS, r.last<LoadFeedPayload>().feed)
        t.tick(5)
        assertEquals(1, r.sent.count { it == OpenPayload })
        assertFalse(t.shows("feed.end"))
        assertTrue(t.shapes.size >= 3)
    }

    @Test
    fun `Annonces shows the pinned group first, the names, the badge's owner and the end row`() {
        val t = open()
        r.state(ANNOUNCER.copy(unread = Unread(announcements = false, global = true)))
        val pinned = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 2 * 24 * 60, author = LUNE, text = "regles", pinnedAt = NOW)
        val other = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 24 * 60, author = FeedFixtures.STEVE, text = "saison")
        answer(FeedKind.ANNOUNCEMENTS, listOf(other), pinned = listOf(pinned))
        t.frame()
        assertTrue(t.shows("post.pinned"))
        assertTrue(t.shows("Lune G."))
        assertTrue(t.shows("@Admin_Lune"))
        // No real name: the username is the name, without a handle.
        assertTrue(t.shows("SteveBuilds"))
        assertFalse(t.shows("@SteveBuilds"))
        assertTrue(t.shows("time.days 2"))
        assertTrue(t.shows("feed.end"))
        val pinnedName = t.texts.first { it.text == "Lune G." }
        val otherName = t.texts.first { it.text == "SteveBuilds" }
        // The pinned cell moves its header down 18 dp; the next cell starts under it.
        assertEquals(44f + 28f, pinnedName.y)
        assertTrue(otherName.y > pinnedName.y + 100f)
    }

    @Test
    fun `anonymous posts show Anonyme, and the private line to their author and to moderators`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        val own = post(anonymous = true, mine = true, text = "a")
        val moderated = post(anonymous = true, author = FeedFixtures.ALEX, text = "b")
        val hidden = post(anonymous = true, author = null, text = "c")
        answer(FeedKind.GLOBAL, listOf(own, moderated, hidden))
        t.frame()
        assertEquals(3, t.texts.count { it.text.startsWith("post.an") })
        assertTrue(t.shows("post.own_anonymous"))
        assertTrue(t.texts.any { it.text.startsWith("post.mod_author") })
        assertFalse(t.shows("Alexandre M."))
    }

    @Test
    fun `empty Global offers to write, a guest gets other words, no button and no compose button`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, emptyList())
        t.frame()
        assertTrue(t.shows("empty.global.title"))
        assertTrue(t.shows("empty.global.text"))
        assertTrue(t.showsStart("empty.global.b"))
        r.state(GUEST)
        t.frame()
        assertTrue(t.shows("empty.global.text_guest"))
        assertFalse(t.showsStart("empty.global.b"))
    }

    @Test
    fun `a first page that can't load shows the block, switched off with its own words`() {
        val t = open()
        val request = r.last<LoadFeedPayload>()
        session.onPage(PagePayload.failed(request.requestId, Outcome.UNAVAILABLE, FeedKind.ANNOUNCEMENTS))
        t.frame()
        assertTrue(t.shows("feed.unavailable.title"))
        assertTrue(t.shows("error.retry"))
        // Retry: the button at the block's bottom, centred.
        val retry = t.texts.first { it.text == "error.retry" }
        val opens = r.sent.count { it == OpenPayload }
        t.click(128f, retry.y + 6f)
        assertEquals(opens + 1, r.sent.count { it == OpenPayload })
        r.state(available = Availability.DISABLED)
        t.frame()
        assertTrue(t.shows("feed.disabled.title"))
        assertTrue(t.shows("feed.disabled.text"))
    }

    @Test
    fun `a live post at the top of Global goes in at once, highlighted`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, listOf(post(minutesAgo = 10, text = "old")))
        t.frame()
        session.onLive(listOf(post(minutesAgo = 0, author = FeedFixtures.STEVE, text = "new")))
        t.frame()
        assertTrue(t.shows("new"))
        assertTrue(session.feed(FeedKind.GLOBAL).waiting.isEmpty())
        assertFalse(t.texts.any { it.text.startsWith("feed.new_posts") })
        // The highlight: an accent-soft fill over the cell.
        assertTrue(t.shapes.any { it.y == 44f && it.width == 256f && it.color == 0x1F3A8DF0 })
    }

    @Test
    fun `scrolled down, live posts wait behind the pill, and a click brings them in`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, (1..10).map { post(minutesAgo = it.toLong() * 10, text = "p$it") })
        t.frame()
        t.scroll(128f, 200f, -5f)
        session.onLive(listOf(post(minutesAgo = 0, text = "n1"), post(minutesAgo = 0, text = "n2")))
        t.frame()
        t.tick(4)
        assertTrue(t.shows("feed.new_posts.other 2"))
        assertEquals(2, session.feed(FeedKind.GLOBAL).waiting.size)
        t.click(128f, 66f)
        assertTrue(session.feed(FeedKind.GLOBAL).waiting.isEmpty())
        t.tick(10)
        assertTrue(t.shows("n1"))
        assertEquals(0f, session.feed(FeedKind.GLOBAL).scroll.offset)
    }

    @Test
    fun `in Annonces at the top, a new announcement under a tall pinned group waits`() {
        val t = open()
        r.state()
        val pins = announcements(3, minutesAgo = 3000).map { it.copy(pinnedAt = NOW, text = "x ".repeat(80).trim()) }
        answer(FeedKind.ANNOUNCEMENTS, announcements(2), pinned = pins)
        t.frame()
        val fresh = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, author = LUNE, text = "nouvelle")
        session.onLive(listOf(fresh))
        t.frame()
        // The pill slides down to y 52 in 150 ms.
        t.tick(4)
        assertTrue(t.shows("feed.new_announcements.one"))
        assertFalse(t.screen!!.showsTarget("post:${fresh.id}"))
        t.click(128f, 66f)
        t.tick(10)
        assertTrue(t.shows("nouvelle"))
        assertTrue(t.screen!!.showsTarget("post:${fresh.id}"))
    }

    @Test
    fun `a heart likes at once with its sound, a guest's only says why`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        val p = post(minutesAgo = 5, text = "gg", likes = 8)
        answer(FeedKind.GLOBAL, listOf(p))
        t.frame()
        // One line of text with a handle: the action row at y 44 + 68, the heart's disc at x 114.
        t.clearSounds()
        t.click(128f, 44f + 68f + 14f)
        assertTrue(r.last<LikePayload>().liked)
        assertTrue(t.shows("9"))
        assertEquals(listOf(PhoneSound.LIKE), t.sounds)

        r.state(GUEST)
        t.clearSounds()
        val likes = r.all<LikePayload>().size
        t.click(128f, 44f + 68f + 14f)
        assertEquals(likes, r.all<LikePayload>().size)
        assertTrue(t.shows("guest.snackbar"))
        assertEquals(listOf(PhoneSound.ERROR), t.sounds)
    }

    @Test
    fun `the connection lost over a list says so with Retry, and the list stays`() {
        val t = open(FeedKind.GLOBAL)
        r.state()
        answer(FeedKind.GLOBAL, listOf(post(text = "reste")))
        t.frame()
        r.state(available = Availability.UNAVAILABLE)
        t.tick(4)
        assertTrue(t.showsStart("feed.conn"))
        assertTrue(t.shows("reste"))
        r.state()
        t.tick()
        assertFalse(t.showsStart("feed.conn"))
    }

    @Test
    fun `the compose button shows where the player may write`() {
        val t = open()
        r.state()
        answer(FeedKind.ANNOUNCEMENTS, announcements(1))
        t.frame()
        val fabs = { t.shapes.count { it.width == 48f && it.height == 48f } }
        assertEquals(0, fabs())
        t.click(192f, 22f)
        answer(FeedKind.GLOBAL, listOf(post(text = "g")))
        t.frame()
        assertEquals(1, fabs())
    }
}

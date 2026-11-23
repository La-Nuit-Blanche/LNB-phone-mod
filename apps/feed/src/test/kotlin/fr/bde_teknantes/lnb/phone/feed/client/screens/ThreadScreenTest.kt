package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.GUEST
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneSound
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import java.time.ZoneOffset

/**
 * The thread, its reply composer and its deletes, played without the game from a session filled by hand. Strings show
 * as their ID with their arguments after it; body text measures 6.5 dp per character, so posts here are one line.
 *
 * The layout used below: the main post (Alexandre M., with a handle, one line) is 125 dp from y 44, its action row at
 * y 136; each reply (SteveBuilds, no handle, one line) is 85 dp, its action row 52 dp from its top.
 */
class ThreadScreenTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }
    private var tester: PhoneTester? = null

    private val main = post(minutesAgo = 1, text = "diamants", replies = 2)

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    /** The app: Feed on Global, and what notifications open. */
    private fun app() = PhoneApp(
        ResourceLocation.fromNamespaceAndPath("lnbphone_feed", "feed"),
        Component.literal("Feed"),
        AppIcon.Glyph(TileColor.AZURE, ResourceLocation.fromNamespaceAndPath("lnbphone_feed", "icon.png")),
        targetScreens = { FeedTargetScreens.screens(session, strings, it, ZoneOffset.UTC) },
    ) { FeedScreen(session, strings, FeedKind.GLOBAL, fromHome = true, zone = ZoneOffset.UTC) }

    /** The thread of [main] alone, as its root screen. */
    private fun open(expand: Boolean = false): PhoneTester {
        r.state()
        return PhoneTester { ThreadScreen(session, strings, main.id, expand = expand, zone = ZoneOffset.UTC) }.also { tester = it }
    }

    private fun answer(replies: List<PostView>, post: PostView? = main, next: String? = null, outcome: Outcome = Outcome.OK) {
        val request = r.last<LoadThreadPayload>()
        session.onThread(ThreadPayload(request.requestId, outcome, post, replies, next))
    }

    @Test
    fun `the main post has its full timestamp and no relative time, then the replies oldest first`() {
        val t = open()
        assertEquals(main.id, r.last<LoadThreadPayload>().threadId)
        val r1 = reply(main, minutesAgo = 1).copy(text = "premiere")
        val r2 = reply(main, minutesAgo = 0).copy(text = "seconde")
        answer(listOf(r1, r2))
        t.frame()
        assertTrue(t.shows("thread.title"))
        // 13:33 UTC: a minute before the fixtures' now.
        assertTrue(t.shows("time.full 13:33 time.date 12 time.month.11"))
        assertTrue(t.texts.first { it.text == "premiere" }.y < t.texts.first { it.text == "seconde" }.y)
        // The main post is 125 dp: the first reply's text 30 dp under its top.
        assertEquals(44f + 125f + 30f, t.texts.first { it.text == "premiere" }.y)
        // The relative time is only on the replies.
        assertEquals(2, t.texts.count { it.text == "time.now" || it.text.startsWith("time.minutes") })
        // Shown and loaded: the thread is marked seen.
        assertEquals(listOf(main.id), r.all<SeenThreadPayload>().map { it.threadId })
    }

    @Test
    fun `no replies shows its row, and the collapsed composer names the post's author`() {
        val t = open()
        answer(emptyList())
        t.frame()
        assertTrue(t.shows("thread.no_replies"))
        assertTrue(t.shows("reply.placeholder Alexandre M."))
        // The composer's hairline and field, docked at y 306.
        assertTrue(t.shapes.any { it.y == 318f && it.width == 232f && it.height == 40f })
    }

    @Test
    fun `a click on the composer expands it, focused, and sending a reply collapses it with the reply highlighted`() {
        val t = open()
        answer(emptyList())
        t.frame()
        t.click(128f, 338f)
        t.tick(4)
        assertTrue(t.screen!!.hasTextFocus)
        assertTrue(t.shows("post.reply_to Alexandre M."))
        assertTrue(t.shows("0/280"))
        // Répondre, disabled while empty: the error sound.
        t.clearSounds()
        t.click(200f, 342f)
        assertEquals(listOf(PhoneSound.ERROR), t.sounds)
        assertTrue(r.all<CreateReplyPayload>().isEmpty())

        // The click on the disabled button took the focus away: back in the text area.
        t.click(128f, 250f)
        t.type("  j arrive  ")
        t.click(200f, 342f)
        val sent = r.last<CreateReplyPayload>()
        assertEquals("j arrive", sent.text)
        assertNull(sent.replyTo)
        assertEquals(main.id, sent.threadId)
        assertTrue(session.replyDraft(main.id).sending)

        val mine = reply(main, mine = true).copy(text = "j arrive")
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = mine))
        t.tick(5)
        assertTrue(t.shows("reply.sent"))
        assertTrue(t.shows("j arrive"))
        assertFalse(t.screen!!.hasTextFocus)
        assertTrue(t.shows("reply.placeholder Alexandre M."))
        assertEquals("", session.replyDraft(main.id).text.text)
    }

    @Test
    fun `a failed reply keeps the draft and its key, and Retry sends it again`() {
        val t = open(expand = true)
        answer(emptyList())
        t.frame()
        assertTrue(t.screen!!.hasTextFocus)
        t.type("hello")
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        val first = r.last<CreateReplyPayload>()
        session.onResult(ResultPayload(first.requestId, Outcome.UNAVAILABLE))
        t.tick()
        assertTrue(t.showsStart("snackbar.send"))
        assertTrue(t.shows("post.reply_to Alexandre M."))
        assertEquals("hello", session.replyDraft(main.id).text.text)
        // The snackbar's Retry, right-aligned in it.
        t.click(220f, 342f)
        val again = r.last<CreateReplyPayload>()
        assertTrue(again.requestId != first.requestId)
        assertEquals(first.key, again.key)
    }

    @Test
    fun `replying to a reply targets it, and the posted reply says whom it answers`() {
        val t = open()
        val steve = reply(main, minutesAgo = 1).copy(text = "question")
        answer(listOf(steve))
        t.frame()
        // The reply's reply button: its action row 52 dp under its top (y 169).
        t.click(60f, 169f + 52f + 14f)
        t.tick(4)
        assertTrue(t.shows("post.reply_to SteveBuilds"))
        t.type("oui")
        t.key(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL)
        assertEquals(steve.id, r.last<CreateReplyPayload>().replyTo)

        val answer = reply(main, mine = true).copy(text = "oui", replyTo = ReplyRef(steve.id, anonymous = false, name = "SteveBuilds"))
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.OK, post = answer))
        t.tick(5)
        assertTrue(t.shows("post.reply_to SteveBuilds"))
        // Collapsed again, the composer is back to the main post.
        assertTrue(t.shows("reply.placeholder Alexandre M."))
    }

    @Test
    fun `back while expanded collapses the composer and keeps the draft`() {
        val t = open(expand = true)
        answer(emptyList())
        t.frame()
        t.type("brouillon")
        // The focus taken from the text area (a click on the main post), then Backspace: back.
        t.click(150f, 100f)
        assertFalse(t.screen!!.hasTextFocus)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        t.tick(4)
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        assertFalse((t.screen as ThreadScreen).composer.expanded)
        assertEquals("brouillon", session.replyDraft(main.id).text.text)
        // The collapsed field shows the start of the draft.
        assertTrue(t.shows("brouillon"))
    }

    @Test
    fun `a guest gets the strip, and a reply button on a reply says why`() {
        val t = open()
        r.state(GUEST)
        answer(listOf(reply(main).copy(text = "salut")))
        t.frame()
        assertTrue(t.shows("guest.reply"))
        assertFalse(t.showsStart("reply.placeholder"))
        t.clearSounds()
        t.click(60f, 169f + 52f + 14f)
        assertTrue(t.shows("guest.snackbar"))
        assertTrue(PhoneSound.ERROR in t.sounds)
        assertTrue(r.all<CreateReplyPayload>().isEmpty())
    }

    @Test
    fun `a thread that can't load with nothing known shows the block, and Retry asks again`() {
        r.state()
        val t = PhoneTester { ThreadScreen(session, strings, main.id, zone = ZoneOffset.UTC) }.also { tester = it }
        val request = r.last<LoadThreadPayload>()
        session.onThread(ThreadPayload.failed(request.requestId, Outcome.UNAVAILABLE))
        t.frame()
        assertTrue(t.shows("feed.unavailable.title"))
        val loads = r.all<LoadThreadPayload>().size
        val retry = t.texts.first { it.text == "error.retry" }
        t.click(128f, retry.y + 6f)
        assertEquals(loads + 1, r.all<LoadThreadPayload>().size)
    }

    @Test
    fun `replies that can't load under a main post known from the feed get the retry row`() {
        r.state()
        r.page(FeedKind.GLOBAL, listOf(main))
        val t = PhoneTester { ThreadScreen(session, strings, main.id, zone = ZoneOffset.UTC) }.also { tester = it }
        answer(emptyList(), post = null, outcome = Outcome.UNAVAILABLE)
        t.frame()
        assertFalse(t.shows("feed.unavailable.title"))
        assertTrue(t.shows("diamants"))
        val retry = t.texts.first { it.text == "error.retry" }
        // The 48 dp row right under the main post.
        assertTrue(retry.y > 44f + 125f && retry.y < 44f + 125f + 48f)
        val loads = r.all<LoadThreadPayload>().size
        t.click(128f, retry.y + 6f)
        assertEquals(loads + 1, r.all<LoadThreadPayload>().size)
    }

    @Test
    fun `a reply's notification opens the thread over its feed, at the reply, highlighted`() {
        r.state()
        val far = (1..8).map { reply(main, minutesAgo = 10L - it).copy(text = "r$it") }
        val target = FeedTarget.Reply(FeedKind.GLOBAL, main.id, far.last().id).toString()
        val t = PhoneTester(app(), target).also { tester = it }
        assertEquals(2, t.screens.size)
        assertInstanceOf(FeedScreen::class.java, t.screens[0])
        assertInstanceOf(ThreadScreen::class.java, t.screen)
        // Shown first, the thread makes the player a watcher.
        assertTrue(OpenPayload in r.sent)
        answer(far)
        t.frame()
        assertTrue(t.shows("r8"))
        assertTrue(t.screen!!.showsTarget(target))
        assertFalse(t.screen!!.showsTarget(FeedTarget.Reply(FeedKind.GLOBAL, FeedFixtures.id(), far.last().id).toString()))
        // Scrolled so that r8 is fully above the composer (y 306), and highlighted.
        val text = t.texts.first { it.text == "r8" }
        assertTrue(text.y < 306f && text.y > 44f)
        assertTrue(t.shapes.any { it.color == 0x1F3A8DF0 && it.width == 256f })
    }

    @Test
    fun `the main post deleted by someone else goes back with This post was deleted`() {
        r.state()
        val target = FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()
        val t = PhoneTester(app(), target).also { tester = it }
        answer(emptyList())
        t.frame()
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
    }

    @Test
    fun `deleting a reply goes through the menu, the dialog and its loading, then the cell folds away with Reply deleted`() {
        val t = open()
        val own = reply(main, mine = true).copy(text = "oups")
        answer(listOf(own))
        t.frame()
        // The reply's more button: 28 dp at (220, 5) in its cell.
        t.click(234f, 169f + 19f)
        val item = t.texts.first { it.text == "menu.delete" }
        t.click(item.x + 10f, item.y + 6f)
        assertTrue(t.showsStart("dialog.delete_reply.t"))
        assertTrue(t.showsStart("dialog.delete_reply.te"))
        t.confirmDialog()
        val sent = r.last<DeletePayload>()
        assertEquals(own.id, sent.postId)
        assertTrue(session.isDeleting(own.id))
        session.onResult(ResultPayload(sent.requestId, Outcome.OK))
        t.tick()
        assertTrue(t.shows("delete.reply_done"))
        // Folding: still drawn for 150 ms, then gone.
        t.tick(4)
        t.frame()
        assertFalse(t.shows("oups"))
        assertTrue(t.shows("thread.no_replies"))
    }

    @Test
    fun `deleting the main post from its thread goes back with Post deleted`() {
        val mineMain = main.copy(mine = true, canDelete = true)
        r.state()
        val target = FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()
        val t = PhoneTester(app(), target).also { tester = it }
        answer(emptyList(), post = mineMain)
        t.frame()
        (t.screen as ThreadScreen).more(mineMain, (t.screen as ThreadScreen).composer)
        t.frame()
        val item = t.texts.first { it.text == "menu.delete" }
        t.click(item.x + 10f, item.y + 6f)
        assertTrue(t.showsStart("dialog.delete_post"))
        t.confirmDialog()
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("delete.post_done"))
        assertFalse(t.shows("thread.deleted"))
    }
}

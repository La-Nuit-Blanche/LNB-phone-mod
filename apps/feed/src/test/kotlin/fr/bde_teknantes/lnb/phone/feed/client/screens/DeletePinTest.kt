package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.GUEST
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.PLAYER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCard
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Deleting (art direction 9.9, F-18, F-19) and pinning (9.4, F-15, F-16) from the feed and the thread: who gets the
 * buttons, the dialogs, the answers in every order, and `pins` arriving meanwhile.
 */
class DeletePinTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private var tester: PhoneTester? = null

    private val moderator = PLAYER.copy(rights = Rights(write = true, announce = true, moderate = true))

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun feed(tab: FeedKind, state: PlayerState, posts: List<PostView>, pinned: List<PostView> = emptyList()): PhoneTester {
        val t = PhoneTester(feedApp(session, tab)).also { tester = it }
        r.state(state)
        r.answerFeed(tab, posts, pinned)
        t.frame()
        return t
    }

    private fun thread(state: PlayerState, main: PostView, replies: List<PostView>): PhoneTester {
        r.state(state)
        val t = PhoneTester { ThreadScreen(session, IDS, main.id, zone = ZoneOffset.UTC) }.also { tester = it }
        r.answerThread(main, replies)
        t.frame()
        return t
    }

    private fun PhoneTester.card(text: String): PostCard = rows().filterIsInstance<PostCard>().first { it.current()?.text == text }

    private fun PhoneTester.clickOn(widget: fr.bde_teknantes.lnb.phone.api.client.ui.Widget) =
        click(widget.toViewportX(widget.width / 2f), widget.toViewportY(widget.height / 2f))

    /** The more button of the card showing [text], its menu's `Supprimer`, then the dialog's confirm button. */
    private fun PhoneTester.delete(text: String) {
        clickOn(card(text).more)
        val item = texts.first { it.text == "menu.delete" }
        click(item.x + 10f, item.y + 6f)
        confirmDialog()
    }

    private fun announcement(text: String, minutesAgo: Long = 10, pinnedAt: java.time.Instant? = null, canPin: Boolean = true) =
        post(FeedKind.ANNOUNCEMENTS, minutesAgo = minutesAgo, author = LUNE, text = text, pinnedAt = pinnedAt, canPin = canPin)

    // ---- Who gets the more button ----

    @Test
    fun `the more button shows on what the viewer's view lets them delete, writing switched off included`() {
        val own = post(text = "mien", mine = true, canDelete = true, minutesAgo = 1)
        val other = post(text = "autre", minutesAgo = 2)
        val t = feed(FeedKind.GLOBAL, PLAYER, listOf(own, other))
        assertTrue(t.card("mien").more.visible)
        assertFalse(t.card("autre").more.visible)
        // A view that says the viewer can delete, as a moderator's: the button, whatever the author.
        session.onLive(listOf(other.copy(canDelete = true)))
        t.frame()
        assertTrue(t.card("autre").more.visible)
        // Writing off, or a banned player: the view decides, since the site still deletes then (9.11). A guest's views
        // never allow it.
        for (state in listOf(PLAYER.copy(writing = false), PLAYER.copy(banned = true))) {
            r.state(state)
            t.tick()
            t.frame()
            assertTrue(t.card("mien").more.visible, "$state")
            assertTrue(t.card("autre").more.visible, "$state")
        }
        r.state(GUEST)
        session.onLive(listOf(own.copy(canDelete = false, mine = false), other.copy(canDelete = false)))
        t.tick()
        t.frame()
        assertFalse(t.card("mien").more.visible)
        assertFalse(t.card("autre").more.visible)
    }

    @Test
    fun `in a thread, the main post and the replies get the more button by the same rule`() {
        val main = post(text = "fil", canDelete = true, minutesAgo = 30)
        val own = reply(main, mine = true, minutesAgo = 2).copy(text = "moi")
        val other = reply(main, minutesAgo = 1).copy(text = "lui")
        val t = thread(moderator, main, listOf(own, other))
        assertTrue(t.card("fil").more.visible)
        assertTrue(t.card("moi").more.visible)
        assertFalse(t.card("lui").more.visible)
        // Writing switched off: the strip takes the composer's place, the more buttons stay.
        r.state(moderator.copy(writing = false))
        t.tick()
        t.frame()
        assertTrue(t.card("fil").more.visible)
        assertTrue(t.card("moi").more.visible)
        assertFalse(t.card("lui").more.visible)
        assertTrue(t.shows("snackbar.writing_off"))
    }

    // ---- The dialog ----

    @Test
    fun `the confirm button loads until the answer, cancel does nothing meanwhile, and success closes the dialog`() {
        val own = post(text = "mien", mine = true, canDelete = true)
        val t = feed(FeedKind.GLOBAL, PLAYER, listOf(own))
        t.clickOn(t.card("mien").more)
        t.clickStart("menu.delete")
        assertTrue(t.confirmLabelShown())
        t.confirmDialog()
        val screen = t.screen as FeedScreen
        assertTrue(screen.isDialogOpen)
        // Loading: the label gives way to the spinner, and cancel is off.
        assertFalse(t.confirmLabelShown())
        t.cancelDialog()
        t.tick(4)
        assertTrue(screen.isDialogOpen)
        assertEquals(1, r.all<DeletePayload>().size)
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        t.tick()
        assertFalse(screen.isDialogOpen)
    }

    @Test
    fun `a delete answered not found takes the post away without saying it was deleted`() {
        val own = post(text = "mien", mine = true, canDelete = true)
        val t = feed(FeedKind.GLOBAL, PLAYER, listOf(own, post(text = "autre", minutesAgo = 5)))
        t.delete("mien")
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.NOT_FOUND))
        t.tick()
        t.tick(6)
        t.frame()
        assertFalse(t.shows("mien"))
        assertFalse(t.showsStart("delete."))
        assertFalse((t.screen as FeedScreen).isDialogOpen)
    }

    @Test
    fun `a delete refused as too fast says so, and the post stays`() {
        val own = post(text = "mien", mine = true, canDelete = true)
        val t = feed(FeedKind.GLOBAL, PLAYER, listOf(own))
        t.delete("mien")
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.RATE_LIMITED, retryAfter = 3))
        t.tick(4)
        assertTrue(t.shows("snackbar.too_fast 3"))
        assertTrue(t.shows("mien"))
        assertTrue(t.card("mien").more.visible)
    }

    // ---- Folding, in either order ----

    @Test
    fun `a reply deleted in its thread folds away whether removed or result comes first, and says Reply deleted`() {
        for (removedFirst in listOf(true, false)) {
            tester?.close()
            val main = post(text = "fil", minutesAgo = 30)
            val own = reply(main, mine = true, minutesAgo = 3).copy(text = "oups")
            val after = reply(main, minutesAgo = 1).copy(text = "apres")
            val t = thread(PLAYER, main, listOf(own, after))
            val before = t.texts.first { it.text == "apres" }.y
            t.delete("oups")
            val removed = RemovedItem(own.id, FeedKind.GLOBAL, main.id)
            if (removedFirst) session.onRemoved(listOf(removed))
            session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
            if (!removedFirst) session.onRemoved(listOf(removed))
            t.tick()
            assertTrue(t.shows("oups"), "still drawn while it folds")
            assertTrue(t.showsStart("delete.reply_done"))
            t.tick()
            assertTrue(t.texts.first { it.text == "apres" }.y < before, "the next reply moves up as it folds")
            t.tick(4)
            t.frame()
            assertFalse(t.shows("oups"))
            assertEquals(before - 85f, t.texts.first { it.text == "apres" }.y)
        }
    }

    @Test
    fun `a delete refused leaves nothing behind, and deleted by someone else later the reply goes at once`() {
        val main = post(text = "fil", minutesAgo = 30)
        val own = reply(main, mine = true, minutesAgo = 3).copy(text = "oups")
        val t = thread(PLAYER, main, listOf(own))
        t.delete("oups")
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.UNAVAILABLE))
        t.tick(4)
        assertTrue(t.showsStart("snackbar.action"))
        assertTrue(t.shows("oups"))
        session.onRemoved(listOf(RemovedItem(own.id, FeedKind.GLOBAL, main.id)))
        t.frame()
        assertFalse(t.shows("oups"))
    }

    @Test
    fun `replies to a reply deleted keep their reply-to line`() {
        val main = post(text = "fil", minutesAgo = 30)
        val anon = reply(main, mine = true, minutesAgo = 3).copy(text = "masque", anonymous = true)
        val answer = reply(main, minutesAgo = 1).copy(text = "reponse", replyTo = ReplyRef(anon.id, anonymous = true, name = null))
        val t = thread(PLAYER, main, listOf(anon, answer))
        t.delete("masque")
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        t.tick()
        t.tick(6)
        t.frame()
        assertFalse(t.shows("masque"))
        assertTrue(t.shows("post.reply_to post.anonymous"))
        assertTrue(t.shows("reponse"))
    }

    @Test
    fun `a post deleted while the feed is scrolled to it folds, and the posts below close up with none lost`() {
        val posts = (1..6).map { post(text = "p$it", minutesAgo = it.toLong(), mine = it == 2, canDelete = it == 2) }
        val t = feed(FeedKind.GLOBAL, PLAYER, posts)
        session.feed(FeedKind.GLOBAL).scroll.offset = 60f
        t.frame()
        t.delete("p2")
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        session.onRemoved(listOf(RemovedItem(posts[1].id, FeedKind.GLOBAL, null)))
        t.tick()
        t.tick(8)
        t.frame()
        assertFalse(t.shows("p2"))
        val shown = t.texts.filter { it.text.matches(Regex("p\\d")) }
        assertEquals(listOf("p1", "p3", "p4"), shown.map { it.text }.take(3))
        // Laid out end to end: p3 right under p1 (101 dp each, text 46 dp under the top).
        val p1 = shown.first { it.text == "p1" }.y
        assertEquals(p1 + 101f, shown.first { it.text == "p3" }.y)
    }

    @Test
    fun `deleting the main post from its thread goes back with Post deleted, also when removed comes first`() {
        val main = post(text = "fil", mine = true, canDelete = true, minutesAgo = 30)
        r.state()
        val t = PhoneTester(feedApp(session), FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()).also { tester = it }
        r.answerThread(main, emptyList())
        t.frame()
        t.delete("fil")
        assertTrue(t.showsStart("dialog.delete_post"))
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("delete.post_done"))
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        t.tick(2)
        assertFalse(t.showsStart("thread.deleted"))
        assertFalse((t.screen as FeedScreen).isDialogOpen)
    }

    @Test
    fun `the main post deleted by someone else while the player's delete dialog is open goes back with This post was deleted`() {
        val main = post(text = "fil", mine = true, canDelete = true, minutesAgo = 30)
        r.state()
        val t = PhoneTester(feedApp(session), FeedTarget.Reply(FeedKind.GLOBAL, main.id, FeedFixtures.id()).toString()).also { tester = it }
        r.answerThread(main, emptyList())
        t.frame()
        t.clickOn(t.card("fil").more)
        t.clickStart("menu.delete")
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        t.tick(5)
        assertInstanceOf(FeedScreen::class.java, t.screen)
        assertTrue(t.shows("thread.deleted"))
        assertTrue(r.all<DeletePayload>().isEmpty())
    }

    // ---- Who gets the pin button ----

    @Test
    fun `the pin button shows on announcements for whoever may pin them, never on Global posts, whatever the view says`() {
        val a = announcement("annonce")
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(a))
        assertTrue(t.card("annonce").pin.visible)
        t.card("annonce").pin.let { assertFalse(it.active) }
        // Writing switched off: the site still pins, the button stays (9.11).
        r.state(ANNOUNCER.copy(writing = false))
        t.tick()
        t.frame()
        assertTrue(t.card("annonce").pin.visible)
        // A view that doesn't allow pinning (a guest's never does): no button.
        r.state(GUEST)
        session.onLive(listOf(a.copy(canPin = false)))
        t.tick()
        t.frame()
        assertFalse(t.card("annonce").pin.visible)
        r.state(ANNOUNCER)
        (t.screen as FeedScreen).showTab(FeedKind.GLOBAL)
        r.answerFeed(FeedKind.GLOBAL, listOf(post(text = "global", canPin = true)))
        t.frame()
        assertFalse(t.card("global").pin.visible)
    }

    @Test
    fun `in a thread, the pin button is on an announcement's main post and never on its replies`() {
        val a = announcement("annonce")
        val replyCanPin = reply(a, minutesAgo = 1).copy(text = "rep", canPin = true)
        val t = thread(ANNOUNCER, a, listOf(replyCanPin))
        assertTrue(t.card("annonce").pin.visible)
        assertFalse(t.card("rep").pin.visible)
    }

    @Test
    fun `a Global post's thread has no pin button even when its view says it can be pinned`() {
        val g = post(text = "global", canPin = true)
        val t = thread(ANNOUNCER, g, emptyList())
        assertFalse(t.card("global").pin.visible)
    }

    @Test
    fun `a Global post's pin button, should it show, sends nothing`() {
        val g = post(text = "global", canPin = true)
        val t = thread(ANNOUNCER, g, emptyList())
        (t.screen as ThreadScreen).pin(session.post(g.id)!!)
        t.frame()
        assertTrue(r.all<PinPayload>().isEmpty())
        assertNull(session.post(g.id)!!.pinnedAt)
    }

    // ---- Pinning ----

    @Test
    fun `pinning from the thread fills the button at once with the pinned line, Post pinned on OK, and unpinning says Post unpinned`() {
        val a = announcement("annonce")
        val t = thread(ANNOUNCER, a, emptyList())
        t.clickOn(t.card("annonce").pin)
        val sent = r.last<PinPayload>()
        assertTrue(sent.pinned)
        assertFalse(sent.replace)
        t.frame()
        assertTrue(t.card("annonce").pin.active)
        assertTrue(t.shows("post.pinned"))
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, pinned = listOf(Pin(a.id, NOW))))
        t.tick()
        assertTrue(t.showsStart("pin.done"))

        t.clickOn(t.card("annonce").pin)
        val unpin = r.last<PinPayload>()
        assertFalse(unpin.pinned)
        t.frame()
        assertFalse(t.card("annonce").pin.active)
        assertFalse(t.shows("post.pinned"))
        session.onResult(ResultPayload(unpin.requestId, Outcome.OK, pinned = emptyList()))
        t.tick()
        assertTrue(t.showsStart("pin.removed"))
    }

    @Test
    fun `a pin refused puts the post back in its date order with the button empty, and says Action impossible`() {
        val newer = announcement("neuve", minutesAgo = 5, canPin = false)
        val a = announcement("annonce", minutesAgo = 10)
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(newer, a))
        t.clickOn(t.card("annonce").pin)
        t.frame()
        // Shown at once on top of the list, in the pinned group.
        assertEquals(listOf(a.id), session.feed(FeedKind.ANNOUNCEMENTS).pinned)
        assertTrue(t.texts.first { it.text == "annonce" }.y < t.texts.first { it.text == "neuve" }.y)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.NOT_PINNABLE))
        t.tick()
        t.frame()
        assertTrue(session.feed(FeedKind.ANNOUNCEMENTS).pinned.isEmpty())
        assertFalse(t.card("annonce").pin.active)
        assertTrue(t.texts.first { it.text == "annonce" }.y > t.texts.first { it.text == "neuve" }.y)
        assertTrue(t.showsStart("snackbar.action"))
    }

    @Test
    fun `a pin answered PIN_LIMIT by the server puts the button back and asks to replace, and confirming replaces`() {
        val pins = (1..2).map { announcement("p$it", minutesAgo = 300L + it, pinnedAt = NOW.minusSeconds(it * 60L)) }
        val a = announcement("annonce", minutesAgo = 10)
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(a), pins)
        // Two pins known here: no dialog, sent as is.
        t.clickOn(t.card("annonce").pin)
        val first = r.last<PinPayload>()
        assertFalse(first.replace)
        // Another announcer pinned meanwhile: the server found 3.
        session.onResult(ResultPayload(first.requestId, Outcome.PIN_LIMIT, oldestPin = pins.last().id))
        t.tick()
        t.frame()
        assertFalse(t.card("annonce").pin.active)
        assertTrue(t.showsStart("dialog.pin_limit"))
        t.confirmDialog()
        val again = r.last<PinPayload>()
        assertTrue(again.pinned)
        assertTrue(again.replace)
        session.onResult(ResultPayload(again.requestId, Outcome.OK, pinned = listOf(Pin(a.id, NOW), Pin(pins[0].id, pins[0].pinnedAt!!))))
        t.tick()
        assertFalse((t.screen as FeedScreen).isDialogOpen)
        assertTrue(t.showsStart("pin.done"))
        assertEquals(listOf(a.id, pins[0].id), session.feed(FeedKind.ANNOUNCEMENTS).pinned)
    }

    @Test
    fun `a thread opened without the Announcements list learns the limit from the server, and cancelling sends nothing more`() {
        val a = announcement("annonce")
        val t = thread(ANNOUNCER, a, emptyList())
        t.clickOn(t.card("annonce").pin)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.PIN_LIMIT))
        t.tick()
        assertTrue(t.showsStart("dialog.pin_limit"))
        t.cancelDialog()
        t.tick(4)
        assertFalse((t.screen as ThreadScreen).isDialogOpen)
        assertEquals(1, r.all<PinPayload>().size)
        assertFalse(t.card("annonce").pin.active)
    }

    @Test
    fun `a fourth pin from the thread asks first when the group is known`() {
        val pins = (1..3).map { announcement("p$it", minutesAgo = 300L + it, pinnedAt = NOW.minusSeconds(it * 60L)) }
        val a = announcement("annonce")
        r.state(ANNOUNCER)
        r.page(FeedKind.ANNOUNCEMENTS, listOf(a), pins)
        val t = thread(ANNOUNCER, a, emptyList())
        t.clickOn(t.card("annonce").pin)
        assertTrue(r.all<PinPayload>().isEmpty())
        assertTrue(t.showsStart("dialog.pin_limit"))
        t.confirmDialog()
        assertTrue(r.last<PinPayload>().replace)
    }

    @Test
    fun `a pins payload during the click stands over the click's guess, and a failure then keeps the server's group`() {
        val other = announcement("autre", minutesAgo = 20)
        val a = announcement("annonce", minutesAgo = 10)
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(a, other))
        t.clickOn(t.card("annonce").pin)
        // Another announcer pinned `autre` while this click went.
        session.onPins(listOf(Pin(other.id, NOW)))
        t.frame()
        assertEquals(listOf(other.id), session.feed(FeedKind.ANNOUNCEMENTS).pinned)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.UNAVAILABLE))
        t.tick()
        t.frame()
        assertEquals(listOf(other.id), session.feed(FeedKind.ANNOUNCEMENTS).pinned)
        assertTrue(t.card("autre").pin.active)
        assertFalse(t.card("annonce").pin.active)
        assertTrue(t.showsStart("snackbar.action"))
    }

    @Test
    fun `a pins payload during the click, then OK, ends with the group the answer gives`() {
        val other = announcement("autre", minutesAgo = 20)
        val a = announcement("annonce", minutesAgo = 10)
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(a, other))
        t.clickOn(t.card("annonce").pin)
        session.onPins(listOf(Pin(other.id, NOW.minusSeconds(5))))
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.OK, pinned = listOf(Pin(a.id, NOW), Pin(other.id, NOW.minusSeconds(5)))))
        t.tick()
        t.frame()
        assertEquals(listOf(a.id, other.id), session.feed(FeedKind.ANNOUNCEMENTS).pinned)
        assertTrue(t.card("annonce").pin.active)
        assertTrue(t.showsStart("pin.done"))
    }

    @Test
    fun `a second click while the pin waits for its answer sends nothing`() {
        val a = announcement("annonce")
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(a))
        t.clickOn(t.card("annonce").pin)
        t.frame()
        t.clickOn(t.card("annonce").pin)
        assertEquals(1, r.all<PinPayload>().size)
    }

    @Test
    fun `a pin answered not found takes the announcement away`() {
        val a = announcement("annonce")
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER, listOf(a, announcement("autre", minutesAgo = 20)))
        t.clickOn(t.card("annonce").pin)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.NOT_FOUND))
        t.tick()
        t.frame()
        assertFalse(t.shows("annonce"))
        assertTrue(t.shows("autre"))
    }

    // ---- Writing switched off: deleting and pinning go on (9.11) ----

    @Test
    fun `writing off, an author deletes their own post with the usual dialog and Post deleted`() {
        val own = post(text = "mien", mine = true, canDelete = true)
        val t = feed(FeedKind.GLOBAL, PLAYER.copy(writing = false), listOf(own, post(text = "autre", minutesAgo = 5)))
        t.clickOn(t.card("mien").more)
        t.clickStart("menu.delete")
        assertTrue(t.showsStart("dialog.delete_post"))
        t.confirmDialog()
        assertEquals(own.id, r.last<DeletePayload>().postId)
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        session.onRemoved(listOf(RemovedItem(own.id, FeedKind.GLOBAL, null)))
        t.tick()
        assertTrue(t.showsStart("delete.post_done"))
        t.tick(8)
        t.frame()
        assertFalse(t.shows("mien"))
    }

    @Test
    fun `writing off, a moderator deletes someone else's reply with the usual dialog and Reply deleted`() {
        val main = post(text = "fil", minutesAgo = 30)
        val other = reply(main, minutesAgo = 1).copy(text = "lui", canDelete = true)
        val t = thread(moderator.copy(writing = false), main, listOf(other))
        t.clickOn(t.card("lui").more)
        t.clickStart("menu.delete")
        assertTrue(t.showsStart("dialog.delete_reply"))
        t.confirmDialog()
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        session.onRemoved(listOf(RemovedItem(other.id, FeedKind.GLOBAL, main.id)))
        t.tick()
        assertTrue(t.showsStart("delete.reply_done"))
    }

    @Test
    fun `writing off, an announcer pins and unpins with Post pinned and Post unpinned`() {
        val a = announcement("annonce")
        val t = feed(FeedKind.ANNOUNCEMENTS, ANNOUNCER.copy(writing = false), listOf(a))
        t.clickOn(t.card("annonce").pin)
        val pin = r.last<PinPayload>()
        assertTrue(pin.pinned)
        session.onResult(ResultPayload(pin.requestId, Outcome.OK, pinned = listOf(Pin(a.id, NOW))))
        t.tick()
        assertTrue(t.showsStart("pin.done"))
        t.clickOn(t.card("annonce").pin)
        val unpin = r.last<PinPayload>()
        assertFalse(unpin.pinned)
        session.onResult(ResultPayload(unpin.requestId, Outcome.OK, pinned = emptyList()))
        t.tick()
        assertTrue(t.showsStart("pin.removed"))
    }

    @Test
    fun `writing off, a delete or a pin refused says what it says otherwise, and the heart still says writing is paused`() {
        val a = announcement("annonce", canPin = true).copy(canDelete = true)
        val t = feed(FeedKind.ANNOUNCEMENTS, moderator.copy(writing = false), listOf(a))
        t.clickOn(t.card("annonce").pin)
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.NOT_ALLOWED))
        t.tick(4)
        assertTrue(t.showsStart("snackbar.action"))
        assertFalse(t.card("annonce").pin.active)
        repeat(100) { t.tick() }
        t.delete("annonce")
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.UNAVAILABLE))
        t.tick(4)
        assertTrue(t.showsStart("snackbar.action"))
        assertTrue(t.shows("annonce"))
        repeat(100) { t.tick() }
        t.click(128f, 44f + 68f + 14f)
        t.tick()
        assertTrue(t.shows("snackbar.writing_off"))
    }
}

/** Whether the dialog's confirm button shows its label (`Supprimer`, cut by the test font), not its spinner. */
private fun PhoneTester.confirmLabelShown() =
    texts.any { it.text.startsWith("dialog.delet") && it.style == fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle.LABEL }

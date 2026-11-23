package fr.bde_teknantes.lnb.phone.feed.capture

import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.feed.client.FeedClient
import fr.bde_teknantes.lnb.phone.feed.client.FeedWriting
import fr.bde_teknantes.lnb.phone.feed.client.widgets.CardLook
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCardLayout
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.SampleContent
import org.lwjgl.glfw.GLFW
import java.util.UUID

/**
 * The capture scenarios of the thread, Compose, deleting and pinning (task M3), as Camille R. (`Cam_Roux`), against the
 * sample content. What a scenario needs beyond the sample (a reply arriving, Camille made an announcer) it makes
 * itself. Cards are found where the session says they are, measured with the game's font as the cards measure
 * themselves, so a click lands on the right card whatever the text.
 */
object WriteScenarios {
    private const val AWAY_X = -20
    private const val GLOBAL_TAB_X = 192
    private const val ANNONCES_TAB_X = 64

    /** The centres of the reply and pin buttons' discs, and of the more button (art direction 9.3). */
    private const val REPLY_X = 64f
    private const val PIN_X = 228f
    private const val MORE_X = 234f

    // ---- Where things are ----

    private val session get() = FeedClient.session
    private val strings get() = FeedClient.strings

    /** The post of [kind] whose text starts with [start]. */
    private fun postOf(kind: FeedKind, start: String): PostView {
        val list = session.feed(kind)
        return (list.pinned + list.posts).mapNotNull(session::post).first { it.text.startsWith(start) }
    }

    private fun height(post: PostView, look: CardLook) = PostCardLayout(post, look, "", TextMetrics, strings).height

    private fun layout(post: PostView, look: CardLook) = PostCardLayout(post, look, "", TextMetrics, strings)

    /** The top of [post]'s card in the feed [kind], in viewport dp. */
    private fun feedCardY(kind: FeedKind, post: PostView): Float {
        val list = session.feed(kind)
        var y = 44f - list.scroll.offset
        for (id in list.pinned + list.posts) {
            val p = session.post(id) ?: continue
            if (id == post.id) return y
            y += height(p, CardLook(more = false, pin = false))
        }
        error("${post.id} isn't in $kind")
    }

    private fun mainLook() = CardLook(more = false, pin = false, time = false, fullTime = "x", hover = false)

    private fun replyLook(p: PostView) = CardLook(more = false, pin = false, replyTo = FeedWriting.replyToLine(p, strings))

    /**
     * The top of reply [replyId]'s card in the thread of [threadId] (or of the main post for the thread's id), with the
     * composer [dock] dp tall. The list draws its kept position held within its content, which shrinks when the
     * composer closes, so the offset is clamped as the list clamps it.
     */
    private fun threadCardY(threadId: UUID, replyId: UUID, dock: Float = 64f): Float {
        val thread = session.thread(threadId)
        val main = session.post(threadId) ?: error("No main post")
        val heights = listOf(threadId to height(main, mainLook())) +
            thread.replies.mapNotNull { id -> session.post(id)?.let { id to height(it, replyLook(it)) } }
        val rows = (if (thread.replies.isEmpty() && !thread.hasMore) 48f else 0f) + (if (thread.hasMore) 48f else 0f)
        val content = heights.sumOf { it.second.toDouble() }.toFloat() + rows + dock + 12f
        val offset = thread.scroll.offset.coerceIn(0f, maxOf(0f, content - 326f))
        var y = 44f - offset
        for ((id, h) in heights) {
            if (id == replyId) return y
            y += h
        }
        error("$replyId isn't in the thread")
    }

    /** A point on the card's text, away from its buttons: opens the thread. */
    private fun cardBody(kind: FeedKind, start: String): Pair<Float, Float> {
        val p = postOf(kind, start)
        val l = layout(p, CardLook(more = false, pin = false))
        return 150f to feedCardY(kind, p) + l.bodyY + 6f
    }

    /** The centre of a feed card's more button (28 dp at (220, 5), under the pinned line if any). */
    private fun feedMore(kind: FeedKind, start: String): Pair<Float, Float> {
        val p = postOf(kind, start)
        return MORE_X to feedCardY(kind, p) + 19f + layout(p, CardLook(more = false, pin = false)).shift
    }

    /** The menu's one item, opened under a more button centred at [more] (art direction 8.7). */
    private fun menuItem(more: Pair<Float, Float>): Pair<Float, Float> = 170f to more.second + 14f + 4f + 4f + 20f

    /** The dialog's confirm button, for a dialog with [title] and [text] (art direction 8.7). */
    private fun dialogConfirm(title: String, text: String): Pair<Float, Float> {
        val titles = TextMetrics.wrap(strings.get(title), TextStyle.TITLE_M, 200f, 2).size
        val texts = TextMetrics.wrap(strings.get(text), TextStyle.BODY, 200f, 3).size
        val h = 16f + titles * 18f + 8f + texts * 18f + 16f + 32f + 16f
        val top = Math.floor(((370f - h) / 4f).toDouble()).toFloat() * 2f
        return 176f to top + h - 16f - 16f
    }

    // ---- Scenarios ----

    /** F-7, F-8, F-10, the no-replies row, deleting (F-18, F-19) and a reply's notification (F-20). */
    fun FeedCapture.Script.thread() {
        start("lnbphone_feed-capture-thread")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)

        // F-7: the thread of post 1 from its card: R1 above the collapsed composer.
        waitForPost(FeedKind.GLOBAL, "Des diamants")
        clickAt { cardBody(FeedKind.GLOBAL, "Des diamants") }
        waitTicks(20)
        away()
        shot("thread_01_collapsed")
        mouseTo(128, 338)
        waitTicks(2)
        shot("thread_02_hover_field")

        // F-8: expanded, then typing.
        click(128, 338)
        waitTicks(6)
        shot("thread_03_expanded")
        run { session.replyDraft(post1().id).text.text = "J'arrive dans 5 min" }
        waitTicks(2)
        away()
        shot("thread_04_typed")

        // F-10: the composer retargeted at R1 (Mika L.), scrolled into view above the open composer.
        mouseTo(128, 100)
        scroll(-4.0)
        waitTicks(4)
        clickAt {
            val main = post1()
            val r1 = session.thread(main.id).replies.mapNotNull(session::post).first { it.text.startsWith("J'arrive !") }
            REPLY_X to threadCardY(main.id, r1.id, dock = 200f) + layout(r1, replyLook(r1)).actionY + 14f
        }
        waitTicks(6)
        away()
        shot("thread_05_reply_to_reply")
        // Sending, with the server held for a moment so the loading state shows.
        backend { Thread.sleep(1500) }
        click(196, 342)
        waitTicks(4)
        shot("thread_06_sending")
        waitUntil { !session.replyDraft(post1().id).sending }
        waitTicks(4)
        away()
        shot("thread_07_sent")

        // An own reply can be deleted: its dialog, the cell folding, the snackbar (F-19, as its author).
        clickAt {
            val main = post1()
            val own = session.thread(main.id).replies.mapNotNull(session::post).first { it.mine }
            MORE_X to threadCardY(main.id, own.id) + 19f
        }
        waitTicks(4)
        shot("delete_01_reply_menu")
        clickAt {
            val main = post1()
            val own = session.thread(main.id).replies.mapNotNull(session::post).first { it.mine }
            menuItem(MORE_X to threadCardY(main.id, own.id) + 19f)
        }
        waitTicks(6)
        away()
        shot("delete_02_reply_dialog")
        clickAt { dialogConfirm("dialog.delete_reply.title", "dialog.delete_reply.text") }
        waitTicks(2)
        shot("delete_03_reply_folding")
        waitTicks(8)
        away()
        shot("delete_04_reply_deleted")

        // The no-replies row: post 4, at the end of Global.
        click(22, 22)
        waitTicks(10)
        mouseTo(128, 200)
        scroll(-30.0)
        waitTicks(6)
        waitForPost(FeedKind.GLOBAL, "gg à tous")
        clickAt { cardBody(FeedKind.GLOBAL, "gg à tous") }
        waitTicks(20)
        away()
        shot("thread_08_no_replies")
        click(22, 22)
        waitTicks(10)
        // Post 8 in view, its more button clear of the compose button.
        mouseTo(128, 200)
        scroll(30.0)
        waitTicks(4)
        scroll(-3.0)
        waitTicks(6)

        // F-20: post 8 with its marker; R4 arrives as a notification; its banner lands on it in the thread.
        away()
        shot("landing_01_marker")
        var post8: UUID? = null
        run { post8 = postOf(FeedKind.GLOBAL, "Quelqu'un a vu mon loup").id }
        backend { it.createReply(UUID.randomUUID(), SampleContent.MIKA, post8!!, null, "Je l'ai vu près du moulin, côté est !", false) }
        waitUntil { fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks.bannerShown }
        waitTicks(6)
        shot("landing_02_banner")
        click(128, 40)
        waitTicks(10)
        away()
        shot("landing_03_thread_r4")
        click(22, 22)
        waitTicks(10)
        away()
        shot("landing_04_marker_cleared")

        // F-18: deleting post 8, Camille's own: the menu, the dialog, cancelled with Esc, then confirmed.
        waitForPost(FeedKind.GLOBAL, "Quelqu'un a vu mon loup")
        clickAt { feedMore(FeedKind.GLOBAL, "Quelqu'un a vu mon loup") }
        waitTicks(2)
        mouseToAt { menuItem(feedMore(FeedKind.GLOBAL, "Quelqu'un a vu mon loup")) }
        waitTicks(4)
        shot("delete_05_post_menu")
        clickAt { menuItem(feedMore(FeedKind.GLOBAL, "Quelqu'un a vu mon loup")) }
        waitTicks(6)
        away()
        shot("delete_06_post_dialog")
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(4)
        shot("delete_07_post_cancelled")
        clickAt { feedMore(FeedKind.GLOBAL, "Quelqu'un a vu mon loup") }
        waitTicks(2)
        clickAt { menuItem(feedMore(FeedKind.GLOBAL, "Quelqu'un a vu mon loup")) }
        waitTicks(6)
        backend { Thread.sleep(1500) }
        clickAt { dialogConfirm("dialog.delete_post.title", "dialog.delete_post.text") }
        waitTicks(4)
        shot("delete_08_post_loading")
        waitUntil { session.feed(FeedKind.GLOBAL).posts.mapNotNull(session::post).none { it.text.startsWith("Quelqu'un a vu mon loup") } }
        shot("delete_09_post_folding")
        waitTicks(8)
        away()
        shot("delete_10_post_deleted")
    }

    /** F-11 to F-14, then pinning (F-15) once Camille is an announcer. */
    fun FeedCapture.Script.compose() {
        start("lnbphone_feed-capture-compose")
        click(GLOBAL_TAB_X, 22)
        waitTicks(20)

        // F-11: a player's Compose, typing, the counter's colours, anonymous.
        click(220, 334)
        waitTicks(10)
        away()
        shot("compose_01_player")
        draft(C1.take(142))
        shot("compose_02_typed")
        draft(C1_LONG.take(265))
        shot("compose_03_warning")
        draft(C1_LONG.take(285))
        shot("compose_04_over_limit")
        draft(C1_LONG.take(270))
        click(226, 330)
        waitTicks(4)
        away()
        shot("compose_05_anonymous")
        backend { Thread.sleep(1500) }
        click(200, 22)
        waitTicks(4)
        shot("compose_06_sending")
        waitUntil { !session.compose.sending }
        waitTicks(8)
        away()
        shot("compose_07_published")
        // The compose button is lifted while `Publié` shows.
        waitTicks(70)

        // F-12: publishing fails, the draft stays; F-13: closed, the draft kept.
        click(220, 334)
        waitTicks(10)
        draft("Quelqu'un pour le Nether ce soir ?")
        backend { it.setOffline(true) }
        waitTicks(10)
        click(200, 22)
        waitTicks(8)
        away()
        shot("compose_08_failed")
        backend { it.setOffline(false) }
        waitTicks(20)
        click(22, 22)
        waitTicks(10)
        away()
        shot("compose_09_closed")
        // `Échec de l'envoi` came along to the feed and lifts the compose button for its 6 seconds.
        waitTicks(130)
        click(220, 334)
        waitTicks(10)
        away()
        shot("compose_10_draft_kept")
        click(22, 22)
        waitTicks(10)

        // F-14: Camille made an announcer: Compose from Annonces, the destination, anonymous.
        backend { it.setAnnouncer(SampleContent.CAMILLE, true, null) }
        waitTicks(20)
        click(ANNONCES_TAB_X, 22)
        waitTicks(20)
        click(220, 334)
        waitTicks(10)
        away()
        shot("compose_11_announcer")
        click(186, 332)
        waitTicks(6)
        away()
        shot("compose_12_announcer_global")
        click(70, 332)
        click(226, 286)
        waitTicks(6)
        away()
        shot("compose_13_announcer_anonymous")
        click(22, 22)
        waitTicks(10)

        // F-15, F-16: pinning from a thread's main post. In the feed, scrolled down to posts 6 and 7, their pin buttons
        // sit under the compose button; the thread's is the same button (F-16 step 3).
        waitForPost(FeedKind.ANNOUNCEMENTS, "La saison 2")
        openThreadOf("La saison 2")
        waitTicks(20)
        mouseToAt { PIN_X to mainPinY("La saison 2") }
        waitTicks(14)
        shot("pin_01_thread_hover_tooltip")
        clickAt { PIN_X to mainPinY("La saison 2") }
        waitTicks(4)
        away()
        shot("pin_02_pinned")
        waitTicks(70)
        openThreadOf("Maintenance cette nuit")
        waitTicks(20)
        clickAt { PIN_X to mainPinY("Maintenance cette nuit") }
        waitTicks(6)
        away()
        shot("pin_03_replace_dialog")
        clickAt { dialogConfirm("dialog.pin_limit.title", "dialog.pin_limit.text") }
        waitTicks(6)
        away()
        shot("pin_04_replaced")
        click(22, 22)
        waitTicks(10)
        away()
        shot("pin_05_group")

        if (FeedCapture.Options.customised) return
        // English at GUI scale 2: the announcer's destination, `Announcements` whole in its segment (8.7, 13.3).
        key(GLFW.GLFW_KEY_ESCAPE)
        waitTicks(10)
        language("en_us")
        guiScale(2)
        run { FeedClient.session.clear() }
        openPhone()
        openFeed()
        waitTicks(40)
        click(220, 334)
        waitTicks(10)
        away()
        shot("compose_14_announcer_english_gui2")
    }

    /** The centre of the pin button of the main post of the announcement starting with [start], in its thread. */
    private fun mainPinY(start: String): Float {
        val main = postOf(FeedKind.ANNOUNCEMENTS, start)
        return 44f - session.thread(main.id).scroll.offset + layout(main, mainLook()).actionY + 14f
    }

    /** Opens Feed afresh on the thread of the announcement starting with [start], over Annonces. */
    private fun FeedCapture.Script.openThreadOf(start: String) = run {
        val id = postOf(FeedKind.ANNOUNCEMENTS, start).id
        val target = fr.bde_teknantes.lnb.phone.feed.FeedTarget.Reply(FeedKind.ANNOUNCEMENTS, id, UUID.randomUUID()).toString()
        check(fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks.openApp(fr.bde_teknantes.lnb.phone.feed.Feed.APP_ID, target)) { "The phone refused the thread" }
    }

    /** F-25 in a thread: the strip, and a reply button that says why. */
    fun FeedCapture.Script.guestThread() {
        click(GLOBAL_TAB_X, 22)
        waitTicks(10)
        waitForPost(FeedKind.GLOBAL, "Des diamants")
        clickAt { cardBody(FeedKind.GLOBAL, "Des diamants") }
        waitTicks(20)
        away()
        shot("guest_04_thread_strip")
        clickAt {
            val main = post1()
            val r1 = session.thread(main.id).replies.mapNotNull(session::post).first { it.text.startsWith("J'arrive !") }
            REPLY_X to threadCardY(main.id, r1.id, dock = 40f) + layout(r1, replyLook(r1)).actionY + 14f
        }
        waitTicks(5)
        shot("guest_05_reply_refused")
    }

    // ---- Steps ----

    private fun post1() = postOf(FeedKind.GLOBAL, "Des diamants")

    private fun FeedCapture.Script.start(world: String) {
        language(FeedCapture.Options.language)
        guiScale(FeedCapture.Options.scale)
        enterWorld(world)
        waitTicks(40)
        openPhone()
        openFeed()
        waitTicks(40)
    }

    /** Waits until the feed [kind] holds the post starting with [start] (its page may still be on its way). */
    private fun FeedCapture.Script.waitForPost(kind: FeedKind, start: String) {
        log("waiting for '$start' in $kind")
        waitUntil {
            val list = session.feed(kind)
            (list.pinned + list.posts).mapNotNull(session::post).any { it.text.startsWith(start) }
        }
    }

    private fun FeedCapture.Script.away() {
        mouseTo(AWAY_X, 200)
        waitTicks(2)
    }

    /** Compose's text, as if typed. */
    private fun FeedCapture.Script.draft(text: String) {
        run { session.compose.text.text = text }
        waitTicks(2)
    }

    /** Text C1 of art direction 18.3, and repeated to reach the counts of F-11. */
    private const val C1 = "Qui est chaud pour une virée dans le Nether ce soir ? Rendez-vous au portail du spawn à 21:00. " +
        "Prenez des potions de résistance au feu, des blocs pour faire des ponts et de quoi manger, on risque d'y passer " +
        "un bon moment avant de trouver une forteresse."
    private val C1_LONG = "$C1 $C1"
}

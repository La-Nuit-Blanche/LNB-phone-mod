package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.SpinnerRow
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.widgets.RetryRow
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.ZoneOffset

/*
 * What the thread, Compose, delete and pin tests share: strings shown as their ID with the arguments after it, the app
 * as the phone registers it (with its notification targets), the game's tick (the session's, then the phone's), and
 * the rows of the list a screen shows.
 *
 * Measures used by the tests (the test font is 6.5 dp per character in body text): in a thread, the main post by
 * Alexandre M. (with a handle, one line) is 125 dp from y 44; a reply by SteveBuilds (no handle, one line) is 85 dp,
 * its text 30 dp and its action row 52 dp under its top, 16 dp more of each with a reply-to line. In a feed, a
 * one-line post by Alexandre M. is 101 dp, its action row 68 dp under its top.
 */

internal val IDS = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }

/** Feed as the phone registers it, opening on [root], with what notifications open. */
internal fun feedApp(session: FeedSession, root: FeedKind = FeedKind.ANNOUNCEMENTS) = PhoneApp(
    ResourceLocation.fromNamespaceAndPath("lnbphone_feed", "feed"),
    Component.literal("Feed"),
    AppIcon.Glyph(TileColor.AZURE, ResourceLocation.fromNamespaceAndPath("lnbphone_feed", "icon.png")),
    targetScreens = { FeedTargetScreens.screens(session, IDS, it, ZoneOffset.UTC) },
) { FeedScreen(session, IDS, root, fromHome = true, zone = ZoneOffset.UTC) }

/** [ticks] game ticks as the game plays them: the session's (the client's tick, while Feed is open), then the phone's. */
internal fun PhoneTester.play(session: FeedSession, ticks: Int) = repeat(ticks) {
    session.tick()
    tick()
}

/** The rows of the list the screen shows (the thread's, or the tab's). */
internal fun PhoneTester.rows(): List<Widget> {
    fun lists(w: Widget): List<ScrollList> = (if (w is ScrollList && w.isShowing) listOf(w) else emptyList()) + w.children.flatMap(::lists)
    val all = (screen as KitScreen).widgets.flatMap(::lists)
    return all.first { list -> list.children.isNotEmpty() }.children
}

internal fun PhoneTester.showsSpinnerRow() = rows().any { it is SpinnerRow }

internal fun PhoneTester.showsRetryRow() = rows().any { it is RetryRow }

/** Scrolls the list under (128, 200) as far down as it goes. */
internal fun PhoneTester.scrollToEnd() {
    scroll(128f, 200f, -1000f)
    tick(10)
    frame()
}

/** The post highlight drawn over a full-width cell (art direction 8.7), at full strength. */
internal const val HIGHLIGHT = 0x1F3A8DF0

/** Whether the cell whose text [text] is drawn has the post highlight on it now. */
internal fun PhoneTester.highlighted(text: String, textOffset: Float = 30f): Boolean {
    val top = texts.first { it.text == text }.y - textOffset
    return shapes.any { it.color == HIGHLIGHT && it.width == 256f && kotlin.math.abs(it.y - top) < 0.5f }
}

/** Answers the last `load_thread` sent with [replies] (and [main]). */
internal fun FeedFixtures.Recorder.answerThread(
    main: PostView?,
    replies: List<PostView>,
    next: String? = null,
    outcome: Outcome = Outcome.OK,
) {
    session.onThread(ThreadPayload(last<LoadThreadPayload>().requestId, outcome, main, replies, next))
}

/** Answers the last `load_feed` of [feed] sent. */
internal fun FeedFixtures.Recorder.answerFeed(feed: FeedKind, posts: List<PostView>, pinned: List<PostView> = emptyList(), next: String? = null) {
    val request = all<LoadFeedPayload>().last { it.feed == feed }
    session.onPage(PagePayload(request.requestId, Outcome.OK, feed, pinned, posts, next))
}

/** Runs [action], which must send exactly [count] payloads of type [T]. */
internal inline fun <reified T> FeedFixtures.Recorder.sends(count: Int, action: () -> Unit) {
    val before = sent.count { it is T }
    action()
    assertEquals(before + count, sent.count { it is T }, "${T::class.simpleName} sent")
}

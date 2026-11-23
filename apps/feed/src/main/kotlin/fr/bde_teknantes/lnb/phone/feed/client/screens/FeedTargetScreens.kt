package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import java.time.ZoneId

/** Where a Feed notification lands (`docs/feed-app.md` 6.2, art direction 9.10): the stack of screens, root first. */
object FeedTargetScreens {
    /**
     * An announcement: Announcements, scrolled to it and highlighted. A reply: the tab of its feed, with the thread on
     * top, scrolled so that the reply is fully visible and highlighted (back leads to the feed). Anything else: empty,
     * which opens Feed on its first screen.
     */
    fun screens(session: FeedSession, strings: FeedStrings, target: String, zone: ZoneId = ZoneId.systemDefault()): List<AppScreen> =
        when (val parsed = FeedTarget.parse(target)) {
            is FeedTarget.Post -> {
                session.land(parsed.postId)
                listOf(FeedScreen(session, strings, FeedKind.ANNOUNCEMENTS, fromHome = true, zone = zone))
            }
            is FeedTarget.Reply -> listOf(
                FeedScreen(session, strings, parsed.feed, fromHome = false, zone = zone),
                ThreadScreen(session, strings, parsed.threadId, landOn = parsed.replyId, zone = zone),
            )
            null -> emptyList()
        }
}

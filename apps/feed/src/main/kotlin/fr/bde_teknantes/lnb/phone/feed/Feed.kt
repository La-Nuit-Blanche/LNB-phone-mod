package fr.bde_teknantes.lnb.phone.feed

import net.minecraft.resources.ResourceLocation

/**
 * Feed's ids and the limits shared by the client, the server and the backends. The numbers are the site's
 * (`docs/feed-mod.md` in LNB-Website, section 7); the server and the client check first with the same values, so the
 * site never refuses what the player was allowed to send.
 *
 * Common code: loads on both sides.
 */
object Feed {
    const val MOD_ID = "lnbphone_feed"

    /** The app's id: registered on the client, named by the server in notifications. */
    val APP_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, "feed")

    /** The longest post or reply, in code points after cleaning ([fr.bde_teknantes.lnb.phone.feed.rules.FeedText]). */
    const val MAX_TEXT = 280

    /** The longest raw text the site takes, before cleaning (a plain 400 beyond). */
    const val MAX_RAW_TEXT = 4000

    /** A text on the wire, in UTF-16 units: [MAX_TEXT] code points can take two units each. */
    const val MAX_TEXT_UNITS = MAX_TEXT * 2

    /** Pinned announcements at most. */
    const val MAX_PINS = 3

    /** Posts the app asks for per feed page. */
    const val FEED_PAGE = 20

    /** Replies the app asks for per thread page. */
    const val THREAD_PAGE = 50

    /** The most posts (or replies) a page may hold, whatever was asked. */
    const val MAX_PAGE = 50

    /** Cursors (`next`, `before`, `after`) are opaque strings of at most this many characters. */
    const val MAX_CURSOR = 128

    /** A Minecraft username, in characters. */
    const val MAX_USERNAME = 16

    /** A real name: 64 code points, so 128 UTF-16 units. */
    const val MAX_REAL_NAME_UNITS = 128

    /** `POST /views`: viewers per call. */
    const val MAX_VIEWERS = 200

    /** `POST /views`: posts per call. */
    const val MAX_VIEW_IDS = 20

    /** Notifications per `GET /notifications`, and IDs per acknowledgement. */
    const val MAX_NOTIFICATIONS = 100
}

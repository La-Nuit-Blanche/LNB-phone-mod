package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.ui.TextMeasurer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.model.PostView

/**
 * What a card shows besides the post itself, decided by where it is and who looks (art direction 9.3, 9.7, 9.11).
 *
 * @property more The more button: the viewer can delete the post, as its view says (writing off included, 9.11).
 * @property pin The pin button: an announcement the viewer can pin, as its view says (writing off included, 9.11).
 * @property time The relative time on the name line; false on a thread's main post, which has its full timestamp.
 * @property replyTo The reply-to line (`En réponse à {name}`), in threads; null for none.
 * @property fullTime The full timestamp under the body (`14:32 · 12 nov.`), on a thread's main post (9.7); null for
 * none.
 * @property hover The hover and pressed tints of a card that opens its thread; false on a thread's main post, which
 * does nothing when clicked (9.7).
 */
data class CardLook(
    val more: Boolean,
    val pin: Boolean,
    val time: Boolean = true,
    val replyTo: String? = null,
    val fullTime: String? = null,
    val hover: Boolean = true,
)

/**
 * Where everything of a post card goes (art direction 9.3), from the post, its look and the measured text: plain logic,
 * so tests reach it with the phone's test measurer. Every y is from the top of the cell, in dp; x are the viewport's,
 * as a card is the list's full width.
 */
class PostCardLayout(val post: PostView, val look: CardLook, val timeText: String, measurer: TextMeasurer, strings: FeedStrings) {
    /** A pinned post has the `Épinglé` line on top, and everything else moves down 18 dp. */
    val pinned = post.pinnedAt != null
    val shift = if (pinned) PINNED_SHIFT else 0f

    /** The anonymous look: no face, `Anonyme` in grey, no handle, no badge (also when the author is shown privately). */
    val anonymous = post.anonymous || post.author == null

    val name: String = if (anonymous) strings.get("post.anonymous") else post.author!!.displayName
    val handle: String? = if (anonymous) null else post.author?.handle
    val badge = !anonymous && post.author?.announcer == true

    val nameY = 10f + shift
    val handleY: Float? = handle?.let { nameY + 18f }
    private val afterHeader = handleY?.let { it + 16f } ?: (nameY + 18f)

    val replyToY: Float? = look.replyTo?.let { afterHeader }

    /** The private line (9.3): the author's own line wins over the moderator's. */
    val privateText: String? = privateLine(post, measurer, strings)

    /** The moderator's line has a tooltip; the author's own hasn't. */
    val privateIsModerator = privateText != null && !post.mine
    val privateY: Float? = privateText?.let { afterHeader + (if (replyToY != null) LINE else 0f) }

    private val optionalLines = (if (replyToY != null) 1 else 0) + (if (privateY != null) 1 else 0)
    val bodyY = afterHeader + LINE * optionalLines + 2f
    val bodyLines: List<String> = measurer.wrap(post.text, TextStyle.BODY, BODY_WIDTH)
    private val bodyEnd = bodyY + bodyLines.size * TextStyle.BODY.lineHeight

    /** A thread's main post: its full timestamp 8 dp under the body, then 4 dp before the action row (9.7). */
    val fullTimeY: Float? = look.fullTime?.let { bodyEnd + 8f }
    val actionY = fullTimeY?.let { it + TextStyle.CAPTION.lineHeight + 4f } ?: (bodyEnd + 4f)

    /**
     * The cell's full height, its divider included: `10 + 18 + 16 (handle) + 16 x optional + 2 + 18 x lines + 4 + 28 + 4 + 1`;
     * on a thread's main post, `8 + 16 + 4` in place of the `4` after the body.
     */
    val height = actionY + ACTION_ROW + 4f + 1f

    /** The time ends at x 244, or before the more button (x 214). */
    val timeRight = if (look.more) 214f else CONTENT_RIGHT
    val timeWidth = if (look.time) measurer.measure(timeText, TextStyle.CAPTION) else 0f

    /** Room for the name: the badge (4 + 16 dp) and 8 dp before the time always fit (9.3). */
    val nameMaxWidth = timeRight - (if (look.time) timeWidth + 8f else 0f) - (if (badge) 20f else 0f) - CONTENT_X
    val nameShown: String = measurer.truncate(name, TextStyle.TITLE_M, nameMaxWidth)
    val nameWidth = measurer.measure(nameShown, TextStyle.TITLE_M)
    val handleShown: String? = handle?.let { measurer.truncate(it, TextStyle.CAPTION, BODY_WIDTH) }

    companion object {
        const val CONTENT_X = 56f
        const val CONTENT_RIGHT = 244f
        const val BODY_WIDTH = 188f
        const val ACTION_ROW = 28f
        const val LINE = 16f
        const val PINNED_SHIFT = 18f

        /** The private line's text runs from x 76 to x 244. */
        const val PRIVATE_WIDTH = 168f

        /**
         * The private line, or null (9.3): `Publié anonymement par toi` for the author of an anonymous post;
         * `Auteur : {name} {handle}` for a moderator, its handle cut first, then its name.
         */
        fun privateLine(post: PostView, measurer: TextMeasurer, strings: FeedStrings): String? {
            if (!post.anonymous) return null
            if (post.mine) return measurer.truncate(strings.get("post.own_anonymous"), TextStyle.CAPTION, PRIVATE_WIDTH)
            val author = post.author ?: return null
            val style = TextStyle.CAPTION
            val handle = author.handle
            val full = strings.get("post.mod_author", author.displayName, handle ?: "").trim()
            if (handle == null || measurer.measure(full, style) <= PRIVATE_WIDTH) return measurer.truncate(full, style, PRIVATE_WIDTH)
            // Everything but the handle, measured with a stand-in in its place.
            val probe = "\u0000"
            val rest = measurer.measure(strings.get("post.mod_author", author.displayName, probe), style) - measurer.measure(probe, style)
            val room = PRIVATE_WIDTH - rest
            val cut = if (room > 0f) measurer.truncate(handle, style, room) else ""
            if (cut.length >= 3) {
                val line = strings.get("post.mod_author", author.displayName, cut)
                if (measurer.measure(line, style) <= PRIVATE_WIDTH) return line
            }
            // No room left for even a cut handle: the name goes, cut.
            return measurer.truncate(strings.get("post.mod_author", author.displayName, "").trim(), style, PRIVATE_WIDTH)
        }
    }
}

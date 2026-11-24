package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PostView

/**
 * The rules of writing that the thread, the reply composer and Compose share (art direction 9.7, 9.8, 9.11), apart
 * from the screens so that tests reach them without the phone.
 */
object FeedWriting {
    /**
     * Whether a draft of [text] can be sent: something besides spaces and line feeds, and within 280 characters
     * counted **untrimmed**, the count the counter shows (art direction 8.7), so that the counter and the button agree.
     * The text is sent trimmed.
     */
    fun canSend(text: String): Boolean = TextLimits.canSend(text, Feed.MAX_TEXT)

    /**
     * Whether [post]'s card has the pin button, in a feed or as a thread's main post: an announcement's main post the
     * viewer can pin, never a Global post or a reply, whatever the view says (art direction 9.3, 9.4). Writing switched
     * off doesn't take it away: the site still pins then (9.11).
     */
    fun showsPin(post: PostView): Boolean = post.canPin && post.feed == FeedKind.ANNOUNCEMENTS && !post.isReply

    /**
     * Where a Compose opened from [button]'s tab sends its post (art direction 9.8, F-13): Global for whoever isn't an
     * [announcer]; the destination [kept] with a draft already started (some [text], or the anonymous toggle on), as
     * cancelling keeps it; the tab whose button opened Compose for a fresh draft.
     */
    fun destinationOnOpen(text: String, anonymous: Boolean, kept: FeedKind, button: FeedKind, announcer: Boolean): FeedKind = when {
        !announcer -> FeedKind.GLOBAL
        text.isNotEmpty() || anonymous -> kept
        else -> button
    }

    /** The display name others see for [post]: null when it is anonymous (`Anonyme`), even to a moderator. */
    fun publicName(post: PostView): String? = if (post.anonymous) null else post.author?.displayName

    /** What a click on the reply button of [post] in its thread makes the composer answer: the main post, or a reply. */
    fun targetOf(post: PostView): ReplyTarget? = if (post.isReply) ReplyTarget(post.id, publicName(post)) else null

    /** The name the composer shows for [target] in a thread whose main post is [main]; null for `Anonyme`. */
    fun targetName(target: ReplyTarget?, main: PostView?): String? = if (target != null) target.name else main?.let(::publicName)

    /** The name, or `Anonyme` ([FeedStrings] `post.anonymous`) for null. */
    fun nameOrAnonymous(name: String?, strings: FeedStrings): String = name ?: strings.get("post.anonymous")

    /**
     * The reply-to line of a reply card (art direction 9.7): `En réponse à Mika L.`, `En réponse à Anonyme` when what it
     * answers was anonymous; none on a reply to the main post.
     */
    fun replyToLine(post: PostView, strings: FeedStrings): String? {
        val ref = post.replyTo ?: return null
        val name = if (ref.anonymous) null else ref.name
        return strings.get("post.reply_to", nameOrAnonymous(name, strings))
    }

    /** The string ID of what a [block] says, on the strip (in a thread) and in its snackbar (art direction 9.11). */
    fun snackbarKey(block: WriteBlock): String = when (block) {
        WriteBlock.GUEST -> "guest.snackbar"
        WriteBlock.BANNED -> "snackbar.banned"
        WriteBlock.WRITING_OFF -> "snackbar.writing_off"
    }

    /** The read-only strip's text in a thread: `Compte LNB requis pour répondre`, or the block's own words. */
    fun stripKey(block: WriteBlock): String = if (block == WriteBlock.GUEST) "guest.reply" else snackbarKey(block)
}

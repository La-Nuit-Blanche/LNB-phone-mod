package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import java.util.UUID

/** Why the player may not write now (art direction 9.11): each has its strip text and snackbar. */
enum class WriteBlock {
    /** No LNB account: `guest.reply`, `guest.snackbar`. */
    GUEST,

    /** Banned on the site, for the moment before Warden's kick: `snackbar.banned`. */
    BANNED,

    /** Writing is switched off on the site (`feed.writing`): `snackbar.writing_off`. */
    WRITING_OFF,
}

/** What became of a draft sent, for its screen to show once (art direction 9.7, 9.8, 9.11). */
sealed interface DraftOutcome {
    /** Published: the draft is cleared and the post is in its list or thread. */
    data class Sent(val post: PostView) : DraftOutcome

    /** `Échec de l'envoi` with Retry, which sends again with the same key. */
    data object SendFailed : DraftOutcome

    /** `Action impossible`, without an action: the site refused it (`NOT_ALLOWED`), and sending again can't help. */
    data object ActionFailed : DraftOutcome

    /** `Doucement ! Attends {n} s`, without an action. */
    data class TooFast(val seconds: Int) : DraftOutcome

    /** Refused for who the player is: the block's text as an error snackbar without an action. */
    data class Refused(val block: WriteBlock) : DraftOutcome

    /** The thread was deleted meanwhile (a reply's `NOT_FOUND`). */
    data object ThreadGone : DraftOutcome
}

/**
 * The idempotency key of a draft (`docs/feed-app.md` 8, "Writing"): made when the draft is first sent, kept until it
 * succeeds or what it would send changes, so that a Retry after a failure reuses it and the site never posts twice.
 */
internal class DraftKey {
    private var key: UUID? = null
    private var sentAs: Any? = null

    /** The key to send [what] with: the same as last time if [what] didn't change. */
    fun forSending(what: Any): UUID {
        val current = key
        if (current != null && sentAs == what) return current
        return UUID.randomUUID().also {
            key = it
            sentAs = what
        }
    }

    fun clear() {
        key = null
        sentAs = null
    }
}

/** The Compose draft (art direction 9.8): one, kept when Compose closes, until published. */
class ComposeDraft internal constructor() {
    /** Up to [FeedSession.MAX_DRAFT] characters, so the counter can show how far past 280 the text went. */
    val text: TextState = TextState.multiline(FeedSession.MAX_DRAFT)

    var anonymous = false

    /** The tab it goes to; only announcers can choose Announcements. */
    var destination = FeedKind.GLOBAL

    /** Waiting for the server's answer: Publier loading, the controls disabled. */
    var sending = false
        internal set

    internal val key = DraftKey()
    internal var outcome: DraftOutcome? = null

    /** What happened to the last send, once: the screen shows it. */
    fun takeOutcome(): DraftOutcome? = outcome.also { outcome = null }

    internal fun fingerprint(): Any = Triple(text.text, anonymous, destination)

    internal fun clear() {
        text.clear()
        anonymous = false
        key.clear()
        sending = false
    }
}

/**
 * Whom a reply answers: the thread's main post ([replyTo] null) or one of its replies, with the display [name] the
 * composer shows (`Répondre à {name}…`), null when that post is anonymous (`Anonyme`).
 */
data class ReplyTarget(val replyTo: UUID?, val name: String?)

/** The reply draft of one thread (art direction 9.7): its text, toggle and target, kept while the player browses. */
class ReplyDraft internal constructor(val threadId: UUID) {
    val text: TextState = TextState.multiline(FeedSession.MAX_DRAFT)

    var anonymous = false

    /** Null: the main post. */
    var target: ReplyTarget? = null

    var sending = false
        internal set

    internal val key = DraftKey()
    internal var outcome: DraftOutcome? = null

    fun takeOutcome(): DraftOutcome? = outcome.also { outcome = null }

    internal fun fingerprint(): Any = Triple(text.text, anonymous, target?.replyTo)

    internal fun clear() {
        text.clear()
        anonymous = false
        target = null
        key.clear()
        sending = false
    }
}

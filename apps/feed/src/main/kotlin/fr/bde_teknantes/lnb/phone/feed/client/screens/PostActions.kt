package fr.bde_teknantes.lnb.phone.feed.client.screens

import fr.bde_teknantes.lnb.phone.api.client.ui.Dialog
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.MenuItem
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.feed.client.FeedNotice
import fr.bde_teknantes.lnb.phone.feed.client.FeedSession
import fr.bde_teknantes.lnb.phone.feed.client.FeedStrings
import fr.bde_teknantes.lnb.phone.feed.client.FeedWriting
import fr.bde_teknantes.lnb.phone.feed.client.PinAttempt
import fr.bde_teknantes.lnb.phone.feed.client.WriteBlock
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import java.util.UUID

/**
 * What the feed and the thread share about deleting and pinning (art direction 9.4, 9.9): the more button's menu, the
 * two delete dialogs and the "Replace a pin?" dialog, whose confirm button loads until the server answered, and the
 * snackbars that follow. One per screen; the screen calls [tick] every tick and hands it the session's notices.
 *
 * It remembers the posts whose deletion the player confirmed on this screen ([foldsAway]), so that their cell folds
 * away (150 ms) when they leave the session rather than vanishing as a post deleted by someone else does.
 */
internal class PostActions(private val screen: KitScreen, private val session: FeedSession, private val strings: FeedStrings) {
    private val confirmed = HashSet<UUID>()
    private var deleteDialog: Pair<UUID, Dialog>? = null
    private var pinDialog: Pair<UUID, Dialog>? = null

    /** The more button of [post]: its menu, with the one destructive item `Supprimer` (9.9). */
    fun more(post: PostView, anchor: Widget) {
        val item = MenuItem(strings.get("menu.delete")) { confirmDelete(post) }.apply {
            icon = Icon.TRASH
            destructive = true
        }
        screen.showMenu(anchor, listOf(item))
    }

    /** The delete dialog of [post]: a post's (its replies go too) or a reply's. */
    fun confirmDelete(post: PostView) {
        val reply = post.isReply
        val id = post.id
        val dialog = Dialog(strings.get(if (reply) "dialog.delete_reply.title" else "dialog.delete_post.title")).apply {
            text = strings.get(if (reply) "dialog.delete_reply.text" else "dialog.delete_post.text")
            cancelLabel = strings.get("dialog.cancel")
            confirmLabel = strings.get("dialog.delete.confirm")
            danger = true
            onConfirm = { d ->
                // Loads until the server answers ([tick]); a post already gone has nothing to wait for.
                if (session.delete(id)) confirmed += id else d.close()
            }
        }
        deleteDialog = id to dialog
        screen.showDialog(dialog)
    }

    /** The pin button of [post]: pinned or unpinned at once, or the dialog first when 3 are pinned (9.4). */
    fun pin(post: PostView) {
        when (session.togglePin(post.id, replace = false)) {
            PinAttempt.NEEDS_REPLACE -> askReplace(post.id)
            PinAttempt.BLOCKED -> session.writeBlock()?.let(::blocked)
            PinAttempt.SENT, PinAttempt.IGNORED -> {}
        }
    }

    /** `Remplacer une épingle ?`: confirmed, the oldest pin goes and [postId] joins the group. */
    fun askReplace(postId: UUID) {
        val dialog = Dialog(strings.get("dialog.pin_limit.title")).apply {
            text = strings.get("dialog.pin_limit.text")
            cancelLabel = strings.get("dialog.cancel")
            confirmLabel = strings.get("dialog.pin_limit.confirm")
            onConfirm = { d -> if (session.togglePin(postId, replace = true) != PinAttempt.SENT) d.close() }
        }
        pinDialog = postId to dialog
        screen.showDialog(dialog)
    }

    /** Whether the cell of [id] should fold away now that it left the session: its deletion was confirmed here. */
    fun foldsAway(id: UUID): Boolean = id in confirmed

    /** Whether the player confirmed the deletion of [id] on this screen (deleting a thread's main post from it). */
    fun deletedHere(id: UUID): Boolean = id in confirmed

    /** The cell of [id] folded away: nothing more to remember. */
    fun forget(id: UUID) {
        confirmed -= id
    }

    /** Every tick: a dialog whose action the server answered closes; a deletion refused is forgotten. */
    fun tick() {
        deleteDialog?.let { (id, dialog) ->
            if (!dialog.isOpen) {
                deleteDialog = null
            } else if (dialog.isLoading && !session.isDeleting(id)) {
                dialog.close()
                deleteDialog = null
            }
        }
        for (id in confirmed.toList()) if (!session.isDeleting(id) && session.post(id) != null) confirmed -= id
        pinDialog?.let { (id, dialog) ->
            if (!dialog.isOpen) {
                pinDialog = null
            } else if (dialog.isLoading && !session.isPinning(id)) {
                dialog.close()
                pinDialog = null
            }
        }
    }

    /**
     * Shows what the session reports about writes, as an error or plain snackbar (`docs/feed-app.md` 5.3, art
     * direction 9.4, 9.9, 9.11). Returns false for the connection's notices, which the screen shows itself.
     */
    fun report(notice: FeedNotice): Boolean {
        when (notice) {
            FeedNotice.ActionFailed -> screen.showErrorSnackbar(strings.get("snackbar.action_failed"))
            is FeedNotice.TooFast -> screen.showErrorSnackbar(strings.get("snackbar.too_fast", notice.seconds))
            is FeedNotice.Refused -> screen.showErrorSnackbar(strings.get(FeedWriting.snackbarKey(notice.block)))
            is FeedNotice.PinDone -> screen.showSnackbar(strings.get(if (notice.pinned) "pin.done" else "pin.removed"))
            // Two announcers pinned at once and the server found 3: the dialog, as for a fourth pin.
            is FeedNotice.PinLimit -> askReplace(notice.postId)
            is FeedNotice.Deleted -> screen.showSnackbar(strings.get(if (notice.reply) "delete.reply_done" else "delete.post_done"))
            FeedNotice.ConnectionLost, FeedNotice.ConnectionBack -> return false
        }
        return true
    }

    /** A guest, a banned player or writing off (9.11): a plain snackbar saying why, with the error sound. */
    fun blocked(block: WriteBlock) {
        screen.showSnackbar(strings.get(FeedWriting.snackbarKey(block)))
        PhoneSounds.error()
    }
}

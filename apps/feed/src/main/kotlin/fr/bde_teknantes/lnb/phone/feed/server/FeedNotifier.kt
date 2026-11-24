package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.FeedMod
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch
import fr.bde_teknantes.lnb.phone.feed.server.backend.QueuedNotification
import net.minecraft.network.chat.Component

/**
 * Delivers the site's notification queue through the phone (`docs/feed-app.md` 6): takes a batch, sends each row,
 * acknowledges what was sent, and again while the site says there is more. One drain at a time: a drain asked for
 * while one runs runs once more after it.
 *
 * - An announcement goes to every player of the world but its author's accounts (`except`), through the phone's
 *   `sendToEveryone`; `except` goes there and nowhere else.
 * - A reply goes to the recipient's accounts (`players`); an empty list is acknowledged without sending.
 *
 * A row is acknowledged once it was handed to the phone: a crash in between delivers it twice at the next drain, which
 * is better than losing it. Nothing here logs a player or a text: only notification IDs. Server thread only.
 */
class FeedNotifier(private val backend: FeedBackend, private val host: FeedHost, private val sink: FeedNotificationSink) {
    private var draining = false
    private var again = false

    fun drain() {
        if (draining) {
            again = true
            return
        }
        draining = true
        next()
    }

    /** Takes back the notifications that point to rows that went (`docs/feed-app.md` 6.3). */
    fun takeBack(items: List<RemovedItem>) {
        for (item in items) {
            val target = if (item.threadId == null) FeedTarget.Post(item.id) else FeedTarget.Reply(item.feed, item.threadId, item.id)
            sink.clearTargetForEveryone(target.toString())
        }
    }

    private fun next() {
        backend.pendingNotifications().whenComplete { result, _ ->
            host.execute { deliver(result) }
        }
    }

    private fun deliver(result: BackendResult<NotificationBatch>?) {
        if (result !is BackendResult.Ok) return finish()
        val batch = result.body
        // Every row is acknowledged, delivered or not deliverable: a row the phone refuses would come back forever.
        val handled = batch.items.map { row ->
            try {
                send(row)
            } catch (e: RuntimeException) {
                FeedMod.LOGGER.warn("Feed notification {} couldn't be delivered: {}", row.id, e.javaClass.simpleName)
            }
            row.id
        }
        if (handled.isEmpty()) return finish()
        val ids = handled.take(Feed.MAX_NOTIFICATIONS)
        backend.ackNotifications(ids).whenComplete { ack, _ ->
            host.execute { if (ack is BackendResult.Ok && batch.more) next() else finish() }
        }
    }

    private fun finish() {
        draining = false
        if (again) {
            again = false
            drain()
        }
    }

    private fun send(row: QueuedNotification) {
        val post = row.post
        // An anonymous post names nobody, whatever the backend sent with it.
        val author = post.author?.takeIf { !post.anonymous }
        val name: Any = author?.displayName ?: Component.translatable("${Feed.MOD_ID}.post.anonymous")
        // The player's words are arguments of Feed's own key, never a key.
        val text = Component.translatable("notif.${Feed.MOD_ID}.text", name, post.text)
        when (row.kind) {
            QueuedNotification.Kind.ANNOUNCEMENT -> sink.sendToEveryone(
                Component.translatable("notif.${Feed.MOD_ID}.announcement"), text, FeedTarget.Post(post.id).toString(), row.except,
            )
            QueuedNotification.Kind.REPLY -> {
                val players = row.players.orEmpty()
                val threadId = post.threadId
                if (players.isEmpty() || threadId == null) return
                sink.send(players, Component.translatable("notif.${Feed.MOD_ID}.reply"), text, FeedTarget.Reply(post.feed, threadId, post.id).toString())
            }
        }
    }
}

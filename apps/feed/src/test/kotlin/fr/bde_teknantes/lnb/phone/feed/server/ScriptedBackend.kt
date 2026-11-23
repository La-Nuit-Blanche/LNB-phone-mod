package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.model.Unread
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerEntry
import fr.bde_teknantes.lnb.phone.feed.server.backend.AnnouncerState
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Created
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.Issuer
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch
import fr.bde_teknantes.lnb.phone.feed.server.backend.PinState
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A backend whose every call waits until the test answers it ([Call.answer], from any thread), or is answered at once
 * by [auto]. It records the calls with their arguments, so a test sees exactly what the server asked of the site.
 */
class ScriptedBackend : FeedBackend {
    class Call(val name: String, val args: List<Any?>, private val future: CompletableFuture<BackendResult<Any?>>) {
        val answered: Boolean get() = future.isDone

        fun answer(result: BackendResult<*>) {
            @Suppress("UNCHECKED_CAST")
            future.complete(result as BackendResult<Any?>)
        }

        fun fail(error: Throwable) {
            future.completeExceptionally(error)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T> arg(index: Int): T = args[index] as T

        override fun toString() = "$name$args"
    }

    val calls = CopyOnWriteArrayList<Call>()

    /** An answer given at once for a call, or null to leave it waiting. */
    @Volatile
    var auto: (Call) -> BackendResult<*>? = { null }

    var listener: FeedEventListener? = null
        private set

    fun calls(name: String): List<Call> = calls.filter { it.name == name }

    fun waiting(name: String): List<Call> = calls(name).filter { !it.answered }

    private fun <T> call(name: String, vararg args: Any?): CompletableFuture<BackendResult<T>> {
        val future = CompletableFuture<BackendResult<Any?>>()
        val call = Call(name, args.toList(), future)
        calls += call
        auto(call)?.let(call::answer)
        @Suppress("UNCHECKED_CAST")
        return future as CompletableFuture<BackendResult<T>>
    }

    override fun player(player: UUID) = call<PlayerState>("player", player)
    override fun page(viewer: UUID, feed: FeedKind, before: String?, limit: Int) = call<FeedPage>("page", viewer, feed, before, limit)
    override fun thread(viewer: UUID, threadId: UUID, after: String?, limit: Int) = call<ThreadPage>("thread", viewer, threadId, after, limit)
    override fun views(viewers: List<UUID>, ids: List<UUID>) = call<Map<UUID, List<PostView>>>("views", viewers, ids)
    override fun createPost(key: UUID, player: UUID, feed: FeedKind, text: String, anonymous: Boolean) =
        call<Created>("createPost", key, player, feed, text, anonymous)
    override fun createReply(key: UUID, player: UUID, threadId: UUID, replyTo: UUID?, text: String, anonymous: Boolean) =
        call<Created>("createReply", key, player, threadId, replyTo, text, anonymous)
    override fun like(player: UUID, postId: UUID, liked: Boolean) = call<LikeState>("like", player, postId, liked)
    override fun pin(player: UUID, postId: UUID, pinned: Boolean, replace: Boolean) = call<PinState>("pin", player, postId, pinned, replace)
    override fun delete(player: UUID, postId: UUID) = call<Unit>("delete", player, postId)
    override fun markFeedSeen(player: UUID, feed: FeedKind, upTo: Instant) = call<Unit>("markFeedSeen", player, feed, upTo)
    override fun markThreadSeen(player: UUID, threadId: UUID) = call<Unit>("markThreadSeen", player, threadId)
    override fun pendingNotifications() = call<NotificationBatch>("pendingNotifications")
    override fun ackNotifications(ids: List<UUID>) = call<Unit>("ackNotifications", ids)
    override fun setAnnouncer(target: UUID, announcer: Boolean, issuer: Issuer?) = call<AnnouncerState>("setAnnouncer", target, announcer, issuer)
    override fun announcers() = call<List<AnnouncerEntry>>("announcers")

    override fun start(listener: FeedEventListener) {
        this.listener = listener
    }

    override fun stop() {
        listener = null
    }

    companion object {
        fun linked(player: UUID, name: String, rights: Rights = Rights(write = true, announce = false, moderate = false)) =
            PlayerState(linked = true, banned = false, writing = true, me = Author(player, name, null, rights.announce), rights = rights, unread = Unread.NONE)

        val GUEST = PlayerState(linked = false, banned = false, writing = true, me = null, rights = Rights.NONE, unread = Unread.NONE)

        /** A view of [id] made for [viewer], who is written into its text so a test can tell whose it is. */
        fun viewFor(viewer: UUID, id: UUID, author: Author? = null) = PostView(
            id = id, feed = FeedKind.GLOBAL, threadId = null, replyTo = null, text = viewer.toString(),
            createdAt = Instant.parse("2026-11-23T20:00:00Z"), anonymous = author == null, author = author, likes = 0, replies = 0,
            pinnedAt = null, liked = false, mine = false, canDelete = false, canPin = false, unseenReplies = 0, answersMe = false,
        )
    }
}

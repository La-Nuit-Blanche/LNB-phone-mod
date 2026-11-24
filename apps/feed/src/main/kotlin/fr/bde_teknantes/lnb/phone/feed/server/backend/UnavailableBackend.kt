package fr.bde_teknantes.lnb.phone.feed.server.backend

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Feed with no site: every call is `Unavailable`, quietly, and no stream is opened. What a single-player or LAN world
 * gets with `backend.kind = "site"`: like LNB Warden, which never calls the site there, Feed doesn't either (the
 * server's token belongs to the dedicated server). The app then says "Feed unavailable".
 */
class UnavailableBackend(private val reason: String) : FeedBackend {
    private fun <T> no(): CompletableFuture<BackendResult<T>> = CompletableFuture.completedFuture(BackendResult.Unavailable(reason))

    override fun player(player: UUID) = no<PlayerState>()
    override fun page(viewer: UUID, feed: FeedKind, before: String?, limit: Int) = no<FeedPage>()
    override fun thread(viewer: UUID, threadId: UUID, after: String?, limit: Int) = no<ThreadPage>()
    override fun views(viewers: List<UUID>, ids: List<UUID>) = no<Map<UUID, List<PostView>>>()
    override fun createPost(key: UUID, player: UUID, feed: FeedKind, text: String, anonymous: Boolean) = no<Created>()
    override fun createReply(key: UUID, player: UUID, threadId: UUID, replyTo: UUID?, text: String, anonymous: Boolean) = no<Created>()
    override fun like(player: UUID, postId: UUID, liked: Boolean) = no<LikeState>()
    override fun pin(player: UUID, postId: UUID, pinned: Boolean, replace: Boolean) = no<PinState>()
    override fun delete(player: UUID, postId: UUID) = no<Unit>()
    override fun markFeedSeen(player: UUID, feed: FeedKind, upTo: Instant) = no<Unit>()
    override fun markThreadSeen(player: UUID, threadId: UUID) = no<Unit>()
    override fun pendingNotifications() = no<NotificationBatch>()
    override fun ackNotifications(ids: List<UUID>) = no<Unit>()
    override fun setAnnouncer(target: UUID, announcer: Boolean, issuer: Issuer?) = no<AnnouncerState>()
    override fun announcers() = no<List<AnnouncerEntry>>()
    override fun start(listener: FeedEventListener) = Unit
    override fun stop() = Unit
}

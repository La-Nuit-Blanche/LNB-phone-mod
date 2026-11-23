package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.CountsPayload
import fr.bde_teknantes.lnb.phone.feed.network.LivePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinsPayload
import fr.bde_teknantes.lnb.phone.feed.network.RemovedPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResetPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.time.Instant
import java.util.UUID

/**
 * Relays the site's changes to the watchers (`docs/feed-app.md` 4.4): the players who opened Feed since they joined.
 * Events may come on any thread; each hops to the server thread first.
 *
 * A new post reaches each watcher as their own view (`POST /views`), never one player's view for another: the
 * views of an anonymous post differ by viewer. Posts announced in the same tick share a call ([flush], every tick).
 */
class Relay(
    private val backend: FeedBackend,
    private val host: FeedHost,
    private val watchers: Set<UUID>,
    private val rights: RightsCache,
    private val notifier: FeedNotifier,
) : FeedEventListener {
    private val pendingPosts = LinkedHashSet<UUID>()

    override fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant) = host.execute {
        pendingPosts += id
    }

    /** Sends the posts announced since the last call to the watchers, each their own views. Called every tick. */
    fun flush() {
        if (pendingPosts.isEmpty()) return
        val ids = pendingPosts.toList()
        pendingPosts.clear()
        val viewers = watchers.toList()
        if (viewers.isEmpty()) return
        for (idChunk in ids.chunked(Feed.MAX_VIEW_IDS)) {
            for (viewerChunk in viewers.chunked(Feed.MAX_VIEWERS)) {
                backend.views(viewerChunk, idChunk).whenComplete { result, _ ->
                    host.execute { deliver(viewerChunk, result) }
                }
            }
        }
    }

    private fun deliver(viewers: List<UUID>, result: BackendResult<Map<UUID, List<PostView>>>?) {
        // A watcher who misses a post here catches up at the next reset or when they open Feed again.
        if (result !is BackendResult.Ok) return
        for (viewer in viewers) {
            if (viewer !in watchers) continue
            // Only the views made for this viewer, keyed by them: never another player's.
            val posts = result.body[viewer].orEmpty()
            if (posts.isNotEmpty()) host.send(viewer, LivePayload(posts.take(LivePayload.MAX)))
        }
    }

    override fun onDeleted(items: List<RemovedItem>) = host.execute {
        for (chunk in items.chunked(RemovedPayload.MAX)) toWatchers(RemovedPayload(chunk))
        notifier.takeBack(items)
    }

    override fun onCounts(items: List<CountUpdate>) = host.execute {
        for (chunk in items.chunked(CountsPayload.MAX)) toWatchers(CountsPayload(chunk))
    }

    override fun onPins(pinned: List<Pin>) = host.execute {
        toWatchers(PinsPayload(pinned.take(Feed.MAX_PINS)))
    }

    override fun onRights(players: List<UUID>) = host.execute {
        for (player in players) if (host.isOnline(player)) rights.refresh(player)
    }

    override fun onNotification() = host.execute {
        notifier.drain()
    }

    /** Catching up (`feed-mod.md` 8.3): the queue, everyone's state (sent to watchers with `OK`), and a reset. */
    override fun onConnected() = host.execute {
        notifier.drain()
        rights.refreshAll(host.onlinePlayers())
        toWatchers(ResetPayload)
    }

    /** "Connection lost" over what is loaded: the watchers keep their last state. */
    override fun onDisconnected(disabled: Boolean) = host.execute {
        val available = if (disabled) Availability.DISABLED else Availability.UNAVAILABLE
        for (watcher in watchers.toList()) host.send(watcher, StatePayload(available, rights.state(watcher)))
    }

    private fun toWatchers(payload: CustomPacketPayload) {
        for (watcher in watchers.toList()) host.send(watcher, payload)
    }
}

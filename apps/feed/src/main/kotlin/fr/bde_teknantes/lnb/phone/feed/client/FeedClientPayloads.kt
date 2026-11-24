package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.network.CountsPayload
import fr.bde_teknantes.lnb.phone.feed.network.LivePayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinsPayload
import fr.bde_teknantes.lnb.phone.feed.network.RemovedPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload

/**
 * Where the server's payloads land on the client, on the client thread (`FeedNetwork` names these in its lambdas, so
 * this class loads only when a payload arrives, never on a dedicated server). Each goes to the client's state,
 * [FeedClient.session], which the screens read.
 */
object FeedClientPayloads {
    /** Availability and the player's state: rights, `me`, the tab dots (none while the site is away: the last stays). */
    fun onState(payload: StatePayload) = FeedClient.session.onState(payload)

    /** A feed page, answering `load_feed` with the same request ID. */
    fun onPage(payload: PagePayload) = FeedClient.session.onPage(payload)

    /** A thread page, answering `load_thread`. */
    fun onThread(payload: ThreadPayload) = FeedClient.session.onThread(payload)

    /** A write's outcome. */
    fun onResult(payload: ResultPayload) = FeedClient.session.onResult(payload)

    /** New posts or replies, each this player's own view. */
    fun onLive(payload: LivePayload) = FeedClient.session.onLive(payload.posts)

    fun onRemoved(payload: RemovedPayload) = FeedClient.session.onRemoved(payload.items)

    fun onCounts(payload: CountsPayload) = FeedClient.session.onCounts(payload.items)

    fun onPins(payload: PinsPayload) = FeedClient.session.onPins(payload.pinned)

    /** The server reconnected to the site: everything held is stale. */
    fun onReset() = FeedClient.session.onReset()

    /** The player left the server. */
    fun clear() = FeedClient.session.clear()
}

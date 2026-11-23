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
 * this class loads only when a payload arrives, never on a dedicated server).
 *
 * Nothing is kept yet: the client's state, `FeedSession` (`docs/feed-app.md` 8), comes with the screens, and each
 * function here then hands its payload to it. The client sends its requests with
 * `PacketDistributor.sendToServer(...)`: `OpenPayload` when the root screen is shown, `LoadFeedPayload` and
 * `LoadThreadPayload` for what is missing or stale, the writes with a request ID it matches in [onPage], [onThread]
 * and [onResult].
 */
object FeedClientPayloads {
    /** Availability and the player's state: rights, `me`, the tab dots (or the last state, while the site is away). */
    fun onState(payload: StatePayload) = Unit

    /** A feed page, answering `load_feed` with the same request ID. */
    fun onPage(payload: PagePayload) = Unit

    /** A thread page, answering `load_thread`. */
    fun onThread(payload: ThreadPayload) = Unit

    /** A write's outcome. */
    fun onResult(payload: ResultPayload) = Unit

    /** New posts or replies, each this player's own view. */
    fun onLive(payload: LivePayload) = Unit

    fun onRemoved(payload: RemovedPayload) = Unit

    fun onCounts(payload: CountsPayload) = Unit

    fun onPins(payload: PinsPayload) = Unit

    /** The server reconnected to the site: everything held is stale. */
    fun onReset() = Unit

    /** The player left the server. */
    fun clear() = Unit
}

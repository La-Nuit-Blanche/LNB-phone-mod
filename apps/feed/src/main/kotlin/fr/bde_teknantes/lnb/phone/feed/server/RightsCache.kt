package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.PlayerState
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * The online players' state on the site (`docs/feed-app.md` 4.2), read when they join, at each `open`, on a `rights`
 * event, on a refusal that says their rights changed, for everyone when the stream connects, and when a copy older
 * than [MAX_AGE_MS] is about to be used. A new state goes to the player if they watch, and their command tree is sent
 * again when their moderate right changed, so that `/feed announcer` appears or disappears.
 *
 * The server never computes a right: it uses this copy for the command's visibility, and the client for what it
 * shows. Every action is decided by the site. Server thread only.
 */
class RightsCache(private val backend: FeedBackend, private val host: FeedHost, private val isWatcher: (UUID) -> Boolean) {
    private class Entry(val state: PlayerState, val at: Long)

    private val entries = HashMap<UUID, Entry>()
    private val refreshing = HashSet<UUID>()
    private val stale = LinkedHashSet<UUID>()

    /** The last state read for [player], or null if none was read since they joined. */
    fun state(player: UUID): PlayerState? = entries[player]?.state

    /**
     * Whether [player] moderates, by the copy at hand, for the command's visibility. A copy older than [MAX_AGE_MS]
     * (or none) is read again at the next [tick] (not from here: this runs while the command tree is being built, and
     * a fresh state sends the tree again); the answer is the copy until then.
     */
    fun moderates(player: UUID): Boolean {
        val entry = entries[player]
        if ((entry == null || host.millis() - entry.at > MAX_AGE_MS) && player !in refreshing && host.isOnline(player)) stale += player
        return entry?.state?.rights?.moderate == true
    }

    /** Reads again the copies found stale by [moderates]. Called every server tick. */
    fun tick() {
        if (stale.isEmpty()) return
        val players = stale.toList()
        stale.clear()
        for (player in players) if (player !in refreshing && host.isOnline(player)) refresh(player)
    }

    /**
     * Reads [player]'s state again. On success it is kept (if they are still online) and sent to them if they watch.
     * The future completes on the server thread, after that, with the backend's answer.
     */
    fun refresh(player: UUID): CompletableFuture<BackendResult<PlayerState>> {
        val done = CompletableFuture<BackendResult<PlayerState>>()
        refreshing += player
        backend.player(player).whenComplete { result, error ->
            val answer = result ?: BackendResult.Unavailable("player state: ${error?.javaClass?.simpleName}")
            host.execute {
                refreshing -= player
                if (answer is BackendResult.Ok && host.isOnline(player)) store(player, answer.body)
                done.complete(answer)
            }
        }
        return done
    }

    fun refreshAll(players: Collection<UUID>) {
        for (player in players) refresh(player)
    }

    fun forget(player: UUID) {
        entries.remove(player)
        refreshing.remove(player)
        stale.remove(player)
    }

    private fun store(player: UUID, state: PlayerState) {
        val before = entries.put(player, Entry(state, host.millis()))
        if (isWatcher(player)) host.send(player, StatePayload(Availability.OK, state))
        // The tree sent at join was built with no copy, as for a player who doesn't moderate.
        if ((before?.state?.rights?.moderate ?: false) != state.rights.moderate) host.refreshCommands(player)
    }

    companion object {
        const val MAX_AGE_MS = 5 * 60 * 1000L
    }
}

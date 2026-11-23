package fr.bde_teknantes.lnb.phone.feed.server

import java.util.UUID

/**
 * The server's own limits on what players make it ask of the site (`docs/feed-app.md` 4.3). The site lets the whole
 * Minecraft server make 1,200 calls a minute, shared by every player: these keep any one client, modified or not, to
 * a small share of it, and every player together well under it, leaving room for the server's own calls (the stream
 * relay, the notification queue, the reads at each reconnection). A normal player never meets them; the site's
 * per-account limits are the ones about behaviour.
 *
 * - at most [MAX_IN_FLIGHT] calls waiting for each player;
 * - per kind and player: reads (`open`, `load_feed`, `load_thread`, and the state read the server makes for the
 *   player at join or after a refusal) a burst of 20, then 1 a second; writes a burst of 10, then 1 every 2 seconds;
 *   `seen_*` a burst of 3, then 1 every 2 seconds;
 * - per player, all kinds together: a burst of 40, then 90 a minute;
 * - the whole server, all players together: a burst of 100, then 600 a minute.
 *
 * Leaving the server forgets nothing: a client that leaves and comes back finds the allowance it left, and answers to
 * calls made before it left free the slots they took. Idle players are pruned once their allowance is full again,
 * which is the same as a fresh one. Server thread only. [millis] is a monotonic clock.
 */
class PlayerLimits(private val millis: () -> Long) {
    enum class Kind(val burst: Double, val perSecond: Double) {
        READ(20.0, 1.0),
        WRITE(10.0, 0.5),
        SEEN(3.0, 0.5),
    }

    private class Bucket(val burst: Double, val perSecond: Double, now: Long) {
        var tokens = burst
            private set
        private var at = now

        fun refill(now: Long) {
            tokens = minOf(burst, tokens + (now - at).coerceAtLeast(0) / 1000.0 * perSecond)
            at = now
        }

        fun take() {
            tokens -= 1.0
        }

        val full get() = tokens >= burst
    }

    private val inFlight = HashMap<UUID, Int>()
    private val buckets = HashMap<Pair<UUID, Kind>, Bucket>()
    private val shares = HashMap<UUID, Bucket>()
    private val server = Bucket(SERVER_BURST, SERVER_PER_MINUTE / 60.0, millis())

    /** Whether [player] may make one more call of [kind] now; if so, it counts as in flight until [finish]. */
    fun tryStart(player: UUID, kind: Kind): Boolean {
        if ((inFlight[player] ?: 0) >= MAX_IN_FLIGHT) return false
        val now = millis()
        val own = buckets.getOrPut(player to kind) { Bucket(kind.burst, kind.perSecond, now) }
        val share = shares.getOrPut(player) { Bucket(SHARE_BURST, SHARE_PER_MINUTE / 60.0, now) }
        val all = listOf(own, share, server)
        all.forEach { it.refill(now) }
        if (all.any { it.tokens < 1.0 }) return false
        all.forEach { it.take() }
        inFlight[player] = (inFlight[player] ?: 0) + 1
        return true
    }

    /** A call started by [tryStart] for [player] was answered, whether they are still here or not. */
    fun finish(player: UUID) {
        val count = inFlight[player] ?: return
        if (count <= 1) inFlight.remove(player) else inFlight[player] = count - 1
    }

    /** Drops the players whose allowance is full again and who wait for nothing: keeping them changes nothing. */
    fun prune() {
        val now = millis()
        buckets.entries.removeIf { (key, bucket) -> key.first !in inFlight && bucket.also { it.refill(now) }.full }
        shares.entries.removeIf { (player, bucket) -> player !in inFlight && buckets.keys.none { it.first == player } && bucket.also { it.refill(now) }.full }
    }

    companion object {
        const val MAX_IN_FLIGHT = 4
        const val SHARE_BURST = 40.0
        const val SHARE_PER_MINUTE = 90.0
        const val SERVER_BURST = 100.0
        const val SERVER_PER_MINUTE = 600.0
    }
}

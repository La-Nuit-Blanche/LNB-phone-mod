package fr.bde_teknantes.lnb.phone.feed.server

import java.util.UUID

/**
 * The server's own per-player limits on what a client makes it ask of the site (`docs/feed-app.md` 4.3): a backstop
 * against a modified client, far above what the app sends. The site's per-account limits are the real ones.
 *
 * - at most [MAX_IN_FLIGHT] backend calls waiting for each player;
 * - reads (`open`, `load_feed`, `load_thread`): a burst of 20, then 10 a second;
 * - writes (`create_post`, `create_reply`, `like`, `pin`, `delete`): a burst of 10, then 1 a second;
 * - `seen_*`: 2 a second.
 *
 * Server thread only. [millis] is a monotonic clock.
 */
class PlayerLimits(private val millis: () -> Long) {
    enum class Kind(val burst: Double, val perSecond: Double) {
        READ(20.0, 10.0),
        WRITE(10.0, 1.0),
        SEEN(2.0, 2.0),
    }

    private class Bucket(var tokens: Double, var at: Long)

    private val inFlight = HashMap<UUID, Int>()
    private val buckets = HashMap<Pair<UUID, Kind>, Bucket>()

    /** Whether [player] may make one more call of [kind] now; if so, it counts as in flight until [finish]. */
    fun tryStart(player: UUID, kind: Kind): Boolean {
        if ((inFlight[player] ?: 0) >= MAX_IN_FLIGHT) return false
        val now = millis()
        val bucket = buckets.getOrPut(player to kind) { Bucket(kind.burst, now) }
        bucket.tokens = minOf(kind.burst, bucket.tokens + (now - bucket.at) / 1000.0 * kind.perSecond)
        bucket.at = now
        if (bucket.tokens < 1.0) return false
        bucket.tokens -= 1.0
        inFlight[player] = (inFlight[player] ?: 0) + 1
        return true
    }

    /** The call started by [tryStart] for [player] was answered. */
    fun finish(player: UUID) {
        val count = inFlight[player] ?: return
        if (count <= 1) inFlight.remove(player) else inFlight[player] = count - 1
    }

    fun forget(player: UUID) {
        inFlight.remove(player)
        buckets.keys.removeIf { it.first == player }
    }

    companion object {
        const val MAX_IN_FLIGHT = 4
    }
}

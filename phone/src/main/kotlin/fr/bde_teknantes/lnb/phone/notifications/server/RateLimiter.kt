package fr.bde_teknantes.lnb.phone.notifications.server

/**
 * A token bucket per key: each key may act [burst] times at once, then [perSecond] times a second. Plain logic over
 * a clock in milliseconds, so it can be tested without waiting. Not thread-safe.
 */
class RateLimiter<K>(
    private val burst: Int,
    private val perSecond: Double,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(burst > 0 && perSecond > 0.0) { "Burst and rate must be positive" }
    }

    private class Bucket(var tokens: Double, var at: Long)

    private val buckets = HashMap<K, Bucket>()

    /** Whether [key] may act now; if so, it spends one token. */
    fun tryAcquire(key: K): Boolean {
        val now = clock()
        val bucket = buckets.getOrPut(key) { Bucket(burst.toDouble(), now) }
        val elapsed = (now - bucket.at).coerceAtLeast(0L)
        bucket.tokens = minOf(burst.toDouble(), bucket.tokens + elapsed * perSecond / 1000.0)
        bucket.at = now
        if (bucket.tokens < 1.0) return false
        bucket.tokens -= 1.0
        return true
    }

    /** Forgets [key] (the player left). */
    fun forget(key: K) {
        buckets.remove(key)
    }
}

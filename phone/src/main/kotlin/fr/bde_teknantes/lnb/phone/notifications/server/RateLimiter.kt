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
        // A rate like 1/3 a second can't be written exactly: 3000 ms of it make 0.999...; a hair of slack counts it whole.
        if (bucket.tokens < 1.0 - EPSILON) return false
        bucket.tokens = (bucket.tokens - 1.0).coerceAtLeast(0.0)
        return true
    }

    /** Forgets the keys whose bucket has filled up again: they act as a new key would, and take no memory. */
    fun forgetIdle() {
        val now = clock()
        buckets.values.removeIf { it.tokens + (now - it.at).coerceAtLeast(0L) * perSecond / 1000.0 >= burst - EPSILON }
    }

    /** Forgets [key] (the player left). */
    fun forget(key: K) {
        buckets.remove(key)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}

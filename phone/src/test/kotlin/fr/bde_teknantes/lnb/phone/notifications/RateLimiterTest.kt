package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.notifications.server.RateLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The limit on what one client may ask of the server: a burst of 10, then 2 a second (as [ServerNotifications] uses it). */
class RateLimiterTest {
    private var now = 1_000_000L
    private val limiter = RateLimiter<String>(burst = 10, perSecond = 2.0) { now }

    /** How many of [count] immediate attempts by [key] pass. */
    private fun attempts(count: Int, key: String = "alice") = (1..count).count { limiter.tryAcquire(key) }

    @Test
    fun `refuses a burst or a rate that isn't positive`() {
        assertThrows(IllegalArgumentException::class.java) { RateLimiter<String>(0, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { RateLimiter<String>(1, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { RateLimiter<String>(1, -1.0) }
    }

    @Test
    fun `a new key may act ten times at once, not eleven`() {
        assertEquals(10, attempts(11))
        assertFalse(limiter.tryAcquire("alice"))
    }

    @Test
    fun `then earns one action every 500 ms`() {
        attempts(10)
        now += 499
        assertFalse(limiter.tryAcquire("alice"))
        now += 1
        assertTrue(limiter.tryAcquire("alice"))
        assertFalse(limiter.tryAcquire("alice"))
        now += 1000
        assertEquals(2, attempts(3))
    }

    @Test
    fun `refused attempts cost nothing`() {
        attempts(10)
        repeat(100) { now += 1; limiter.tryAcquire("alice") } // 100 ms of hammering
        now += 400
        assertTrue(limiter.tryAcquire("alice"))
    }

    @Test
    fun `asking every millisecond earns as much as waiting`() {
        attempts(10)
        var granted = 0
        repeat(10_000) {
            now += 1
            if (limiter.tryAcquire("alice")) granted++
        }
        // 10 s at 2 a second; floating-point sums may lose at most the last one.
        assertTrue(granted in 19..20, "granted $granted")
    }

    @Test
    fun `a long wait refills the burst and no more`() {
        attempts(10)
        now += 3_600_000
        assertEquals(10, attempts(20))
    }

    @Test
    fun `keys are independent`() {
        attempts(10, "alice")
        assertEquals(10, attempts(10, "bob"))
        assertFalse(limiter.tryAcquire("alice"))
    }

    @Test
    fun `forgetting a key gives it a fresh burst`() {
        attempts(10)
        limiter.forget("alice")
        assertEquals(10, attempts(11))
        limiter.forget("nobody") // nothing to forget: no error
    }

    @Test
    fun `a clock going backwards grants nothing and refills from the new time`() {
        attempts(10)
        now -= 60_000
        assertFalse(limiter.tryAcquire("alice"))
        now += 499
        assertFalse(limiter.tryAcquire("alice"))
        now += 1
        assertTrue(limiter.tryAcquire("alice"))
    }

    @Test
    fun `a clock standing still grants nothing`() {
        attempts(10)
        repeat(1000) { assertFalse(limiter.tryAcquire("alice")) }
    }

    @Test
    fun `extreme clock values don't overflow into a refill or a failure`() {
        val clock = longArrayOf(Long.MIN_VALUE)
        val extreme = RateLimiter<String>(burst = 10, perSecond = 2.0) { clock[0] }
        repeat(10) { assertTrue(extreme.tryAcquire("k")) }
        clock[0] = Long.MAX_VALUE // the difference overflows to a negative number
        assertFalse(extreme.tryAcquire("k"))
        clock[0] = Long.MAX_VALUE
        assertFalse(extreme.tryAcquire("k"))
    }

    @Test
    fun `a fractional rate works over time`() {
        val slow = RateLimiter<String>(burst = 1, perSecond = 0.25) { now }
        assertTrue(slow.tryAcquire("k"))
        now += 3_999
        assertFalse(slow.tryAcquire("k"))
        now += 1
        assertTrue(slow.tryAcquire("k"))
    }
}

package fr.bde_teknantes.lnb.phone.client.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/** A banner's (or peek's) way out, hold and way back over time (art direction 10.2, 10.3, 14). */
class BannerTimelineTest {
    private val start = 1000.0
    private val eps = 1e-4f

    private fun inPhone() = BannerTimeline(BannerMotion.IN_PHONE, start)

    @Test
    fun `durations are those of 14, in whole ticks`() {
        assertEquals(BannerMotion(150.0, 4000.0, 150.0), BannerMotion.IN_PHONE)
        assertEquals(BannerMotion(200.0, 4000.0, 200.0), BannerMotion.PEEK)
        assertEquals(50.0, BannerMotion.TICK_MS)
        for (motion in listOf(BannerMotion.IN_PHONE, BannerMotion.PEEK)) {
            for (ms in listOf(motion.enterMs, motion.holdMs, motion.exitMs)) assertEquals(0.0, ms % BannerMotion.TICK_MS)
        }
    }

    @Test
    fun `hidden before it starts`() {
        val t = inPhone()
        assertEquals(0f, t.progress(start - 1))
        assertEquals(0f, t.progress(0.0))
        assertFalse(t.isHolding(start - 1))
        assertFalse(t.isFinished(start - 1))
    }

    @Test
    fun `comes out with a quadratic ease-out`() {
        val t = inPhone()
        assertEquals(0f, t.progress(start))
        assertEquals(1f - 0.75f * 0.75f, t.progress(start + 37.5), eps)
        assertEquals(0.75f, t.progress(start + 75), eps)
        assertEquals(1f - 0.25f * 0.25f, t.progress(start + 112.5), eps)
        assertEquals(1f, t.progress(start + 150))
        assertFalse(t.isHolding(start + 149.9))
    }

    @Test
    fun `holds 4000 ms fully out`() {
        val t = inPhone()
        assertTrue(t.isHolding(start + 150))
        assertTrue(t.isHolding(start + 4149.9))
        assertFalse(t.isHolding(start + 4150))
        assertEquals(1f, t.progress(start + 2000))
    }

    @Test
    fun `goes back with a quadratic ease-in and is then finished`() {
        val t = inPhone()
        assertEquals(1f, t.progress(start + 4150))
        assertEquals(1f - 0.25f, t.progress(start + 4150 + 75), eps)
        assertEquals(1f - 0.75f * 0.75f, t.progress(start + 4150 + 112.5), eps)
        assertFalse(t.isFinished(start + 4299.9))
        assertTrue(t.isFinished(start + 4300))
        assertEquals(0f, t.progress(start + 4300))
        assertEquals(0f, t.progress(start + 1e9))
    }

    @Test
    fun `the peek lasts 200, 4000 and 200 ms`() {
        val t = BannerTimeline(BannerMotion.PEEK, 0.0)
        assertEquals(0.75f, t.progress(100.0), eps)
        assertEquals(1f, t.progress(200.0))
        assertTrue(t.isHolding(4199.0))
        assertEquals(0.75f, t.progress(4300.0), eps)
        assertTrue(t.isFinished(4400.0))
    }

    // ---- A new notification during the banner (10.2 Queue) ----

    @Test
    fun `a restart while coming out carries on and holds a full 4000 ms after`() {
        val t = inPhone()
        t.restart(start + 75)
        assertEquals(0.75f, t.progress(start + 75), eps)
        assertEquals(1f, t.progress(start + 150))
        assertTrue(t.isHolding(start + 4149))
        assertFalse(t.isHolding(start + 4151))
    }

    @Test
    fun `a restart during the hold starts the hold over`() {
        val t = inPhone()
        t.restart(start + 3000)
        assertTrue(t.isHolding(start + 3000 + 3999))
        assertFalse(t.isHolding(start + 3000 + 4000))
        assertTrue(t.isFinished(start + 3000 + 4000 + 150))
    }

    @Test
    fun `a restart while going back comes out again from where it is`() {
        val t = inPhone()
        val at = start + 4150 + 100 // well into the way back
        val p = t.progress(at)
        t.restart(at)
        assertEquals(p, t.progress(at), eps)
        assertTrue(t.progress(at + 10) > p)
        // Back fully out within the rest of the way out, then a full hold.
        val out = (1.0 - kotlin.math.sqrt(1.0 - p)) * 150
        assertEquals(1f, t.progress(at + 150 - out + 0.01))
        assertTrue(t.isHolding(at + 150 - out + 3999))
        assertFalse(t.isFinished(at + 150 - out + 4000 + 149))
        assertTrue(t.isFinished(at + 150 - out + 4000 + 150))
    }

    @Test
    fun `a restart after it finished plays it all again`() {
        val t = inPhone()
        val later = start + 10_000
        t.restart(later)
        assertEquals(0f, t.progress(later))
        assertEquals(0.75f, t.progress(later + 75), eps)
        assertTrue(t.isHolding(later + 150))
        assertTrue(t.isFinished(later + 4300))
    }

    // ---- Dismissed ----

    @Test
    fun `dismissed during the hold, it goes back at once`() {
        val t = inPhone()
        val at = start + 1000
        t.dismiss(at)
        assertEquals(1f, t.progress(at))
        assertFalse(t.isHolding(at))
        assertEquals(0.75f, t.progress(at + 75), eps)
        assertTrue(t.isFinished(at + 150))
    }

    @Test
    fun `dismissed while coming out, it turns back from where it is`() {
        val t = inPhone()
        val at = start + 75
        val p = t.progress(at)
        t.dismiss(at)
        assertEquals(p, t.progress(at), eps)
        assertTrue(t.progress(at + 10) < p)
        assertFalse(t.isHolding(at))
        // It has only the remaining way back to go: 1 - sqrt(1 - 0.75) of 150 ms.
        assertTrue(t.isFinished(at + 75.0 + 0.01))
        assertFalse(t.isFinished(at + 74.9))
    }

    @Test
    fun `dismissed before it starts, it is finished at once`() {
        val t = inPhone()
        t.dismiss(start - 50)
        assertEquals(0f, t.progress(start - 50))
        assertEquals(0f, t.progress(start + 75))
        assertTrue(t.isFinished(start - 50))
    }

    @Test
    fun `dismissed while going back, nothing changes`() {
        val t = inPhone()
        val at = start + 4200
        val before = (0..200).map { t.progress(at + it) }
        t.dismiss(at)
        assertEquals(before, (0..200).map { t.progress(at + it) })
    }

    // ---- Time ----

    @Test
    fun `progress is a function of the time asked, so time going back reads earlier states`() {
        val t = inPhone()
        val forth = t.progress(start + 4225)
        assertEquals(0.75f, t.progress(start + 75), eps)
        assertEquals(forth, t.progress(start + 4225))
        assertFalse(t.isFinished(start + 4000))
    }

    @Test
    fun `a jump in time skips straight to the state at the new time`() {
        val t = inPhone()
        assertTrue(t.isFinished(start + 1e12))
        assertEquals(1f, t.progress(start + 3000))
    }

    @Test
    fun `whatever happens, progress stays between 0 and 1 and moves no faster than the easing allows`() {
        val random = Random(4150)
        val maxSlope = 2.0 / 150.0 // easeOut's slope at its start, per ms
        repeat(200) {
            val t = inPhone()
            var now = start
            var previous = t.progress(now)
            repeat(6000) {
                when (random.nextInt(400)) {
                    0 -> t.restart(now)
                    1 -> t.dismiss(now)
                }
                val p = t.progress(now)
                assertTrue(p in 0f..1f && !p.isNaN(), "progress $p at $now")
                assertTrue(abs(p - previous) <= maxSlope + 1e-3, "jump from $previous to $p at $now")
                if (t.isHolding(now)) assertEquals(1f, p)
                previous = p
                now += 1.0
            }
        }
    }

    @Test
    fun `events at times going backwards never give an impossible state`() {
        val random = Random(76)
        repeat(500) {
            val t = inPhone()
            repeat(20) {
                val now = start + random.nextDouble(-500.0, 6000.0)
                if (random.nextBoolean()) t.restart(now) else t.dismiss(now)
                for (probe in 0..100) {
                    val at = start - 600 + probe * 70.0
                    val p = t.progress(at)
                    assertTrue(p in 0f..1f && !p.isNaN())
                    if (t.isHolding(at)) assertEquals(1f, p)
                    if (t.isFinished(at)) assertEquals(0f, p)
                }
            }
        }
    }
}

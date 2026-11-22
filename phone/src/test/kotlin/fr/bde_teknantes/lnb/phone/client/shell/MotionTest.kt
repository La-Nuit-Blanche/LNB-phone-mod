package fr.bde_teknantes.lnb.phone.client.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Section 14 of the art direction: durations in 50 ms ticks, quadratic easing, whole GUI px. */
class MotionTest {
    private val tickMs = 50

    @Test
    fun `durations match section 14`() {
        assertEquals(250f, Motion.OPEN_TICKS * tickMs)
        assertEquals(200f, Motion.CLOSE_TICKS * tickMs)
        assertEquals(250f, Motion.GLIDE_TICKS * tickMs)
        assertEquals(150f, Motion.SCREEN_TICKS * tickMs)
        assertEquals(100f, Motion.PRESS_MIN_TICKS * tickMs)
        assertEquals(500, Motion.LED_ON_TICKS * tickMs)
        assertEquals(2000, Motion.LED_CYCLE_TICKS * tickMs)
        assertEquals(250, Motion.PULSE_STEP_TICKS * tickMs)
        assertEquals(1500, Motion.PULSE_TICKS * tickMs)
    }

    @Test
    fun `easing curves at their endpoints and midpoints`() {
        assertEquals(0f, Easing.EASE_OUT.apply(0f))
        assertEquals(0.75f, Easing.EASE_OUT.apply(0.5f))
        assertEquals(1f, Easing.EASE_OUT.apply(1f))
        assertEquals(0f, Easing.EASE_IN.apply(0f))
        assertEquals(0.25f, Easing.EASE_IN.apply(0.5f))
        assertEquals(1f, Easing.EASE_IN.apply(1f))
        // Quadratic: a quarter of the way.
        assertEquals(0.4375f, Easing.EASE_OUT.apply(0.25f))
        assertEquals(0.0625f, Easing.EASE_IN.apply(0.25f))
    }

    @Test
    fun `easing never overshoots and never goes backwards`() {
        for (easing in Easing.entries) {
            var previous = easing.apply(-1f)
            assertEquals(0f, previous)
            for (i in -100..1100) {
                val value = easing.apply(i / 1000f)
                assertTrue(value in 0f..1f, "$easing($i/1000) = $value")
                assertTrue(value >= previous, "$easing goes back at $i/1000")
                previous = value
            }
            assertEquals(1f, easing.apply(5f))
        }
    }

    @Test
    fun `progress is clamped before the start and after the end`() {
        val slide = DeviceSlide.open(DevicePose(0, 0, 0f), 100f)
        assertEquals(0f, slide.progress(0f))
        assertEquals(0f, slide.progress(-1e6f))
        assertEquals(0f, slide.progress(99.99f))
        assertEquals(1f, slide.progress(105f))
        assertEquals(1f, slide.progress(1e6f))
        assertFalse(slide.isDone(104.99f))
        assertTrue(slide.isDone(105f))
        assertEquals(0f, Motion.linear(10f, 4f, 5f))
        assertEquals(0.5f, Motion.linear(10f, 4f, 12f))
        assertEquals(1f, Motion.linear(10f, 4f, 99f))
    }

    @Test
    fun `each slide has its own duration and easing`() {
        val from = DevicePose(0, 0, 0f)
        val open = DeviceSlide.open(from, 0f)
        val close = DeviceSlide.close(from, 0f)
        val glide = DeviceSlide.glide(from, 0f)
        // Half the time: ease-out is three quarters there, ease-in a quarter.
        assertEquals(0.75f, open.progress(2.5f))
        assertEquals(0.25f, close.progress(2f))
        assertEquals(0.75f, glide.progress(2.5f))
        assertTrue(open.isDone(5f) && !open.isDone(4.999f))
        assertTrue(close.isDone(4f) && !close.isDone(3.999f))
        assertTrue(glide.isDone(5f) && !glide.isDone(4.999f))
    }

    @Test
    fun `positions snap to whole pixels, halves to the even neighbour`() {
        assertEquals(146, Motion.snap(145.5f))
        assertEquals(28, Motion.snap(28.5f))
        assertEquals(94, Motion.snap(94.5f))
        assertEquals(2, Motion.snap(1.5f))
        assertEquals(2, Motion.snap(2.5f))
        assertEquals(0, Motion.snap(0.5f))
        assertEquals(0, Motion.snap(-0.5f))
        assertEquals(-2, Motion.snap(-1.5f))
        assertEquals(10, Motion.snap(10.4999f))
        assertEquals(11, Motion.snap(10.5001f))
    }

    @Test
    fun `mid-slide frames of the flows`() {
        // P-1.2 and P-7.9's opening: up from the bottom edge (270) to (334, 36).
        assertEquals(DevicePose(334, 153, 0f), DeviceSlide.open(DevicePose(334, 270, 0f), 0f).pose(0.5f, 334, 36, 0f))
        // P-2.3 and P-7.8: down from (334, 36) to the bottom edge.
        assertEquals(DevicePose(334, 153, 0f), DeviceSlide.close(DevicePose(334, 36, 0f), 0f).pose(0.5f, 334, 270, 0f))
        // P-3.2: up to the centre, scrim fading in; y 145.5 shows as 146.
        assertEquals(DevicePose(170, 146, 0.5f), DeviceSlide.open(DevicePose(170, 270, 0f), 0f).pose(0.5f, 170, 21, 1f))
        // P-3.4: down from the centre, scrim fading out; y 145.5 again.
        assertEquals(DevicePose(170, 146, 0.5f), DeviceSlide.close(DevicePose(170, 21, 1f), 0f).pose(0.5f, 170, 270, 0f))
        // P-12.4: corner to centre; y 28.5 shows as 28.
        assertEquals(DevicePose(252, 28, 0.5f), DeviceSlide.glide(DevicePose(334, 36, 0f), 0f).pose(0.5f, 170, 21, 1f))
        // P-12.7: centre back to the corner, the same frame.
        assertEquals(DevicePose(252, 28, 0.5f), DeviceSlide.glide(DevicePose(170, 21, 1f), 0f).pose(0.5f, 334, 36, 0f))
        // P-9.2: from the peek (334, 198) to fully open.
        assertEquals(DevicePose(334, 117, 0f), DeviceSlide.open(DevicePose(334, 198, 0f), 0f).pose(0.5f, 334, 36, 0f))
    }

    @Test
    fun `the open slide frame by frame, and the close slide`() {
        val open = DeviceSlide.open(DevicePose(334, 270, 0f), 0f)
        assertEquals(listOf(270, 186, 120, 73, 45, 36), (0..5).map { open.pose(open.progress(it.toFloat()), 334, 36, 0f).y })
        val close = DeviceSlide.close(DevicePose(334, 36, 0f), 0f)
        // Ease-in: 0, 1/16, 1/4, 9/16, 1 of 234 px; 94.5 shows as 94.
        assertEquals(listOf(36, 51, 94, 168, 270), (0..4).map { close.pose(close.progress(it.toFloat()), 334, 270, 0f).y })
    }

    @Test
    fun `slides move one way only and never pass their target`() {
        val open = DeviceSlide.open(DevicePose(170, 270, 0f), 0f)
        val close = DeviceSlide.close(DevicePose(170, 21, 1f), 0f)
        val glide = DeviceSlide.glide(DevicePose(334, 36, 0f), 0f)
        var last = listOf(open.pose(0f, 170, 21, 1f), close.pose(0f, 170, 270, 0f), glide.pose(0f, 170, 21, 1f))
        for (i in 1..600) {
            val now = i / 100f
            val poses = listOf(
                open.pose(open.progress(now), 170, 21, 1f),
                close.pose(close.progress(now), 170, 270, 0f),
                glide.pose(glide.progress(now), 170, 21, 1f),
            )
            val (o, c, g) = poses
            assertTrue(o.y <= last[0].y && o.y >= 21 && o.scrim >= last[0].scrim && o.scrim <= 1f, "open at $now: $o")
            assertTrue(c.y >= last[1].y && c.y <= 270 && c.scrim <= last[1].scrim && c.scrim >= 0f, "close at $now: $c")
            assertTrue(g.x <= last[2].x && g.x >= 170 && g.y <= last[2].y && g.y >= 21, "glide at $now: $g")
            last = poses
        }
        assertEquals(DevicePose(170, 21, 1f), last[0])
        assertEquals(DevicePose(170, 270, 0f), last[1])
        assertEquals(DevicePose(170, 21, 1f), last[2])
    }

    @Test
    fun `a slide reversed mid-way starts from where the device is`() {
        val open = DeviceSlide.open(DevicePose(334, 270, 0f), 0f)
        for (i in 1..49) {
            val now = i / 10f
            val here = open.pose(open.progress(now), 334, 36, 0f)
            val back = DeviceSlide.close(here, now)
            assertEquals(here, back.pose(back.progress(now), 334, 270, 0f))
            // And down from there to the edge, without going up first.
            assertTrue(back.pose(back.progress(now + 0.5f), 334, 270, 0f).y >= here.y)
        }
    }

    @Test
    fun `screen slides cover the viewport's 128 px in 150 ms`() {
        val forward = ScreenSlide(true, 10f)
        val back = ScreenSlide(false, 10f)
        assertEquals(128, forward.offset(forward.progress(10f)))
        assertEquals(0, forward.offset(forward.progress(13f)))
        assertEquals(0, back.offset(back.progress(10f)))
        assertEquals(128, back.offset(back.progress(13f)))
        // P-6.4 and P-7.3: half way, 128 dp from the left edge.
        assertEquals(64, forward.offset(0.5f))
        assertEquals(64, back.offset(0.5f))
        // Half the time: forward eases out (3/4 there), back eases in (1/4 gone).
        assertEquals(32, forward.offset(forward.progress(11.5f)))
        assertEquals(32, back.offset(back.progress(11.5f)))
        assertFalse(forward.isDone(12.99f))
        assertTrue(forward.isDone(13f))
        // Before the start (a time jump backwards) the slide hasn't moved.
        assertEquals(128, forward.offset(forward.progress(0f)))
        assertEquals(0, back.offset(back.progress(0f)))
    }

    @Test
    fun `screen slides move one way only`() {
        val forward = ScreenSlide(true, 0f)
        val back = ScreenSlide(false, 0f)
        var f = 128
        var b = 0
        for (i in 0..400) {
            val now = i / 100f
            val fo = forward.offset(forward.progress(now))
            val bo = back.offset(back.progress(now))
            assertTrue(fo <= f && fo in 0..128, "forward at $now: $fo")
            assertTrue(bo >= b && bo in 0..128, "back at $now: $bo")
            f = fo
            b = bo
        }
    }

    @Test
    fun `the LED is lit 500 ms out of every 2 s`() {
        val lit = (0L until 80L).filter { Motion.ledLit(it) }
        assertEquals((0L..9L) + (40L..49L), lit)
        // Ticks before zero follow the same cycle.
        assertTrue(Motion.ledLit(-40))
        assertFalse(Motion.ledLit(-1))
        assertTrue(Motion.ledLit(-31))
        assertFalse(Motion.ledLit(-30))
    }

    @Test
    fun `the badge pulses three times in 1500 ms, starting light`() {
        val start = 1000L
        val light = (start - 10 until start + 50).filter { Motion.pulseLit(start, it) }.map { it - start }
        assertEquals((0L..4L) + (10L..14L) + (20L..24L), light)
        assertFalse(Motion.pulseLit(start, start + 30))
        assertFalse(Motion.pulseLit(start, start - 1))
    }
}

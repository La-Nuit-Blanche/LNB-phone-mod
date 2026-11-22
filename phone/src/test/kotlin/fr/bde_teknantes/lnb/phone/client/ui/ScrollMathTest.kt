package fr.bde_teknantes.lnb.phone.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScrollMathTest {
    private val view = 300f

    @Test
    fun `content shorter than the viewport doesn't scroll`() {
        assertEquals(0f, ScrollMath.maxOffset(200f, view))
        assertEquals(0f, ScrollMath.maxOffset(300f, view))
        assertEquals(0f, ScrollMath.wheel(0f, -3f, 200f, view))
        assertEquals(0f, ScrollMath.clamp(50f, 200f, view))
    }

    @Test
    fun `the wheel scrolls 36 dp per notch and stops at both ends`() {
        assertEquals(72f, ScrollMath.wheel(0f, -2f, 1000f, view))
        assertEquals(0f, ScrollMath.wheel(30f, 1f, 1000f, view))
        assertEquals(700f, ScrollMath.wheel(700f, -1f, 1000f, view))
        // Fractional notches (touchpads) scroll in proportion.
        assertEquals(18f, ScrollMath.wheel(0f, -0.5f, 1000f, view))
    }

    @Test
    fun `an offset left out of range by shrinking content is brought back`() {
        assertEquals(100f, ScrollMath.clamp(700f, 400f, view))
        assertEquals(0f, ScrollMath.clamp(-20f, 1000f, view))
    }

    @Test
    fun `the thumb is as long as the viewport's share, at least 24 dp`() {
        assertEquals(view, ScrollMath.thumbLength(200f, view))
        assertEquals(view, ScrollMath.thumbLength(0f, view))
        assertEquals(150f, ScrollMath.thumbLength(600f, view))
        assertEquals(24f, ScrollMath.thumbLength(1_000_000f, view))
    }

    @Test
    fun `the thumb travels from the top to the bottom of the track`() {
        assertEquals(0f, ScrollMath.thumbTop(0f, 600f, view))
        assertEquals(75f, ScrollMath.thumbTop(150f, 600f, view))
        assertEquals(150f, ScrollMath.thumbTop(300f, 600f, view))
        assertEquals(150f, ScrollMath.thumbTop(999f, 600f, view))
        assertEquals(0f, ScrollMath.thumbTop(50f, 200f, view))
        // With the minimum length, the travel is what the 24 dp leave.
        assertEquals(276f, ScrollMath.thumbTop(ScrollMath.maxOffset(1_000_000f, view), 1_000_000f, view))
    }

    @Test
    fun `dragging the thumb moves the content in proportion, clamped`() {
        // 600 content in 300: max 300, travel 150, so 1 dp of thumb is 2 dp of content.
        assertEquals(100f, ScrollMath.dragThumb(0f, 50f, 600f, view))
        assertEquals(300f, ScrollMath.dragThumb(0f, 500f, 600f, view))
        assertEquals(0f, ScrollMath.dragThumb(100f, -80f, 600f, view))
        assertEquals(0f, ScrollMath.dragThumb(0f, 50f, 200f, view))
    }

    @Test
    fun `the bar shows 800 ms after the last scroll, then fades over 200 ms`() {
        assertEquals(1f, ScrollMath.barAlpha(800f, 0f, held = false))
        assertEquals(0.5f, ScrollMath.barAlpha(900f, 0f, held = false))
        assertEquals(0f, ScrollMath.barAlpha(1000f, 0f, held = false))
        assertEquals(1f, ScrollMath.barAlpha(5000f, 0f, held = true))
        assertEquals(0f, ScrollMath.barAlpha(0f, Float.NEGATIVE_INFINITY, held = false))
    }

    @Test
    fun `revealing a band moves the least, or not at all`() {
        // Already visible.
        assertEquals(100f, ScrollMath.reveal(100f, 150f, 200f, view, 1000f))
        // Above: its top comes to the top.
        assertEquals(50f, ScrollMath.reveal(100f, 50f, 90f, view, 1000f))
        // Below: its bottom comes to the bottom.
        assertEquals(200f, ScrollMath.reveal(0f, 450f, 500f, view, 1000f))
        // Taller than the viewport: its top shows.
        assertEquals(400f, ScrollMath.reveal(0f, 400f, 900f, view, 1000f))
        // Near the end: clamped.
        assertEquals(700f, ScrollMath.reveal(0f, 950f, 1000f, view, 1000f))
    }
}

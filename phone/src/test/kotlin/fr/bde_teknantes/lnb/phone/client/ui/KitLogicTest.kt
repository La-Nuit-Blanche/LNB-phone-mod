package fr.bde_teknantes.lnb.phone.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KitLogicTest {
    @Test
    fun `scrolling stays in range and the thumb follows`() {
        assertEquals(0f, ScrollMath.wheel(10f, 1f, 1000f, 300f))
        assertEquals(36f, ScrollMath.wheel(0f, -1f, 1000f, 300f))
        assertEquals(700f, ScrollMath.wheel(690f, -1f, 1000f, 300f))
        assertEquals(90f, ScrollMath.thumbLength(1000f, 300f))
        assertEquals(24f, ScrollMath.thumbLength(100000f, 300f))
        assertEquals(210f, ScrollMath.thumbTop(700f, 1000f, 300f))
        assertEquals(700f, ScrollMath.dragThumb(0f, 210f, 1000f, 300f))
        assertEquals(1f, ScrollMath.barAlpha(1000f, 300f, held = false))
        assertEquals(0.5f, ScrollMath.barAlpha(1900f, 1000f, held = false))
        assertEquals(0f, ScrollMath.barAlpha(5000f, 1000f, held = false))
        assertEquals(100f, ScrollMath.reveal(0f, 300f, 400f, 300f, 1000f))
    }

    @Test
    fun `a quick click stays pressed 100 ms`() {
        val p = PressTracker()
        p.press(0f)
        assertTrue(p.release(20f, over = true))
        assertTrue(p.isPressed(90f, over = false))
        assertFalse(p.isPressed(101f, over = false))
        p.press(200f)
        assertFalse(p.release(400f, over = false))
        assertFalse(p.isPressed(401f, over = true))
    }

    @Test
    fun `menus open under their button, or above it near the bottom`() {
        assertEquals(Box(80f, 37f, 168f, 48f), Placement.menu(Box(220f, 5f, 28f, 28f), 1))
        assertEquals(Box(80f, 300f - 4f - 48f, 168f, 48f), Placement.menu(Box(220f, 300f, 28f, 28f), 1))
    }

    @Test
    fun `tooltips sit above, below near the top, inside the sides`() {
        assertEquals(Box(4f, 100f - 6f - 24f, 60f, 24f), Placement.tooltip(Box(0f, 100f, 20f, 20f), 60f, 24f))
        assertEquals(Box(4f, 26f, 60f, 24f), Placement.tooltip(Box(0f, 0f, 20f, 20f), 60f, 24f))
    }

    @Test
    fun `dialogs are as tall as the art direction's sum, on even dp`() {
        assertEquals(142f, Placement.dialogHeight(1, 2))
        assertEquals(114f, Placement.dialogTop(142f))
    }

    @Test
    fun `focus goes round`() {
        assertEquals("a", FocusOrder.next(listOf("a", "b"), null, backwards = false))
        assertEquals("a", FocusOrder.next(listOf("a", "b"), "b", backwards = false))
        assertEquals("b", FocusOrder.next(listOf("a", "b"), "a", backwards = true))
    }

    @Test
    fun `icons fall back to the nearest density exported`() {
        assertEquals(listOf("1_5x", "2x", "1x"), SmoothTextures.candidates(3).take(3))
        assertEquals("2_5x", SmoothTextures.folder(5))
        assertEquals("1x", SmoothTextures.candidates(2).first())
    }
}

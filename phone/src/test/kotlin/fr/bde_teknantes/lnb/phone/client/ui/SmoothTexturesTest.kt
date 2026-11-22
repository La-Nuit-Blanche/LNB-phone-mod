package fr.bde_teknantes.lnb.phone.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which icon density a GUI scale loads, and what it falls back to when that density wasn't exported. */
class SmoothTexturesTest {
    private val all = (2..9).map(SmoothTextures::folder).toSet()

    @Test
    fun `each GUI scale has its own density folder, in half steps`() {
        assertEquals(
            listOf("1x", "1_5x", "2x", "2_5x", "3x", "3_5x", "4x", "4_5x"),
            (2..9).map(SmoothTextures::folder),
        )
    }

    @Test
    fun `with every density exported, each scale from 2 to 9 loads its own`() {
        for (scale in 2..9) assertEquals(SmoothTextures.folder(scale), SmoothTextures.choose(scale) { it in all })
    }

    @Test
    fun `every candidate list holds each density once, the scale's own first`() {
        for (scale in 2..12) {
            val candidates = SmoothTextures.candidates(scale)
            assertEquals(SmoothTextures.folder(scale), candidates.first())
            assertEquals(candidates.size, candidates.toSet().size)
            assertEquals(maxOf(9, scale) - 1, candidates.size)
        }
    }

    @Test
    fun `a missing density falls back to the nearest one, the larger on a tie`() {
        val exported = setOf("1x", "1_5x", "2x")
        assertEquals("2x", SmoothTextures.choose(5) { it in exported })
        assertEquals("2x", SmoothTextures.choose(9) { it in exported })
        // Scale 5 sits between 4 and 6: with both there, 6 (scaled down) wins.
        assertEquals("3x", SmoothTextures.choose(5) { it == "2x" || it == "3x" })
        assertEquals(listOf("2_5x", "3x", "2x", "3_5x", "1_5x"), SmoothTextures.candidates(5).take(5))
    }

    @Test
    fun `above GUI scale 9 the nearest exported density is the largest`() {
        assertEquals("5x", SmoothTextures.candidates(10).first())
        assertEquals("4_5x", SmoothTextures.choose(10) { it in all })
        assertEquals("4_5x", SmoothTextures.choose(14) { it in all })
    }

    @Test
    fun `GUI scale 1 and below ask for the smallest density`() {
        assertEquals("1x", SmoothTextures.choose(1) { it in all })
        assertEquals("1x", SmoothTextures.choose(0) { it in all })
        assertEquals("1x", SmoothTextures.choose(-3) { it in all })
    }

    @Test
    fun `with nothing exported the scale's own folder is still named`() {
        assertEquals("1_5x", SmoothTextures.choose(3) { false })
    }

    @Test
    fun `only the smallest exported serves every scale`() {
        for (scale in 2..12) assertEquals("1x", SmoothTextures.choose(scale) { it == "1x" })
    }
}

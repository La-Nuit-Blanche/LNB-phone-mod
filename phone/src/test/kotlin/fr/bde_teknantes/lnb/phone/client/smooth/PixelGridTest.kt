package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PixelGridTest {
    @Test
    fun `snaps dp edges to whole pixels at GUI scale 3`() {
        val grid = PixelGrid(3)
        assertEquals(1.5f, grid.pxPerDp)
        assertEquals(66, grid.snap(44f))
        // Odd dp land on half pixels and round up.
        assertEquals(65, grid.snap(43f))
        assertEquals(2, grid.strokePx(1f))
    }

    @Test
    fun `hairlines are never thinner than a pixel`() {
        assertEquals(1, PixelGrid(2).strokePx(1f))
        assertEquals(1, PixelGrid(2).strokePx(0.25f))
        assertEquals(2, PixelGrid(4).strokePx(1f))
    }
}

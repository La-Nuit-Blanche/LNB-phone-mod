package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What can be checked without a GPU: no page is created to answer these. */
class MaskAtlasTest {
    @Test
    fun `only regions that fit an empty page, padding included, are accepted`() {
        val atlas = MaskAtlas(pageSize = 64)
        assertTrue(atlas.fits(62, 62))
        assertTrue(atlas.fits(1, 1))
        assertFalse(atlas.fits(63, 10))
        assertFalse(atlas.fits(10, 63))
        assertFalse(atlas.fits(0, 10))
        assertNull(atlas.allocate(200, 10), "too large: refused before any page is made")
    }
}

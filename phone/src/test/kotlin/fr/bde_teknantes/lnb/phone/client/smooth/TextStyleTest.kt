package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.smooth.FontWeight.REGULAR
import fr.bde_teknantes.lnb.phone.client.smooth.FontWeight.SEMIBOLD
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TextStyleTest {
    @Test
    fun `the ramp is the art direction's (8,4)`() {
        assertEquals(TextStyle(SEMIBOLD, 17f, 22f), TextStyles.TITLE_L)
        assertEquals(TextStyle(SEMIBOLD, 14f, 18f), TextStyles.TITLE_M)
        assertEquals(TextStyle(REGULAR, 13f, 18f), TextStyles.BODY)
        assertEquals(TextStyle(SEMIBOLD, 13f, 18f), TextStyles.BODY_STRONG)
        assertEquals(TextStyle(SEMIBOLD, 13f, 16f), TextStyles.LABEL)
        assertEquals(TextStyle(REGULAR, 12f, 16f), TextStyles.CAPTION)
        assertEquals(TextStyle(SEMIBOLD, 12f, 16f), TextStyles.CAPTION_STRONG)
        assertEquals(TextStyle(SEMIBOLD, 11f, 14f), TextStyles.MICRO)
    }

    @Test
    fun `sizes and line heights must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { TextStyle(REGULAR, 0f, 16f) }
        assertThrows(IllegalArgumentException::class.java) { TextStyle(REGULAR, 12f, -1f) }
        assertThrows(IllegalArgumentException::class.java) { TextStyle(REGULAR, Float.NaN, 16f) }
    }
}

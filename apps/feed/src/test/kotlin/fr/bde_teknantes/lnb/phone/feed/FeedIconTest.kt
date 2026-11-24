package fr.bde_teknantes.lnb.phone.feed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

/** The home icon is Feed's own: it ships in Feed's jar, under Feed's namespace, drawn at the glyph's 16 x 16. */
class FeedIconTest {
    @Test
    fun `Feed ships its own home icon`() {
        val stream = javaClass.getResourceAsStream("/assets/${Feed.MOD_ID}/textures/gui/feed.png")
        assertNotNull(stream, "the icon FeedClient registers is missing from Feed's resources")
        val image = stream!!.use(ImageIO::read)
        assertEquals(16, image.width)
        assertEquals(16, image.height)
    }
}

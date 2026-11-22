package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.GposKerning
import java.awt.Font
import java.awt.font.FontRenderContext
import java.nio.ByteBuffer

/**
 * The bundled Inter's real advances and kerning without FreeType (which needs a game window): advances through AWT at
 * a size of one em in font units, where they are the font's unhinted design advances, as [fr.bde_teknantes.lnb.phone.client.smooth.SmoothFont]
 * reads them; kerning from the same GPOS reader the phone uses. Ascender and descender aren't needed by the layout
 * tested here and are Inter's rounded values.
 */
internal class InterMetrics(fileName: String) : FontMetrics {
    private val bytes = javaClass.getResourceAsStream("/assets/lnbphone/font/$fileName")!!.use { it.readBytes() }
    private val unitsPerEm = headUnitsPerEm(ByteBuffer.wrap(bytes))
    private val font = Font.createFont(Font.TRUETYPE_FONT, bytes.inputStream()).deriveFont(unitsPerEm.toFloat())
    private val context = FontRenderContext(null, true, true)
    private val gpos = GposKerning(ByteBuffer.wrap(bytes))

    private fun glyphs(codePoint: Int) = font.createGlyphVector(context, String(Character.toChars(codePoint)))

    override fun advance(codePoint: Int): Float = glyphs(codePoint).getGlyphMetrics(0).advanceX / unitsPerEm

    override fun kerning(left: Int, right: Int): Float =
        gpos.kerning(glyphs(left).getGlyphCode(0), glyphs(right).getGlyphCode(0)).toFloat() / unitsPerEm

    override val ascender = 0.97f
    override val descender = 0.24f

    companion object {
        val REGULAR by lazy { InterMetrics("inter_regular.ttf") }
        val SEMIBOLD by lazy { InterMetrics("inter_semibold.ttf") }

        /** `unitsPerEm` from the `head` table, found through the table directory. */
        private fun headUnitsPerEm(font: ByteBuffer): Int {
            val tables = font.getShort(4).toInt()
            for (i in 0 until tables) {
                val record = 12 + 16 * i
                val tag = String(ByteArray(4) { font.get(record + it) }, Charsets.US_ASCII)
                if (tag == "head") return font.getShort(font.getInt(record + 8) + 18).toInt() and 0xFFFF
            }
            error("No head table")
        }
    }
}

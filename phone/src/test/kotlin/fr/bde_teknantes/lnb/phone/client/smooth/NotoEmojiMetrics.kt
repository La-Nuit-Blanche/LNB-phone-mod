package fr.bde_teknantes.lnb.phone.client.smooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The bundled Noto Emoji's metrics read straight from the TTF, without FreeType, as [EmojiFace] gives them: the cmap
 * (format 12), hmtx advances in em, and ligatures through [GsubLigatures]. With [withInter], text can be laid out on
 * the two real fonts in plain unit tests.
 */
class NotoEmojiMetrics private constructor(bytes: ByteArray) : EmojiMetrics {
    private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    private val unitsPerEm: Float
    private val advances: IntArray
    private val cmap = HashMap<Int, Int>()
    val ligatures = GsubLigatures(ByteBuffer.wrap(bytes))

    init {
        unitsPerEm = u16(table("head") + 18).toFloat()
        val metrics = u16(table("hhea") + 34)
        val hmtx = table("hmtx")
        advances = IntArray(metrics) { u16(hmtx + 4 * it) }
        readCmap()
    }

    override fun glyph(codePoint: Int): Int = cmap[codePoint] ?: 0

    override fun advance(glyph: Int): Float =
        (advances[minOf(glyph, advances.size - 1)] / unitsPerEm * EmojiFace.SCALE).coerceIn(0f, EmojiFace.MAX_ADVANCE)

    override fun ligature(glyphs: IntArray, from: Int, count: Int): Long = ligatures.ligature(glyphs, from, count)

    /** [font]'s metrics with this emoji face, as the game's [TextFace] combines them. */
    fun withInter(font: FontMetrics): FontMetrics = object : FontMetrics by font {
        override val emoji: EmojiMetrics get() = this@NotoEmojiMetrics
    }

    private fun u16(at: Int) = buf.getShort(at).toInt() and 0xFFFF

    private fun table(tag: String): Int {
        for (i in 0 until u16(4)) {
            val record = 12 + 16 * i
            if (String(ByteArray(4) { buf.get(record + it) }, Charsets.US_ASCII) == tag) return buf.getInt(record + 8)
        }
        error("No $tag table")
    }

    /** The Windows full-repertoire subtable (3, 10), format 12. */
    private fun readCmap() {
        val cmapTable = table("cmap")
        for (i in 0 until u16(cmapTable + 2)) {
            val record = cmapTable + 4 + 8 * i
            if (u16(record) != 3 || u16(record + 2) != 10) continue
            val sub = cmapTable + buf.getInt(record + 4)
            check(u16(sub) == 12) { "Expected cmap format 12" }
            val groups = buf.getInt(sub + 12)
            for (g in 0 until groups) {
                val at = sub + 16 + 12 * g
                val start = buf.getInt(at)
                val end = buf.getInt(at + 4)
                val glyph = buf.getInt(at + 8)
                for (cp in start..end) cmap[cp] = glyph + (cp - start)
            }
            return
        }
        error("No (3, 10) cmap")
    }

    companion object {
        val NOTO by lazy { NotoEmojiMetrics(InterMetrics.bytes("noto_emoji.ttf")) }
    }
}

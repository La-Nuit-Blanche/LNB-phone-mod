package fr.bde_teknantes.lnb.phone.client.smooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The bundled Inter's metrics read straight from the TTF, without FreeType, so text layout can be tested on the real
 * font. Gives what [SmoothFont] gives: hmtx advances in em (FreeType's `FT_LOAD_NO_SCALE` advance), hhea ascender and
 * descender (FreeType's face values), and GPOS kerning through [GposKerning].
 */
class InterMetrics private constructor(bytes: ByteArray) : FontMetrics {
    private val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    private val unitsPerEm: Float
    private val advances: IntArray
    private val cmap = HashMap<Int, Int>()
    val gpos = GposKerning(ByteBuffer.wrap(bytes))
    override val ascender: Float
    override val descender: Float

    init {
        val head = table("head")
        val hhea = table("hhea")
        unitsPerEm = u16(head + 18).toFloat()
        ascender = buf.getShort(hhea + 4) / unitsPerEm
        descender = -buf.getShort(hhea + 6) / unitsPerEm
        val metrics = u16(hhea + 34)
        val hmtx = table("hmtx")
        advances = IntArray(metrics) { u16(hmtx + 4 * it) }
        readCmap()
    }

    fun glyphId(codePoint: Int): Int = cmap[codePoint] ?: 0

    /** Advance in font units. */
    fun advanceUnits(codePoint: Int): Int = advances[minOf(glyphId(codePoint), advances.size - 1)]

    override fun advance(codePoint: Int): Float = advanceUnits(codePoint) / unitsPerEm

    override fun kerning(left: Int, right: Int): Float = gpos.kerning(glyphId(left), glyphId(right)) / unitsPerEm

    /** Width of [text] in font units, the way HarfBuzz reports it with only `kern` on. */
    fun widthUnits(text: String): Float = TextLayout.width(text, this, unitsPerEm)

    private fun u16(at: Int) = buf.getShort(at).toInt() and 0xFFFF

    private fun table(tag: String): Int {
        for (i in 0 until u16(4)) {
            val record = 12 + 16 * i
            if (String(ByteArray(4) { buf.get(record + it) }, Charsets.US_ASCII) == tag) return buf.getInt(record + 8)
        }
        error("No $tag table")
    }

    /** The Windows Unicode BMP subtable (3, 1), format 4: enough for French and the punctuation the phone uses. */
    private fun readCmap() {
        val cmapTable = table("cmap")
        for (i in 0 until u16(cmapTable + 2)) {
            val record = cmapTable + 4 + 8 * i
            if (u16(record) != 3 || u16(record + 2) != 1) continue
            val sub = cmapTable + buf.getInt(record + 4)
            check(u16(sub) == 4) { "Expected cmap format 4" }
            val segments = u16(sub + 6) / 2
            val ends = sub + 14
            val starts = ends + 2 * segments + 2
            val deltas = starts + 2 * segments
            val rangeOffsets = deltas + 2 * segments
            for (s in 0 until segments) {
                val start = u16(starts + 2 * s)
                val end = u16(ends + 2 * s)
                val delta = u16(deltas + 2 * s)
                val rangeOffset = u16(rangeOffsets + 2 * s)
                for (cp in start..end) {
                    if (cp == 0xFFFF) continue
                    val glyph = if (rangeOffset == 0) {
                        (cp + delta) and 0xFFFF
                    } else {
                        val g = u16(rangeOffsets + 2 * s + rangeOffset + 2 * (cp - start))
                        if (g == 0) 0 else (g + delta) and 0xFFFF
                    }
                    if (glyph != 0) cmap[cp] = glyph
                }
            }
            return
        }
        error("No (3, 1) cmap")
    }

    companion object {
        fun bytes(name: String): ByteArray =
            InterMetrics::class.java.getResourceAsStream("/assets/lnbphone/font/$name")!!.use { it.readBytes() }

        val REGULAR by lazy { InterMetrics(bytes("inter_regular.ttf")) }
        val SEMIBOLD by lazy { InterMetrics(bytes("inter_semibold.ttf")) }
    }
}

package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * The emoji font as the smooth layer uses it: Noto Emoji, a single-colour outline font, so an emoji is a glyph like any
 * other, a coverage mask drawn in the text's colour.
 *
 * Sizes: the font is drawn at the text's own size ([SCALE] 1). Its emoji are about 1 em tall, centred about 0.34 em
 * above the baseline, which is where Inter's capitals have their middle, so they sit on the line as designed and stay
 * inside the line box of every style. Its advance (1.27 em) gives them their own side bearings.
 *
 * Weight: the font is variable; it is drawn at 400 next to Inter Regular and 600 next to SemiBold, so an emoji's strokes
 * match the text around it. Metrics are the same at every weight.
 */
class EmojiFace(val font: SmoothFont) : EmojiMetrics, AutoCloseable {
    override fun glyph(codePoint: Int): Int = font.glyphId(codePoint)

    override fun advance(glyph: Int): Float = (font.glyphAdvance(glyph) * SCALE).coerceIn(0f, MAX_ADVANCE)

    override fun ligature(glyphs: IntArray, from: Int, count: Int): Long = font.ligatures.ligature(glyphs, from, count)

    override fun close() = font.close()

    companion object {
        /** Emoji em per text em. */
        const val SCALE = 1f

        /** Advance cap, in em: a replacement font with absurd metrics still lays out text that fits the screen. */
        const val MAX_ADVANCE = 2f

        /** The emoji font's weight next to text of [weight]. */
        fun weightOf(weight: FontWeight): Float = when (weight) {
            FontWeight.REGULAR -> 400f
            FontWeight.SEMIBOLD -> 600f
        }
    }
}

/**
 * The metrics text of one weight is laid out with: Inter's, plus the emoji face, loaded the first time a text needs it.
 * One per weight until the next resource reload, so caches keyed by it ([fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics])
 * start over when the fonts change.
 */
class TextFace internal constructor(val text: SmoothFont, private val emojiSource: () -> EmojiFace?) : FontMetrics by text {
    override val emoji: EmojiMetrics? get() = emojiSource()
}

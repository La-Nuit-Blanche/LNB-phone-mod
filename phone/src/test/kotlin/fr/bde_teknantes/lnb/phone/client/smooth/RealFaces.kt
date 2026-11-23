package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * The game's text faces without a GPU: Inter's metrics read from the bundled font ([InterMetrics]) with the emoji face
 * ([NotoEmojiMetrics]), as [TextFace] combines them; `hasGlyph` is what the game answers (glyph 0 is missing).
 */
object RealFaces {
    private fun withEmoji(inter: InterMetrics): FontMetrics = object : FontMetrics by inter {
        override fun hasGlyph(codePoint: Int) = inter.glyphId(codePoint) != 0
        override val emoji: EmojiMetrics get() = NotoEmojiMetrics.NOTO
    }

    /** Inter alone, as the text was laid out before the emoji face existed. */
    private fun interOnly(inter: InterMetrics): FontMetrics = object : FontMetrics by inter {
        override fun hasGlyph(codePoint: Int) = inter.glyphId(codePoint) != 0
    }

    val REGULAR: FontMetrics by lazy { withEmoji(InterMetrics.REGULAR) }
    val SEMIBOLD: FontMetrics by lazy { withEmoji(InterMetrics.SEMIBOLD) }
    val INTER_ONLY: FontMetrics by lazy { interOnly(InterMetrics.REGULAR) }

    /** What a text draws, glyph by glyph: an Inter code point, or an emoji glyph (marked). */
    fun drawn(text: String, font: FontMetrics = REGULAR, size: Float = 13f): List<Int> {
        val out = ArrayList<Int>()
        TextLayout.forEachGlyph(text, font, size) { d, _ -> out += d }
        return out
    }

    fun emojiGlyph(codePoint: Int) = EmojiShaping.EMOJI_GLYPH or NotoEmojiMetrics.NOTO.glyph(codePoint)

    val inter: InterMetrics get() = InterMetrics.REGULAR
    val noto: NotoEmojiMetrics get() = NotoEmojiMetrics.NOTO
}

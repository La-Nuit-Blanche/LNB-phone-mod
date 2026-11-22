package fr.bde_teknantes.lnb.phone.client.smooth

/** The two Inter weights the phone bundles (art direction 8.4). */
enum class FontWeight { REGULAR, SEMIBOLD }

/** A type style in dp: font size (the em) and the height of the line box the text sits in. */
data class TextStyle(val weight: FontWeight, val size: Float, val lineHeight: Float) {
    init {
        require(size > 0f && lineHeight > 0f) { "Size and line height must be positive" }
    }
}

/** The type ramp of art direction 8.4. */
object TextStyles {
    val TITLE_L = TextStyle(FontWeight.SEMIBOLD, 17f, 22f)
    val TITLE_M = TextStyle(FontWeight.SEMIBOLD, 14f, 18f)
    val BODY = TextStyle(FontWeight.REGULAR, 13f, 18f)
    val BODY_STRONG = TextStyle(FontWeight.SEMIBOLD, 13f, 18f)
    val LABEL = TextStyle(FontWeight.SEMIBOLD, 13f, 16f)
    val CAPTION = TextStyle(FontWeight.REGULAR, 12f, 16f)
    val CAPTION_STRONG = TextStyle(FontWeight.SEMIBOLD, 12f, 16f)
    val MICRO = TextStyle(FontWeight.SEMIBOLD, 11f, 14f)
}

/**
 * Horizontal and vertical metrics of one typeface, in em (1 = the font size). Kept apart from FreeType so text layout
 * can be computed, and tested, from plain numbers.
 */
interface FontMetrics {
    /** How far the pen moves after [codePoint]. */
    fun advance(codePoint: Int): Float

    /** Adjustment between two consecutive code points (negative brings them closer). */
    fun kerning(left: Int, right: Int): Float

    /** Distance from the baseline up to the top of the font's line (positive). */
    val ascender: Float

    /** Distance from the baseline down to the bottom of the font's line (positive). */
    val descender: Float
}

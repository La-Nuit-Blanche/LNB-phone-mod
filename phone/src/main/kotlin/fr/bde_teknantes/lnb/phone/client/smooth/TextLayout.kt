package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * Text measurement, truncation and wrapping, in dp, from [FontMetrics] alone. Advances are the font's unhinted ones,
 * scaled linearly, so a string measures the same in dp at every GUI scale and wraps at the same words everywhere;
 * only the final drawing snaps glyphs to pixels.
 */
object TextLayout {
    const val ELLIPSIS = "…"
    private const val ELLIPSIS_CP = 0x2026
    private const val EPSILON = 1e-3f

    // A regular space before these, or after «, is French typography's (non-breaking) space: never break there, so a
    // line never starts with "!" or ends with "«".
    private const val NO_BREAK_BEFORE = "!?:;»"
    private const val NO_BREAK_AFTER = "«"

    /**
     * Walks [text] glyph by glyph: [glyph] receives each code point and its pen position in dp, kerning included.
     * Returns the total advance.
     */
    inline fun forEachGlyph(text: String, font: FontMetrics, size: Float, glyph: (codePoint: Int, x: Float) -> Unit): Float {
        var pen = 0f
        var previous = -1
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (previous >= 0) pen += font.kerning(previous, cp) * size
            glyph(cp, pen)
            pen += font.advance(cp) * size
            previous = cp
            i += Character.charCount(cp)
        }
        return pen
    }

    fun width(text: String, font: FontMetrics, size: Float): Float = forEachGlyph(text, font, size) { _, _ -> }

    /** Distance in dp from the top of a line box of [style] to its baseline, with the leading split evenly. */
    fun baseline(style: TextStyle, font: FontMetrics): Float {
        val content = (font.ascender + font.descender) * style.size
        return (style.lineHeight - content) / 2f + font.ascender * style.size
    }

    /**
     * [text] if it fits in [maxWidth], else its longest start that fits followed by an ellipsis (spaces before the
     * ellipsis dropped). Empty when not even the ellipsis fits.
     */
    fun truncate(text: String, font: FontMetrics, size: Float, maxWidth: Float): String {
        val cps = text.codePoints().toArray()
        // ends[i]: width of the first i code points.
        val ends = FloatArray(cps.size + 1)
        var pen = 0f
        for (i in cps.indices) {
            if (i > 0) pen += font.kerning(cps[i - 1], cps[i]) * size
            pen += font.advance(cps[i]) * size
            ends[i + 1] = pen
        }
        if (ends[cps.size] <= maxWidth + EPSILON) return text

        val ellipsis = font.advance(ELLIPSIS_CP) * size
        for (count in cps.size - 1 downTo 0) {
            var kept = count
            while (kept > 0 && Character.isWhitespace(cps[kept - 1])) kept--
            val kern = if (kept > 0) font.kerning(cps[kept - 1], ELLIPSIS_CP) * size else 0f
            if (ends[kept] + kern + ellipsis <= maxWidth + EPSILON) {
                return String(cps, 0, kept) + ELLIPSIS
            }
        }
        return ""
    }

    /**
     * Breaks [text] into lines no wider than [maxWidth]: at spaces, at line feeds, and inside a word only when the
     * word alone is wider than a line. The space a line breaks at is dropped.
     */
    fun wrap(text: String, font: FontMetrics, size: Float, maxWidth: Float): List<String> {
        val lines = ArrayList<String>()
        for (paragraph in text.split('\n')) wrapParagraph(paragraph, font, size, maxWidth, lines)
        return lines
    }

    private fun wrapParagraph(paragraph: String, font: FontMetrics, size: Float, maxWidth: Float, out: MutableList<String>) {
        var line = ""
        for (word in words(paragraph)) {
            if (line.isNotEmpty()) {
                val candidate = "$line $word"
                if (width(candidate, font, size) <= maxWidth + EPSILON) {
                    line = candidate
                    continue
                }
                out.add(line)
                line = ""
            }
            if (word.isEmpty()) continue
            if (width(word, font, size) <= maxWidth + EPSILON) {
                line = word
            } else {
                val pieces = breakWord(word, font, size, maxWidth)
                out.addAll(pieces.subList(0, pieces.size - 1))
                line = pieces.last()
            }
        }
        out.add(line)
    }

    /** Splits at the spaces a line may break at. */
    internal fun words(paragraph: String): List<String> {
        val words = ArrayList<String>()
        var start = 0
        for (i in paragraph.indices) {
            if (paragraph[i] != ' ') continue
            val next = paragraph.getOrNull(i + 1)
            val previous = paragraph.getOrNull(i - 1)
            val glued = (next != null && next in NO_BREAK_BEFORE) || (previous != null && previous in NO_BREAK_AFTER)
            if (glued) continue
            words.add(paragraph.substring(start, i))
            start = i + 1
        }
        words.add(paragraph.substring(start))
        return words
    }

    /** Cuts a word wider than a line into pieces that fit, at least one code point each. */
    private fun breakWord(word: String, font: FontMetrics, size: Float, maxWidth: Float): List<String> {
        val pieces = ArrayList<String>()
        var start = 0
        var end = 0
        while (end < word.length) {
            val next = end + Character.charCount(word.codePointAt(end))
            if (end > start && width(word.substring(start, next), font, size) > maxWidth + EPSILON) {
                pieces.add(word.substring(start, end))
                start = end
            }
            end = next
        }
        pieces.add(word.substring(start))
        return pieces
    }
}

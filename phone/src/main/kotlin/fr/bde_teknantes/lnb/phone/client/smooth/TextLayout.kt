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
    private const val LINE_FEED = '\u000A'

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
     * One line of wrapped text: `text[start, end)` is drawn, [width] dp wide; [next] is where the following line
     * starts, past the space or line feed this one broke at (equal to [end] for a word cut in two). Offsets are UTF-16
     * indexes into the wrapped text, for placing a caret, a selection or marks on characters.
     */
    data class Line(val start: Int, val end: Int, val next: Int, val width: Float)

    /**
     * Breaks [text] into lines no wider than [maxWidth]: at spaces, at line feeds, and inside a word only when the
     * word alone is wider than a line. The space a line breaks at is dropped. An empty text is one empty line.
     */
    fun wrap(text: String, font: FontMetrics, size: Float, maxWidth: Float): List<String> =
        lines(text, font, size, maxWidth).map { text.substring(it.start, it.end) }

    /**
     * [wrap], but at most [maxLines] lines; when text is left out, the last line kept ends with an ellipsis (cut to
     * fit). No lines when [maxLines] is below 1.
     */
    fun wrap(text: String, font: FontMetrics, size: Float, maxWidth: Float, maxLines: Int): List<String> {
        if (maxLines < 1) return emptyList()
        val lines = lines(text, font, size, maxWidth)
        if (lines.size <= maxLines) return lines.map { text.substring(it.start, it.end) }
        val kept = lines.subList(0, maxLines).map { text.substring(it.start, it.end) }.toMutableList()
        // The ellipsis says there is more, so it goes on even when the last kept line would fit whole.
        kept[kept.size - 1] = truncate(kept.last().trimEnd() + ELLIPSIS, font, size, maxWidth)
        return kept
    }

    /** The lines of [wrap], with where each comes from in [text] and its width. */
    fun lines(text: String, font: FontMetrics, size: Float, maxWidth: Float): List<Line> {
        val out = ArrayList<Line>()
        var paragraphStart = 0
        while (true) {
            val feed = text.indexOf(LINE_FEED, paragraphStart)
            val paragraphEnd = if (feed < 0) text.length else feed
            wrapParagraph(text, paragraphStart, paragraphEnd, font, size, maxWidth, out)
            if (feed < 0) break
            // The line feed belongs to the paragraph's last line, like a break space.
            out[out.size - 1] = out.last().copy(next = feed + 1)
            paragraphStart = feed + 1
        }
        return out
    }

    private fun wrapParagraph(text: String, from: Int, to: Int, font: FontMetrics, size: Float, maxWidth: Float, out: MutableList<Line>) {
        fun width(a: Int, b: Int) = width(text.substring(a, b), font, size)
        fun fits(a: Int, b: Int) = width(a, b) <= maxWidth + EPSILON
        var lineStart = from
        var lineEnd = from
        var hasContent = false
        var wordStart = from
        var i = from
        while (i <= to) {
            if (i < to && !(text[i] == ' ' && breakable(text, i))) {
                i++
                continue
            }
            // A word runs from wordStart to i: on the current line if it fits there, else on a new one.
            if (hasContent) {
                if (fits(lineStart, i)) {
                    lineEnd = i
                } else {
                    out.add(Line(lineStart, lineEnd, wordStart, width(lineStart, lineEnd)))
                    hasContent = false
                }
            }
            if (!hasContent) {
                var pieceStart = wordStart
                if (!fits(wordStart, i)) {
                    // Wider than a line alone: cut into pieces that fit, at least one code point each.
                    var j = wordStart
                    while (j < i) {
                        val next = j + Character.charCount(text.codePointAt(j))
                        if (j > pieceStart && !fits(pieceStart, next)) {
                            out.add(Line(pieceStart, j, j, width(pieceStart, j)))
                            pieceStart = j
                        }
                        j = next
                    }
                }
                lineStart = pieceStart
                lineEnd = i
                hasContent = true
            }
            if (i == to) break
            wordStart = i + 1
            i++
        }
        out.add(Line(lineStart, lineEnd, to, width(lineStart, lineEnd)))
    }

    /** Whether a line may break at the space at [i]: not before high punctuation, not after an opening guillemet. */
    private fun breakable(text: String, i: Int): Boolean {
        val next = text.getOrNull(i + 1)
        val previous = text.getOrNull(i - 1)
        return !((next != null && next in NO_BREAK_BEFORE) || (previous != null && previous in NO_BREAK_AFTER))
    }

    /** The line of [lines] a caret at [index] is drawn on: the last one starting at or before it. */
    fun lineOf(lines: List<Line>, index: Int): Int {
        var result = 0
        for ((n, line) in lines.withIndex()) {
            if (line.start <= index) result = n else break
        }
        return result
    }

    /** The caret position on [line] nearest to [x] dp from the line's start (a click, or moving up and down). */
    fun indexAt(text: String, line: Line, x: Float, font: FontMetrics, size: Float): Int {
        var best = line.start
        var bestDistance = Float.MAX_VALUE
        var i = line.start
        while (true) {
            val d = kotlin.math.abs(width(text.substring(line.start, i), font, size) - x)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
            if (i >= line.end) break
            i += Character.charCount(text.codePointAt(i))
        }
        return best
    }
}

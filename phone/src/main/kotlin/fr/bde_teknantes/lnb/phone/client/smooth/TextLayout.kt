package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * Text measurement, truncation and wrapping, in dp, from [FontMetrics] alone. Advances are the font's unhinted ones,
 * scaled linearly, so a string measures the same in dp at every GUI scale and wraps at the same words everywhere;
 * only the final drawing snaps glyphs to pixels.
 *
 * Text from players is cleaned up as it is laid out, the same way for measuring and drawing:
 * - `\r\n`, a lone `\r` and `\n` break lines; a tab is a space;
 * - other control characters, zero-width and bidirectional format characters, variation selectors and joiners take
 *   no room and draw nothing;
 * - a character the font lacks draws one missing-glyph box for its whole grapheme (an emoji with its skin tone, a
 *   flag's two letters, a letter with accents the font lacks), not one per code point.
 */
object TextLayout {
    const val ELLIPSIS = "…"
    private const val ELLIPSIS_CP = 0x2026
    private const val EPSILON = 1e-3f
    @PublishedApi
    internal const val SPACE = 0x20

    // A regular space before these, or after «, is French typography's (non-breaking) space: never break there, so a
    // line never starts with "!" or ends with "«".
    private const val NO_BREAK_BEFORE = "!?:;»"
    private const val NO_BREAK_AFTER = "«"

    /** Whether [cp] takes no room and is never drawn. */
    fun isInvisible(cp: Int): Boolean {
        if (cp == '\t'.code || cp == '\n'.code || cp == '\r'.code) return false
        return when {
            cp < 0x20 || cp in 0x7F..0x9F -> true
            cp == 0x034F -> true // combining grapheme joiner
            cp in 0x200B..0x200F -> true // zero-width space, joiners, LRM, RLM
            cp in 0x202A..0x202E -> true // bidirectional embeddings and overrides
            cp in 0x2060..0x2064 || cp in 0x2066..0x206F -> true // word joiner, invisible operators, isolates
            cp == 0xFEFF -> true // zero-width no-break space
            cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF -> true // variation selectors
            cp in 0xE0000..0xE007F -> true // tags
            else -> Character.getType(cp) == Character.FORMAT.toInt()
        }
    }

    /** Whether [cp] belongs to the grapheme before it rather than starting one (marks, skin tones). */
    private fun extendsGrapheme(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() || cp in 0x1F3FB..0x1F3FF
    }

    @PublishedApi
    internal fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /**
     * Walks [text] unit by unit, with the rules above: [unit] receives each code point's range in the text
     * ([start] to [end]), the code point to draw (-1 for one that draws nothing), the pen position in dp, kerning
     * included, and the advance it takes. Returns the total advance.
     */
    inline fun forEachUnit(
        text: String, font: FontMetrics, size: Float,
        unit: (start: Int, end: Int, drawn: Int, x: Float, advance: Float) -> Unit,
    ): Float {
        var pen = 0f
        var previous = -1
        // The last drawn unit was a missing-glyph box, and how far its grapheme goes.
        var lastMissing = false
        var afterJoiner = false
        var regional = 0
        var i = 0
        while (i < text.length) {
            val raw = text.codePointAt(i)
            val end = i + Character.charCount(raw)
            val cp = if (raw == '\t'.code || raw == '\n'.code || raw == '\r'.code) SPACE else raw
            if (isInvisibleUnit(cp)) {
                if (cp == 0x200D) afterJoiner = true
                unit(i, end, -1, pen, 0f)
                i = end
                continue
            }
            val missing = !font.hasGlyph(cp)
            val pairsFlag = isRegionalIndicator(cp) && regional % 2 == 1
            // A missing character that continues a grapheme draws nothing: its grapheme has its box, or its base.
            val skip = missing && (extendsGraphemeUnit(cp) || afterJoiner || pairsFlag) && (lastMissing || !pairsFlag)
            regional = if (isRegionalIndicator(cp)) regional + 1 else 0
            afterJoiner = false
            if (skip) {
                unit(i, end, -1, pen, 0f)
                i = end
                continue
            }
            if (previous >= 0) pen += font.kerning(previous, cp) * size
            val advance = font.advance(cp) * size
            unit(i, end, cp, pen, advance)
            pen += advance
            previous = cp
            lastMissing = missing
            i = end
        }
        return pen
    }

    @PublishedApi
    internal fun isInvisibleUnit(cp: Int) = isInvisible(cp)

    @PublishedApi
    internal fun extendsGraphemeUnit(cp: Int) = extendsGrapheme(cp)

    /**
     * Walks [text] glyph by glyph: [glyph] receives each code point to draw and its pen position in dp, kerning
     * included. Returns the total advance.
     */
    inline fun forEachGlyph(text: String, font: FontMetrics, size: Float, glyph: (codePoint: Int, x: Float) -> Unit): Float =
        forEachUnit(text, font, size) { _, _, drawn, x, _ -> if (drawn >= 0) glyph(drawn, x) }

    fun width(text: String, font: FontMetrics, size: Float): Float = forEachGlyph(text, font, size) { _, _ -> }

    /** Distance in dp from the top of a line box of [style] to its baseline, with the leading split evenly. */
    fun baseline(style: TextStyle, font: FontMetrics): Float {
        val content = (font.ascender + font.descender) * style.size
        return (style.lineHeight - content) / 2f + font.ascender * style.size
    }

    /**
     * [text] if it fits in [maxWidth], else its longest start that fits followed by an ellipsis (spaces before the
     * ellipsis dropped). Empty when not even the ellipsis fits. Linear in the text's length.
     */
    fun truncate(text: String, font: FontMetrics, size: Float, maxWidth: Float): String {
        // ends[k]: width of the text up to the end of its k-th code point; starts[k]: where that code point starts;
        // drawnBefore[k]: the last drawn code point among the first k (for the kerning before the ellipsis).
        val count = text.codePointCount(0, text.length)
        val ends = FloatArray(count + 1)
        val starts = IntArray(count + 1)
        val drawnBefore = IntArray(count + 1) { -1 }
        val spaceBefore = BooleanArray(count + 1)
        var k = 0
        val total = forEachUnit(text, font, size) { start, _, drawn, x, advance ->
            starts[k] = start
            ends[k + 1] = if (drawn >= 0) x + advance else ends[k]
            drawnBefore[k + 1] = if (drawn >= 0) drawn else drawnBefore[k]
            spaceBefore[k + 1] = Character.isWhitespace(text.codePointAt(start))
            k++
        }
        starts[count] = text.length
        if (total <= maxWidth + EPSILON) return text

        // kept[n]: n without the whitespace that ends it, computed once (a long run of spaces stays linear).
        val kept = IntArray(count + 1)
        for (n in 1..count) kept[n] = if (spaceBefore[n]) kept[n - 1] else n
        val ellipsis = font.advance(ELLIPSIS_CP) * size
        for (n in count - 1 downTo 0) {
            val keep = kept[n]
            val last = drawnBefore[keep]
            val kern = if (keep > 0 && last >= 0) font.kerning(last, ELLIPSIS_CP) * size else 0f
            if (ends[keep] + kern + ellipsis <= maxWidth + EPSILON) return text.substring(0, starts[keep]) + ELLIPSIS
        }
        return ""
    }

    /**
     * One line of wrapped text: `text[start, end)` is drawn, [width] dp wide; [next] is where the following line
     * starts, past the space or line break this one broke at (equal to [end] for a word cut in two). Offsets are UTF-16
     * indexes into the wrapped text, for placing a caret, a selection or marks on characters.
     */
    data class Line(val start: Int, val end: Int, val next: Int, val width: Float)

    /**
     * Breaks [text] into lines no wider than [maxWidth]: at spaces, at line breaks, and inside a word only when the
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
            var feed = paragraphStart
            while (feed < text.length && text[feed] != '\n' && text[feed] != '\r') feed++
            wrapParagraph(text, paragraphStart, feed, font, size, maxWidth, out)
            if (feed >= text.length) break
            // The line break (\n, \r or \r\n) belongs to the paragraph's last line, like a break space.
            val next = if (text[feed] == '\r' && text.getOrNull(feed + 1) == '\n') feed + 2 else feed + 1
            out[out.size - 1] = out.last().copy(next = next)
            paragraphStart = next
        }
        return out
    }

    private fun isBreakSpace(text: String, i: Int) = (text[i] == ' ' || text[i] == '\t') && breakable(text, i)

    private fun wrapParagraph(text: String, from: Int, to: Int, font: FontMetrics, size: Float, maxWidth: Float, out: MutableList<Line>) {
        fun width(a: Int, b: Int) = width(text.substring(a, b), font, size)
        fun fits(a: Int, b: Int) = width(a, b) <= maxWidth + EPSILON
        val first = out.size
        var lineStart = from
        var lineEnd = from
        var hasContent = false
        var wordStart = from
        var i = from
        while (i <= to) {
            if (i < to && !isBreakSpace(text, i)) {
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
            if (!hasContent && wordStart == i && out.size > first) {
                // A space right after a break: the line it would start is not drawn with it.
                lineStart = i + 1
                lineEnd = i + 1
                if (i == to) break
                wordStart = i + 1
                i++
                continue
            }
            if (!hasContent) {
                var pieceStart = wordStart
                if (!fits(wordStart, i)) {
                    // Wider than a line alone: cut into pieces that fit, at least one code point each.
                    var j = wordStart
                    while (j < i) {
                        val next = j + Character.charCount(text.codePointAt(j))
                        if (j > pieceStart && !fits(pieceStart, next)) {
                            if (text[j] == ' ') {
                                // The space that glues French punctuation to the word ("…forteresse !"): it ends this
                                // piece rather than start the next one.
                                out.add(Line(pieceStart, j, next, width(pieceStart, j)))
                                pieceStart = next
                            } else {
                                out.add(Line(pieceStart, j, j, width(pieceStart, j)))
                                pieceStart = j
                            }
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
        // Empty here after earlier lines means the paragraph ended in spaces it broke at: they make no line of their
        // own, and the line before them reaches the paragraph's end.
        if (lineEnd > lineStart || out.size == first) {
            out.add(Line(lineStart, maxOf(lineStart, lineEnd), to, width(lineStart, maxOf(lineStart, lineEnd))))
        } else {
            out[out.size - 1] = out.last().copy(next = to)
        }
    }

    /** Splits a paragraph at the spaces a line may break at, as [wrap] does. */
    internal fun words(paragraph: String): List<String> {
        val words = ArrayList<String>()
        var start = 0
        for (i in paragraph.indices) {
            if (!isBreakSpace(paragraph, i)) continue
            words.add(paragraph.substring(start, i))
            start = i + 1
        }
        words.add(paragraph.substring(start))
        return words
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

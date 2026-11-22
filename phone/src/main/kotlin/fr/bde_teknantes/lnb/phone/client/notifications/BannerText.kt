package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.smooth.EmojiShaping
import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.Graphemes

/**
 * Makes a notification's title or text drawable by the banner's fonts (Inter: Latin, Greek and Cyrillic; the emoji
 * face: emoji; no CJK, Arabic or other scripts), which would otherwise draw an empty box for each grapheme they lack.
 * Plain logic over [FontMetrics], so it can be tested without the fonts. Decided grapheme by grapheme ([Graphemes]):
 *
 * - Line breaks, tabs and other control characters become spaces: a banner is one continuous snippet.
 * - What the fonts draw stays, emoji sequences whole (skin tones, joiners, flags, keycaps), as the apps draw them
 *   ([EmojiShaping]).
 * - Emoji the emoji face lacks (or all emoji, without an emoji face) are dropped with their companions: they decorate
 *   a sentence rather than carry it ("🎉 Party tonight" reads "Party tonight").
 * - Any other character the fonts lack becomes `?`, once per run, so a name in an unsupported script still shows that
 *   a name is there ("田中 : salut" reads "? : salut").
 * - A space the font lacks becomes a plain space; spaces are collapsed and trimmed (a no-break space stays, so the
 *   French space before ":" keeps its line).
 */
object BannerText {
    const val MISSING = "?"

    /** [clean] with Inter alone, as [hasGlyph] tells: without an emoji face. */
    fun clean(text: String, hasGlyph: (Int) -> Boolean): String = clean(text, object : FontMetrics {
        override fun advance(codePoint: Int) = 0f
        override fun kerning(left: Int, right: Int) = 0f
        override fun hasGlyph(codePoint: Int) = hasGlyph(codePoint)
        override val ascender = 0f
        override val descender = 0f
    })

    fun clean(text: String, font: FontMetrics): String {
        val out = StringBuilder(text.length)
        var pendingSpace = false
        var inMissingRun = false
        var start = 0
        while (start < text.length) {
            val end = Graphemes.next(text, start)
            val base = text.codePointAt(start)
            when {
                isBreakingSpace(base) || (Character.isSpaceChar(base) && !font.hasGlyph(base)) -> {
                    pendingSpace = true
                    inMissingRun = false
                }
                EmojiShaping.drawsAsEmoji(text, start, end, font) -> {
                    flushSpace(out, pendingSpace)
                    pendingSpace = false
                    inMissingRun = false
                    out.append(text, start, end)
                }
                isInvisible(base) -> Unit
                font.hasGlyph(base) -> {
                    flushSpace(out, pendingSpace)
                    pendingSpace = false
                    inMissingRun = false
                    // The letter and what the font draws of its accents; selectors, joiners and the rest go.
                    var i = start
                    while (i < end) {
                        val cp = text.codePointAt(i)
                        if (i == start || (!isInvisible(cp) && font.hasGlyph(cp))) out.appendCodePoint(cp)
                        i += Character.charCount(cp)
                    }
                }
                isEmojiPart(base) || isMark(base) -> Unit
                else -> {
                    flushSpace(out, pendingSpace)
                    pendingSpace = false
                    if (!inMissingRun) out.append(MISSING)
                    inMissingRun = true
                }
            }
            start = end
        }
        return out.toString()
    }

    private fun flushSpace(out: StringBuilder, pending: Boolean) {
        if (pending && out.isNotEmpty()) out.append(' ')
    }

    /** Whitespace a line may break at, and control characters, which all read as one space here. */
    private fun isBreakingSpace(cp: Int) = Character.isWhitespace(cp) || Character.isISOControl(cp)

    /** Formatting characters that never draw anything: joiners, direction marks, tags, variation selectors. */
    private fun isInvisible(cp: Int): Boolean =
        Character.getType(cp) == Character.FORMAT.toInt() ||
            cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF

    private fun isMark(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()
    }

    private fun isEmojiPart(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || // pictographs, emoticons, transport, flags (regional indicators), skin tones
            cp in 0x2600..0x27BF || // miscellaneous symbols and dingbats
            cp in 0x2B00..0x2BFF || // arrows and stars used as emoji
            cp == 0x20E3 || // keycap
            Character.getType(cp) == Character.OTHER_SYMBOL.toInt()
}

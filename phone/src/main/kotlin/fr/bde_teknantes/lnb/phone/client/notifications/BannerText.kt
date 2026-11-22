package fr.bde_teknantes.lnb.phone.client.notifications

/**
 * Makes a notification's title or text drawable by the banner's font (Inter: Latin, Greek and Cyrillic, no emoji, no
 * CJK, Arabic or other scripts), which would otherwise draw an empty box for each character it lacks. Plain logic over
 * a [hasGlyph] test, so it can be tested without the font.
 *
 * - Line breaks, tabs and other control characters become spaces: a banner is one continuous snippet.
 * - Emoji and their companions (skin tones, variation selectors, joiners, keycaps, flags, tags) are dropped: they
 *   decorate a sentence rather than carry it ("🎉 Party tonight" reads "Party tonight").
 * - Any other character the font lacks becomes `?`, once per run, so a name in an unsupported script still shows
 *   that a name is there ("田中 : salut" reads "? : salut").
 * - A space the font lacks becomes a plain space; spaces are collapsed and trimmed (a no-break space stays, so the
 *   French space before ":" keeps its line).
 */
object BannerText {
    const val MISSING = "?"

    fun clean(text: String, hasGlyph: (Int) -> Boolean): String {
        val out = StringBuilder(text.length)
        var pendingSpace = false
        var inMissingRun = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                isBreakingSpace(cp) || (Character.isSpaceChar(cp) && !hasGlyph(cp)) -> {
                    pendingSpace = true
                    inMissingRun = false
                }
                isEmojiPart(cp) && !hasGlyph(cp) || isInvisible(cp) -> Unit
                hasGlyph(cp) -> {
                    flushSpace(out, pendingSpace)
                    pendingSpace = false
                    inMissingRun = false
                    out.appendCodePoint(cp)
                }
                isMark(cp) -> Unit
                else -> {
                    flushSpace(out, pendingSpace)
                    pendingSpace = false
                    if (!inMissingRun) out.append(MISSING)
                    inMissingRun = true
                }
            }
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

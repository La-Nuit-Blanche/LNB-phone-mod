package fr.bde_teknantes.lnb.phone.client.smooth

/**
 * Glyph-level metrics of the emoji face, the second face of the smooth layer: it draws what Inter lacks and the
 * emoji a player expects. Kept apart from FreeType, like [FontMetrics], so the choices below can be tested from plain
 * numbers.
 */
interface EmojiMetrics {
    /** The face's glyph for [codePoint], or 0 when it has none. */
    fun glyph(codePoint: Int): Int

    /** How far the pen moves after [glyph], in em of the surrounding text's size. */
    fun advance(glyph: Int): Float

    /**
     * The ligature that starts at `glyphs[from]` among the glyphs before [count] (a sequence the face composes into
     * one glyph), packed as `(ligature glyph shl 32) or components`, or 0 when there is none.
     */
    fun ligature(glyphs: IntArray, from: Int, count: Int): Long = 0L

    /**
     * What [grapheme] shapes to, from a cache when the face keeps one: text is laid out again at every frame it is
     * drawn, and shaping a grapheme asks the font's tables, which a replaced font may make slow. [shape] computes it;
     * the result is never changed by the caller.
     */
    fun shaped(grapheme: String, shape: () -> IntArray): IntArray = shape()
}

/**
 * Which face draws a grapheme, and what an emoji grapheme draws. There is no shaping engine in the smooth layer: this
 * is the small part of one that emoji need, decided per grapheme ([Graphemes]), never per code point.
 *
 * **Which face.** A grapheme goes to the emoji face when the face has its first character and one of these holds:
 * - it asks for emoji: a variation selector 16 after the first character, a skin tone, a ZWJ, tags, or a keycap mark;
 * - its first character is emoji by default (Unicode's `Emoji_Presentation`: 😀, 🎉, a flag's letters);
 * - Inter lacks its first character (☺, ✔).
 *
 * Otherwise Inter draws it. So text-style symbols Inter has (© ® ™, ♥, ❤, ↔ without a selector) stay Inter's
 * in running text, and become emoji only when the selector asks (❤️, as emoji keyboards send it). Digits, `#` and `*`
 * are keycap bases: they go to the emoji face only with the keycap mark (1️⃣), never with the selector alone. A text
 * selector (15) after the first character keeps Inter's glyph when Inter has one.
 *
 * **What it draws.** The grapheme's characters, variation selectors left out, become the face's glyphs, composed by its
 * ligatures ([EmojiMetrics.ligature]); what remains uncomposed degrades to the base emoji and its visible parts:
 * - a skin tone the face can't compose with its base is dropped (never a stray swatch); the monochrome face draws
 *   toned emoji as their base anyway;
 * - a ZWJ sequence the face can't compose shows its emoji side by side (👨‍👩‍👧 as 👨👩👧), ZWJs drawing nothing;
 * - two regional indicators that make no known flag show their two letters;
 * - tags that make no known subdivision flag leave the black flag; a lone keycap mark draws nothing;
 * - a part the face lacks (a newer emoji after a ZWJ) is left out.
 */
object EmojiShaping {
    /** Marks a drawn unit as a glyph of the emoji face rather than a code point of Inter. */
    const val EMOJI_GLYPH = 1 shl 30

    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3

    fun isEmojiGlyph(drawn: Int): Boolean = drawn >= 0 && drawn and EMOJI_GLYPH != 0

    fun glyphOf(drawn: Int): Int = drawn and 0xFFFF

    /** Digits, `#` and `*`: plain characters that a keycap mark turns into emoji. */
    fun isKeycapBase(cp: Int): Boolean = cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code

    private fun isTag(cp: Int) = cp in 0xE0020..0xE007F

    private fun isMark(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }

    /** Whether the emoji face draws the grapheme `text[start, end)`, by the rules above. */
    fun drawsAsEmoji(text: String, start: Int, end: Int, font: FontMetrics): Boolean {
        val base = text.codePointAt(start)
        if (base <= 0x20 || Character.isWhitespace(base) || TextLayout.isInvisible(base) || isMark(base)) return false
        val afterBase = start + Character.charCount(base)
        // Most text is one plain character per grapheme: decided without looking further, or loading the face.
        val presentation = Character.isEmojiPresentation(base)
        if (afterBase == end && !presentation && font.hasGlyph(base)) return false

        var textSelector = false
        var asksForEmoji = false
        var keycap = false
        var i = afterBase
        while (i < end) {
            val cp = text.codePointAt(i)
            when {
                cp == VS16 && i == afterBase -> asksForEmoji = true
                cp == VS15 && i == afterBase -> textSelector = true
                cp == KEYCAP -> keycap = true
                cp == ZWJ || isTag(cp) || Character.isEmojiModifier(cp) -> asksForEmoji = true
            }
            i += Character.charCount(cp)
        }
        val interHas = font.hasGlyph(base)
        val wanted = when {
            isKeycapBase(base) -> keycap
            textSelector -> !interHas
            else -> asksForEmoji || keycap || presentation || !interHas
        }
        if (!wanted) return false
        val emoji = font.emoji ?: return false
        return emoji.glyph(base) != 0
    }

    /**
     * What the emoji grapheme `text[start, end)` draws, as triples `(glyph, from, to)`: each glyph of [emoji] (or -1
     * for a part that draws nothing) and the range of the text it stands for. The ranges follow each other and cover
     * the grapheme; the first triple is the base emoji, or the sequence composed from it. Asked once per grapheme and
     * face ([EmojiMetrics.shaped]), not at every frame.
     */
    fun shape(text: String, start: Int, end: Int, emoji: EmojiMetrics): IntArray {
        val grapheme = text.substring(start, end)
        val relative = emoji.shaped(grapheme) { compose(grapheme, 0, grapheme.length, emoji) }
        return IntArray(relative.size) { if (it % 3 == 0) relative[it] else relative[it] + start }
    }

    private fun compose(text: String, start: Int, end: Int, emoji: EmojiMetrics): IntArray {
        val capacity = text.codePointCount(start, end)
        val glyphs = IntArray(capacity)
        val chars = IntArray(capacity)
        val froms = IntArray(capacity)
        val tos = IntArray(capacity)
        val composed = BooleanArray(capacity)
        var n = 0
        var i = start
        while (i < end) {
            val cp = text.codePointAt(i)
            val next = i + Character.charCount(cp)
            if (cp in 0xFE00..0xFE0F && n > 0) {
                // A variation selector chose the face; the font's ligatures never list it.
                tos[n - 1] = next
            } else {
                glyphs[n] = emoji.glyph(cp)
                chars[n] = cp
                froms[n] = i
                tos[n] = next
                n++
            }
            i = next
        }

        var p = 0
        while (p < n) {
            val ligature = if (glyphs[p] != 0) emoji.ligature(glyphs, p, n) else 0L
            val length = (ligature and 0xFFFF).toInt()
            if (ligature != 0L && length >= 2 && p + length <= n) {
                glyphs[p] = (ligature ushr 32).toInt()
                tos[p] = tos[p + length - 1]
                composed[p] = true
                val removed = length - 1
                for (k in p + 1 until n - removed) {
                    glyphs[k] = glyphs[k + removed]
                    chars[k] = chars[k + removed]
                    froms[k] = froms[k + removed]
                    tos[k] = tos[k + removed]
                    composed[k] = composed[k + removed]
                }
                n -= removed
            }
            p++
        }

        val out = IntArray(3 * n)
        for (k in 0 until n) {
            val cp = chars[k]
            val drawn = when {
                k == 0 || composed[k] -> glyphs[k]
                glyphs[k] == 0 -> -1
                cp == ZWJ || isTag(cp) || cp == KEYCAP || Character.isEmojiModifier(cp) -> -1
                isMark(cp) || TextLayout.isInvisible(cp) -> -1
                else -> glyphs[k]
            }
            out[3 * k] = if (drawn > 0) drawn else -1
            out[3 * k + 1] = froms[k]
            out[3 * k + 2] = tos[k]
        }
        return out
    }
}

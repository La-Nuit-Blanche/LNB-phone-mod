package fr.bde_teknantes.lnb.phone.client.smooth

import java.text.BreakIterator
import java.text.StringCharacterIterator

/**
 * Grapheme boundaries: where a caret may stand and where text may be cut, so that an emoji with its skin tone, a
 * family joined with ZWJ, a flag, a keycap or a letter with its accents is never split.
 *
 * The rules are Unicode's extended grapheme clusters (UAX #29), as Java's character [BreakIterator] implements them
 * since Java 20, with one limit: a grapheme holds at most [MAX_LINKS] emoji joined to it by ZWJ. Unicode joins any
 * number, so a pasted chain of a hundred unrelated emoji would be one grapheme 1,600 dp wide that no line could break
 * and one Backspace would erase; no real sequence has more than three links (a family of four), so the chain becomes
 * groups of five emoji, cut before an emoji, never inside a real sequence. Pairs of common characters (Latin, French
 * punctuation, the euro sign) always have a boundary between them, so text made of them never reaches the iterator.
 * Positions are UTF-16 indexes. Thread-safe.
 */
object Graphemes {
    private val iterators = ThreadLocal.withInitial { BreakIterator.getCharacterInstance() }

    /** Characters long enough for any real grapheme; a longer one (a pile of combining marks) widens the window. */
    private const val WINDOW = 32

    /**
     * Whether a boundary always separates [c] from a neighbour that is also simple: no grapheme rule joins two of
     * these, except a carriage return with a line feed, handled apart.
     */
    private fun simple(c: Char): Boolean =
        c < '̀' || c in '‐'..'‧' || c in '‰'..'⁞' || c in '₠'..'⃀'

    /** Emoji joined by ZWJ after the first one of a grapheme, at most: three in the longest real sequence. */
    const val MAX_LINKS = 4

    /**
     * The start of the link that would be one too many in `text[start, end)` (an emoji right after a ZWJ, the
     * [MAX_LINKS] + 1st), or -1 when there are not that many.
     */
    private fun tooManyLinks(text: String, start: Int, end: Int): Int {
        var links = 0
        var previous = -1
        var i = start
        while (i < end) {
            val cp = text.codePointAt(i)
            if (previous == 0x200D && Character.isExtendedPictographic(cp) && ++links > MAX_LINKS) return i
            previous = cp
            i += Character.charCount(cp)
        }
        return -1
    }

    /** The end of the grapheme that starts at [start], which must be a boundary. [start] itself at the end. */
    fun next(text: String, start: Int): Int {
        if (start >= text.length) return text.length
        val c = text[start]
        if (start + 1 == text.length) return text.length
        val following = text[start + 1]
        if (simple(c) && simple(following)) return if (c == '\r' && following == '\n') start + 2 else start + 1
        val iterator = iterators.get()
        var window = WINDOW
        while (true) {
            var end = minOf(text.length, start + window)
            // Never cut a surrogate pair: half of one is a lone surrogate, which no grapheme rule joins to what precedes.
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end++
            iterator.setText(StringCharacterIterator(text, start, end, start))
            val boundary = iterator.following(start)
            val found = boundary != BreakIterator.DONE && (boundary < end || end == text.length)
            // Everything before the boundary, or the whole window when none was found in it, is inside the grapheme:
            // one link too many there ends it, without reading further.
            val cut = tooManyLinks(text, start, if (found) boundary else end)
            if (cut > start) return cut
            // A boundary at the window's end may only be where the window cut the grapheme: look further.
            if (found) return boundary
            if (end == text.length) return text.length
            window *= 2
        }
    }

    /**
     * A boundary at or before [index] from which a forward walk finds every later boundary: the start of the text, or
     * a position between two plain ASCII characters other than a line end.
     */
    private fun anchorBefore(text: String, index: Int): Int {
        var i = minOf(index, text.length)
        while (i > 0) {
            val a = text[i - 1]
            val b = if (i < text.length) text[i] else ' '
            if (a < '\u0080' && b < '\u0080' && a != '\r' && a != '\n' && b != '\r' && b != '\n') return i
            i--
        }
        return 0
    }

    /** The last boundary at or before [index]. */
    fun floor(text: String, index: Int): Int {
        if (index <= 0) return 0
        if (index >= text.length) return text.length
        var at = anchorBefore(text, index)
        while (true) {
            val next = next(text, at)
            if (next > index) return at
            at = next
            if (at == index) return at
        }
    }

    /** The first boundary at or after [index]. */
    fun ceil(text: String, index: Int): Int {
        val floor = floor(text, index)
        return if (floor == index.coerceIn(0, text.length)) floor else next(text, floor)
    }

    /** The boundary before [index] (the start of the grapheme it ends), 0 at the start. */
    fun previous(text: String, index: Int): Int = if (index <= 0) 0 else floor(text, ceil(text, index) - 1)

    /** Whether a caret may stand at [index]. */
    fun isBoundary(text: String, index: Int): Boolean = index in 0..text.length && floor(text, index) == index

    /** Every boundary of [text] as a mask over its UTF-16 indexes, 0 and the end included. */
    fun boundaries(text: String): BooleanArray {
        val mask = BooleanArray(text.length + 1)
        var i = 0
        mask[0] = true
        while (i < text.length) {
            i = next(text, i)
            mask[i] = true
        }
        return mask
    }
}

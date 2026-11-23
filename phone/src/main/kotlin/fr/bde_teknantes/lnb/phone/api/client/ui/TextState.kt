package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.text.Graphemes

/**
 * The content of a [TextField] or [TextArea]: the text, the caret and the selection, with the editing operations.
 *
 * A field creates its own state unless you give it one. Give it one you keep elsewhere (an object of your app that
 * lives for the session) when the text must survive the screen: a draft kept when the player leaves Compose and comes
 * back. The screen is rebuilt each time; the state you pass in is what carries over. Make one with [singleLine] (for a
 * [TextField]) or [multiline] (for a [TextArea]):
 *
 * ```kotlin
 * object Drafts {
 *     val post = TextState.multiline()
 *     val name = TextState.singleLine(maxLength = 16)
 * }
 * TextArea(Drafts.post).apply { placeholder = "What's new?"; limit = 280 }
 * ```
 *
 * Positions are indexes into [text] (UTF-16 units, as everywhere in Kotlin), always between graphemes, what a reader
 * sees as one character: the caret never stands inside an emoji with its skin tone, a family joined with ZWJ, a flag
 * or a keycap, and arrows, Backspace and Delete take one whole. Characters that can't be typed are filtered out of what is inserted (control
 * characters, the section sign Minecraft uses for formatting; line feeds unless [multiline]), and the text is kept in
 * Unicode normalisation form C, so a pasted `e` plus combining accent becomes the one character `é`, drawn and counted
 * as such ([TextLimits]). Plain logic: no game classes, usable in tests.
 *
 * @property multiline Whether line feeds are kept (a text area) or turned into spaces (a single-line field).
 * @property maxLength The most characters (code points) the text can hold; what would go past is cut. Separate from a
 * text area's visible limit, which may be exceeded and is then marked.
 */
class TextState internal constructor(initial: String, val multiline: Boolean, val maxLength: Int) {
    internal constructor() : this("", true, DEFAULT_MAX_LENGTH)

    internal constructor(initial: String) : this(initial, true, DEFAULT_MAX_LENGTH)

    internal constructor(initial: String, multiline: Boolean) : this(initial, multiline, DEFAULT_MAX_LENGTH)
    private var content = ""

    /** Grapheme boundaries of [content], found on first use after each change: stepping over a long run of emoji by
     *  asking [Graphemes] for each boundary again would walk the text from its start every time. */
    private var boundaryMask: BooleanArray? = null
    private val listeners = ArrayList<(TextState) -> Unit>()

    /** Where the caret is. */
    var caret: Int = 0
        private set

    /** The other end of the selection; equal to [caret] when nothing is selected. */
    var anchor: Int = 0
        private set

    /** Increases at every change of the text, for caches. */
    var version: Int = 0
        private set

    init {
        require(maxLength > 0) { "maxLength must be positive" }
        content = TextLimits.normalize(clean(initial, replacedLength = 0))
        caret = content.length
        anchor = caret
    }

    /** The text. Setting it puts the caret at the end, with no selection. */
    var text: String
        get() = content
        set(value) {
            val cleaned = TextLimits.normalize(clean(value, replacedLength = length))
            replaceAll(cleaned, cleaned.length)
        }

    val selectionStart: Int get() = minOf(caret, anchor)
    val selectionEnd: Int get() = maxOf(caret, anchor)
    val hasSelection: Boolean get() = caret != anchor
    val selectedText: String get() = content.substring(selectionStart, selectionEnd)

    /**
     * Number of characters as limits count them ([TextLimits]): code points of the normalised text, which is how the
     * text is stored, so `é` is one however it was typed or pasted, and an emoji is one.
     */
    val length: Int get() = content.codePointCount(0, content.length)

    /** Whether the text is empty once spaces and line feeds are trimmed: nothing to send. */
    val isBlank: Boolean get() = content.isBlank()

    /** Calls [listener] after each change of the text (not of the caret alone). */
    fun addListener(listener: (TextState) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (TextState) -> Unit) {
        listeners -= listener
    }

    /** Empties the text. */
    fun clear() {
        text = ""
    }

    // ---- Editing ----

    /** Replaces the selection (or inserts at the caret) with [typed], filtered; the caret ends after it. */
    fun insert(typed: String) {
        val start = selectionStart
        val end = selectionEnd
        val cleaned = clean(typed, replacedLength = content.codePointCount(start, end))
        if (cleaned.isEmpty() && start == end) return
        // Normalised with what is around it: a combining accent typed after its letter becomes one character.
        val before = TextLimits.normalize(content.substring(0, start) + cleaned)
        replaceAll(TextLimits.normalize(before + content.substring(end)), before.length)
    }

    /** Backspace: deletes the selection, or the character (with [word], the word) before the caret. */
    fun deleteBackward() = deleteBackward(word = false)

    /** Backspace with Ctrl when [word]: the word before the caret. */
    fun deleteBackward(word: Boolean) {
        if (hasSelection) return insert("")
        if (caret == 0) return
        val start = if (word) previousWord(caret) else previous(caret)
        replaceAll(content.substring(0, start) + content.substring(caret), start)
    }

    /** Delete: deletes the selection, or the character (with [word], the word) after the caret. */
    fun deleteForward() = deleteForward(word = false)

    /** Delete with Ctrl when [word]: the word after the caret. */
    fun deleteForward(word: Boolean) {
        if (hasSelection) return insert("")
        if (caret == content.length) return
        val end = if (word) nextWord(caret) else next(caret)
        replaceAll(content.substring(0, caret) + content.substring(end), caret)
    }

    // ---- Caret and selection ----

    /** Moves the caret to [index] (snapped back to a grapheme boundary); with [select], extends the selection. */
    fun moveTo(index: Int) = moveTo(index, select = false)

    /** [moveTo], extending the selection when [select]. */
    fun moveTo(index: Int, select: Boolean) {
        caret = boundary(index.coerceIn(0, content.length))
        if (!select) anchor = caret
    }

    /** Left arrow: one character, or one word with [word]. Without [select], a selection collapses to its start. */
    fun moveLeft() = moveLeft(select = false, word = false)

    /** [moveLeft] with Shift ([select]) and Ctrl ([word]). */
    fun moveLeft(select: Boolean, word: Boolean) {
        if (hasSelection && !select) return moveTo(selectionStart)
        moveTo(if (word) previousWord(caret) else previous(caret), select)
    }

    /** Right arrow: one character, or one word with [word]. Without [select], a selection collapses to its end. */
    fun moveRight() = moveRight(select = false, word = false)

    /** [moveRight] with Shift ([select]) and Ctrl ([word]). */
    fun moveRight(select: Boolean, word: Boolean) {
        if (hasSelection && !select) return moveTo(selectionEnd)
        moveTo(if (word) nextWord(caret) else next(caret), select)
    }

    /** Selects everything. */
    fun selectAll() {
        anchor = 0
        caret = content.length
    }

    /**
     * Selects the word (or run of spaces, or of punctuation) around [index], as a double click does. At the boundary
     * between a word and what follows it, the word wins: a click on the right half of a word's last letter lands
     * there, and the player meant the word.
     */
    fun selectWordAt(index: Int) {
        if (content.isEmpty()) return
        val i = boundary(index.coerceIn(0, content.length))
        val wordBefore = i > 0 && kindAt(previous(i)) == 1
        val from = if (i == content.length || (wordBefore && kindAt(i) != 1)) previous(i) else i
        val kind = kindAt(from)
        var start = from
        var end = start
        while (start > 0 && kindAt(previous(start)) == kind) start = previous(start)
        while (end < content.length && kindAt(end) == kind) end = next(end)
        anchor = start
        caret = end
    }

    // ---- Internals ----

    private fun replaceAll(newText: String, newCaret: Int) {
        val changed = newText != content
        content = newText
        boundaryMask = null
        // Forward: what was just typed may have joined the grapheme before it (a skin tone, a ZWJ and the next emoji),
        // and the caret goes after it rather than back before the whole sequence.
        caret = Graphemes.ceil(newText, newCaret.coerceIn(0, newText.length))
        anchor = caret
        if (changed) {
            version++
            listeners.toList().forEach { it(this) }
        }
    }

    /** [typed] without what can't be typed, cut so that the text stays within [maxLength] once [replacedLength] code points go. */
    private fun clean(typed: String, replacedLength: Int): String {
        val out = StringBuilder(typed.length)
        var i = 0
        while (i < typed.length) {
            val cp = typed.codePointAt(i)
            i += Character.charCount(cp)
            when {
                // Windows line ends from the clipboard: one line feed.
                cp == '\r'.code -> if (i < typed.length && typed[i] == '\n') continue else out.append(if (multiline) '\n' else ' ')
                cp == '\n'.code -> out.append(if (multiline) '\n' else ' ')
                cp == '\t'.code -> out.append(' ')
                isAllowed(cp) -> out.appendCodePoint(cp)
            }
        }
        val room = maxLength - (content.codePointCount(0, content.length) - replacedLength)
        if (room <= 0) return ""
        val count = out.codePointCount(0, out.length)
        if (count <= room) return out.toString()
        // Cut between graphemes: half a family would be a man and a dangling joiner, not what was pasted.
        return out.substring(0, Graphemes.floor(out.toString(), out.offsetByCodePoints(0, room)))
    }

    private fun mask(): BooleanArray = boundaryMask ?: Graphemes.boundaries(content).also { boundaryMask = it }

    /** The boundary before [index]: the start of the grapheme that ends at it, or that it is inside. 0 at the start. */
    private fun previous(index: Int): Int {
        val mask = mask()
        var i = minOf(index, content.length) - 1
        while (i > 0 && !mask[i]) i--
        return maxOf(i, 0)
    }

    /** The boundary after [index], where the grapheme that starts at it ends. */
    private fun next(index: Int): Int {
        val mask = mask()
        var i = maxOf(index, -1) + 1
        while (i < content.length && !mask[i]) i++
        return minOf(i, content.length)
    }

    /** [index], moved back to the start of the grapheme it is inside. */
    private fun boundary(index: Int): Int {
        val mask = mask()
        var i = index.coerceIn(0, content.length)
        while (i > 0 && !mask[i]) i--
        return i
    }

    /** 0: space, 1: word character, 2: anything else (punctuation). */
    private fun kindAt(index: Int): Int {
        val cp = content.codePointAt(index)
        return when {
            Character.isWhitespace(cp) -> 0
            Character.isLetterOrDigit(cp) || cp == '_'.code || cp == '\''.code || cp == '’'.code -> 1
            else -> 2
        }
    }

    /** Ctrl+Left: back over spaces, then over the word (or punctuation run) before them. */
    private fun previousWord(index: Int): Int {
        var i = index
        while (i > 0 && kindAt(previous(i)) == 0) i = previous(i)
        if (i == 0) return 0
        val kind = kindAt(previous(i))
        while (i > 0 && kindAt(previous(i)) == kind) i = previous(i)
        return i
    }

    /** Ctrl+Right: over the word (or punctuation run) at the caret, then over the spaces after it. */
    private fun nextWord(index: Int): Int {
        var i = index
        if (i < content.length) {
            val kind = kindAt(i)
            if (kind != 0) while (i < content.length && kindAt(i) == kind) i = next(i)
        }
        while (i < content.length && kindAt(i) == 0) i = next(i)
        return i
    }

    override fun toString() = "TextState(\"$content\", caret $caret, anchor $anchor)"

    companion object {
        /** Far beyond any limit a field shows; only there so a huge paste can't stall the game. */
        val DEFAULT_MAX_LENGTH = 4000

        /** An empty single-line text, for a [TextField]: line feeds typed or pasted become spaces. */
        fun singleLine(): TextState = TextState("", false, DEFAULT_MAX_LENGTH)

        /** An empty single-line text of at most [maxLength] characters: what would go past is cut as it comes. */
        fun singleLine(maxLength: Int): TextState = TextState("", false, maxLength)

        /** An empty text of several lines, for a [TextArea]. */
        fun multiline(): TextState = TextState("", true, DEFAULT_MAX_LENGTH)

        /** An empty text of several lines and at most [maxLength] characters: what would go past is cut as it comes. */
        fun multiline(maxLength: Int): TextState = TextState("", true, maxLength)

        /** Whether [codePoint] may be typed: not a control character, not Minecraft's section sign, not half a surrogate pair. */
        fun isAllowed(codePoint: Int): Boolean =
            codePoint >= 0x20 && codePoint != 0x7F && codePoint !in 0x80..0x9F && codePoint != 0xA7 &&
                codePoint !in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code
    }
}

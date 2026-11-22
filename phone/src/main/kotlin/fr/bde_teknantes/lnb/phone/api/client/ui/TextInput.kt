package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout
import fr.bde_teknantes.lnb.phone.client.ui.EditCommand
import fr.bde_teknantes.lnb.phone.client.ui.EditKey
import fr.bde_teknantes.lnb.phone.client.ui.TextEnvironment
import fr.bde_teknantes.lnb.phone.client.ui.TextKeys
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * What [TextField] and [TextArea] share: the caret blinking 500 ms on, 500 ms off, selection with the mouse and Shift,
 * word moves with Ctrl (Alt on a Mac), the clipboard, double click to select a word, and the focus. Typed text arrives
 * whole, accents and dead keys included.
 *
 * While one has the focus, the screen's [hasTextFocus][fr.bde_teknantes.lnb.phone.api.client.AppScreen.hasTextFocus] is
 * true and every key but Esc and Tab belongs to it, letters included: the phone leaves Backspace and its own key to it,
 * and neither your screen's shortcuts nor other mods see what the player types.
 */
sealed class TextInput(val state: TextState) : Widget() {
    private var lastActivity = 0f
    private var lastClickAt = Float.NEGATIVE_INFINITY
    private var lastClickX = 0f
    private var lastClickY = 0f
    private var selecting = false
    private var notifiedVersion = state.version
    private var revealedVersion = -1
    private var goalX: Float? = null

    /** Grey text shown while the field is empty. */
    var placeholder: String = ""

    /**
     * Called after each change of the text: at once for the player's typing, by the next frame for changes made by
     * code (a draft restored, a field cleared after sending). The widget asks; it never registers on the state, so a
     * long-lived [TextState] keeps no reference to the screen.
     */
    var onChange: ((TextState) -> Unit)? = null

    /** Called when the player submits: Enter in a field, Ctrl+Enter in a text area. */
    var onSubmit: (() -> Unit)? = null

    /** Called when the field gains (true) or loses (false) the focus: a collapsed composer expands on focus. */
    var onFocusChange: ((Boolean) -> Unit)? = null

    /** Draws the focused look (border and a steady caret) without the focus: for galleries and screenshots. */
    @KitPreview
    var showFocused = false

    /** Draws the hover look without the pointer: for galleries and screenshots. */
    @KitPreview
    var showHovered = false

    /** Whether the text can be changed now (not while sending). */
    protected open val editable: Boolean get() = isEnabled

    override val catchesPointer get() = true
    override val focusable get() = isEnabled
    override val acceptsText get() = isEnabled

    /** The text, as a shortcut for [state]'s. */
    var text: String
        get() = state.text
        set(value) {
            state.text = value
        }

    @OptIn(KitPreview::class)
    protected val looksFocused: Boolean get() = isFocused || showFocused

    @OptIn(KitPreview::class)
    protected val looksHovered: Boolean get() = isHovered || showHovered

    /** Whether the caret shows now: focused, editable, and in the "on" half of its blink (always on just after a key). */
    @OptIn(KitPreview::class)
    protected val caretVisible: Boolean
        get() = (showFocused && editable) ||
            (isFocused && editable && ((now - lastActivity) % (2 * Motion.CARET_BLINK_MS)) < Motion.CARET_BLINK_MS)

    /** The border colour and thickness for the current state. */
    protected fun border(error: Boolean): Pair<Int, Float> = when {
        !isEnabled -> Colors.HAIRLINE to 1f
        error -> Colors.DANGER to 2f
        looksFocused && editable -> Colors.ACCENT to 2f
        looksHovered && editable -> Colors.STROKE_HOVER to 1f
        else -> Colors.STROKE to 1f
    }

    override fun focusChanged(focused: Boolean) {
        lastActivity = now
        if (!focused) selecting = false
        onFocusChange?.invoke(focused)
    }

    override fun update() {
        // Changes made by code since the last frame (and the text the field opened with): the caret comes into view.
        if (state.version != revealedVersion) {
            revealedVersion = state.version
            revealCaret()
        }
        if (state.version != notifiedVersion) {
            notifiedVersion = state.version
            onChange?.invoke(state)
        }
    }

    // ---- Layout, by subclass ----

    /** The caret position nearest to the point ([x], [y]) of the widget. */
    protected abstract fun indexAt(x: Float, y: Float): Int

    /** Where Home and End lead from [index]: the start and end of its line. */
    protected abstract fun lineStart(index: Int): Int
    protected abstract fun lineEnd(index: Int): Int

    /** Distance of [index] from the start of its line, in dp: the column Up and Down keep. */
    protected abstract fun columnOf(index: Int): Float

    /** The index one line up ([lines] < 0) or down from [index], nearest to column [x], or null past the first or last line. */
    protected abstract fun verticalMove(index: Int, lines: Int, x: Float): Int?

    /** Scrolls so the caret is visible. */
    protected abstract fun revealCaret()

    // ---- Input ----

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true
        if (!isEnabled) return true
        val index = indexAt(x, y)
        // A double click is two presses close in time and place; the caret may land a character apart.
        val double = now - lastClickAt < DOUBLE_CLICK_MS && abs(x - lastClickX) <= DOUBLE_CLICK_DP && abs(y - lastClickY) <= DOUBLE_CLICK_DP
        goalX = null
        if (double) {
            state.selectWordAt(index)
            lastClickAt = Float.NEGATIVE_INFINITY
        } else {
            state.moveTo(index, select = TextEnvironment.current.shiftDown)
            lastClickAt = now
            lastClickX = x
            lastClickY = y
            selecting = true
        }
        lastActivity = now
        return true
    }

    override fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (!selecting || !isEnabled) return false
        state.moveTo(indexAt(x, y), select = true)
        revealCaret()
        lastActivity = now
        return true
    }

    override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        selecting = false
        return true
    }

    override fun kitPointerLost() {
        selecting = false
    }

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean {
        if (!isEnabled) return false
        if (!editable) return true
        val before = state.version
        goalX = null
        state.insert(String(Character.toChars(codePoint)))
        edited(before)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isEnabled) return false
        // Esc keeps its meaning (close the overlay or the phone) and Tab moves the focus; every other key is the
        // field's, even one it doesn't use (a letter, which arrives through charTyped; a dead key; AltGr).
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_TAB) return false
        val key = TextKeys.read(keyCode, modifiers, state.multiline, TextEnvironment.current.mac) ?: return true
        val before = state.version
        apply(key)
        edited(before)
        return true
    }

    private fun apply(key: EditKey) {
        val s = state
        if (key.command != EditCommand.UP && key.command != EditCommand.DOWN) goalX = null
        // While sending, the text stays as it is: moving, selecting and copying are all that work.
        val changes = when (key.command) {
            EditCommand.DELETE_BACKWARD, EditCommand.DELETE_FORWARD, EditCommand.CUT, EditCommand.PASTE,
            EditCommand.NEWLINE, EditCommand.SUBMIT -> true
            else -> false
        }
        if (changes && !editable) return
        when (key.command) {
            EditCommand.LEFT -> s.moveLeft(key.select, key.word)
            EditCommand.RIGHT -> s.moveRight(key.select, key.word)
            EditCommand.UP, EditCommand.DOWN -> {
                val column = goalX ?: columnOf(s.caret).also { goalX = it }
                val up = key.command == EditCommand.UP
                s.moveTo(verticalMove(s.caret, if (up) -1 else 1, column) ?: if (up) 0 else s.text.length, key.select)
            }
            EditCommand.LINE_START -> s.moveTo(lineStart(s.caret), key.select)
            EditCommand.LINE_END -> s.moveTo(lineEnd(s.caret), key.select)
            EditCommand.TEXT_START -> s.moveTo(0, key.select)
            EditCommand.TEXT_END -> s.moveTo(s.text.length, key.select)
            EditCommand.DELETE_BACKWARD -> s.deleteBackward(key.word)
            EditCommand.DELETE_FORWARD -> s.deleteForward(key.word)
            EditCommand.SELECT_ALL -> s.selectAll()
            EditCommand.COPY -> if (s.hasSelection) clipboard = s.selectedText
            EditCommand.CUT -> if (s.hasSelection) {
                clipboard = s.selectedText
                s.insert("")
            }
            EditCommand.PASTE -> s.insert(clipboard)
            EditCommand.NEWLINE -> s.insert("\n")
            // Once per press: a held Enter types nothing, and must not send twenty times a second.
            EditCommand.SUBMIT -> if (!isKeyRepeat) onSubmit?.invoke()
        }
    }

    private fun edited(versionBefore: Int) {
        lastActivity = now
        revealCaret()
        revealedVersion = state.version
        if (state.version != versionBefore) {
            notifiedVersion = state.version
            onChange?.invoke(state)
        }
    }

    private var clipboard: String
        get() = TextEnvironment.current.clipboard
        set(value) {
            TextEnvironment.current.clipboard = value
        }

    // ---- Drawing helpers ----

    /** Draws the selection behind `text[start, end)` of a line drawn from [x], [y]. */
    protected fun drawSelection(canvas: AppCanvas, line: String, lineStart: Int, lineEnd: Int, x: Float, y: Float) {
        if (!state.hasSelection || !looksFocused) return
        val from = state.selectionStart.coerceIn(lineStart, lineEnd)
        val to = state.selectionEnd.coerceIn(lineStart, lineEnd)
        if (from >= to) return
        val x0 = canvas.measure(line.substring(0, from - lineStart), TextStyle.BODY)
        val x1 = canvas.measure(line.substring(0, to - lineStart), TextStyle.BODY)
        canvas.fillRect(x + x0, y, x1 - x0, TextStyle.BODY.lineHeight, Colors.SELECTION)
    }

    /** The caret, 2 x 18 dp of accent, 1 dp after the character at [x] (as the mockups place it). */
    protected fun drawCaret(canvas: AppCanvas, x: Float, y: Float) {
        if (caretVisible) canvas.fillRect(x + CARET_GAP, y, CARET_WIDTH, TextStyle.BODY.lineHeight, Colors.ACCENT)
    }

    /** Where the placeholder starts: after the caret while it is focused, so the caret never covers its first letter. */
    protected val placeholderX: Float
        get() = if (looksFocused && editable) PADDING + CARET_WIDTH + CARET_GAP else PADDING

    protected companion object {
        const val PADDING = 12f
        const val CARET_WIDTH = 2f
        const val CARET_GAP = 1f
        const val DOUBLE_CLICK_MS = 400f
        const val DOUBLE_CLICK_DP = 4f
    }
}

/**
 * A single-line text field (art direction 8.7): 40 dp tall, 232 wide by default, the [placeholder] in grey until
 * something is typed. Enter calls [onSubmit]. With [error] set, a red border and the message under the field.
 *
 * Without a state of its own, it creates a single-line one; pass a [TextState] of yours (`TextState("", false)`)
 * to keep what is typed.
 */
class TextField(state: TextState) : TextInput(state) {
    /** A field with a text of its own. */
    constructor() : this(TextState("", false))

    private var scrollX = 0f

    /** An error message shown in red under the field (4 dp below it), with a red border; null for none. */
    var error: String? = null

    override val preferredWidth get() = 232f
    override val preferredHeight get() = 40f

    private val innerWidth get() = width - 2 * PADDING

    override fun indexAt(x: Float, y: Float): Int {
        val line = TextLayout.Line(0, text.length, text.length, 0f)
        return TextLayout.indexAt(text, line, x - PADDING + scrollX, TextMetrics.font(TextStyle.BODY), TextStyle.BODY.size)
    }

    override fun lineStart(index: Int) = 0
    override fun lineEnd(index: Int) = text.length
    override fun columnOf(index: Int) = TextMetrics.measure(text.substring(0, index), TextStyle.BODY)
    override fun verticalMove(index: Int, lines: Int, x: Float): Int? = null

    override fun revealCaret() {
        val caretX = TextMetrics.measure(text.substring(0, state.caret), TextStyle.BODY)
        val total = TextMetrics.measure(text, TextStyle.BODY)
        val room = innerWidth - CARET_WIDTH
        scrollX = when {
            caretX - scrollX > room -> caretX - room
            caretX < scrollX -> caretX
            else -> scrollX
        }.coerceIn(0f, maxOf(0f, total - room))
    }

    override fun render(canvas: AppCanvas) {
        val (borderColor, thickness) = border(error != null)
        canvas.fillRoundRect(0f, 0f, width, height, Radius.SMALL, if (isEnabled) Colors.FIELD else Colors.BG)
        canvas.strokeRoundRect(0f, 0f, width, height, Radius.SMALL, thickness, borderColor)
        val y = (height - TextStyle.BODY.lineHeight) / 2f
        canvas.pushClip(PADDING - CARET_WIDTH, 0f, innerWidth + 2 * CARET_WIDTH, height)
        if (text.isEmpty()) {
            canvas.drawTextTruncated(placeholder, placeholderX, y, innerWidth - (placeholderX - PADDING), TextStyle.BODY, Colors.TEXT_3)
            drawCaret(canvas, PADDING - CARET_GAP, y)
        } else {
            val x = PADDING - scrollX
            drawSelection(canvas, text, 0, text.length, x, y)
            canvas.drawText(text, x, y, TextStyle.BODY, if (isEnabled) Colors.TEXT_1 else Colors.TEXT_3)
            drawCaret(canvas, x + canvas.measure(text.substring(0, state.caret), TextStyle.BODY), y)
        }
        canvas.popClip()
        error?.let { canvas.drawTextTruncated(it, 0f, height + 4f, width, TextStyle.CAPTION, Colors.DANGER) }
    }
}

/**
 * A multi-line text area with a character counter (art direction 8.7): the text wraps and scrolls inside the box when
 * longer than it. With a [limit], the bottom 28 dp hold the counter (`142/280`, amber in the last 20 characters, `-5` in
 * red past the limit) and the characters past the limit get a red background. Enter starts a new line; Ctrl+Enter
 * calls [onSubmit].
 *
 * Whether the text can be sent is [TextLimits.canSend][fr.bde_teknantes.lnb.phone.api.TextLimits.canSend]: enable your
 * send button with it in [onChange]. Send the text trimmed.
 *
 * Without a state of its own, it creates one; pass a [TextState] kept by your app to keep a draft.
 */
class TextArea(state: TextState) : TextInput(state) {
    /** A text area with a text of its own. */
    constructor() : this(TextState())

    private var scrollY = 0f

    /** The number of characters allowed, shown by the counter; null for no counter. */
    var limit: Int? = null

    /**
     * The sending state: the text turns grey and the caret goes, while what was typed is on its way. The field keeps
     * the focus and its keys (so Backspace never leaves the screen), but the text can't change; it gets the caret back
     * when set to false.
     */
    var sending = false

    override val editable get() = isEnabled && !sending

    override val preferredWidth get() = 232f
    override val preferredHeight get() = 112f

    private val innerWidth get() = width - 2 * PADDING
    private val footer get() = if (limit != null) FOOTER else PADDING
    private val innerHeight get() = height - PADDING - footer

    private fun lines() = TextMetrics.lines(text, TextStyle.BODY, innerWidth)

    override fun indexAt(x: Float, y: Float): Int {
        val lines = lines()
        val n = ((y - PADDING + scrollY) / TextStyle.BODY.lineHeight).toInt().coerceIn(0, lines.size - 1)
        return TextLayout.indexAt(text, lines[n], x - PADDING, TextMetrics.font(TextStyle.BODY), TextStyle.BODY.size)
    }

    override fun lineStart(index: Int): Int {
        val lines = lines()
        return lines[TextLayout.lineOf(lines, index)].start
    }

    override fun lineEnd(index: Int): Int {
        val lines = lines()
        return lines[TextLayout.lineOf(lines, index)].end
    }

    override fun columnOf(index: Int): Float {
        val lines = lines()
        val line = lines[TextLayout.lineOf(lines, index)]
        return TextMetrics.measure(text.substring(line.start, index.coerceIn(line.start, line.end)), TextStyle.BODY)
    }

    override fun verticalMove(index: Int, lines: Int, x: Float): Int? {
        val all = lines()
        val target = TextLayout.lineOf(all, index) + lines
        if (target !in all.indices) return null
        return TextLayout.indexAt(text, all[target], x, TextMetrics.font(TextStyle.BODY), TextStyle.BODY.size)
    }

    override fun revealCaret() {
        val lines = lines()
        val top = TextLayout.lineOf(lines, state.caret) * TextStyle.BODY.lineHeight
        val bottom = top + TextStyle.BODY.lineHeight
        scrollY = when {
            top < scrollY -> top
            bottom > scrollY + innerHeight -> bottom - innerHeight
            else -> scrollY
        }
        clampScroll(lines.size)
    }

    private fun clampScroll(lineCount: Int) {
        scrollY = scrollY.coerceIn(0f, maxOf(0f, lineCount * TextStyle.BODY.lineHeight - innerHeight))
    }

    override fun mouseScrolled(x: Float, y: Float, notches: Float): Boolean {
        val lines = lines()
        val max = lines.size * TextStyle.BODY.lineHeight - innerHeight
        if (max <= 0f) return false
        // Two lines per notch; at either end the wheel stays here rather than scrolling the screen behind.
        scrollY -= notches * TextStyle.BODY.lineHeight * 2
        clampScroll(lines.size)
        return true
    }

    override fun render(canvas: AppCanvas) {
        val (borderColor, thickness) = if (sending) Colors.STROKE to 1f else border(false)
        canvas.fillRoundRect(0f, 0f, width, height, Radius.SMALL, if (isEnabled) Colors.FIELD else Colors.BG)
        canvas.strokeRoundRect(0f, 0f, width, height, Radius.SMALL, thickness, borderColor)

        val lines = lines()
        clampScroll(lines.size)
        val lh = TextStyle.BODY.lineHeight
        canvas.pushClip(PADDING - CARET_WIDTH, PADDING, innerWidth + 2 * CARET_WIDTH, innerHeight)
        if (text.isEmpty()) {
            canvas.drawTextWrapped(placeholder, placeholderX, PADDING, innerWidth - (placeholderX - PADDING), TextStyle.BODY, Colors.TEXT_3, maxOf(1, (innerHeight / lh).toInt()))
            drawCaret(canvas, PADDING - CARET_GAP, PADDING)
        } else {
            val overflow = limit?.let { CharacterCounter.overflowStart(text, it) }
            val color = when {
                !isEnabled -> Colors.TEXT_3
                sending -> Colors.TEXT_2
                else -> Colors.TEXT_1
            }
            val caretLine = TextLayout.lineOf(lines, state.caret)
            lines.forEachIndexed { n, line ->
                val y = PADDING + n * lh - scrollY
                if (y + lh < PADDING || y > PADDING + innerHeight) return@forEachIndexed
                val shown = text.substring(line.start, line.end)
                if (overflow != null && overflow < line.end) {
                    val from = maxOf(overflow, line.start) - line.start
                    val x0 = canvas.measure(shown.substring(0, from), TextStyle.BODY)
                    canvas.fillRect(PADDING + x0, y, line.width - x0, lh, Colors.DANGER_SOFT)
                }
                drawSelection(canvas, shown, line.start, line.end, PADDING, y)
                canvas.drawText(shown, PADDING, y, TextStyle.BODY, color)
                if (n == caretLine) drawCaret(canvas, PADDING + canvas.measure(text.substring(line.start, state.caret.coerceIn(line.start, line.end)), TextStyle.BODY), y)
            }
        }
        canvas.popClip()

        limit?.let {
            val count = state.length
            val label = CharacterCounter.label(count, it)
            val w = canvas.measure(label, TextStyle.CAPTION)
            canvas.drawText(label, width - PADDING - w, height - COUNTER_FROM_BOTTOM, TextStyle.CAPTION, CharacterCounter.color(count, it))
        }
    }

    private companion object {
        const val FOOTER = 28f
        const val COUNTER_FROM_BOTTOM = 22f
    }
}

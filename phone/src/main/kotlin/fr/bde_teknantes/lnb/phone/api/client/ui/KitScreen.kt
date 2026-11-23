package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.Phone
import fr.bde_teknantes.lnb.phone.client.ui.Box
import fr.bde_teknantes.lnb.phone.client.ui.FocusOrder
import fr.bde_teknantes.lnb.phone.client.ui.Placement
import org.lwjgl.glfw.GLFW
import java.util.WeakHashMap

/**
 * A screen built from the UI kit's widgets: the usual way to write an app screen. Create your widgets in the
 * constructor, [add] them, and the screen draws them, routes the pointer and the keyboard to them, moves the focus,
 * shows tooltips, and hosts the overlays ([showMenu], [showDialog], [showSnackbar]).
 *
 * ```kotlin
 * class NotesScreen(notes: List<String>) : KitScreen() {
 *     init {
 *         add(Header("Notes").at(0f, 0f))
 *         val list = add(ScrollList().at(0f, 44f, 256f, 326f))
 *         for (note in notes) list.add(ListRow(note) { phone.push(NoteScreen(note)) })
 *         addFloating(Fab { phone.push(EditScreen()) })
 *     }
 * }
 * ```
 *
 * The screen fills the viewport with [background] first, then draws what was [add]ed, then what was added with
 * [addFloating] (a floating action button stays on top however the content is rebuilt), then the overlays.
 *
 * **Keys.** Esc closes an open menu or dialog (otherwise the phone is put away), Tab moves the focus (inside the menu
 * or dialog when one is open), and [AppScreen.hasTextFocus] is true exactly while a text field has the focus, so the
 * phone leaves Backspace and its own key to the field. Backspace and the header's back arrow both call [onBack] first,
 * then go back; with a menu or dialog open, Backspace does nothing.
 *
 * **Hooks.** The input and drawing methods of [AppScreen] are taken by the kit and final here, as are [onShown] and
 * [onHidden]: override [onKey], [onTick], [onBack], [onScreenShown] and [onScreenHidden] instead, or write a widget of
 * your own ([Widget]).
 *
 * Like any screen, a [KitScreen] is created anew each time the app opens: keep what must last (drafts, scroll
 * positions) in state objects of your app and hand them to the widgets ([TextState], [ScrollState]).
 */
abstract class KitScreen : AppScreen() {
    internal class Root(val owner: KitScreen) : Widget() {
        init {
            setBounds(0f, 0f, AppCanvas.WIDTH.toFloat(), AppCanvas.HEIGHT.toFloat())
        }
    }

    private val content = Root(this)
    private val floating = Root(this)
    private val overlays = Root(this)
    private var ticks = 0
    private var pendingHighSurrogate: Char? = null

    private var menu: Menu? = null
    private var dialog: Dialog? = null
    private var snackbar: Snackbar? = null

    /** The colour the viewport is filled with before the widgets are drawn. */
    open val background: Int get() = Colors.BG

    /** The screen's clock in milliseconds: 50 per tick, interpolated between ticks while drawing. */
    var now = 0f
        private set

    /** How many times the screen was shown: widgets drawn in the current showing animate their changes. */
    internal var shownCount = 0
        private set

    /** Where the pointer was last seen, in viewport dp. */
    var mouseX = -1f
        private set
    var mouseY = -1f
        private set

    /** The widget under the pointer (the topmost that catches it), if any. */
    var hovered: Widget? = null
        private set
    private var hoverStart = 0f

    /** The widget with the keyboard focus, if any. */
    var focused: Widget? = null
        private set

    /** Whether the focus came from the keyboard, so focus rings show. */
    var focusVisible = false
        private set

    /** The widget a mouse button went down on, which gets the drag and the release. */
    internal var capturing: Widget? = null
        private set
    private var captureButton = -1

    /** The phone, for widgets of the kit: null before the screen is on it. */
    internal val phoneOrNull: Phone? get() = host

    /** Whether the back button should show: there is a screen to go back to. */
    internal val canGoBack: Boolean get() = host?.canGoBack == true

    // ---- Building ----

    /** Adds [widget] to the screen, on top of what was added before, and returns it. */
    fun <T : Widget> add(widget: T): T = content.add(widget)

    /**
     * Adds [widget] to the floating layer, above everything [add]ed whenever it was added, under the overlays: for a
     * floating action button, which then stays on top while the content is rebuilt.
     */
    fun <T : Widget> addFloating(widget: T): T = floating.add(widget)

    /** Takes [widget] off the screen, from either layer. */
    fun remove(widget: Widget) {
        content.remove(widget)
        floating.remove(widget)
    }

    /** The widgets added to the screen with [add]. */
    val widgets: List<Widget> get() = content.children

    // ---- Hooks ----

    /**
     * A key no widget used (the focused widget and its parents get it first). Return true to keep it: the phone then
     * doesn't act on it (Esc would put the phone away). Esc arrives here only with no menu or dialog open; Backspace
     * goes to [onBack].
     */
    open fun onKey(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false

    /**
     * The player asked to go back: the header's back arrow, or Backspace with no text field focused and no menu or
     * dialog open. Return true when the screen handled it itself (a reply composer collapses); false goes back a
     * screen.
     */
    open fun onBack(): Boolean = false

    /** Called 20 times a second while the screen is shown, after the widgets' own [Widget.onTick]. */
    open fun onTick() {}

    /** The screen became the one the player sees (see [AppScreen.onShown]). */
    open fun onScreenShown() {}

    /** The screen stopped being seen but stays alive (see [AppScreen.onHidden]). */
    open fun onScreenHidden() {}

    /** Goes back as the header's arrow does: [onBack], then the previous screen. */
    fun goBack() {
        if (!onBack()) host?.pop()
    }

    // ---- Focus ----

    /** Gives the keyboard focus to [widget] (if it can take it), or takes it away with null. */
    fun focus(widget: Widget?) = focus(widget, visible = false)

    /** Gives the focus as [focus] does; [visible] when it comes from the keyboard, so its ring shows. */
    internal fun focus(widget: Widget?, visible: Boolean) {
        val target = widget?.takeIf { it.focusable && it.isShowing && it.screen === this }
        focusVisible = visible && target != null
        if (target === focused) return
        val old = focused
        focused = target
        old?.focusChanged(false)
        target?.focusChanged(true)
    }

    /** Forgets a widget leaving the screen: focus, hover and press go if they were on it or inside it. */
    internal fun forget(widget: Widget) {
        if (widget.contains(focused)) focus(null)
        if (widget.contains(hovered)) hovered = null
        if (widget.contains(capturing)) capturing = null
        widget.pointerLostTree()
    }

    private fun focusOrder(): List<Widget> {
        val out = ArrayList<Widget>()
        fun walk(w: Widget) {
            if (!w.visible) return
            if (w.focusable && w !is Root) out += w
            w.children.forEach(::walk)
        }
        val modal = menu ?: dialog?.takeIf { it.isOpen }
        if (modal != null) {
            walk(modal)
        } else {
            walk(content)
            walk(floating)
        }
        return out
    }

    /** After a Tab: a row scrolled out of its list comes into view. */
    private fun revealFocused() {
        var w = focused ?: return
        while (true) {
            val p = w.parent ?: return
            if (p is ScrollList) {
                p.reveal(w)
                return
            }
            w = p
        }
    }

    // ---- Overlays ----

    /**
     * Opens a menu of [items] under [anchor] (the button that opens it), as art direction 8.7 places it: its top-right
     * corner 4 dp under the button's bottom-right, or above the button when there is no room below. A click on an
     * item runs it and closes the menu; a click anywhere else, or Esc, only closes it.
     */
    fun showMenu(anchor: Widget, items: List<MenuItem>) {
        require(items.isNotEmpty()) { "A menu needs at least one item" }
        closeMenu()
        val box = Box(anchor.toViewportX(0f), anchor.toViewportY(0f), anchor.width, anchor.height)
        menu = overlays.add(Menu(items, Placement.menu(box, items.size), now) { closeMenu() }).also { it.at(0f, 0f, 256f, 370f) }
    }

    /** Closes the open menu, if any. */
    fun closeMenu() {
        menu?.let(overlays::remove)
        menu = null
    }

    /** Whether a menu is open. */
    val isMenuOpen: Boolean get() = menu != null

    /** Shows [dialog] over the screen, replacing one already open. See [Dialog]. */
    fun showDialog(dialog: Dialog) {
        closeMenu()
        this.dialog?.let(overlays::remove)
        this.dialog = dialog
        dialog.opened(now)
        overlays.add(dialog.at(0f, 0f, 256f, 370f))
        focus(null)
    }

    /** Whether a dialog is open (not counting one fading out). */
    val isDialogOpen: Boolean get() = dialog?.isOpen == true

    /**
     * Shows a snackbar: a short message at the bottom of the viewport for 3 seconds, replacing the one showing. It
     * outlives the screen: popped right after, the screen hands it to the one the player comes back to (Compose
     * closes, "Posted" shows on the feed).
     */
    fun showSnackbar(text: String) = snackbar(text, null, error = false)

    /** [showSnackbar] with a button such as "Retry", held 6 seconds. */
    fun showSnackbar(text: String, action: SnackbarAction) = snackbar(text, action, error = false)

    /** An error snackbar: a red alert icon before the text, and the error sound. */
    fun showErrorSnackbar(text: String) = snackbar(text, null, error = true)

    /** An error snackbar with a button such as "Retry". */
    fun showErrorSnackbar(text: String, action: SnackbarAction) = snackbar(text, action, error = true)

    private fun snackbar(text: String, action: SnackbarAction?, error: Boolean) {
        val wasShowing = snackbar?.isShowing(now) == true
        dismissSnackbar()
        snackbar = overlays.add(0, Snackbar(text, action, error, now, slideIn = !wasShowing))
        if (error) PhoneSounds.error()
    }

    /** Hides the snackbar at once. */
    fun dismissSnackbar() {
        snackbar?.let(overlays::remove)
        snackbar = null
    }

    /** How far a FAB is lifted out of the snackbar's way, 0 to 1. */
    internal fun snackbarLift(): Float = snackbar?.lift(now) ?: 0f

    // ---- AppScreen ----

    final override fun render(canvas: AppCanvas, mouseX: Float, mouseY: Float, partialTick: Float) {
        now = (ticks + partialTick) * Motion.TICK_MS
        this.mouseX = mouseX
        this.mouseY = mouseY
        // Animations and layout first, so that what is hit and what is drawn agree within the frame.
        updateWidgets()
        updateHover()
        canvas.fillRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), background)
        content.draw(canvas)
        floating.draw(canvas)
        overlays.draw(canvas)
        drawTooltip(canvas)
    }

    /** Sizes, layout and the widgets' own updates, top-down; then a focused widget that can't keep the focus loses it. */
    private fun updateWidgets() {
        content.updateTree()
        floating.updateTree()
        overlays.updateTree()
        // A widget that can't take the focus any more loses it: disabled, hidden, gone, or no longer a stop of its own
        // (a toggle its row now owns).
        focused?.let { if (!it.focusable || !it.isEnabled || !it.isShowing || it.screen !== this) focus(null) }
    }

    final override fun tick() {
        ticks++
        // Overlays that have faded out leave here, between frames, never while the tree is being drawn.
        val time = ticks * Motion.TICK_MS
        snackbar?.let { if (it.isOver(time)) dismissSnackbar() }
        dialog?.let {
            if (it.isGone(time)) {
                overlays.remove(it)
                dialog = null
            }
        }
        content.tickTree()
        floating.tickTree()
        overlays.tickTree()
        onTick()
    }

    final override fun onShown() {
        shownCount++
        restoreSnackbar()
        onScreenShown()
        // Brought up to date before the first frame: a key or a click that comes first finds what that frame will
        // show (a header's back button there or not, rows at their size).
        updateWidgets()
    }

    final override fun onHidden() {
        // The release of a press in progress may never come: drop it, and the hover with it.
        capturing = null
        hovered = null
        content.pointerLostTree()
        floating.pointerLostTree()
        overlays.pointerLostTree()
        saveSnackbar()
        onScreenHidden()
    }

    final override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        moveMouse(x, y)
        capturing?.let { held ->
            // While a button is held on a widget, other buttons don't take its press away.
            if (button != captureButton) return true
            // The same button pressed again: its release was lost (let go outside the window). Start afresh.
            capturing = null
            held.pointerLostTree()
        }
        val target = widgetAt(x, y) ?: run {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) focus(null)
            return false
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && target !is Overlay) focus(target.takeIf { it.focusable })
        var w: Widget? = target
        while (w != null && w !is Root) {
            // Set before the call: a press that navigates hides the screen, which must find the capture to drop.
            capturing = w
            captureButton = button
            if (w.mousePressed(x - w.toViewportX(0f), y - w.toViewportY(0f), button)) return true
            if (capturing === w) capturing = null
            w = w.parent
        }
        return true
    }

    final override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        moveMouse(x, y)
        val w = capturing ?: return false
        if (button != captureButton) return false
        capturing = null
        return w.mouseReleased(x - w.toViewportX(0f), y - w.toViewportY(0f), button)
    }

    final override fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        moveMouse(x, y)
        val w = capturing ?: return false
        if (button != captureButton) return false
        return w.mouseDragged(x - w.toViewportX(0f), y - w.toViewportY(0f), button, dragX, dragY)
    }

    final override fun mouseScrolled(x: Float, y: Float, scrollX: Float, scrollY: Float): Boolean {
        moveMouse(x, y)
        var w: Widget? = widgetAt(x, y)
        while (w != null && w !is Root) {
            if (w.mouseScrolled(x - w.toViewportX(0f), y - w.toViewportY(0f), scrollY)) return true
            w = w.parent
        }
        return false
    }

    final override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val overlayOpen = menu != null || isDialogOpen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) {
                closeMenu()
                return true
            }
            dialog?.takeIf { it.isOpen }?.let {
                it.cancel()
                return true
            }
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focus(FocusOrder.next(focusOrder(), focused, backwards = modifiers and GLFW.GLFW_MOD_SHIFT != 0), visible = true)
            revealFocused()
            return true
        }
        var w: Widget? = focused?.takeIf { it.focusable && it.isEnabled && it.isShowing }
        while (w != null && w !is Root) {
            if (w.keyPressed(keyCode, scanCode, modifiers)) return true
            w = w.parent
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            // Under a menu or dialog nothing goes back: the dialog may be waiting for the server on this screen. A
            // held Backspace goes back once, as the phone does: its repeats are for text fields only.
            if (overlayOpen || isKeyRepeat) return true
            return onBack()
        }
        if (overlayOpen) return false
        return onKey(keyCode, scanCode, modifiers)
    }

    final override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        // Minecraft hands characters outside the basic plane over in two halves: put them back together.
        val code = when {
            Character.isHighSurrogate(codePoint) -> {
                pendingHighSurrogate = codePoint
                return true
            }
            Character.isLowSurrogate(codePoint) -> {
                val high = pendingHighSurrogate ?: return false
                pendingHighSurrogate = null
                Character.toCodePoint(high, codePoint)
            }
            else -> codePoint.code
        }
        pendingHighSurrogate = null
        val w = focused?.takeIf { it.focusable && it.isEnabled && it.isShowing } ?: return false
        return w.charTyped(code, modifiers)
    }

    final override val hasTextFocus: Boolean
        get() = focused?.let { it.acceptsText && it.isEnabled && it.isShowing } == true

    // ---- Internals ----

    private fun moveMouse(x: Float, y: Float) {
        mouseX = x
        mouseY = y
    }

    /** The topmost widget catching the pointer at ([x], [y]) in viewport dp: overlays, then floating, then content. */
    fun widgetAt(x: Float, y: Float): Widget? {
        if (x < 0f || y < 0f || x >= AppCanvas.WIDTH || y >= AppCanvas.HEIGHT) return null
        return overlays.hitTest(x, y) ?: floating.hitTest(x, y) ?: content.hitTest(x, y)
    }

    private fun updateHover() {
        val target = widgetAt(mouseX, mouseY)
        if (target !== hovered) {
            hovered = target
            hoverStart = now
        }
    }

    private fun drawTooltip(canvas: AppCanvas) {
        val target = hovered ?: return
        val text = target.tooltip ?: return
        if (capturing != null || now - hoverStart < Motion.TOOLTIP_DELAY_MS) return
        val inner = Placement.TOOLTIP_MAX_WIDTH - 2 * Placement.TOOLTIP_PAD_X
        val lines = canvas.wrap(text, TextStyle.CAPTION, inner, 2)
        val textWidth = lines.maxOf { canvas.measure(it, TextStyle.CAPTION) }
        val w = if (lines.size > 1) Placement.TOOLTIP_MAX_WIDTH else textWidth + 2 * Placement.TOOLTIP_PAD_X
        val h = lines.size * TextStyle.CAPTION.lineHeight + 2 * Placement.TOOLTIP_PAD_Y
        val box = Placement.tooltip(Box(target.toViewportX(0f), target.toViewportY(0f), target.width, target.height), w, h)
        canvas.fillRoundRect(box.x, box.y, box.w, box.h, Radius.SMALL, Colors.SURFACE_3)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, box.x + Placement.TOOLTIP_PAD_X, box.y + Placement.TOOLTIP_PAD_Y + i * TextStyle.CAPTION.lineHeight, TextStyle.CAPTION, Colors.TEXT_1)
        }
    }

    // A snackbar outlives its screen within the app: handed over through the app's session when the screen is hidden.
    private fun saveSnackbar() {
        val session = host?.session ?: return
        val s = snackbar?.takeIf { it.isShowing(now) }
        if (s == null) handedOver.remove(session) else handedOver[session] = Handover(s.saved(now), System.nanoTime())
        dismissSnackbar()
    }

    private fun restoreSnackbar() {
        val session = host?.session ?: return
        val handover = handedOver.remove(session) ?: return
        // Handed to the next screen at once (a pop, a push); not brought back long after, when the phone reopens.
        if (System.nanoTime() - handover.at > HANDOVER_NANOS) return
        dismissSnackbar()
        snackbar = overlays.add(0, Snackbar.restore(handover.snackbar, now))
    }

    private class Handover(val snackbar: Snackbar.Saved, val at: Long)

    private companion object {
        const val HANDOVER_NANOS = 1_000_000_000L
        val handedOver = WeakHashMap<Any, Handover>()
    }
}

/** Marks overlay layers that cover the viewport (a menu's or dialog's click catcher): clicking them doesn't move the focus. */
internal interface Overlay

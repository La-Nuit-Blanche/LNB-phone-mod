package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.offset

/**
 * A piece of a [KitScreen]: a button, a field, a list, or something of your own. A screen creates its widgets once,
 * adds them to itself, and they draw themselves and react to the player from then on. Widgets keep their state (what
 * is typed, where a list is scrolled) for as long as the screen lives; hand them a state object of your own
 * ([TextState], [ScrollState]) when it must outlive the screen.
 *
 * **Place** a widget with [at] (or [atRight]), in dp relative to its parent: the screen for widgets added to it,
 * the enclosing widget otherwise. Each widget has a natural size ([preferredWidth] and [preferredHeight]). Placed
 * without a size, a widget takes its natural size and keeps following it: a row given a subtitle later grows to 56 dp,
 * a button whose label changes keeps its left edge (its right edge with [atRight]). Placed with a size, it keeps that
 * size.
 *
 * ```kotlin
 * add(Button("Post") { send() }.apply { fixedWidth = 88f }.atRight(244f, 6f))
 * ```
 *
 * **Settings** are properties, set after construction (`apply { ... }`); constructors only take what a widget can't
 * exist without. New settings can then be added in later versions of the phone without breaking compiled apps. The
 * order doesn't matter: set them before or after placing and adding the widget, or in [KitScreen.onScreenShown]; a
 * value set before the screen's first frame simply appears, where a later change animates ([shouldAnimate]).
 *
 * **Your own widget** extends this class (or [Clickable] for something that reacts to clicks) and overrides [render],
 * drawing at (0, 0) in its own coordinates; its children are drawn on top. [isHovered], [isFocused] and [now] give
 * what drawing states and animations need; [KitDrawing] has the kit's focus ring and tints. Input methods receive
 * coordinates in the widget's own dp.
 *
 * **Hooks** are the members you may override, and every `open` member is one. The kit keeps its own work (focus,
 * hover, presses, sounds) out of them, so an override never needs to call `super`; overriding one replaces only its
 * default, the ordinary behaviour named in its documentation (drawing the children, finding what the pointer is on):
 * - drawing: [render], [renderOver], [renderChildren];
 * - size and layout: [preferredWidth], [preferredHeight], [heightFor], [layout], [update];
 * - input: [mousePressed], [mouseReleased], [mouseDragged], [mouseScrolled], [keyPressed], [charTyped], with
 *   [catchesPointer], [hitTest], [focusable], [acceptsText] and [focusChanged];
 * - time and the tree: [onTick], [onPointerLost], [onChildrenChanged].
 *
 * Where the kit handles an input itself, its entry point is final and the class offers a narrower hook instead:
 * [Clickable] takes the mouse and Enter / Space, and offers [Clickable.perform] and [Clickable.onKey].
 *
 * Widgets belong to the client thread, like screens. Adding and removing widgets is safe at any time, from any
 * callback, even while the screen is drawing.
 */
abstract class Widget {
    /** Left edge in the parent, dp. */
    var x = 0f
        private set

    /** Top edge in the parent, dp. */
    var y = 0f
        private set

    var width = 0f
        private set

    var height = 0f
        private set

    /** Hidden widgets are neither drawn nor reached by the pointer, and keep their place free. */
    var visible = true

    /**
     * Whether the widget reacts. A disabled widget is drawn in its disabled state, ignores clicks and keys (a click on
     * it plays the "can't" sound), and loses the focus. Disabling a widget disables everything inside it: read
     * [isEnabled] for the effective state.
     */
    var enabled = true
        set(value) {
            field = value
            if (!value) screen?.let { if (contains(it.focused)) it.focus(null) }
        }

    /** Whether this widget and every widget it is inside are enabled. */
    val isEnabled: Boolean
        get() {
            var w: Widget? = this
            while (w != null) {
                if (!w.enabled) return false
                w = w.parent
            }
            return true
        }

    /**
     * Text shown in a small bubble when the pointer rests on the widget for half a second. Null for none. A widget
     * with a tooltip catches the pointer, so it can't be clicked through.
     */
    open var tooltip: String? = null

    /** The widget this one was added to; null for a widget not added anywhere yet. */
    var parent: Widget? = null
        private set

    private val childList = ArrayList<Widget>()

    /** The widgets added to this one, in drawing order (later ones on top). */
    val children: List<Widget> get() = childList

    /** Width the widget takes when placed without one. */
    open val preferredWidth: Float get() = width

    /** Height the widget takes when placed without one. */
    open val preferredHeight: Float get() = height

    /**
     * Height the widget needs at [width], for widgets whose height follows their content (a post card whose text
     * wraps). A [ScrollList] stacks its rows with it. Defaults to [preferredHeight].
     */
    open fun heightFor(width: Float): Float = preferredHeight

    // ---- Placement ----

    // Placed without a size (at, atRight): the widget follows its preferred size, keeping its left edge, or its right
    // edge when rightEdge is a number.
    private var followsPreferredSize = false
    private var rightEdge = Float.NaN

    /** Places the widget, its top-left corner at ([x], [y]) in its parent, [width] x [height] dp, and keeps that size. */
    fun setBounds(x: Float, y: Float, width: Float, height: Float) {
        followsPreferredSize = false
        place(x, y, width, height)
    }

    /** Places the widget at its preferred size, which it then follows: from its left edge, or from [right] if given. */
    internal fun placeNatural(x: Float, y: Float, right: Float) {
        val w = preferredWidth
        place(if (right.isNaN()) x else right - w, y, w, preferredHeight)
        followsPreferredSize = true
        rightEdge = right
    }

    /** A widget placed without a size takes its preferred size again, if it changed. Once per frame, before update. */
    private fun followPreferredSize() {
        if (!followsPreferredSize) return
        val w = preferredWidth
        val h = preferredHeight
        if (w != width || h != height) place(if (rightEdge.isNaN()) x else rightEdge - w, y, w, h)
    }

    private fun place(x: Float, y: Float, width: Float, height: Float) {
        this.x = x
        this.y = y
        val resized = width != this.width || height != this.height
        this.width = width
        this.height = height
        if (resized) layout()
    }

    /**
     * Hook: the widget's size changed, the place to position children that depend on it. Also call it yourself after
     * changing what the layout depends on.
     */
    open fun layout() {}

    // ---- Tree ----

    /** Adds [child] on top of this widget's other children and returns it. A widget has one parent at a time. */
    fun <T : Widget> add(child: T): T = add(childList.size, child)

    /** Inserts [child] at [index] in the drawing order and returns it. */
    fun <T : Widget> add(index: Int, child: T): T {
        var a: Widget? = this
        while (a != null) {
            require(a !== child) { "A widget can't be added inside itself" }
            a = a.parent
        }
        child.parent?.remove(child)
        childList.add(index.coerceIn(0, childList.size), child)
        child.parent = this
        onChildrenChanged()
        return child
    }

    /** Takes [child] out of this widget. If it held the focus or a press, they go. */
    fun remove(child: Widget) {
        if (!childList.remove(child)) return
        screen?.forget(child)
        child.parent = null
        onChildrenChanged()
    }

    /** Takes every child out. */
    fun removeAll() {
        childList.toList().forEach(::remove)
    }

    /** Hook: a child was added or removed. */
    protected open fun onChildrenChanged() {}

    /** The screen this widget is on, or null while it isn't on one. */
    val screen: KitScreen?
        get() {
            var w: Widget? = this
            while (w != null) {
                if (w is KitScreen.Root) return w.owner
                w = w.parent
            }
            return null
        }

    /** Whether [other] is this widget or inside it. */
    fun contains(other: Widget?): Boolean {
        var w = other
        while (w != null) {
            if (w === this) return true
            w = w.parent
        }
        return false
    }

    // ---- State for drawing ----

    /** The screen's clock in milliseconds, for animations: advances 50 ms per game tick, smoothly between ticks. */
    val now: Float get() = screen?.now ?: 0f

    /**
     * Whether a change of what the widget shows should animate now: it is on a screen that has drawn a frame since it
     * was last shown. A value set while the screen is built or shown (its constructor, [KitScreen.onScreenShown])
     * should simply be there when the player first sees it. The kit's animated values ([Toggle.checked],
     * [SegmentedControl.selected], [Tabs.selected]) follow this; so should a widget of yours.
     */
    val shouldAnimate: Boolean get() = screen?.drawnSinceShown == true

    /** Whether the pointer is on this widget (and on nothing above it that catches the pointer). */
    open val isHovered: Boolean get() = screen?.hovered === this

    /**
     * Whether the pointer is over a part of this widget the player can see: the topmost widget there is this one or
     * one inside it (not an overlay, not a part cut off by a scrolling list).
     */
    val isPointerOver: Boolean
        get() {
            val s = screen ?: return false
            return contains(s.widgetAt(s.mouseX, s.mouseY))
        }

    /** Whether the widget has the keyboard focus. */
    val isFocused: Boolean get() = screen?.focused === this

    /** Whether to draw a focus ring: focused, and the focus came from the keyboard (Tab), not a click. */
    open val showsFocusRing: Boolean get() = isFocused && screen?.focusVisible == true

    /** Whether this widget and all its parents are visible. */
    val isShowing: Boolean
        get() {
            var w: Widget? = this
            while (w != null) {
                if (!w.visible) return false
                w = w.parent
            }
            return true
        }

    // ---- Drawing ----

    /** Hook: draws the widget itself, in its own coordinates ((0, 0) is its top-left corner). Children come after. */
    open fun render(canvas: AppCanvas) {}

    /** Hook: draws above the children, for the rare widget that needs it (an indicator over its tabs). */
    open fun renderOver(canvas: AppCanvas) {}

    /** Hook: draws the children, each moved to its place. Override to clip or to skip some (a list does). */
    protected open fun renderChildren(canvas: AppCanvas) {
        for (child in childList.toTypedArray()) child.draw(canvas)
    }

    /**
     * Hook, once per frame before anything is drawn or hit, top-down: the place to follow an animation (a widget's
     * position) or the state the widget shows.
     */
    open fun update() {}

    internal fun updateTree() {
        if (!visible) return
        followPreferredSize()
        update()
        for (child in childList.toTypedArray()) child.updateTree()
    }

    internal fun draw(canvas: AppCanvas) {
        if (!visible) return
        canvas.offset(x, y) {
            render(canvas)
            renderChildren(canvas)
            renderOver(canvas)
        }
    }

    // ---- Pointer ----

    /** Whether the widget catches the pointer: it can be hovered and clicked, and hides what is under it. */
    open val catchesPointer: Boolean get() = tooltip != null

    /** Where children are drawn relative to this widget's top-left corner: a scrolled list moves them up. */
    open val childOffsetY: Float get() = 0f

    /**
     * The topmost widget catching the pointer at ([localX], [localY]) in this widget's coordinates: a child, or this
     * one, or null. A point outside the widget reaches none of its children.
     */
    open fun hitTest(localX: Float, localY: Float): Widget? {
        if (!visible || localX < 0f || localY < 0f || localX >= width || localY >= height) return null
        val cy = localY - childOffsetY
        for (i in childList.indices.reversed()) {
            val child = childList[i]
            child.hitTest(localX - child.x, cy - child.y)?.let { return it }
        }
        return if (catchesPointer) this else null
    }

    /** Converts a point of this widget to viewport dp. */
    fun toViewportX(localX: Float): Float {
        var vx = localX + x
        var p = parent
        while (p != null) {
            vx += p.x
            p = p.parent
        }
        return vx
    }

    fun toViewportY(localY: Float): Float {
        var vy = localY + y
        var p = parent
        while (p != null) {
            vy += p.y + p.childOffsetY
            p = p.parent
        }
        return vy
    }

    /** A mouse button went down on the widget. Return true if used: the widget then gets the drag and the release. */
    open fun mousePressed(x: Float, y: Float, button: Int): Boolean = false

    /** The button pressed on this widget was released, wherever the pointer is now. */
    open fun mouseReleased(x: Float, y: Float, button: Int): Boolean = false

    /** The pointer moved with the button held, after a press on this widget. */
    open fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean = false

    /**
     * The wheel turned over the widget ([notches] positive towards the top). Unused, it goes to the parent: a text
     * area that can't scroll further lets its list scroll.
     */
    open fun mouseScrolled(x: Float, y: Float, notches: Float): Boolean = false

    // ---- Keyboard ----

    /**
     * Whether the widget can take the keyboard focus (Tab moves through such widgets in the order they were added;
     * a click gives the focus to what it lands on).
     */
    open val focusable: Boolean get() = false

    /** Whether the widget, focused, takes typed text: the phone then leaves Backspace and its own key to it. */
    open val acceptsText: Boolean get() = false

    /**
     * A key, while the widget has the focus; unused keys go to its parents, then to the screen. Held keys repeat:
     * check [isKeyRepeat] before acting on a key that does something (Enter, Space), not before editing.
     */
    open fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false

    /**
     * While [keyPressed] runs: whether the key is repeating as the player holds it down, rather than newly pressed.
     * See [AppScreen.isKeyRepeat][fr.bde_teknantes.lnb.phone.api.client.AppScreen.isKeyRepeat].
     */
    protected val isKeyRepeat: Boolean get() = screen?.keyRepeat == true

    /**
     * A typed character, while the widget has the focus: a whole code point (accents and dead keys arrive composed,
     * `é` as one; characters outside the basic plane arrive whole, not as two halves).
     */
    open fun charTyped(codePoint: Int, modifiers: Int): Boolean = false

    /** Hook: the widget gained or lost the focus. */
    open fun focusChanged(focused: Boolean) {}

    /** Takes the focus, if [focusable] and on a screen. */
    fun requestFocus() {
        screen?.focus(this)
    }

    // ---- Ticks and lost presses ----

    /** Hook: called 20 times a second while the screen is shown. Children get their own call either way. */
    open fun onTick() {}

    /** Hook: the screen was hidden or the widget removed while the pointer was doing something with it: drop it. */
    open fun onPointerLost() {}

    internal fun tickTree() {
        onTick()
        for (child in childList.toTypedArray()) child.tickTree()
    }

    /** The kit's own reaction to a lost pointer, which an app's override of [onPointerLost] can't skip. */
    internal open fun kitPointerLost() {}

    internal fun pointerLostTree() {
        kitPointerLost()
        onPointerLost()
        for (child in childList.toTypedArray()) child.pointerLostTree()
    }
}

/**
 * Places the widget at ([x], [y]) in its parent at its natural size, which it keeps following: a row given a subtitle
 * later grows, a button whose label changes keeps its left edge. Returns it, for chaining.
 */
fun <T : Widget> T.at(x: Float, y: Float): T {
    placeNatural(x, y, Float.NaN)
    return this
}

/** Places the widget at ([x], [y]) in its parent, [width] x [height] dp, a size it keeps. Returns it, for chaining. */
fun <T : Widget> T.at(x: Float, y: Float, width: Float, height: Float): T {
    setBounds(x, y, width, height)
    return this
}

/**
 * Places the widget with its right edge at [right] and its top at [y], at its natural size, which it keeps following
 * from that right edge: as headers and dialogs align buttons, whose labels change with the language.
 */
fun <T : Widget> T.atRight(right: Float, y: Float): T {
    placeNatural(right, y, right)
    return this
}

/** Places the widget with its right edge at [right] and its top at [y], [width] x [height] dp, a size it keeps. */
fun <T : Widget> T.atRight(right: Float, y: Float, width: Float, height: Float): T {
    setBounds(right - width, y, width, height)
    return this
}

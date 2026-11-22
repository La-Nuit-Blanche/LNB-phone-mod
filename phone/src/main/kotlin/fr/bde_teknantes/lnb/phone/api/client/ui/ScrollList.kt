package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.client.ui.ScrollMath
import org.lwjgl.glfw.GLFW

/**
 * A vertical list that scrolls (art direction 8.7): the widgets added to it are stacked top to bottom at the list's
 * full width, each as tall as its [Widget.heightFor] says, so rows can have different heights (posts that wrap) and
 * change height (a row folding away). What leaves the list's area is cut off.
 *
 * The mouse wheel scrolls 36 dp per notch. A thin scroll bar shows on the right while the list scrolls and while the
 * pointer is over it, then fades; its thumb can be dragged.
 *
 * ```kotlin
 * val list = add(ScrollList(FeedState.scroll).at(0f, 44f, 256f, 326f))
 * posts.forEach { list.add(PostCard(it)) }
 * list.add(MessageRow(tr("feed.end")))
 * list.add(Spacer(72f))   // so the floating button never covers the last row
 * ```
 *
 * @param state Where the list is scrolled. Pass one kept outside the screen to come back to the same place.
 */
class ScrollList(val state: ScrollState = ScrollState()) : Widget() {
    private val animation = Tween(0f)
    private var animating = false
    private var lastActivity = Float.NEGATIVE_INFINITY
    private var draggingThumb = false
    private var dragStartOffset = 0f
    private var dragStartY = 0f

    /** Called when the list scrolls, by the player or by code, with the new offset. */
    var onScroll: ((Float) -> Unit)? = null

    /** Total height of the rows, in dp, as of the last layout. */
    var contentHeight = 0f
        private set

    /** How far the list is scrolled from the top, in dp. */
    val offset: Float get() = state.offset

    /** The largest offset. */
    val maxOffset: Float get() = ScrollMath.maxOffset(contentHeight, height)

    /** Whether the list is at its top. */
    val isAtTop: Boolean get() = state.offset <= 0.5f

    override val childOffsetY get() = -state.offset
    override val catchesPointer get() = true

    /** Scrolls to [target] dp from the top: at once, or over 250 ms when [animate]. */
    fun scrollTo(target: Float, animate: Boolean = false) {
        layoutRows()
        val clamped = ScrollMath.clamp(target, contentHeight, height)
        if (animate) {
            animation.snapTo(state.offset)
            animation.animateTo(clamped, now, Motion.SCROLL_TO_TOP_MS, Easing.OUT)
            animating = true
        } else {
            animating = false
            setOffset(clamped)
        }
        lastActivity = now
    }

    /** Scrolls back to the top, over 250 ms unless told otherwise. */
    fun scrollToTop(animate: Boolean = true) = scrollTo(0f, animate)

    /** Scrolls the least needed for [row] (one of the list's widgets) to be fully visible. */
    fun reveal(row: Widget, animate: Boolean = false) {
        layoutRows()
        scrollTo(ScrollMath.reveal(state.offset, row.y, row.y + row.height, height, contentHeight), animate)
    }

    private fun setOffset(value: Float) {
        if (value == state.offset) return
        state.offset = value
        onScroll?.invoke(value)
    }

    /** Stacks the rows and keeps the offset in range. Runs every frame; call it to get positions before drawing. */
    fun layoutRows() {
        var y = 0f
        for (child in children) {
            if (!child.visible) continue
            val h = child.heightFor(width)
            child.setBounds(0f, y, width, h)
            y += h
        }
        contentHeight = y
        // With no rows yet (the list was just placed, before its rows were added), there is nothing to clamp a kept
        // position against: leave it for the rows to come rather than lose it.
        if (!animating && contentHeight > 0f) setOffset(ScrollMath.clamp(state.offset, contentHeight, height))
    }

    override fun layout() = layoutRows()

    override fun update() {
        if (animating) {
            setOffset(animation.value(now))
            if (!animation.isRunning(now)) animating = false
        }
        layoutRows()
    }

    override fun renderChildren(canvas: AppCanvas) {
        canvas.pushClip(0f, 0f, width, height)
        canvas.pushOffset(0f, -state.offset)
        val top = state.offset
        val bottom = top + height
        for (child in children) {
            if (child.visible && child.y + child.height > top && child.y < bottom) child.draw(canvas)
        }
        canvas.popOffset()
        canvas.popClip()
    }

    override fun renderOver(canvas: AppCanvas) {
        if (maxOffset <= 0f) return
        val alpha = ScrollMath.barAlpha(now, lastActivity, held = draggingThumb || pointerInside())
        if (alpha <= 0f) return
        val length = ScrollMath.thumbLength(contentHeight, height)
        val top = ScrollMath.thumbTop(state.offset, contentHeight, height)
        val hot = draggingThumb || pointerOnThumb()
        canvas.pushAlpha(alpha)
        canvas.fillRoundRect(width - 6f, top, 4f, length, Radius.FULL, if (hot) Colors.SCROLL_THUMB_HOVER else Colors.SCROLL_THUMB)
        canvas.popAlpha()
    }

    private fun pointerLocal(): Pair<Float, Float>? {
        val s = screen ?: return null
        return (s.mouseX - toViewportX(0f)) to (s.mouseY - toViewportY(0f))
    }

    private fun pointerInside(): Boolean {
        val (px, py) = pointerLocal() ?: return false
        return px >= 0f && py >= 0f && px < width && py < height && isShowing
    }

    private fun pointerOnThumb(): Boolean {
        val (px, py) = pointerLocal() ?: return false
        return onThumb(px, py)
    }

    private fun onThumb(px: Float, py: Float): Boolean {
        if (maxOffset <= 0f || px < width - THUMB_ZONE || px >= width) return false
        val top = ScrollMath.thumbTop(state.offset, contentHeight, height)
        return py >= top && py < top + ScrollMath.thumbLength(contentHeight, height)
    }

    override fun hitTest(localX: Float, localY: Float): Widget? {
        if (!visible || localX < 0f || localY < 0f || localX >= width || localY >= height) return null
        if (onThumb(localX, localY)) return this
        return super.hitTest(localX, localY)
    }

    override fun mouseScrolled(x: Float, y: Float, notches: Float): Boolean {
        if (maxOffset <= 0f) return false
        animating = false
        setOffset(ScrollMath.wheel(state.offset, notches, contentHeight, height))
        lastActivity = now
        return true
    }

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !onThumb(x, y)) return false
        draggingThumb = true
        animating = false
        dragStartOffset = state.offset
        dragStartY = y
        return true
    }

    override fun mouseDragged(x: Float, y: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (!draggingThumb) return false
        setOffset(ScrollMath.dragThumb(dragStartOffset, y - dragStartY, contentHeight, height))
        lastActivity = now
        return true
    }

    override fun mouseReleased(x: Float, y: Float, button: Int): Boolean {
        if (!draggingThumb) return false
        draggingThumb = false
        lastActivity = now
        return true
    }

    override fun pointerLost() {
        draggingThumb = false
        super.pointerLost()
    }

    private companion object {
        /** Width of the strip along the right edge where the thumb can be grabbed (the thumb itself is 4 dp). */
        const val THUMB_ZONE = 8f
    }
}

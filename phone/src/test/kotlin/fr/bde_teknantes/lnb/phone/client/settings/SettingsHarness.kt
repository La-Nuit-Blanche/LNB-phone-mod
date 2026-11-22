package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.api.client.FakeCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Toggle
import org.lwjgl.glfw.GLFW

/**
 * A [FakeCanvas] that follows the widgets' offsets and records, in viewport dp, the text drawn, the circles (a toggle's
 * knob) and the rounded rectangles (a toggle's track, a segmented control's pill).
 */
internal class ViewportCanvas : FakeCanvas() {
    data class Text(val text: String, val x: Float, val y: Float, val style: TextStyle, val color: Int)
    data class Circle(val x: Float, val y: Float, val diameter: Float, val color: Int)
    data class Round(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int)

    val texts = ArrayList<Text>()
    val circles = ArrayList<Circle>()
    val rounds = ArrayList<Round>()
    private val offsets = ArrayDeque<Pair<Float, Float>>()
    private var dx = 0f
    private var dy = 0f

    fun clear() {
        texts.clear()
        circles.clear()
        rounds.clear()
    }

    override fun pushOffset(dx: Float, dy: Float) {
        offsets.addLast(this.dx to this.dy)
        this.dx += dx
        this.dy += dy
    }

    override fun popOffset() {
        val (x, y) = offsets.removeLast()
        dx = x
        dy = y
    }

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float {
        texts += Text(text, dx + x, dy + y, style, color)
        return super.drawText(text, x, y, style, color)
    }

    override fun fillCircle(x: Float, y: Float, diameter: Float, color: Int) {
        circles += Circle(dx + x, dy + y, diameter, color)
        super.fillCircle(x, y, diameter, color)
    }

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        rounds += Round(dx + x, dy + y, w, h, color)
        super.fillRoundRect(x, y, w, h, radius, color)
    }
}

/**
 * Plays any [KitScreen] as the phone does: frames (which move the pointer), ticks of 50 ms, the mouse and the keys.
 * The kit's own `TestScreen` does the same for screens written as its subclasses; an app's real screen isn't one.
 */
internal open class ScreenPlayer(val screen: KitScreen) {
    val canvas = ViewportCanvas()

    fun frame(x: Float = screen.mouseX, y: Float = screen.mouseY) {
        canvas.clear()
        screen.render(canvas, x, y, 0f)
    }

    /** [times] ticks, then a frame. */
    fun tick(times: Int = 1) {
        repeat(times) { screen.tick() }
        frame()
    }

    fun click(x: Float, y: Float) {
        frame(x, y)
        screen.mousePressed(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT)
        frame(x, y)
    }

    fun key(keyCode: Int, modifiers: Int = 0): Boolean = screen.keyPressed(keyCode, 0, modifiers)

    /** Where a toggle's knob was drawn in the last frame (its left edge, viewport dp). */
    fun knobX(toggle: Toggle): Float {
        val y = toggle.toViewportY(2f)
        return canvas.circles.single { it.diameter == 16f && it.y == y }.x - toggle.toViewportX(0f)
    }

    /** The colour of a toggle's track in the last frame. */
    fun trackColor(toggle: Toggle): Int =
        canvas.rounds.single { it.w == 36f && it.h == 20f && it.y == toggle.toViewportY(0f) }.color

    /** Where a segmented control's pill was drawn in the last frame, from the control's left edge. */
    fun pillX(control: SegmentedControl): Float =
        canvas.rounds.single { it.h == control.height - 6f && it.y == control.toViewportY(3f) }.x - control.toViewportX(0f)
}

/** The Settings screen played on [store], with its widgets found the way a player sees them. */
internal class SettingsPlayer(val store: PhoneSettingsStore) : ScreenPlayer(SettingsScreen(SettingsChoices(store))) {
    val position: SegmentedControl = screen.widgets.filterIsInstance<SegmentedControl>().single()
    val corner: Clickable get() = position.children[0] as Clickable
    val centre: Clickable get() = position.children[1] as Clickable
    private val rows = screen.widgets.filterIsInstance<ListRow>()
    val soundsRow: ListRow = rows[0]
    val previewRow: ListRow = rows[1]
    val sounds: Toggle = soundsRow.trailing as Toggle
    val preview: Toggle = previewRow.trailing as Toggle

    /** What the controls show: the selected segment and the two toggles. */
    val shown: Triple<Int, Boolean, Boolean> get() = Triple(position.selected, sounds.checked, preview.checked)

    companion object {
        // Points of art direction 17 (P-12, P-13, P-14), app-local dp.
        const val CENTRE_X = 186f
        const val CENTRE_Y = 116f
        const val CORNER_X = 70f
        const val SOUNDS_Y = 169f
        const val PREVIEW_Y = 218f
        const val TOGGLE_X = 226f

        // Where the knob stands, from the toggle's left edge; where the pill stands, from the control's.
        const val KNOB_OFF = 2f
        const val KNOB_ON = 18f
        const val PILL_CORNER = 3f
        const val PILL_CENTRE = 119f
    }
}

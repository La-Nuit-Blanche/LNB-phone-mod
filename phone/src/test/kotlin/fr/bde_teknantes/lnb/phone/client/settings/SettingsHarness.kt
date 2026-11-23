package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.api.client.ui.Clickable
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.Toggle
import fr.bde_teknantes.lnb.phone.testing.DrawnShape
import fr.bde_teknantes.lnb.phone.testing.DrawnText
import fr.bde_teknantes.lnb.phone.testing.PhoneTester

/** Where a toggle's knob was drawn in the last frame (its left edge, from the toggle's). */
internal fun PhoneTester.knobX(toggle: Toggle): Float {
    val y = toggle.toViewportY(2f)
    return shapes.single { it.kind == DrawnShape.Kind.CIRCLE && it.width == 16f && it.y == y }.x - toggle.toViewportX(0f)
}

/** The colour of a toggle's track in the last frame. */
internal fun PhoneTester.trackColor(toggle: Toggle): Int =
    shapes.single { it.kind == DrawnShape.Kind.ROUND_RECT && it.width == 36f && it.height == 20f && it.y == toggle.toViewportY(0f) }.color

/** Where a segmented control's pill was drawn in the last frame, from the control's left edge. */
internal fun PhoneTester.pillX(control: SegmentedControl): Float =
    shapes.single { it.kind == DrawnShape.Kind.ROUND_RECT && it.height == control.height - 6f && it.y == control.toViewportY(3f) }.x -
        control.toViewportX(0f)

/** The Settings app played on [store] by the phone's test support, its widgets found the way a player sees them. */
internal class SettingsPlayer(val store: PhoneSettingsStore) : AutoCloseable {
    val tester = PhoneTester { SettingsScreen(SettingsChoices(store)) }
    val screen = tester.screen as KitScreen
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

    /** The text drawn in the last frame. */
    val texts: List<DrawnText> get() = tester.texts

    fun frame() = tester.frame()

    fun frame(x: Float, y: Float) = tester.frame(x, y)

    fun tick(times: Int = 1) = tester.tick(times)

    fun click(x: Float, y: Float) = tester.click(x, y)

    fun key(keyCode: Int, modifiers: Int = 0): Boolean = tester.key(keyCode, modifiers)

    fun knobX(toggle: Toggle) = tester.knobX(toggle)

    fun trackColor(toggle: Toggle) = tester.trackColor(toggle)

    fun pillX(control: SegmentedControl) = tester.pillX(control)

    override fun close() = tester.close()

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

package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * An on/off switch (art direction 8.7), 36 x 20 dp: the knob slides in 100 ms and the track turns blue halfway. Put it
 * at the end of a [ListRow] (the whole row then switches it), or make the area around it a [Clickable] that calls
 * [toggle].
 *
 * @param onChange Called with the new value when the player switches it (not when [checked] is set by code).
 */
class Toggle(checked: Boolean = false, var onChange: ((Boolean) -> Unit)? = null) : Clickable() {
    private val knob = Tween(if (checked) ON_X else OFF_X)

    /** Whether it is on. Setting it slides the knob, without calling [onChange]. */
    var checked: Boolean = checked
        set(value) {
            if (value == field) return
            field = value
            knob.animateTo(if (value) ON_X else OFF_X, now, Motion.QUICK_MS, Easing.OUT)
        }

    init {
        onClick = { toggle() }
    }

    /** Switches it as a click would, calling [onChange]. */
    fun toggle() {
        if (!enabled) return
        checked = !checked
        onChange?.invoke(checked)
    }

    override val preferredWidth get() = 36f
    override val preferredHeight get() = 20f

    override fun render(canvas: AppCanvas) {
        val look = interaction
        val x = knob.value(now)
        // The track's colour follows the knob: it switches when the knob passes the middle.
        val on = x > (OFF_X + ON_X) / 2f
        val (track, knobColor) = when {
            look == Interaction.DISABLED -> Colors.HAIRLINE to Colors.TEXT_3
            on -> (if (look == Interaction.HOVER || look == Interaction.PRESSED) Colors.ACCENT_HOVER else Colors.ACCENT) to Colors.ON_ACCENT
            else -> (if (look == Interaction.HOVER || look == Interaction.PRESSED) Colors.STROKE_HOVER else Colors.STROKE) to Colors.TEXT_1
        }
        canvas.fillRoundRect(0f, 0f, width, height, Radius.FULL, track)
        if (showsFocusRing) focusRing(canvas, width, height)
        canvas.fillCircle(x, 2f, 16f, knobColor)
    }

    private companion object {
        const val OFF_X = 2f
        const val ON_X = 18f
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * An on/off switch (art direction 8.7), 36 x 20 dp: the knob slides in 100 ms and the track turns blue halfway. Off at
 * first. Put it at the end of a [ListRow], or use a [ToggleRow]: the whole row then switches it and is one Tab stop.
 */
class Toggle : Clickable() {
    private val knob = Tween(OFF_X)

    /** Called with the new value when the player switches it (not when [checked] is set by code). */
    var onChange: ((Boolean) -> Unit)? = null

    /** Whether it is on. Setting it slides the knob, without calling [onChange]. */
    var checked: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            val x = if (value) ON_X else OFF_X
            if (screen != null) knob.animateTo(x, now, Motion.QUICK_MS, Easing.OUT) else knob.snapTo(x)
        }

    init {
        onClick = { toggle() }
    }

    /** Switches it as a click would, calling [onChange]. */
    fun toggle() {
        if (!isEnabled) return
        checked = !checked
        onChange?.invoke(checked)
    }

    // Inside a row, the row is the Tab stop and the hover target: the toggle follows it.
    private val row: Widget? get() = parent?.takeIf { it is ListRow || it is ToggleRow }

    override val focusable get() = isEnabled && row == null

    override val hoverLook get() = isHovered || row?.isHovered == true

    override val showsFocusRing get() = super.showsFocusRing || row?.showsFocusRing == true

    override val preferredWidth get() = 36f
    override val preferredHeight get() = 20f

    override fun render(canvas: AppCanvas) {
        val look = interaction
        val x = knob.value(now)
        // The track's colour follows the knob: it switches when the knob passes the middle.
        val on = x > (OFF_X + ON_X) / 2f
        val hot = look == Interaction.HOVER || look == Interaction.PRESSED
        val (track, knobColor) = when {
            look == Interaction.DISABLED -> Colors.HAIRLINE to Colors.TEXT_3
            on -> (if (hot) Colors.ACCENT_HOVER else Colors.ACCENT) to Colors.ON_ACCENT
            else -> (if (hot) Colors.STROKE_HOVER else Colors.STROKE) to Colors.TEXT_1
        }
        canvas.fillRoundRect(0f, 0f, width, height, Radius.FULL, track)
        if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, width, height, Radius.FULL)
        canvas.fillCircle(x, 2f, 16f, knobColor)
    }

    private companion object {
        const val OFF_X = 2f
        const val ON_X = 18f
    }
}

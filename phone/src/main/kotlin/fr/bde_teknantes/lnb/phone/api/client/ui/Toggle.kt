package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * An on/off switch (art direction 8.7), 36 x 20 dp: the knob slides in 100 ms and the track turns blue halfway. Off at
 * first.
 *
 * Usually it sits in something that owns it: the [trailing][ListRow.trailing] end of a [ListRow] without a click
 * handler of its own, or a [LabeledToggle]. The owner then takes the pointer, the hover, the focus and the click for
 * the whole row, the toggle included, and the toggle only shows the row's state: hovering anywhere on the row lights
 * both, a click anywhere switches it, and the row is one Tab stop. Anywhere else (a row with its own [ListRow.onClick],
 * a toggle placed on its own) it is a target of its own: it is clicked and focused by itself.
 */
class Toggle : Clickable() {
    private val knob = Tween(OFF_X)

    /** Called with the new value when the player switches it (not when [checked] is set by code). */
    var onChange: ((Boolean) -> Unit)? = null

    /**
     * Whether it is on. Setting it slides the knob, without calling [onChange]; set before the screen's first frame, the
     * knob is simply there ([shouldAnimate]).
     */
    var checked: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            val x = if (value) ON_X else OFF_X
            if (shouldAnimate) knob.animateTo(x, now, Motion.QUICK_MS, Easing.OUT) else knob.snapTo(x)
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

    // The row this toggle belongs to, if it owns it: the row is then the target, and the toggle follows its state.
    private val owner: Clickable?
        get() {
            val p = parent
            return if (p is ToggleOwner && p is Clickable && p.owns(this)) p else null
        }

    override val catchesPointer get() = owner == null

    override val focusable get() = isEnabled && owner == null

    override val hoverLook get() = owner?.isHovered ?: isHovered

    override val showsFocusRing get() = super.showsFocusRing || owner?.showsFocusRing == true

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

/** A row that may own a [Toggle] inside it: it takes the toggle's pointer, focus and click (see [Toggle]). */
internal interface ToggleOwner {
    fun owns(toggle: Toggle): Boolean
}

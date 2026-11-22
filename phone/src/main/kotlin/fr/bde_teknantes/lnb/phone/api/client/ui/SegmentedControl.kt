package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A choice between a few options shown side by side (art direction 8.7): a 32 dp pill track split into equal segments,
 * the selected one a lighter pill inset 3 dp that slides in 150 ms. 232 dp wide in the phone's layouts.
 *
 */
class SegmentedControl(labels: List<String>) : Widget() {
    /** Called with the new index when the player picks another segment (not when [selected] is set by code). */
    var onSelect: ((Int) -> Unit)? = null

    private val segments = labels.mapIndexed { i, label -> add(Segment(i, label)) }
    private val pill = Tween(0f)

    init {
        require(labels.isNotEmpty()) { "A segmented control needs at least one label" }
    }

    /**
     * The selected segment. Setting it slides the pill, without calling [onSelect]; set before the screen's first
     * frame, the pill is simply there ([shouldAnimate]).
     */
    var selected: Int = 0
        set(value) {
            val index = value.coerceIn(0, segments.size - 1)
            if (index == field) return
            field = index
            if (width > 0f && shouldAnimate) pill.animateTo(pillX(index), now, Motion.SHORT_MS, Easing.OUT) else pill.snapTo(pillX(index))
        }

    override val preferredWidth get() = 232f
    override val preferredHeight get() = 32f

    private fun segmentWidth() = width / segments.size

    private fun pillX(index: Int) = index * segmentWidth() + INSET

    override fun layout() {
        val w = segmentWidth()
        segments.forEachIndexed { i, s -> s.at(i * w, 0f, w, height) }
        pill.snapTo(pillX(selected))
    }

    override fun render(canvas: AppCanvas) {
        canvas.fillRoundRect(0f, 0f, width, height, Radius.FULL, Colors.SURFACE)
        canvas.strokeRoundRect(0f, 0f, width, height, Radius.FULL, 1f, Colors.STROKE)
        canvas.fillRoundRect(pill.value(now), INSET, segmentWidth() - 2 * INSET, height - 2 * INSET, Radius.FULL, Colors.SURFACE_3)
    }

    private inner class Segment(private val index: Int, private val label: String) : Clickable() {
        init {
            onClick = {
                if (selected != index) {
                    selected = index
                    onSelect?.invoke(index)
                }
            }
        }

        override fun render(canvas: AppCanvas) {
            val look = interaction
            val color = when {
                // Disabled is not in the art direction: the choice stays readable, the others recede.
                look == Interaction.DISABLED -> if (selected == index) Colors.TEXT_2 else Colors.TEXT_3
                selected == index || look == Interaction.HOVER || look == Interaction.PRESSED -> Colors.TEXT_1
                else -> Colors.TEXT_2
            }
            val text = canvas.truncate(label, TextStyle.LABEL, width - 2 * INSET - 16f)
            canvas.drawTextCentered(text, width / 2f, (height - TextStyle.LABEL.lineHeight) / 2f, TextStyle.LABEL, color)
            if (showsFocusRing) KitDrawing.focusRing(canvas, INSET, INSET, width - 2 * INSET, height - 2 * INSET, Radius.FULL)
        }
    }

    private companion object {
        const val INSET = 3f
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A choice between a few options shown side by side (art direction 8.7): a 32 dp pill track split into equal segments,
 * the selected one a lighter pill inset 3 dp that slides in 150 ms. 232 dp wide in the phone's layouts.
 *
 * @param onSelect Called with the new index when the player picks another segment (not when [selected] is set by code).
 */
class SegmentedControl(labels: List<String>, selected: Int = 0, var onSelect: ((Int) -> Unit)? = null) : Widget() {
    private val segments = labels.mapIndexed { i, label -> add(Segment(i, label)) }
    private val pill = Tween(Float.NaN)

    init {
        require(labels.isNotEmpty()) { "A segmented control needs at least one label" }
    }

    /** The selected segment. Setting it slides the pill, without calling [onSelect]. */
    var selected: Int = selected.coerceIn(0, labels.size - 1)
        set(value) {
            val index = value.coerceIn(0, segments.size - 1)
            if (index == field) return
            field = index
            pill.animateTo(pillX(index), now, Motion.SHORT_MS, Easing.OUT)
        }

    override var enabled: Boolean
        get() = super.enabled
        set(value) {
            super.enabled = value
            segments.forEach { it.enabled = value }
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
            if (showsFocusRing) {
                canvas.strokeRoundRect(INSET - 4f, INSET - 4f, width - 2 * INSET + 8f, height - 2 * INSET + 8f, Radius.FULL, 2f, Colors.FOCUS_RING)
            }
        }
    }

    private companion object {
        const val INSET = 3f
    }
}

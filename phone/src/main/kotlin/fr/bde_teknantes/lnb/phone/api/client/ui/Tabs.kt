package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * Top-level tabs (art direction 8.7): 44 dp, used at the very top of a screen in place of a header. Tabs share the
 * width equally; the active one has a 48 dp accent indicator, centred on the tab whatever its label, which slides to
 * the new tab in 150 ms. An inactive tab can show an unread dot ([setUnread]).
 *
 * Switching tabs is the app's business: [onSelect] gets the new index, and the content should swap at once.
 *
 * @param labels One per tab. Each must fit its tab in both languages (2 tabs: 128 dp each, the dot takes 10 more).
 */
class Tabs(labels: List<String>) : Widget() {
    /** Called with the new index when the player picks another tab (not when [selected] is set by code). */
    var onSelect: ((Int) -> Unit)? = null

    private val tabs = labels.mapIndexed { i, label -> add(Tab(i, label)) }
    private val indicator = Tween(0f)

    init {
        require(labels.isNotEmpty()) { "Tabs need at least one label" }
    }

    /**
     * The active tab, the first one at first. Setting it slides the indicator, without calling [onSelect]; set before
     * the screen's first frame, the indicator is simply there ([shouldAnimate]).
     */
    var selected: Int = 0
        set(value) {
            val index = value.coerceIn(0, tabs.size - 1)
            if (index == field) return
            field = index
            if (width > 0f && shouldAnimate) {
                indicator.animateTo(indicatorX(index), now, Motion.SHORT_MS, Easing.OUT)
            } else {
                indicator.snapTo(indicatorX(index))
            }
        }

    /** Shows or hides the unread dot of tab [index]. The active tab never shows it. */
    fun setUnread(index: Int, unread: Boolean) {
        tabs[index].unread = unread
    }

    override val preferredWidth get() = 256f
    override val preferredHeight get() = 44f

    override fun layout() {
        val w = width / tabs.size
        tabs.forEachIndexed { i, tab -> tab.at(i * w, 0f, w, height - 1f) }
        indicator.snapTo(indicatorX(selected))
    }

    private fun indicatorX(index: Int): Float = index * (width / tabs.size) + (width / tabs.size - INDICATOR_WIDTH) / 2f

    override fun render(canvas: AppCanvas) {
        canvas.fillRect(0f, 0f, width, height, Colors.BG)
        canvas.hairline(0f, width, height - 1f)
    }

    override fun renderOver(canvas: AppCanvas) {
        // Over the hairline (8.7: the line is under the indicator), at y 41..43.
        canvas.fillRoundRect(indicator.value(now), height - 3f, INDICATOR_WIDTH, 3f, Radius.FULL, Colors.ACCENT)
    }

    private inner class Tab(private val index: Int, val label: String) : Clickable() {
        var unread = false

        init {
            onClick = {
                if (selected != index) {
                    selected = index
                    onSelect?.invoke(index)
                }
            }
        }

        override fun render(canvas: AppCanvas) {
            val active = selected == index
            val look = interaction
            if (!active) KitDrawing.stateTint(canvas, look, 0f, 0f, width, height)
            val color = if (active || look == Interaction.HOVER || look == Interaction.PRESSED) Colors.TEXT_1 else Colors.TEXT_2
            val text = canvas.truncate(label, TextStyle.LABEL, width - 24f)
            val labelWidth = canvas.measure(text, TextStyle.LABEL)
            val labelX = (width - labelWidth) / 2f
            canvas.drawText(text, labelX, LABEL_Y, TextStyle.LABEL, color)
            if (unread && !active) canvas.fillCircle(labelX + labelWidth + 4f, LABEL_Y + 5f, 6f, Colors.ACCENT)
            if (showsFocusRing) KitDrawing.focusRing(canvas, 4f, 4f, width - 8f, height - 8f, Radius.SMALL - 4f)
        }
    }

    companion object {
        val INDICATOR_WIDTH = 48f
        private const val LABEL_Y = 14f
    }
}

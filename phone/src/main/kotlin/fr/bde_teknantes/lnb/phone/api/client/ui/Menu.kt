package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.Shadow
import fr.bde_teknantes.lnb.phone.client.ui.Box
import fr.bde_teknantes.lnb.phone.client.ui.Placement

/**
 * One entry of a menu opened with [KitScreen.showMenu].
 *
 * @property label What the item says.
 * @property onClick What the item does. The menu is already closed when it runs.
 */
class MenuItem(val label: String, val onClick: () -> Unit) {
    /** A 16 dp icon before the label, or null. */
    var icon: Icon? = null

    /** Draws the item in red: for deleting and other actions that can't be undone. */
    var destructive = false
}

/**
 * The popover menu (art direction 8.7): 168 dp wide, items of 40 dp, fading in over 100 ms. Covers the viewport with an
 * invisible layer, so that a click outside the menu closes it and does nothing else.
 */
internal class Menu(items: List<MenuItem>, private val box: Box, private val openedAt: Float, private val close: () -> Unit) : Widget(), Overlay {
    private val panel = add(Panel(box))

    init {
        items.forEachIndexed { i, item ->
            panel.add(Item(item).at(0f, Placement.MENU_PADDING + i * Placement.MENU_ITEM_HEIGHT, box.w, Placement.MENU_ITEM_HEIGHT))
        }
    }

    override val catchesPointer get() = true

    override fun mousePressed(x: Float, y: Float, button: Int): Boolean {
        close()
        return true
    }

    override fun mouseScrolled(x: Float, y: Float, notches: Float): Boolean = true

    override fun renderChildren(canvas: AppCanvas) {
        canvas.pushAlpha(((now - openedAt) / Motion.QUICK_MS).coerceIn(0f, 1f))
        super.renderChildren(canvas)
        canvas.popAlpha()
    }

    private class Panel(box: Box) : Widget() {
        init {
            setBounds(box.x, box.y, box.w, box.h)
        }

        override val catchesPointer get() = true

        // A press on the panel's padding stops here: unused, it would reach the catcher and close the menu.
        override fun mousePressed(x: Float, y: Float, button: Int): Boolean = true

        override fun render(canvas: AppCanvas) {
            canvas.drawShadow(Shadow.SHEET, 0f, 0f, width, height)
            canvas.fillRoundRect(0f, 0f, width, height, Radius.SMALL, Colors.SURFACE_3)
        }
    }

    private inner class Item(private val item: MenuItem) : Clickable() {
        override fun perform() {
            close()
            super.perform()
        }

        init {
            onClick = item.onClick
        }

        override fun render(canvas: AppCanvas) {
            KitDrawing.stateTint(canvas, interaction, 0f, 0f, width, height)
            if (showsFocusRing) KitDrawing.focusRing(canvas, 4f, 4f, width - 8f, height - 8f, Radius.SMALL - 4f)
            val color = if (item.destructive) Colors.DANGER else Colors.TEXT_1
            item.icon?.let { canvas.drawIcon(it, 12f, (height - 16f) / 2f, color, 16f) }
            canvas.drawTextTruncated(item.label, 40f, (height - 18f) / 2f, width - 40f - 12f, TextStyle.BODY, color)
        }
    }
}

package fr.bde_teknantes.lnb.phone.client.ui

/** A rectangle in dp: left, top, width, height. */
internal data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
    val centerX get() = x + w / 2f

    fun contains(px: Float, py: Float) = px >= x && px < right && py >= y && py < bottom
}

/** Where overlays go (art direction 8.7), apart from drawing so it can be tested. Viewport-local dp. */
internal object Placement {
    const val VIEWPORT_W = 256f
    const val VIEWPORT_H = 370f

    // ---- Menu ----

    const val MENU_WIDTH = 168f
    const val MENU_PADDING = 4f
    const val MENU_ITEM_HEIGHT = 40f
    const val MENU_GAP = 4f

    /** Lowest the bottom of a menu may go before it opens upwards. */
    const val MENU_FLOOR = 362f

    fun menuHeight(items: Int) = 2 * MENU_PADDING + items * MENU_ITEM_HEIGHT

    /**
     * The menu's box for one opened by a button at [anchor]: its top-right corner 4 dp under the button's bottom-right
     * corner, or its bottom-right corner 4 dp above the button's top-right when it would pass y 362. Kept inside the
     * viewport horizontally.
     */
    fun menu(anchor: Box, items: Int): Box {
        val h = menuHeight(items)
        val below = anchor.bottom + MENU_GAP
        val y = if (below + h > MENU_FLOOR) anchor.y - MENU_GAP - h else below
        val x = (anchor.right - MENU_WIDTH).coerceIn(0f, VIEWPORT_W - MENU_WIDTH)
        return Box(x, y, MENU_WIDTH, h)
    }

    // ---- Tooltip ----

    const val TOOLTIP_MAX_WIDTH = 200f
    const val TOOLTIP_PAD_X = 8f
    const val TOOLTIP_PAD_Y = 4f
    const val TOOLTIP_GAP = 6f
    const val TOOLTIP_MIN_ROOM = 8f

    /** Keeps tooltips off the viewport's sides (the art direction centres them and leaves the edges open). */
    const val TOOLTIP_EDGE = 4f

    /** Tooltip of [w] x [h] dp for an element at [target]: centred 6 dp above it, below when there is less than 8 dp above. */
    fun tooltip(target: Box, w: Float, h: Float): Box {
        val above = target.y - TOOLTIP_GAP - h
        val y = if (target.y - TOOLTIP_GAP - h < TOOLTIP_MIN_ROOM) target.bottom + TOOLTIP_GAP else above
        val x = (target.centerX - w / 2f).coerceIn(TOOLTIP_EDGE, VIEWPORT_W - TOOLTIP_EDGE - w)
        return Box(x, y, w, h)
    }

    // ---- Dialog ----

    const val DIALOG_X = 12f
    const val DIALOG_WIDTH = 232f
    const val DIALOG_PADDING = 16f
    const val DIALOG_CANCEL_WIDTH = 88f
    const val DIALOG_CONFIRM_WIDTH = 104f
    const val DIALOG_MAX_TITLE_LINES = 2
    const val DIALOG_MAX_TEXT_LINES = 3

    /** Height of a dialog: `16 + title + 8 + text + 16 + 32 + 16`, the texts in lines of 18 dp. */
    fun dialogHeight(titleLines: Int, textLines: Int): Float =
        DIALOG_PADDING + titleLines * 18f + (if (textLines > 0) 8f + textLines * 18f else 0f) + 16f + 32f + DIALOG_PADDING

    /** Top of a dialog of height [h], centred vertically, on an even dp so its edges stay sharp. */
    fun dialogTop(h: Float): Float = Math.floor(((VIEWPORT_H - h) / 4f).toDouble()).toFloat() * 2f

    // ---- Snackbar ----

    val SNACKBAR = Box(8f, 322f, 240f, 40f)

    /** Where the FAB sits, and where it goes while a snackbar shows. */
    const val FAB_Y = 310f
    const val FAB_LIFTED_Y = 262f
}

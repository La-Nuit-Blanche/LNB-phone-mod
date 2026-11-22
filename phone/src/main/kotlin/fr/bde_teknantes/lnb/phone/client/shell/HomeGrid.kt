package fr.bde_teknantes.lnb.phone.client.shell

import kotlin.math.floor

/**
 * The home screen's geometry (art direction 6), in viewport-local GUI px: (0, 0) is the viewport's top-left pixel,
 * just under the status bar. Apps are numbered in home order; a page holds 30.
 */
internal object HomeGrid {
    const val COLUMNS = 5
    const val ROWS = 6
    const val PER_PAGE = COLUMNS * ROWS
    const val ICON_SIZE = 20
    const val PITCH = 24
    const val LEFT = 6
    const val TOP = 8

    /** The hover ring: 24 x 24 at (icon - 2, icon - 2). */
    const val RING_SIZE = 24
    const val RING_INSET = 2

    /** The label line: the hovered app's name, glyph rows 160..166, at most 20 characters (120 px). */
    const val LABEL_Y = 160
    const val LABEL_MAX_CHARS = 20
    const val LABEL_MAX_WIDTH = 120

    /** Page dots: 3 x 3, pitch 6, rows 176..178, shown with more than one page. */
    const val DOT_SIZE = 3
    const val DOT_PITCH = 6
    const val DOT_Y = 176

    /** Badge: 9 px tall, 9 wide for 1..9, 15 wide for `9+`; digits at badge-local (2, 1). */
    const val BADGE_HEIGHT = 9
    const val BADGE_TEXT_X = 2
    const val BADGE_TEXT_Y = 1

    fun pageCount(apps: Int): Int = maxOf(1, (apps + PER_PAGE - 1) / PER_PAGE)

    /** The page that shows app [index]. */
    fun pageOf(index: Int): Int = index / PER_PAGE

    /** Left edge of the icon in [slot] (0..29, left to right, top to bottom). */
    fun iconX(slot: Int): Int = LEFT + PITCH * (slot % COLUMNS)

    fun iconY(slot: Int): Int = TOP + PITCH * (slot / COLUMNS)

    /** The apps on [page] out of [apps]: their indices in home order. */
    fun appsOnPage(page: Int, apps: Int): IntRange = (page * PER_PAGE) until minOf(apps, (page + 1) * PER_PAGE)

    /**
     * The app under the pointer at viewport-local ([vx], [vy]) on [page], or null. An icon answers over its whole hover
     * ring (24 x 24): the rings of neighbouring icons touch, so moving across the grid never falls into a gap.
     */
    fun appAt(vx: Double, vy: Double, page: Int, apps: Int): Int? {
        val x = floor(vx).toInt() - LEFT + RING_INSET
        val y = floor(vy).toInt() - TOP + RING_INSET
        if (x < 0 || y < 0) return null
        val column = x / PITCH
        val row = y / PITCH
        if (column >= COLUMNS || row >= ROWS) return null
        val index = page * PER_PAGE + row * COLUMNS + column
        return index.takeIf { it in appsOnPage(page, apps) }
    }

    /** The page after a mouse wheel turn of [notches] (positive away from the player: back a page). */
    fun pageAfterScroll(page: Int, notches: Double, apps: Int): Int = when {
        notches > 0 -> maxOf(0, page - 1)
        notches < 0 -> minOf(pageCount(apps) - 1, page + 1)
        else -> page
    }

    /** What a badge shows for [count] unseen notifications: nothing at 0, the digit up to 9, then `9+`. */
    fun badgeText(count: Int): String? = when {
        count <= 0 -> null
        count <= 9 -> count.toString()
        else -> "9+"
    }

    fun badgeWidth(text: String): Int = if (text.length == 1) 9 else 15

    /** The badge's top-left, viewport-local: over the icon's top-right corner, 3 px above it (6.1). */
    fun badgeX(slot: Int, width: Int): Int = iconX(slot) + ICON_SIZE + 3 - width

    fun badgeY(slot: Int): Int = iconY(slot) - 3

    /** Left edge of a line of [drawnWidth] px centred in the viewport (label line, GUI-scale message). */
    fun centredX(drawnWidth: Int): Int = Math.floorDiv(Device.VIEWPORT_WIDTH - drawnWidth, 2)

    /** Left edge of the first of [pages] page dots. */
    fun firstDotX(pages: Int): Int = centredX(DOT_PITCH * (pages - 1) + DOT_SIZE)
}

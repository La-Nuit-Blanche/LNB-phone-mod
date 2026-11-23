package fr.bde_teknantes.lnb.phone.feed.client

/**
 * The thread's vertical layout (art direction 9.7, 9.11), in viewport dp: the list runs from under the header to the
 * bottom of the viewport, and the reply composer (or a guest's strip) is docked over its bottom. The list ends with a
 * spacer of the dock's height and 12 dp, so its last row can be scrolled clear of the dock; what the player sees of
 * the list is the part above the dock, which shrinks while the composer expands.
 */
object ThreadLayout {
    /** Under the 44 dp header. */
    const val LIST_TOP = 44f
    const val BOTTOM = 370f

    /** The composer collapsed: `y 306..369`. */
    const val COLLAPSED = 64f

    /** The composer expanded: `y 170..369`. */
    const val EXPANDED = 200f

    /** The read-only strip of guests, banned players and writing off: `y 330..369`. */
    const val STRIP = 40f

    /** Space after the last row besides the dock's height. */
    const val SPACER_GAP = 12f

    /** The top of a dock [height] dp tall. */
    fun dockTop(height: Float) = BOTTOM - height

    /** How much of the list shows above a dock [height] dp tall. */
    fun visibleHeight(height: Float) = dockTop(height) - LIST_TOP

    /** The spacer at the end of the list, for a dock [height] dp tall (0 without a dock). */
    fun spacer(height: Float) = if (height > 0f) height + SPACER_GAP else SPACER_GAP
}

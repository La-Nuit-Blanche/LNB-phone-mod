package fr.bde_teknantes.lnb.phone.api.client.ui

/**
 * Distances in dp. Space things by these steps only (2, 4, 8, 12, 16, 24, 32), and keep rectangle edges on even
 * values so they stay sharp at every GUI scale. Like [Colors], read at run time.
 */
object Spacing {
    val XXS = 2f
    val XS = 4f
    val S = 8f
    val M = 12f
    val L = 16f
    val XL = 24f
    val XXL = 32f

    /** Padding on both sides of every screen: content starts at x 12 and ends at x 244. */
    val SCREEN_PADDING = 12f

    /** Where text starts in rows and posts that have a 32 dp avatar or icon at x 12. */
    val CONTENT_X = 56f

    /** Right edge of content: the viewport's width minus [SCREEN_PADDING]. */
    val CONTENT_RIGHT = 244f

    /** Smallest area that may react to a click, in both directions. */
    val MIN_HIT = 28f
}

/** Corner radii in dp. */
object Radius {
    /** Text fields, text areas, snackbars, menus, tooltips. */
    val SMALL = 10f

    /** Dialogs and notification banners. */
    val LARGE = 14f

    /**
     * Fully rounded: the shape's ends are half circles. Buttons, pills, toggles, avatars, badges. Any radius at least
     * half the shape's height does the same; this one always does.
     */
    val FULL = 1000f
}

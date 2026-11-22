package fr.bde_teknantes.lnb.phone.api.client.ui

/**
 * Distances in dp. Space things by these steps only (2, 4, 8, 12, 16, 24, 32), and keep rectangle edges on even
 * values so they stay sharp at every GUI scale.
 */
object Spacing {
    const val XXS = 2f
    const val XS = 4f
    const val S = 8f
    const val M = 12f
    const val L = 16f
    const val XL = 24f
    const val XXL = 32f

    /** Padding on both sides of every screen: content starts at x 12 and ends at x 244. */
    const val SCREEN_PADDING = 12f

    /** Where text starts in rows and posts that have a 32 dp avatar or icon at x 12. */
    const val CONTENT_X = 56f

    /** Right edge of content: the viewport's width minus [SCREEN_PADDING]. */
    const val CONTENT_RIGHT = 244f

    /** Smallest area that may react to a click, in both directions. */
    const val MIN_HIT = 28f
}

/** Corner radii in dp. */
object Radius {
    /** Text fields, text areas, snackbars, menus, tooltips. */
    const val SMALL = 10f

    /** Dialogs and notification banners. */
    const val LARGE = 14f

    /**
     * Fully rounded: the shape's ends are half circles. Buttons, pills, toggles, avatars, badges. Any radius at least
     * half the shape's height does the same; this one always does.
     */
    const val FULL = 1000f
}

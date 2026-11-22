package fr.bde_teknantes.lnb.phone.api.client.ui

/**
 * The phone's colours, as ARGB ints (`0xAARRGGBB`), the format every drawing method of [AppCanvas][fr.bde_teknantes.lnb.phone.api.client.AppCanvas]
 * takes. Draw with these rather than your own values, so your app looks like the rest of the phone: a dark background,
 * one blue accent, three greys of text.
 *
 * The kit's components already pick the right ones; you need them for what you draw yourself.
 */
object Colors {
    /** App background, headers and tab bars. Every screen starts by filling the viewport with it. */
    const val BG: Int = 0xFF12151B.toInt()

    /** A raised area on [BG]: pinned posts, the segmented control's track, the empty-state disc. */
    const val SURFACE: Int = 0xFF1A1E26.toInt()

    /** Raised a step further: dialogs, notification banners, loading skeletons. */
    const val SURFACE_2: Int = 0xFF242A34.toInt()

    /** Raised the most: snackbars, menus, tooltips, the selected segment. */
    const val SURFACE_3: Int = 0xFF313846.toInt()

    /** Inside text fields and text areas. */
    const val FIELD: Int = 0xFF0D1015.toInt()

    /** Borders of inputs and secondary buttons, the track of a toggle that is off. */
    const val STROKE: Int = 0xFF3A4250.toInt()

    /** [STROKE] under the pointer. */
    const val STROKE_HOVER: Int = 0xFF465063.toInt()

    /** Dividers and the line under headers. */
    const val HAIRLINE: Int = 0xFF2A303B.toInt()

    /** Primary text and active labels. */
    const val TEXT_1: Int = 0xFFEEF1F5.toInt()

    /** Secondary text: handles, times, counts, inactive tabs. */
    const val TEXT_2: Int = 0xFF96A0B0.toInt()

    /** Placeholders, disabled text, private notes. */
    const val TEXT_3: Int = 0xFF5C6575.toInt()

    /** The one accent: primary buttons, focus, the active tab, toggles that are on, unread markers. */
    const val ACCENT: Int = 0xFF3A8DF0.toInt()

    /** [ACCENT] fills under the pointer. */
    const val ACCENT_HOVER: Int = 0xFF529CF3.toInt()

    /** [ACCENT] fills while pressed, and loading buttons. */
    const val ACCENT_PRESSED: Int = 0xFF2E78D2.toInt()

    /** A light accent tint: hover behind text buttons and accent icons, highlighted posts. */
    const val ACCENT_SOFT: Int = 0x1F3A8DF0

    /** [ACCENT_SOFT] while pressed. */
    const val ACCENT_SOFT_2: Int = 0x333A8DF0

    /** Text and icons on [ACCENT] and [DANGER] fills. */
    const val ON_ACCENT: Int = 0xFFFFFFFF.toInt()

    /** A liked heart and its count. */
    const val LIKE: Int = 0xFFF2545B.toInt()

    /** Hover tint behind the like icon. */
    const val LIKE_SOFT: Int = 0x1FF2545B

    /** [LIKE_SOFT] while pressed. */
    const val LIKE_SOFT_2: Int = 0x33F2545B

    /** The announcer badge's disc. */
    const val ANNOUNCER: Int = 0xFF7A5CE6.toInt()

    /** The announcer badge's glyph. */
    const val ON_ANNOUNCER: Int = 0xFFFFFFFF.toInt()

    /** A warning: the character counter in its last 20 characters. */
    const val WARNING: Int = 0xFFF2B33A.toInt()

    /** Errors, going over a limit, destructive actions. */
    const val DANGER: Int = 0xFFF0564A.toInt()

    /** [DANGER] fills under the pointer. */
    const val DANGER_HOVER: Int = 0xFFF36D63.toInt()

    /** [DANGER] fills while pressed or loading. */
    const val DANGER_PRESSED: Int = 0xFFD94A3F.toInt()

    /** Behind characters past a text area's limit. */
    const val DANGER_SOFT: Int = 0x33F0564A

    /** Count badges. */
    const val BADGE: Int = 0xFFE5483C.toInt()

    /** Drawn over a row, card, tab or menu item under the pointer. */
    const val OVERLAY_HOVER: Int = 0x0AFFFFFF

    /** Drawn over a pressed row, card or menu item, and behind an icon button under the pointer. */
    const val OVERLAY_PRESSED: Int = 0x14FFFFFF

    /** Behind a pressed icon button. */
    const val OVERLAY_PRESSED_2: Int = 0x1FFFFFFF

    /** Darkens the viewport behind a dialog. */
    const val SCRIM: Int = 0x99000000.toInt()

    /** The anonymous avatar's disc. */
    const val ANON_BG: Int = 0xFF3A4250.toInt()

    /** The keyboard focus ring. */
    const val FOCUS_RING: Int = 0x803A8DF0.toInt()

    /** Behind selected text in a field. Not in the art direction, which leaves selection out. */
    const val SELECTION: Int = 0x663A8DF0

    /** A scroll bar's thumb. */
    const val SCROLL_THUMB: Int = 0x33FFFFFF

    /** A scroll bar's thumb under the pointer or dragged. */
    const val SCROLL_THUMB_HOVER: Int = 0x59FFFFFF

    /** [color] with its alpha multiplied by [alpha] (0 to 1), for fades. */
    fun withAlpha(color: Int, alpha: Float): Int {
        if (alpha >= 1f) return color
        val a = ((color ushr 24) * alpha.coerceAtLeast(0f) + 0.5f).toInt()
        return (a shl 24) or (color and 0xFFFFFF)
    }
}

package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles

/**
 * The notification banner's geometry (art direction 10.1), in dp, banner-local unless said otherwise, and how its
 * title and text fit. Plain logic over [FontMetrics], so it can be tested without a game window.
 */
object BannerLayout {
    const val WIDTH = 240f
    const val HEIGHT = 76f
    const val RADIUS = 14f
    const val EDGE = 1f

    const val ICON_X = 12f
    const val ICON_Y = 18f

    /** The app's 20 x 20 GUI px pixel icon, never resized. */
    const val ICON_SIZE = 40f

    const val TEXT_X = 64f
    const val TITLE_Y = 10f
    const val TEXT_Y = 28f

    /** Title and text width: from `x 64` to `x 228`. */
    const val TEXT_WIDTH = 164f
    const val MAX_TEXT_LINES = 2

    /** The `shadow/sheet` sprite is drawn this much larger than the banner on each side (16.2). */
    const val SHADOW_MARGIN = 8f

    /** The sprite's size and nine-slice border, in dp. */
    const val SHADOW_SPRITE = 48f
    const val SHADOW_BORDER = 22f

    /** Where the banner rests over the open phone, app-local (10.3). */
    const val IN_PHONE_X = 8f
    const val IN_PHONE_Y = 4f

    /** Where it starts and ends its slide: just above the viewport, which clips it (P-10.2: half-way is `y -36`). */
    const val IN_PHONE_HIDDEN_Y = -HEIGHT

    /** Where it sits on a peeking phone, screen-local GUI px (10.2): `sx 4..123`, `sy 14..51`. */
    const val PEEK_SCREEN_X_GUI = 4
    const val PEEK_SCREEN_Y_GUI = 14

    /** The banner's top over the open phone, app-local, for a [BannerTimeline.progress] of 0 (hidden) to 1 (resting). */
    fun inPhoneY(progress: Float): Float = IN_PHONE_HIDDEN_Y + (IN_PHONE_Y - IN_PHONE_HIDDEN_Y) * progress

    /**
     * How opaque the shadow is for a [BannerTimeline.progress] of 0 (hidden) to 1 (resting): it fades in and out with
     * the slide, so the 10 dp it reaches below the card don't show while the card is still above the viewport.
     */
    fun shadowOpacity(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** Whether the banner-local point ([x], [y]) is on the banner: what a click must hit. */
    fun contains(x: Float, y: Float): Boolean = x >= 0f && x < WIDTH && y >= 0f && y < HEIGHT

    /** The title on one line, cut with an ellipsis at [TEXT_WIDTH]. [font] is `title-m`'s (SemiBold). */
    fun title(title: String, font: FontMetrics): String =
        TextLayout.truncate(title, font, TextStyles.TITLE_M.size, TEXT_WIDTH)

    /**
     * The text word-wrapped on at most [MAX_TEXT_LINES] lines of [TEXT_WIDTH]; when it needs more, the second line ends
     * with an ellipsis, as CSS's line clamp does: the line as wrapped, shortened until the ellipsis fits. [font] is
     * `body`'s (Regular). Empty text gives no line.
     */
    fun textLines(text: String, font: FontMetrics): List<String> {
        if (text.isEmpty()) return emptyList()
        val size = TextStyles.BODY.size
        val lines = TextLayout.wrap(text, font, size, TEXT_WIDTH)
        if (lines.size <= MAX_TEXT_LINES) return lines
        // truncate() keeps a string that fits as it is, so "line…" stays whole when it fits and is cut back (before
        // the added ellipsis) when it doesn't.
        val last = TextLayout.truncate(lines[MAX_TEXT_LINES - 1] + TextLayout.ELLIPSIS, font, size, TEXT_WIDTH)
        return lines.subList(0, MAX_TEXT_LINES - 1) + last
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyle as SmoothStyle

/**
 * The phone's type styles. Apps write with Inter, in these eight styles only: no other size and no other weight, so
 * every app reads the same.
 *
 * Text is drawn in a *line box*: a band [lineHeight] dp tall with the text centred in it. The y you give to
 * [AppCanvas.drawText][fr.bde_teknantes.lnb.phone.api.client.AppCanvas.drawText] is the top of that box, which is how
 * the art direction places text ("line box at y 11").
 *
 * More styles may be added in later versions of the phone: a `when` over this enum needs an `else` branch.
 *
 * @property size The font size in dp.
 * @property lineHeight The height of the line box in dp; also the distance between two lines of wrapped text.
 * @property semibold Whether the style uses Inter SemiBold (600) rather than Regular (400).
 */
enum class TextStyle(val size: Float, val lineHeight: Float, val semibold: Boolean) {
    /** 17 / 22 dp, SemiBold: the title in an app's header. */
    TITLE_L(17f, 22f, true),

    /** 14 / 18 dp, SemiBold: names on posts, titles of dialogs and empty states. */
    TITLE_M(14f, 18f, true),

    /** 13 / 18 dp, Regular: running text, field text, snackbars, dialog text, menu items. */
    BODY(13f, 18f, false),

    /** 13 / 18 dp, SemiBold: list row titles, setting labels. */
    BODY_STRONG(13f, 18f, true),

    /** 13 / 16 dp, SemiBold: buttons, tabs, segmented controls. */
    LABEL(13f, 16f, true),

    /** 12 / 16 dp, Regular: handles, times, counts, helper text, tooltips. */
    CAPTION(12f, 16f, false),

    /** 12 / 16 dp, SemiBold: section headers, small emphasised labels. */
    CAPTION_STRONG(12f, 16f, true),

    /** 11 / 14 dp, SemiBold: count badges. The smallest text allowed. */
    MICRO(11f, 14f, true),
    ;

    /** The same style for the smooth layer's drawing code. */
    internal val smooth: SmoothStyle
        get() = when (this) {
            TITLE_L -> TextStyles.TITLE_L
            TITLE_M -> TextStyles.TITLE_M
            BODY -> TextStyles.BODY
            BODY_STRONG -> TextStyles.BODY_STRONG
            LABEL -> TextStyles.LABEL
            CAPTION -> TextStyles.CAPTION
            CAPTION_STRONG -> TextStyles.CAPTION_STRONG
            MICRO -> TextStyles.MICRO
        }
}

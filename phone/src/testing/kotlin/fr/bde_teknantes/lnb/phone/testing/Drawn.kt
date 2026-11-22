package fr.bde_teknantes.lnb.phone.testing

import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle

/** A line of text a screen drew in the last frame, its line box's top-left corner in viewport dp. */
class DrawnText(val text: String, val x: Float, val y: Float, val style: TextStyle, val color: Int) {
    override fun toString() = "\"$text\" at ($x, $y) in $style"
}

/** A shape a screen drew in the last frame, its bounding box in viewport dp, colour ARGB. */
class DrawnShape(val kind: Kind, val x: Float, val y: Float, val width: Float, val height: Float, val color: Int) {
    /** What was drawn. More kinds may be added: a `when` over it needs an `else`. */
    enum class Kind {
        /** A filled rectangle ([fr.bde_teknantes.lnb.phone.api.client.AppCanvas.fillRect]). */
        RECT,

        /** A filled rounded rectangle or pill. */
        ROUND_RECT,

        /** A filled circle: [width] is its diameter. */
        CIRCLE,
    }

    override fun toString() = "$kind ($x, $y) ${width}x$height #${Integer.toHexString(color)}"
}

/** A sound of the phone, as [PhoneTester.sounds] heard it. More may be added: a `when` over it needs an `else`. */
enum class PhoneSound {
    /** A click on something ([fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds.tap]). */
    TAP,

    /** Something was liked. */
    LIKE,

    /** Something was sent. */
    SENT,

    /** Something failed, or a disabled control was clicked. */
    ERROR,

    /** The phone came out. */
    OPEN,

    /** The phone was put away. */
    CLOSE,

    /** One chime of a notification (one arrives as two). */
    NOTIFICATION,
}

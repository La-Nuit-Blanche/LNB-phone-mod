package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A pill button, 32 dp tall, its label centred (art direction 8.7). A [Kind.PRIMARY] button unless told otherwise.
 *
 * Give buttons that sit in a fixed layout a [fixedWidth] wide enough for the longer of the English and French labels
 * (art direction 13.3); without one, the button is as wide as its label plus padding (at least 64 dp, except
 * [Kind.TEXT]). [loading] swaps the label for a spinner and makes the button unclickable while keeping its width.
 * Placed without a width ([at], [atRight]), a button follows its label when it changes, keeping its left edge (its
 * right edge with [atRight]).
 *
 * ```kotlin
 * val publish = Button(tr("compose.post")) { send() }.apply { fixedWidth = 88f }
 * publish.enabled = TextLimits.canSend(draft.text, 280)
 * ```
 */
class Button(label: String) : Clickable() {
    /** A button that runs [onClick] when clicked. */
    constructor(label: String, onClick: () -> Unit) : this(label) {
        this.onClick = onClick
    }

    /**
     * The looks of buttons. More may be added in later versions of the phone: a `when` over it needs an `else` branch.
     */
    enum class Kind {
        /** Filled with the accent: the main action of a screen. */
        PRIMARY,

        /** Filled red: confirms something that can't be undone. */
        DANGER,

        /** Outlined: the other choice (cancel, retry). */
        SECONDARY,

        /** Accent text, no fill: a light action inside something else (a snackbar's "Retry"). */
        TEXT,
    }

    /** The look. */
    var kind: Kind = Kind.PRIMARY

    var label: String = label

    /** A width that doesn't follow the label, in dp; null to follow it. */
    var fixedWidth: Float? = null

    /**
     * Shows a spinner instead of the label and ignores clicks, until set back to false: while the action the button
     * started is under way.
     */
    var loading = false

    private val spinner = Spinner().apply { size = 16f }

    override val preferredWidth: Float
        get() = fixedWidth ?: run {
            val padding = if (kind == Kind.TEXT) 8f else 16f
            val natural = TextMetrics.measure(label, TextStyle.LABEL) + 2 * padding
            if (kind == Kind.TEXT) natural else maxOf(MIN_WIDTH, natural)
        }

    override val preferredHeight get() = HEIGHT

    override val isBusy get() = loading

    override fun render(canvas: AppCanvas) {
        val look = interaction
        val disabled = look == Interaction.DISABLED
        val (fill, border, text) = colors(look)
        if (fill != 0) canvas.fillRoundRect(0f, 0f, width, height, Radius.FULL, fill)
        if (border != 0) canvas.strokeRoundRect(0f, 0f, width, height, Radius.FULL, 1f, border)
        if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, width, height, Radius.FULL)
        if (loading && !disabled) {
            val color = if (kind == Kind.PRIMARY || kind == Kind.DANGER) Colors.ON_ACCENT else Colors.ACCENT
            spinner.paint(canvas, (width - 16f) / 2f, (height - 16f) / 2f, color, now)
        } else {
            // A label too long for a fixed width keeps 8 dp from the edges and ends with an ellipsis.
            val shown = canvas.truncate(label, TextStyle.LABEL, width - 16f)
            canvas.drawTextCentered(shown, width / 2f, (height - TextStyle.LABEL.lineHeight) / 2f, TextStyle.LABEL, text)
        }
    }

    private fun colors(look: Interaction): Triple<Int, Int, Int> {
        val disabled = look == Interaction.DISABLED
        return when (kind) {
            Kind.PRIMARY -> when {
                disabled -> Triple(Colors.HAIRLINE, 0, Colors.TEXT_3)
                loading || look == Interaction.PRESSED -> Triple(Colors.ACCENT_PRESSED, 0, Colors.ON_ACCENT)
                look == Interaction.HOVER -> Triple(Colors.ACCENT_HOVER, 0, Colors.ON_ACCENT)
                else -> Triple(Colors.ACCENT, 0, Colors.ON_ACCENT)
            }
            Kind.DANGER -> when {
                disabled -> Triple(Colors.HAIRLINE, 0, Colors.TEXT_3)
                loading || look == Interaction.PRESSED -> Triple(Colors.DANGER_PRESSED, 0, Colors.ON_ACCENT)
                look == Interaction.HOVER -> Triple(Colors.DANGER_HOVER, 0, Colors.ON_ACCENT)
                else -> Triple(Colors.DANGER, 0, Colors.ON_ACCENT)
            }
            // A loading secondary button (the error state's retry) keeps its normal look around the spinner.
            Kind.SECONDARY -> when {
                disabled -> Triple(0, Colors.HAIRLINE, Colors.TEXT_3)
                loading -> Triple(0, Colors.STROKE, Colors.ACCENT)
                look == Interaction.PRESSED -> Triple(Colors.OVERLAY_PRESSED, Colors.STROKE, Colors.TEXT_1)
                look == Interaction.HOVER -> Triple(Colors.OVERLAY_HOVER, Colors.STROKE, Colors.TEXT_1)
                else -> Triple(0, Colors.STROKE, Colors.TEXT_1)
            }
            Kind.TEXT -> when {
                disabled -> Triple(0, 0, Colors.TEXT_3)
                look == Interaction.PRESSED -> Triple(Colors.ACCENT_SOFT_2, 0, Colors.ACCENT)
                look == Interaction.HOVER -> Triple(Colors.ACCENT_SOFT, 0, Colors.ACCENT)
                else -> Triple(0, 0, Colors.ACCENT)
            }
        }
    }

    companion object {
        val HEIGHT = 32f
        val MIN_WIDTH = 64f
    }
}

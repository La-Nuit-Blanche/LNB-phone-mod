package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A piece of text. One line by default, cut with an ellipsis when too long for its width; with [maxLines] above 1, it
 * wraps on words and its height follows (so it can sit in a [ScrollList]).
 *
 * @param align Where the text sits horizontally in the label's width.
 */
class Label(
    text: String,
    var style: TextStyle = TextStyle.BODY,
    var color: Int = Colors.TEXT_1,
    val maxLines: Int = 1,
    var align: Align = Align.START,
) : Widget() {
    enum class Align { START, CENTER, END }

    var text: String = text

    override val preferredWidth get() = TextMetrics.measure(text, style)
    override val preferredHeight get() = style.lineHeight

    override fun heightFor(width: Float): Float =
        if (maxLines == 1) style.lineHeight else lines(TextMetrics, width).size * style.lineHeight

    private fun lines(measurer: TextMeasurer, width: Float): List<String> =
        if (maxLines == 1) listOf(measurer.truncate(text, style, width)) else measurer.wrap(text, style, width, maxLines)

    override fun render(canvas: AppCanvas) {
        lines(canvas, width).forEachIndexed { i, line ->
            val w = canvas.measure(line, style)
            val x = when (align) {
                Align.START -> 0f
                Align.CENTER -> (width - w) / 2f
                Align.END -> width - w
            }
            canvas.drawText(line, x, i * style.lineHeight, style, color)
        }
    }
}

/** An icon on its own, tinted, centred in the widget. */
class IconView(var icon: Icon, var color: Int = Colors.TEXT_1, val size: Float = icon.sizes.first().toFloat()) : Widget() {
    override val preferredWidth get() = size
    override val preferredHeight get() = size

    override fun render(canvas: AppCanvas) = canvas.drawIcon(icon, (width - size) / 2f, (height - size) / 2f, color, size)
}

/**
 * A 1 dp divider (art direction 8.7) at the top of the widget, across its width; with [inset], it starts at x 56, as
 * between rows that have an avatar or icon.
 */
class Divider(val inset: Boolean = false) : Widget() {
    override val preferredWidth get() = 256f
    override val preferredHeight get() = 1f

    override fun render(canvas: AppCanvas) = canvas.hairline(if (inset) Spacing.CONTENT_X else 0f, width, 0f)
}

/**
 * A row of a list (art direction 8.7), the full width of the screen.
 *
 * - With a title only: 48 dp, the title in [TextStyle.BODY_STRONG].
 * - With a [subtitle]: 56 dp, the subtitle in grey under the title.
 * - [leading]: a 32 dp [Avatar] or an [IconView] (20 dp), in a 32 dp box at x 12; the text then starts at x 56.
 * - [trailing]: a [Toggle], an [IconView] of [Icon.CHEVRON], or a grey [Label], right-aligned at x 244. With a toggle,
 *   a click anywhere on the row switches it.
 *
 * The row lights up under the pointer and when pressed; it ends with a divider (from x 56 with a leading element)
 * unless [divider] is false.
 *
 * @param onClick What a click does; for a row with a toggle, leave it null and listen to the toggle.
 */
class ListRow(
    var title: String,
    var subtitle: String? = null,
    leading: Widget? = null,
    trailing: Widget? = null,
    var divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) : Clickable(onClick) {
    val leading: Widget? = leading?.let { add(it) }
    val trailing: Widget? = trailing?.let { add(it) }

    init {
        if (onClick == null && trailing is Toggle) {
            // The whole row switches it; the toggle plays the tap itself.
            this.onClick = { trailing.toggle() }
        }
    }

    override val catchesPointer get() = true

    override val preferredWidth get() = 256f
    override val preferredHeight get() = if (subtitle != null) 56f else 48f

    override fun layout() {
        leading?.let {
            val w = it.preferredWidth
            val h = it.preferredHeight
            val boxY = if (subtitle != null) 12f else (height - 32f) / 2f
            it.at(12f + (32f - w) / 2f, boxY + (32f - h) / 2f, w, h)
        }
        trailing?.let { it.atRight(Spacing.CONTENT_RIGHT, (height - it.preferredHeight) / 2f) }
    }

    override fun render(canvas: AppCanvas) {
        when (interaction) {
            Interaction.HOVER -> canvas.fillRect(0f, 0f, width, height, Colors.OVERLAY_HOVER)
            Interaction.PRESSED -> canvas.fillRect(0f, 0f, width, height, Colors.OVERLAY_PRESSED)
            else -> {}
        }
        if (showsFocusRing) canvas.strokeRoundRect(2f, 2f, width - 4f, height - 4f, Radius.SMALL, 2f, Colors.FOCUS_RING)
        val x = if (leading != null) Spacing.CONTENT_X else Spacing.SCREEN_PADDING
        val right = (trailing?.takeIf { it.visible }?.x ?: Spacing.CONTENT_RIGHT + 12f) - 12f
        val disabled = interaction == Interaction.DISABLED
        val sub = subtitle
        if (sub == null) {
            canvas.drawTextTruncated(title, x, (height - 18f) / 2f, right - x, TextStyle.BODY_STRONG, if (disabled) Colors.TEXT_3 else Colors.TEXT_1)
        } else {
            canvas.drawTextTruncated(title, x, 10f, right - x, TextStyle.BODY_STRONG, if (disabled) Colors.TEXT_3 else Colors.TEXT_1)
            canvas.drawTextTruncated(sub, x, 30f, right - x, TextStyle.CAPTION, if (disabled) Colors.TEXT_3 else Colors.TEXT_2)
        }
        if (divider) canvas.hairline(x, width, height - 1f)
    }

    override var enabled: Boolean
        get() = super.enabled
        set(value) {
            super.enabled = value
            (trailing as? Toggle)?.enabled = value
        }
}

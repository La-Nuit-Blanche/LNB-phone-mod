package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A piece of text, in [TextStyle.BODY] and [Colors.TEXT_1] unless told otherwise. One line by default, cut with an
 * ellipsis when too long for its width; with [maxLines] above 1, it wraps on words and its height follows (so it can
 * sit in a [ScrollList]).
 */
class Label(var text: String) : Widget() {
    /** Where the text sits horizontally. Complete for good: a `when` over it needs no `else`. */
    enum class Align { START, CENTER, END }

    var style: TextStyle = TextStyle.BODY
    var color: Int = Colors.TEXT_1
    var maxLines: Int = 1
    var align: Align = Align.START

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

/** An icon on its own, tinted ([Colors.TEXT_1] unless told otherwise), centred in the widget at its smallest size. */
class IconView(var icon: Icon) : Widget() {
    var color: Int = Colors.TEXT_1

    /** The size drawn, in dp; the icon's smallest by default. */
    var size: Float = icon.sizes.first().toFloat()

    override val preferredWidth get() = size
    override val preferredHeight get() = size

    override fun render(canvas: AppCanvas) = canvas.drawIcon(icon, (width - size) / 2f, (height - size) / 2f, color, size)
}

/**
 * A 1 dp divider (art direction 8.7) at the top of the widget, across its width; with [inset], it starts at x 56, as
 * between rows that have an avatar or icon.
 */
class Divider : Widget() {
    var inset = false

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
 *   a click anywhere on the row switches it, and the row is one Tab stop.
 *
 * The row lights up under the pointer and when pressed; it ends with a divider (from x 56 with a leading element)
 * unless [divider] is false.
 */
class ListRow(var title: String) : Clickable() {
    /** A row that runs [onClick] when clicked. */
    constructor(title: String, onClick: () -> Unit) : this(title) {
        this.onClick = onClick
    }

    var subtitle: String? = null
    var divider = true

    var leading: Widget? = null
        set(value) {
            field?.let(::remove)
            field = value?.let { add(it) }
            layout()
        }

    var trailing: Widget? = null
        set(value) {
            field?.let(::remove)
            field = value?.let { add(it) }
            layout()
        }

    override val preferredWidth get() = 256f
    override val preferredHeight get() = if (subtitle != null) 56f else 48f

    override fun perform() {
        // With a toggle and nothing else to do, the whole row switches it; the row's tap is the only one.
        val toggle = trailing as? Toggle
        if (onClick == null && toggle != null) toggle.toggle() else super.perform()
    }

    override fun layout() {
        leading?.let {
            val w = it.preferredWidth
            val h = it.preferredHeight
            val boxY = if (subtitle != null) 12f else (height - 32f) / 2f
            it.at(12f + (32f - w) / 2f, boxY + (32f - h) / 2f, w, h)
        }
        trailing?.let { it.atRight(Spacing.CONTENT_RIGHT, (height - it.preferredHeight) / 2f) }
    }

    override fun update() = layout()

    override fun render(canvas: AppCanvas) {
        KitDrawing.stateTint(canvas, interaction, 0f, 0f, width, height)
        if (showsFocusRing) KitDrawing.focusRing(canvas, 4f, 4f, width - 8f, height - 8f, Radius.SMALL - 4f)
        val x = if (leading != null) Spacing.CONTENT_X else Spacing.SCREEN_PADDING
        val right = (trailing?.takeIf { it.visible }?.x ?: (Spacing.CONTENT_RIGHT + 12f)) - 12f
        val disabled = interaction == Interaction.DISABLED
        val sub = subtitle
        val titleColor = if (disabled) Colors.TEXT_3 else Colors.TEXT_1
        if (sub == null) {
            canvas.drawTextTruncated(title, x, (height - 18f) / 2f, right - x, TextStyle.BODY_STRONG, titleColor)
        } else {
            canvas.drawTextTruncated(title, x, 10f, right - x, TextStyle.BODY_STRONG, titleColor)
            canvas.drawTextTruncated(sub, x, 30f, right - x, TextStyle.CAPTION, if (disabled) Colors.TEXT_3 else Colors.TEXT_2)
        }
        if (divider) canvas.hairline(x, width, height - 1f)
    }
}

/**
 * A title, an optional helper line and a toggle, the whole row switching it and counting as one Tab stop: Compose's
 * "Post anonymously" row (art direction 9.8). No hover tint of its own: the toggle shows the row's hover.
 *
 * - With a [helper]: 44 dp, the title in [TextStyle.BODY_STRONG] at row y 4, the helper in grey at y 22, the toggle
 *   at the right, centred.
 * - Without: 32 dp, the title and the toggle centred.
 * - [toggleFirst] puts the toggle at the left and the title 44 dp right of it, as the reply composer does (9.7).
 *
 * Give it its width (232 dp at x 12 in Compose).
 */
class ToggleRow(var title: String) : Clickable() {
    /** The row's switch: read and set [Toggle.checked], listen with [Toggle.onChange]. */
    val toggle: Toggle = add(Toggle())

    var helper: String? = null

    var toggleFirst = false

    init {
        silent = true
        onClick = { toggle.click() }
    }

    override val preferredWidth get() = 232f
    override val preferredHeight get() = if (helper != null) 44f else 32f

    override fun layout() {
        val y = (height - toggle.preferredHeight) / 2f
        if (toggleFirst) toggle.at(0f, y) else toggle.atRight(width, y)
    }

    override fun update() = layout()

    override fun render(canvas: AppCanvas) {
        val x = if (toggleFirst) 44f else 0f
        val right = if (toggleFirst) width else width - toggle.preferredWidth - 12f
        val disabled = interaction == Interaction.DISABLED
        val help = helper
        if (help == null) {
            canvas.drawTextTruncated(title, x, (height - 18f) / 2f, right - x, TextStyle.BODY_STRONG, if (disabled) Colors.TEXT_3 else Colors.TEXT_1)
        } else {
            canvas.drawTextTruncated(title, x, 4f, right - x, TextStyle.BODY_STRONG, if (disabled) Colors.TEXT_3 else Colors.TEXT_1)
            canvas.drawTextTruncated(help, x, 22f, right - x, TextStyle.CAPTION, if (disabled) Colors.TEXT_3 else Colors.TEXT_2)
        }
    }
}

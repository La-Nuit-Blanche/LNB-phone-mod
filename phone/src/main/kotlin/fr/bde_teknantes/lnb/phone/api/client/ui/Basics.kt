package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A piece of text, in [TextStyle.BODY] and [Colors.TEXT_1] unless told otherwise. One line by default, cut with an
 * ellipsis when too long for its width; with [maxLines] above 1, it wraps on words and its height follows (so it can
 * sit in a [ScrollList]).
 *
 * The text starts at the widget's left edge: placed on a screen, put the label at the margin (x 12); in a list, which
 * gives its rows the full width, give it an [inset] of [Spacing.SCREEN_PADDING] so it lines up with the rows' text.
 */
class Label(var text: String) : Widget() {
    /** Where the text sits horizontally. Complete for good: a `when` over it needs no `else`. */
    enum class Align { START, CENTER, END }

    var style: TextStyle = TextStyle.BODY
    var color: Int = Colors.TEXT_1
    var maxLines: Int = 1
    var align: Align = Align.START

    /** Space kept free at the left and at the right, in dp; none by default. */
    var inset: Float = 0f

    override val preferredWidth get() = TextMetrics.measure(text, style) + 2 * inset
    override val preferredHeight get() = style.lineHeight

    override fun heightFor(width: Float): Float =
        if (maxLines == 1) style.lineHeight else lines(TextMetrics, width).size * style.lineHeight

    private fun lines(measurer: TextMeasurer, width: Float): List<String> {
        val room = width - 2 * inset
        return if (maxLines == 1) listOf(measurer.truncate(text, style, room)) else measurer.wrap(text, style, room, maxLines)
    }

    override fun render(canvas: AppCanvas) {
        lines(canvas, width).forEachIndexed { i, line ->
            val w = canvas.measure(line, style)
            val x = when (align) {
                Align.START -> inset
                Align.CENTER -> (width - w) / 2f
                Align.END -> width - inset - w
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
 * A 1 dp divider (art direction 8.7) at the top of the widget, from [start] to its right edge: full width between
 * sections; from x 12 ([Spacing.SCREEN_PADDING]) under rows without a leading element, from x 56
 * ([Spacing.CONTENT_X]) under rows with an avatar or an icon, as a [ListRow]'s own divider does. 256 dp wide unless
 * told otherwise.
 *
 * ```kotlin
 * add(Divider().apply { start = Spacing.SCREEN_PADDING }.at(0f, 193f))
 * ```
 */
class Divider : Widget() {
    /** Where the line starts, in dp from the widget's left edge: 0 by default. */
    var start = 0f

    override val preferredWidth get() = 256f
    override val preferredHeight get() = 1f

    override fun render(canvas: AppCanvas) = canvas.hairline(start, width, 0f)
}

/**
 * A row of a list (art direction 8.7), the full width of the screen.
 *
 * - With a title only: 48 dp, the title in [TextStyle.BODY_STRONG].
 * - With a [subtitle]: 56 dp, the subtitle in grey under the title.
 * - [leading]: a 32 dp [Avatar] or an [IconView] (20 dp), in a 32 dp box at x 12; the text then starts at x 56.
 * - [trailing]: a [Toggle], an [IconView] of [Icon.CHEVRON], or a [Label], right-aligned at x 244. Give a chevron
 *   [Colors.TEXT_2] and a label [TextStyle.CAPTION] in [Colors.TEXT_2], as art direction 8.7 draws them: the row
 *   shows them as they are.
 *
 * A row with a trailing toggle and no [onClick] of its own is a setting's row: it owns the toggle. A click anywhere on
 * the row, the toggle included, switches it; the row lights up as one, and is one Tab stop (Space switches). With an
 * [onClick], the row opens something and the toggle is a target of its own: a click on the toggle switches it, a click
 * elsewhere runs [onClick], and Tab stops at the row, then at the toggle. To grey out a setting, disable its toggle:
 * the row that owns it counts as disabled too (grey title, no hover tint, the "can't" sound on a click).
 *
 * The row lights up under the pointer and when pressed. Its [divider] is its own last dp row, inside its 48 or 56 dp,
 * from x 56 with a leading element and x 12 without. Rows stacked in a [ScrollList] want just that. Rows that must
 * stand 49 dp apart with the line between them (Settings, art direction 12) set [divider] to false and get a [Divider]
 * under each.
 */
class ListRow(var title: String) : Clickable(), ToggleOwner {
    /** A row that runs [onClick] when clicked. */
    constructor(title: String, onClick: () -> Unit) : this(title) {
        this.onClick = onClick
    }

    /** A grey line under the title; the row is then 56 dp tall. */
    var subtitle: String? = null

    /** Whether the row draws its own divider on its last dp row (see above). */
    var divider = true

    var leading: Widget? = null
        // Forgotten once it is added elsewhere: this widget no longer places, draws around or clicks it.
        get() {
            if (field?.parent !== this) field = null
            return field
        }
        set(value) {
            field?.takeIf { it.parent === this }?.let(::remove)
            field = value?.let { add(it) }
            layout()
        }

    var trailing: Widget? = null
        // Forgotten once it is added elsewhere: this widget no longer places, draws around or clicks it.
        get() {
            if (field?.parent !== this) field = null
            return field
        }
        set(value) {
            field?.takeIf { it.parent === this }?.let(::remove)
            field = value?.let { add(it) }
            layout()
        }

    override val preferredWidth get() = 256f
    override val preferredHeight get() = if (subtitle != null) 56f else 48f

    override fun owns(toggle: Toggle) = toggle === trailing && onClick == null

    // A setting greyed out by disabling its toggle greys out its row.
    override val kitEnabled get() = (trailing as? Toggle)?.takeIf { owns(it) }?.enabled ?: true

    override fun perform() {
        // Owning its toggle, the row switches it; the row's tap is the only one.
        val toggle = trailing as? Toggle
        if (toggle != null && owns(toggle)) toggle.toggle() else super.perform()
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
 * A toggle with its label, inside a form: Compose's "Post anonymously" (art direction 9.8), the reply composer's
 * "Anonymous" (9.7). Not a row of a list: for a setting in a list, use a [ListRow] with a trailing [Toggle].
 *
 * It owns its [toggle] (see [Toggle]): a click anywhere on it switches the toggle, it is one Tab stop, and it has no
 * hover tint of its own: the toggle shows its hover. Read and set [Toggle.checked], listen with [Toggle.onChange]; an
 * [onClick], if you set one, runs after the switch. Disabling it or its toggle disables both.
 *
 * - With a [helper]: 44 dp, the title in [TextStyle.BODY_STRONG] at y 4, the helper in grey at y 22, the toggle at
 *   the right, centred.
 * - Without: 32 dp, the title and the toggle centred.
 * - [toggleFirst] puts the toggle at the left and the title 44 dp right of it, as the reply composer does (9.7).
 *
 * Give it its width (232 dp at x 12 in Compose).
 */
class LabeledToggle(var title: String) : Clickable(), ToggleOwner {
    /** The switch: read and set [Toggle.checked], listen with [Toggle.onChange]. */
    val toggle: Toggle = add(Toggle())

    var helper: String? = null

    var toggleFirst = false

    override fun owns(toggle: Toggle) = toggle === this.toggle

    override val kitEnabled get() = toggle.enabled

    override fun perform() {
        toggle.toggle()
        super.perform()
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

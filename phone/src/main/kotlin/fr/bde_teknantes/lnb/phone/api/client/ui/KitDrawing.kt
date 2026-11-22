package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * Marks switches meant for galleries and screenshots, which force a widget's look whatever the player does
 * ([Clickable.showAs], [TextInput.showFocused]...). An app has no use for them: using one needs an explicit
 * `@OptIn(KitPreview::class)`.
 */
@RequiresOptIn(message = "Forces a widget's look for galleries and screenshots; apps never need it.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class KitPreview

/**
 * The kit's standard decorations, for your own widgets to look like the kit's: the keyboard focus ring and the hover
 * and pressed tints.
 *
 * ```kotlin
 * override fun render(canvas: AppCanvas) {
 *     KitDrawing.stateTint(canvas, interaction, 0f, 0f, width, height)
 *     if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, width, height, Radius.SMALL)
 *     // ... the widget's content
 * }
 * ```
 */
object KitDrawing {
    /**
     * The keyboard focus ring around the rectangle ([x], [y], [w], [h]) whose corners have [radius]: 2 dp of
     * [Colors.FOCUS_RING], 2 dp outside it (art direction 8.7). [Radius.FULL] follows a pill or a circle.
     */
    fun focusRing(canvas: AppCanvas, x: Float, y: Float, w: Float, h: Float, radius: Float) {
        val r = if (radius >= Radius.FULL) Radius.FULL else radius + 4f
        canvas.strokeRoundRect(x - 4f, y - 4f, w + 8f, h + 8f, r, 2f, Colors.FOCUS_RING)
    }

    /**
     * The hover or pressed tint of rows, cards, tabs and menu items over the rectangle ([x], [y], [w], [h]):
     * [Colors.OVERLAY_HOVER] or [Colors.OVERLAY_PRESSED] for those looks, nothing otherwise. Draw it over the
     * widget's own fill and under its content.
     */
    fun stateTint(canvas: AppCanvas, interaction: Interaction, x: Float, y: Float, w: Float, h: Float) {
        when (interaction) {
            Interaction.HOVER -> canvas.fillRect(x, y, w, h, Colors.OVERLAY_HOVER)
            Interaction.PRESSED -> canvas.fillRect(x, y, w, h, Colors.OVERLAY_PRESSED)
            else -> {}
        }
    }

    /** The disc behind a hovered or pressed icon button: [Colors.OVERLAY_PRESSED], then [Colors.OVERLAY_PRESSED_2]. */
    fun iconDisc(canvas: AppCanvas, interaction: Interaction, x: Float, y: Float, diameter: Float) {
        when (interaction) {
            Interaction.HOVER -> canvas.fillCircle(x, y, diameter, Colors.OVERLAY_PRESSED)
            Interaction.PRESSED -> canvas.fillCircle(x, y, diameter, Colors.OVERLAY_PRESSED_2)
            else -> {}
        }
    }
}

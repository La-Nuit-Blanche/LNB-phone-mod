package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A button that is only an icon (art direction 8.7): no fill; a disc appears under the pointer and darkens when
 * pressed. 32 x 32 dp in headers, 28 x 28 in post cards ([size]). Give it a [tooltip] saying what it does.
 *
 * @param icon Drawn at its smallest size, centred.
 * @param tint The icon's colour: [Colors.TEXT_1] in headers, [Colors.TEXT_2] in post cards.
 */
class IconButton(
    var icon: Icon,
    val size: Float = 32f,
    var tint: Int = Colors.TEXT_1,
    onClick: (() -> Unit)? = null,
) : Clickable(onClick) {
    override val preferredWidth get() = size
    override val preferredHeight get() = size

    override fun render(canvas: AppCanvas) {
        when (interaction) {
            Interaction.HOVER -> canvas.fillCircle(0f, 0f, width, Colors.OVERLAY_PRESSED)
            Interaction.PRESSED -> canvas.fillCircle(0f, 0f, width, Colors.OVERLAY_PRESSED_2)
            else -> {}
        }
        if (showsFocusRing) focusRing(canvas, width, height)
        val iconSize = icon.sizes.first().toFloat()
        val color = if (interaction == Interaction.DISABLED) Colors.TEXT_3 else tint
        canvas.drawIcon(icon, (width - iconSize) / 2f, (height - iconSize) / 2f, color, iconSize)
    }
}

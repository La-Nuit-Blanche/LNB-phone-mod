package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas

/**
 * A button that is only an icon (art direction 8.7): no fill; a disc appears under the pointer and darkens when
 * pressed. 32 x 32 dp in headers, 28 x 28 in post cards ([size]). Give it a [tooltip] saying what it does. The icon is
 * drawn at its smallest size, centred.
 */
class IconButton(var icon: Icon) : Clickable() {
    /** A button that runs [onClick] when clicked. */
    constructor(icon: Icon, onClick: () -> Unit) : this(icon) {
        this.onClick = onClick
    }

    /** 32 dp, or 28 in post cards. */
    var size = 32f

    /** The icon's colour: [Colors.TEXT_1] in headers, [Colors.TEXT_2] in post cards. */
    var tint = Colors.TEXT_1

    override val preferredWidth get() = size
    override val preferredHeight get() = size

    override fun render(canvas: AppCanvas) {
        KitDrawing.iconDisc(canvas, interaction, 0f, 0f, width)
        if (showsFocusRing) KitDrawing.focusRing(canvas, 0f, 0f, width, height, Radius.FULL)
        val iconSize = icon.sizes.first().toFloat()
        val color = if (interaction == Interaction.DISABLED) Colors.TEXT_3 else tint
        canvas.drawIcon(icon, (width - iconSize) / 2f, (height - iconSize) / 2f, color, iconSize)
    }
}

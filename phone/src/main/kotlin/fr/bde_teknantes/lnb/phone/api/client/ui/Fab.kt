package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.Shadow
import fr.bde_teknantes.lnb.phone.client.ui.Placement

/**
 * The floating action button (art direction 8.7): a 48 dp accent circle with a 24 dp icon and a soft shadow, at
 * (196, 310), 12 dp from the viewport's right and bottom edges. It places itself: add it to the screen last, so it
 * floats over the content. While a snackbar shows, it moves up out of its way (150 ms).
 *
 * @param floating Whether it keeps its place in the corner and dodges snackbars; false lets you place it with [at]
 * (a gallery).
 */
class Fab(var icon: Icon = Icon.COMPOSE, private val floating: Boolean = true, onClick: (() -> Unit)? = null) : Clickable(onClick) {
    init {
        setBounds(196f, Placement.FAB_Y, SIZE, SIZE)
    }

    override val preferredWidth get() = SIZE
    override val preferredHeight get() = SIZE

    override fun update() {
        if (!floating) return
        val lift = screen?.snackbarLift() ?: 0f
        setBounds(x, Placement.FAB_Y + (Placement.FAB_LIFTED_Y - Placement.FAB_Y) * lift, SIZE, SIZE)
    }

    override fun render(canvas: AppCanvas) {
        canvas.drawShadow(Shadow.FAB, 0f, 0f, width, height)
        val fill = when (interaction) {
            Interaction.PRESSED -> Colors.ACCENT_PRESSED
            Interaction.HOVER -> Colors.ACCENT_HOVER
            Interaction.DISABLED -> Colors.HAIRLINE
            Interaction.NORMAL -> Colors.ACCENT
        }
        canvas.fillCircle(0f, 0f, width, fill)
        if (showsFocusRing) focusRing(canvas, width, height)
        val color = if (interaction == Interaction.DISABLED) Colors.TEXT_3 else Colors.ON_ACCENT
        canvas.drawIcon(icon, (width - 24f) / 2f, (height - 24f) / 2f, color, 24f)
    }

    companion object {
        const val SIZE = 48f
    }
}

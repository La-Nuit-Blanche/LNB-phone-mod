package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at

/**
 * The 48 dp row that takes the spinner row's place when older posts or replies can't be loaded (art direction 9.12,
 * F-24): the text button `Réessayer` centred. The kit has the spinner row and the message row, not this one.
 */
class RetryRow(label: String, onRetry: () -> Unit) : Widget() {
    val button: Button = add(Button(label, onRetry).apply { kind = Button.Kind.TEXT })

    override val preferredWidth get() = 256f
    override val preferredHeight get() = HEIGHT

    override fun update() = layout()

    override fun layout() {
        val w = button.preferredWidth
        button.at(((width - w) / 2f).toInt().toFloat(), (HEIGHT - button.preferredHeight) / 2f)
    }

    companion object {
        const val HEIGHT = 48f
    }
}

package fr.bde_teknantes.lnb.phone.testing

import fr.bde_teknantes.lnb.phone.api.client.ui.TextMeasurer
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout

/**
 * Measures text as [PhoneTester] does, every character half its size wide (6.5 dp in [TextStyle.BODY]), with the
 * phone's own rules for cutting and wrapping: for testing code of yours that lays text out through a [TextMeasurer]
 * (`TextMetrics` in the game), without a tester.
 */
object TestTextMeasurer : TextMeasurer() {
    override fun measure(text: String, style: TextStyle): Float = TextLayout.width(text, font, style.size)

    override fun truncate(text: String, style: TextStyle, maxWidth: Float): String = TextLayout.truncate(text, font, style.size, maxWidth)

    override fun wrap(text: String, style: TextStyle, maxWidth: Float): List<String> = wrap(text, style, maxWidth, Int.MAX_VALUE)

    override fun wrap(text: String, style: TextStyle, maxWidth: Float, maxLines: Int): List<String> =
        TextLayout.wrap(text, font, style.size, maxWidth, maxLines)

    private val font: FontMetrics get() = MonospaceFont
}

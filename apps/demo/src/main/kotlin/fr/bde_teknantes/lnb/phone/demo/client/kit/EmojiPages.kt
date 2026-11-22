@file:OptIn(KitPreview::class)

package fr.bde_teknantes.lnb.phone.demo.client.kit

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.IconView
import fr.bde_teknantes.lnb.phone.api.client.ui.KitPreview
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.ScrollList
import fr.bde_teknantes.lnb.phone.api.client.ui.TextArea
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.demo.client.smooth.SampleSkins

/**
 * Emoji in the kit (task 13): the same French text with common emoji in a text area (caret after an emoji), a field,
 * a post card, list rows and a snackbar; every kind of emoji sequence in each text style, Regular and SemiBold; and a
 * page of many distinct emoji for the glyph atlas at high GUI scales.
 */
object EmojiPages {
    const val PARAGRAPH = "Soirée au spawn ce soir 🎉 On part au Nether 🔥 vers 21:00, ramenez des potions 🧪 et à manger 🍗. " +
        "Qui vient ? 😄👍"

    /** One line per kind of sequence, with a label. */
    val SEQUENCES = listOf(
        "Simples" to "😀 🎉 🔥 ❤️ 👀 🐺 ✨",
        "Peau" to "👍 👍🏽 👋🏿 🙏🏻 💪🏼",
        "ZWJ" to "👨‍👩‍👧 ❤️‍🔥 🏳️‍🌈 👩🏽‍💻",
        "Drapeaux" to "🇫🇷 🇧🇪 🇨🇦 🏴󠁧󠁢󠁥󠁮󠁧󠁿 🇽🇽",
        "Touches" to "1️⃣ #️⃣ *️⃣ · 1 # *",
        "Sélecteur" to "❤️ ❤ ©️ © ™️ ™ ☺️",
        "Repli" to "😀🏽 👨‍🦖 🫩 田",
    )

    val pages: List<HostedPage> = listOf(
        HostedPage("Emoji: text area, field, snackbar") { inputs() },
        HostedPage("Emoji: post card, rows") { post() },
        HostedPage("Emoji: sequences, Regular") { sequences(semibold = false) },
        HostedPage("Emoji: sequences, SemiBold") { sequences(semibold = true) },
        HostedPage("Emoji: many (atlas)") { many() },
    )

    private fun inputs() = Gallery.Page("Emoji") {
        caption("Text area, focused, caret after 🔥", 50f)
        val state = TextState.multiline().also { it.text = PARAGRAPH }
        state.moveTo(PARAGRAPH.indexOf("🔥") + "🔥".length)
        add(TextArea(state).apply {
            limit = 280
            showFocused = true
        }.at(12f, 68f, 232f, 128f))
        caption("Field, caret after 🎉", 204f)
        val field = TextState.singleLine().also { it.text = "Bravo 🎉👍🏽 à tous !" }
        field.moveTo("Bravo 🎉".length)
        add(TextField(field).apply { showFocused = true }.at(12f, 222f, 232f, 40f))
        showSnackbar("Publié 🎉 Merci !")
    }

    private fun post() = Gallery.Page("Emoji") {
        val list = add(ScrollList().at(0f, 44f, 256f, 326f))
        val camille = Author("Cam_Roux", "Camille 🌸 Roux", { SampleSkins.CAMILLE })
        list.add(PostCard(Post(20, camille, { tr("time.minutes", 2) }, PARAGRAPH, 3, 12, liked = true)))
        list.add(ListRow("Les Loups 🐺 du Nord").apply {
            subtitle = "12 membres · 🔥 actifs"
            trailing = IconView(Icon.CHEVRON).apply { color = Colors.TEXT_2 }
        })
        list.add(ListRow("Sondage 📊 : 1️⃣ ou 2️⃣ ?"))
        list.add(Label("Libellé : on y va 🚀 !").apply { inset = 12f })
    }

    /** Each style of the ramp in the chosen weight, a line per kind of sequence, on alternating line boxes. */
    private fun sequences(semibold: Boolean) = Gallery.Page(if (semibold) "Emoji SemiBold" else "Emoji Regular") {
        add(object : Widget() {
            override fun render(canvas: AppCanvas) {
                val styles = TextStyle.entries.filter { it.semibold == semibold }
                val body = if (semibold) TextStyle.BODY_STRONG else TextStyle.BODY
                var y = 4f
                for ((label, line) in SEQUENCES) {
                    canvas.fillRect(0f, y, 256f, body.lineHeight, if (((y / body.lineHeight).toInt()) % 2 == 0) BAND else Colors.BG)
                    canvas.drawText(label, 12f, y, TextStyle.CAPTION, Colors.TEXT_3)
                    canvas.drawText(line, 76f, y, body, Colors.TEXT_1)
                    y += body.lineHeight
                }
                y += 8f
                for (style in styles) {
                    canvas.fillRect(0f, y, 256f, style.lineHeight, BAND)
                    canvas.drawText("${style.name.lowercase()} Où ça ? 😄🎉👍🏽🇫🇷", 12f, y, style, Colors.TEXT_1)
                    y += style.lineHeight + 2f
                }
                y += 6f
                // Colour comes from the text: the same line in the accent, danger and secondary colours.
                for (color in listOf(Colors.ACCENT, Colors.DANGER, Colors.TEXT_2)) {
                    canvas.drawText("Couleur 🎉🔥❤️ texte", 12f, y, body, color)
                    y += body.lineHeight
                }
            }
        }.at(0f, 44f, 256f, 326f))
    }

    /** About two hundred distinct emoji, wrapped: every one is a separate glyph in the atlas. */
    private fun many() = Gallery.Page("Emoji ×200") {
        val text = buildString {
            var cp = 0x1F600
            while (cp <= 0x1F64F) appendCodePoint(cp++)
            cp = 0x1F400
            while (cp <= 0x1F47F) appendCodePoint(cp++)
        }
        add(Label(text).apply {
            maxLines = 40
            inset = 12f
        }.at(0f, 44f, 256f, 326f))
    }

    private const val BAND = 0xFF1A1E26.toInt()
}

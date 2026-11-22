package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.Divider
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.Label
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.Spacing
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.api.client.ui.Toggle
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import net.minecraft.client.resources.language.I18n

/**
 * The Settings app's one screen (art direction 12): where the phone stands, its sounds, the notification preview.
 * Built from the public UI kit only, like any app's screen.
 */
internal class SettingsScreen(private val choices: SettingsChoices) : KitScreen() {
    // Each widget starts on the stored value: set before the first frame, it stands there without sliding.
    private val position = SegmentedControl(listOf(tr("position.corner"), tr("position.centre"))).apply {
        selected = choices.position
        onSelect = choices::choosePosition
    }
    private val sounds = Toggle().apply {
        checked = choices.sounds
        onChange = choices::setSounds
    }
    private val preview = Toggle().apply {
        checked = choices.preview
        onChange = choices::setPreview
    }

    init {
        val x = Spacing.SCREEN_PADDING
        val width = Spacing.CONTENT_RIGHT - x
        add(Header(I18n.get(TITLE_KEY)).at(0f, 0f))
        add(Label(tr("section.phone")).apply {
            style = TextStyle.CAPTION_STRONG
            color = Colors.TEXT_2
        }.at(x, 56f, width, TextStyle.CAPTION_STRONG.lineHeight))
        add(Label(tr("position")).apply { style = TextStyle.BODY_STRONG }.at(x, 76f, width, TextStyle.BODY_STRONG.lineHeight))
        add(position.at(x, 100f))
        add(Divider().at(0f, 144f))
        // Rows 49 dp apart with the hairline between them, as section 12 places them: a row's own divider would sit
        // on its last line, 1 dp higher.
        add(toggleRow(tr("sounds"), sounds).at(0f, 145f))
        add(Divider().at(x, 193f, 256f - x, 1f))
        add(toggleRow(tr("preview"), preview).at(0f, 194f))
        add(Divider().at(x, 242f, 256f - x, 1f))
    }

    // The store may change while the screen is up (the placement set from elsewhere): show what it holds.
    override fun onTick() {
        position.selected = choices.position
        sounds.checked = choices.sounds
        preview.checked = choices.preview
    }

    private fun toggleRow(title: String, toggle: Toggle) = ListRow(title).apply {
        trailing = toggle
        divider = false
    }

    companion object {
        /** The app's name, on the home screen and in the header. */
        const val TITLE_KEY = "app.lnbphone.settings"

        private fun tr(key: String): String = I18n.get("settings.lnbphone.$key")
    }
}

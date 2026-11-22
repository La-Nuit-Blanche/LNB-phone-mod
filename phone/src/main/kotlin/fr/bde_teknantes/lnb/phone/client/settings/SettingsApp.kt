package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * The phone's built-in Settings app (art direction 12), registered through [RegisterPhoneAppsEvent] like any other
 * app; the registry puts it last on the home screen. Its graphite gear is the phone's own glyph (7.4).
 */
internal object SettingsApp {
    fun register(event: RegisterPhoneAppsEvent) {
        event.register(
            PhoneApp(
                id = AppRegistry.SETTINGS_ID,
                name = Component.translatable(SettingsScreen.TITLE_KEY),
                icon = AppIcon.Glyph(TileColor.GRAPHITE, ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/sprites/icon/settings.png")),
            ) { SettingsScreen(SettingsChoices(LnbPhoneClient.settings)) },
        )
    }
}

package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistration
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.fml.ModList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.function.Consumer

/** The Settings app's registration (art direction 6 and 12) and its strings (13.2). */
class SettingsAppTest {
    private fun phoneEvent(registry: AppRegistry) = RegisterPhoneAppsEvent(registry).apply { modId = LnbPhone.ID }

    @Test
    fun `registers one app, lnbphone settings, named from the language files, with the graphite gear`() {
        val registry = AppRegistry()
        SettingsApp.register(phoneEvent(registry))

        val app = registry.apps.single()
        assertEquals("lnbphone:settings", app.id.toString())
        assertEquals(AppRegistry.SETTINGS_ID, app.id)
        val name = assertInstanceOf(TranslatableContents::class.java, app.name.contents)
        assertEquals("app.lnbphone.settings", name.key)
        assertEquals(SettingsScreen.TITLE_KEY, name.key)
        val icon = assertInstanceOf(AppIcon.Glyph::class.java, app.icon)
        assertEquals(TileColor.GRAPHITE, icon.tile)
        assertEquals("lnbphone:textures/gui/sprites/icon/settings.png", icon.texture.toString())
        assertNotNull(javaClass.getResource("/assets/lnbphone/textures/gui/sprites/icon/settings.png"), "the gear's sprite ships")
    }

    @Test
    fun `through the phone's own bus it comes once, and last, whatever registers after it`() {
        // LnbPhoneClient.init puts SettingsApp on the phone's bus; tests run as a dedicated server, so this does it.
        val bus = ModList.get().getModContainerById(LnbPhone.ID).get().eventBus!!
        val settings = Consumer<RegisterPhoneAppsEvent> { SettingsApp.register(it) }
        val others = Consumer<RegisterPhoneAppsEvent> { event -> (0 until 13).forEach { event.register(testApp("app$it")) } }
        bus.addListener(RegisterPhoneAppsEvent::class.java, settings)
        bus.addListener(RegisterPhoneAppsEvent::class.java, others)
        val registry = AppRegistry()
        try {
            AppRegistration.fire(registry)
        } finally {
            bus.unregister(settings)
            bus.unregister(others)
        }
        assertEquals(14, registry.apps.size)
        assertEquals(1, registry.apps.count { it.id == AppRegistry.SETTINGS_ID })
        assertEquals(AppRegistry.SETTINGS_ID, registry.apps.last().id)
        assertEquals((0 until 13).map { "app$it" }, registry.apps.dropLast(1).map { it.id.path })
    }

    @Test
    fun `registered twice, or after a mod's impostor, it is still the one Settings`() {
        val registry = AppRegistry()
        val fake = testApp("x")
        val impostor = PhoneApp(AppRegistry.SETTINGS_ID, fake.name, fake.icon) { error("not ours") }
        RegisterPhoneAppsEvent(registry).apply { modId = "othermod" }.register(impostor)
        val event = phoneEvent(registry)
        SettingsApp.register(event)
        SettingsApp.register(event)
        event.register(testApp("late"))
        val app = registry.apps.last()
        assertEquals(2, registry.apps.size)
        assertEquals(AppRegistry.SETTINGS_ID, app.id)
        assertInstanceOf(TranslatableContents::class.java, app.name.contents)
    }

    @Test
    fun `the strings of section 13 are in both language files, word for word`() {
        // Section 13.2's ids, with the mod's namespace after their first part.
        val expected = mapOf(
            "app.lnbphone.settings" to ("Settings" to "Réglages"),
            "settings.lnbphone.section.phone" to ("PHONE" to "TÉLÉPHONE"),
            "settings.lnbphone.position" to ("Position on screen" to "Position à l'écran"),
            "settings.lnbphone.position.corner" to ("Corner" to "Coin"),
            "settings.lnbphone.position.centre" to ("Centre" to "Centre"),
            "settings.lnbphone.sounds" to ("Sounds" to "Sons"),
            "settings.lnbphone.preview" to ("Notification preview" to "Aperçu des notifications"),
        )
        val en = lang("en_us")
        val fr = lang("fr_fr")
        for ((key, texts) in expected) {
            assertEquals(texts.first, en.get(key)?.asString, "en_us $key")
            assertEquals(texts.second, fr.get(key)?.asString, "fr_fr $key")
        }
        // Both files hold the same keys, so no string falls back to English for French players or the other way.
        assertEquals(en.keySet(), fr.keySet())
    }

    private fun lang(name: String): JsonObject =
        javaClass.getResourceAsStream("/assets/lnbphone/lang/$name.json")!!.reader(Charsets.UTF_8)
            .use { JsonParser.parseReader(it).asJsonObject }
}

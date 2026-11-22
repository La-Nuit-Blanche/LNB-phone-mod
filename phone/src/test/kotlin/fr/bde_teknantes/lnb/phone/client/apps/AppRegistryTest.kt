package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class AppRegistryTest {
    private val settings = PhoneApp(
        AppRegistry.SETTINGS_ID,
        Component.literal("Settings"),
        AppIcon.Glyph(TileColor.GRAPHITE, AppRegistry.SETTINGS_ID),
    ) { RecordingScreen("settings", mutableListOf()) }

    @Test
    fun `apps keep registration order and Settings comes last`() {
        val registry = AppRegistry()
        val a = testApp("a")
        val b = testApp("b")
        registry.register(a, "test")
        registry.register(settings, LnbPhone.ID)
        registry.register(b, "test")
        assertEquals(listOf(a, b, settings), registry.apps)
    }

    @Test
    fun `a duplicate id keeps the first app`() {
        val registry = AppRegistry()
        val first = testApp("a")
        registry.register(first, "one")
        assertFalse(registry.register(testApp("a"), "two"))
        assertEquals(listOf(first), registry.apps)
        assertSame(first, registry[appId("a")])
    }

    @Test
    fun `registering after the freeze throws`() {
        val registry = AppRegistry()
        registry.freeze()
        assertThrows(IllegalStateException::class.java) { registry.register(testApp("a"), "test") }
    }
}

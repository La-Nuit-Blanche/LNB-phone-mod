package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun `Settings registered first still comes last`() {
        val registry = AppRegistry()
        val a = testApp("a")
        val b = testApp("b")
        registry.register(settings, LnbPhone.ID)
        registry.register(a, "test")
        assertEquals(listOf(a, settings), registry.apps)
        registry.register(b, "test")
        assertEquals(listOf(a, b, settings), registry.apps)
    }

    @Test
    fun `apps from several mods keep their order and nothing moves without Settings`() {
        val registry = AppRegistry()
        val apps = listOf(testApp("z"), testApp("a"), testApp("m"))
        assertTrue(registry.register(apps[0], "first"))
        assertTrue(registry.register(apps[1], "second"))
        assertTrue(registry.register(apps[2], "first"))
        assertEquals(apps, registry.apps)
    }

    @Test
    fun `more apps than a home page holds are all kept in order with Settings last`() {
        // Art direction 6: 30 icons a page, more apps go on further pages; the registry itself has no limit.
        val registry = AppRegistry()
        val apps = (1..61).map { testApp("app$it") }
        apps.take(45).forEach { registry.register(it, "test") }
        registry.register(settings, LnbPhone.ID)
        apps.drop(45).forEach { registry.register(it, "test") }

        assertEquals(62, registry.apps.size)
        assertEquals(apps + settings, registry.apps)
    }

    @Test
    fun `a second Settings is left out like any duplicate`() {
        val registry = AppRegistry()
        val impostor = testApp("x")
        val other = PhoneApp(AppRegistry.SETTINGS_ID, Component.literal("Other"), impostor.icon) { RecordingScreen("other", mutableListOf()) }
        registry.register(settings, LnbPhone.ID)
        assertFalse(registry.register(other, "othermod"))
        registry.register(impostor, "test")
        assertSame(settings, registry[AppRegistry.SETTINGS_ID])
        assertEquals(listOf(impostor, settings), registry.apps)
    }

    @Test
    fun `a duplicate leaves the first app in its original place`() {
        val registry = AppRegistry()
        val a = testApp("a")
        val b = testApp("b")
        registry.register(a, "one")
        registry.register(b, "one")
        registry.register(testApp("a"), "two")
        assertEquals(listOf(a, b), registry.apps)
    }

    @Test
    fun `an id that was never registered finds nothing`() {
        val registry = AppRegistry()
        registry.register(testApp("a"), "test")
        assertNull(registry[appId("b")])
        assertNull(registry[AppRegistry.SETTINGS_ID])
    }

    @Test
    fun `the freeze keeps the apps and refuses even a new id`() {
        val registry = AppRegistry()
        val a = testApp("a")
        registry.register(a, "test")
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertThrows(IllegalStateException::class.java) { registry.register(testApp("b"), "test") }
        assertThrows(IllegalStateException::class.java) { registry.register(testApp("a"), "test") }
        assertEquals(listOf(a), registry.apps)
        assertNull(registry[appId("b")])
    }

    @Test
    fun `the event adds apps to the registry and refuses them once registration is over`() {
        val registry = AppRegistry()
        val event = RegisterPhoneAppsEvent(registry)
        val a = testApp("a")
        event.register(a)
        assertEquals(listOf(a), registry.apps)

        registry.freeze()
        assertThrows(IllegalStateException::class.java) { event.register(testApp("b")) }
        assertEquals(listOf(a), registry.apps)
    }

    @Test
    fun `only the phone registers ids in its namespace`() {
        val registry = AppRegistry()
        val other = PhoneApp(AppRegistry.SETTINGS_ID, Component.literal("Other"), settings.icon) { RecordingScreen("other", mutableListOf()) }
        assertFalse(registry.register(other, "othermod"))
        assertFalse(registry.register(other, null))
        assertNull(registry[AppRegistry.SETTINGS_ID])

        assertTrue(registry.register(settings, LnbPhone.ID))
        assertSame(settings, registry[AppRegistry.SETTINGS_ID])
    }

    @Test
    fun `the Settings id is the phone's`() {
        assertEquals("${LnbPhone.ID}:settings", AppRegistry.SETTINGS_ID.toString())
    }
}

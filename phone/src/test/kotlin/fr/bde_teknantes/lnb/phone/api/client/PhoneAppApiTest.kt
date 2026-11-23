package fr.bde_teknantes.lnb.phone.api.client

import fr.bde_teknantes.lnb.phone.client.apps.RecordingScreen
import fr.bde_teknantes.lnb.phone.client.apps.testApp
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/** The parts of the public API that hold logic of their own. */
class PhoneAppApiTest {
    @Test
    fun `the root factory makes a new screen on every call`() {
        var made = 0
        val app = testApp("app") { RecordingScreen("root${++made}", mutableListOf()) }
        val first = app.createRootScreen()
        val second = app.createRootScreen()
        assertNotSame(first, second)
        assertEquals(2, made)
    }

    @Test
    fun `an app without target support gives no screens for a target`() {
        assertEquals(emptyList(), testApp("app").createTargetScreens("thread:1"))
    }

    @Test
    fun `target screens come from the app's target factory, given the target as is`() {
        val asked = mutableListOf<String>()
        val app = testApp("app", targets = { asked += it; emptyList() })
        app.createTargetScreens("thread:4182")
        app.createTargetScreens("")
        assertEquals(listOf("thread:4182", ""), asked)
    }

    @Test
    fun `a screen can't reach the phone before the phone has it`() {
        val screen = RecordingScreen("loose", mutableListOf())
        assertThrows(IllegalStateException::class.java) { screen.handle }
    }

    @Test
    fun `the tile palette is the art direction's`() {
        // Art direction 7.3, in order: hi, base, lo.
        val expected = listOf(
            "AZURE" to listOf(0x7CC0FF, 0x3A8DF0, 0x1F5FB0),
            "TEAL" to listOf(0x7FE3E0, 0x1FA3A0, 0x12706E),
            "GREEN" to listOf(0x9BEA7A, 0x4DB33C, 0x2D7A22),
            "ORANGE" to listOf(0xFFC48A, 0xE57A24, 0xA65116),
            "RED" to listOf(0xFF8F86, 0xDB4237, 0x9A2720),
            "PINK" to listOf(0xFFB0DA, 0xE0609F, 0xA03A70),
            "VIOLET" to listOf(0xC7A8FF, 0x8457E6, 0x56339E),
            "GRAPHITE" to listOf(0x5C6372, 0x3A404B, 0x23272E),
        ).map { (name, rgb) -> name to rgb.map { 0xFF000000.toInt() or it } }
        assertEquals(expected, TileColor.entries.map { it.name to listOf(it.hi, it.base, it.lo) })
    }

    @Test
    fun `an item icon keeps its own copy of the stack`() {
        val stack = ItemStack(Items.DIAMOND, 3)
        val icon = AppIcon.Item(TileColor.TEAL, stack)
        stack.count = 1
        assertEquals(3, icon.stack.count)
        assertEquals(Items.DIAMOND, icon.stack.item)
    }

    @Test
    fun `the viewport is 256 by 370 dp`() {
        // Art direction 2.1: 128 x 185 GUI px, 2 dp to the pixel.
        assertEquals(256, AppCanvas.WIDTH)
        assertEquals(370, AppCanvas.HEIGHT)
    }
}

package fr.bde_teknantes.lnb.phone.client.settings

import fr.bde_teknantes.lnb.phone.api.client.ui.KitFakes
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.SegmentedControl
import fr.bde_teknantes.lnb.phone.api.client.ui.Toggle
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.KNOB_OFF
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.KNOB_ON
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PILL_CENTRE
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PILL_CORNER
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.SOUNDS_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.TOGGLE_X
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import java.nio.file.Path

/**
 * What the kit does today, seen from its first real app. These pin behaviour the Settings screen depends on or works
 * around; they are observations, not wishes: where the kit's documentation says otherwise, the test says so.
 */
class KitFromSettingsTest {
    @TempDir
    lateinit var dir: Path

    private class Plain : KitScreen()

    @BeforeEach
    fun setUp() {
        KitFakes.install()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `a row with a click handler of its own doesn't switch its toggle, though ListRow's KDoc says any click does`() {
        val screen = Plain()
        var opened = 0
        val toggle = Toggle()
        val row = screen.add(ListRow("Wi-Fi") { opened++ }.apply { trailing = toggle }.at(0f, 0f))
        val p = ScreenPlayer(screen).apply { frame() }

        p.click(40f, 24f)
        assertEquals(1, opened)
        assertFalse(toggle.checked)

        p.key(GLFW_KEY_TAB)
        assertSame(row, screen.focused)
        p.key(GLFW_KEY_SPACE)
        assertEquals(2, opened)
        assertFalse(toggle.checked)

        // Only the toggle itself still switches it.
        p.click(226f, 24f)
        assertTrue(toggle.checked)
        assertEquals(2, opened)
    }

    @Test
    fun `the tap sound comes before the toggle flips, so it follows the setting as it was`() {
        // The kit has no seam for sounds: with sounds on, PhoneSounds goes to Minecraft.getInstance(), which tests
        // don't have, and throws. Where it throws shows the order. In the game, PhoneSounds reads the Sounds setting at
        // that moment, before the store changes: switching sounds off plays a tap, switching them on plays none.
        val store = PhoneSettingsStore(dir.resolve("lnbphone-client.json")) { "alice" }
        val s = SettingsPlayer(store).apply { frame() }
        PhoneSounds.forcedEnabled = true
        for (x in listOf(40f, TOGGLE_X)) {
            val thrown = assertThrows(NullPointerException::class.java) { s.click(x, SOUNDS_Y) }
            assertTrue(thrown.stackTrace.any { it.className.endsWith(".PhoneSounds") }, "thrown while playing the tap")
            assertTrue(s.sounds.checked, "not flipped yet when the tap played (click at x $x)")
            assertTrue(store.current.sounds)
        }
        // Silent, the same clicks go through.
        PhoneSounds.forcedEnabled = false
        s.click(40f, SOUNDS_Y)
        assertFalse(store.current.sounds)
    }

    @Test
    fun `a toggle set after it is on a screen slides on the first frames, set before it stands still`() {
        val screen = Plain()
        val late = screen.add(Toggle().at(0f, 0f))
        late.checked = true
        val early = screen.add(Toggle().apply { checked = true }.at(0f, 40f))
        val p = ScreenPlayer(screen).apply { frame() }

        assertEquals(KNOB_OFF, p.knobX(late))
        assertEquals(KNOB_ON, p.knobX(early))
        p.tick(2)
        assertEquals(KNOB_ON, p.knobX(late))
        assertEquals(KNOB_ON, p.knobX(early))
    }

    @Test
    fun `a segmented control set after it is on a screen slides on the first frames, set before it stands still`() {
        val screen = Plain()
        val late = screen.add(SegmentedControl(listOf("a", "b")).at(12f, 0f))
        late.selected = 1
        val early = screen.add(SegmentedControl(listOf("a", "b")).apply { selected = 1 }.at(12f, 50f))
        val p = ScreenPlayer(screen).apply { frame() }

        assertEquals(PILL_CORNER, p.pillX(late))
        assertEquals(PILL_CENTRE, p.pillX(early))
        p.tick(3)
        assertEquals(PILL_CENTRE, p.pillX(late))
        assertEquals(PILL_CENTRE, p.pillX(early))
    }
}

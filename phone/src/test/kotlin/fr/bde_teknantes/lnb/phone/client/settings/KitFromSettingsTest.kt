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
 * The kit seen from its first real app: what the Settings screen relied on or once had to work around.
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
    fun `a row with a click handler of its own leaves its toggle a target of its own, by pointer and by Tab`() {
        val screen = Plain()
        var opened = 0
        val toggle = Toggle()
        val row = screen.add(ListRow("Wi-Fi") { opened++ }.apply { trailing = toggle }.at(0f, 0f))
        val p = ScreenPlayer(screen).apply { frame() }

        p.click(40f, 24f)
        assertEquals(1, opened)
        assertFalse(toggle.checked)
        p.click(TOGGLE_X, 24f)
        assertTrue(toggle.checked)
        assertEquals(1, opened)

        p.key(GLFW_KEY_TAB)
        assertSame(row, screen.focused)
        p.key(GLFW_KEY_SPACE)
        assertEquals(2, opened)
        p.key(GLFW_KEY_TAB)
        assertSame(toggle, screen.focused)
        p.key(GLFW_KEY_SPACE)
        assertFalse(toggle.checked)
        assertEquals(2, opened)
    }

    @Test
    fun `a settings row owns its toggle, hovered as one, one tap per click, one Tab stop`() {
        val store = PhoneSettingsStore(dir.resolve("lnbphone-client.json")) { "alice" }
        val s = SettingsPlayer(store).apply { frame() }
        s.frame(TOGGLE_X, SOUNDS_Y)
        assertSame(s.soundsRow, s.screen.hovered, "the pointer on the toggle hovers the row")
        s.click(TOGGLE_X, SOUNDS_Y)
        assertFalse(store.current.sounds)
        assertEquals(listOf(PhoneSounds.Sound.TAP), KitFakes.sounds)
        assertSame(s.soundsRow, s.screen.focused, "the click on the toggle focused the row")
        s.key(GLFW_KEY_TAB)
        assertSame(s.previewRow, s.screen.focused, "the toggle is no Tab stop of its own")
    }

    @Test
    fun `the tap follows the sound setting as the click leaves it`() {
        // The click acts first and taps after: switching sounds off is silent, switching them on is heard.
        val store = PhoneSettingsStore(dir.resolve("lnbphone-client.json")) { "alice" }
        val s = SettingsPlayer(store).apply { frame() }
        PhoneSounds.setting = { store.current.sounds }
        for (x in listOf(40f, TOGGLE_X)) {
            KitFakes.sounds.clear()
            s.click(x, SOUNDS_Y)
            assertFalse(store.current.sounds)
            assertEquals(emptyList<PhoneSounds.Sound>(), KitFakes.sounds, "switched off at x $x: no tap")
            s.click(x, SOUNDS_Y)
            assertTrue(store.current.sounds)
            assertEquals(listOf(PhoneSounds.Sound.TAP), KitFakes.sounds, "switched on at x $x: one tap")
        }
    }

    @Test
    fun `a toggle set after it is on a screen, or when the screen shows, stands still, and slides after the first frame`() {
        val toggles = ArrayList<Toggle>()
        val screen = object : KitScreen() {
            override fun onScreenShown() {
                toggles[2].checked = true
            }
        }
        val late = screen.add(Toggle().at(0f, 0f))
        late.checked = true
        val early = screen.add(Toggle().apply { checked = true }.at(0f, 40f))
        val shown = screen.add(Toggle().at(0f, 80f))
        toggles += listOf(late, early, shown)
        screen.onShown()
        val p = ScreenPlayer(screen).apply { frame() }
        for (t in toggles) assertEquals(KNOB_ON, p.knobX(t))

        // Once the player has seen it, a change slides.
        late.checked = false
        p.frame()
        assertTrue(p.knobX(late) > KNOB_OFF)
        p.tick(2)
        assertEquals(KNOB_OFF, p.knobX(late))
    }

    @Test
    fun `a segmented control set after it is on a screen stands still`() {
        val screen = Plain()
        val late = screen.add(SegmentedControl(listOf("a", "b")).at(12f, 0f))
        late.selected = 1
        val early = screen.add(SegmentedControl(listOf("a", "b")).apply { selected = 1 }.at(12f, 50f))
        val p = ScreenPlayer(screen).apply { frame() }

        assertEquals(PILL_CENTRE, p.pillX(late))
        assertEquals(PILL_CENTRE, p.pillX(early))
        late.selected = 0
        p.frame()
        assertTrue(p.pillX(late) > PILL_CORNER, "after the first frame, a change slides")
        p.tick(3)
        assertEquals(PILL_CORNER, p.pillX(late))
    }
}

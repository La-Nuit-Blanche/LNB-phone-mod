package fr.bde_teknantes.lnb.phone.client.settings

import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.phone.api.client.ui.Colors
import fr.bde_teknantes.lnb.phone.api.client.ui.KitFakes
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.CENTRE_X
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.CENTRE_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.CORNER_X
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.KNOB_OFF
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.KNOB_ON
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PILL_CENTRE
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PILL_CORNER
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.PREVIEW_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.SOUNDS_Y
import fr.bde_teknantes.lnb.phone.client.settings.SettingsPlayer.Companion.TOGGLE_X
import fr.bde_teknantes.lnb.phone.client.shell.Placement
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
import java.nio.file.Files
import java.nio.file.Path

/**
 * The real Settings screen (art direction 12, flows P-12 to P-14) played through the kit against a real settings
 * store in a temporary folder: what it shows, the mouse, the keyboard, and what reaches the file.
 */
class SettingsScreenSessionTest {
    @TempDir
    lateinit var dir: Path

    private val file get() = dir.resolve("lnbphone-client.json")

    private fun store(player: String = "alice") = PhoneSettingsStore(file) { player }

    /** What a fresh game would read from the file now. */
    private fun onDisk() = store().current

    private fun open(store: PhoneSettingsStore = store()) = SettingsPlayer(store).apply { frame() }

    @BeforeEach
    fun setUp() {
        KitFakes.install()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    // ---- What it shows ----

    @Test
    fun `the first frame shows the defaults already in place, and nothing moves after`() {
        val s = open()
        assertEquals(Triple(0, true, true), s.shown)
        assertEquals(PILL_CORNER, s.pillX(s.position))
        assertEquals(KNOB_ON, s.knobX(s.sounds))
        assertEquals(KNOB_ON, s.knobX(s.preview))
        assertEquals(Colors.ACCENT, s.trackColor(s.sounds))

        repeat(6) {
            s.tick()
            assertEquals(PILL_CORNER, s.pillX(s.position))
            assertEquals(KNOB_ON, s.knobX(s.sounds))
            assertEquals(KNOB_ON, s.knobX(s.preview))
        }
    }

    @Test
    fun `the first frame shows saved settings already in place, with no slide from the defaults`() {
        store().update { PhoneSettings(Placement.CENTRE, sounds = false, preview = false) }
        val s = open()
        assertEquals(Triple(1, false, false), s.shown)
        assertEquals(PILL_CENTRE, s.pillX(s.position))
        assertEquals(KNOB_OFF, s.knobX(s.sounds))
        assertEquals(KNOB_OFF, s.knobX(s.preview))
        assertEquals(Colors.STROKE, s.trackColor(s.sounds))
        assertEquals(Colors.STROKE, s.trackColor(s.preview))

        repeat(6) {
            s.tick()
            assertEquals(PILL_CENTRE, s.pillX(s.position))
            assertEquals(KNOB_OFF, s.knobX(s.sounds))
            assertEquals(KNOB_OFF, s.knobX(s.preview))
        }
    }

    @Test
    fun `the controls sit where section 12 puts them`() {
        val s = open()
        fun box(w: fr.bde_teknantes.lnb.phone.api.client.ui.Widget) = listOf(w.toViewportX(0f), w.toViewportY(0f), w.width, w.height)
        assertEquals(listOf(12f, 100f, 232f, 32f), box(s.position))
        assertEquals(listOf(0f, 145f, 256f, 48f), box(s.soundsRow))
        assertEquals(listOf(0f, 194f, 256f, 48f), box(s.previewRow))
        // The toggles end at x 244, centred in their rows: P-13's and P-14's cursor (226, 169) and (226, 218) is on them.
        assertEquals(listOf(208f, 159f, 36f, 20f), box(s.sounds))
        assertEquals(listOf(208f, 208f, 36f, 20f), box(s.preview))
        val section = s.texts.single { it.style == TextStyle.CAPTION_STRONG }
        assertEquals(12f to 56f, section.x to section.y)
        assertEquals(Colors.TEXT_2, section.color)
        val label = s.texts.single { it.text == "settings.lnbphone.position" }
        assertEquals(Triple(12f, 76f, TextStyle.BODY_STRONG), Triple(label.x, label.y, label.style))
    }

    @Test
    fun `opening and leaving the screen open writes nothing`() {
        val s = open()
        s.tick(40)
        s.frame(CENTRE_X, CENTRE_Y)
        s.tick(10)
        assertFalse(Files.exists(file))
    }

    // ---- The mouse ----

    @Test
    fun `clicking Centre saves at once, slides the pill there, and a second screen opens on Centre`() {
        val store = store()
        val heard = ArrayList<PhoneSettings>()
        store.addListener { _, new -> heard += new }
        val s = open(store)

        s.click(CENTRE_X, CENTRE_Y)
        // Written before any tick: a game quitting now would keep it.
        assertEquals(PhoneSettings(Placement.CENTRE), onDisk())
        assertEquals(listOf(PhoneSettings(Placement.CENTRE)), heard)
        assertEquals(1, s.position.selected)
        // The pill slides over 150 ms: under way after one tick, there after three, and still there later.
        s.tick()
        val mid = s.pillX(s.position)
        assertTrue(mid > PILL_CORNER && mid < PILL_CENTRE, "mid-slide at $mid")
        s.tick(2)
        assertEquals(PILL_CENTRE, s.pillX(s.position))
        s.tick(10)
        assertEquals(PILL_CENTRE, s.pillX(s.position))

        val second = open(store)
        assertEquals(1, second.position.selected)
        assertEquals(PILL_CENTRE, second.pillX(second.position))
        val fresh = open(store())
        assertEquals(1, fresh.position.selected)

        // Centre again: already chosen, nothing written. Corner: back, written.
        s.click(CENTRE_X, CENTRE_Y)
        assertEquals(1, heard.size)
        s.click(CORNER_X, CENTRE_Y)
        assertEquals(PhoneSettings(), onDisk())
        assertEquals(2, heard.size)
        assertEquals(0, s.position.selected)
    }

    @Test
    fun `a click anywhere across a row switches its toggle exactly once`() {
        val store = store()
        var changes = 0
        store.addListener { _, _ -> changes++ }
        val s = open(store)

        var expected = true
        for (x in listOf(0f, 12f, 120f, 200f, 207f, TOGGLE_X, 245f, 255f)) {
            for (y in listOf(146f, SOUNDS_Y, 192f)) {
                s.click(x, y)
                expected = !expected
                assertEquals(expected, s.sounds.checked, "click at ($x, $y)")
                assertEquals(expected, store.current.sounds, "click at ($x, $y)")
                assertEquals(expected, onDisk().sounds, "click at ($x, $y)")
                // The next tick reads the store back and agrees: no flip back.
                s.tick()
                assertEquals(expected, s.sounds.checked, "after the tick, click at ($x, $y)")
            }
        }
        assertEquals(24, changes)
        assertTrue(s.preview.checked)
        assertEquals(0, s.position.selected)
    }

    @Test
    fun `the preview row and its toggle switch the preview, and only the preview`() {
        val s = open()
        s.click(40f, PREVIEW_Y)
        assertEquals(PhoneSettings(preview = false), onDisk())
        s.click(TOGGLE_X, PREVIEW_Y)
        assertEquals(PhoneSettings(), onDisk())
        s.click(TOGGLE_X, SOUNDS_Y)
        assertEquals(PhoneSettings(sounds = false), onDisk())
        assertEquals(Triple(0, false, true), s.shown)
    }

    @Test
    fun `clicking a toggle slides its knob in 100 ms, and the tick reading the store back doesn't restart it`() {
        val s = open()
        s.click(TOGGLE_X, SOUNDS_Y)
        assertEquals(KNOB_ON, s.knobX(s.sounds))
        s.tick()
        val mid = s.knobX(s.sounds)
        assertTrue(mid > KNOB_OFF && mid < KNOB_ON, "mid-slide at $mid")
        s.tick()
        assertEquals(KNOB_OFF, s.knobX(s.sounds))
        assertEquals(Colors.STROKE_HOVER, s.trackColor(s.sounds), "off and hovered, as P-13 step 2")
    }

    @Test
    fun `the header, labels and dividers take no clicks`() {
        val store = store()
        var changes = 0
        store.addListener { _, _ -> changes++ }
        val s = open(store)
        for ((x, y) in listOf(128f to 20f, 30f to 60f, 30f to 84f, 128f to 144f, 128f to 193f, 128f to 242f, 128f to 300f)) {
            s.click(x, y)
        }
        assertEquals(0, changes)
        assertFalse(Files.exists(file))
    }

    // ---- The keyboard ----

    @Test
    fun `Tab goes Coin, Centre, Sounds, Preview and round, Shift+Tab back`() {
        val s = open()
        val order = listOf(s.corner, s.centre, s.soundsRow, s.previewRow)
        for (expected in order + order.first()) {
            s.key(GLFW_KEY_TAB)
            assertSame(expected, s.screen.focused)
        }
        s.key(GLFW_KEY_TAB, GLFW_MOD_SHIFT)
        assertSame(s.previewRow, s.screen.focused)
        s.key(GLFW_KEY_TAB, GLFW_MOD_SHIFT)
        assertSame(s.soundsRow, s.screen.focused)
        // The row is the stop; its toggle shows the ring with it.
        s.frame()
        assertTrue(s.soundsRow.showsFocusRing)
        assertTrue(s.sounds.showsFocusRing)
        assertFalse(s.sounds.focusable)
    }

    @Test
    fun `Enter and Space work each control once, and every change is on disk at once`() {
        val store = store()
        var changes = 0
        store.addListener { _, _ -> changes++ }
        val s = open(store)

        s.key(GLFW_KEY_TAB)
        s.key(GLFW_KEY_TAB)
        assertTrue(s.key(GLFW_KEY_ENTER))
        assertEquals(PhoneSettings(Placement.CENTRE), onDisk())
        assertTrue(s.key(GLFW_KEY_SPACE))
        assertEquals(1, changes, "the chosen segment again changes nothing")

        s.key(GLFW_KEY_TAB)
        assertTrue(s.key(GLFW_KEY_SPACE))
        assertEquals(PhoneSettings(Placement.CENTRE, sounds = false), onDisk())
        s.tick()
        assertTrue(s.key(GLFW_KEY_ENTER))
        assertEquals(PhoneSettings(Placement.CENTRE), onDisk())

        s.key(GLFW_KEY_TAB)
        assertTrue(s.key(GLFW_KEY_ENTER))
        assertEquals(PhoneSettings(Placement.CENTRE, preview = false), onDisk())

        s.key(GLFW_KEY_TAB, GLFW_MOD_SHIFT)
        s.key(GLFW_KEY_TAB, GLFW_MOD_SHIFT)
        s.key(GLFW_KEY_TAB, GLFW_MOD_SHIFT)
        assertSame(s.corner, s.screen.focused)
        s.key(GLFW_KEY_SPACE)
        assertEquals(PhoneSettings(preview = false), onDisk())
        assertEquals(5, changes)
        s.tick()
        assertEquals(Triple(0, true, false), s.shown)
        assertEquals(Triple(0, true, false), open(store()).shown)
    }

    @Test
    fun `Left and Right move the focus between the segments, without choosing`() {
        val s = open()
        s.key(GLFW_KEY_TAB)
        assertSame(s.corner, s.screen.focused)
        assertTrue(s.key(GLFW_KEY_RIGHT))
        assertSame(s.centre, s.screen.focused)
        assertTrue(s.centre.showsFocusRing)
        assertTrue(s.key(GLFW_KEY_RIGHT), "at the end, the key is kept and nothing moves")
        assertSame(s.centre, s.screen.focused)
        assertTrue(s.key(GLFW_KEY_LEFT))
        assertSame(s.corner, s.screen.focused)
        assertFalse(s.key(GLFW_KEY_DOWN), "Down is left to the phone and the game")
        assertEquals(0, s.position.selected)
        assertFalse(Files.exists(file), "moving chooses nothing")
        s.key(GLFW_KEY_RIGHT)
        s.key(GLFW_KEY_SPACE)
        assertEquals(1, s.position.selected)
    }

    @Test
    fun `a click moves the focus without rings`() {
        val s = open()
        s.key(GLFW_KEY_TAB)
        s.key(GLFW_KEY_TAB)
        s.key(GLFW_KEY_TAB)
        assertSame(s.soundsRow, s.screen.focused)
        assertTrue(s.soundsRow.showsFocusRing)
        s.click(TOGGLE_X, PREVIEW_Y)
        // A click on a row's toggle lands on the row, which owns it: the row takes the focus, from the pointer.
        assertSame(s.previewRow, s.screen.focused)
        for (w in listOf(s.soundsRow, s.sounds, s.previewRow, s.preview)) assertFalse(w.showsFocusRing, "$w")
    }

    // ---- Strings ----

    @Test
    fun `every text on the screen is a key of section 13 present in both language files`() {
        // No language is loaded in tests, so the screen draws its keys: each must exist in en_us and fr_fr. A key too
        // long for a segment is cut with an ellipsis, as a long label would be.
        val s = open()
        val expected = setOf(
            "app.lnbphone.settings",
            "settings.lnbphone.section.phone",
            "settings.lnbphone.position",
            "settings.lnbphone.position.corner",
            "settings.lnbphone.position.centre",
            "settings.lnbphone.sounds",
            "settings.lnbphone.preview",
        )
        val drawn = s.texts.map { it.text }
        assertEquals(expected.size, drawn.size, "$drawn")
        for (text in drawn) {
            assertTrue(text in expected || (text.endsWith("…") && expected.any { it.startsWith(text.dropLast(1)) }), text)
        }
        val shownKeys = expected.filter { key -> drawn.any { it == key || (it.endsWith("…") && key.startsWith(it.dropLast(1))) } }
        assertEquals(expected.size, shownKeys.size, "every key shows")
        val keysDrawn = expected
        for (lang in listOf("en_us", "fr_fr")) {
            val keys = javaClass.getResourceAsStream("/assets/lnbphone/lang/$lang.json")!!.reader(Charsets.UTF_8)
                .use { JsonParser.parseReader(it).asJsonObject.keySet() }
            assertTrue(keys.containsAll(keysDrawn), "$lang lacks ${keysDrawn - keys}")
        }
    }
}

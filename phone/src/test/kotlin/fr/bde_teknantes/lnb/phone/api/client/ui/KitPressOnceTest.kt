package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB

/** Held keys repeat; what acts does it once per press, what edits text keeps repeating. The tap comes after the act. */
class KitPressOnceTest {
    private lateinit var screen: Screen

    private class Screen : TestScreen() {
        var backs = 0

        override fun onBack(): Boolean {
            backs++
            return false
        }
    }

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = Screen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `holding Space on a toggle switches it once`() {
        val toggle = screen.add(Toggle().at(12f, 12f))
        val heard = ArrayList<Boolean>()
        toggle.onChange = { heard += it }
        screen.frame()
        screen.key(GLFW_KEY_TAB)
        screen.hold(GLFW_KEY_SPACE, repeats = 20)
        assertEquals(listOf(true), heard)
        assertEquals(listOf(PhoneSounds.Sound.TAP), KitFakes.sounds)
        // A new press is a new switch.
        screen.key(GLFW_KEY_SPACE)
        assertEquals(listOf(true, false), heard)
    }

    @Test
    fun `holding Enter on a button clicks it once`() {
        var clicks = 0
        screen.add(Button("Envoyer") { clicks++ }.at(12f, 12f))
        screen.frame()
        screen.key(GLFW_KEY_TAB)
        screen.hold(GLFW_KEY_ENTER, repeats = 20)
        assertEquals(1, clicks)
    }

    @Test
    fun `holding Enter in a field submits once, holding Backspace keeps erasing`() {
        var submits = 0
        val field = screen.add(TextField(TextState("bonjour", false)).apply { onSubmit = { submits++ } }.at(12f, 12f))
        screen.frame()
        screen.focus(field)
        screen.hold(GLFW_KEY_ENTER, repeats = 10)
        assertEquals(1, submits)
        screen.hold(GLFW_KEY_BACKSPACE, repeats = 3)
        assertEquals("bon", field.text)
    }

    @Test
    fun `holding Backspace goes back once`() {
        screen.frame()
        screen.hold(GLFW_KEY_BACKSPACE, repeats = 10)
        assertEquals(1, screen.backs)
    }

    @Test
    fun `the tap plays after the click has acted`() {
        val order = ArrayList<String>()
        PhoneSounds.output = { order += "sound $it" }
        screen.add(Button("OK") { order += "click" }.at(12f, 12f))
        screen.click(30f, 20f)
        assertEquals(listOf("click", "sound TAP"), order)
    }

    @Test
    fun `a busy button takes presses and does nothing`() {
        var clicks = 0
        val button = screen.add(Button("OK") { clicks++ }.apply { loading = true }.at(12f, 12f))
        screen.click(30f, 20f)
        button.click()
        assertEquals(0, clicks)
        assertEquals(emptyList<PhoneSounds.Sound>(), KitFakes.sounds)
        button.loading = false
        screen.click(30f, 20f)
        assertEquals(1, clicks)
    }

    @Test
    fun `a clickable of your own hears other keys in onKey`() {
        val keys = ArrayList<Int>()
        val spot = screen.add(object : Clickable() {
            override fun onKey(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
                keys += keyCode
                return true
            }
        }.at(0f, 0f, 40f, 40f))
        var clicks = 0
        spot.onClick = { clicks++ }
        screen.frame()
        screen.key(GLFW_KEY_TAB)
        assertTrue(screen.key(GLFW_KEY_LEFT))
        screen.key(GLFW_KEY_SPACE)
        assertEquals(listOf(GLFW_KEY_LEFT), keys)
        assertEquals(1, clicks)
    }
}

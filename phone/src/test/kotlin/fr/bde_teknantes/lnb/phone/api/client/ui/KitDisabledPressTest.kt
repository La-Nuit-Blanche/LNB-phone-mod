package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A press on a disabled widget leaves the focus where it was: a player typing who clicks a disabled send button keeps
 * typing in the field, and their next Backspace erases instead of going back a screen.
 */
class KitDisabledPressTest {
    private lateinit var screen: TestScreen
    private lateinit var area: TextArea
    private lateinit var send: Button

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
        area = screen.add(TextArea().at(12f, 50f, 232f, 112f))
        send = screen.add(Button("Send").apply { enabled = false }.at(12f, 200f, 96f, 32f))
        screen.frame()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `a press on a disabled button keeps the text area's focus, and the error sound says it can't`() {
        screen.click(30f, 60f)
        assertSame(area, screen.focused)
        KitFakes.sounds.clear()
        screen.click(40f, 210f)
        assertSame(area, screen.focused)
        assertTrue(screen.hasTextFocus)
        assertEquals(listOf(PhoneSounds.Sound.ERROR), KitFakes.sounds)
    }

    @Test
    fun `a press on a disabled toggle row keeps the focus too`() {
        val row = screen.add(LabeledToggle("Anonymous").apply { toggle.enabled = false }.at(12f, 250f, 232f, 32f))
        screen.frame()
        screen.click(30f, 60f)
        screen.click(100f, 260f)
        assertFalse(row.toggle.checked)
        assertSame(area, screen.focused)
    }

    @Test
    fun `a press on nothing, or on an enabled widget that takes no focus, still takes the focus away`() {
        screen.click(30f, 60f)
        screen.click(200f, 330f)
        assertNull(screen.focused)
        screen.click(30f, 60f)
        screen.add(Label("text").at(12f, 300f, 100f, 18f).apply { tooltip = "catches the pointer" })
        screen.frame()
        screen.click(20f, 305f)
        assertNull(screen.focused)
    }

    @Test
    fun `once enabled, a press on the button takes the focus as before`() {
        screen.click(30f, 60f)
        send.enabled = true
        screen.frame()
        screen.click(40f, 210f)
        assertSame(send, screen.focused)
    }
}

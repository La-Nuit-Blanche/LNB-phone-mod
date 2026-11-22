package fr.bde_teknantes.lnb.phone.testing

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW

/** The phone's test support plays an app as the phone does. */
class PhoneTesterTest {
    private var tester: PhoneTester? = null

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    private fun play(factory: () -> KitScreen) = PhoneTester { factory() }.also { tester = it }

    class Notes : KitScreen() {
        init {
            add(Header("Notes").at(0f, 0f))
            add(ListRow("Courses") { phone.push(Note("Courses")) }.at(0f, 44f))
        }
    }

    class Note(val title: String) : KitScreen() {
        val draft = TextState("", false)

        init {
            add(Header(title).at(0f, 0f))
            add(TextField(draft).at(12f, 56f))
        }

        override fun onScreenShown() = focus(widgets[1])
    }

    @Test
    fun `navigation is the phone's, a row pushes, Backspace goes back, Esc puts the phone away`() {
        val t = play { Notes() }
        assertTrue(t.shows("Notes"))
        t.click(40f, 68f)
        assertEquals(listOf(PhoneSound.TAP), t.sounds)
        assertInstanceOf(Note::class.java, t.screen)
        assertTrue(t.shows("Courses"))
        assertEquals(2, t.screens.size)

        // The field has the focus: Backspace erases instead of going back.
        t.type("lait")
        t.holdKey(GLFW.GLFW_KEY_BACKSPACE, repeats = 3)
        assertEquals("", (t.screen as Note).draft.text)
        t.key(GLFW.GLFW_KEY_TAB)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        assertInstanceOf(Notes::class.java, t.screen)

        t.key(GLFW.GLFW_KEY_ESCAPE)
        assertFalse(t.isPhoneOpen)
        assertEquals(null, t.screen)
        t.openPhone()
        assertInstanceOf(Notes::class.java, t.screen, "the phone reopens where it was")
    }

    @Test
    fun `a held Enter on a button clicks it once`() {
        var clicks = 0
        val t = play { object : KitScreen() { init { add(Button("OK") { clicks++ }.at(12f, 12f)) } } }
        t.key(GLFW.GLFW_KEY_TAB)
        t.holdKey(GLFW.GLFW_KEY_ENTER, repeats = 15)
        assertEquals(1, clicks)
        assertEquals(listOf(PhoneSound.TAP), t.sounds)
    }

    @Test
    fun `sounds follow the pretended setting`() {
        val t = play { object : KitScreen() { init { add(Button("OK") {}.at(12f, 12f)) } } }
        t.soundsOn = false
        t.click(30f, 20f)
        assertEquals(emptyList<PhoneSound>(), t.sounds)
        t.soundsOn = true
        t.click(30f, 20f)
        assertEquals(listOf(PhoneSound.TAP), t.sounds)
    }

    @Test
    fun `the clipboard is the tester's`() {
        val t = play { Note("Courses") }
        t.type("pain")
        t.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL)
        t.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL)
        assertEquals("pain", t.clipboard)
    }

    @Test
    fun `an app that throws makes the call throw, with the cause`() {
        val t = play { object : KitScreen() { init { add(Button("Boum") { error("boum") }.at(12f, 12f)) } } }
        val thrown = assertThrows(IllegalStateException::class.java) { t.click(30f, 20f) }
        assertEquals("boum", thrown.cause?.message)
        assertFalse(t.isAppOpen)
    }

    @Test
    fun `an app opened at a target lands on the screens it gives`() {
        val app = PhoneApp(
            ResourceLocation.fromNamespaceAndPath("notes", "notes"),
            Component.literal("Notes"),
            AppIcon.Glyph(TileColor.TEAL, ResourceLocation.fromNamespaceAndPath("notes", "icon.png")),
            targetScreens = { target -> listOf(Notes(), Note(target)) },
        ) { Notes() }
        val t = PhoneTester(app, "Courses").also { tester = it }
        assertEquals("Courses", (t.screen as Note).title)
        t.key(GLFW.GLFW_KEY_TAB)
        t.key(GLFW.GLFW_KEY_BACKSPACE)
        assertInstanceOf(Notes::class.java, t.screen)
        assertSame(t.screens.single(), t.screen)
    }
}

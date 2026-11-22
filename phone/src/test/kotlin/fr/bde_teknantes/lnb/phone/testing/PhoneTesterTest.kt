package fr.bde_teknantes.lnb.phone.testing

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.api.client.ui.Button
import fr.bde_teknantes.lnb.phone.api.client.ui.Header
import fr.bde_teknantes.lnb.phone.api.client.ui.KitScreen
import fr.bde_teknantes.lnb.phone.api.client.ui.ListRow
import fr.bde_teknantes.lnb.phone.api.client.ui.PhoneSounds
import fr.bde_teknantes.lnb.phone.api.client.ui.TextArea
import fr.bde_teknantes.lnb.phone.api.client.ui.TextField
import fr.bde_teknantes.lnb.phone.api.client.ui.TextState
import fr.bde_teknantes.lnb.phone.api.client.ui.Widget
import fr.bde_teknantes.lnb.phone.api.client.ui.at
import fr.bde_teknantes.lnb.phone.client.ui.TextEnvironment
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

    // ---- Held keys across screens (a held key acts once, on the screen it was pressed on) ----

    /** A list whose row opens a note on Space or Enter, the note's field focused as it shows (Ping's conversation). */
    class Rows : KitScreen() {
        val note = Note("Courses")

        init {
            add(ListRow("Courses") { phone.push(note) }.at(0f, 44f))
        }
    }

    class Compose : KitScreen() {
        val draft = TextState.multiline()

        init {
            add(TextArea(draft).at(12f, 56f))
        }

        override fun onScreenShown() = focus(widgets[0])
    }

    @Test
    fun `holding Space on a row types nothing into the field of the screen it opens`() {
        val t = play { Rows() }
        val list = t.screen as Rows
        t.key(GLFW.GLFW_KEY_TAB)
        t.holdKey(GLFW.GLFW_KEY_SPACE, repeats = 20)
        assertSame(list.note, t.screen)
        assertEquals("", list.note.draft.text)
        // Released, the key is the new screen's: the next Space types.
        t.key(GLFW.GLFW_KEY_SPACE)
        assertEquals(" ", list.note.draft.text)
    }

    @Test
    fun `holding Enter on a button puts no line feeds in the text area it opens`() {
        lateinit var compose: Compose
        val t = play { object : KitScreen() { init { add(Button("Écrire") { compose = Compose(); phone.push(compose) }.at(12f, 12f)) } } }
        t.key(GLFW.GLFW_KEY_TAB)
        t.holdKey(GLFW.GLFW_KEY_ENTER, repeats = 20)
        assertEquals("", compose.draft.text)
    }

    /** A field focused as the screen shows, and a row that opens a screen over it. */
    class Under : KitScreen() {
        val draft = TextState.singleLine().apply { text = "lait" }

        init {
            add(TextField(draft).at(12f, 56f))
            add(ListRow("Plus") { phone.push(object : KitScreen() {}) }.at(0f, 120f))
        }

        override fun onScreenShown() = focus(widgets[0])
    }

    @Test
    fun `holding Backspace to go back erases nothing in the field underneath`() {
        val t = play { Under() }
        val under = t.screen as Under
        t.click(40f, 140f)
        assertTrue(t.screen !== under)
        t.holdKey(GLFW.GLFW_KEY_BACKSPACE, repeats = 10)
        assertSame(under, t.screen)
        assertEquals("lait", under.draft.text)
    }

    @Test
    fun `keys type their characters, with Shift for capitals`() {
        val t = play { Note("Courses") }
        val note = t.screen as Note
        t.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_SHIFT)
        t.key(GLFW.GLFW_KEY_SPACE)
        t.key(GLFW.GLFW_KEY_1)
        t.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL)
        t.type("b!é")
        assertEquals("A 1b!é", note.draft.text)
    }

    @Test
    fun `closing the tester ends the app, as leaving it does`() {
        val closed = ArrayList<String>()
        val t = play { object : KitScreen() { override fun onClosed() { closed += "closed" } } }
        t.close()
        assertEquals(listOf("closed"), closed)
    }

    class Leaky : KitScreen() {
        var leak = false

        init {
            add(object : Widget() {
                override fun render(canvas: AppCanvas) {
                    if (leak) canvas.pushClip(0f, 0f, 10f, 10f)
                }
            }.at(0f, 0f, 10f, 10f))
        }
    }

    @Test
    fun `a frame that leaves a clip pushed fails the call`() {
        val t = play { Leaky() }
        (t.screen as Leaky).leak = true
        assertThrows(IllegalStateException::class.java) { t.frame() }
    }

    @Test
    fun `opening a tester closes the one before, and one that fails to open leaves the game as it was`() {
        val closed = ArrayList<String>()
        val first = play { object : KitScreen() { override fun onClosed() { closed += "first" } } }
        val second = PhoneTester { Notes() }.also { tester = it }
        assertEquals(listOf("first"), closed)
        assertThrows(IllegalStateException::class.java) { first.frame() }
        second.close()
        assertThrows(IllegalStateException::class.java) { PhoneTester { error("no screen") } }
        assertSame(PhoneSounds.gameOutput, PhoneSounds.output)
        assertSame(TextEnvironment.Game, TextEnvironment.current)
    }
}

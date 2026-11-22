package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB

/** The kit's clickable widgets as a player uses them: toggles, rows, tabs, segments, buttons. */
class KitWidgetsTest {
    private lateinit var screen: TestScreen

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `a click anywhere on a row switches its toggle, once`() {
        val heard = ArrayList<Boolean>()
        val toggle = Toggle().apply { onChange = { heard += it } }
        screen.add(ListRow("Publier anonymement").apply {
            subtitle = "Nom masqué aux joueurs"
            trailing = toggle
        }.at(0f, 0f))
        screen.frame()
        screen.click(40f, 28f)
        assertTrue(toggle.checked)
        // On the toggle itself (right-aligned to x 244, centred in the 56 dp row).
        screen.advance(500f)
        screen.click(230f, 28f)
        assertFalse(toggle.checked)
        assertEquals(listOf(true, false), heard)
    }

    @Test
    fun `a disabled row disables its toggle, and neither switches`() {
        val toggle = Toggle()
        val row = screen.add(ListRow("Sons").apply { trailing = toggle }.at(0f, 0f))
        row.enabled = false
        assertFalse(toggle.isEnabled)
        screen.click(40f, 24f)
        screen.click(230f, 24f)
        assertFalse(toggle.checked)
        toggle.toggle()
        assertFalse(toggle.checked)
    }

    @Test
    fun `setting checked by code moves the knob without calling onChange`() {
        var calls = 0
        val toggle = screen.add(Toggle().apply { onChange = { calls++ } }.at(0f, 0f))
        toggle.checked = true
        assertEquals(0, calls)
        toggle.toggle()
        assertEquals(1, calls)
        assertFalse(toggle.checked)
    }

    @Test
    fun `tabs select on click, not again on the active tab`() {
        val picked = ArrayList<Int>()
        val tabs = screen.add(Tabs(listOf("Annonces", "Global")).apply { onSelect = { picked += it } }.at(0f, 0f))
        screen.frame()
        screen.click(192f, 22f)
        assertEquals(1, tabs.selected)
        screen.advance(500f)
        screen.click(192f, 22f)
        screen.click(64f, 22f)
        assertEquals(listOf(1, 0), picked)
    }

    @Test
    fun `setting the tab by code calls nothing and stays in range`() {
        val picked = ArrayList<Int>()
        val tabs = screen.add(Tabs(listOf("Annonces", "Global")).apply { onSelect = { picked += it } }.at(0f, 0f))
        tabs.selected = 5
        assertEquals(1, tabs.selected)
        tabs.selected = -1
        assertEquals(0, tabs.selected)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `a segmented control picks on click and from the keyboard, and not while disabled`() {
        val picked = ArrayList<Int>()
        val control = screen.add(SegmentedControl(listOf("Annonces", "Global")).apply { onSelect = { picked += it } }.at(12f, 316f))
        screen.frame()
        screen.click(186f, 332f)
        assertEquals(1, control.selected)
        screen.key(GLFW_KEY_TAB)
        screen.key(GLFW_KEY_ENTER)
        assertEquals(0, control.selected)
        control.enabled = false
        screen.click(186f, 332f)
        assertEquals(0, control.selected)
        assertEquals(listOf(1, 0), picked)
    }

    @Test
    fun `a loading button ignores clicks and Enter, and keeps its width`() {
        var clicks = 0
        val button = screen.add(Button("Publier") { clicks++ }.apply { fixedWidth = 88f }.atRight(244f, 6f))
        button.loading = true
        screen.click(200f, 22f)
        screen.focus(button)
        screen.key(GLFW_KEY_ENTER)
        assertEquals(0, clicks)
        assertEquals(88f, button.width)
        button.loading = false
        screen.click(200f, 22f)
        assertEquals(1, clicks)
    }

    @Test
    fun `a button without a fixed width is as wide as its label plus padding, at least 64 dp`() {
        val short = Button("OK")
        assertEquals(64f, short.preferredWidth)
        val long = Button("Écrire un post")
        assertEquals(14 * 6.5f + 32f, long.preferredWidth)
        val text = Button("Réessayer").apply { kind = Button.Kind.TEXT }
        assertEquals(9 * 6.5f + 16f, text.preferredWidth)
        // A new label resizes it, by the next frame.
        val b = screen.add(Button("OK").at(0f, 0f))
        b.label = "Se désabonner"
        screen.frame()
        assertEquals(13 * 6.5f + 32f, b.width)
    }

    @Test
    fun `a dialog's buttons say Cancel and OK in the player's language until told otherwise`() {
        val dialog = Dialog("Supprimer ce post ?")
        // No language is loaded in tests: the labels are the phone's keys, present in both language files.
        assertEquals("gui.lnbphone.dialog.cancel", dialog.cancelLabel)
        assertEquals("gui.lnbphone.dialog.confirm", dialog.confirmLabel)
        dialog.confirmLabel = "Supprimer"
        screen.showDialog(dialog)
        screen.frame()
        assertTrue(screen.canvas.drew("Supprimer"))
    }

    @Test
    fun `text states are made by kind`() {
        val line = TextState.singleLine(maxLength = 4)
        line.insert("ab\ncdef")
        assertEquals("ab c", line.text)
        val area = TextState.multiline()
        area.insert("ab\ncd")
        assertEquals("ab\ncd", area.text)
        assertEquals(TextState.DEFAULT_MAX_LENGTH, TextState.singleLine().maxLength)
    }

    @Test
    fun `a label inset lines up with the rows of a full-width list`() {
        val list = screen.add(ScrollList().at(0f, 0f, 256f, 200f))
        list.add(Label("Début").apply { inset = Spacing.SCREEN_PADDING; maxLines = 2 })
        list.add(Label("Fin").apply { inset = Spacing.SCREEN_PADDING; align = Label.Align.END })
        screen.frame()
        assertEquals(12f, screen.canvas.texts.single { it.text == "Début" }.x)
        assertEquals(256f - 12f - 3 * 6.5f, screen.canvas.texts.single { it.text == "Fin" }.x)
    }
}

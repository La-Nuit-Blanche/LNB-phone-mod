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
        val toggle = Toggle(onChange = { heard += it })
        screen.add(ListRow("Publier anonymement", subtitle = "Nom masqué aux joueurs", trailing = toggle).at(0f, 0f))
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
        val row = screen.add(ListRow("Sons", trailing = toggle).at(0f, 0f))
        row.enabled = false
        assertFalse(toggle.enabled)
        screen.click(40f, 24f)
        screen.click(230f, 24f)
        assertFalse(toggle.checked)
        toggle.toggle()
        assertFalse(toggle.checked)
    }

    @Test
    fun `setting checked by code moves the knob without calling onChange`() {
        var calls = 0
        val toggle = screen.add(Toggle(onChange = { calls++ }).at(0f, 0f))
        toggle.checked = true
        assertEquals(0, calls)
        toggle.toggle()
        assertEquals(1, calls)
        assertFalse(toggle.checked)
    }

    @Test
    fun `tabs select on click, not again on the active tab`() {
        val picked = ArrayList<Int>()
        val tabs = screen.add(Tabs(listOf("Annonces", "Global"), onSelect = { picked += it }).at(0f, 0f))
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
        val tabs = screen.add(Tabs(listOf("Annonces", "Global"), onSelect = { picked += it }).at(0f, 0f))
        tabs.selected = 5
        assertEquals(1, tabs.selected)
        tabs.selected = -1
        assertEquals(0, tabs.selected)
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `a segmented control picks on click and from the keyboard, and not while disabled`() {
        val picked = ArrayList<Int>()
        val control = screen.add(SegmentedControl(listOf("Annonces", "Global"), onSelect = { picked += it }).at(12f, 316f))
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
        val button = screen.add(Button("Publier", fixedWidth = 88f) { clicks++ }.atRight(244f, 6f))
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
        val text = Button("Réessayer", Button.Kind.TEXT)
        assertEquals(9 * 6.5f + 16f, text.preferredWidth)
        // A new label resizes it.
        val b = screen.add(Button("OK").at(0f, 0f))
        b.label = "Se désabonner"
        assertEquals(13 * 6.5f + 32f, b.width)
    }
}

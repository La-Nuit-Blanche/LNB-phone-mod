package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_R
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT

/** The behaviours the kit's review asked for, played on a real [KitScreen]. */
class KitReviewTest {
    private lateinit var screen: Screen

    private class Screen : TestScreen() {
        var backs = 0
        var handleBack = false

        override fun onBack(): Boolean {
            backs++
            return handleBack
        }
    }

    private class Spot : Clickable() {
        var clicks = 0

        init {
            onClick = { clicks++ }
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
    fun `backspace goes to onBack, and does nothing under a menu or dialog`() {
        val anchor = screen.add(Spot().at(100f, 100f, 40f, 40f))
        screen.frame()
        assertFalse(screen.key(GLFW_KEY_BACKSPACE))
        assertEquals(1, screen.backs)
        screen.handleBack = true
        assertTrue(screen.key(GLFW_KEY_BACKSPACE))
        screen.showMenu(anchor, listOf(MenuItem("A") {}))
        assertTrue(screen.key(GLFW_KEY_BACKSPACE))
        assertEquals(2, screen.backs, "under a menu, Backspace is kept and goes nowhere")
        screen.closeMenu()
        screen.showDialog(Dialog("T").apply { onConfirm = {} })
        screen.frame()
        assertTrue(screen.key(GLFW_KEY_BACKSPACE))
        assertEquals(2, screen.backs)
    }

    @Test
    fun `a field keeps letters, so the screen's shortcuts don't fire while typing`() {
        val field = screen.add(TextField().at(12f, 12f))
        screen.frame()
        screen.click(50f, 30f)
        assertSame(field, screen.focused)
        assertTrue(screen.key(GLFW_KEY_R))
        assertEquals(0, screen.keysToScreen)
    }

    @Test
    fun `removing a widget from a scroll callback during drawing is safe`() {
        val list = screen.add(ScrollList().at(0f, 0f, 256f, 100f))
        val pill = screen.add(Spot().at(0f, 0f, 50f, 20f))
        repeat(10) { list.add(Spacer(40f)) }
        list.onScroll = { if (it == 0f) screen.remove(pill) }
        screen.frame()
        list.scrollTo(200f)
        list.scrollTo(0f, animate = true)
        screen.advance(300f)
        assertNull(pill.parent)
    }

    @Test
    fun `a kept scroll position survives a short first page and loading rows`() {
        val state = ScrollState(500f)
        val list = screen.add(ScrollList(state).at(0f, 0f, 256f, 100f))
        repeat(3) { list.add(SkeletonPost()) }
        screen.frame()
        assertEquals(500f, state.offset, "skeletons don't overwrite the kept position")
        list.removeAll()
        repeat(20) { list.add(Spacer(50f)) }
        screen.frame()
        assertEquals(500f, list.offset)
    }

    @Test
    fun `row positions are known as soon as rows are added`() {
        val list = screen.add(ScrollList().at(0f, 0f, 256f, 100f))
        list.add(Spacer(30f))
        val second = list.add(Spacer(20f))
        assertEquals(30f, second.y)
    }

    @Test
    fun `a right press doesn't take the left button's press away`() {
        val spot = screen.add(Spot().at(0f, 0f, 100f, 100f))
        screen.press(50f, 50f)
        screen.mousePressed(50f, 50f, GLFW_MOUSE_BUTTON_RIGHT)
        screen.release(50f, 50f)
        assertEquals(1, spot.clicks)
    }

    @Test
    fun `a widget hidden while held gets no click`() {
        val spot = screen.add(Spot().at(0f, 0f, 100f, 100f))
        screen.press(50f, 50f)
        spot.visible = false
        screen.release(50f, 50f)
        assertEquals(0, spot.clicks)
    }

    @Test
    fun `a release over a row scrolled out of its list is no click`() {
        val list = screen.add(ScrollList().at(0f, 0f, 256f, 100f))
        val row = list.add(ListRow("Ligne") {})
        var clicks = 0
        row.onClick = { clicks++ }
        repeat(10) { list.add(Spacer(48f)) }
        screen.frame()
        screen.press(50f, 20f)
        list.scrollTo(100f)
        screen.release(50f, 20f)
        assertEquals(0, clicks)
    }

    @Test
    fun `a widget can't be added inside itself`() {
        val outer = Spot()
        val inner = outer.add(Spot())
        assertThrows(IllegalArgumentException::class.java) { inner.add(outer) }
    }

    @Test
    fun `disabling a container disables what is inside`() {
        val outer = screen.add(Clickable().at(0f, 0f, 200f, 200f))
        val inner = outer.add(Spot().at(10f, 10f, 50f, 50f))
        outer.enabled = false
        assertFalse(inner.isEnabled)
        screen.click(20f, 20f)
        assertEquals(0, inner.clicks)
    }

    @Test
    fun `tab with a menu open goes round the menu's items`() {
        val anchor = screen.add(Spot().at(100f, 100f, 40f, 40f))
        screen.add(Spot().at(0f, 0f, 40f, 40f))
        screen.frame()
        screen.showMenu(anchor, listOf(MenuItem("A") {}, MenuItem("B") {}))
        screen.frame()
        screen.key(GLFW_KEY_TAB)
        val first = screen.focused
        screen.key(GLFW_KEY_TAB)
        screen.key(GLFW_KEY_TAB)
        assertSame(first, screen.focused)
        assertTrue(first !is Spot)
    }

    @Test
    fun `tab onto a row out of view scrolls it in`() {
        val list = screen.add(ScrollList().at(0f, 0f, 256f, 100f))
        repeat(5) { list.add(Spacer(48f)) }
        val row = list.add(ListRow("Loin") {})
        screen.frame()
        screen.key(GLFW_KEY_TAB)
        assertSame(row, screen.focused)
        assertTrue(list.offset > 0f)
    }

    @Test
    fun `a toggle row is one stop and switches from anywhere`() {
        val row = screen.add(LabeledToggle("Publier anonymement").apply { helper = "Nom masqué aux joueurs" }.at(12f, 0f))
        val heard = ArrayList<Boolean>()
        row.toggle.onChange = { heard += it }
        screen.frame()
        assertEquals(44f, row.height)
        screen.click(30f, 10f)
        assertEquals(listOf(true), heard)
        screen.key(GLFW_KEY_TAB)
        assertSame(row, screen.focused)
    }

    @Test
    fun `onChange hears changes made by code by the next frame`() {
        val state = TextState()
        val area = screen.add(TextArea(state).at(12f, 12f))
        var heard = 0
        area.onChange = { heard++ }
        screen.frame()
        state.text = "Brouillon"
        assertEquals(0, heard)
        screen.frame()
        assertEquals(1, heard)
        screen.frame()
        assertEquals(1, heard)
    }

    @Test
    fun `floating widgets stay above the content, whenever it is added`() {
        val fab = screen.addFloating(Spot().at(0f, 0f, 100f, 100f))
        val content = screen.add(Spot().at(0f, 0f, 100f, 100f))
        screen.click(50f, 50f)
        assertEquals(1, fab.clicks)
        assertEquals(0, content.clicks)
    }

    @Test
    fun `a header keeps a growing trailing button inside its margin`() {
        val button = Button("OK")
        screen.add(Header("Titre").apply { trailing = button }.at(0f, 0f))
        screen.frame()
        button.label = "Un libellé bien plus long"
        screen.frame()
        assertEquals(244f, button.x + button.width)
    }

    @Test
    fun `two clicks a few dp apart select a word`() {
        val field = screen.add(TextField(TextState("bonjour monde", false)).at(0f, 0f))
        screen.frame()
        screen.click(30f, 20f)
        screen.click(32f, 21f)
        assertEquals("bonjour", field.state.selectedText)
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT

/** How a [KitScreen] routes the pointer and the keyboard to its widgets. */
class KitScreenRoutingTest {
    private lateinit var screen: TestScreen

    /** A clickable rectangle that counts its clicks and focus changes. */
    private class Spot : Clickable() {
        var clicks = 0
        val focusEvents = ArrayList<Boolean>()

        init {
            onClick = { clicks++ }
        }

        override fun focusChanged(focused: Boolean) {
            focusEvents += focused
        }
    }

    private fun spot(x: Float, y: Float, w: Float = 100f, h: Float = 40f) = screen.add(Spot().at(x, y, w, h))

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Nested
    inner class Pointer {
        @Test
        fun `where widgets overlap, the one added last gets the press`() {
            val below = spot(0f, 0f, 100f, 100f)
            val above = spot(50f, 50f, 100f, 100f)
            screen.click(75f, 75f)
            assertEquals(0, below.clicks)
            assertEquals(1, above.clicks)
            screen.click(25f, 25f)
            assertEquals(1, below.clicks)
        }

        @Test
        fun `an invisible widget on top lets the press through, a disabled one swallows it`() {
            val below = spot(0f, 0f)
            val hidden = spot(0f, 0f).apply { visible = false }
            screen.click(10f, 10f)
            assertEquals(1, below.clicks)
            assertEquals(0, hidden.clicks)
            val disabled = spot(0f, 0f).apply { enabled = false }
            screen.click(10f, 10f)
            assertEquals(1, below.clicks)
            assertEquals(0, disabled.clicks)
        }

        @Test
        fun `the left and top edges are in, the right and bottom ones out`() {
            val s = spot(10f, 10f, 20f, 20f)
            screen.click(10f, 10f)
            screen.click(29.9f, 29.9f)
            assertEquals(2, s.clicks)
            screen.click(30f, 15f)
            screen.click(15f, 30f)
            assertEquals(2, s.clicks)
        }

        @Test
        fun `outside the viewport nothing is hit`() {
            val s = spot(0f, 0f, 256f, 370f)
            screen.click(-1f, 10f)
            screen.click(10f, 370f)
            screen.click(256f, 10f)
            assertEquals(0, s.clicks)
        }

        @Test
        fun `a press on one widget and a release on another clicks neither`() {
            val a = spot(0f, 0f)
            val b = spot(0f, 100f)
            screen.press(10f, 10f)
            screen.drag(10f, 110f)
            screen.release(10f, 110f)
            assertEquals(0, a.clicks)
            assertEquals(0, b.clicks)
        }

        @Test
        fun `dragging off and back on before releasing still clicks`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            screen.drag(10f, 200f)
            screen.drag(20f, 20f)
            screen.release(20f, 20f)
            assertEquals(1, a.clicks)
        }

        @Test
        fun `a right click clicks nothing and moves no focus`() {
            val a = spot(0f, 0f)
            val b = spot(0f, 100f)
            screen.focus(b)
            screen.press(10f, 10f, GLFW_MOUSE_BUTTON_RIGHT)
            screen.release(10f, 10f, GLFW_MOUSE_BUTTON_RIGHT)
            assertEquals(0, a.clicks)
            assertSame(b, screen.focused)
        }

        @Test
        fun `a release of another button doesn't end the press`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            assertFalse(screen.release(10f, 10f, GLFW_MOUSE_BUTTON_RIGHT))
            assertTrue(a.isPressed)
            screen.release(10f, 10f)
            assertEquals(1, a.clicks)
        }

        @Test
        fun `the pressed look lasts at least 100 ms after a quick click, then hover comes back`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            assertEquals(Interaction.PRESSED, a.interaction)
            screen.release(10f, 10f)
            assertEquals(Interaction.PRESSED, a.interaction)
            screen.advance(50f)
            assertEquals(Interaction.PRESSED, a.interaction)
            screen.advance(50f)
            assertEquals(Interaction.HOVER, a.interaction)
        }

        @Test
        fun `a long press releases into hover at once`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            screen.advance(300f)
            assertEquals(Interaction.PRESSED, a.interaction)
            screen.release(10f, 10f)
            assertEquals(Interaction.HOVER, a.interaction)
        }

        @Test
        fun `leaving during a press drops the pressed look, and nothing else lights up meanwhile`() {
            val a = spot(0f, 0f)
            val b = spot(0f, 100f)
            screen.press(10f, 10f)
            screen.drag(10f, 110f)
            assertEquals(Interaction.NORMAL, a.interaction)
            assertSame(b, screen.hovered)
            assertEquals(Interaction.NORMAL, b.interaction)
            screen.drag(10f, 10f)
            assertEquals(Interaction.PRESSED, a.interaction)
            screen.release(10f, 10f)
        }

        @Test
        fun `a press dragged off and let go shows no pressed look afterwards`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            screen.drag(10f, 200f)
            screen.release(10f, 200f)
            screen.moveTo(10f, 10f)
            assertEquals(Interaction.HOVER, a.interaction)
        }

        @Test
        fun `hover follows the pointer from frame to frame`() {
            val a = spot(0f, 0f)
            assertNull(screen.hovered)
            screen.moveTo(10f, 10f)
            assertSame(a, screen.hovered)
            assertEquals(Interaction.HOVER, a.interaction)
            screen.moveTo(10f, 300f)
            assertNull(screen.hovered)
            assertEquals(Interaction.NORMAL, a.interaction)
        }

        @Test
        fun `a click on a child that ignores it reaches its parent`() {
            val parent = spot(0f, 0f, 200f, 100f)
            val label = parent.add(Label("Titre").at(10f, 10f))
            screen.click(15f, 15f)
            assertEquals(1, parent.clicks)
            assertSame(parent, label.parent)
        }

        @Test
        fun `a widget removed during its own click is forgotten cleanly`() {
            val a = spot(0f, 0f)
            a.onClick = { screen.remove(a) }
            screen.click(10f, 10f)
            assertFalse(a in screen.widgets)
            assertNull(screen.hovered)
            assertNull(screen.capturing)
        }
    }

    @Nested
    inner class Removal {
        @Test
        fun `a widget removed while pressed gets no release and no click`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            screen.remove(a)
            assertNull(screen.capturing)
            assertFalse(screen.release(10f, 10f))
            assertEquals(0, a.clicks)
            assertFalse(a.isPressed)
        }

        @Test
        fun `a widget removed while hovered is no longer hovered`() {
            val a = spot(0f, 0f)
            screen.moveTo(10f, 10f)
            screen.remove(a)
            assertNull(screen.hovered)
        }

        @Test
        fun `a widget removed while focused loses the focus and hears it`() {
            val a = spot(0f, 0f)
            screen.focus(a)
            screen.remove(a)
            assertNull(screen.focused)
            assertEquals(listOf(true, false), a.focusEvents)
        }

        @Test
        fun `removing a parent takes the focus from a child inside it`() {
            val box = screen.add(object : Widget() {}.at(0f, 0f, 200f, 200f))
            val field = box.add(TextField().at(0f, 0f))
            screen.focus(field)
            assertTrue(screen.hasTextFocus)
            screen.remove(box)
            assertNull(screen.focused)
            assertFalse(screen.hasTextFocus)
        }

        @Test
        fun `a removed widget can't take the focus`() {
            val a = spot(0f, 0f)
            screen.remove(a)
            a.requestFocus()
            screen.focus(a)
            assertNull(screen.focused)
        }

        @Test
        fun `hiding the screen drops a press in progress`() {
            val a = spot(0f, 0f)
            screen.press(10f, 10f)
            screen.onHidden()
            assertNull(screen.capturing)
            assertFalse(screen.release(10f, 10f))
            assertEquals(0, a.clicks)
        }
    }

    @Nested
    inner class Focus {
        @Test
        fun `tab goes through focusable widgets in the order they were added, and wraps`() {
            val a = spot(0f, 0f)
            val disabled = spot(0f, 50f).apply { enabled = false }
            val hidden = spot(0f, 100f).apply { visible = false }
            val box = screen.add(object : Widget() {}.at(0f, 150f, 200f, 100f))
            val inHiddenBox = box.add(Spot().at(0f, 0f, 50f, 50f))
            box.visible = false
            val label = screen.add(Label("pas focusable").at(0f, 260f))
            val e = spot(0f, 300f)

            val order = (1..4).map { screen.key(GLFW_KEY_TAB); screen.focused }
            assertEquals(listOf<Widget?>(a, e, a, e), order)
            assertTrue(listOf(disabled, hidden, inHiddenBox, label).none { it === screen.focused })
        }

        @Test
        fun `shift+tab goes backwards, starting from the last`() {
            val a = spot(0f, 0f)
            val b = spot(0f, 50f)
            val c = spot(0f, 100f)
            val order = (1..4).map { screen.key(GLFW_KEY_TAB, SHIFT); screen.focused }
            assertEquals(listOf<Widget?>(c, b, a, c), order)
        }

        @Test
        fun `children come right after their parent in the order`() {
            val row = screen.add(ListRow("Publier anonymement", trailing = Toggle()).at(0f, 0f))
            val after = spot(0f, 100f)
            val order = (1..3).map { screen.key(GLFW_KEY_TAB); screen.focused }
            // Both the row and its toggle take the focus: two stops for one setting.
            assertEquals(listOf(row, row.trailing, after), order)
        }

        @Test
        fun `tab with nothing focusable focuses nothing and keeps the key`() {
            screen.add(Label("texte").at(0f, 0f))
            assertTrue(screen.key(GLFW_KEY_TAB))
            assertNull(screen.focused)
        }

        @Test
        fun `focus from tab shows the ring, focus from a click doesn't`() {
            val a = spot(0f, 0f)
            screen.key(GLFW_KEY_TAB)
            assertTrue(a.showsFocusRing)
            screen.click(10f, 10f)
            assertSame(a, screen.focused)
            assertFalse(a.showsFocusRing)
        }

        @Test
        fun `enter and space click the focused widget`() {
            val a = spot(0f, 0f)
            screen.key(GLFW_KEY_TAB)
            screen.key(GLFW_KEY_ENTER)
            screen.key(GLFW_KEY_SPACE)
            assertEquals(2, a.clicks)
            assertEquals(Interaction.PRESSED, a.interaction)
            screen.advance(100f)
            assertEquals(Interaction.NORMAL, a.interaction)
        }

        @Test
        fun `a focused widget that is hidden no longer gets keys, and tab starts over`() {
            val a = spot(0f, 0f)
            val b = spot(0f, 50f)
            screen.focus(b)
            b.visible = false
            screen.key(GLFW_KEY_ENTER)
            assertEquals(0, b.clicks)
            screen.key(GLFW_KEY_TAB)
            assertSame(a, screen.focused)
        }

        @Test
        fun `keys nobody uses reach the screen`() {
            spot(0f, 0f)
            screen.key(GLFW_KEY_TAB)
            screen.key(org.lwjgl.glfw.GLFW.GLFW_KEY_F)
            assertEquals(1, screen.keysToScreen)
        }
    }

    @Nested
    inner class TextFocus {
        @Test
        fun `true exactly while an editable field has the focus`() {
            val field = screen.add(TextField().at(0f, 0f))
            val button = spot(0f, 100f)
            assertFalse(screen.hasTextFocus)
            screen.click(50f, 20f)
            assertTrue(screen.hasTextFocus)
            screen.key(GLFW_KEY_TAB)
            assertSame(button, screen.focused)
            assertFalse(screen.hasTextFocus)
            screen.key(GLFW_KEY_TAB)
            assertSame(field, screen.focused)
            assertTrue(screen.hasTextFocus)
        }

        @Test
        fun `false once the field is disabled, removed or hidden`() {
            val disabled = screen.add(TextField().at(0f, 0f))
            screen.focus(disabled)
            disabled.enabled = false
            assertFalse(screen.hasTextFocus)

            val removed = screen.add(TextField().at(0f, 50f))
            screen.focus(removed)
            screen.remove(removed)
            assertFalse(screen.hasTextFocus)

            val hidden = screen.add(TextField().at(0f, 100f))
            screen.focus(hidden)
            hidden.visible = false
            assertFalse(screen.hasTextFocus)
            hidden.visible = true
            // Shown again with the focus it kept.
            assertTrue(screen.hasTextFocus)
        }

        @Test
        fun `a click on empty space ends it`() {
            screen.add(TextField().at(0f, 0f))
            screen.click(50f, 20f)
            screen.click(50f, 300f)
            assertFalse(screen.hasTextFocus)
        }

        @Test
        fun `a focus listener hears the field gain and lose the focus`() {
            val field = screen.add(TextArea().at(0f, 0f))
            val heard = ArrayList<Boolean>()
            field.onFocusChange = { heard += it }
            screen.click(50f, 20f)
            screen.click(50f, 20f)
            screen.click(50f, 300f)
            assertEquals(listOf(true, false), heard)
        }
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB

/** The order in which an app sets a widget up doesn't change what the player gets. */
class KitOrderTest {
    private lateinit var screen: TestScreen

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    @Test
    fun `a widget placed without a size follows its preferred size when its settings change`() {
        val row = screen.add(ListRow("Alex").at(0f, 44f))
        val label = screen.add(Label("Titre").at(12f, 120f))
        val icon = screen.add(IconButton(Icon.MORE).at(200f, 200f))
        row.subtitle = "@Alex_Craft"
        label.style = TextStyle.TITLE_L
        icon.size = 28f
        screen.frame()
        assertEquals(56f, row.height)
        assertEquals(TextStyle.TITLE_L.lineHeight, label.height)
        assertEquals(TextMetrics.measure("Titre", TextStyle.TITLE_L), label.width)
        assertEquals(28f, icon.width)
        assertEquals(200f, icon.x, "it keeps its left edge")
    }

    @Test
    fun `placed by its right edge, a widget keeps it as its label changes`() {
        val button = screen.add(Button("OK").atRight(244f, 100f))
        button.label = "Un libellé bien plus long"
        screen.frame()
        assertEquals(244f, button.x + button.width)
        assertEquals(button.preferredWidth, button.width)
    }

    @Test
    fun `a size given at placement is kept`() {
        val button = screen.add(Button("OK").at(12f, 100f, 100f, 32f))
        button.label = "Un libellé bien plus long"
        screen.frame()
        assertEquals(100f, button.width)
    }

    @Test
    fun `before the first frame, Tab can't land on a header's back button that won't show`() {
        val header = screen.add(Header("Titre").at(0f, 0f))
        val button = screen.add(Button("OK").at(12f, 100f))
        // Shown with nothing to go back to, and a key comes before any frame.
        screen.onShown()
        screen.key(GLFW_KEY_TAB)
        assertSame(button, screen.focused)
        assertFalse(header.children.first().visible)
        assertNotSame(header.children.first(), screen.focused)
    }

    @Test
    fun `shouldAnimate is false until the screen has drawn since it was shown`() {
        val toggle = screen.add(Toggle().at(0f, 0f))
        assertFalse(toggle.shouldAnimate)
        screen.frame()
        assertEquals(true, toggle.shouldAnimate)
        screen.onHidden()
        screen.onShown()
        assertFalse(toggle.shouldAnimate, "shown again: values set now simply appear")
        assertFalse(Toggle().shouldAnimate, "off any screen")
    }

    @Test
    fun `a row added after the first frame and set up in either order doesn't slide`() {
        val list = screen.add(ScrollList().at(0f, 0f, 256f, 300f))
        screen.frame()
        // A list rebuilt from a server's answer: value set after add, or before.
        val after = Toggle()
        list.add(ListRow("Après").apply { trailing = after })
        after.checked = true
        val before = Toggle().apply { checked = true }
        list.add(ListRow("Avant").apply { trailing = before })
        assertFalse(after.shouldAnimate)
        assertFalse(before.shouldAnimate)
        screen.frame()
        assertEquals(true, after.shouldAnimate, "drawn once, its changes animate")
    }

    @Test
    fun `a child moved to another widget is forgotten by the first`() {
        val toggle = Toggle()
        val a = screen.add(ListRow("A").apply { trailing = toggle }.at(0f, 0f))
        val b = screen.add(ListRow("B").at(0f, 48f))
        b.trailing = toggle
        assertEquals(null, a.trailing)
        screen.frame()
        assertEquals(48f + (48f - 20f) / 2f, toggle.toViewportY(0f), "B places it")
        screen.click(40f, 24f)
        assertFalse(toggle.checked, "a click on A doesn't flip B's toggle")
        val header = screen.add(Header("T").at(0f, 100f))
        val close = IconButton(Icon.CLOSE)
        header.trailing = close
        screen.add(close)
        assertEquals(null, header.trailing)
    }

    @Test
    fun `a row owning a disabled toggle counts as disabled`() {
        val toggle = Toggle()
        val row = screen.add(ListRow("Aperçu").apply { trailing = toggle }.at(0f, 0f))
        toggle.enabled = false
        assertFalse(row.isEnabled)
        assertEquals(Interaction.DISABLED, row.interaction)
        KitFakes.sounds.clear()
        screen.click(40f, 24f)
        assertFalse(toggle.checked)
        assertEquals(listOf(PhoneSounds.Sound.ERROR), KitFakes.sounds)
        assertEquals(Colors.TEXT_3, screen.canvas.texts.single { it.text == "Aperçu" }.color)
    }

    @Test
    fun `a toggle that becomes owned loses the focus`() {
        val toggle = Toggle()
        val row = screen.add(ListRow("Wi-Fi") {}.apply { trailing = toggle }.at(0f, 0f))
        screen.frame()
        screen.focus(toggle)
        assertSame(toggle, screen.focused)
        row.onClick = null
        screen.frame()
        assertEquals(null, screen.focused)
    }
}

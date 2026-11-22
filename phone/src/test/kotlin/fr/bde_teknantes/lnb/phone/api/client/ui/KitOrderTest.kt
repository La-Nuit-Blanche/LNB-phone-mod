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
}

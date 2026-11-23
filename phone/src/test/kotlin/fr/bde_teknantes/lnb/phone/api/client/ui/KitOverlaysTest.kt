package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.client.ui.Placement
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
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_TAB

/** Menus, dialogs, snackbars and tooltips on a [KitScreen]. */
class KitOverlaysTest {
    private lateinit var screen: TestScreen

    @BeforeEach
    fun setUp() {
        KitFakes.install()
        screen = TestScreen()
        screen.frame()
    }

    @AfterEach
    fun tearDown() = KitFakes.uninstall()

    private class Counter : Clickable() {
        var clicks = 0

        init {
            onClick = { clicks++ }
        }
    }

    @Nested
    inner class Menus {
        private lateinit var more: Counter
        private lateinit var behind: Counter
        private val ran = ArrayList<String>()
        private var menuOpenWhenRun: Boolean? = null

        @BeforeEach
        fun open() {
            behind = screen.add(Counter().at(0f, 100f, 256f, 200f))
            more = screen.add(Counter().at(218f, 6f, 32f, 32f))
            screen.showMenu(
                more,
                listOf(
                    MenuItem("Copier") { ran += "copy"; menuOpenWhenRun = screen.isMenuOpen },
                    MenuItem("Supprimer") { ran += "delete" }.apply { destructive = true },
                ),
            )
            screen.frame()
        }

        // The menu: 168 x 88 dp, its top-right 4 dp under the button's bottom-right: x 82..250, y 42..130.
        private val firstItem = 150f to (42f + 4f + 20f)
        private val secondItem = 150f to (42f + 4f + 60f)

        @Test
        fun `an item click runs it once, with the menu already closed`() {
            screen.click(firstItem.first, firstItem.second)
            assertEquals(listOf("copy"), ran)
            assertEquals(false, menuOpenWhenRun)
            assertFalse(screen.isMenuOpen)
        }

        @Test
        fun `a click elsewhere only closes the menu`() {
            screen.click(20f, 200f)
            assertFalse(screen.isMenuOpen)
            assertEquals(0, behind.clicks)
            assertTrue(ran.isEmpty())
            // The next click reaches the screen again.
            screen.click(20f, 200f)
            assertEquals(1, behind.clicks)
        }

        @Test
        fun `a click on the menu's own padding keeps it open`() {
            screen.click(150f, 43f)
            assertTrue(screen.isMenuOpen)
            assertTrue(ran.isEmpty())
        }

        @Test
        fun `the wheel does nothing while a menu is open`() {
            assertTrue(screen.wheel(20f, 200f, -1f))
            assertTrue(screen.isMenuOpen)
        }

        @Test
        fun `esc closes the menu and goes no further`() {
            assertTrue(screen.key(GLFW_KEY_ESCAPE))
            assertFalse(screen.isMenuOpen)
            assertEquals(0, screen.keysToScreen)
        }

        @Test
        fun `a press on an item dragged off and released runs nothing`() {
            screen.press(secondItem.first, secondItem.second)
            screen.drag(20f, 300f)
            screen.release(20f, 300f)
            assertTrue(ran.isEmpty())
        }

        @Test
        fun `opening a menu replaces the one open`() {
            screen.showMenu(more, listOf(MenuItem("Seul") { ran += "only" }))
            screen.frame()
            screen.click(firstItem.first, firstItem.second)
            assertEquals(listOf("only"), ran)
        }

        @Test
        fun `a menu needs items`() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) { screen.showMenu(more, emptyList()) }
        }
    }

    @Nested
    inner class Dialogs {
        private lateinit var behind: Counter
        private var cancels = 0
        private var confirms = 0
        private lateinit var dialog: Dialog

        @BeforeEach
        fun open() {
            behind = screen.add(Counter().at(0f, 0f, 256f, 370f))
            dialog = Dialog("Supprimer ce post ?").apply {
                cancelLabel = "Annuler"
                confirmLabel = "Supprimer"
                text = "C'est définitif."
                danger = true
                onCancel = { cancels++ }
                onConfirm = { confirms++ }
            }
            screen.showDialog(dialog)
            screen.frame()
        }

        // One title line, one text line: 124 dp tall at y 122, buttons 32 dp at y 122 + 124 - 48 = 198.
        private val cardTop = 122f
        private val buttonsY = 198f + 16f
        private val cancelX = 244f - 16f - 104f - 8f - 44f
        private val confirmX = 244f - 16f - 52f

        @Test
        fun `the card sits where the art direction's sum puts it`() {
            assertEquals(124f, Placement.dialogHeight(1, 1))
            assertEquals(cardTop, Placement.dialogTop(124f))
        }

        @Test
        fun `nothing behind the scrim gets the pointer, the wheel or the keys`() {
            assertTrue(screen.wheel(128f, 20f, -1f))
            assertFalse(screen.key(org.lwjgl.glfw.GLFW.GLFW_KEY_F))
            assertEquals(0, screen.keysToScreen)
            screen.click(128f, 20f)
            assertEquals(0, behind.clicks)
        }

        @Test
        fun `a click on the scrim cancels`() {
            screen.advance(150f)
            screen.click(128f, 20f)
            assertEquals(1, cancels)
            assertFalse(screen.isDialogOpen)
        }

        @Test
        fun `a click on the card's text does nothing`() {
            screen.click(60f, cardTop + 20f)
            assertEquals(0, cancels)
            assertTrue(screen.isDialogOpen)
        }

        @Test
        fun `cancel and confirm buttons`() {
            screen.click(cancelX, buttonsY)
            assertEquals(1, cancels)
            assertEquals(0, confirms)
            assertFalse(screen.isDialogOpen)
        }

        @Test
        fun `confirm loads until closed, and can't be cancelled meanwhile`() {
            screen.click(confirmX, buttonsY)
            assertEquals(1, confirms)
            assertTrue(dialog.isLoading)
            assertTrue(screen.key(GLFW_KEY_ESCAPE))
            screen.click(128f, 20f)
            screen.click(confirmX, buttonsY)
            assertEquals(0, cancels)
            assertEquals(1, confirms)
            assertTrue(screen.isDialogOpen)
            dialog.close()
            assertFalse(screen.isDialogOpen)
        }

        @Test
        fun `esc cancels once, then belongs to the phone again`() {
            assertTrue(screen.key(GLFW_KEY_ESCAPE))
            assertEquals(1, cancels)
            // Fading out: Esc goes on to the screen (and then to the phone, which puts itself away).
            assertFalse(screen.key(GLFW_KEY_ESCAPE))
            assertEquals(1, cancels)
            assertEquals(1, screen.keysToScreen)
        }

        @Test
        fun `a closed dialog fades for 100 ms, catching clicks, then leaves and lets them through`() {
            dialog.cancel()
            screen.advance(50f)
            screen.click(128f, 20f)
            assertEquals(0, behind.clicks, "the second click of a double click must not reach what is behind")
            screen.advance(50f)
            screen.click(128f, 20f)
            assertEquals(1, behind.clicks)
        }

        @Test
        fun `a press on the scrim in its first 150 ms is the end of a double click`() {
            screen.click(128f, 20f)
            assertEquals(0, cancels)
            assertTrue(screen.isDialogOpen)
        }

        @Test
        fun `tab stays inside the dialog, from cancel to confirm and round again`() {
            screen.key(GLFW_KEY_TAB)
            val first = screen.focused
            screen.key(GLFW_KEY_TAB)
            val second = screen.focused
            screen.key(GLFW_KEY_TAB)
            assertSame(first, screen.focused)
            assertTrue(first is Button && second is Button && first !== second)
            assertFalse(first === behind || second === behind)
            screen.key(GLFW_KEY_TAB)
            screen.key(GLFW_KEY_ENTER)
            assertEquals(1, confirms)
        }

        @Test
        fun `showing a dialog takes the focus from the screen behind`() {
            dialog.close()
            screen.advance(100f)
            val field = screen.add(TextField().at(0f, 0f))
            screen.focus(field)
            screen.showDialog(Dialog("T").apply { onConfirm = {} })
            assertNull(screen.focused)
            assertFalse(screen.hasTextFocus)
            screen.type("x")
            assertEquals("", field.text)
        }

        @Test
        fun `a menu over a dialog closes first on esc`() {
            screen.showMenu(behind, listOf(MenuItem("A") {}))
            assertTrue(screen.key(GLFW_KEY_ESCAPE))
            assertFalse(screen.isMenuOpen)
            assertTrue(screen.isDialogOpen)
            assertTrue(screen.key(GLFW_KEY_ESCAPE))
            assertFalse(screen.isDialogOpen)
        }

        @Test
        fun `showing a dialog closes an open menu`() {
            dialog.close()
            screen.advance(100f)
            screen.showMenu(behind, listOf(MenuItem("A") {}))
            screen.showDialog(dialog)
            assertFalse(screen.isMenuOpen)
            assertTrue(screen.isDialogOpen)
        }
    }

    @Nested
    inner class Snackbars {
        private fun showing(text: String): Boolean {
            screen.frame()
            return screen.canvas.drew(text)
        }

        @Test
        fun `shows for 150 + 3000 + 150 ms`() {
            screen.showSnackbar("Publié")
            assertTrue(showing("Publié"))
            screen.advance(3250f)
            assertTrue(showing("Publié"))
            screen.advance(50f)
            assertFalse(showing("Publié"))
        }

        @Test
        fun `with an action it holds 6 seconds`() {
            screen.showErrorSnackbar("Échec de l'envoi", SnackbarAction("Réessayer") {})
            screen.advance(6250f)
            assertTrue(showing("Échec de l'envoi"))
            screen.advance(50f)
            assertFalse(showing("Échec de l'envoi"))
        }

        @Test
        fun `a new snackbar replaces the one showing, at once and without sliding`() {
            screen.showSnackbar("Publié")
            screen.advance(1000f)
            screen.showSnackbar("Post supprimé")
            assertFalse(showing("Publié"))
            assertTrue(showing("Post supprimé"))
            assertEquals(1f, screen.snackbarLift())
            // Its own 3000 + 150 ms count from now, with no slide-in.
            screen.advance(3100f)
            assertTrue(showing("Post supprimé"))
            screen.advance(50f)
            assertFalse(showing("Post supprimé"))
        }

        @Test
        fun `the action runs, then the snackbar fades`() {
            var retries = 0
            screen.showSnackbar("Échec de l'envoi", SnackbarAction("Réessayer") { retries++ })
            screen.advance(200f)
            // The action is a text button right-aligned 4 dp from the snackbar's right edge (x 248), at y 326.
            screen.click(240f, 340f)
            assertEquals(1, retries)
            screen.advance(150f)
            assertFalse(showing("Échec de l'envoi"))
        }

        @Test
        fun `beside an action, the text runs up to the button, whose padding is the gap`() {
            // 6.5 dp a character: "Réessayer" makes a 74.5 dp text button ending 4 dp from the right edge (x 248), at
            // x 161.5; the error text starts at x 38, so it has 123.5 dp, with no gap besides the button's own padding.
            screen.showErrorSnackbar("Connexion perdue", SnackbarAction("Réessayer") {})
            screen.frame()
            assertEquals(123.5f, screen.canvas.room.getValue("Connexion perdue"), 0.01f)
        }

        @Test
        fun `a click on the snackbar's text doesn't reach what is under it`() {
            val behind = screen.add(Counter().at(0f, 0f, 256f, 370f))
            screen.showSnackbar("Publié")
            screen.advance(200f)
            screen.click(40f, 340f)
            assertEquals(0, behind.clicks)
        }

        @Test
        fun `dismissing hides it at once`() {
            screen.showSnackbar("Publié")
            screen.dismissSnackbar()
            assertFalse(showing("Publié"))
        }
    }

    @Nested
    inner class Tooltips {
        private lateinit var target: Counter

        @BeforeEach
        fun place() {
            target = screen.add(Counter().at(100f, 200f, 28f, 28f))
            target.tooltip = "Plus d'options"
        }

        @Test
        fun `appears after the pointer rests 500 ms, disappears when it leaves`() {
            screen.moveTo(110f, 210f)
            screen.advance(450f)
            assertFalse(screen.canvas.drew("Plus d'options"))
            screen.advance(50f)
            assertTrue(screen.canvas.drew("Plus d'options"))
            screen.moveTo(10f, 10f)
            assertFalse(screen.canvas.drew("Plus d'options"))
            // Coming back starts the wait again.
            screen.moveTo(110f, 210f)
            assertFalse(screen.canvas.drew("Plus d'options"))
        }

        @Test
        fun `hidden while the button is held`() {
            screen.moveTo(110f, 210f)
            screen.advance(500f)
            screen.press(110f, 210f)
            screen.frame()
            assertFalse(screen.canvas.drew("Plus d'options"))
            screen.release(110f, 210f)
            assertTrue(screen.canvas.drew("Plus d'options"))
        }

        @Test
        fun `drawn above the element, below it near the top`() {
            screen.moveTo(110f, 210f)
            screen.advance(500f)
            val above = screen.canvas.texts.single { it.text == "Plus d'options" }
            // A 24 dp bubble 6 dp above y 200, its text 4 dp inside.
            assertEquals(200f - 6f - 24f + 4f, above.y)

            val top = screen.add(Counter().at(0f, 0f, 32f, 32f))
            top.tooltip = "Retour"
            screen.moveTo(10f, 10f)
            screen.advance(500f)
            val below = screen.canvas.texts.single { it.text == "Retour" }
            assertEquals(32f + 6f + 4f, below.y)
            assertTrue(below.x >= 4f + 8f)
        }

        @Test
        fun `a widget with a tooltip catches the pointer`() {
            val under = screen.add(Counter().at(0f, 300f, 256f, 60f))
            val label = screen.add(Label("Auteur : Mika Laurent").at(10f, 310f))
            label.tooltip = "Visible uniquement par les opérateurs"
            screen.click(20f, 315f)
            assertEquals(0, under.clicks)
        }
    }
}

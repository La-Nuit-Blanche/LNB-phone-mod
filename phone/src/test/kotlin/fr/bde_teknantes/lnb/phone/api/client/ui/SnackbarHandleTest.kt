package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.testing.PhoneTester
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The handle a snackbar is shown with: it takes that snackbar away, and no other, wherever it went. */
class SnackbarHandleTest {
    private class Screen : KitScreen() {
        fun shown() = phone
    }

    private var tester: PhoneTester? = null
    private val first = Screen()

    private fun open(): PhoneTester = PhoneTester { first }.also { tester = it }

    @AfterEach
    fun tearDown() {
        tester?.close()
    }

    @Test
    fun `dismiss takes its own snackbar away at once`() {
        val t = open()
        val handle = first.showErrorSnackbar("Connexion perdue", SnackbarAction("Réessayer") {})
        t.tick(4)
        assertTrue(t.shows("Connexion perdue"))
        assertTrue(handle.isShowing)
        handle.dismiss()
        t.frame()
        assertFalse(t.shows("Connexion perdue"))
        assertFalse(handle.isShowing)
    }

    @Test
    fun `dismiss leaves alone the snackbar that replaced its own`() {
        val t = open()
        val lost = first.showErrorSnackbar("Connexion perdue")
        first.showErrorSnackbar("Action impossible")
        assertFalse(lost.isShowing)
        lost.dismiss()
        t.tick(2)
        assertTrue(t.shows("Action impossible"))
    }

    @Test
    fun `dismiss reaches its snackbar once handed to the next screen`() {
        val t = open()
        val handle = first.showSnackbar("Publié")
        val second = Screen()
        first.shown().push(second)
        t.tick(4)
        assertTrue(t.shows("Publié"))
        assertTrue(handle.isShowing)
        handle.dismiss()
        t.frame()
        assertFalse(t.shows("Publié"))
    }

    @Test
    fun `a dismissed snackbar doesn't come back on the screen popped to`() {
        val t = open()
        val handle = first.showSnackbar("Publié")
        val second = Screen()
        first.shown().push(second)
        handle.dismiss()
        t.tick(2)
        second.shown().pop()
        t.tick(2)
        assertFalse(t.shows("Publié"))
    }

    @Test
    fun `a snackbar whose time ran out reads as gone, and its handle does nothing`() {
        val t = open()
        val handle = first.showSnackbar("Publié")
        t.advance(3400)
        assertFalse(handle.isShowing)
        val next = first.showSnackbar("Post supprimé")
        handle.dismiss()
        t.frame()
        assertTrue(next.isShowing)
        assertTrue(t.shows("Post supprimé"))
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextStateTest {
    @Test
    fun `typing inserts at the caret and replaces the selection`() {
        val s = TextState("Bonjour")
        s.moveTo(3)
        s.insert("é")
        assertEquals("Bonéjour", s.text)
        assertEquals(4, s.caret)
        s.moveTo(0)
        s.moveTo(4, select = true)
        s.insert("X")
        assertEquals("Xjour", s.text)
    }

    @Test
    fun `filters what can't be typed`() {
        val single = TextState("", false)
        single.insert("a\u0007b§c\r\nd\te")
        assertEquals("abc d e", single.text)
        val area = TextState()
        area.insert("a\r\nb")
        assertEquals("a\nb", area.text)
    }

    @Test
    fun `never splits a character outside the basic plane`() {
        val s = TextState("a😀b")
        s.moveTo(2)
        assertEquals(1, s.caret)
        s.moveTo(3)
        s.deleteBackward()
        assertEquals("ab", s.text)
        assertEquals(2, TextState("😀😀").length)
    }

    @Test
    fun `moves and deletes by words`() {
        val s = TextState("Premier arrivé, premier servi")
        s.moveLeft(false, true)
        assertEquals("Premier arrivé, premier ".length, s.caret)
        s.deleteBackward(word = true)
        assertEquals("Premier arrivé, servi", s.text)
        s.moveTo(0)
        s.moveRight(false, true)
        assertEquals("Premier ".length, s.caret)
    }

    @Test
    fun `a collapsed move ends the selection where the arrow points`() {
        val s = TextState("abcdef")
        s.moveTo(1)
        s.moveTo(4, select = true)
        s.moveLeft()
        assertEquals(1, s.caret)
        assertFalse(s.hasSelection)
    }

    @Test
    fun `keeps to the maximum length`() {
        val s = TextState("", true, 5)
        s.insert("abcdefgh")
        assertEquals("abcde", s.text)
        s.selectAll()
        s.insert("xy")
        assertEquals("xy", s.text)
    }

    @Test
    fun `double click selects a word`() {
        val s = TextState("le loup bleu")
        s.selectWordAt(4)
        assertEquals("loup", s.selectedText)
    }

    @Test
    fun `listeners hear text changes only`() {
        val s = TextState("ab")
        var calls = 0
        s.addListener { calls++ }
        s.moveTo(0)
        s.insert("x")
        s.deleteForward()
        assertEquals(2, calls)
        assertTrue(s.version >= 2)
    }

    @Test
    fun `the counter reads and colours as the art direction says`() {
        assertEquals("142/280", CharacterCounter.label(142, 280))
        assertEquals("-5", CharacterCounter.label(285, 280))
        assertEquals(Colors.TEXT_2, CharacterCounter.color(260, 280))
        assertEquals(Colors.WARNING, CharacterCounter.color(261, 280))
        assertEquals(Colors.WARNING, CharacterCounter.color(280, 280))
        assertEquals(Colors.DANGER, CharacterCounter.color(281, 280))
        assertFalse(CharacterCounter.canSend("   \n ", 280))
        assertEquals(3, CharacterCounter.overflowStart("abcdé", 3))
    }
}

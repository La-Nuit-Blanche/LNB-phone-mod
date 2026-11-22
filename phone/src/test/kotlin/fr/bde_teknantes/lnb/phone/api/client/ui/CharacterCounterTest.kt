package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test

/** The counter of art direction 8.7 and 9.8 at every threshold, with characters counted as code points. */
class CharacterCounterTest {
    private val limit = 280

    @ParameterizedTest(name = "{0} characters")
    @CsvSource(
        "0,    0/280, TEXT_2,  false",
        "1,    1/280, TEXT_2,  true",
        "259,  259/280, TEXT_2, true",
        "260,  260/280, TEXT_2, true",
        "261,  261/280, WARNING, true",
        "279,  279/280, WARNING, true",
        "280,  280/280, WARNING, true",
        "281,  -1, DANGER, false",
        "285,  -5, DANGER, false",
        "1000, -720, DANGER, false",
    )
    fun `label, colour and sending at each threshold`(count: Int, label: String, color: String, canSend: Boolean) {
        val expectedColor = when (color) {
            "TEXT_2" -> Colors.TEXT_2
            "WARNING" -> Colors.WARNING
            else -> Colors.DANGER
        }
        val text = "a".repeat(count)
        assertEquals(label, CharacterCounter.label(count, limit))
        assertEquals(expectedColor, CharacterCounter.color(count, limit))
        assertEquals(canSend, CharacterCounter.canSend(text, limit))
        assertEquals(count, CharacterCounter.count(text))
    }

    @Test
    fun `the excess is marked from the 281st character, and only past the limit`() {
        assertNull(CharacterCounter.overflowStart("a".repeat(280), limit))
        assertEquals(280, CharacterCounter.overflowStart("a".repeat(281), limit))
        assertEquals(280, CharacterCounter.overflowStart("a".repeat(1000), limit))
        assertNull(CharacterCounter.overflowStart("", limit))
    }

    @Test
    fun `an emoji counts one, and the mark starts after whole pairs`() {
        val text = "a".repeat(279) + "😀" + "b"
        assertEquals(281, CharacterCounter.count(text))
        assertEquals("-1", CharacterCounter.label(CharacterCounter.count(text), limit))
        // 279 letters and an emoji (two UTF-16 units) are within the limit: the mark starts at "b".
        assertEquals(281, CharacterCounter.overflowStart(text, limit))
        assertEquals("b", text.substring(CharacterCounter.overflowStart(text, limit)!!))
    }

    @Test
    fun `an emoji exactly at the limit is not marked`() {
        val text = "a".repeat(279) + "😀"
        assertEquals(280, CharacterCounter.count(text))
        assertNull(CharacterCounter.overflowStart(text, limit))
        assertTrue(CharacterCounter.canSend(text, limit))
    }

    @Test
    fun `accents count one when precomposed, two when decomposed`() {
        // What a "character" is isn't written in the art direction; the code counts code points. A decomposed é
        // (pasted from some systems) looks like one character and counts two.
        assertEquals(1, CharacterCounter.count("é"))
        assertEquals(2, CharacterCounter.count("é"))
        // A flag is two code points, a family emoji five.
        assertEquals(2, CharacterCounter.count("🇫🇷"))
        assertEquals(5, CharacterCounter.count("👨‍👩‍👧"))
    }

    @Test
    fun `a text of spaces and line feeds can't be sent, a single dot can`() {
        assertFalse(CharacterCounter.canSend("   \n\n ", limit))
        assertFalse(CharacterCounter.canSend(" ", limit))
        assertTrue(CharacterCounter.canSend(".", limit))
    }

    @Test
    fun `spaces around the text count toward the limit`() {
        // The send button checks the text as typed: trailing spaces can put a post over the limit.
        val text = "a".repeat(280) + " "
        assertFalse(CharacterCounter.canSend(text, limit))
    }

    @Test
    fun `other limits keep a 20 character amber zone`() {
        assertEquals(Colors.TEXT_2, CharacterCounter.color(30, 50))
        assertEquals(Colors.WARNING, CharacterCounter.color(31, 50))
        // A limit below 20 is amber from the first character.
        assertEquals(Colors.WARNING, CharacterCounter.color(0, 10))
    }
}

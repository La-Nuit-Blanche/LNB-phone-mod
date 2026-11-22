package fr.bde_teknantes.lnb.phone.api.client.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** [TextState] as a text editor's core: caret, selection, words, deletion, insertion, limits, surrogate pairs. */
class TextStateEditingTest {
    private fun state(text: String, caret: Int = text.length, anchor: Int = caret, multiline: Boolean = true, maxLength: Int = TextState.DEFAULT_MAX_LENGTH) =
        TextState(text, multiline, maxLength).apply {
            moveTo(anchor)
            moveTo(caret, select = true)
        }

    @Nested
    inner class CaretMoves {
        @Test
        fun `a new state puts the caret at the end, with nothing selected`() {
            val s = TextState("Salut")
            assertEquals(5, s.caret)
            assertEquals(5, s.anchor)
            assertFalse(s.hasSelection)
        }

        @Test
        fun `left and right move one character and stop at the ends`() {
            val s = state("abc", caret = 0)
            s.moveLeft()
            assertEquals(0, s.caret)
            s.moveRight()
            assertEquals(1, s.caret)
            s.moveRight()
            s.moveRight()
            assertEquals(3, s.caret)
            s.moveRight()
            assertEquals(3, s.caret)
            s.moveLeft()
            assertEquals(2, s.caret)
        }

        @Test
        fun `moveTo clamps outside the text`() {
            val s = state("abc")
            s.moveTo(-5)
            assertEquals(0, s.caret)
            s.moveTo(99)
            assertEquals(3, s.caret)
        }

        @Test
        fun `on an empty text every move stays at 0`() {
            val s = TextState("")
            s.moveLeft()
            s.moveRight(word = true)
            s.moveLeft(select = true, word = true)
            assertEquals(0, s.caret)
            assertFalse(s.hasSelection)
        }
    }

    @Nested
    inner class Selection {
        @Test
        fun `shift extends the selection from the anchor, in both directions, across it`() {
            val s = state("abcdef", caret = 2)
            s.moveRight(select = true)
            s.moveRight(select = true)
            assertEquals(2, s.anchor)
            assertEquals("cd", s.selectedText)
            // Back over the anchor: the selection flips to the other side of it.
            repeat(4) { s.moveLeft(select = true) }
            assertEquals(0, s.caret)
            assertEquals(2, s.anchor)
            assertEquals("ab", s.selectedText)
            assertEquals(0, s.selectionStart)
            assertEquals(2, s.selectionEnd)
        }

        @Test
        fun `coming back to the anchor leaves nothing selected`() {
            val s = state("abcdef", caret = 2)
            s.moveRight(select = true)
            s.moveLeft(select = true)
            assertFalse(s.hasSelection)
            assertEquals("", s.selectedText)
        }

        @Test
        fun `left without shift collapses a selection to its start, right to its end, whichever way it was made`() {
            val forward = state("abcdef", caret = 4, anchor = 1)
            forward.moveLeft()
            assertEquals(1, forward.caret)
            assertFalse(forward.hasSelection)

            val backward = state("abcdef", caret = 1, anchor = 4)
            backward.moveRight()
            assertEquals(4, backward.caret)
            assertFalse(backward.hasSelection)

            val backwardLeft = state("abcdef", caret = 1, anchor = 4)
            backwardLeft.moveLeft()
            assertEquals(1, backwardLeft.caret)
        }

        @Test
        fun `a word move without shift also only collapses a selection`() {
            val s = state("un deux trois", caret = 7, anchor = 3)
            s.moveRight(word = true)
            assertEquals(7, s.caret)
            assertFalse(s.hasSelection)
        }

        @Test
        fun `shift with ctrl extends by words`() {
            val s = state("un deux trois", caret = 0)
            s.moveRight(select = true, word = true)
            s.moveRight(select = true, word = true)
            assertEquals("un deux ", s.selectedText)
            s.moveLeft(select = true, word = true)
            assertEquals("un ", s.selectedText)
        }

        @Test
        fun `select all, then an arrow lands at either end`() {
            val s = state("abc", caret = 1)
            s.selectAll()
            assertEquals("abc", s.selectedText)
            assertEquals(3, s.caret)
            assertEquals(0, s.anchor)
            s.moveLeft()
            assertEquals(0, s.caret)
            s.selectAll()
            s.moveRight()
            assertEquals(3, s.caret)
        }

        @Test
        fun `select all on an empty text selects nothing`() {
            val s = TextState("")
            s.selectAll()
            assertFalse(s.hasSelection)
        }
    }

    @Nested
    inner class FrenchWords {
        @Test
        fun `an apostrophe stays inside a word, straight or curly`() {
            val s = state("l'End est là", caret = 0)
            s.moveRight(word = true)
            assertEquals("l'End ".length, s.caret)
            s.moveRight(word = true)
            assertEquals("l'End est ".length, s.caret)
            s.moveRight(word = true)
            assertEquals(12, s.caret)

            val curly = state("l’heure", caret = 0)
            curly.moveRight(word = true)
            assertEquals(7, curly.caret)
        }

        @Test
        fun `aujourd'hui is one word, and the comma after it is a stop of its own`() {
            val s = state("aujourd'hui, c'est", caret = 0)
            s.moveRight(word = true)
            assertEquals(11, s.caret)
            s.moveRight(word = true)
            assertEquals(13, s.caret)
            s.moveRight(word = true)
            assertEquals(18, s.caret)
            s.moveLeft(word = true)
            assertEquals(13, s.caret)
            s.moveLeft(word = true)
            assertEquals(11, s.caret)
            s.moveLeft(word = true)
            assertEquals(0, s.caret)
        }

        @Test
        fun `a hyphen splits est-ce into three stops`() {
            // Decided by the code, not by the art direction: the hyphen is punctuation, a run of its own.
            val s = state("est-ce que", caret = 0)
            val stops = (1..4).map { s.moveRight(word = true); s.caret }
            assertEquals(listOf(3, 4, 7, 10), stops)
            val back = (1..4).map { s.moveLeft(word = true); s.caret }
            assertEquals(listOf(7, 4, 3, 0), back)
        }

        @Test
        fun `ctrl+left skips the spaces before the word`() {
            val s = state("Bonjour   ")
            s.moveLeft(word = true)
            assertEquals(0, s.caret)
        }

        @Test
        fun `a non-breaking space before French punctuation moves with the punctuation`() {
            // Java doesn't count U+00A0 as white space, so " !" is one punctuation run.
            val s = state("Salut ! Oui")
            s.moveTo(0)
            s.moveRight(word = true)
            assertEquals(5, s.caret)
            s.moveRight(word = true)
            assertEquals(8, s.caret)
        }

        @Test
        fun `digits are word characters, the colon of a time is not`() {
            val s = state("à 15:00", caret = 2)
            s.moveRight(word = true)
            assertEquals(4, s.caret)
            s.moveRight(word = true)
            assertEquals(5, s.caret)
        }
    }

    @Nested
    inner class Deletion {
        @Test
        fun `backspace at the start and delete at the end do nothing`() {
            val s = state("abc", caret = 0)
            val version = s.version
            s.deleteBackward()
            s.deleteBackward(word = true)
            assertEquals("abc", s.text)
            s.moveTo(3)
            s.deleteForward()
            s.deleteForward(word = true)
            assertEquals("abc", s.text)
            assertEquals(version, s.version)
        }

        @Test
        fun `backspace and delete in the middle`() {
            val s = state("abcd", caret = 2)
            s.deleteBackward()
            assertEquals("acd", s.text)
            assertEquals(1, s.caret)
            s.deleteForward()
            assertEquals("ad", s.text)
            assertEquals(1, s.caret)
        }

        @Test
        fun `either key deletes the selection only, even with ctrl`() {
            val a = state("un deux trois", caret = 7, anchor = 3)
            a.deleteBackward(word = true)
            assertEquals("un  trois", a.text)
            assertEquals(3, a.caret)
            val b = state("un deux trois", caret = 3, anchor = 7)
            b.deleteForward(word = true)
            assertEquals("un  trois", b.text)
            assertEquals(3, b.caret)
        }

        @Test
        fun `ctrl+backspace deletes the word before the caret and the spaces after it`() {
            val s = state("Rendez-vous à l'arène   ")
            s.deleteBackward(word = true)
            assertEquals("Rendez-vous à ", s.text)
            s.deleteBackward(word = true)
            assertEquals("Rendez-vous ", s.text)
            s.deleteBackward(word = true)
            assertEquals("Rendez-", s.text)
            s.deleteBackward(word = true)
            assertEquals("Rendez", s.text)
        }

        @Test
        fun `ctrl+delete deletes the word after the caret and the spaces that follow`() {
            val s = state("Bonjour le monde", caret = 0)
            s.deleteForward(word = true)
            assertEquals("le monde", s.text)
            assertEquals(0, s.caret)
            s.moveTo(2)
            s.deleteForward(word = true)
            assertEquals("lemonde", s.text)
        }

        @Test
        fun `typing replaces a selection made in either direction`() {
            val forward = state("Bonjour Camille", caret = 15, anchor = 8)
            forward.insert("Lune")
            assertEquals("Bonjour Lune", forward.text)
            assertEquals(12, forward.caret)
            assertFalse(forward.hasSelection)

            val backward = state("Bonjour Camille", caret = 8, anchor = 15)
            backward.insert("Lune")
            assertEquals("Bonjour Lune", backward.text)
        }

        @Test
        fun `inserting nothing over a selection deletes it`() {
            val s = state("abc", caret = 3, anchor = 1)
            s.insert("")
            assertEquals("a", s.text)
        }

        @Test
        fun `inserting only filtered characters over a selection deletes it`() {
            val s = state("abc", caret = 3, anchor = 1)
            s.insert("§\u0007")
            assertEquals("a", s.text)
        }
    }

    @Nested
    inner class Pasting {
        @Test
        fun `a paste longer than the room left is cut at the maximum`() {
            val s = state("abcdef", maxLength = 10)
            s.insert("123456")
            assertEquals("abcdef1234", s.text)
            assertEquals(10, s.caret)
        }

        @Test
        fun `the room counts the selection being replaced`() {
            val s = state("abcdef", caret = 4, anchor = 2, maxLength = 10)
            s.insert("123456")
            assertEquals("ab123456ef", s.text)
            assertEquals(8, s.caret)
        }

        @Test
        fun `a full text takes nothing more, wherever the caret is`() {
            val s = state("abcde", caret = 2, maxLength = 5)
            val version = s.version
            s.insert("X")
            assertEquals("abcde", s.text)
            assertEquals(2, s.caret)
            assertEquals(version, s.version)
        }

        @Test
        fun `line breaks pasted into a single-line field become spaces, whatever their kind`() {
            val s = TextState(multiline = false)
            s.insert("un\ndeux\r\ntrois\rquatre")
            assertEquals("un deux trois quatre", s.text)
        }

        @Test
        fun `Windows line ends become one line feed in a text area and count as one character`() {
            val s = TextState(maxLength = 5)
            s.insert("a\r\nb\r\nc")
            assertEquals("a\nb\nc", s.text)
            assertEquals(5, s.length)
        }

        @Test
        fun `a carriage return at the very end still becomes a line feed`() {
            val s = TextState()
            s.insert("fin\r")
            assertEquals("fin\n", s.text)
        }

        @Test
        fun `tabs become spaces and control characters go`() {
            val s = TextState()
            s.insert("a\tb\u0000c\u001Bd\u007Fe\u0085f")
            assertEquals("a bcdef", s.text)
        }

        @Test
        fun `the section sign is filtered, French spaces are kept`() {
            val s = TextState()
            s.insert("§cRouge ! ?")
            assertEquals("cRouge ! ?", s.text)
        }

        @Test
        fun `setting the text puts the caret at the end, cut at the maximum`() {
            val s = state("abc", caret = 1, maxLength = 4)
            s.text = "abcdefgh"
            assertEquals("abcd", s.text)
            assertEquals(4, s.caret)
            assertFalse(s.hasSelection)
        }

        @Test
        fun `an initial text over the maximum is cut too`() {
            assertEquals("abc", TextState("abcdef", maxLength = 3).text)
        }

        @Test
        fun `a maximum below 1 is refused`() {
            assertThrows(IllegalArgumentException::class.java) { TextState(maxLength = 0) }
        }

        @Test
        fun `the default maximum stops a huge paste`() {
            val s = TextState()
            s.insert("x".repeat(10_000))
            assertEquals(TextState.DEFAULT_MAX_LENGTH, s.length)
        }
    }

    @Nested
    inner class SurrogatePairs {
        private val smile = "😀"

        @Test
        fun `arrows step over an emoji whole`() {
            val s = state("a${smile}b", caret = 1)
            s.moveRight()
            assertEquals(3, s.caret)
            s.moveLeft()
            assertEquals(1, s.caret)
        }

        @Test
        fun `a caret placed inside a pair goes back before it, with or without shift`() {
            val s = state("a${smile}b")
            s.moveTo(2)
            assertEquals(1, s.caret)
            s.moveTo(4)
            s.moveTo(2, select = true)
            assertEquals(1, s.caret)
            assertEquals("${smile}b", s.selectedText)
        }

        @Test
        fun `backspace and delete remove the whole emoji`() {
            val s = state("a${smile}${smile}b", caret = 3)
            s.deleteBackward()
            assertEquals("a${smile}b", s.text)
            assertEquals(1, s.caret)
            s.deleteForward()
            assertEquals("ab", s.text)
        }

        @Test
        fun `shift selection takes whole emoji`() {
            val s = state("${smile}${smile}", caret = 0)
            s.moveRight(select = true)
            assertEquals(smile, s.selectedText)
        }

        @Test
        fun `emoji are a punctuation run for word moves`() {
            val s = state("Salut ${smile}${smile} toi", caret = 6)
            s.moveRight(word = true)
            assertEquals(11, s.caret)
            s.deleteBackward(word = true)
            assertEquals("Salut toi", s.text)
        }

        @Test
        fun `the maximum never cuts a pair in two`() {
            val s = TextState("a", maxLength = 2)
            s.insert("$smile$smile")
            assertEquals("a$smile", s.text)
            assertEquals(2, s.length)
        }

        @Test
        fun `lone halves of a pair are dropped`() {
            val s = TextState()
            s.insert("a\uD83Db\uDE00c")
            assertEquals("abc", s.text)
        }

        @Test
        fun `a flag is two characters, and the caret can stop between its halves`() {
            // Code points, not what the player sees: a flag is two regional indicators. Reported, not decided.
            val flag = "🇫🇷"
            val s = TextState(flag)
            assertEquals(2, s.length)
            s.moveLeft()
            assertEquals(2, s.caret)
        }
    }

    @Nested
    inner class Accents {
        @Test
        fun `a precomposed accent is one character`() {
            val s = TextState()
            s.insert("é")
            assertEquals(1, s.length)
            s.moveLeft()
            assertEquals(0, s.caret)
        }

        @Test
        fun `a decomposed accent counts two and backspace takes the accent alone`() {
            // What a dead key gives on most systems is the precomposed letter; a pasted decomposed one stays two.
            val s = TextState("é")
            assertEquals(2, s.length)
            s.deleteBackward()
            assertEquals("e", s.text)
        }
    }

    @Nested
    inner class DoubleClick {
        @Test
        fun `selects the French word under the index, apostrophes included`() {
            val s = TextState("Rendez-vous à l'arène demain")
            s.selectWordAt(17)
            assertEquals("l'arène", s.selectedText)
            s.selectWordAt(3)
            assertEquals("Rendez", s.selectedText)
            s.selectWordAt(9)
            assertEquals("vous", s.selectedText)
        }

        @Test
        fun `at the very end selects the last word`() {
            val s = TextState("le loup")
            s.selectWordAt(7)
            assertEquals("loup", s.selectedText)
            assertEquals(7, s.caret)
            assertEquals(3, s.anchor)
        }

        @Test
        fun `in a run of spaces selects the spaces`() {
            val s = TextState("a   b")
            s.selectWordAt(2)
            assertEquals("   ", s.selectedText)
        }

        @Test
        fun `on an emoji selects the emoji run`() {
            val s = TextState("ok 😀😀 ok")
            s.selectWordAt(4)
            assertEquals("😀😀", s.selectedText)
        }

        @Test
        fun `on an empty text does nothing`() {
            val s = TextState("")
            s.selectWordAt(0)
            assertFalse(s.hasSelection)
        }

        @Test
        fun `at the end of a word selects the word, not what follows it`() {
            val s = TextState("le loup, bleu")
            s.selectWordAt(2)
            assertEquals("le", s.selectedText)
            s.selectWordAt(7)
            assertEquals("loup", s.selectedText)
            // At the start of a word, still the word; between two spaces, the spaces.
            s.selectWordAt(3)
            assertEquals("loup", s.selectedText)
            // Between a word and a hyphen: the word.
            val hyphen = TextState("est-ce")
            hyphen.selectWordAt(3)
            assertEquals("est", hyphen.selectedText)
            val spaces = TextState("a   b")
            spaces.selectWordAt(1)
            assertEquals("a", spaces.selectedText)
            spaces.selectWordAt(3)
            assertEquals("   ", spaces.selectedText)
        }
    }

    @Nested
    inner class Changes {
        @Test
        fun `listeners hear each change once, and no caret move`() {
            val s = TextState("ab")
            val heard = ArrayList<String>()
            val listener: (TextState) -> Unit = { heard += it.text }
            s.addListener(listener)
            s.moveLeft()
            s.selectAll()
            s.insert("x")
            s.text = "x"
            s.deleteBackward()
            assertEquals(listOf("x", ""), heard)
            s.removeListener(listener)
            s.insert("y")
            assertEquals(2, heard.size)
        }

        @Test
        fun `blank means spaces and line feeds only, non-breaking spaces included`() {
            assertTrue(TextState("  \n  ").isBlank)
            assertFalse(TextState(" . ").isBlank)
        }

        @Test
        fun `clear empties and moves the caret home`() {
            val s = TextState("abc")
            s.clear()
            assertEquals("", s.text)
            assertEquals(0, s.caret)
        }
    }
}

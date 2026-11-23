package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.BreakIterator
import java.time.Duration
import kotlin.random.Random

/**
 * [Graphemes] against Java's own [BreakIterator] run over the whole text (no fast path, no window), on every kind of
 * sequence and on malformed ones. The caret, the layout and the server's limits all rely on these boundaries.
 */
class GraphemesTest {
    companion object {
        /** Pieces a player's text is made of: letters, French punctuation, every kind of emoji sequence, and junk. */
        val PIECES = listOf(
            "a", "é", "é", " ", "!", "?", ":", "«", "»", " ", "\n", "\r\n", "\r", "1", "#", "*", "€",
            "😀", "👍", "👍🏽", "🏽", "👨‍👩‍👧", "👨‍", "‍", "‍‍", "❤️", "❤", "❤︎", "❤️‍🔥",
            "🏳️‍🌈", "🇫", "🇫🇷", "🇫🇷🇩🇪", "🇽", "1️⃣", "1⃣", "#️⃣", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "\uD83D", "\uDC4D", "\uDC4D\uD83D",
            "️", "️️", "ç", "田中", "ا", "é̂̃", "👩🏽‍💻", "🧑🏻‍❤️‍💋‍🧑🏼", "©️", "©",
        )

        /**
         * The reference: Java's character [BreakIterator] over the whole text, plus the one limit [Graphemes] adds: a
         * boundary before the emoji that would be a grapheme's [Graphemes.MAX_LINKS] + 1st joined by ZWJ.
         */
        fun reference(text: String): BooleanArray {
            val mask = BooleanArray(text.length + 1)
            val it = BreakIterator.getCharacterInstance()
            it.setText(text)
            var b = it.first()
            while (b != BreakIterator.DONE) {
                mask[b] = true
                b = it.next()
            }
            var links = 0
            var previous = -1
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (mask[i]) links = 0
                if (previous == 0x200D && Character.isExtendedPictographic(cp) && !mask[i] && ++links > Graphemes.MAX_LINKS) {
                    mask[i] = true
                    links = 0
                }
                previous = cp
                i += Character.charCount(cp)
            }
            return mask
        }
    }

    private fun describe(text: String) = text.map { "%04X".format(it.code) }.joinToString(" ")

    @Test
    fun `boundaries agree with the reference on random mixes of every piece`() {
        val random = Random(2024)
        assertTimeoutPreemptively(Duration.ofSeconds(30)) {
            repeat(4000) {
                val text = buildString { repeat(random.nextInt(0, 14)) { append(PIECES[random.nextInt(PIECES.size)]) } }
                assertTrue(reference(text).contentEquals(Graphemes.boundaries(text)), "text ${describe(text)}")
            }
        }
    }

    @Test
    fun `floor, ceil, previous, next and isBoundary agree with the boundary mask at every index`() {
        val random = Random(7)
        repeat(1500) {
            val text = buildString { repeat(random.nextInt(0, 10)) { append(PIECES[random.nextInt(PIECES.size)]) } }
            val mask = reference(text)
            for (i in 0..text.length) {
                val floor = (i downTo 0).first { mask[it] }
                val ceil = (i..text.length).first { mask[it] }
                assertEquals(floor, Graphemes.floor(text, i), "floor $i of ${describe(text)}")
                assertEquals(ceil, Graphemes.ceil(text, i), "ceil $i of ${describe(text)}")
                assertEquals(mask[i], Graphemes.isBoundary(text, i), "isBoundary $i of ${describe(text)}")
                if (mask[i]) {
                    val next = (i + 1..text.length).firstOrNull { mask[it] } ?: text.length
                    assertEquals(next, Graphemes.next(text, i), "next $i of ${describe(text)}")
                }
                val previous = if (i == 0) 0 else (i - 1 downTo 0).first { mask[it] }
                assertEquals(previous, Graphemes.previous(text, i), "previous $i of ${describe(text)}")
            }
        }
    }

    @Test
    fun `out of range indexes are clamped, never thrown at`() {
        val text = "a👍🏽b"
        assertEquals(0, Graphemes.floor(text, -5))
        assertEquals(text.length, Graphemes.floor(text, 99))
        assertEquals(0, Graphemes.ceil(text, -5))
        assertEquals(text.length, Graphemes.ceil(text, 99))
        assertEquals(0, Graphemes.previous(text, -1))
        assertEquals(text.length, Graphemes.next(text, 99))
        assertFalse(Graphemes.isBoundary(text, -1))
        assertFalse(Graphemes.isBoundary(text, 99))
        assertTrue(booleanArrayOf(true).contentEquals(Graphemes.boundaries("")))
        assertEquals(0, Graphemes.next("", 0))
        assertEquals(0, Graphemes.previous("", 0))
    }

    @Test
    fun `a letter with a pile of combining marks is one grapheme however the window falls`() {
        // The window is 32 units: a mark cut by its edge (half a surrogate pair) must not end the grapheme early.
        for (marks in 0..80) {
            for (tail in listOf("́", "🏽", "️", "‍👍")) {
                val text = "e" + "́".repeat(marks) + tail + "x"
                val mask = reference(text)
                assertTrue(mask.contentEquals(Graphemes.boundaries(text)), "marks $marks tail ${describe(tail)}")
            }
        }
    }

    @Test
    fun `a base followed by a skin tone is one grapheme wherever it sits in a window`() {
        for (pad in 0..70) {
            val text = "x".repeat(pad) + "👍🏽" + "y"
            assertTrue(reference(text).contentEquals(Graphemes.boundaries(text)), "pad $pad")
            val text2 = "é".repeat(pad) + "👨‍👩‍👧‍👦" + "y"
            assertTrue(reference(text2).contentEquals(Graphemes.boundaries(text2)), "pad $pad family")
        }
    }

    @Test
    fun `a hundred skin tones, joiners or regional indicators in a row are handled quickly and stay in step`() {
        val inputs = listOf(
            "👍" + "🏽".repeat(100), "👨" + "‍".repeat(100), "‍".repeat(100), "🏽".repeat(100),
            "🇫".repeat(101), "👨" + "‍👨".repeat(100), "x" + "́".repeat(3000),
        )
        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            for (text in inputs) {
                assertTrue(reference(text).contentEquals(Graphemes.boundaries(text)), describe(text.take(20)))
                // The walk by next() visits the same boundaries as the mask, and always moves forward.
                var i = 0
                var steps = 0
                while (i < text.length) {
                    val n = Graphemes.next(text, i)
                    assertTrue(n > i)
                    i = n
                    steps++
                }
                assertEquals(reference(text).count { it } - 1, steps)
            }
        }
    }

    @Test
    fun `a chain of emoji joined by ZWJ is cut into groups of five emoji, real sequences never`() {
        val chain = (0 until 100).joinToString("‍") { String(Character.toChars(0x1F600 + it % 60)) }
        val starts = (0 until chain.length).filter { Graphemes.isBoundary(chain, it) }
        // Each group is five emoji and four joiners, then the joiner that linked it to the next one: 2 x 5 + 5 units.
        assertEquals(List(20) { it * 15 }, starts)
        assertTrue(starts.all { Character.isExtendedPictographic(chain.codePointAt(it)) }, "every group starts with an emoji")
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val long = (0 until 2000).joinToString("‍") { "😀" }
            var i = long.length
            while (i > 0) i = Graphemes.previous(long, i)
        }
        for (real in listOf("👨‍👩‍👧‍👦", "🧑🏻‍❤️‍💋‍🧑🏼", "🏴‍☠️", "👩🏽‍💻", "🧑🏻‍🤝‍🧑🏼", "🏳️‍⚧️")) {
            assertEquals(real.length, Graphemes.next(real + "x", 0), real)
        }
    }

    @Test
    fun `walking back and forth over a very long run of emoji stays linear enough`() {
        // 4000 flags' worth of code points (the editing limit), no ASCII anchor anywhere: every floor() starts at 0.
        val text = "😀".repeat(2000)
        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            var i = text.length
            while (i > 0) i = Graphemes.previous(text, i)
            assertEquals(0, i)
        }
    }

    @Test
    fun `malformed text never throws and never gets a boundary inside a valid surrogate pair`() {
        val random = Random(99)
        val junk = listOf("\uD83D", "\uDC4D", "a", "👍", "‍", "️", "🇫", "🏽", "́", "\r", "\n")
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            repeat(3000) {
                val text = buildString { repeat(random.nextInt(0, 12)) { append(junk[random.nextInt(junk.size)]) } }
                val mask = Graphemes.boundaries(text)
                assertTrue(mask[0] && mask[text.length])
                for (i in 1 until text.length) {
                    if (Character.isHighSurrogate(text[i - 1]) && Character.isLowSurrogate(text[i])) {
                        assertFalse(mask[i], "inside a pair, ${describe(text)} at $i")
                    }
                }
                assertTrue(reference(text).contentEquals(mask), "text ${describe(text)}")
            }
        }
    }

    @Test
    fun `CR LF is one grapheme, and Latin text and French punctuation break everywhere`() {
        assertEquals(2, Graphemes.next("\r\nx", 0))
        assertEquals(1, Graphemes.next("\n\rx", 0))
        val text = "Été : « salut » ! ça ? 12,5 € — ok…"
        assertTrue(Graphemes.boundaries(text).all { it })
        // A decomposed accent is joined to its letter.
        assertFalse(Graphemes.isBoundary("é", 1))
    }
}

package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.client.smooth.Graphemes
import fr.bde_teknantes.lnb.phone.client.smooth.GraphemesTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.random.Random

/**
 * [TextState] editing by grapheme: every operation, over every kind of sequence and over mixed text, and over what a
 * player or a clipboard can send that is not well formed. Nothing may throw, split a surrogate pair, or loop.
 *
 * Invisible characters are written as code points (see [cp]) so that the source stays readable.
 */
class TextStateGraphemeTest {
    companion object {
        /** A string from code points. */
        fun cp(vararg codePoints: Int) = String(codePoints, 0, codePoints.size)

        const val ZWJ = 0x200D
        const val VS16 = 0xFE0F
        const val KEYCAP = 0x20E3
        const val TONE = 0x1F3FD
        const val THUMB = 0x1F44D
        val FAMILY = cp(0x1F468, ZWJ, 0x1F469, ZWJ, 0x1F467)
        val SKIN = cp(THUMB, TONE)
        val FR = cp(0x1F1EB, 0x1F1F7)
        val DE = cp(0x1F1E9, 0x1F1EA)
    }

    private val sequences = listOf(
        SKIN to "skin tone",
        FAMILY to "family",
        cp(0x2764, VS16, ZWJ, 0x1F525) to "heart on fire",
        cp(0x1F3F3, VS16, ZWJ, 0x1F308) to "rainbow flag",
        FR to "flag",
        cp('1'.code, VS16, KEYCAP) to "keycap",
        cp('#'.code, VS16, KEYCAP) to "hash keycap",
        cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F) to "England",
        cp(0x2764, VS16) to "emoji heart",
        cp(0x1F600) to "plain",
        cp('x'.code, 0x301, 0x302) to "letter with two accents that don't compose",
        cp(0x1F469, TONE, ZWJ, 0x1F4BB) to "toned worker",
    )
    private val mixed = "été $FR $SKIN!"

    private fun describe(text: String) = text.map { "%04X".format(it.code) }.joinToString(" ")

    /** What every state must satisfy after every operation. */
    private fun check(s: TextState, context: String = "") {
        val t = s.text
        assertTrue(s.caret in 0..t.length && s.anchor in 0..t.length, "range $s $context")
        assertTrue(Graphemes.isBoundary(t, s.caret), "caret inside a grapheme: ${describe(t)} caret ${s.caret} $context")
        assertTrue(Graphemes.isBoundary(t, s.anchor), "anchor inside a grapheme: ${describe(t)} anchor ${s.anchor} $context")
        for (i in 1 until t.length) {
            if (Character.isHighSurrogate(t[i - 1]) && Character.isLowSurrogate(t[i])) {
                assertTrue(i != s.caret && i != s.anchor, "inside a surrogate pair: $s")
            }
        }
        assertTrue(TextLimits.normalize(t) == t, "stored text is NFC $context")
    }

    // ---- Caret moves ----

    @Test
    fun `left and right step over each sequence whole, alone and in mixed text`() {
        for ((sequence, name) in sequences) {
            val s = TextState("a${sequence}b")
            s.moveTo(0)
            s.moveRight()
            assertEquals(1, s.caret)
            s.moveRight()
            assertEquals(1 + sequence.length, s.caret, name)
            s.moveRight()
            assertEquals(s.text.length, s.caret)
            s.moveLeft()
            s.moveLeft()
            assertEquals(1, s.caret, name)
            check(s)
        }
        // The mixed sentence, one step at a time, always lands on the reference boundaries.
        val s = TextState(mixed)
        s.moveTo(0)
        val steps = ArrayList<Int>()
        repeat(mixed.length) {
            val before = s.caret
            s.moveRight()
            if (s.caret != before) steps += s.caret
        }
        val reference = GraphemesTest.reference(mixed)
        assertEquals(reference.indices.filter { reference[it] && it > 0 }, steps)
        // é, t, é, space, flag, space, thumb with tone, !
        assertEquals(8, steps.size)
    }

    @Test
    fun `Shift selection extends by a whole sequence either way`() {
        for ((sequence, name) in sequences) {
            val s = TextState("a${sequence}b")
            s.moveTo(1)
            s.moveRight(select = true, word = false)
            assertEquals(1, s.anchor)
            assertEquals(1 + sequence.length, s.caret, name)
            assertEquals(sequence, s.selectedText, name)
            s.moveTo(1 + sequence.length)
            s.moveLeft(select = true, word = false)
            assertEquals(sequence, s.selectedText, name)
            check(s)
        }
    }

    @Test
    fun `a selection collapses to its start or end by an arrow`() {
        val s = TextState("a${FAMILY}b")
        s.selectAll()
        s.moveLeft()
        assertEquals(0, s.caret)
        s.selectAll()
        s.moveRight()
        assertEquals(s.text.length, s.caret)
    }

    @Test
    fun `word moves treat an emoji as one character and never stop inside one`() {
        val s = TextState("salut $FAMILY les $FR$DE amis")
        val stops = ArrayList<Int>()
        s.moveTo(0)
        repeat(20) {
            val before = s.caret
            s.moveRight(select = false, word = true)
            check(s)
            if (s.caret != before) stops += s.caret
        }
        assertEquals(s.text.length, s.caret)
        assertEquals("salut ".length, stops.first())
        assertTrue(stops.all { Graphemes.isBoundary(s.text, it) })
        repeat(20) {
            val before = s.caret
            s.moveLeft(select = false, word = true)
            check(s)
            assertTrue(s.caret <= before)
        }
        assertEquals(0, s.caret)
    }

    @Test
    fun `moveTo into a sequence snaps back to its start, with or without Shift`() {
        for ((sequence, name) in sequences.filter { it.first.length > 2 }) {
            val s = TextState("a${sequence}b")
            for (inside in 2 until 1 + sequence.length) {
                if (Graphemes.isBoundary(s.text, inside)) continue
                s.moveTo(inside)
                assertEquals(1, s.caret, "$name at $inside")
                s.moveTo(inside, select = true)
                assertEquals(1, s.caret, name)
            }
            s.moveTo(0)
            s.moveTo(s.text.length, select = true)
            assertEquals(s.text, s.selectedText)
            s.moveTo(-10)
            assertEquals(0, s.caret)
            s.moveTo(1000)
            assertEquals(s.text.length, s.caret)
            check(s)
        }
    }

    @Test
    fun `a selection whose one end was set inside a sequence stops before the sequence`() {
        val s = TextState("a${FAMILY}b")
        s.moveTo(0)
        s.moveTo(4, select = true)
        assertEquals("a", s.selectedText)
        // Dragging on past it takes it whole.
        s.moveTo(1 + FAMILY.length, select = true)
        assertEquals("a$FAMILY", s.selectedText)
        check(s)
    }

    @Test
    fun `double click selects the emoji alone between spaces, and a run of emoji together`() {
        val s = TextState("bonjour $FAMILY monde")
        s.selectWordAt(8 + 3)
        assertEquals(FAMILY, s.selectedText)
        val u = TextState("$FR$DE$SKIN")
        u.selectWordAt(2)
        assertEquals(u.text, u.selectedText)
        check(u)
    }

    @Test
    fun `an emoji next to a word acts like punctuation there, and a click inside it is the click at its start`() {
        // Not decided by the developer: what the code does is that the word wins at the boundary, as for "!".
        val t = TextState("hey${SKIN}ça")
        val twin = TextState("hey!ça")
        t.selectWordAt(1)
        assertEquals("hey", t.selectedText)
        t.selectWordAt(5)
        assertEquals("hey", t.selectedText)
        t.selectWordAt(3 + SKIN.length)
        twin.selectWordAt(4)
        assertEquals("ça", t.selectedText)
        assertEquals("ça", twin.selectedText)
        check(t)
    }

    // ---- Deleting ----

    @Test
    fun `Backspace and Delete remove one whole sequence, in the middle of mixed text`() {
        for ((sequence, name) in sequences) {
            val back = TextState("é${sequence}!")
            back.moveTo(1 + sequence.length)
            back.deleteBackward()
            assertEquals("é!", back.text, name)
            assertEquals(1, back.caret)
            val forward = TextState("é${sequence}!")
            forward.moveTo(1)
            forward.deleteForward()
            assertEquals("é!", forward.text, name)
            assertEquals(1, forward.caret)
            check(back)
            check(forward)
        }
        val s = TextState(mixed)
        s.moveTo(mixed.length - 1)
        s.deleteBackward()
        assertEquals("été $FR !", s.text)
        s.deleteBackward()
        assertEquals("été $FR!", s.text)
        s.deleteBackward()
        assertEquals("été !", s.text)
    }

    @Test
    fun `Backspace removes a ten-part sequence in one press, and an emptied text stays empty`() {
        val kiss = cp(0x1F9D1, 0x1F3FB, ZWJ, 0x2764, VS16, ZWJ, 0x1F48B, ZWJ, 0x1F9D1, 0x1F3FC)
        val s = TextState(kiss)
        s.deleteBackward()
        assertEquals("", s.text)
        s.deleteBackward()
        s.deleteForward()
        assertEquals("", s.text)
        assertEquals(0, s.caret)
    }

    @Test
    fun `word delete removes whole sequences with the word, never half of one`() {
        val s = TextState("salut $FAMILY les amis")
        s.moveTo("salut $FAMILY".length)
        s.deleteBackward(word = true)
        assertEquals("salut  les amis", s.text)
        check(s)
        val t = TextState("salut $FAMILY les amis")
        t.moveTo("salut ".length)
        t.deleteForward(word = true)
        assertEquals("salut les amis", t.text)
        check(t)
    }

    // ---- Replacing, cutting, pasting ----

    @Test
    fun `replacing a selection takes whole sequences, and typing over a selection of one is one emoji`() {
        val s = TextState("a${FAMILY}b")
        s.moveTo(1)
        s.moveTo(5, select = true) // inside the family: snaps back before it, nothing selected
        assertFalse(s.hasSelection)
        s.insert("X")
        assertEquals("aX${FAMILY}b", s.text)
        check(s)
        val t = TextState("a${FAMILY}b")
        t.selectAll()
        t.insert(cp(THUMB))
        assertEquals(cp(THUMB), t.text)
        assertEquals(2, t.caret)
    }

    @Test
    fun `cut then paste round-trips every sequence`() {
        for ((sequence, name) in sequences) {
            val s = TextState("a${sequence}b")
            s.moveTo(1)
            s.moveRight(select = true, word = false)
            val clip = s.selectedText
            s.insert("")
            assertEquals("ab", s.text)
            s.insert(clip)
            assertEquals("a${sequence}b", s.text, name)
            assertEquals(1 + sequence.length, s.caret, name)
            check(s)
        }
    }

    @Test
    fun `typing a tone, a selector or a keycap mark after a base merges and the caret ends after the merge`() {
        val s = TextState("x")
        s.insert(cp(THUMB))
        s.insert(cp(TONE))
        assertEquals("x$SKIN", s.text)
        assertEquals(s.text.length, s.caret)
        check(s)
        // Regional indicators one at a time, as an emoji panel sends them.
        val f = TextState()
        f.insert(cp(0x1F1EB))
        f.insert(cp(0x1F1F7))
        assertEquals(4, f.caret)
        f.insert(cp(0x1F1E9))
        assertEquals(6, f.caret)
        f.insert(cp(0x1F1EA))
        assertEquals(8, f.caret)
        assertEquals("$FR$DE", f.text)
        check(f)
        val k = TextState()
        k.insert("1")
        k.insert(cp(VS16))
        k.insert(cp(KEYCAP))
        assertEquals(cp('1'.code, VS16, KEYCAP), k.text)
        assertEquals(3, k.caret)
        check(k)
    }

    @Test
    fun `a joiner typed between two emoji merges them and the caret ends after the merged sequence`() {
        val s = TextState(cp(0x1F468, 0x1F469))
        s.moveTo(2)
        s.insert(cp(ZWJ))
        assertEquals(cp(0x1F468, ZWJ, 0x1F469), s.text)
        assertEquals(s.text.length, s.caret)
        check(s)
        // Inserting in front of an already joined pair splits nothing.
        val joined = cp(0x1F468, ZWJ, 0x1F469)
        val t = TextState("a${joined}b")
        t.moveTo(1)
        t.insert("X")
        assertEquals("aX${joined}b", t.text)
        assertEquals(2, t.caret)
        // A caret set inside a tone pair snaps before it: text typed there goes before the base.
        val u = TextState(SKIN)
        u.moveTo(2)
        assertEquals(0, u.caret)
        u.insert("!")
        assertEquals("!$SKIN", u.text)
    }

    // ---- Malformed input ----

    @Test
    fun `lone surrogates are filtered out of what is typed, never stored`() {
        val s = TextState()
        s.insert("a\uD83Db")
        assertEquals("ab", s.text)
        s.insert("\uDC4D")
        assertEquals("ab", s.text)
        val t = TextState("x\uD83D")
        assertEquals("x", t.text)
        t.text = "\uDC4Dy👍"
        assertEquals("y${cp(THUMB)}", t.text)
        check(t)
    }

    @Test
    fun `a lone joiner, selector, tone, indicator or mark at the start or end is edited without trouble`() {
        for (junk in listOf(cp(ZWJ), cp(VS16), cp(KEYCAP), cp(TONE), cp(0x1F1EB), cp(0x301))) {
            val s = TextState(junk + "abc" + junk)
            check(s, describe(junk))
            s.moveTo(0)
            repeat(8) { s.moveRight(); check(s, describe(junk)) }
            repeat(8) { s.moveLeft(select = true, word = false); check(s, describe(junk)) }
            s.deleteBackward(); check(s, describe(junk))
            s.selectAll(); s.deleteForward(); check(s, describe(junk))
            assertEquals("", s.text)
        }
    }

    @Test
    fun `three regional indicators are a flag and a lone letter, five are two flags and a letter`() {
        val s = TextState(FR + cp(0x1F1E9))
        s.moveTo(s.text.length)
        s.deleteBackward()
        assertEquals(FR, s.text)
        s.deleteBackward()
        assertEquals("", s.text)
        val t = TextState(FR + DE + cp(0x1F1EE))
        t.moveTo(4)
        t.deleteForward()
        assertEquals(FR + cp(0x1F1EE), t.text)
        check(t)
    }

    @Test
    fun `a hundred skin tones, joiners or marks in a row are one deletion and take no time`() {
        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            for (tail in listOf(cp(TONE), cp(ZWJ), cp(0x301), cp(ZWJ, THUMB), cp(VS16))) {
                val s = TextState("a" + cp(THUMB) + tail.repeat(100) + "b")
                s.moveTo(1)
                s.moveRight()
                assertEquals(s.text.length - 1, s.caret, "100 of ${describe(tail)}")
                s.deleteBackward()
                assertEquals("ab", s.text)
                s.insert("e" + cp(0x301).repeat(100))
                s.moveLeft()
                check(s)
            }
        }
    }

    @Test
    fun `a letter with many combining marks is one step and one deletion`() {
        val marks = cp(0x301, 0x302, 0x303, 0x304, 0x305, 0x306, 0x307, 0x308).repeat(10)
        val s = TextState("xa${marks}y")
        s.moveTo(1)
        s.moveRight()
        assertEquals(s.text.length - 1, s.caret)
        s.deleteBackward()
        assertEquals("xy", s.text)
    }

    @Test
    fun `a text that is all joiners, tones or selectors, or empty, takes every operation`() {
        for (text in listOf("", cp(ZWJ).repeat(50), cp(TONE).repeat(50), cp(VS16).repeat(50))) {
            val s = TextState(text)
            s.selectWordAt(0)
            s.selectWordAt(s.text.length)
            s.moveLeft(select = true, word = true)
            s.moveRight(select = true, word = true)
            s.deleteBackward(word = true)
            s.deleteForward(word = true)
            s.insert(text)
            s.selectAll()
            s.insert("")
            check(s, describe(text))
        }
    }

    // ---- Long texts ----

    @Test
    fun `word moves, word deletion and double click stay quick on the longest text, made of emoji`() {
        // 4000 characters is the editing limit. A run of emoji has no ASCII to anchor a boundary search on.
        for (text in listOf("😀".repeat(2000), "😀 ".repeat(1300), FR.repeat(1000), "a😀".repeat(1500), FAMILY.repeat(800))) {
            val s = TextState(text)
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                s.moveTo(s.text.length)
                repeat(5) { s.moveLeft(select = false, word = true) }
                s.selectWordAt(s.text.length / 2)
                s.moveTo(s.text.length)
                s.deleteBackward(word = true)
                repeat(200) { s.moveLeft() }
                s.moveTo(s.text.length / 2)
                s.insert("x")
                s.moveTo(0)
                repeat(200) { s.moveRight(select = true, word = false) }
                s.selectAll()
                s.insert("")
            }
            check(s)
        }
    }

    // ---- Fuzz ----

    @Test
    fun `random editing never breaks the invariants`() {
        val random = Random(31337)
        val fragments = GraphemesTest.PIECES + listOf(cp(ZWJ, THUMB), SKIN.repeat(5), "salut le monde ", cp('e'.code, 0x301))
        assertTimeoutPreemptively(Duration.ofSeconds(60)) {
            repeat(300) { round ->
                val s = TextState(if (round % 3 == 0) "" else mixed, multiline = true, maxLength = 40)
                repeat(80) {
                    val before = s.text
                    val op = random.nextInt(14)
                    val shift = random.nextBoolean()
                    val word = random.nextInt(4) == 0
                    when (op) {
                        0 -> s.moveLeft(shift, word)
                        1 -> s.moveRight(shift, word)
                        2 -> s.moveTo(random.nextInt(-2, s.text.length + 3), shift)
                        3 -> s.selectWordAt(random.nextInt(-2, s.text.length + 3))
                        4 -> s.deleteBackward(word)
                        5 -> s.deleteForward(word)
                        6, 7, 8 -> s.insert(fragments[random.nextInt(fragments.size)])
                        9 -> s.selectAll()
                        10 -> s.insert("")
                        11 -> s.moveTo(0, shift)
                        12 -> s.moveTo(s.text.length, shift)
                        13 -> s.text = buildString { repeat(random.nextInt(6)) { append(fragments[random.nextInt(fragments.size)]) } }
                    }
                    check(s, "after op $op on ${describe(before)}")
                    assertTrue(s.length <= s.maxLength, "length ${s.length}")
                }
            }
        }
    }
}

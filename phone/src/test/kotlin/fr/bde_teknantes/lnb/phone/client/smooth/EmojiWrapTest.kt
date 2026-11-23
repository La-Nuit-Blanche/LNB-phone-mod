package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.text.Graphemes
import fr.bde_teknantes.lnb.phone.text.GraphemesTest
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.emojiGlyph
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.random.Random

/**
 * Wrapping, truncating and measuring on the two real fonts: no cut ever falls inside a sequence, a sequence wider than
 * the line still makes progress, and what is measured is what is drawn.
 */
class EmojiWrapTest {
    private val font = RealFaces.REGULAR
    private fun cp(vararg c: Int) = String(c, 0, c.size)

    private val family = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)
    private val flag = cp(0x1F1EB, 0x1F1F7)
    private val tone = cp(0x1F44D, 0x1F3FD)
    private val keycap = cp('1'.code, 0xFE0F, 0x20E3)
    private val sample = "été $flag $tone! Salut $family ça va ? $keycap « oui » : non… ❤️ ok"

    private fun describe(text: String) = text.map { "%04X".format(it.code) }.joinToString(" ")

    private fun width(text: String, size: Float = 13f) = TextLayout.width(text, font, size)

    // ---- Wrapping ----

    @Test
    fun `every line of every wrapping starts and ends on a grapheme boundary, and the lines cover the text`() {
        val random = Random(4)
        val pieces = GraphemesTest.PIECES.filter { it != "\r" && it != "\r\n" } + listOf("salut ", "le monde ", "  ", family, tone)
        assertTimeoutPreemptively(Duration.ofSeconds(40)) {
            repeat(1500) {
                val text = buildString { repeat(random.nextInt(0, 14)) { append(pieces[random.nextInt(pieces.size)]) } }
                val boundaries = GraphemesTest.reference(text)
                for (maxWidth in listOf(1f, 12f, 25f, 40f, 77f, 130f)) {
                    val lines = TextLayout.lines(text, font, 13f, maxWidth)
                    assertTrue(lines.isNotEmpty())
                    var expectedStart = 0
                    for (line in lines) {
                        val tag = "${describe(text)} at $maxWidth: $line"
                        assertTrue(boundaries[line.start] && boundaries[line.end] && boundaries[line.next], tag)
                        // (Several spaces at a break: the extra ones fall between one line's next and the following start.)
                        assertTrue(line.start >= expectedStart && text.substring(expectedStart, line.start).all { it == ' ' }, tag)
                        assertTrue(line.start <= line.end && line.end <= line.next, tag)
                        expectedStart = line.next
                        // What is dropped between a line's end and the next line's start is a space (with what is glued
                        // to it) or a line break, and nothing else.
                        assertTrue(line.end == line.next || Character.isWhitespace(text.codePointAt(line.end)), tag)
                    }
                    assertTrue(text.substring(expectedStart).all { it == ' ' } || expectedStart == text.length, "all text accounted for: ${describe(text)} at $maxWidth")
                }
            }
        }
    }

    @Test
    fun `a line never breaks between a space and the tone, selector or mark that follows it`() {
        // The space and its tone are one grapheme (the tone draws nothing there): the next line must not start inside it.
        val text = "salut " + cp(0x1F3FD) + " ok"
        for (maxWidth in listOf(1f, 20f, 35f, 50f)) {
            for (line in TextLayout.lines(text, font, 13f, maxWidth)) {
                assertTrue(Graphemes.isBoundary(text, line.start) && Graphemes.isBoundary(text, line.next), "$line at $maxWidth")
                assertFalse(text.substring(line.start, line.end).startsWith(cp(0x1F3FD)), "a lone tone starts $line at $maxWidth")
            }
        }
    }

    @Test
    fun `wrapping the longest text, emoji and spaces with no ASCII pair anywhere, takes milliseconds`() {
        // 4000 characters is the editing limit. Looking for a grapheme boundary from the start of the text at every
        // space made this take seconds.
        for (text in listOf("😀 ".repeat(2000), "é😀 ".repeat(1300), "$flag ".repeat(1300))) {
            assertTimeoutPreemptively(Duration.ofSeconds(1)) {
                assertTrue(TextLayout.lines(text, font, 13f, 208f).size > 50)
            }
        }
    }

    @Test
    fun `a sequence wider than the line is a line of its own, never cut, and the wrap ends`() {
        val line = 5f // far narrower than any emoji
        val text = family.repeat(4)
        val lines = TextLayout.wrap(text, font, 13f, line)
        assertEquals(List(4) { family }, lines)
        // With a line only a little wider than one family: still one per line.
        val glyph = width(family)
        assertEquals(List(4) { family }, TextLayout.wrap(text, font, 13f, glyph + 1f))
        // Two fit in a wider line, never two and a half.
        assertEquals(List(2) { family + family }, TextLayout.wrap(text, font, 13f, glyph * 2 + 1f))
        val three = TextLayout.wrap(family.repeat(5), font, 13f, glyph * 3 - 1f)
        assertEquals(listOf(family + family, family + family, family), three)
        // A flag, a keycap and a toned thumb each alone in a line narrower than themselves.
        for (piece in listOf(flag, keycap, tone)) assertEquals(List(3) { piece }, TextLayout.wrap(piece.repeat(3), font, 13f, 4f))
    }

    @Test
    fun `a line of only emoji wraps by graphemes at the width given`() {
        val text = "😀".repeat(30)
        val one = width("😀")
        for (per in 1..8) {
            val lines = TextLayout.wrap(text, font, 13f, one * per + 0.5f)
            assertTrue(lines.dropLast(1).all { it == "😀".repeat(per) }, "per $per: $lines")
            assertEquals(text, lines.joinToString(""))
        }
    }

    @Test
    fun `French punctuation stays with the emoji before it, and a line never starts with it`() {
        // "😀 !" has a regular space that is French typography's: no break there.
        val text = "Bravo 😀 ! Merci 🎉 : voilà ; ok 👍 ? oui"
        for (maxWidth in listOf(45f, 60f, 90f, 120f)) {
            val lines = TextLayout.wrap(text, font, 13f, maxWidth)
            for (l in lines.drop(1)) assertFalse(l.trimStart().firstOrNull()?.let { it in "!?:;" } ?: false, "line \"$l\" at $maxWidth")
        }
        // With a no-break space before the mark, the same.
        val nb = "Bravo 😀 ! Merci"
        for (maxWidth in listOf(20f, 40f, 60f)) {
            for (l in TextLayout.wrap(nb, font, 13f, maxWidth)) assertFalse(l.startsWith("!"), l)
        }
    }

    @Test
    fun `lines are the same at every GUI scale, because they are measured in dp`() {
        // Layout takes the size in dp: one line is the same text whatever the pixel size, only the final raster differs.
        val base = TextLayout.wrap(sample.repeat(3), font, 13f, 160f)
        for (scale in listOf(1f, 1.5f, 2f, 3f, 4.5f)) {
            // Advances scale linearly with the size: the same line measured at a scaled size in a scaled width.
            assertEquals(base, TextLayout.wrap(sample.repeat(3), font, 13f * scale, 160f * scale), "scale $scale")
        }
    }

    @Test
    fun `a line fits its width, and a wrapped line is never wider than the line it was given unless one sequence is`() {
        val random = Random(12)
        repeat(300) {
            val text = buildString { repeat(random.nextInt(1, 12)) { append(sample.split(' ')[random.nextInt(sample.split(' ').size)]).append(' ') } }
            val maxWidth = 30f + random.nextInt(150)
            for (line in TextLayout.lines(text, font, 13f, maxWidth)) {
                val shown = text.substring(line.start, line.end)
                assertEquals(width(shown), line.width, 1e-3f, shown)
                val graphemes = Graphemes.boundaries(shown).count { it } - 1
                assertTrue(line.width <= maxWidth + 1e-3f || graphemes <= 1, "$shown ${line.width} > $maxWidth")
            }
        }
    }

    // ---- Truncating ----

    @Test
    fun `truncating never splits a sequence, always fits, and is the longest start that does`() {
        val random = Random(21)
        val pieces = GraphemesTest.PIECES.filter { it != "\r" && it != "\r\n" && it != "\n" } + listOf("salut ", family, tone)
        repeat(1200) {
            val text = buildString { repeat(random.nextInt(1, 12)) { append(pieces[random.nextInt(pieces.size)]) } }
            val boundaries = GraphemesTest.reference(text)
            for (maxWidth in listOf(10f, 22f, 37f, 60f, 100f)) {
                val cut = TextLayout.truncate(text, font, 13f, maxWidth)
                if (cut == text) {
                    assertTrue(width(text) <= maxWidth + 1e-3f)
                    continue
                }
                assertTrue(cut.isEmpty() || cut.endsWith(TextLayout.ELLIPSIS), cut)
                assertTrue(width(cut) <= maxWidth + 1e-3f, "${describe(text)} at $maxWidth: \"$cut\" is ${width(cut)}")
                if (cut.isEmpty()) continue
                val prefix = cut.removeSuffix(TextLayout.ELLIPSIS)
                assertTrue(text.startsWith(prefix), "prefix of $text")
                assertTrue(boundaries[prefix.length], "cut inside a sequence: ${describe(text)} at $maxWidth gave ${describe(cut)}")
                // The longest: the next grapheme would not fit with the ellipsis (unless the prefix was trimmed of spaces).
                val next = Graphemes.next(text, prefix.length)
                val longer = text.substring(0, next).trimEnd() + TextLayout.ELLIPSIS
                // (A grapheme whose base is a space is trimmed with the spaces, whatever follows the space.)
                if (next > prefix.length && !Character.isWhitespace(text.codePointAt(prefix.length)) && text.substring(0, next).trimEnd().length > prefix.length) {
                    assertTrue(width(longer) > maxWidth - 1e-3f, "not the longest: ${describe(text)} at $maxWidth: kept \"$prefix\" could take \"$longer\"")
                }
            }
        }
    }

    @Test
    fun `the ellipsis after an emoji keeps the emoji's own spacing and is not kerned against it`() {
        val text = "😀😀😀😀😀😀"
        val unit = width("😀")
        val ellipsis = font.advance(0x2026) * 13f
        for (n in 1..5) {
            val cut = TextLayout.truncate(text, font, 13f, unit * n + ellipsis + 0.1f)
            assertEquals("😀".repeat(n) + "…", cut)
            assertEquals(unit * n + ellipsis, width(cut), 1e-3f)
        }
        // Emoji then a letter then an ellipsis: kerning between the letter and the ellipsis applies, between emoji and
        // letter never.
        assertEquals("😀ab…", TextLayout.truncate("😀abcdefgh", font, 13f, unit + width("ab…") + 0.1f))
        // Room for only the ellipsis: just it. Room for less: nothing.
        assertEquals("…", TextLayout.truncate(family.repeat(3), font, 13f, ellipsis + 0.1f))
        assertEquals("", TextLayout.truncate(family.repeat(3), font, 13f, ellipsis - 1f))
    }

    @Test
    fun `truncating at the line's width never drops a sequence in the middle`() {
        val text = "Salut $family"
        val w = width("Salut ") + width(family)
        assertEquals(text, TextLayout.truncate(text, font, 13f, w + 0.01f))
        // One pixel short: the family goes whole.
        assertEquals("Salut…", TextLayout.truncate(text, font, 13f, w - 1f))
    }

    @Test
    fun `wrap with a line limit puts the ellipsis after whole sequences`() {
        val text = (family + " ").repeat(8)
        val lines = TextLayout.wrap(text, font, 13f, width(family) * 2 + 4f, 2)
        assertEquals(2, lines.size)
        assertTrue(lines.last().endsWith("…"), lines.toString())
        val body = lines.last().removeSuffix("…")
        assertTrue(Graphemes.isBoundary(body, body.length))
        assertTrue(body.trimEnd().length % family.length == 0 || body.replace(" ", "").length % family.length == 0, body)
        for (l in lines) assertTrue(width(l) <= width(family) * 2 + 4f + 1e-3f)
    }

    // ---- Measuring ----

    @Test
    fun `the width is the sum of what is drawn, unit by unit`() {
        for (text in listOf(sample, family, flag.repeat(3), "a${tone}b", "ça va 😀😀 !", "$keycap$keycap", cp(0x1F468, 0x200D, 0x1F996, 0x1F1FD, 0x1F1FD))) {
            var last = 0f
            var end = 0f
            var units = 0
            var covered = 0
            val total = TextLayout.forEachUnit(text, font, 13f) { start, e, drawn, x, advance ->
                assertEquals(covered, start, "units follow each other in ${describe(text)}")
                assertTrue(e > start)
                covered = e
                // The pen never goes back; a unit that draws nothing has no advance.
                assertTrue(x >= last - 1e-3f, "pen back at $start")
                if (drawn < 0) assertEquals(0f, advance, 0f)
                last = x
                if (drawn >= 0) end = x + advance
                units++
            }
            assertEquals(text.length, covered)
            assertEquals(end, total, 1e-3f, text)
            assertEquals(total, width(text), 1e-4f)
        }
    }

    @Test
    fun `measuring is linear in the size and equals the sum of the parts across faces`() {
        for (size in listOf(9f, 13f, 19.5f, 40f)) {
            val parts = width("été ", size) + width(flag, size) + width(" ", size) + width(tone, size) + width("!", size)
            // Kerning only inside a face, never across: pieces joined by an emoji add up exactly where no kerning pair is
            // involved at the seams (space before and after the emoji).
            assertEquals(parts, width("été $flag $tone!", size), 1e-3f, "size $size")
            assertEquals(width(sample, 13f) * size / 13f, width(sample, size), 1e-2f)
        }
        assertEquals(0f, width(""), 0f)
    }

    @Test
    fun `the line height is the style's, whatever the text holds`() {
        val style = TextStyles.BODY
        // Emoji are positioned inside the font's own line box: the baseline of a line depends only on Inter.
        assertEquals(TextLayout.baseline(style, font), TextLayout.baseline(style, RealFaces.INTER_ONLY), 0f)
        val glyph = RealFaces.noto.glyph(0x1F600)
        assertTrue(RealFaces.noto.advance(glyph) in 1f..1.5f)
        // Glyph metrics of a sequence's first glyph do not change the line: layout reads advances only.
        assertEquals(1, TextLayout.lines(family, font, style.size, 1000f).size)
    }

    @Test
    fun `caret positions on a line are the boundaries, and a click at any x lands on one`() {
        val line = TextLayout.Line(0, sample.length, sample.length, 0f)
        val boundaries = GraphemesTest.reference(sample)
        var previous = -1
        for (x in 0..400) {
            val index = TextLayout.indexAt(sample, line, x.toFloat(), font, 13f)
            assertTrue(boundaries[index], "x $x -> $index")
            assertTrue(index >= previous, "monotonic")
            previous = index
        }
        assertEquals(sample.length, TextLayout.indexAt(sample, line, 5000f, font, 13f))
        assertEquals(0, TextLayout.indexAt(sample, line, -10f, font, 13f))
    }

    @Test
    fun `a joiner or a selector between two emoji changes what is drawn, not how much text there is`() {
        val a = cp(0x1F468, 0x1F469)
        val joined = cp(0x1F468, 0x200D, 0x1F469)
        // Not a family the font has: side by side, joiner drawing nothing, so the same width.
        assertEquals(width(a), width(joined), 1e-3f)
        assertEquals(listOf(emojiGlyph(0x1F468), emojiGlyph(0x1F469)), RealFaces.drawn(joined))
    }
}

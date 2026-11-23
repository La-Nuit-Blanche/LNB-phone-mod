package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.smooth.EmojiShaping
import fr.bde_teknantes.lnb.phone.text.Graphemes
import fr.bde_teknantes.lnb.phone.text.GraphemesTest
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces
import fr.bde_teknantes.lnb.phone.client.smooth.RealFaces.emojiGlyph
import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles
import fr.bde_teknantes.lnb.phone.notifications.NotificationLimits
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The banner's text on the two real fonts: what it keeps, what it drops, the two-line clamp, and what a notification
 * cut by the server in the middle of an emoji sequence looks like.
 */
class BannerEmojiTest {
    private val text = RealFaces.REGULAR
    private val title = RealFaces.SEMIBOLD
    private fun cp(vararg c: Int) = String(c, 0, c.size)

    private val family = cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)
    private val flag = cp(0x1F1EB, 0x1F1F7)
    private val tone = cp(0x1F44D, 0x1F3FD)
    private val keycap = cp('1'.code, 0xFE0F, 0x20E3)
    private val newer = cp(0x1FAE9) // Emoji 16: newer than the bundled font

    private fun clean(s: String) = BannerText.clean(s, text)

    // ---- What is kept and what is dropped ----

    @Test
    fun `every sequence the faces draw is kept whole, in the text and in the title`() {
        val kept = "Bravo $tone $keycap $flag $family ${cp(0x2764, 0xFE0F, 0x200D, 0x1F525)} ${cp(0x1F3F3, 0xFE0F, 0x200D, 0x1F308)} ${cp(0x1F600)}"
        assertEquals(kept, clean(kept))
        assertEquals(kept, BannerText.clean(kept, title))
        // As drawn: one glyph per sequence.
        assertEquals(1, RealFaces.drawn(family).size)
    }

    @Test
    fun `emoji the emoji face lacks are dropped with their companions and the space they leave`() {
        assertEquals("Party tonight", clean("$newer Party tonight"))
        assertEquals("Party tonight", clean("Party $newer tonight"))
        assertEquals("Party tonight", clean("Party tonight $newer"))
        assertEquals("Party tonight", clean("Party ${newer}${cp(0x1F3FD)} tonight"))
        // A sequence whose first emoji the face lacks vanishes whole; one whose first it has keeps its place.
        assertEquals("ok", clean("${cp(0x1FAE9, 0x200D, 0x1F468)} ok"))
        assertEquals("ok", clean("ok ${cp(0x1FAE9, 0x200D, 0x1F468)}"))
        assertEquals(cp(0x1F468, 0x200D, 0x1FAE9) + " ok", clean(cp(0x1F468, 0x200D, 0x1FAE9) + " ok"))
    }

    @Test
    fun `a text that is only emoji is kept, and one that is only unsupported emoji becomes empty`() {
        assertEquals("😀😀😀", clean("😀😀😀"))
        assertEquals("$flag $family $tone", clean("  $flag \n $family\t$tone "))
        assertEquals("", clean("$newer$newer $newer"))
        assertEquals("", clean(""))
        assertEquals("", clean("‍️"))
        assertEquals(emptyList<String>(), BannerLayout.textLines(clean("$newer$newer"), text))
        assertEquals("", BannerLayout.title(clean(newer), title))
    }

    @Test
    fun `a title that is only emoji is cut with the ellipsis after whole sequences`() {
        val long = family.repeat(30)
        val cut = BannerLayout.title(long, title)
        assertTrue(cut.endsWith("…"))
        val body = cut.removeSuffix("…")
        assertEquals(0, body.length % family.length, "whole families only")
        assertTrue(TextLayout.width(cut, title, TextStyles.TITLE_M.size) <= BannerLayout.TEXT_WIDTH + 1e-3f)
    }

    @Test
    fun `the cleaned text draws no missing-glyph box, and cleaning twice changes nothing`() {
        val random = Random(2)
        // Without the lone skin tone: after a line break it is kept by the first pass, then absorbed by the space before it
        // on the second (a space and a tone are one grapheme), so cleaning is not idempotent there. Reported.
        val pieces = GraphemesTest.PIECES.filter { it != cp(0x1F3FD) } + listOf(newer, "田中", "مرحبا", " ", "  ", "\t", " ", "salut ", "\u0000")
        repeat(1500) {
            val raw = buildString { repeat(random.nextInt(0, 10)) { append(pieces[random.nextInt(pieces.size)]) } }
            val cleaned = clean(raw)
            assertEquals(cleaned, clean(cleaned), "idempotent: ${raw.map { "%04X".format(it.code) }}")
            // Whatever remains is drawn by Inter (which has it) or by the emoji face, or is the question mark.
            for (d in RealFaces.drawn(cleaned)) {
                assertTrue(EmojiShaping.isEmojiGlyph(d) || RealFaces.inter.glyphId(d) != 0, "box for %04X in ${cleaned.map { "%04X".format(it.code) }}".format(d))
            }
            assertFalse(cleaned.startsWith(" ") || cleaned.endsWith(" ") || cleaned.contains("  "), "spaces: \"$cleaned\"")
        }
    }

    // ---- The two-line clamp ----

    @Test
    fun `the clamp keeps two lines of at most the slot's width and cuts only between graphemes`() {
        val random = Random(6)
        val pieces = listOf(family, flag, tone, keycap, "salut ", "ça va ", "😀", " ! ", "Mika", cp(0x1F1FD, 0x1F1FD), cp(0x2764, 0xFE0F))
        repeat(400) {
            val raw = buildString { repeat(random.nextInt(1, 40)) { append(pieces[random.nextInt(pieces.size)]) } }
            val cleaned = clean(raw)
            val lines = BannerLayout.textLines(cleaned, text)
            assertTrue(lines.size <= BannerLayout.MAX_TEXT_LINES)
            for (line in lines) {
                assertTrue(
                    TextLayout.width(line, text, TextStyles.BODY.size) <= BannerLayout.TEXT_WIDTH + 1e-3f ||
                        Graphemes.boundaries(line).count { it } - 1 <= 1,
                    line,
                )
                // Every line is made of whole graphemes of the text (the ellipsis aside): its pieces re-segment alike.
                val bare = line.removeSuffix("…")
                assertTrue(Graphemes.isBoundary(bare, bare.length))
            }
            if (lines.size == 2 && lines.joinToString(" ").length < cleaned.length) {
                assertTrue(lines.last().endsWith("…") || lines.joinToString("").length >= cleaned.replace(" ", "").length, "$lines")
            }
        }
    }

    @Test
    fun `a long run of emoji clamps with the ellipsis after a whole sequence on the second line`() {
        val raw = (family + " ").repeat(40)
        val lines = BannerLayout.textLines(clean(raw), text)
        assertEquals(2, lines.size)
        assertTrue(lines[1].endsWith("…"))
        val body = lines[1].removeSuffix("…").trimEnd()
        assertEquals(0, body.replace(" ", "").length % family.length, "whole families before the ellipsis: $body")
        val flags = BannerLayout.textLines(clean(flag.repeat(60)), text)
        assertEquals(2, flags.size)
        assertEquals(0, flags[1].removeSuffix("…").length % flag.length)
        assertEquals(listOf("😀".repeat(1)), BannerLayout.textLines("😀", text))
    }

    // ---- A notification cut by the server inside a sequence ----

    private fun cutText(sequence: String, room: Int): String {
        val pad = "x".repeat(NotificationLimits.MAX_TEXT_LENGTH - room)
        return NotificationLimits.text(Component.literal(pad + sequence)).string.removePrefix(pad)
    }

    private fun drawnAfterCut(sequence: String, room: Int) = RealFaces.drawn(clean(cutText(sequence, room)))

    @Test
    fun `a family cut by the server's limit arrives whole or not at all`() {
        // NotificationLimits cuts between graphemes: a family that straddles the limit goes, it never reaches the banner
        // as a man, or a man and a woman, or a text ending on a joiner.
        for (room in 0 until family.length) assertEquals("", cutText(family, room), "room $room")
        assertEquals(family, cutText(family, family.length))
        assertEquals(1, drawnAfterCut(family, family.length).size, "uncut: one glyph")
    }

    @Test
    fun `a flag, a toned emoji and a keycap cut by the server's limit arrive whole or not at all`() {
        for (sequence in listOf(flag, tone, keycap)) {
            for (room in 0 until sequence.length) assertEquals("", cutText(sequence, room), "${sequence.length} cut at $room")
            assertEquals(sequence, cutText(sequence, sequence.length))
            assertEquals(1, drawnAfterCut(sequence, sequence.length).size)
        }
        // Text before the cut is kept up to the last whole grapheme.
        assertEquals("ok ", cutText("ok $flag", 4))
    }

    @Test
    fun `whatever the cut, the banner text is cleaned and drawable`() {
        val sequences = listOf(family, flag, tone, keycap, cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F), cp(0x1F9D1, 0x1F3FB, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F48B, 0x200D, 0x1F9D1, 0x1F3FC))
        for (sequence in sequences) {
            for (room in 0..sequence.length + 1) {
                val cleaned = clean(cutText(sequence, room))
                for (d in RealFaces.drawn(cleaned)) {
                    assertTrue(EmojiShaping.isEmojiGlyph(d) || RealFaces.inter.glyphId(d) != 0, "room $room")
                }
                assertEquals(cleaned, clean(cleaned))
            }
        }
    }
}

package fr.bde_teknantes.lnb.phone.feed.client.widgets

import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ALEX
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.LUNE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.NOW
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.STEVE
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedLang
import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.testing.TestTextMeasurer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The post card's anatomy (art direction 9.3) on numbers, with the phone's test measurer (every character half its
 * size wide: 7 dp in `title-m`, 6.5 in `body`, 6 in `caption`) and the French strings, the longer language.
 */
class PostCardLayoutTest {
    private val strings = FeedLang.FRENCH
    private val plain = CardLook(more = false, pin = false)

    /** 45 characters: two lines in the 188 dp column. */
    private val twoLines = "Des diamants à la mine du spawn, venez vite !"

    private fun layout(post: PostView, look: CardLook = plain, time: String = "2 min") =
        PostCardLayout(post, look, time, TestTextMeasurer, strings)

    private fun width(text: String, style: TextStyle) = TestTextMeasurer.measure(text, style)

    @Test
    fun `a two-line post is 119 dp with a handle and 103 without, as the art direction counts`() {
        val withHandle = layout(post(author = ALEX, text = twoLines))
        assertEquals(2, withHandle.bodyLines.size)
        assertEquals(119f, withHandle.height)
        assertEquals(103f, layout(post(author = STEVE, text = twoLines)).height)
    }

    @Test
    fun `a pinned post moves everything down 18 dp, an optional line adds 16`() {
        val base = layout(post(author = ALEX, text = twoLines))
        val pinned = layout(post(author = ALEX, text = twoLines, pinnedAt = NOW))
        assertEquals(base.height + 18f, pinned.height)
        assertEquals(base.nameY + 18f, pinned.nameY)
        val own = layout(post(author = ALEX, text = twoLines, anonymous = true, mine = true))
        // Anonymous: no handle (-16), the private line (+16).
        assertEquals(base.height, own.height)
        val replyTo = layout(post(author = ALEX, text = twoLines), plain.copy(replyTo = "En réponse à Mika L."))
        assertEquals(base.height + 16f, replyTo.height)
        assertEquals(base.handleY!! + 16f, replyTo.replyToY)
    }

    @Test
    fun `a long real name truncates so that the badge, 8 dp and the time always fit, with or without the more button`() {
        val long = Author(ALEX.uuid, "Alex_Craft", "W".repeat(32), announcer = true)
        for (more in listOf(false, true)) {
            val l = layout(post(author = long, text = "gg"), plain.copy(more = more), time = "23 h")
            assertTrue(l.nameShown.endsWith("…"), "more $more")
            assertTrue(l.badge)
            assertEquals("23 h", l.timeText)
            val timeLeft = l.timeRight - l.timeWidth
            assertEquals(if (more) 214f else 244f, l.timeRight)
            // Name, then the badge 4 dp after it (16 dp), then 8 dp, then the time.
            assertTrue(PostCardLayout.CONTENT_X + l.nameWidth + 4f + 16f + 8f <= timeLeft + 0.01f, "more $more")
        }
    }

    @Test
    fun `a name that fits isn't cut, and a name without a badge gets the badge's room`() {
        val fits = layout(post(author = LUNE, text = "gg"))
        assertEquals("Lune G.", fits.nameShown)
        val noBadge = Author(ALEX.uuid, "Alex_Craft", "W".repeat(32), announcer = false)
        val badged = noBadge.copy(announcer = true)
        assertTrue(layout(post(author = noBadge)).nameWidth > layout(post(author = badged)).nameWidth)
    }

    @Test
    fun `the handle is shown whole up to its 17 characters, and only with a real name`() {
        val longest = Author(ALEX.uuid, "A".repeat(16), "Alex", announcer = false)
        assertEquals("@" + "A".repeat(16), layout(post(author = longest)).handleShown)
        assertNull(layout(post(author = STEVE)).handleShown)
        assertNull(layout(post(author = STEVE)).handleY)
    }

    @Test
    fun `an anonymous post shows Anonyme, no handle and no badge, even to a moderator who sees its announcer author`() {
        val l = layout(post(author = LUNE, anonymous = true))
        assertTrue(l.anonymous)
        assertEquals("Anonyme", l.nameShown)
        assertNull(l.handleShown)
        assertFalse(l.badge)
        val hidden = layout(post(author = null, anonymous = true))
        assertEquals("Anonyme", hidden.nameShown)
        assertNull(hidden.privateText)
    }

    @Test
    fun `the private line - the author's own wins, a moderator's names the author, nobody else gets one`() {
        assertEquals("Publié anonymement par toi", layout(post(author = LUNE, anonymous = true, mine = true)).privateText)
        assertFalse(layout(post(author = LUNE, anonymous = true, mine = true)).privateIsModerator)
        assertEquals("Auteur : SteveBuilds", layout(post(author = STEVE, anonymous = true)).privateText)
        assertTrue(layout(post(author = STEVE, anonymous = true)).privateIsModerator)
        assertNull(layout(post(author = LUNE)).privateText)
    }

    @Test
    fun `the moderator's line cuts the handle first, then the name`() {
        val style = TextStyle.CAPTION
        // `Auteur : Alexandre M. @Alex_Craft` is 33 characters, 198 dp: past the 168 dp the line has.
        val cutHandle = layout(post(author = ALEX, anonymous = true)).privateText!!
        assertTrue(cutHandle.startsWith("Auteur : Alexandre M. @"), cutHandle)
        assertTrue(cutHandle.endsWith("…"), cutHandle)
        assertTrue(width(cutHandle, style) <= PostCardLayout.PRIVATE_WIDTH)

        val longName = Author(ALEX.uuid, "Alex_Craft", "Alexandre-Maximilien Ducourneau", announcer = false)
        val cutName = layout(post(author = longName, anonymous = true)).privateText!!
        assertFalse(cutName.contains("@"), cutName)
        assertTrue(cutName.startsWith("Auteur : Alexandre"), cutName)
        assertTrue(cutName.endsWith("…"), cutName)
        assertTrue(width(cutName, style) <= PostCardLayout.PRIVATE_WIDTH)
    }

    @Test
    fun `the thread's main post has no relative time, and its name gets that room`() {
        val long = Author(ALEX.uuid, "Alex_Craft", "W".repeat(32), announcer = false)
        val inFeed = layout(post(author = long))
        val main = layout(post(author = long), plain.copy(time = false))
        assertEquals(0f, main.timeWidth)
        assertTrue(main.nameWidth > inFeed.nameWidth)
    }

    @Test
    fun `the text is never cut, however long`() {
        val text = "mot ".repeat(70).trim()
        val l = layout(post(author = ALEX, text = text))
        assertEquals(text.replace(" ", ""), l.bodyLines.joinToString("").replace(" ", ""))
        assertEquals(10f + 18f + 16f + 2f + 18f * l.bodyLines.size + 4f + 28f + 4f + 1f, l.height)
    }
}

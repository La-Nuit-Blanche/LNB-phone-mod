package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.testing.TestTextMeasurer
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.client.widgets.CardLook
import fr.bde_teknantes.lnb.phone.feed.client.widgets.PostCardLayout
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The writing rules shared by the thread, the composer and Compose, and the thread's layout numbers. */
class FeedWritingTest {
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }

    private companion object {
        /** The French flag: two regional indicators, two characters. */
        val FLAG = String(Character.toChars(0x1F1EB)) + String(Character.toChars(0x1F1F7))
        val COMBINING_ACUTE = String(Character.toChars(0x0301))
    }

    @Test
    fun `a draft can be sent with something besides spaces, within 280 counted untrimmed`() {
        assertFalse(FeedWriting.canSend(""))
        assertFalse(FeedWriting.canSend(" \n "))
        assertTrue(FeedWriting.canSend("a"))
        assertTrue(FeedWriting.canSend("a".repeat(280)))
        assertFalse(FeedWriting.canSend("a".repeat(281)))
        // Untrimmed, as the counter counts: 280 letters and a space are over the limit.
        assertFalse(FeedWriting.canSend("a".repeat(280) + " "))
        // A flag is two characters, an accent typed apart one.
        assertTrue(FeedWriting.canSend(FLAG.repeat(140)))
        assertFalse(FeedWriting.canSend(FLAG.repeat(140) + "e"))
        assertTrue(FeedWriting.canSend(("e" + COMBINING_ACUTE).repeat(280)))
    }

    @Test
    fun `the composer's target and its name`() {
        val main = post(text = "x")
        assertNull(FeedWriting.targetOf(main))
        assertEquals("Alexandre M.", FeedWriting.targetName(null, main))
        val r = reply(main)
        assertEquals(ReplyTarget(r.id, "SteveBuilds"), FeedWriting.targetOf(r))
        // Anonymous, even to a moderator who is sent the author: Anonyme.
        val anon = reply(main).copy(anonymous = true)
        assertNull(FeedWriting.targetOf(anon)!!.name)
        assertNull(FeedWriting.targetName(null, post(anonymous = true)))
        assertEquals("post.anonymous", FeedWriting.nameOrAnonymous(null, strings))
    }

    @Test
    fun `the reply-to line names what a reply answers, Anonyme for an anonymous one, none for the main post`() {
        val main = post(text = "x")
        assertNull(FeedWriting.replyToLine(reply(main), strings))
        val toMika = reply(main).copy(replyTo = ReplyRef(FeedFixtures.id(), anonymous = false, name = "Mika L."))
        assertEquals("post.reply_to Mika L.", FeedWriting.replyToLine(toMika, strings))
        val toAnon = reply(main).copy(replyTo = ReplyRef(FeedFixtures.id(), anonymous = true, name = "SteveBuilds"))
        assertEquals("post.reply_to post.anonymous", FeedWriting.replyToLine(toAnon, strings))
        // Purged: no id, the name kept.
        val purged = reply(main).copy(replyTo = ReplyRef(null, anonymous = false, name = "Mika L."))
        assertEquals("post.reply_to Mika L.", FeedWriting.replyToLine(purged, strings))
    }

    @Test
    fun `the strip and the snackbars name why the player can't write`() {
        assertEquals("guest.reply", FeedWriting.stripKey(WriteBlock.GUEST))
        assertEquals("guest.snackbar", FeedWriting.snackbarKey(WriteBlock.GUEST))
        assertEquals("snackbar.banned", FeedWriting.stripKey(WriteBlock.BANNED))
        assertEquals("snackbar.writing_off", FeedWriting.snackbarKey(WriteBlock.WRITING_OFF))
    }

    @Test
    fun `the dock is the composer at 64 or 200 dp, strip 40, spacer the dock and 12`() {
        assertEquals(306f, ThreadLayout.dockTop(ThreadLayout.COLLAPSED))
        assertEquals(170f, ThreadLayout.dockTop(ThreadLayout.EXPANDED))
        assertEquals(330f, ThreadLayout.dockTop(ThreadLayout.STRIP))
        assertEquals(262f, ThreadLayout.visibleHeight(ThreadLayout.COLLAPSED))
        assertEquals(76f, ThreadLayout.spacer(ThreadLayout.COLLAPSED))
        assertEquals(52f, ThreadLayout.spacer(ThreadLayout.STRIP))
    }

    @Test
    fun `a thread's main post has its full timestamp under the body, 24 dp taller than a card`() {
        val p = post(text = "x")
        val card = PostCardLayout(p, CardLook(more = false, pin = false), "now", TestTextMeasurer, strings)
        val main = PostCardLayout(p, CardLook(more = false, pin = false, time = false, fullTime = "14:32", hover = false), "", TestTextMeasurer, strings)
        assertNull(card.fullTimeY)
        // Body at 46, one line: the timestamp 8 dp under it, the action row 4 dp under the timestamp.
        assertEquals(46f + 18f + 8f, main.fullTimeY)
        assertEquals(main.fullTimeY!! + 16f + 4f, main.actionY)
        assertEquals(card.height + 24f, main.height)
    }

    @Test
    fun `rows folding away keep their place among the others`() {
        assertEquals(listOf("a", "b", "c"), Rows.keepInPlace(listOf("a", "c"), listOf("a", "b", "c"), setOf("b")))
        assertEquals(listOf("b", "a"), Rows.keepInPlace(listOf("a"), listOf("b", "a"), setOf("b")))
        assertEquals(listOf("a", "b", "c", "end"), Rows.keepInPlace(listOf("a", "end"), listOf("a", "b", "c", "end"), setOf("b", "c")))
        // A row not kept is not brought back.
        assertEquals(listOf("a"), Rows.keepInPlace(listOf("a"), listOf("a", "b"), emptySet()))
    }
}

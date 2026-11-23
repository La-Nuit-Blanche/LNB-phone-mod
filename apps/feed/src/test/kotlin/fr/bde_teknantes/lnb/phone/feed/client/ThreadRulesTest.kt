package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.reply
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.model.ReplyRef
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rules under the thread, the composer and the folds, without a screen: rows kept while they fold, the dock,
 * writing, draft keys, which threads and drafts the session keeps, and when a thread is marked seen.
 */
class ThreadRulesTest {
    private val strings = FeedStrings { id, args -> if (args.isEmpty()) id else "$id ${args.joinToString(" ")}" }
    private val r = FeedFixtures.Recorder()
    private val session = r.session

    // ---- Rows.keepInPlace ----

    @Test
    fun `rows folding next to each other stay together, in their order`() {
        assertEquals(listOf("a", "b", "c", "d"), Rows.keepInPlace(listOf("a", "d"), listOf("a", "b", "c", "d"), setOf("b", "c")))
        // The first rows of the list.
        assertEquals(listOf("a", "b", "c"), Rows.keepInPlace(listOf("c"), listOf("a", "b", "c"), setOf("a", "b")))
        // Everything else gone.
        assertEquals(listOf("b"), Rows.keepInPlace(emptyList(), listOf("a", "b", "c"), setOf("b")))
    }

    @Test
    fun `a row folding after a row that left at once goes after the last row still there`() {
        // `b` left without folding (someone else deleted it), `c` folds: it goes after `a`.
        assertEquals(listOf("a", "c", "end"), Rows.keepInPlace(listOf("a", "end"), listOf("a", "b", "c", "end"), setOf("c")))
    }

    @Test
    fun `a row kept that is still wanted isn't doubled, and rows new above a fold keep the fold in place`() {
        assertEquals(listOf("a", "b"), Rows.keepInPlace(listOf("a", "b"), listOf("a", "b"), setOf("b")))
        // A live post `n` arrived on top while `b` folds under `a`.
        assertEquals(listOf("n", "a", "b", "c"), Rows.keepInPlace(listOf("n", "a", "c"), listOf("a", "b", "c"), setOf("b")))
    }

    // ---- ThreadLayout ----

    @Test
    fun `the list shows above the dock as it settles, and ends with room for it`() {
        assertEquals(370f - 200f - 44f, ThreadLayout.visibleHeight(ThreadLayout.EXPANDED))
        assertEquals(370f - 40f - 44f, ThreadLayout.visibleHeight(ThreadLayout.STRIP))
        // No dock yet (the main post isn't known): 12 dp only.
        assertEquals(12f, ThreadLayout.spacer(0f))
        assertEquals(212f, ThreadLayout.spacer(ThreadLayout.EXPANDED))
    }

    // ---- FeedWriting ----

    @Test
    fun `who the composer answers and what it calls them`() {
        val main = post(text = "x")
        // A main post never is a target: replying to it is replying to the thread.
        assertNull(FeedWriting.targetOf(post(text = "y", anonymous = true)))
        assertNull(FeedWriting.targetName(null, post(text = "z", anonymous = true, author = FeedFixtures.ALEX)))
        assertNull(FeedWriting.targetName(null, null))
        val target = ReplyTarget(FeedFixtures.id(), "Mika L.")
        assertEquals("Mika L.", FeedWriting.targetName(target, main))
        assertEquals("post.anonymous", FeedWriting.nameOrAnonymous(FeedWriting.targetName(ReplyTarget(target.replyTo, null), main), strings))
        // A reply-to whose name isn't known reads Anonymous rather than nothing.
        val unnamed = reply(main).copy(replyTo = ReplyRef(FeedFixtures.id(), anonymous = false, name = null))
        assertEquals("post.reply_to post.anonymous", FeedWriting.replyToLine(unnamed, strings))
    }

    @Test
    fun `the strip's words and the snackbars' for each reason`() {
        assertEquals("snackbar.banned", FeedWriting.snackbarKey(WriteBlock.BANNED))
        assertEquals("snackbar.writing_off", FeedWriting.stripKey(WriteBlock.WRITING_OFF))
        assertEquals("guest.reply", FeedWriting.stripKey(WriteBlock.GUEST))
        // Every key is in both languages.
        for (block in WriteBlock.entries) {
            for (key in listOf(FeedWriting.stripKey(block), FeedWriting.snackbarKey(block))) {
                FeedLang.FRENCH.get(key)
                FeedLang.ENGLISH.get(key)
            }
        }
    }

    @Test
    fun `line feeds count as characters, as the counter counts them`() {
        assertTrue(FeedWriting.canSend("a\n".repeat(140)))
        assertFalse(FeedWriting.canSend("a\n".repeat(140) + "b"))
        assertFalse(FeedWriting.canSend("\n\n\n"))
        assertFalse(FeedWriting.canSend("\t "))
    }

    // ---- Keys ----

    @Test
    fun `a draft key stays while what is sent stays, and changes with it or once cleared`() {
        val key = DraftKey()
        val first = key.forSending(Triple("a", false, null))
        assertEquals(first, key.forSending(Triple("a", false, null)))
        val other = key.forSending(Triple("a", true, null))
        assertNotEquals(first, other)
        // Back to the first draft: a new send, a new key.
        assertNotEquals(first, key.forSending(Triple("a", false, null)))
        key.clear()
        assertNotEquals(first, key.forSending(Triple("a", false, null)))
    }

    @Test
    fun `a reply's target is part of what its key stands for`() {
        r.state()
        val main = post(text = "x")
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, emptyList(), null))
        val draft = session.replyDraft(main.id)
        draft.text.text = "salut"
        assertTrue(session.sendReply(main.id))
        val toMain = r.last<CreateReplyPayload>()
        session.onResult(ResultPayload(toMain.requestId, Outcome.UNAVAILABLE))
        draft.target = ReplyTarget(FeedFixtures.id(), "Steve")
        assertTrue(session.sendReply(main.id))
        assertNotEquals(toMain.key, r.last<CreateReplyPayload>().key)
        // Sending while one is on its way does nothing.
        assertFalse(session.sendReply(main.id))
    }

    // ---- What the session keeps ----

    @Test
    fun `threads are let go least recently used first, and one used again stays`() {
        val kept = FeedFixtures.id()
        session.thread(kept)
        val others = (1..(FeedSession.MAX_THREADS - 1)).map { FeedFixtures.id().also(session::thread) }
        // Used again: the most recent now.
        session.thread(kept)
        session.thread(FeedFixtures.id())
        session.thread(FeedFixtures.id())
        assertTrue(session.threadOrNull(kept) != null)
        assertNull(session.threadOrNull(others[0]))
        assertNull(session.threadOrNull(others[1]))
        assertTrue(session.threadOrNull(others[2]) != null)
    }

    @Test
    fun `reply drafts are kept for the 20 threads used last`() {
        val first = FeedFixtures.id()
        session.replyDraft(first).text.text = "garde"
        val second = FeedFixtures.id()
        session.replyDraft(second).text.text = "perdu"
        repeat(FeedSession.MAX_REPLY_DRAFTS - 2) { session.replyDraft(FeedFixtures.id()) }
        session.replyDraft(first)
        session.replyDraft(FeedFixtures.id())
        assertEquals("garde", session.replyDraft(first).text.text)
        assertEquals("", session.replyDraft(second).text.text)
    }

    @Test
    fun `a thread deleted is never asked for again, and its Retry does nothing`() {
        r.state()
        val main = post(text = "x")
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, emptyList(), null))
        session.onRemoved(listOf(RemovedItem(main.id, FeedKind.GLOBAL, null)))
        assertTrue(session.thread(main.id).deleted)
        session.onReset()
        session.loadThreadIfNeeded(main.id)
        session.retryThread(main.id)
        session.loadMoreReplies(main.id)
        assertEquals(1, r.all<LoadThreadPayload>().size)
    }

    @Test
    fun `a removed reply leaves its thread and the count of the post it answered isn't guessed`() {
        r.state()
        val main = post(text = "x", replies = 2)
        val a = reply(main)
        val b = reply(main)
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, listOf(a, b), null))
        session.onRemoved(listOf(RemovedItem(a.id, FeedKind.GLOBAL, main.id)))
        assertEquals(listOf(b.id), session.thread(main.id).replies)
        assertNull(session.post(a.id))
        // The site's `counts` says the new count; the client doesn't make one up.
        assertEquals(2, session.post(main.id)!!.replies)
        // A late live copy doesn't bring it back.
        session.onLive(listOf(a))
        assertEquals(listOf(b.id), session.thread(main.id).replies)
    }

    // ---- Seen marks ----

    @Test
    fun `a thread shown before its page is marked when the page comes, and a thread left before its page is not`() {
        r.state()
        val shown = post(text = "x")
        session.showThread(shown.id)
        session.loadThreadIfNeeded(shown.id)
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, shown, emptyList(), null))
        assertEquals(listOf(shown.id), r.all<SeenThreadPayload>().map { it.threadId })

        val left = post(text = "y")
        session.showThread(left.id)
        session.loadThreadIfNeeded(left.id)
        session.showThread(null)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, left, emptyList(), null))
        assertEquals(1, r.all<SeenThreadPayload>().size)
    }

    @Test
    fun `a guest who links an account while a thread shows marks it seen then, once`() {
        r.state(FeedFixtures.GUEST)
        val main = post(text = "x")
        session.showThread(main.id)
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, emptyList(), null))
        r.state(FeedFixtures.GUEST)
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
        r.state()
        r.state()
        assertEquals(1, r.all<SeenThreadPayload>().size)
    }

    @Test
    fun `the markers clear in the client at once, also for a guest, who sends nothing`() {
        r.state(FeedFixtures.GUEST)
        val main = post(text = "x", unseenReplies = 3)
        session.showThread(main.id)
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, emptyList(), null))
        assertEquals(0, session.post(main.id)!!.unseenReplies)
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
    }

    @Test
    fun `the thread screen's state object is the session's as long as it shows`() {
        val id = FeedFixtures.id()
        val held = session.thread(id)
        session.showThread(id)
        repeat(FeedSession.MAX_THREADS * 2) { session.thread(FeedFixtures.id()) }
        assertSame(held, session.thread(id))
        session.showThread(null)
        repeat(FeedSession.MAX_THREADS) { session.thread(FeedFixtures.id()) }
        assertNotSame(held, session.thread(id))
    }
}

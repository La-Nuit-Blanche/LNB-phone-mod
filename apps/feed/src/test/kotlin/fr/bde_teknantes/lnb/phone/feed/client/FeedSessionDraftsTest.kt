package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.ANNOUNCER
import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * The Compose draft and the reply drafts (`docs/feed-app.md` 8, "Writing"; art direction 9.7, 9.8, 9.11): keys across
 * failures, every outcome, and the post or reply placed once whichever of `result` and `live` comes first.
 */
class FeedSessionDraftsTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val global get() = session.feed(FeedKind.GLOBAL)

    private fun compose(text: String = "Bonjour"): CreatePostPayload {
        session.compose.text.text = text
        assertTrue(session.sendPost())
        return r.last()
    }

    // ---- Compose ----

    @Test
    fun `every failure keeps the draft and its key, and says what the art direction says`() {
        r.state()
        val cases = listOf(
            ResultPayload(0, Outcome.UNAVAILABLE) to DraftOutcome.SendFailed,
            ResultPayload(0, Outcome.DISABLED) to DraftOutcome.SendFailed,
            ResultPayload(0, Outcome.INVALID) to DraftOutcome.SendFailed,
            ResultPayload(0, Outcome.RATE_LIMITED, retryAfter = 12) to DraftOutcome.TooFast(12),
            ResultPayload(0, Outcome.RATE_LIMITED) to DraftOutcome.TooFast(1),
            ResultPayload(0, Outcome.NOT_LINKED) to DraftOutcome.Refused(WriteBlock.GUEST),
            ResultPayload(0, Outcome.BANNED) to DraftOutcome.Refused(WriteBlock.BANNED),
            // OK without the post is a broken answer: not a success.
            ResultPayload(0, Outcome.OK) to DraftOutcome.SendFailed,
        )
        val first = compose()
        for ((answer, expected) in cases) {
            val sent = r.last<CreatePostPayload>()
            assertEquals(first.key, sent.key, "$answer")
            session.onResult(answer.copy(requestId = sent.requestId))
            assertFalse(session.compose.sending)
            assertEquals(expected, session.compose.takeOutcome(), "$answer")
            assertNull(session.compose.takeOutcome())
            assertEquals("Bonjour", session.compose.text.text)
            assertTrue(session.sendPost())
        }
    }

    @Test
    fun `the key changes with the text, the toggle or the destination, and after a success`() {
        r.state(ANNOUNCER)
        val keys = ArrayList<java.util.UUID>()
        keys += compose().key
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.UNAVAILABLE))
        keys += compose("Bonsoir").key
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.UNAVAILABLE))
        session.compose.anonymous = true
        assertTrue(session.sendPost())
        keys += r.last<CreatePostPayload>().key
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.UNAVAILABLE))
        session.compose.destination = FeedKind.ANNOUNCEMENTS
        assertTrue(session.sendPost())
        val toAnnouncements = r.last<CreatePostPayload>()
        assertEquals(FeedKind.ANNOUNCEMENTS, toAnnouncements.feed)
        assertTrue(toAnnouncements.anonymous)
        keys += toAnnouncements.key
        session.onResult(ResultPayload(toAnnouncements.requestId, Outcome.OK, post = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, mine = true)))
        keys += compose("Bonsoir").key
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `a success clears the text, the toggle and the key, and keeps the destination`() {
        r.state(ANNOUNCER)
        session.compose.destination = FeedKind.ANNOUNCEMENTS
        session.compose.anonymous = true
        val sent = compose()
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, mine = true)))
        assertEquals("", session.compose.text.text)
        assertFalse(session.compose.anonymous)
        assertEquals(FeedKind.ANNOUNCEMENTS, session.compose.destination)
    }

    @Test
    fun `one send at a time, and none when blank, too long or not allowed to write`() {
        r.state()
        compose()
        assertTrue(session.compose.sending)
        assertFalse(session.sendPost())
        assertEquals(1, r.all<CreatePostPayload>().size)

        for (state in listOf(FeedFixtures.GUEST, FeedFixtures.PLAYER.copy(banned = true), FeedFixtures.PLAYER.copy(writing = false))) {
            val rec = FeedFixtures.Recorder()
            rec.state(state)
            rec.session.compose.text.text = "Bonjour"
            assertFalse(rec.session.sendPost(), "$state")
            assertTrue(rec.all<CreatePostPayload>().isEmpty())
        }
    }

    @Test
    fun `280 characters are counted as code points and the text goes trimmed`() {
        r.state()
        // 280 emoji are 560 UTF-16 units: still 280 characters.
        val emoji = "😀".repeat(280)
        assertEquals(emoji, compose("  $emoji\n").text)
        session.onResult(ResultPayload(r.last<CreatePostPayload>().requestId, Outcome.UNAVAILABLE))
        session.compose.text.text = emoji + "😀"
        assertFalse(session.sendPost())
        // Untrimmed past 280 but 280 once trimmed: the text sent is what counts.
        session.compose.text.text = "a".repeat(280) + "   "
        assertTrue(session.sendPost())
        assertEquals("a".repeat(280), r.last<CreatePostPayload>().text)
    }

    @Test
    @Disabled(
        "Defect (feed-mod.md 11: 'A key whose post was deleted since answers 404 notFound: stop retrying and clear the " +
            "draft'): postAnswered treats NOT_FOUND as any failure, so the draft and its key stay and every Retry gets " +
            "NOT_FOUND again.",
    )
    fun `a draft whose replayed key finds its post deleted is cleared`() {
        r.state()
        val sent = compose()
        session.onResult(ResultPayload(sent.requestId, Outcome.NOT_FOUND))
        assertEquals("", session.compose.text.text)
        session.compose.text.text = "Bonjour"
        assertTrue(session.sendPost())
        assertNotEquals(sent.key, r.last<CreatePostPayload>().key)
    }

    @Test
    fun `publishing brings in every waiting post along with one's own, which goes on top`() {
        r.state()
        val old = post(minutesAgo = 30)
        r.firstPage(FeedKind.GLOBAL, listOf(old))
        val w1 = post(minutesAgo = 5)
        val w2 = post(minutesAgo = 3)
        session.onLive(listOf(w1, w2))
        assertEquals(2, global.waiting.size)
        val sent = compose()
        val mine = post(minutesAgo = 0, mine = true)
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = mine))
        assertEquals(listOf(mine.id, w2.id, w1.id, old.id), global.posts)
        assertTrue(global.waiting.isEmpty())
        assertTrue(session.wantsHighlight(w1.id))
        assertEquals(mine.id, session.takeReveal(FeedKind.GLOBAL))
    }

    @Test
    @Disabled(
        "Defect (low): postAnswered puts the own post at index 0 after releasing the waiting posts, above a post of " +
            "someone else that is newer (it arrived live before the answer). The list is no longer newest first.",
    )
    fun `one's own post answered after a newer live post keeps the list newest first`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post(minutesAgo = 30)))
        val sent = compose()
        val mine = post(minutesAgo = 2, mine = true)
        val newer = post(minutesAgo = 1)
        session.onLive(listOf(newer))
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = mine))
        assertEquals(listOf(newer.id, mine.id), global.posts.take(2))
    }

    @Test
    fun `an own post deleted before its answer arrives isn't brought back`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(post(minutesAgo = 30)))
        val sent = compose()
        val mine = post(minutesAgo = 0, mine = true)
        session.onRemoved(listOf(RemovedItem(mine.id, FeedKind.GLOBAL, null)))
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = mine))
        assertFalse(global.posts.contains(mine.id))
        assertInstanceOf(DraftOutcome.Sent::class.java, session.compose.takeOutcome())
        assertNull(session.takeReveal(FeedKind.GLOBAL))
    }

    @Test
    fun `an own announcement goes under the pinned group of a list not shown, and nowhere when it isn't loaded`() {
        r.state(ANNOUNCER)
        val pinned = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 500, pinnedAt = FeedFixtures.NOW)
        r.firstPage(FeedKind.ANNOUNCEMENTS, listOf(post(FeedKind.ANNOUNCEMENTS, minutesAgo = 30)), pinned = listOf(pinned))
        session.show(FeedKind.GLOBAL)
        session.compose.destination = FeedKind.ANNOUNCEMENTS
        val sent = compose()
        val mine = post(FeedKind.ANNOUNCEMENTS, minutesAgo = 0, mine = true)
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = mine))
        val list = session.feed(FeedKind.ANNOUNCEMENTS)
        assertEquals(listOf(pinned.id), list.pinned)
        assertEquals(mine.id, list.posts.first())
        assertEquals(mine.id, session.takeReveal(FeedKind.ANNOUNCEMENTS))

        session.compose.destination = FeedKind.GLOBAL
        val toGlobal = compose()
        session.onResult(ResultPayload(toGlobal.requestId, Outcome.OK, post = post(minutesAgo = 0, mine = true)))
        assertFalse(global.loaded)
        assertNull(session.takeReveal(FeedKind.GLOBAL))
    }

    @Test
    fun `a result for a reply, a result for the wrong request, and one after logging out change nothing`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, emptyList())
        val sent = compose()
        // A hostile answer: a reply where a post was asked.
        session.onResult(ResultPayload(sent.requestId, Outcome.OK, post = replyIn(post())))
        assertTrue(global.posts.isEmpty())
        val again = compose()
        session.clear()
        session.compose.text.text = "nouveau"
        session.onResult(ResultPayload(again.requestId, Outcome.OK, post = post(mine = true)))
        assertEquals("nouveau", session.compose.text.text)
        assertNull(session.compose.takeOutcome())
    }

    // ---- Replies ----

    private val main = post(minutesAgo = 10)

    private fun reply(text: String = "J'arrive !"): CreateReplyPayload {
        session.replyDraft(main.id).text.text = text
        assertTrue(session.sendReply(main.id))
        return r.last()
    }

    @Test
    fun `a reply goes to its target, keeps its key across failures, and changes key with the target`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        val other = replyIn(main, minutesAgo = 5)
        r.loadThread(main, listOf(other))
        val toMain = reply()
        assertNull(toMain.replyTo)
        assertEquals(main.id, toMain.threadId)
        session.onResult(ResultPayload(toMain.requestId, Outcome.UNAVAILABLE))
        assertEquals(DraftOutcome.SendFailed, session.replyDraft(main.id).takeOutcome())
        assertTrue(session.sendReply(main.id))
        assertEquals(toMain.key, r.last<CreateReplyPayload>().key)
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.BANNED))
        assertEquals(DraftOutcome.Refused(WriteBlock.BANNED), session.replyDraft(main.id).takeOutcome())

        session.replyDraft(main.id).target = ReplyTarget(other.id, "SteveBuilds")
        assertTrue(session.sendReply(main.id))
        val toOther = r.last<CreateReplyPayload>()
        assertEquals(other.id, toOther.replyTo)
        assertNotEquals(toMain.key, toOther.key)
    }

    @Test
    fun `a reply answered before its live copy and one whose live copy came first are shown once, at the end`() {
        for (liveFirst in listOf(false, true)) {
            val rec = FeedFixtures.Recorder()
            val s = rec.session
            rec.state()
            rec.firstPage(FeedKind.GLOBAL, listOf(main))
            val earlier = replyIn(main, minutesAgo = 5)
            rec.loadThread(main, listOf(earlier))
            s.replyDraft(main.id).text.text = "moi"
            s.replyDraft(main.id).anonymous = true
            s.replyDraft(main.id).target = ReplyTarget(earlier.id, null)
            assertTrue(s.sendReply(main.id))
            val mine = replyIn(main, to = earlier, minutesAgo = 0, mine = true)
            val live = { s.onLive(listOf(mine)) }
            if (liveFirst) live()
            s.onResult(ResultPayload(rec.last<CreateReplyPayload>().requestId, Outcome.OK, post = mine))
            if (!liveFirst) live()
            assertEquals(listOf(earlier.id, mine.id), s.thread(main.id).replies, "live first: $liveFirst")
            assertEquals(mine.id, s.takeThreadReveal(main.id))
            assertTrue(s.takeHighlight(mine.id))
            val draft = s.replyDraft(main.id)
            assertEquals("", draft.text.text)
            assertFalse(draft.anonymous)
            assertNull(draft.target)
            // The player's own reply raises no marker.
            assertEquals(0, s.post(main.id)!!.unseenReplies)
        }
    }

    @Test
    fun `a reply to a thread with later pages unloaded waits for its page, the draft cleared`() {
        r.state()
        val replies = (1..2).map { replyIn(main, minutesAgo = 9 - it.toLong()) }
        r.loadThread(main, replies, next = "t1")
        reply()
        val mine = replyIn(main, minutesAgo = 0, mine = true)
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.OK, post = mine))
        assertEquals(replies.map { it.id }, session.thread(main.id).replies)
        assertNull(session.takeThreadReveal(main.id))
        assertEquals("", session.replyDraft(main.id).text.text)
    }

    @Test
    fun `a reply answered NOT_FOUND leaves the thread, deleted`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, emptyList())
        reply()
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.NOT_FOUND))
        assertEquals(DraftOutcome.ThreadGone, session.replyDraft(main.id).takeOutcome())
        assertTrue(session.thread(main.id).deleted)
        assertTrue(global.posts.isEmpty())
    }

    @Test
    fun `a reply answer for another thread than asked isn't put in either`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        r.loadThread(main, emptyList())
        reply()
        val elsewhere = replyIn(post(), mine = true)
        session.onResult(ResultPayload(r.last<CreateReplyPayload>().requestId, Outcome.OK, post = elsewhere))
        assertTrue(session.thread(main.id).replies.isEmpty())
        assertNull(session.threadOrNull(elsewhere.threadId!!))
    }

    @Test
    fun `the 20 most recent reply drafts are kept`() {
        val threads = (1..21).map { FeedFixtures.id() }
        val drafts = threads.map { id -> session.replyDraft(id).also { it.text.text = "draft $id" } }
        // The first was the least recently used: it went; the others are the same drafts.
        assertNotSame(drafts[0], session.replyDraft(threads[0]))
        assertEquals("", session.replyDraft(threads[0]).text.text)
        assertSame(drafts[20], session.replyDraft(threads[20]))
        assertEquals("draft ${threads[20]}", session.replyDraft(threads[20]).text.text)
    }

    @Test
    fun `drafts stay across a reset and go at logout`() {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(main))
        session.compose.text.text = "compose"
        session.compose.destination = FeedKind.ANNOUNCEMENTS
        session.replyDraft(main.id).text.text = "reply"
        session.onReset()
        assertEquals("compose", session.compose.text.text)
        assertEquals("reply", session.replyDraft(main.id).text.text)
        session.clear()
        assertEquals("", session.compose.text.text)
        assertEquals(FeedKind.GLOBAL, session.compose.destination)
        assertEquals("", session.replyDraft(main.id).text.text)
    }
}

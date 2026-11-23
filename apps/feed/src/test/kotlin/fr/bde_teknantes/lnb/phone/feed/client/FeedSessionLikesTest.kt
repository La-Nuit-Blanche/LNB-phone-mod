package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Likes shown at once (art direction 9.3, F-6): answers in every order, counts meanwhile, every outcome. */
class FeedSessionLikesTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session
    private val p = post(likes = 8)

    private fun liked() = session.post(p.id)!!.liked
    private fun likes() = session.post(p.id)!!.likes

    /** Clicks the heart; returns the request sent. */
    private fun click(): LikePayload {
        assertEquals(LikeAttempt.SENT, session.toggleLike(p.id))
        return r.last()
    }

    private fun ok(request: LikePayload, likes: Int, liked: Boolean) =
        session.onResult(ResultPayload(request.requestId, Outcome.OK, likes = likes, liked = liked))

    private fun fail(request: LikePayload, outcome: Outcome = Outcome.UNAVAILABLE) = session.onResult(ResultPayload(request.requestId, outcome))

    init {
        r.state()
        r.firstPage(FeedKind.GLOBAL, listOf(p))
    }

    @Test
    fun `a like answered takes the server's count`() {
        click()
        assertTrue(liked())
        assertEquals(9, likes())
        ok(r.last(), likes = 12, liked = true)
        assertEquals(12, likes())
        assertTrue(liked())
        assertNull(session.takeNotice())
    }

    @Test
    fun `an unlike answered before the like it overtook - the last click's answer stands`() {
        val like = click()
        val unlike = click()
        ok(unlike, likes = 8, liked = false)
        ok(like, likes = 9, liked = true)
        assertFalse(liked())
        assertEquals(8, likes())
    }

    @Test
    fun `an earlier click that failed is overtaken by the later one that went through`() {
        val like = click()
        val unlike = click()
        fail(like)
        assertFalse(liked())
        assertNull(session.takeNotice())
        ok(unlike, likes = 8, liked = false)
        assertFalse(liked())
        assertEquals(8, likes())
    }

    @Test
    fun `three clicks, the last refused - back to what the second left on the server`() {
        val a = click()
        val b = click()
        val c = click()
        assertTrue(c.liked)
        ok(a, likes = 9, liked = true)
        ok(b, likes = 8, liked = false)
        assertTrue(liked())
        fail(c)
        assertFalse(liked())
        assertEquals(8, likes())
        assertEquals(FeedNotice.ActionFailed, session.takeNotice())
    }

    @Test
    fun `the last click refused before an earlier one's success - the success stands`() {
        val like = click()
        val unlike = click()
        fail(unlike)
        ok(like, likes = 9, liked = true)
        assertTrue(liked())
        assertEquals(9, likes())
    }

    @Test
    fun `counts while a like waits leave the heart as clicked, and are what a refusal goes back to`() {
        click()
        session.onCounts(listOf(CountUpdate(p.id, likes = 20, replies = 3)))
        assertTrue(liked())
        assertEquals(9, likes())
        assertEquals(3, session.post(p.id)!!.replies)
        fail(r.last())
        assertFalse(liked())
        assertEquals(20, likes())
    }

    @Test
    fun `counts while a like waits, then its success - the answer's count`() {
        click()
        session.onCounts(listOf(CountUpdate(p.id, likes = 20, replies = 0)))
        ok(r.last(), likes = 21, liked = true)
        assertEquals(21, likes())
        // Settled: counts apply again.
        session.onCounts(listOf(CountUpdate(p.id, likes = 30, replies = 0)))
        assertEquals(30, likes())
        assertTrue(liked())
    }

    @Test
    fun `an OK like answer missing its fields doesn't freeze the count`() {
        click()
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.OK))
        session.onCounts(listOf(CountUpdate(p.id, likes = 30, replies = 0)))
        assertEquals(30, likes())
    }

    @Test
    fun `a like answered NOT_FOUND removes the post from its feed and its thread, silently`() {
        val reply = replyIn(p)
        r.loadThread(p, listOf(reply))
        assertEquals(LikeAttempt.SENT, session.toggleLike(reply.id))
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.NOT_FOUND))
        assertNull(session.post(reply.id))
        assertTrue(session.thread(p.id).replies.isEmpty())
        assertNull(session.takeNotice())

        click()
        fail(r.last(), Outcome.NOT_FOUND)
        assertNull(session.post(p.id))
        assertTrue(session.feed(FeedKind.GLOBAL).posts.isEmpty())
        assertNull(session.takeNotice())
    }

    @ParameterizedTest
    @EnumSource(Outcome::class, names = ["UNAVAILABLE", "DISABLED", "NOT_ALLOWED", "INVALID", "NOT_PINNABLE", "PIN_LIMIT"])
    fun `a like refused goes back, with Action impossible`(outcome: Outcome) {
        click()
        fail(r.last(), outcome)
        assertFalse(liked())
        assertEquals(8, likes())
        assertEquals(FeedNotice.ActionFailed, session.takeNotice())
    }

    @Test
    fun `a like refused for who the player is, or as too fast, says so`() {
        click()
        fail(r.last(), Outcome.NOT_LINKED)
        assertEquals(FeedNotice.Refused(WriteBlock.GUEST), session.takeNotice())
        click()
        fail(r.last(), Outcome.BANNED)
        assertEquals(FeedNotice.Refused(WriteBlock.BANNED), session.takeNotice())
        click()
        session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.RATE_LIMITED, retryAfter = 5))
        assertEquals(FeedNotice.TooFast(5), session.takeNotice())
        assertFalse(liked())
    }

    @Test
    fun `the seconds to wait are kept between 1 and an hour`() {
        for ((given, shown) in listOf(0 to 1, -4 to 1, null to 1, 59 to 59, 1_000_000 to 3600)) {
            click()
            session.onResult(ResultPayload(r.last<LikePayload>().requestId, Outcome.RATE_LIMITED, retryAfter = given))
            assertEquals(FeedNotice.TooFast(shown), session.takeNotice(), "retryAfter $given")
        }
    }

    @Test
    fun `nothing is sent for a post not held, and a like for a post deleted meanwhile is forgotten`() {
        val sent = r.all<LikePayload>().size
        assertEquals(LikeAttempt.IGNORED, session.toggleLike(FeedFixtures.id()))
        assertEquals(sent, r.all<LikePayload>().size)
        click()
        session.onRemoved(listOf(RemovedItem(p.id, FeedKind.GLOBAL, null)))
        ok(r.last(), likes = 9, liked = true)
        assertNull(session.post(p.id))
        assertNull(session.takeNotice())
    }

    @Test
    fun `whoever may not write now likes nothing`() {
        for (state in listOf(FeedFixtures.GUEST, FeedFixtures.PLAYER.copy(banned = true), FeedFixtures.PLAYER.copy(writing = false))) {
            val rec = FeedFixtures.Recorder()
            rec.state(state)
            val q = post(likes = 3)
            rec.firstPage(FeedKind.GLOBAL, listOf(q))
            assertEquals(LikeAttempt.BLOCKED, rec.session.toggleLike(q.id))
            assertEquals(3, rec.session.post(q.id)!!.likes)
            assertFalse(rec.session.post(q.id)!!.liked)
            assertTrue(rec.all<LikePayload>().isEmpty())
        }
    }

    @Test
    fun `an unlike never shows a count below zero`() {
        val broken = post(likes = 0, liked = true)
        val rec = FeedFixtures.Recorder()
        rec.state()
        rec.firstPage(FeedKind.GLOBAL, listOf(broken))
        rec.session.toggleLike(broken.id)
        assertEquals(0, rec.session.post(broken.id)!!.likes)
    }

    @Test
    fun `a page, a live copy or a thread bringing the post while its like waits keep the heart as clicked`() {
        val like = click()
        // A reload after a reset, from before the like.
        session.onReset()
        r.firstPage(FeedKind.GLOBAL, listOf(p))
        assertTrue(liked())
        assertEquals(9, likes())
        session.onLive(listOf(p))
        assertTrue(liked())
        r.loadThread(p, emptyList())
        assertTrue(liked())
        assertEquals(9, likes())
        ok(like, likes = 9, liked = true)
        assertTrue(liked())
    }
}

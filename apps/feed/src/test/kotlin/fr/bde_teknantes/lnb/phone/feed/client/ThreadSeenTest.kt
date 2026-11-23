package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedFixtures.post
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What the session adds for the thread and pin screens: seen marks sent once the state is known, a pin in flight. */
class ThreadSeenTest {
    private val r = FeedFixtures.Recorder()
    private val session = r.session

    @Test
    fun `a thread shown before the player's state is known is marked seen when it comes, once`() {
        val main = post(text = "x")
        session.showThread(main.id)
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, emptyList(), null))
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
        r.state()
        assertEquals(listOf(main.id), r.all<SeenThreadPayload>().map { it.threadId })
        r.state()
        assertEquals(1, r.all<SeenThreadPayload>().size)
    }

    @Test
    fun `a guest's thread is never marked seen on the site`() {
        val main = post(text = "x")
        r.state(FeedFixtures.GUEST)
        session.showThread(main.id)
        session.loadThreadIfNeeded(main.id)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, emptyList(), null))
        r.state(FeedFixtures.GUEST)
        assertTrue(r.all<SeenThreadPayload>().isEmpty())
    }

    @Test
    fun `a reply deleted whose removed came before the answer still says Reply deleted`() {
        r.state()
        val main = post(text = "x")
        session.showThread(main.id)
        session.loadThreadIfNeeded(main.id)
        val own = FeedFixtures.reply(main, mine = true)
        session.onThread(ThreadPayload(r.last<LoadThreadPayload>().requestId, Outcome.OK, main, listOf(own), null))
        assertTrue(session.delete(own.id))
        // The relay's `removed` reaches the client before the answer to `delete`.
        session.onRemoved(listOf(RemovedItem(own.id, FeedKind.GLOBAL, main.id)))
        session.onResult(ResultPayload(r.last<DeletePayload>().requestId, Outcome.OK))
        assertEquals(FeedNotice.Deleted(own.id, reply = true), session.takeNotice())
    }

    @Test
    fun `a pin waits for its answer`() {
        r.state(FeedFixtures.ANNOUNCER)
        val p = post(FeedKind.ANNOUNCEMENTS, text = "a", canPin = true)
        r.page(FeedKind.ANNOUNCEMENTS, listOf(p))
        assertEquals(PinAttempt.SENT, session.togglePin(p.id, replace = false))
        assertTrue(session.isPinning(p.id))
        session.onResult(ResultPayload(r.last<PinPayload>().requestId, Outcome.UNAVAILABLE))
        assertFalse(session.isPinning(p.id))
    }
}

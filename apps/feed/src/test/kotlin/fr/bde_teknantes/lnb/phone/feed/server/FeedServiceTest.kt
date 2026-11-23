package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LivePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.RemovedPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResetPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.Players.CAMILLE
import fr.bde_teknantes.lnb.phone.feed.server.Players.KAEL
import fr.bde_teknantes.lnb.phone.feed.server.Players.LUNE
import fr.bde_teknantes.lnb.phone.feed.server.Players.MIKA
import fr.bde_teknantes.lnb.phone.feed.server.Players.VISITOR
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryBackend
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The server side over the memory backend: requests, the relay, the notifier, rights. */
class FeedServiceTest {
    private val clock = MutableClock()
    private val host = FakeHost().apply { online += listOf(CAMILLE, MIKA, LUNE, KAEL, VISITOR) }
    private val sink = RecordingSink()
    private val backend = MemoryBackend(clock, Players.directory(), guests = listOf("Visitor42"), rateLimits = false)
    private val service = FeedService(backend, host, sink).apply {
        start()
        backend.setAnnouncer(KAEL, true, null)
        for (player in listOf(CAMILLE, MIKA, LUNE, KAEL, VISITOR)) onJoin(player)
    }
    private var requests = 0
    private var keys = 0L

    private fun open(vararg players: UUID) = players.forEach(service::handleOpen)

    private fun write(player: UUID, feed: FeedKind = FeedKind.GLOBAL, text: String = "Salut", anonymous: Boolean = false): ResultPayload {
        clock.advanceSeconds(1)
        val id = ++requests
        service.handleRequest(player, CreatePostPayload(id, UUID(7, keys++), feed, text, anonymous))
        return host.received<ResultPayload>(player).single { it.requestId == id }
    }

    private fun replyTo(player: UUID, thread: UUID, replyTo: UUID? = null): ResultPayload {
        clock.advanceSeconds(1)
        val id = ++requests
        service.handleRequest(player, CreateReplyPayload(id, UUID(7, keys++), thread, replyTo, "Moi !", false))
        return host.received<ResultPayload>(player).single { it.requestId == id }
    }

    private fun liveFor(player: UUID): List<PostView> = host.received<LivePayload>(player).flatMap { it.posts }

    private fun args(component: Component) = (component.contents as TranslatableContents).args.toList()

    @Test
    fun `open makes a watcher and answers the state`() {
        open(CAMILLE)
        assertTrue(service.isWatcher(CAMILLE))
        val state = host.received<StatePayload>(CAMILLE).last()
        assertEquals(Availability.OK, state.available)
        assertEquals("Cam_Roux", state.state?.me?.username)
        service.onLeave(CAMILLE)
        assertFalse(service.isWatcher(CAMILLE))
    }

    @Test
    fun `a new post reaches each watcher as their own view, and nobody else`() {
        open(CAMILLE, LUNE, MIKA)
        val result = write(CAMILLE, anonymous = true)
        assertEquals(Outcome.OK, result.outcome)
        val post = result.post!!
        assertTrue(post.mine)
        assertTrue(liveFor(CAMILLE).isEmpty(), "views wait for the tick")
        service.tick()

        val camille = liveFor(CAMILLE).single()
        assertTrue(camille.mine && camille.author != null)
        val lune = liveFor(LUNE).single()
        assertTrue(!lune.mine && lune.author != null)
        assertNull(liveFor(MIKA).single().author)
        assertTrue(liveFor(KAEL).isEmpty(), "not a watcher")
        service.tick()
        assertEquals(1, liveFor(MIKA).size, "sent once")
    }

    @Test
    fun `an announcement notifies everyone but its author, anonymous or not`() {
        val named = write(KAEL, feed = FeedKind.ANNOUNCEMENTS).post!!
        val sent = sink.sent.single()
        assertNull(sent.players)
        assertEquals(listOf(KAEL), sent.except)
        assertEquals("post:${named.id}", sent.target)
        assertEquals("notif.lnbphone_feed.announcement", (sent.title.contents as TranslatableContents).key)
        assertEquals(listOf("Kael_Builder", "Salut"), args(sent.text))

        write(KAEL, feed = FeedKind.ANNOUNCEMENTS, text = "Secret", anonymous = true)
        val anonymous = sink.sent.last()
        assertEquals(listOf(KAEL), anonymous.except)
        val name = args(anonymous.text).first() as Component
        assertEquals("lnbphone_feed.post.anonymous", (name.contents as TranslatableContents).key)
        assertTrue(backend.pendingNotifications().join().let { it is BackendResult.Ok && it.body.items.isEmpty() })
    }

    @Test
    fun `a reply notifies the author of what it answers, and a deletion takes it back`() {
        open(MIKA)
        val post = write(CAMILLE).post!!
        val reply = replyTo(MIKA, post.id).post!!
        val sent = sink.sent.single()
        assertEquals(listOf(CAMILLE), sent.players)
        assertEquals("reply:global:${post.id}:${reply.id}", sent.target)
        assertEquals("notif.lnbphone_feed.reply", (sent.title.contents as TranslatableContents).key)

        service.handleRequest(CAMILLE, DeletePayload(99, post.id))
        assertEquals(Outcome.OK, host.received<ResultPayload>(CAMILLE).single { it.requestId == 99 }.outcome)
        assertEquals(listOf("post:${post.id}", "reply:global:${post.id}:${reply.id}"), sink.cleared)
        assertEquals(setOf(post.id, reply.id), host.received<RemovedPayload>(MIKA).single().items.map { it.id }.toSet())
    }

    @Test
    fun `a lost stream tells the watchers, and a reconnection drains, refreshes and resets`() {
        open(CAMILLE)
        val before = host.received<StatePayload>(CAMILLE).last().state
        backend.setOffline(true)
        val lost = host.received<StatePayload>(CAMILLE).last()
        assertEquals(Availability.UNAVAILABLE, lost.available)
        assertEquals(before, lost.state)

        backend.setOffline(false)
        assertEquals(1, host.received<ResetPayload>(CAMILLE).size)
        assertEquals(Availability.OK, host.received<StatePayload>(CAMILLE).last().available)
        assertTrue(host.received<ResetPayload>(MIKA).isEmpty(), "not a watcher")

        backend.setDisabled(true)
        assertEquals(Availability.DISABLED, host.received<StatePayload>(CAMILLE).last().available)
        service.handleRequest(CAMILLE, LoadFeedPayload(50, FeedKind.GLOBAL, null))
        assertEquals(Outcome.DISABLED, host.received<PagePayload>(CAMILLE).single().outcome)
    }

    @Test
    fun `refusals map to outcomes, and those about rights bring a fresh state`() {
        open(VISITOR)
        val states = host.received<StatePayload>(VISITOR).size
        assertEquals(Outcome.NOT_LINKED, write(VISITOR).outcome)
        assertEquals(states + 1, host.received<StatePayload>(VISITOR).size)

        assertEquals(Outcome.INVALID, write(CAMILLE, text = "   ").outcome)
        assertEquals(Outcome.NOT_ALLOWED, write(CAMILLE, feed = FeedKind.ANNOUNCEMENTS).outcome)

        val pinned = (1..4).map { write(LUNE, feed = FeedKind.ANNOUNCEMENTS, text = "A$it").post!!.id }
        for ((i, id) in pinned.take(3).withIndex()) service.handleRequest(LUNE, PinPayload(100 + i, id, pinned = true, replace = false))
        service.handleRequest(LUNE, PinPayload(200, pinned[3], pinned = true, replace = false))
        val limit = host.received<ResultPayload>(LUNE).single { it.requestId == 200 }
        assertEquals(Outcome.PIN_LIMIT, limit.outcome)
        assertEquals(pinned[0], limit.oldestPin)

        // An unpin that changes nothing is a success, even for a Global post.
        val global = write(LUNE).post!!
        service.handleRequest(LUNE, PinPayload(300, global.id, pinned = false, replace = false))
        assertEquals(Outcome.OK, host.received<ResultPayload>(LUNE).single { it.requestId == 300 }.outcome)

        backend.setWriting(false)
        assertEquals(Outcome.DISABLED, write(CAMILLE).outcome)
        assertFalse(service.rights.state(CAMILLE)!!.writing)
    }

    @Test
    fun `the server's own limit answers RATE_LIMITED without asking the site`() {
        val post = write(CAMILLE).post!!
        host.time = 10_000
        repeat(12) { service.handleRequest(MIKA, LikePayload(300 + it, post.id, it % 2 == 0)) }
        val answers = host.received<ResultPayload>(MIKA)
        assertEquals(10, answers.count { it.outcome == Outcome.OK })
        val limited = answers.filter { it.outcome == Outcome.RATE_LIMITED }
        assertEquals(2, limited.size)
        assertEquals(1, limited.first().retryAfter)
    }

    @Test
    fun `seen marks are for linked players only`() {
        val post = write(MIKA).post!!
        service.handleSeenFeed(VISITOR, FeedKind.GLOBAL, post.createdAt)
        assertTrue(backend.player(CAMILLE).join().let { it is BackendResult.Ok && it.body.unread.global })
        service.handleSeenFeed(CAMILLE, FeedKind.GLOBAL, post.createdAt)
        assertTrue(backend.player(CAMILLE).join().let { it is BackendResult.Ok && !it.body.unread.global })
    }

    @Test
    fun `a moderator's command tree is sent again when their state arrives, and on a rights change`() {
        assertTrue(LUNE in host.commandRefreshes)
        assertFalse(CAMILLE in host.commandRefreshes)
        assertTrue(service.rights.moderates(LUNE))
        assertFalse(service.rights.moderates(CAMILLE))
        val state = service.rights.state(KAEL)!!
        assertTrue(state.rights.announce)

        open(CAMILLE)
        backend.setAnnouncer(CAMILLE, true, null)
        assertTrue(host.received<StatePayload>(CAMILLE).last().state!!.rights.announce)
    }
}

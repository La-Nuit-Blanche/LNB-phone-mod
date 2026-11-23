package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Author
import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.PostView
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.network.CountsPayload
import fr.bde_teknantes.lnb.phone.feed.network.LivePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinsPayload
import fr.bde_teknantes.lnb.phone.feed.network.RemovedPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResetPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The relay with many watchers (`docs/feed-app.md` 4.4, `docs/feed-mod.md` 8.2 and 12): views asked in chunks of 200
 * viewers and 20 posts, each watcher sent only the views made for them, whatever the backend answers and whenever.
 */
class RelayTest {
    private val host = FakeHost()
    private val sink = RecordingSink()
    private val backend = ScriptedBackend().apply {
        auto = { call -> if (call.name == "player") BackendResult.Ok(ScriptedBackend.linked(call.arg(0), "p")) else null }
    }
    private val service = FeedService(backend, host, sink).apply { start() }
    private val relay get() = backend.listener!!
    private val time = Instant.parse("2026-11-23T20:00:00Z")
    private val bystander = UUID(9, 9)

    private fun watchers(count: Int): List<UUID> {
        val players = (1..count).map { UUID(1, it.toLong()) }
        host.online += players
        host.online += bystander
        players.forEach(service::handleOpen)
        return players
    }

    private fun live(player: UUID): List<PostView> = host.received<LivePayload>(player).flatMap { it.posts }

    /** Answers a views call as a site would, each viewer asked for getting views made for them. */
    private fun ScriptedBackend.Call.answerViews(extra: Map<UUID, List<PostView>> = emptyMap()) {
        val viewers = arg<List<UUID>>(0)
        val ids = arg<List<UUID>>(1)
        answer(BackendResult.Ok(viewers.associateWith { viewer -> ids.map { ScriptedBackend.viewFor(viewer, it) } } + extra))
    }

    @Test
    fun `450 watchers and 25 posts in one tick are asked in chunks of 200 viewers and 20 posts, each pair once`() {
        val watchers = watchers(450)
        val ids = (1..25).map { UUID(2, it.toLong()) }
        for (id in ids) relay.onPost(id, FeedKind.GLOBAL, null, time)
        service.tick()

        val calls = backend.calls("views")
        assertEquals(6, calls.size)
        val pairs = HashSet<Pair<UUID, UUID>>()
        for (call in calls) {
            val viewers = call.arg<List<UUID>>(0)
            val asked = call.arg<List<UUID>>(1)
            assertTrue(viewers.size in 1..200 && viewers.toSet().size == viewers.size, "viewers: ${viewers.size}")
            assertTrue(asked.size in 1..20 && asked.toSet().size == asked.size, "ids: ${asked.size}")
            assertTrue(bystander !in viewers)
            for (viewer in viewers) for (id in asked) assertTrue(pairs.add(viewer to id), "asked twice")
        }
        assertEquals(450 * 25, pairs.size)

        // Answered late, from another thread, and with views made for other players mixed in: each watcher still
        // gets their own views only, once, when the server thread runs.
        host.deferred = true
        val hostile = mapOf(bystander to listOf(ScriptedBackend.viewFor(bystander, ids[0])))
        Thread { calls.forEach { it.answerViews(extra = hostile) } }.apply { start() }.join()
        assertTrue(host.received<LivePayload>(watchers[0]).isEmpty(), "nothing before the server thread runs")
        host.runTasks()

        for (watcher in watchers) {
            val posts = live(watcher)
            assertTrue(posts.all { it.text == watcher.toString() }, "only views made for $watcher")
            assertEquals(ids.toSet(), posts.map { it.id }.toSet())
            assertTrue(host.received<LivePayload>(watcher).all { it.posts.size <= LivePayload.MAX })
        }
        assertTrue(host.sentTo(bystander).none { it is LivePayload }, "not a watcher")
    }

    @Test
    fun `a views answer naming a watcher of another chunk doesn't reach them from that call`() {
        val watchers = watchers(201)
        val id = UUID(2, 1)
        relay.onPost(id, FeedKind.GLOBAL, null, time)
        service.tick()
        val (first, second) = backend.calls("views")
        val inFirst = first.arg<List<UUID>>(0)
        val inSecond = second.arg<List<UUID>>(0).single()
        assertTrue(inSecond !in inFirst)
        // The first call's answer also holds a view for the watcher of the second chunk, made for someone else.
        first.answerViews(extra = mapOf(inSecond to listOf(ScriptedBackend.viewFor(watchers[0], id))))
        assertTrue(live(inSecond).isEmpty())
        second.answerViews()
        assertEquals(listOf(inSecond.toString()), live(inSecond).map { it.text })
    }

    @Test
    fun `a watcher who left before the answer, a failed answer and a broken future send nothing`() {
        val (camille, mika) = watchers(2)
        relay.onPost(UUID(2, 1), FeedKind.GLOBAL, null, time)
        service.tick()
        service.onLeave(mika)
        host.online -= mika
        host.online += mika // back, but not a watcher until they open Feed again
        backend.calls("views").single().answerViews()
        assertEquals(1, live(camille).size)
        assertTrue(live(mika).isEmpty())

        relay.onPost(UUID(2, 2), FeedKind.GLOBAL, null, time)
        service.tick()
        backend.calls("views").last().answer(BackendResult.Unavailable("down"))
        relay.onPost(UUID(2, 3), FeedKind.GLOBAL, null, time)
        service.tick()
        backend.calls("views").last().fail(IllegalStateException("boom"))
        assertEquals(1, live(camille).size)
    }

    @Test
    fun `posts of different ticks are separate calls, a post announced twice in a tick is asked once, and nobody watching asks nothing`() {
        relay.onPost(UUID(2, 1), FeedKind.GLOBAL, null, time)
        service.tick()
        assertTrue(backend.calls("views").isEmpty(), "no watchers")
        service.tick()
        assertTrue(backend.calls("views").isEmpty(), "and the post isn't kept for later")

        watchers(1)
        relay.onPost(UUID(2, 2), FeedKind.GLOBAL, null, time)
        relay.onPost(UUID(2, 2), FeedKind.GLOBAL, null, time)
        service.tick()
        relay.onPost(UUID(2, 3), FeedKind.ANNOUNCEMENTS, null, time)
        service.tick()
        assertEquals(listOf(listOf(UUID(2, 2)), listOf(UUID(2, 3))), backend.calls("views").map { it.arg<List<UUID>>(1) })
    }

    @Test
    fun `deletions, counts and pins reach the watchers in payloads within their bounds`() {
        val (camille) = watchers(1)
        val removed = (1..600).map { RemovedItem(UUID(3, it.toLong()), FeedKind.GLOBAL, if (it == 1) null else UUID(3, 1)) }
        relay.onDeleted(removed)
        assertEquals(listOf(512, 88), host.received<RemovedPayload>(camille).map { it.items.size })
        assertEquals(600, sink.cleared.size, "each row's notifications taken back")
        assertEquals("post:${UUID(3, 1)}", sink.cleared.first())
        assertEquals("reply:global:${UUID(3, 1)}:${UUID(3, 2)}", sink.cleared[1])

        relay.onCounts((1..300).map { CountUpdate(UUID(3, it.toLong()), it, 0) })
        assertEquals(listOf(256, 44), host.received<CountsPayload>(camille).map { it.items.size })

        relay.onPins((1..5).map { Pin(UUID(4, it.toLong()), time) })
        assertEquals(3, host.received<PinsPayload>(camille).single().pinned.size)
        assertTrue(host.sentTo(bystander).isEmpty())
    }

    @Test
    fun `a lost stream sends each watcher their own last state, and a reconnection refreshes everyone and resets the watchers`() {
        val (camille, mika) = watchers(2)
        relay.onDisconnected(false)
        assertEquals(Availability.UNAVAILABLE, host.received<StatePayload>(camille).last().available)
        assertEquals(camille, host.received<StatePayload>(camille).last().state?.me?.uuid)
        assertEquals(mika, host.received<StatePayload>(mika).last().state?.me?.uuid)
        relay.onDisconnected(true)
        assertEquals(Availability.DISABLED, host.received<StatePayload>(mika).last().available)
        assertTrue(host.sentTo(bystander).isEmpty())

        val reads = backend.calls("player").size
        val drains = backend.calls("pendingNotifications").size
        relay.onConnected()
        assertEquals(drains, backend.calls("pendingNotifications").size, "a drain still runs: the next one waits for it")
        assertEquals(reads + 3, backend.calls("player").size, "every online player, watcher or not")
        assertEquals(1, host.received<ResetPayload>(camille).size)
        assertEquals(Availability.OK, host.received<StatePayload>(camille).last().available)
        assertTrue(host.sentTo(bystander).none { it is ResetPayload || it is StatePayload })
    }

    @Test
    fun `rights events refresh only online players`() {
        watchers(1)
        val before = backend.calls("player").size
        relay.onRights(listOf(UUID(1, 1), UUID(7, 7), bystander))
        assertEquals(listOf(UUID(1, 1), bystander), backend.calls("player").drop(before).map { it.arg<UUID>(0) })
    }

    @Test
    fun `an anonymous announcement's author is filled only in the views of its author and the moderators`() {
        // Over the memory backend: the site's own rules decide the views, the relay must keep each where it belongs.
        val clock = MutableClock()
        val memory = fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryBackend(clock, Players.directory(), guests = listOf("Visitor42"), rateLimits = false)
        val host = FakeHost().apply { online += listOf(Players.CAMILLE, Players.LUNE, Players.KAEL, Players.VISITOR, Players.MIKA) }
        val sink = RecordingSink()
        val service = FeedService(memory, host, sink).apply { start() }
        memory.setAnnouncer(Players.KAEL, true, null)
        listOf(Players.CAMILLE, Players.LUNE, Players.KAEL, Players.VISITOR).forEach(service::handleOpen)
        service.handleRequest(Players.KAEL, fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload(1, UUID(5, 5), FeedKind.ANNOUNCEMENTS, "Secret", true))
        service.tick()

        fun liveFor(player: UUID) = host.received<LivePayload>(player).flatMap { it.posts }.single()
        assertEquals(Author(Players.KAEL, "Kael_Builder", null, true), liveFor(Players.KAEL).author)
        assertEquals("Kael_Builder", liveFor(Players.LUNE).author?.username)
        assertEquals(null, liveFor(Players.CAMILLE).author)
        assertEquals(null, liveFor(Players.VISITOR).author)
        assertTrue(host.sentTo(Players.MIKA).isEmpty(), "not a watcher")
        // Kael's UUID reaches the phone as the notification's except, and no player's client.
        assertEquals(listOf(Players.KAEL), sink.sent.single().except)
        for (player in listOf(Players.CAMILLE, Players.VISITOR)) {
            assertTrue(host.received<LivePayload>(player).flatMap { it.posts }.none { it.author?.uuid == Players.KAEL })
        }
    }
}

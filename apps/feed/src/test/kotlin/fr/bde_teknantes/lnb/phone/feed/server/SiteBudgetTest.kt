package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The site lets the whole Minecraft server make 1,200 calls a minute (`feed-mod.md` 7): no client, modified or not,
 * can spend more than a small share of it, all of them together stay well under it, and a normal player never
 * notices (`feed-app.md` 4.3).
 */
class SiteBudgetTest {
    private val host = FakeHost()
    private val backend = ScriptedBackend()
    private val service = FeedService(backend, host, RecordingSink())
    private val post = UUID(2, 1)

    init {
        backend.auto = { call ->
            when (call.name) {
                "player" -> BackendResult.Ok(ScriptedBackend.linked(call.arg(0), "p"))
                "page" -> BackendResult.Ok(FeedPage(FeedKind.GLOBAL, emptyList(), emptyList(), null))
                "thread" -> BackendResult.Ok(ThreadPage(ScriptedBackend.viewFor(call.arg(0), post), emptyList(), null))
                "like" -> BackendResult.Ok(LikeState(post, 1, true))
                "createPost" -> BackendResult.Refused(403, "notAllowed")
                else -> BackendResult.Ok(Unit)
            }
        }
    }

    private fun siteCalls() = backend.calls.count { it.name != "pendingNotifications" }

    @Test
    fun `a client sending every 10 ms for a minute makes at most its share of calls`() {
        val camille = UUID(1, 1)
        host.online += camille
        var id = 0
        for (ms in 0L until 60_000L step 10L) {
            host.time = ms
            service.handleRequest(camille, LoadFeedPayload(id++, FeedKind.GLOBAL, null))
            service.handleRequest(camille, LikePayload(id++, post, true))
            service.handleOpen(camille)
            service.handleSeenFeed(camille, FeedKind.GLOBAL, Instant.EPOCH)
        }
        val calls = siteCalls()
        assertTrue(calls <= PlayerLimits.SHARE_BURST + PlayerLimits.SHARE_PER_MINUTE, "$calls calls in a minute")
        assertTrue(calls >= 90, "and still its share: $calls")
    }

    @Test
    fun `many clients together stay under the server-wide budget, far below the site's 1,200`() {
        val players = (1..40).map { UUID(1, it.toLong()) }
        host.online += players
        var id = 0
        for (ms in 0L until 60_000L step 100L) {
            host.time = ms
            for (player in players) service.handleRequest(player, LoadThreadPayload(id++, post, null))
        }
        val calls = siteCalls()
        assertTrue(calls <= PlayerLimits.SERVER_BURST + PlayerLimits.SERVER_PER_MINUTE, "$calls calls in a minute")
    }

    @Test
    fun `refusals that bring a state read count against the player`() {
        val camille = UUID(1, 1)
        host.online += camille
        var id = 0
        repeat(60) {
            service.handleRequest(camille, CreatePostPayload(id++, UUID(3, it.toLong()), FeedKind.ANNOUNCEMENTS, "x", false))
            host.time += 10
        }
        val writes = backend.calls("createPost").size
        val reads = backend.calls("player").size
        assertEquals(10, writes, "the writes' burst")
        assertEquals(10, reads, "one state read per refusal, within the reads' allowance")
        // Once the reads' allowance is spent, refusals bring no read at all.
        repeat(30) {
            host.time += 2_000
            service.handleRequest(camille, CreatePostPayload(id++, UUID(4, it.toLong()), FeedKind.ANNOUNCEMENTS, "x", false))
            repeat(5) { service.handleOpen(camille) }
        }
        assertTrue(backend.calls("player").size <= 20 + 60 + 2, "${backend.calls("player").size} state reads")
        assertTrue(host.received<ResultPayload>(camille).none { it.outcome == Outcome.OK })
    }

    @Test
    fun `leaving and joining again in a loop gets no fresh allowance`() {
        val camille = UUID(1, 1)
        host.online += camille
        repeat(200) {
            service.onJoin(camille)
            service.handleOpen(camille)
            service.onLeave(camille)
        }
        assertEquals(20, backend.calls("player").size, "the reads' burst, once: joins and opens share it")
    }

    @Test
    fun `a normal player scrolling two feeds, opening threads and liking quickly is never refused`() {
        val camille = UUID(1, 1)
        host.online += camille
        var id = 0
        service.onJoin(camille)
        service.handleOpen(camille)
        service.handleRequest(camille, LoadFeedPayload(id++, FeedKind.ANNOUNCEMENTS, null))
        service.handleRequest(camille, LoadFeedPayload(id++, FeedKind.GLOBAL, null))
        repeat(10) { service.handleRequest(camille, LikePayload(id++, post, true)) }
        // Five minutes: a page and a seen mark every 4 seconds, a thread every 10, a like every 3.
        for (s in 1..300) {
            host.time = s * 1_000L
            if (s % 4 == 0) {
                service.handleRequest(camille, LoadFeedPayload(id++, FeedKind.GLOBAL, "c$s"))
                service.handleSeenFeed(camille, FeedKind.GLOBAL, Instant.EPOCH)
            }
            if (s % 10 == 0) service.handleRequest(camille, LoadThreadPayload(id++, post, null))
            if (s % 3 == 0) service.handleRequest(camille, LikePayload(id++, post, s % 2 == 0))
        }
        val refused = host.received<PagePayload>(camille).count { it.outcome != Outcome.OK } +
            host.received<ThreadPayload>(camille).count { it.outcome != Outcome.OK } +
            host.received<ResultPayload>(camille).count { it.outcome != Outcome.OK }
        assertEquals(0, refused)
    }

    @Test
    fun `when the whole server's budget is spent, every request is answered so the client can recover`() {
        val players = (1..150).map { UUID(1, it.toLong()) }
        host.online += players
        // 150 players tap the same notification at once: more opens than the server's burst of 100.
        for (player in players) service.handleOpen(player)
        for (player in players) {
            val states = host.received<StatePayload>(player)
            assertEquals(1, states.size, "every open is answered")
        }
        val turnedAway = players.filter { host.received<StatePayload>(it).single().available == Availability.UNAVAILABLE }
        assertTrue(turnedAway.size >= 50, "${turnedAway.size} told to retry")

        // The other kinds answer RATE_LIMITED with the seconds to wait; only seen marks may go unanswered.
        val late = turnedAway.first()
        var id = 0
        val requests = listOf(
            LoadFeedPayload(id++, FeedKind.GLOBAL, null), LoadThreadPayload(id++, post, null), LikePayload(id++, post, true),
            CreatePostPayload(id++, UUID(5, 5), FeedKind.GLOBAL, "x", false),
        )
        for (request in requests) service.handleRequest(late, request)
        val answers = host.received<PagePayload>(late).map { it.outcome to null } +
            host.received<ThreadPayload>(late).map { it.outcome to null } +
            host.received<ResultPayload>(late).map { it.outcome to it.retryAfter }
        assertEquals(requests.size, answers.size)
        assertTrue(answers.all { it.first == Outcome.RATE_LIMITED })
        assertTrue(host.received<ResultPayload>(late).all { it.retryAfter == 1 })
    }
}

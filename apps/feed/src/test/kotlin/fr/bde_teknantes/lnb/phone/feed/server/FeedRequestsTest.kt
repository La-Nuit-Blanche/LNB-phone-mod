package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.Availability
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.Rights
import fr.bde_teknantes.lnb.phone.feed.network.CreatePostPayload
import fr.bde_teknantes.lnb.phone.feed.network.CreateReplyPayload
import fr.bde_teknantes.lnb.phone.feed.network.DeletePayload
import fr.bde_teknantes.lnb.phone.feed.network.FeedRequest
import fr.bde_teknantes.lnb.phone.feed.network.LikePayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.LoadThreadPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.network.PinPayload
import fr.bde_teknantes.lnb.phone.feed.network.ResultPayload
import fr.bde_teknantes.lnb.phone.feed.network.StatePayload
import fr.bde_teknantes.lnb.phone.feed.network.ThreadPayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.Created
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedPage
import fr.bde_teknantes.lnb.phone.feed.server.backend.LikeState
import fr.bde_teknantes.lnb.phone.feed.server.backend.PinState
import fr.bde_teknantes.lnb.phone.feed.server.backend.ThreadPage
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * What a client can make the server do, over a scripted backend (`docs/feed-app.md` 4.3 and 5.3, `docs/feed-mod.md` 5
 * and 11): the server's own checks, the per-player limits with answers that come late and from another thread, and
 * each failure of the site as the outcome the client gets.
 */
class FeedRequestsTest {
    private val host = FakeHost()
    private val backend = ScriptedBackend()
    private val service = FeedService(backend, host, RecordingSink())

    private val camille = UUID(1, 1)
    private val mika = UUID(1, 2)
    private val post = UUID(2, 1)
    private val thread = UUID(2, 2)
    private val time = Instant.parse("2026-11-23T20:00:00Z")

    init {
        host.online += listOf(camille, mika)
        backend.auto = { call ->
            when (call.name) {
                "player" -> BackendResult.Ok(ScriptedBackend.linked(call.arg(0), if (call.arg<UUID>(0) == camille) "Cam_Roux" else "MikaPvP"))
                "pendingNotifications" -> BackendResult.Ok(fr.bde_teknantes.lnb.phone.feed.server.backend.NotificationBatch(emptyList(), false))
                else -> null
            }
        }
        service.start()
        service.onJoin(camille)
        service.onJoin(mika)
    }

    private fun view(id: UUID = post, viewer: UUID = camille) = ScriptedBackend.viewFor(viewer, id)

    private fun every(name: String, result: BackendResult<*>) {
        val before = backend.auto
        backend.auto = { call -> if (call.name == name) result else before(call) }
    }

    private fun sendAll(player: UUID, vararg requests: FeedRequest) = requests.forEach { service.handleRequest(player, it) }

    private fun answer(player: UUID, requestId: Int): CustomPacketPayload = host.sentTo(player).single {
        (it as? ResultPayload)?.requestId == requestId || (it as? PagePayload)?.requestId == requestId || (it as? ThreadPayload)?.requestId == requestId
    }

    private fun outcome(player: UUID, requestId: Int): Outcome = when (val payload = answer(player, requestId)) {
        is ResultPayload -> payload.outcome
        is PagePayload -> payload.outcome
        is ThreadPayload -> payload.outcome
        else -> error("not an answer: $payload")
    }

    private fun backendCalls() = backend.calls.filter { it.name != "player" && it.name != "pendingNotifications" }

    /** One request of every kind, all with [id]. */
    private fun everyRequest(id: Int): List<FeedRequest> = listOf(
        LoadFeedPayload(id, FeedKind.GLOBAL, null),
        LoadThreadPayload(id, thread, null),
        CreatePostPayload(id, UUID(3, 1), FeedKind.GLOBAL, "Salut", false),
        CreateReplyPayload(id, UUID(3, 2), thread, null, "Moi !", false),
        LikePayload(id, post, true),
        PinPayload(id, post, true, false),
        DeletePayload(id, post),
    )

    // ---- The server's own checks ----

    @Test
    fun `a text the phone wouldn't send is INVALID, and the site never hears of it`() {
        val refused = listOf("a".repeat(281), "👍".repeat(281), "   \n  ", "", " ".repeat(500))
        for ((i, text) in refused.withIndex()) {
            service.handleRequest(camille, CreatePostPayload(i, UUID(3, i.toLong()), FeedKind.GLOBAL, text, false))
            service.handleRequest(camille, CreateReplyPayload(100 + i, UUID(4, i.toLong()), thread, null, text, true))
            assertEquals(Outcome.INVALID, outcome(camille, i))
            assertEquals(Outcome.INVALID, outcome(camille, 100 + i))
        }
        assertTrue(backendCalls().isEmpty())
    }

    @Test
    fun `a text within 280 is sent trimmed, and the longest emoji text passes`() {
        service.handleRequest(camille, CreatePostPayload(1, UUID(3, 1), FeedKind.GLOBAL, "  " + "a".repeat(280) + " \n ", false))
        service.handleRequest(camille, CreateReplyPayload(2, UUID(3, 2), thread, post, "\n👍".repeat(1) + "👍".repeat(279) + "  ", true))
        val (created, replied) = backendCalls()
        assertEquals("a".repeat(280), created.arg<String>(3))
        assertEquals("👍".repeat(280), replied.arg<String>(4))
        assertEquals(post, replied.arg<UUID>(3))
    }

    @Test
    fun `a cursor past 128 characters is INVALID without a call`() {
        sendAll(camille, LoadFeedPayload(1, FeedKind.GLOBAL, "c".repeat(129)), LoadThreadPayload(2, thread, "c".repeat(129)))
        assertEquals(Outcome.INVALID, outcome(camille, 1))
        assertEquals(FeedKind.GLOBAL, (answer(camille, 1) as PagePayload).feed)
        assertEquals(Outcome.INVALID, outcome(camille, 2))
        assertTrue(backendCalls().isEmpty())
        sendAll(camille, LoadFeedPayload(3, FeedKind.ANNOUNCEMENTS, "c".repeat(128)))
        assertEquals(listOf("c".repeat(128)), backendCalls().map { it.arg<String>(2) })
    }

    /** What a site answers to each call, for the viewer it names. */
    private fun okFor(call: ScriptedBackend.Call): BackendResult<*>? = when (call.name) {
        "player" -> BackendResult.Ok(ScriptedBackend.linked(call.arg(0), "p"))
        "page" -> BackendResult.Ok(FeedPage(call.arg(1), emptyList(), listOf(view(viewer = call.arg(0))), null))
        "thread" -> BackendResult.Ok(ThreadPage(view(thread, call.arg(0)), emptyList(), null))
        "createPost", "createReply" -> BackendResult.Ok(Created(false, view(viewer = call.arg(1))))
        "like" -> BackendResult.Ok(LikeState(call.arg(1), 1, call.arg(2)))
        "pin" -> BackendResult.Ok(PinState(listOf(Pin(call.arg(1), time)), null))
        else -> BackendResult.Ok(Unit)
    }

    @Test
    fun `every call acts for the sender, and each answer goes to the sender only, with the sender's request ID`() {
        backend.auto = ::okFor
        for (request in everyRequest(7)) service.handleRequest(mika, request)
        for (call in backendCalls()) assertEquals(mika, call.args.first { it is UUID && (it == mika || it == camille) }, "$call")
        assertEquals(listOf(FeedKind.GLOBAL, 20), backendCalls().single { it.name == "page" }.args.drop(1).let { listOf(it[0], it[2]) })
        assertEquals(50, backendCalls().single { it.name == "thread" }.arg<Int>(3))
        assertEquals(7, host.sentTo(mika).size)
        assertTrue(host.sentTo(mika).all { it !is PagePayload || it.posts.single().text == mika.toString() })
        assertTrue(host.sentTo(camille).isEmpty())

        // Both send request ID 8, answered in the other order: each gets only their own answer.
        backend.auto = { null }
        service.handleRequest(mika, LikePayload(8, post, true))
        service.handleRequest(camille, LikePayload(8, post, false))
        val (mikaLike, camilleLike) = backend.waiting("like")
        assertEquals(listOf(mika, camille), listOf(mikaLike.arg<UUID>(0), camilleLike.arg<UUID>(0)))
        camilleLike.answer(BackendResult.Ok(LikeState(post, 0, false)))
        mikaLike.answer(BackendResult.Ok(LikeState(post, 1, true)))
        assertEquals(ResultPayload(8, Outcome.OK, likes = 0, liked = false), host.received<ResultPayload>(camille).single())
        assertEquals(ResultPayload(8, Outcome.OK, likes = 1, liked = true), host.received<ResultPayload>(mika).last())
    }

    @Test
    fun `each write answers with what its kind carries`() {
        backend.auto = { call ->
            when (call.name) {
                "createPost" -> BackendResult.Ok(Created(true, view()))
                "createReply" -> BackendResult.Ok(Created(false, view(UUID(2, 9))))
                "like" -> BackendResult.Ok(LikeState(post, 13, true))
                "pin" -> BackendResult.Ok(PinState(listOf(Pin(post, time)) + List(4) { Pin(UUID(5, it.toLong()), time) }, UUID(6, 6)))
                "delete" -> BackendResult.Ok(Unit)
                else -> null
            }
        }
        sendAll(camille, *everyRequest(1).drop(2).toTypedArray())
        val results = host.received<ResultPayload>(camille)
        assertEquals(post, results[0].post?.id)
        assertEquals(UUID(2, 9), results[1].post?.id)
        assertEquals(13 to true, results[2].likes to results[2].liked)
        assertEquals(3, results[3].pinned?.size, "a group past 3 is cut to what the payload carries")
        assertEquals(UUID(6, 6), results[3].unpinned)
        assertEquals(ResultPayload(1, Outcome.OK), results[4])
    }

    @Test
    fun `a page or thread past the payloads' bounds is cut, never sent whole`() {
        sendAll(camille, LoadFeedPayload(1, FeedKind.ANNOUNCEMENTS, null), LoadThreadPayload(2, thread, null))
        val (page, threadCall) = backendCalls()
        page.answer(BackendResult.Ok(FeedPage(FeedKind.ANNOUNCEMENTS, List(5) { view(UUID(7, it.toLong())) }, List(60) { view(UUID(8, it.toLong())) }, "n")))
        threadCall.answer(BackendResult.Ok(ThreadPage(view(thread), List(70) { view(UUID(9, it.toLong())) }, null)))
        val sentPage = answer(camille, 1) as PagePayload
        assertEquals(3 to 50, sentPage.pinned.size to sentPage.posts.size)
        assertEquals("n", sentPage.next)
        assertEquals(50, (answer(camille, 2) as ThreadPayload).replies.size)
    }

    // ---- Failures of the site ----

    @Test
    fun `the site unreachable is UNAVAILABLE for every request, and for a broken future too`() {
        for (name in listOf("page", "thread", "createPost", "createReply", "like", "pin", "delete")) every(name, BackendResult.Unavailable("down"))
        sendAll(camille, *everyRequest(1).toTypedArray())
        val answers = host.sentTo(camille).filter { it !is StatePayload }
        assertEquals(7, answers.size)
        assertEquals(PagePayload.failed(1, Outcome.UNAVAILABLE, FeedKind.GLOBAL), answers[0])
        assertEquals(ThreadPayload.failed(1, Outcome.UNAVAILABLE), answers[1])
        assertTrue(answers.drop(2).all { it == ResultPayload(1, Outcome.UNAVAILABLE) })

        backend.auto = { null }
        service.handleRequest(mika, LikePayload(2, post, true))
        backendCalls().last().fail(RuntimeException("boom"))
        assertEquals(Outcome.UNAVAILABLE, outcome(mika, 2))
    }

    @Test
    fun `each refusal of the site becomes the outcome of section 5_3`() {
        val cases = listOf(
            BackendResult.Refused(400, null) to Outcome.UNAVAILABLE,
            BackendResult.Refused(400, "emptyText") to Outcome.INVALID,
            BackendResult.Refused(400, "tooLong") to Outcome.INVALID,
            BackendResult.Refused(409, "idempotencyMismatch") to Outcome.INVALID,
            BackendResult.Refused(404, "notFound") to Outcome.NOT_FOUND,
            BackendResult.Refused(409, "notPinnable") to Outcome.NOT_PINNABLE,
            BackendResult.Refused(404, null) to Outcome.UNAVAILABLE,
            BackendResult.Refused(418, "teapot") to Outcome.UNAVAILABLE,
            BackendResult.Disabled("feed") to Outcome.DISABLED,
            BackendResult.Disabled("feed.serverApi") to Outcome.DISABLED,
        )
        for ((i, case) in cases.withIndex()) {
            val (result, expected) = case
            backend.auto = { call -> if (call.name == "createPost") result else null }
            val reads = backend.calls("player").size
            service.handleRequest(camille, CreatePostPayload(i, UUID(3, i.toLong()), FeedKind.GLOBAL, "Salut", false))
            assertEquals(expected, outcome(camille, i), "$result")
            assertEquals(reads, backend.calls("player").size, "$result says nothing of the player's rights")
        }
    }

    @Test
    fun `a thread or page the site won't give answers its outcome with nothing of it`() {
        backend.auto = { call ->
            when (call.name) {
                "thread" -> BackendResult.Refused(404, "notFound")
                "page" -> BackendResult.Disabled("feed")
                else -> null
            }
        }
        sendAll(camille, LoadThreadPayload(1, thread, null), LoadFeedPayload(2, FeedKind.ANNOUNCEMENTS, "c1"))
        assertEquals(ThreadPayload(1, Outcome.NOT_FOUND, null, emptyList(), null), answer(camille, 1))
        assertEquals(PagePayload(2, Outcome.DISABLED, FeedKind.ANNOUNCEMENTS, emptyList(), emptyList(), null), answer(camille, 2))
    }

    @Test
    fun `rate limits carry the site's seconds, or 1 when it gives none`() {
        backend.auto = { call -> if (call.name == "like") BackendResult.Refused(429, "rateLimited", retryAfterSeconds = 40) else null }
        service.handleRequest(camille, LikePayload(1, post, true))
        assertEquals(ResultPayload(1, Outcome.RATE_LIMITED, retryAfter = 40), answer(camille, 1))
        backend.auto = { call -> if (call.name == "like") BackendResult.Refused(429, null) else null }
        service.handleRequest(camille, LikePayload(2, post, true))
        assertEquals(ResultPayload(2, Outcome.RATE_LIMITED, retryAfter = 1), answer(camille, 2))
        backend.auto = { call -> if (call.name == "pin") BackendResult.Refused(409, "pinLimit", oldestPin = UUID(6, 1)) else null }
        service.handleRequest(camille, PinPayload(3, post, true, false))
        assertEquals(ResultPayload(3, Outcome.PIN_LIMIT, oldestPin = UUID(6, 1)), answer(camille, 3))
    }

    @Test
    fun `banned, not allowed and unlinked answers bring the player's new state, which a watcher is sent`() {
        service.handleOpen(camille)
        val cases = listOf(
            BackendResult.Refused(403, "banned") to Outcome.BANNED,
            BackendResult.Refused(403, "notAllowed") to Outcome.NOT_ALLOWED,
            BackendResult.Refused(404, "notLinked") to Outcome.NOT_LINKED,
            BackendResult.Disabled("feed.writing") to Outcome.DISABLED,
        )
        for ((i, case) in cases.withIndex()) {
            val (result, expected) = case
            val states = host.received<StatePayload>(camille).size
            backend.auto = { call ->
                when (call.name) {
                    "createPost" -> result
                    // Unlinked while online: the site now says guest.
                    "player" -> BackendResult.Ok(ScriptedBackend.GUEST)
                    else -> null
                }
            }
            service.handleRequest(camille, CreatePostPayload(i, UUID(3, i.toLong()), FeedKind.GLOBAL, "Salut", false))
            assertEquals(expected, outcome(camille, i))
            assertEquals(states + 1, host.received<StatePayload>(camille).size, "$result")
            assertFalse(host.received<StatePayload>(camille).last().state!!.linked)
        }
        // A player who isn't watching is read again too, but sent nothing.
        val before = host.sentTo(mika).size
        service.handleRequest(mika, CreatePostPayload(9, UUID(3, 9), FeedKind.GLOBAL, "Salut", false))
        assertEquals(before + 1, host.sentTo(mika).size)
        assertFalse(service.rights.state(mika)!!.linked)
    }

    @Test
    fun `open while the site is away answers its availability with the last state`() {
        service.handleOpen(camille)
        val known = host.received<StatePayload>(camille).last().state
        backend.auto = { call -> if (call.name == "player") BackendResult.Unavailable("down") else null }
        service.handleOpen(camille)
        assertEquals(StatePayload(Availability.UNAVAILABLE, known), host.received<StatePayload>(camille).last())
        backend.auto = { call -> if (call.name == "player") BackendResult.Disabled("feed") else null }
        service.handleOpen(camille)
        assertEquals(StatePayload(Availability.DISABLED, known), host.received<StatePayload>(camille).last())

        // Someone whose state was never read gets no state at all, only the reason.
        val newcomer = UUID(1, 9)
        host.online += newcomer
        service.handleOpen(newcomer)
        assertEquals(StatePayload(Availability.DISABLED, null), host.received<StatePayload>(newcomer).single())
        assertTrue(service.isWatcher(newcomer), "a watcher still, to hear when Feed comes back")
    }

    // ---- Limits ----

    @Test
    fun `at most 4 calls in flight per player, freed only on the server thread when a late answer comes`() {
        backend.auto = { null }
        host.deferred = true
        for (i in 1..4) service.handleRequest(camille, LoadFeedPayload(i, FeedKind.GLOBAL, null))
        service.handleRequest(camille, LikePayload(5, post, true))
        service.handleOpen(camille)
        service.handleSeenFeed(camille, FeedKind.GLOBAL, time)
        assertEquals(ResultPayload(5, Outcome.RATE_LIMITED, retryAfter = 1), answer(camille, 5))
        assertEquals(4, backend.calls("page").size)
        assertEquals(2, backend.calls("player").size, "the open past the limit asked nothing (only the join's read)")
        assertTrue(backend.calls("markFeedSeen").isEmpty())

        // Another player isn't held up.
        service.handleRequest(mika, LikePayload(1, post, true))
        assertEquals(1, backend.calls("like").size)

        Thread { backend.calls("page").first().answer(BackendResult.Ok(FeedPage(FeedKind.GLOBAL, emptyList(), emptyList(), null))) }.apply { start() }.join()
        service.handleRequest(camille, LikePayload(6, post, true))
        assertEquals(ResultPayload(6, Outcome.RATE_LIMITED, retryAfter = 1), answer(camille, 6), "the answer hasn't reached the server thread")
        host.runTasks()
        assertEquals(Outcome.OK, outcome(camille, 1))
        service.handleRequest(camille, LikePayload(7, post, true))
        assertEquals(2, backend.calls("like").size, "a slot is free again")
    }

    @Test
    fun `reads come in a burst of 20, then 1 a second, the join's read included`() {
        every("page", BackendResult.Ok(FeedPage(FeedKind.GLOBAL, emptyList(), emptyList(), null)))
        // The join took one read: give it back first.
        host.time += 1_000
        for (i in 1..21) service.handleRequest(camille, LoadFeedPayload(i, FeedKind.GLOBAL, null))
        assertEquals(20, backend.calls("page").size)
        assertEquals(Outcome.RATE_LIMITED, outcome(camille, 21))
        host.time += 500
        service.handleRequest(camille, LoadThreadPayload(22, thread, null))
        assertEquals(Outcome.RATE_LIMITED, outcome(camille, 22), "loads of threads share the reads' allowance")
        host.time += 500
        service.handleRequest(camille, LoadFeedPayload(23, FeedKind.GLOBAL, null))
        assertEquals(21, backend.calls("page").size)
        // Reads don't use up writes.
        every("like", BackendResult.Ok(LikeState(post, 1, true)))
        service.handleRequest(camille, LikePayload(24, post, true))
        assertEquals(Outcome.OK, outcome(camille, 24))
    }

    @Test
    fun `writes come in a burst of 10, then 1 every 2 seconds`() {
        every("delete", BackendResult.Ok(Unit))
        for (i in 1..11) service.handleRequest(camille, DeletePayload(i, post))
        assertEquals(10, backend.calls("delete").size)
        assertEquals(ResultPayload(11, Outcome.RATE_LIMITED, retryAfter = 1), answer(camille, 11))
        host.time += 1_000
        service.handleRequest(camille, PinPayload(12, post, false, false))
        assertEquals(Outcome.RATE_LIMITED, outcome(camille, 12))
        host.time += 1_000
        every("pin", BackendResult.Ok(PinState(emptyList(), null)))
        service.handleRequest(camille, PinPayload(13, post, false, false))
        assertEquals(Outcome.OK, outcome(camille, 13))
    }

    @Test
    fun `seen marks past a burst of 3, then 1 every 2 seconds, are dropped, and a guest's are never sent`() {
        every("markFeedSeen", BackendResult.Ok(Unit))
        every("markThreadSeen", BackendResult.Ok(Unit))
        repeat(5) { service.handleSeenFeed(camille, FeedKind.GLOBAL, time) }
        service.handleSeenThread(camille, thread)
        assertEquals(3, backend.calls("markFeedSeen").size)
        assertTrue(backend.calls("markThreadSeen").isEmpty())
        host.time += 2_000
        service.handleSeenThread(camille, thread)
        assertEquals(listOf(camille, thread), backend.calls("markThreadSeen").single().args)
        assertTrue(host.sentTo(camille).isEmpty(), "seen marks have no answer")

        val guest = UUID(1, 5)
        host.online += guest
        backend.auto = { call -> if (call.name == "player") BackendResult.Ok(ScriptedBackend.GUEST) else BackendResult.Ok(Unit) }
        service.onJoin(guest)
        service.handleSeenFeed(guest, FeedKind.GLOBAL, time)
        service.handleSeenThread(guest, thread)
        // Nor one whose state isn't known yet.
        service.handleSeenFeed(UUID(1, 6), FeedKind.GLOBAL, time)
        assertEquals(3, backend.calls("markFeedSeen").size)
        assertEquals(1, backend.calls("markThreadSeen").size)
    }

    @Test
    fun `opens past the reads' burst are dropped without an answer`() {
        for (i in 1..25) service.handleOpen(camille)
        assertEquals(1 + 19, backend.calls("player").count { it.arg<UUID>(0) == camille }, "the join's read and 19 opens: 20 reads")
    }

    @Test
    fun `answers to calls made before a player left free their own slots, not the next visit's`() {
        backend.auto = { call -> if (call.name == "player") BackendResult.Ok(ScriptedBackend.linked(call.arg(0), "p")) else null }
        host.time += 1_000
        for (i in 1..4) service.handleRequest(camille, LoadFeedPayload(i, FeedKind.GLOBAL, null))
        service.onLeave(camille)
        service.onJoin(camille)
        for (i in 11..14) service.handleRequest(camille, LoadFeedPayload(i, FeedKind.GLOBAL, null))
        // Leaving resets nothing: the 4 calls of the last visit still wait, so the new visit gets none, nor its join read.
        assertEquals(4, backend.calls("page").size)
        assertEquals(Outcome.RATE_LIMITED, outcome(camille, 11))
        backend.calls("page").take(2).forEach { it.answer(BackendResult.Ok(FeedPage(FeedKind.GLOBAL, emptyList(), emptyList(), null))) }
        for (i in 20..22) service.handleRequest(camille, LoadFeedPayload(i, FeedKind.GLOBAL, null))
        assertEquals(6, backend.calls("page").size, "two slots freed, two calls more, never past 4 in flight")
        assertEquals(Outcome.RATE_LIMITED, outcome(camille, 22))
    }

    @Test
    fun `leaving forgets watching, and a late answer goes nowhere`() {
        backend.auto = { null }
        service.handleOpen(camille)
        service.handleRequest(camille, LikePayload(1, post, true))
        service.onLeave(camille)
        host.online -= camille
        assertFalse(service.isWatcher(camille))
        backend.calls("like").single().answer(BackendResult.Ok(LikeState(post, 1, true)))
        backend.calls("player").last().answer(BackendResult.Ok(ScriptedBackend.linked(camille, "Cam_Roux", Rights(true, true, true))))
        assertTrue(host.sentTo(camille).isEmpty())
        assertNull(service.rights.state(camille), "the state of a player who left isn't kept")
        assertTrue(camille !in host.commandRefreshes)
    }
}

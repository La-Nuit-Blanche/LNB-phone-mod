package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Outcome
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.PagePayload
import fr.bde_teknantes.lnb.phone.feed.server.FakeHost
import fr.bde_teknantes.lnb.phone.feed.server.FeedNotifier
import fr.bde_teknantes.lnb.phone.feed.server.FeedService
import fr.bde_teknantes.lnb.phone.feed.server.RecordingSink
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.warden.server.api.ApiHealth
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A site that sends the headers, then part of the body, then nothing: the request's timeout doesn't cover that, so
 * every call has a total deadline, and what its caller holds is freed when it passes.
 */
class StalledSiteTest {
    private val site = FakeSite()
    private val timeout = Duration.ofMillis(300)
    private val backend = SiteBackend(timeout) { site.url to "s3cret" }

    @AfterEach
    fun close() = site.close()

    /** Runs [host]'s tasks until [done], for a few seconds at most. */
    private fun until(host: FakeHost, done: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < end) {
            host.runTasks()
            if (done()) return
            Thread.sleep(10)
        }
        throw AssertionError("still waiting after 5 seconds")
    }

    @Test
    fun `a body that stalls after the headers ends the call as Unavailable, told to ApiHealth as a failure`() {
        site.answer = { FakeSite.Stall }
        val started = System.nanoTime()
        val result = backend.player(UUID(1, 1)).get(5, TimeUnit.SECONDS)
        val took = Duration.ofNanos(System.nanoTime() - started)
        assertInstanceOf(BackendResult.Unavailable::class.java, result)
        assertTrue(took < Duration.ofSeconds(2), "ended after $took")
        assertTrue(took >= timeout, "not before the timeout: $took")
        val error = ApiHealth.lastError!!
        assertTrue(error.message.startsWith("feed player"), error.message)
    }

    @Test
    fun `stalled calls free the player's slots`() {
        val host = FakeHost().apply { deferred = true }
        val camille = UUID(1, 1)
        host.online += camille
        site.answer = { request -> if (request.path.endsWith("/posts")) FakeSite.Stall else FakeSite.Reply(503, """{"data":{"problem":"disabled","flag":"feed"}}""") }
        val service = FeedService(backend, host, RecordingSink())
        for (i in 1..5) service.handleRequest(camille, LoadFeedPayload(i, FeedKind.GLOBAL, null))
        assertEquals(Outcome.RATE_LIMITED, host.received<PagePayload>(camille).single().outcome, "4 in flight, the 5th refused")

        until(host) { host.received<PagePayload>(camille).size == 5 }
        assertEquals(4, host.received<PagePayload>(camille).count { it.outcome == Outcome.UNAVAILABLE })

        site.answer = { FakeSite.Reply(200, """{"feed":"global","pinned":[],"posts":[],"next":null}""") }
        service.handleRequest(camille, LoadFeedPayload(6, FeedKind.GLOBAL, null))
        until(host) { host.received<PagePayload>(camille).any { it.requestId == 6 } }
        assertEquals(Outcome.OK, host.received<PagePayload>(camille).single { it.requestId == 6 }.outcome)
    }

    @Test
    fun `a drain stalled by the site ends, so the next drain asks again`() {
        val host = FakeHost().apply { deferred = true }
        site.answer = { FakeSite.Stall }
        val notifier = FeedNotifier(backend, host, RecordingSink())
        notifier.drain()
        assertEquals("/api/feed/server/notifications", site.nextRequest().path)
        // Past the deadline, the stalled drain ends on the server thread.
        Thread.sleep(timeout.toMillis() * 2)
        host.runTasks()

        site.answer = { FakeSite.Reply(200, """{"items":[],"more":false}""") }
        notifier.drain()
        assertEquals("/api/feed/server/notifications", site.nextRequest().path, "a stuck drain would only mark itself to run again")
    }
}

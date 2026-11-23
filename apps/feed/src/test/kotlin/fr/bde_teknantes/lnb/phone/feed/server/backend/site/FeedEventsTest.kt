package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import fr.bde_teknantes.lnb.phone.feed.model.CountUpdate
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.model.Pin
import fr.bde_teknantes.lnb.phone.feed.model.RemovedItem
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * The `feed` stream against a site in process (`docs/feed-mod.md` 8.1, `docs/feed-app.md` 3.2): connection, events,
 * `Last-Event-ID`, the backoff, the 60 seconds while disabled, the 75-second watchdog, and "disconnected" told once.
 *
 * Time is the test's: the waits between connections are handed to the test instead of slept, and the watchdog reads
 * the test's clock, so nothing here waits for real seconds.
 */
class FeedEventsTest {
    private val site = FakeSite()
    private val heard = LinkedBlockingQueue<String>()
    private val sleeps = LinkedBlockingQueue<Long>()
    private val wake = Semaphore(0)
    private val now = AtomicLong(1_000_000)

    @Volatile
    private var configured = true

    private val events = FeedEvents(
        connection = { if (configured) site.url to "s3cret" else null },
        clock = now::get,
        sleep = { delay ->
            sleeps.put(delay)
            wake.acquire()
        },
    )

    private val listener = object : FeedEventListener {
        override fun onConnected() = heard.put("connected")
        override fun onDisconnected(disabled: Boolean) = heard.put(if (disabled) "disconnected(disabled)" else "disconnected")
        override fun onPost(id: UUID, feed: FeedKind, threadId: UUID?, createdAt: Instant) = heard.put("post $id $feed $threadId $createdAt")
        override fun onDeleted(items: List<RemovedItem>) = heard.put("deleted ${items.map { it.id }}")
        override fun onCounts(items: List<CountUpdate>) = heard.put("counts ${items.map { it.likes }}")
        override fun onPins(pinned: List<Pin>) = heard.put("pins ${pinned.size}")
        override fun onRights(players: List<UUID>) = heard.put("rights $players")
        override fun onNotification() = heard.put("notification")
    }

    private val post = UUID.fromString("0d1c7e3a-7f2b-4a51-9a63-2f5d8c1b4e70")

    @AfterEach
    fun stop() {
        events.stop()
        wake.release(100)
        site.close()
    }

    private fun next(): String = heard.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("the listener heard nothing")

    private fun nextSleep(): Long = sleeps.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("the stream didn't wait")

    /** Nothing more is heard, waiting a little: for what must *not* happen. */
    private fun quiet() = assertNull(heard.poll(300, TimeUnit.MILLISECONDS))

    private fun refuse(vararg answers: FakeSite.Answer): MutableList<FakeSite.Answer> {
        val queue = answers.toMutableList()
        site.answer = { synchronized(queue) { if (queue.size > 1) queue.removeAt(0) else queue.first() } }
        return queue
    }

    private fun postEvent(id: String?) =
        (if (id != null) "id: $id\n" else "") + "event: post\ndata: {\"id\":\"$post\",\"feed\":\"global\",\"threadId\":null,\"createdAt\":\"2026-11-23T20:14:05.000Z\"}\n\n"

    @Test
    fun `it connects with the secret, and hands each event to the listener in order`() {
        val stream = FakeSite.Stream()
        site.answer = { stream }
        events.start(listener)
        assertEquals("connected", next())
        val request = site.nextRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/feed/server/events", request.path)
        assertEquals("Bearer s3cret", request.headers["authorization"])
        // ApiClient.request already sets Accept: application/json, and header() adds rather than replaces.
        assertTrue("text/event-stream" in request.headers["accept"].orEmpty().split(','), request.headers["accept"])
        assertNull(request.headers["last-event-id"], "the first connection starts from now")

        stream.send(postEvent("41"))
        stream.send(": a comment\n\nevent: ping\ndata: {}\n\n")
        stream.send("id: 42\nevent: economy\ndata: {\"x\":1}\n\n")
        // A data field split over two lines is one JSON text.
        stream.send("event: counts\ndata: {\"items\":[{\"id\":\"$post\",\n")
        stream.send("data: \"likes\":13,\"replies\":3}]}\n\n")
        stream.send("event:rights\ndata:{\"uuids\":[\"$post\"]}\n\n")
        stream.send("event: notification\ndata: {}\n\n")
        assertEquals("post $post GLOBAL null 2026-11-23T20:14:05Z", next())
        assertEquals("counts [13]", next())
        assertEquals("rights [$post]", next())
        assertEquals("notification", next())
        quiet()
    }

    @Test
    fun `a dropped stream is told once, and the next connection sends the last event ID`() {
        val first = FakeSite.Stream()
        val second = FakeSite.Stream()
        val streams = refuse(first, second)
        events.start(listener)
        assertEquals("connected", next())
        site.nextRequest()
        first.send(postEvent("7"))
        // An event without an ID keeps the last one.
        first.send("event: notification\ndata: {}\n\n")
        assertEquals("post $post GLOBAL null 2026-11-23T20:14:05Z", next())
        assertEquals("notification", next())
        first.end()

        assertEquals("disconnected", next())
        assertEquals(1_000, nextSleep())
        wake.release()
        assertEquals("connected", next())
        assertEquals("7", site.nextRequest().headers["last-event-id"])
        assertEquals(1, streams.size)
    }

    @Test
    fun `failed retries back off from 1 to 30 seconds, telling the listener once, until a lasting connection resets it`() {
        val stream = FakeSite.Stream()
        val answers = refuse(*Array(7) { FakeSite.Reply(502, "<html>") }, stream)
        events.start(listener)
        assertEquals("disconnected", next())
        val waits = (1..7).map {
            val wait = nextSleep()
            wake.release()
            wait
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), waits)
        assertEquals("connected", next(), "told once, not for each failed retry")
        assertEquals(1, answers.size)

        // A connection that lasted a minute resets the backoff: the next loss waits a second again.
        now.addAndGet(FeedEvents.HEALTHY_MS)
        stream.end()
        assertEquals("disconnected", next())
        assertEquals(1_000, nextSleep())
    }

    @Test
    fun `a stream accepted and dropped at once keeps backing off, so catch-ups don't come every second`() {
        val streams = List(6) { FakeSite.Stream() }
        refuse(*streams.toTypedArray())
        events.start(listener)
        val waits = streams.take(5).map { stream ->
            assertEquals("connected", next())
            stream.end()
            assertEquals("disconnected", next())
            val wait = nextSleep()
            wake.release()
            wait
        }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), waits)
    }

    @Test
    fun `a site that answers disabled is tried every 60 seconds, told once as disabled`() {
        val disabled = FakeSite.Reply(503, """{"statusCode":503,"data":{"problem":"disabled","flag":"feed"}}""")
        val stream = FakeSite.Stream()
        refuse(disabled, disabled, disabled, FakeSite.Reply(500), stream)
        events.start(listener)
        assertEquals("disconnected(disabled)", next())
        repeat(3) {
            assertEquals(60_000, nextSleep())
            wake.release()
        }
        // From "switched off" to "unreachable": told again, and the backoff starts from its first step.
        assertEquals("disconnected", next())
        assertEquals(1_000, nextSleep())
        wake.release()
        assertEquals("connected", next())
        quiet()
    }

    @Test
    fun `a connection silent for more than 75 seconds is dropped and made again`() {
        val first = FakeSite.Stream()
        val second = FakeSite.Stream()
        refuse(first, second)
        events.start(listener)
        assertEquals("connected", next())
        first.send(postEvent("9"))
        next()

        // 75 seconds exactly is not yet "more than 75".
        now.addAndGet(75_000)
        events.checkSilence()
        quiet()
        // A ping is data: it keeps the connection (the notification after it says it was read).
        first.send("event: ping\ndata: {}\n\nevent: notification\ndata: {}\n\n")
        assertEquals("notification", next())
        now.addAndGet(70_000)
        events.checkSilence()
        quiet()

        now.addAndGet(5_001)
        events.checkSilence()
        assertEquals("disconnected", next())
        assertEquals(1_000, nextSleep())
        wake.release()
        assertEquals("connected", next())
        val requests = generateSequence { site.requests.poll() }.toList()
        assertEquals("9", requests.last().headers["last-event-id"])
    }

    @Test
    fun `without api url and token it never calls the site, and says so once`() {
        configured = false
        events.start(listener)
        assertEquals("disconnected", next())
        repeat(3) {
            nextSleep()
            wake.release()
        }
        quiet()
        assertTrue(site.requests.isEmpty())
    }

    @Test
    fun `stopped, it neither reconnects nor tells the listener anything more`() {
        val stream = FakeSite.Stream()
        site.answer = { stream }
        events.start(listener)
        assertEquals("connected", next())
        events.stop()
        stream.end()
        quiet()
        assertNull(sleeps.poll(300, TimeUnit.MILLISECONDS))
    }
}

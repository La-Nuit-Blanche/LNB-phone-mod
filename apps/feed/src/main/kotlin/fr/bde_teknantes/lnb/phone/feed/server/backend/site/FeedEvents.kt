package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.google.gson.JsonObject
import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.server.backend.BackendResult
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedEventListener
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Keeps the site's `GET /api/feed/server/events` open on its own daemon thread (`docs/feed-mod.md` 8), written like
 * `QuestEvents` in lnb-quests: the last event ID kept in memory and sent on reconnection, a connection dropped after
 * [SILENCE_MS] silent (the site pings after 25), a backoff from 1 to 30 seconds (back to 1 only after a connection
 * that stayed up [HEALTHY_MS]), 60 seconds while the site answers 503 `disabled`.
 *
 * The listener hears [FeedEventListener.onConnected] after each successful connection, and
 * [FeedEventListener.onDisconnected] once when a connection is lost or refused, not again for each failed retry. It is
 * called on this thread and hops to the server thread itself. Unknown event types are ignored.
 *
 * @param clock wall-clock milliseconds, for the silence watchdog; tests pass their own.
 * @param sleep waits between connections; tests pass their own, so that retries take no real time.
 */
class FeedEvents(
    private val connection: () -> Pair<String, String>?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var watchdog: ScheduledExecutorService? = null

    @Volatile
    private var stream: InputStream? = null

    @Volatile
    private var lastEventId: String? = null

    @Volatile
    private var lastDataAt = 0L

    @Volatile
    private var connected = false

    // Both reset when a connection succeeds; used by the stream's own thread only.
    private var backoffMs = MIN_BACKOFF_MS
    private var connectedAt: Long? = null
    /** What the listener was last told since the stream was up: null for nothing, else whether it was "disabled". */
    private var reported: Boolean? = null

    fun start(listener: FeedEventListener) {
        if (running) return
        running = true
        thread = Thread({ run(listener) }, "LNB Feed events").apply {
            isDaemon = true
            start()
        }
        // The read below blocks: a connection the site stopped pinging is closed from here, so the thread reconnects.
        watchdog = Executors.newSingleThreadScheduledExecutor { Thread(it, "LNB Feed events watchdog").apply { isDaemon = true } }.apply {
            scheduleWithFixedDelay(::checkSilence, 5, 5, TimeUnit.SECONDS)
        }
    }

    /** Closes a connection silent for more than [SILENCE_MS], so the thread reconnects. Run every 5 seconds. */
    internal fun checkSilence() {
        if (connected && clock() - lastDataAt > SILENCE_MS) closeStream()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        watchdog?.shutdownNow()
        watchdog = null
        closeStream()
    }

    private fun run(listener: FeedEventListener) {
        var lastReason: String? = null
        while (running) {
            var disabled = false
            val reason = try {
                if (connection() == null) {
                    SiteBackend.NOT_CONFIGURED
                } else {
                    listen(listener)
                    "the stream ended"
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: DisabledException) {
                disabled = true
                "Feed is switched off on the site"
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            connected = false
            // Only a connection that stayed up a while starts the backoff over: a proxy that accepts the stream and
            // drops it at once would otherwise bring a reconnection, and its whole catch-up, every second.
            val since = connectedAt
            connectedAt = null
            if (since != null && clock() - since >= HEALTHY_MS) backoffMs = MIN_BACKOFF_MS
            if (!running) break
            // Once per loss, and again only if the reason turns from "unreachable" to "switched off" or back.
            if (reported != disabled) {
                reported = disabled
                listener.onDisconnected(disabled)
            }
            // Once per change, not again for each failed reconnection.
            if (reason != lastReason) LOGGER.warn("LNB's Feed event stream is disconnected ({}), reconnecting", reason)
            lastReason = reason
            try {
                sleep(if (disabled) DISABLED_RETRY_MS else backoffMs)
            } catch (e: InterruptedException) {
                break
            }
            if (!disabled) backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun listen(listener: FeedEventListener) {
        val (url, token) = connection() ?: return
        val request = ApiClient.request(url, SiteBackend.BASE + "/events", token)
            .header("Accept", "text/event-stream")
            // The site sends nothing, its headers included, before the first event or its 25-second ping: a quiet
            // stream connects after about 25 seconds. Waiting for the headers as long as for silence (75 seconds)
            // keeps a slow ping from turning every connection into a timeout.
            .timeout(Duration.ofMillis(SILENCE_MS))
            .apply { lastEventId?.let { header("Last-Event-ID", it) } }
            .GET()
            .build()
        val response = ApiClient.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            val body = response.body().use { it.readNBytes(4096).toString(Charsets.UTF_8) }
            val classified = SiteAnswers.classify(response.statusCode(), body, null) { }
            if (classified.result is BackendResult.Disabled) throw DisabledException()
            throw IOException("HTTP ${response.statusCode()}")
        }
        stream = response.body()
        lastDataAt = clock()
        connected = true
        connectedAt = clock()
        reported = null
        LOGGER.info("Connected to LNB's Feed event stream")
        listener.onConnected()

        response.body().bufferedReader().use { reader -> read(reader, listener) }
        stream = null
    }

    private fun read(reader: BufferedReader, listener: FeedEventListener) {
        var type = "message"
        var id: String? = null
        val data = StringBuilder()
        while (running) {
            val line = reader.readLine() ?: break
            lastDataAt = clock()
            when {
                line.isEmpty() -> {
                    if (id != null) lastEventId = id
                    if (data.isNotEmpty()) dispatch(listener, type, data.toString().trimEnd('\n'))
                    type = "message"
                    id = null
                    data.clear()
                }
                line.startsWith(":") -> Unit
                else -> {
                    val value = line.substringAfter(':', "").removePrefix(" ")
                    when (line.substringBefore(':')) {
                        "event" -> type = value
                        "data" -> data.append(value).append('\n')
                        "id" -> id = value
                    }
                }
            }
        }
    }

    private fun closeStream() {
        try {
            stream?.close()
        } catch (_: IOException) {
        }
    }

    private class DisabledException : Exception()

    companion object {
        private val LOGGER = LogUtils.getLogger()
        private const val MIN_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val DISABLED_RETRY_MS = 60_000L

        /** How long a connection must stay up before the backoff starts again from its first step. */
        const val HEALTHY_MS = 60_000L

        /** The site pings after 25 quiet seconds: past this, the connection is presumed dead. */
        private const val SILENCE_MS = 75_000L

        /**
         * Hands one event to [listener]. Pure apart from the listener, so it is tested on strings. A malformed event
         * is skipped with a warning naming its type only.
         */
        fun dispatch(listener: FeedEventListener, type: String, data: String) {
            if (type == "ping") return
            try {
                val json = SiteJson.parse(data)
                when (type) {
                    "post" -> {
                        val o = json as? JsonObject ?: throw SiteFormatException("post: not an object")
                        val id = UUID.fromString(o.get("id").asString)
                        val feed = FeedKind.fromWire(o.get("feed")?.asString) ?: throw SiteFormatException("post: feed")
                        val thread = o.get("threadId")?.takeIf { !it.isJsonNull }?.asString?.let(UUID::fromString)
                        listener.onPost(id, feed, thread, Instant.parse(o.get("createdAt").asString))
                    }
                    "deleted" -> listener.onDeleted(SiteJson.removedItems(json))
                    "counts" -> listener.onCounts(SiteJson.countUpdates(json))
                    "pins" -> listener.onPins(SiteJson.pins(json))
                    "rights" -> listener.onRights(SiteJson.uuids(json))
                    "notification" -> listener.onNotification()
                    else -> Unit
                }
            } catch (e: RuntimeException) {
                LOGGER.warn("Ignoring a malformed LNB Feed {} event", type)
            }
        }
    }
}

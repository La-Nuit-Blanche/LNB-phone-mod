package fr.bde_teknantes.lnb.phone.feed.server.backend.site

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The site, in process, on a free port of 127.0.0.1 (plain HTTP is what Warden's `ApiClient` allows on the local
 * machine): records every request and answers with what the test says, as `docs/feed-mod.md` describes.
 */
class FakeSite : AutoCloseable {
    /** A request as the site saw it. Header names are lowercased. */
    data class Request(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    sealed interface Answer

    /** A plain answer. A 204 is sent with no body at all. */
    data class Reply(val status: Int, val body: String = "", val headers: Map<String, String> = emptyMap()) : Answer

    /** No answer at all, until the site stops: a site that hangs. */
    data object Hang : Answer

    /** The status line and headers, then part of the body, then nothing more until the site stops. */
    data object Stall : Answer

    /**
     * A `text/event-stream` answer, kept open: what the test [send]s goes out as it comes, until [end] (or the client
     * goes away, or the site stops).
     */
    class Stream : Answer {
        private val out = LinkedBlockingQueue<String>()

        fun send(text: String) = out.put(text)

        fun end() = out.put(END)

        internal fun next(): String = out.take()

        companion object {
            internal const val END = "\u0000end"
        }
    }

    private val executor: ExecutorService = Executors.newCachedThreadPool { Thread(it, "Fake LNB site").apply { isDaemon = true } }
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val requests = LinkedBlockingQueue<Request>()

    /** What the site answers; by default an empty 200. */
    @Volatile
    var answer: (Request) -> Answer = { Reply(200, "{}") }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { exchange -> handle(exchange) }
        server.executor = executor
        server.start()
    }

    /** The next request, waiting for it at most a few seconds. */
    fun nextRequest(): Request = requests.poll(10, TimeUnit.SECONDS) ?: throw AssertionError("the site got no request")

    private fun handle(exchange: HttpExchange) {
        try {
            val headers = exchange.requestHeaders.entries.associate { (name, values) -> name.lowercase() to values.joinToString(",") }
            val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val request = Request(exchange.requestMethod, exchange.requestURI.rawPath, exchange.requestURI.rawQuery, headers, body)
            requests.put(request)
            when (val answer = answer(request)) {
                Hang -> Thread.sleep(Long.MAX_VALUE)
                Stall -> {
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, 100)
                    exchange.responseBody.write("{\"linked\":".toByteArray())
                    exchange.responseBody.flush()
                    Thread.sleep(Long.MAX_VALUE)
                }
                is Reply -> {
                    answer.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
                    if (answer.body.isNotEmpty()) exchange.responseHeaders.add("Content-Type", "application/json")
                    val bytes = answer.body.toByteArray()
                    exchange.sendResponseHeaders(answer.status, if (answer.status == 204 || bytes.isEmpty()) -1 else bytes.size.toLong())
                    if (bytes.isNotEmpty() && answer.status != 204) exchange.responseBody.write(bytes)
                }
                is Stream -> {
                    exchange.responseHeaders.add("Content-Type", "text/event-stream")
                    exchange.sendResponseHeaders(200, 0)
                    val out = exchange.responseBody
                    out.write(": connected\n\n".toByteArray())
                    out.flush()
                    while (true) {
                        val text = answer.next()
                        if (text == Stream.END) break
                        out.write(text.toByteArray())
                        out.flush()
                    }
                }
            }
        } catch (_: IOException) {
            // The client went away.
        } catch (_: InterruptedException) {
            // The site is stopping.
        } finally {
            exchange.close()
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}

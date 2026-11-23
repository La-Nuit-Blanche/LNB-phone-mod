package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.network.LoadFeedPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.lang.reflect.Proxy
import java.util.UUID

/** Warnings a client can cause are kept to one per kind a minute, saying how many were left out. */
class ThrottledLogTest {
    private val lines = ArrayList<Pair<String, List<Any?>>>()
    private val logger = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Logger::class.java)) { _, method, args ->
        if (method.name == "warn" || method.name == "error") {
            @Suppress("UNCHECKED_CAST")
            val rest = (args.getOrNull(1) as? Array<Any?>)?.toList() ?: args.drop(1)
            lines += args[0] as String to rest
        }
        if (method.returnType == Boolean::class.javaPrimitiveType) false else null
    } as Logger
    private var now = 0L
    private val log = ThrottledLog(logger, { now })

    @Test
    fun `one line per kind a minute, the next one counting what was left out`() {
        repeat(1000) { log.warn("malformed", "Feed refused a malformed {} request", "like") }
        log.warn("unexpected", "Unexpected answer {}", 400)
        assertEquals(2, lines.size)
        now += 60_000
        log.warn("malformed", "Feed refused a malformed {} request", "like")
        assertEquals(3, lines.size)
        assertEquals("Feed refused a malformed {} request ({} more like it left out)", lines.last().first)
        assertEquals(listOf("like", 999), lines.last().second)
    }

    @Test
    fun `a client flooding malformed requests writes one line a minute`() {
        val host = FakeHost()
        val camille = UUID(1, 1)
        host.online += camille
        val service = FeedService(ScriptedBackend(), host, RecordingSink())
        val field = FeedService::class.java.getDeclaredField("log").apply { isAccessible = true }
        field.set(service, ThrottledLog(logger, host::millis))
        repeat(500) {
            host.time += 2_000
            service.handleRequest(camille, LoadFeedPayload(it, FeedKind.GLOBAL, "c".repeat(129)))
        }
        // 500 requests over 1,000 seconds: one line every 60 seconds at most.
        assertEquals(1_000 / 60 + 1, lines.size)
    }
}

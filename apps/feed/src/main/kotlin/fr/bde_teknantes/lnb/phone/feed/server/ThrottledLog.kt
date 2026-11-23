package fr.bde_teknantes.lnb.phone.feed.server

import org.slf4j.Logger

/**
 * Warnings that a client can cause (a malformed request, an answer of the site to what it sent), at most one per kind
 * every [everyMs]: a modified client can't flood the log. The next one says how many were left out, as the phone's
 * flood warning does. Server thread only.
 */
class ThrottledLog(private val logger: Logger, private val millis: () -> Long, private val everyMs: Long = 60_000L) {
    private val last = HashMap<String, Long>()
    private val skipped = HashMap<String, Int>()

    fun warn(kind: String, message: String, vararg args: Any?) = log(kind, message, args) { text, all -> logger.warn(text, *all) }

    fun error(kind: String, message: String, vararg args: Any?) = log(kind, message, args) { text, all -> logger.error(text, *all) }

    /** Whether a message of [kind] may be written now; if not, it is counted for the next one. */
    fun allow(kind: String): Boolean {
        val now = millis()
        val at = last[kind]
        if (at != null && now - at < everyMs) {
            skipped.merge(kind, 1, Int::plus)
            return false
        }
        last[kind] = now
        return true
    }

    private fun log(kind: String, message: String, args: Array<out Any?>, write: (String, Array<Any?>) -> Unit) {
        if (!allow(kind)) return
        val left = skipped.remove(kind) ?: 0
        if (left == 0) write(message, arrayOf(*args)) else write("$message ({} more like it left out)", arrayOf(*args, left))
    }
}

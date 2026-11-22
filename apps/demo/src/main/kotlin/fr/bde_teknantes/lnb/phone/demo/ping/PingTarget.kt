package fr.bde_teknantes.lnb.phone.demo.ping

import java.util.UUID

/**
 * Where a ping's notification leads: the screen to answer its sender, written `from:<uuid>/<name>`. The server writes
 * it, the client reads it back when the player opens the notification, maybe days later and with a newer Ping, so
 * [parse] answers null for anything it doesn't recognise rather than throwing.
 *
 * The name travels with the id because the client can only look names up for players who are online.
 */
data class PingTarget(val player: UUID, val name: String) {
    override fun toString() = "$PREFIX$player/$name"

    companion object {
        private const val PREFIX = "from:"

        fun parse(target: String): PingTarget? {
            if (!target.startsWith(PREFIX)) return null
            val id = target.substring(PREFIX.length).substringBefore('/')
            val name = target.substringAfter('/', missingDelimiterValue = "")
            if (name.isEmpty()) return null
            val player = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return PingTarget(player, name)
        }
    }
}

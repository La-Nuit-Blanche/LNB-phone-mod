package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.PlayerDirectory
import net.minecraft.core.UUIDUtil
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** The players of the contract's examples (`feed-mod.md` 4), by their offline UUIDs. */
object Players {
    val CAMILLE: UUID = UUIDUtil.createOfflinePlayerUUID("Cam_Roux")
    val MIKA: UUID = UUIDUtil.createOfflinePlayerUUID("MikaPvP")
    val LUNE: UUID = UUIDUtil.createOfflinePlayerUUID("Admin_Lune")
    val KAEL: UUID = UUIDUtil.createOfflinePlayerUUID("Kael_Builder")
    val VISITOR: UUID = UUIDUtil.createOfflinePlayerUUID("Visitor42")

    /** Everyone above, Lune an operator (a moderator in memory mode). Visitor42 is a guest only if listed as one. */
    fun directory() = FakeDirectory(
        mapOf(CAMILLE to "Cam_Roux", MIKA to "MikaPvP", LUNE to "Admin_Lune", KAEL to "Kael_Builder", VISITOR to "Visitor42"),
        operators = setOf(LUNE),
    )
}

class FakeDirectory(private val names: Map<UUID, String>, private val operators: Set<UUID> = emptySet()) : PlayerDirectory {
    override fun username(player: UUID) = names[player]
    override fun isOperator(player: UUID) = player in operators
}

class MutableClock(var now: Instant = Instant.parse("2026-11-23T20:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now

    fun advanceSeconds(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

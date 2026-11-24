package fr.bde_teknantes.lnb.phone.feed.server.backend.memory

import com.mojang.authlib.GameProfile
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * The running server's players, for the memory backend: names from the connected players and the server's profile
 * cache (players who have joined before), operators from the server's permission levels (the op list, or the owner of
 * a single-player game with cheats).
 */
class MemoryPlayers(private val server: MinecraftServer) : PlayerDirectory {
    override fun username(player: UUID): String? =
        server.playerList.getPlayer(player)?.gameProfile?.name ?: server.profileCache?.get(player)?.orElse(null)?.name

    override fun isOperator(player: UUID): Boolean {
        val name = username(player) ?: return false
        return server.getProfilePermissions(GameProfile(player, name)) >= 2
    }
}

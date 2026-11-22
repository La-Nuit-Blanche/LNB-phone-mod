package fr.bde_teknantes.lnb.phone.api.client.ui

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.SkullBlockEntity
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Finds a player's skin from their UUID and name alone, as posts and messages carry them, for players who may be
 * offline or have never been seen by this client. Ask again every frame ([Avatar] does): the answer improves as what is
 * known arrives, and the face changes by itself.
 *
 * 1. A player on the server right now: the skin the tab list already has.
 * 2. Otherwise the profile is fetched once in the background, from Mojang's session service by UUID, then by name when
 *    the UUID isn't known there (an offline-mode server's UUIDs), and kept for the session. Its skin then downloads
 *    as usual.
 * 3. Until then, and for players the session service doesn't know at all (offline-mode accounts, made-up names), the
 *    default skin Minecraft gives that UUID (one of the nine default characters). A failed lookup is tried again after
 *    five minutes.
 *
 * Client thread.
 */
object PlayerSkins {
    private const val RETRY_NANOS = 5L * 60 * 1_000_000_000

    private sealed interface Entry
    private object Loading : Entry
    private class Found(val profile: GameProfile) : Entry
    private class Missing(val retryAt: Long) : Entry

    private val entries = HashMap<UUID, Entry>()

    /** The best skin known for the player [id] named [name] right now. */
    fun skin(id: UUID, name: String): ResourceLocation {
        val mc = Minecraft.getInstance()
        mc.connection?.getPlayerInfo(id)?.let { return it.skin.texture() }
        when (val entry = entries[id]) {
            is Found -> return mc.skinManager.getInsecureSkin(entry.profile).texture()
            is Missing -> if (System.nanoTime() >= entry.retryAt) fetch(id, name)
            Loading -> {}
            null -> fetch(id, name)
        }
        return DefaultPlayerSkin.get(id).texture()
    }

    private fun fetch(id: UUID, name: String) {
        entries[id] = Loading
        val mc = Minecraft.getInstance()
        SkullBlockEntity.fetchGameProfile(id)
            .thenCompose { byId -> if (byId.isPresent || name.isBlank()) CompletableFuture.completedFuture(byId) else SkullBlockEntity.fetchGameProfile(name) }
            .exceptionally { Optional.empty() }
            .thenAcceptAsync({ found -> entries[id] = found.map<Entry> { Found(it) }.orElse(Missing(System.nanoTime() + RETRY_NANOS)) }, mc)
    }

    /** Forgets what was found, when the player disconnects. */
    internal fun clear() = entries.clear()
}

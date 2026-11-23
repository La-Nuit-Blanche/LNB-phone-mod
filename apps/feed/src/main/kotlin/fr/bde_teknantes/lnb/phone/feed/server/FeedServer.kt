package fr.bde_teknantes.lnb.phone.feed.server

import fr.bde_teknantes.lnb.phone.feed.FeedMod
import fr.bde_teknantes.lnb.phone.feed.network.FeedRequest
import fr.bde_teknantes.lnb.phone.feed.network.OpenPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenFeedPayload
import fr.bde_teknantes.lnb.phone.feed.network.SeenThreadPayload
import fr.bde_teknantes.lnb.phone.feed.server.backend.FeedBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryBackend
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.MemoryPlayers
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.SampleContent
import fr.bde_teknantes.lnb.phone.feed.server.backend.site.SiteBackend
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Feed on the logical server (a dedicated server, or a single-player game's integrated one): wires the game's events
 * and the payloads to the [FeedService] of the running server. Common code that only the logical server runs; it
 * names no client class.
 */
object FeedServer {
    /** The running server's Feed, from its start to its stop; null otherwise. Server thread. */
    @Volatile
    var current: FeedService? = null
        private set

    /** Set by the screenshot harness: a memory backend's clock, `sample` or an ISO instant (`docs/feed-app.md` 12.1). */
    const val FIXED_CLOCK_PROPERTY = "lnbphone_feed.memory.clock"

    fun init() {
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
        NeoForge.EVENT_BUS.addListener(::onServerStopping)
        NeoForge.EVENT_BUS.addListener(::onServerTick)
        NeoForge.EVENT_BUS.addListener(::onLoggedIn)
        NeoForge.EVENT_BUS.addListener(::onLoggedOut)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        val service = FeedService(createBackend(server), MinecraftFeedHost(server), PhoneFeedNotifications)
        current = service
        service.start()
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        current?.stop()
        current = null
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        current?.tick()
    }

    private fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        current?.onJoin(event.entity.uuid)
    }

    private fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        current?.onLeave(event.entity.uuid)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        FeedCommand.register(event.dispatcher, devCommands = FeedConfig.kind == FeedConfig.Kind.MEMORY)
    }

    // ---- Payloads (FeedNetwork), on the server thread ----

    fun onOpen(player: ServerPlayer, payload: OpenPayload) {
        current?.handleOpen(player.uuid)
    }

    fun onRequest(player: ServerPlayer, request: FeedRequest) {
        current?.handleRequest(player.uuid, request)
    }

    fun onSeenFeed(player: ServerPlayer, payload: SeenFeedPayload) {
        current?.handleSeenFeed(player.uuid, payload.feed, payload.upTo)
    }

    fun onSeenThread(player: ServerPlayer, payload: SeenThreadPayload) {
        current?.handleSeenThread(player.uuid, payload.threadId)
    }

    private fun createBackend(server: MinecraftServer): FeedBackend {
        if (FeedConfig.kind == FeedConfig.Kind.SITE) {
            FeedMod.LOGGER.info("Feed uses LNB's website (api.url in lnbwarden-server.toml)")
            return SiteBackend()
        }
        val clock = fixedClock() ?: Clock.systemUTC()
        val backend = MemoryBackend(clock, MemoryPlayers(server), FeedConfig.guests, FeedConfig.rateLimits)
        if (FeedConfig.seed == FeedConfig.Seed.SAMPLE) SampleContent.seed(backend, clock.instant())
        FeedMod.LOGGER.warn("Feed uses its memory backend ({} seed): nothing is kept, for development only", FeedConfig.seed.name.lowercase())
        return backend
    }

    private fun fixedClock(): Clock? {
        val value = System.getProperty(FIXED_CLOCK_PROPERTY)?.takeIf { it.isNotBlank() } ?: return null
        val instant = if (value == "sample") SampleContent.FIXED_NOW else runCatching { Instant.parse(value) }.getOrNull()
        if (instant == null) {
            FeedMod.LOGGER.warn("Ignoring {}={}: not 'sample' or an ISO instant", FIXED_CLOCK_PROPERTY, value)
            return null
        }
        return Clock.fixed(instant, ZoneOffset.UTC)
    }
}

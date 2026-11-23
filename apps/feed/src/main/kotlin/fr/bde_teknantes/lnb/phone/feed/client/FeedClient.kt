package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.feed.Feed
import fr.bde_teknantes.lnb.phone.feed.FeedMod
import fr.bde_teknantes.lnb.phone.feed.FeedTarget
import fr.bde_teknantes.lnb.phone.feed.client.screens.FeedScreen
import fr.bde_teknantes.lnb.phone.feed.model.FeedKind
import fr.bde_teknantes.lnb.phone.feed.server.FeedServer
import fr.bde_teknantes.lnb.phone.feed.server.backend.memory.SampleContent
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor
import java.time.Instant

/**
 * Feed's client side: puts the app on the phone and holds the client's state, [session], which the payload handlers
 * ([FeedClientPayloads]) feed and the screens read. Loaded only on the physical client.
 */
object FeedClient {
    /** The home icon: the phone's Feed glyph (art direction 7.4) on an azure tile. */
    private val ICON = ResourceLocation.fromNamespaceAndPath("lnbphone", "textures/gui/sprites/icon/feed.png")

    /** Feed's state on this client, for the server joined; cleared when the player leaves it. */
    val session: FeedSession by lazy { FeedSession(send = { PacketDistributor.sendToServer(it) }, clock = clock()) }

    /** Feed's strings, in the game's language: `lnbphone_feed.` and the art direction's ID. */
    val strings = FeedStrings { id, args -> I18n.get("${Feed.MOD_ID}.$id", *args) }

    fun init(modBus: IEventBus) {
        modBus.addListener(::registerApps)
        NeoForge.EVENT_BUS.addListener(::onLoggingOut)
    }

    private fun registerApps(event: RegisterPhoneAppsEvent) {
        event.register(
            PhoneApp(
                id = Feed.APP_ID,
                name = Component.translatable("app.${Feed.MOD_ID}.feed"),
                icon = AppIcon.Glyph(TileColor.AZURE, ICON),
                targetScreens = ::targetScreens,
            ) { FeedScreen(session, strings, FeedKind.ANNOUNCEMENTS, fromHome = true) },
        )
    }

    /**
     * The screens a notification opens (`docs/feed-app.md` 6.2): an announcement lands on Announcements, scrolled to it
     * and highlighted. A reply's target opens the tab of its feed for now; the thread screen, built next, goes on top of
     * it. Anything else opens Feed on Announcements.
     */
    fun targetScreens(target: String): List<AppScreen> = when (val parsed = FeedTarget.parse(target)) {
        is FeedTarget.Post -> {
            session.land(parsed.postId)
            listOf(FeedScreen(session, strings, FeedKind.ANNOUNCEMENTS, fromHome = true))
        }
        // The thread screen (task M3) goes on top of this one, at the reply.
        is FeedTarget.Reply -> listOf(FeedScreen(session, strings, parsed.feed, fromHome = false))
        null -> emptyList()
    }

    private fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        // What the client holds belongs to the server just left. Hop to the client thread, where it is used.
        Minecraft.getInstance().execute { FeedClientPayloads.clear() }
    }

    /**
     * Real time, or the screenshot harness's fixed clock (the property the memory backend reads, outside a production
     * game only), so that the relative times drawn match the art direction's.
     */
    private fun clock(): () -> Instant {
        val value = System.getProperty(FeedServer.FIXED_CLOCK_PROPERTY)?.takeIf { it.isNotBlank() && !FMLEnvironment.production }
            ?: return Instant::now
        val fixed = if (value == "sample") SampleContent.FIXED_NOW else runCatching { Instant.parse(value) }.getOrNull()
        if (fixed == null) {
            FeedMod.LOGGER.warn("Ignoring {}={} on the client: not 'sample' or an ISO instant", FeedServer.FIXED_CLOCK_PROPERTY, value)
            return Instant::now
        }
        return { fixed }
    }
}

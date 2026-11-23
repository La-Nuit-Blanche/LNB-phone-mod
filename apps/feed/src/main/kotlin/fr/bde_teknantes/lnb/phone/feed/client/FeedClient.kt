package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.feed.Feed
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge

/**
 * Feed's client side: puts the app on the phone. Loaded only on the physical client.
 *
 * For now the app opens on [BuildingScreen]. The screens (`FeedScreen`, `ThreadScreen`, `ComposeScreen`) replace it,
 * reading everything from a `FeedSession` (`docs/feed-app.md` 8) that [FeedClientPayloads] feeds; `targetScreens` will
 * turn a notification's [fr.bde_teknantes.lnb.phone.feed.FeedTarget] into the stack of 6.2.
 */
object FeedClient {
    /** The home icon: the phone's Feed glyph (art direction 7.4) on an azure tile. */
    private val ICON = ResourceLocation.fromNamespaceAndPath("lnbphone", "textures/gui/sprites/icon/feed.png")

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
            ) { BuildingScreen() },
        )
    }

    private fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        // What the client holds belongs to the server just left. Hop to the client thread, where it is used.
        Minecraft.getInstance().execute { FeedClientPayloads.clear() }
    }
}

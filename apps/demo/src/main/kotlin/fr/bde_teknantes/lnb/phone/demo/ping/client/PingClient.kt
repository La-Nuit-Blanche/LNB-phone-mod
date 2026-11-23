package fr.bde_teknantes.lnb.phone.demo.ping.client

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import fr.bde_teknantes.lnb.phone.demo.ping.PingTarget
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge

/** Ping's client side: puts the app on the phone. Loaded only on the physical client. */
object PingClient {
    fun init(modBus: IEventBus) {
        modBus.addListener(::registerApps)
        NeoForge.EVENT_BUS.addListener(::onLoggingOut)
    }

    private fun registerApps(event: RegisterPhoneAppsEvent) {
        event.register(
            PhoneApp(
                id = Ping.APP_ID,
                name = Component.translatable("app.lnbphone_demo.ping"),
                icon = AppIcon.Glyph(TileColor.PINK, ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "textures/gui/ping.png")),
                // A ping's notification opens the screen to answer its sender, over the list as if picked from it.
                targetScreens = { target ->
                    val from = PingTarget.parse(target)
                    if (from == null) emptyList() else listOf(PlayersScreen(), PingScreen(from.player, from.name))
                },
            ) { PlayersScreen() },
        )
    }

    private fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        // Drafts belong to the server just left. Hop to the client thread, where the session is used.
        Minecraft.getInstance().execute { PingSession.clear() }
    }
}

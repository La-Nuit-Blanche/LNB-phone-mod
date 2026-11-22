package fr.bde_teknantes.lnb.phone.demo.ping

import fr.bde_teknantes.lnb.phone.demo.DemoApp
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment

/**
 * Ping, the example app that `docs/writing-an-app.md` walks through: pick a player, write a short message, and it
 * reaches their phone as a notification that opens Ping on the screen to answer them.
 *
 * This object is common code: it runs on the client and on a dedicated server, so it names no client class.
 */
object Ping {
    /** The app's id: registered on the client, named by the server when it sends a notification. */
    val APP_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "ping")

    /** The longest ping, in characters as [fr.bde_teknantes.lnb.phone.api.TextLimits] counts them. */
    const val MAX_LENGTH = 100

    fun init(modBus: IEventBus) {
        // Both sides: the client sends pings, the server receives them.
        modBus.addListener(PingNetwork::register)
        // Behind the check, by its full name, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) fr.bde_teknantes.lnb.phone.demo.ping.client.PingClient.init(modBus)
    }
}

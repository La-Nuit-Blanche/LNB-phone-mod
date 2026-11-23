package fr.bde_teknantes.lnb.phone.demo

import fr.bde_teknantes.lnb.phone.demo.ping.Ping
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge

/**
 * The demo mod, for development only (never shipped). It holds two things, kept apart:
 *
 * - Ping (package `ping`), the example app that `docs/writing-an-app.md` walks through;
 * - the development tools: the Hello app, the UI kit's gallery, the sample Feed, the test sheets, `/phonedemo` and
 *   the screenshot harness (package `client` and [DemoCommands]).
 */
@Mod(DemoApp.ID)
class DemoApp(modBus: IEventBus) {
    init {
        // Development tools. Common: the server side sends /phonedemo's notifications.
        NeoForge.EVENT_BUS.addListener(DemoCommands::register)
        // Referenced by their full names, behind the check, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) {
            fr.bde_teknantes.lnb.phone.demo.client.DemoAppClient.init(modBus)
            fr.bde_teknantes.lnb.phone.demo.client.DemoClient.init()
        }
        // The example app. Started after the tools so that it comes after their apps on the home screen: capture
        // scenarios click those by their place.
        Ping.init(modBus)
    }

    companion object {
        const val ID = "lnbphone_demo"

        /** The development Hello app, on both sides: registered on the client, named by `/phonedemo` in notifications. */
        val HELLO_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, "hello")
    }
}

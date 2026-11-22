package fr.bde_teknantes.lnb.phone.demo

import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge

/** Development app: proves an app in its own mod reaches the phone through the public API. Never shipped. */
@Mod(DemoApp.ID)
class DemoApp(modBus: IEventBus) {
    init {
        // Common: the server side sends the demo's notifications.
        NeoForge.EVENT_BUS.addListener(DemoCommands::register)
        // Referenced by their full names, behind the check, so a dedicated server never loads a client class.
        if (FMLEnvironment.dist.isClient) {
            fr.bde_teknantes.lnb.phone.demo.client.DemoAppClient.init(modBus)
            fr.bde_teknantes.lnb.phone.demo.client.DemoClient.init()
        }
    }

    companion object {
        const val ID = "lnbphone_demo"

        /** The demo's app, on both sides: registered on the client, named by the server in notifications. */
        val HELLO_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, "hello")
    }
}

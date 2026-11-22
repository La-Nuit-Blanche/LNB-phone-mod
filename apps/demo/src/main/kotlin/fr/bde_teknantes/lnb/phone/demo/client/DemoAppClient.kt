package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus

/** The demo's client side: registers its app with the phone. */
object DemoAppClient {
    fun init(modBus: IEventBus) {
        modBus.addListener(::registerApps)
    }

    private fun registerApps(event: RegisterPhoneAppsEvent) {
        event.register(
            PhoneApp(
                id = ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "hello"),
                name = Component.translatable("app.lnbphone_demo.hello"),
                icon = AppIcon.Glyph(TileColor.TEAL, ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "textures/gui/hello.png")),
            ) { HelloScreen(depth = 1) },
        )
    }
}

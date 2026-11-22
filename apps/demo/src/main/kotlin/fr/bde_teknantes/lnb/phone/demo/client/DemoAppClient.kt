package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import fr.bde_teknantes.lnb.phone.demo.DemoCommands
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
                id = DemoApp.HELLO_ID,
                name = Component.translatable("app.lnbphone_demo.hello"),
                icon = AppIcon.Glyph(TileColor.TEAL, ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "textures/gui/hello.png")),
                // `/phonedemo notify <player> <n>` leads to the screen at depth n, with the screens above it to go back.
                targetScreens = { target ->
                    val depth = target.removePrefix("depth:").toIntOrNull()?.takeIf { it in 1..DemoCommands.MAX_DEPTH }
                    if (depth == null) emptyList() else (1..depth).map(::HelloScreen)
                },
            ) { HelloScreen(depth = 1) },
        )
    }
}

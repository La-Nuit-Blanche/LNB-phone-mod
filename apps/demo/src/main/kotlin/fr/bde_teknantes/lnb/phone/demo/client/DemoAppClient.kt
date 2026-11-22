package fr.bde_teknantes.lnb.phone.demo.client

import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import fr.bde_teknantes.lnb.phone.api.client.TileColor
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import fr.bde_teknantes.lnb.phone.demo.DemoCommands
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.bus.api.IEventBus

/**
 * The demo's client side: registers its app with the phone. With `-Dlnbphone.demo.apps=<n>` (the capture run's
 * `-PdemoApps`), it also fills the home screen with n sample apps: the art direction's example icons, an item icon,
 * then numbered fillers, enough to need a second page from 30 apps.
 */
object DemoAppClient {
    fun init(modBus: IEventBus) {
        modBus.addListener(::registerApps)
    }

    private fun registerApps(event: RegisterPhoneAppsEvent) {
        val samples = System.getProperty("lnbphone.demo.apps")?.toIntOrNull() ?: 0
        // The example apps of art direction 7.4 come first, as on the home reference sheet (18.2).
        EXAMPLES.take(samples).forEach { (name, tile) ->
            event.register(app("sample_$name", Component.translatable("app.lnbphone_demo.sample.$name"), glyphIcon(tile, name)))
        }
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
        if (samples > EXAMPLES.size) {
            event.register(app("sample_map", Component.translatable("app.lnbphone_demo.sample.map"), AppIcon.Item(TileColor.PINK, ItemStack(Items.COMPASS))))
        }
        for (n in EXAMPLES.size + 2..samples) {
            val (glyph, _) = EXAMPLES[n % EXAMPLES.size]
            val tile = TileColor.entries[n % TileColor.entries.size]
            event.register(app("sample_$n", Component.translatable("app.lnbphone_demo.sample.filler", n), glyphIcon(tile, glyph)))
        }
    }

    private fun app(path: String, name: Component, icon: AppIcon) =
        PhoneApp(ResourceLocation.fromNamespaceAndPath(DemoApp.ID, path), name, icon) { HelloScreen(depth = 1) }

    // The phone's own example glyphs, read as plain textures.
    private fun glyphIcon(tile: TileColor, glyph: String) =
        AppIcon.Glyph(tile, ResourceLocation.fromNamespaceAndPath("lnbphone", "textures/gui/sprites/icon/$glyph.png"))

    private val EXAMPLES = listOf(
        "feed" to TileColor.AZURE,
        "messages" to TileColor.GREEN,
        "wallet" to TileColor.VIOLET,
        "quests" to TileColor.ORANGE,
        "settings" to TileColor.GRAPHITE,
    )
}

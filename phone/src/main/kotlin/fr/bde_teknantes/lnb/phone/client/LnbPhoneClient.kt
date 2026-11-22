package fr.bde_teknantes.lnb.phone.client

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistration
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.icons.AppIconTextures
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationHandler
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotifications
import fr.bde_teknantes.lnb.phone.client.settings.PhoneSettingsStore
import fr.bde_teknantes.lnb.phone.client.shell.PhoneKeys
import fr.bde_teknantes.lnb.phone.client.shell.PhoneScreen
import fr.bde_teknantes.lnb.phone.client.shell.PhoneShell
import fr.bde_teknantes.lnb.phone.client.shell.PhoneSounds
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothShader
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterShadersEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge

/** Client-only listeners and state. Loaded only on the physical client. */
object LnbPhoneClient {
    /** The apps on the phone, complete once client setup is over. */
    internal val apps = AppRegistry()

    /** What the phone shows. Lives as long as the game: the phone reopens where it was left, until disconnection. */
    internal val navigator = PhoneNavigator(apps) { Minecraft.getInstance().isSameThread }

    /** The player's notifications, mirrored from the server: badges, LED, inventory button. */
    internal val notifications = ClientNotifications { apps[it] != null }

    /** The phone settings of the player on this computer: placement, sounds, notification preview. */
    internal val settings = PhoneSettingsStore(FMLPaths.CONFIGDIR.get().resolve("lnbphone-client.json")) {
        Minecraft.getInstance().user.profileId.toString()
    }

    // Takes the mod bus because client setup (key mappings, screens) is registered there, not on NeoForge's bus.
    fun init(modBus: IEventBus) {
        modBus.addListener(::onClientSetup)
        modBus.addListener(RegisterKeyMappingsEvent::class.java, PhoneKeys::register)
        modBus.addListener(RegisterClientReloadListenersEvent::class.java) {
            // The smooth layer's fonts and atlas, and the composed app icons, come from resources: drop them when
            // resource packs change.
            it.registerReloadListener(SmoothResources)
            it.registerReloadListener(AppIconTextures)
        }
        modBus.addListener(RegisterShadersEvent::class.java, SmoothShader::register)
        modBus.addListener(RegisterGuiLayersEvent::class.java) { event ->
            // The phone while it slides, over the whole HUD; once it stands open, its screen draws it.
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "phone")) { graphics, _ ->
                if (Minecraft.getInstance().screen !is PhoneScreen) PhoneShell.render(graphics, false, 0.0, 0.0)
            }
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post::class.java) {
            PhoneSounds.tick()
            PhoneKeys.discardClicks()
            PhoneShell.tick()
        }
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre::class.java) { PhoneShell.frame() }
        // A canvas whose end() an exception skipped must not clip the next frame to the phone's viewport.
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post::class.java) { SmoothCanvas.endAbandoned() }
        NeoForge.EVENT_BUS.addListener(PhoneKeys::onKey)
        NeoForge.EVENT_BUS.addListener(PhoneKeys::onMouseButton)
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre::class.java) { event ->
            // Other screens hide the crosshair under their dark background; the phone keeps the world clear, so it
            // hides it itself while the cursor is free.
            if (event.name == VanillaGuiLayers.CROSSHAIR && Minecraft.getInstance().screen is PhoneScreen) event.isCanceled = true
        }
        NeoForge.EVENT_BUS.addListener(::onLoggingOut)
        navigator.addListener(ClientNotificationHandler)
        PhoneShell.init()
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        // Queued to the main thread after the parallel setup: apps register one mod after the other, in a fixed order.
        event.enqueueWork { AppRegistration.fire(apps) }
    }

    private fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        // The phone keeps its screens until the player disconnects (art direction 2.3); the notifications belong to
        // the server just left. execute runs at once on the client thread, and queues there from any other.
        Minecraft.getInstance().execute {
            navigator.reset()
            notifications.reset()
            PhoneShell.reset()
        }
    }
}

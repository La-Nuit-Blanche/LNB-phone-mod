package fr.bde_teknantes.lnb.phone.client

import fr.bde_teknantes.lnb.phone.client.apps.AppRegistration
import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.client.icons.AppIconTextures
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotificationHandler
import fr.bde_teknantes.lnb.phone.client.notifications.ClientNotifications
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.common.NeoForge

/** Client-only listeners and state. Loaded only on the physical client. */
object LnbPhoneClient {
    /** The apps on the phone, complete once client setup is over. */
    internal val apps = AppRegistry()

    /** What the phone shows. Lives as long as the game: the phone reopens where it was left, until disconnection. */
    internal val navigator = PhoneNavigator(apps) { Minecraft.getInstance().isSameThread }

    /** The player's notifications, mirrored from the server: badges, LED, inventory button. */
    internal val notifications = ClientNotifications { apps[it] != null }

    // Takes the mod bus because client setup (key mappings, screens) is registered there, not on NeoForge's bus.
    fun init(modBus: IEventBus) {
        modBus.addListener(::onClientSetup)
        modBus.addListener(RegisterClientReloadListenersEvent::class.java) {
            // The smooth layer's fonts and atlas, and the composed app icons, come from resources: drop them when
            // resource packs change.
            it.registerReloadListener(SmoothResources)
            it.registerReloadListener(AppIconTextures)
        }
        NeoForge.EVENT_BUS.addListener(::onLoggingOut)
        navigator.addListener(ClientNotificationHandler)
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
        }
    }
}

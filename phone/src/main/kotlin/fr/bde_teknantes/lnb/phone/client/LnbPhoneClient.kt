package fr.bde_teknantes.lnb.phone.client

import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent

/** Client-only listeners. Loaded only on the physical client. */
object LnbPhoneClient {
    // Takes the mod bus because client setup (key mappings, screens) is registered there, not on NeoForge's bus.
    fun init(modBus: IEventBus) {
        // The smooth layer's fonts and atlas come from resources: drop them when resource packs change.
        modBus.addListener(RegisterClientReloadListenersEvent::class.java) { it.registerReloadListener(SmoothResources) }
    }
}

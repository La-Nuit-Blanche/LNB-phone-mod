package fr.bde_teknantes.lnb.phone.client

import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothShader
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RegisterShadersEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import net.neoforged.neoforge.common.NeoForge

/** Client-only listeners. Loaded only on the physical client. */
object LnbPhoneClient {
    // Takes the mod bus because client setup (key mappings, screens) is registered there, not on NeoForge's bus.
    fun init(modBus: IEventBus) {
        // The smooth layer's fonts and atlas come from resources: drop them when resource packs change.
        modBus.addListener(RegisterClientReloadListenersEvent::class.java) { it.registerReloadListener(SmoothResources) }
        modBus.addListener(RegisterShadersEvent::class.java, SmoothShader::register)
        // A canvas whose end() an exception skipped must not clip the next frame to the phone's viewport.
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post::class.java) { SmoothCanvas.endAbandoned() }
    }
}

package fr.bde_teknantes.lnb.phone.client

import net.neoforged.bus.api.IEventBus

/** Client-only listeners. Loaded only on the physical client. */
object LnbPhoneClient {
    // Takes the mod bus because client setup (key mappings, screens) is registered there, not on NeoForge's bus.
    fun init(modBus: IEventBus) {
    }
}

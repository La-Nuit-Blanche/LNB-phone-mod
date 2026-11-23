package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent
import net.neoforged.fml.ModList

/** Asks every mod for its apps, once, at client startup. */
internal object AppRegistration {
    /**
     * Posts [RegisterPhoneAppsEvent] to each mod's bus in loading order, then freezes [registry]. Run on the main
     * thread, after the parallel client setup, so that listeners run one after the other in a fixed order.
     */
    fun fire(registry: AppRegistry) {
        val event = RegisterPhoneAppsEvent(registry)
        // Mod by mod rather than through ModLoader.postEvent: there, a listener that throws stops the game with a
        // loading error. Here it costs that mod's remaining apps only, Errors included: an app built against an older
        // phone throws NoSuchMethodError, and that must not stop the game in the phone's name.
        ModList.get().forEachModInOrder { mod ->
            val bus = mod.eventBus ?: return@forEachModInOrder
            event.modId = mod.modId
            try {
                bus.post(event)
            } catch (e: Throwable) {
                rethrowIfFatal(e)
                LnbPhone.LOGGER.error("{} failed while registering its phone apps; the ones it registered before stay", mod.modId, e)
            }
        }
        event.modId = null
        registry.freeze()
        LnbPhone.LOGGER.info("Phone apps ({}): {}", registry.apps.size, registry.apps.joinToString { it.id.toString() })
    }
}

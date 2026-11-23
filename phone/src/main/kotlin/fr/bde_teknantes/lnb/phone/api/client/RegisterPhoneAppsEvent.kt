package fr.bde_teknantes.lnb.phone.api.client

import fr.bde_teknantes.lnb.phone.client.apps.AppRegistry
import net.neoforged.bus.api.Event
import net.neoforged.fml.event.IModBusEvent

/**
 * Fired once, on the physical client, while the game starts (after client setup): the moment to add your apps to the
 * phone. It is a mod bus event, so listen to it on your mod's own bus, from a class that only the client loads:
 *
 * ```kotlin
 * @Mod("mymod")
 * class MyMod(modBus: IEventBus) {
 *     init {
 *         // Behind the check, so a dedicated server never loads the phone's client classes.
 *         if (FMLEnvironment.dist.isClient) MyModClient.init(modBus)
 *     }
 * }
 *
 * // In a `client` package of your mod:
 * object MyModClient {
 *     fun init(modBus: IEventBus) = modBus.addListener(::registerApps)
 *
 *     private fun registerApps(event: RegisterPhoneAppsEvent) {
 *         event.register(PhoneApp(id, name, icon) { NotesScreen() })
 *     }
 * }
 * ```
 *
 * Your mod must depend on `lnbphone` with `ordering="AFTER"` in its `neoforge.mods.toml`.
 *
 * The home screen lists apps in the order they were registered, mod by mod in loading order, and in the order of your
 * [register] calls within your mod. The phone's own Settings app always comes last.
 */
class RegisterPhoneAppsEvent internal constructor(private val registry: AppRegistry) : Event(), IModBusEvent {
    // The mod whose listeners are running, so that the log can say who registered what.
    internal var modId: String? = null

    /**
     * Adds [app] to the phone. If an app with the same [PhoneApp.id] is already registered (by your mod or another
     * one), [app] is left out and the log says so: the phone keeps the first one.
     *
     * @throws IllegalStateException when called after the event is over (keeping the event for later doesn't work).
     */
    fun register(app: PhoneApp) {
        registry.register(app, modId)
    }
}

package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import net.minecraft.resources.ResourceLocation

/**
 * The apps on the phone, in home screen order. Filled during [fr.bde_teknantes.lnb.phone.api.client.RegisterPhoneAppsEvent],
 * then frozen: from then on it is read only, from the client thread.
 */
internal class AppRegistry {
    private val byId = LinkedHashMap<ResourceLocation, Registered>()

    private class Registered(val app: PhoneApp, val modId: String?)

    var isFrozen = false
        private set

    /**
     * Every app in home screen order: registration order, except the phone's Settings app which always comes last
     * (art direction 6), wherever it was registered from.
     */
    var apps: List<PhoneApp> = emptyList()
        private set

    operator fun get(id: ResourceLocation): PhoneApp? = byId[id]?.app

    /**
     * Adds [app], unless its id is taken: then the first app stays and the newcomer is left out, so that two mods
     * clashing on an id cost one app, not the game. Ids in the phone's own namespace are kept for the phone's apps,
     * so that no mod can take Settings' place by registering first.
     *
     * @return whether [app] was added.
     */
    fun register(app: PhoneApp, modId: String?): Boolean {
        check(!isFrozen) { "Phone apps are registered during RegisterPhoneAppsEvent only; ${app.id} came too late" }
        if (app.id.namespace == LnbPhone.ID && modId != LnbPhone.ID) {
            LnbPhone.LOGGER.error(
                "Phone app {} from {} is left out: the {} namespace is kept for the phone's own apps",
                app.id, modId ?: "an unknown mod", LnbPhone.ID,
            )
            return false
        }
        val existing = byId[app.id]
        if (existing != null) {
            LnbPhone.LOGGER.error(
                "Phone app {} from {} is left out: {} already registered an app with that id",
                app.id, modId ?: "an unknown mod", existing.modId ?: "an unknown mod",
            )
            return false
        }
        byId[app.id] = Registered(app, modId)
        apps = order()
        return true
    }

    /** Ends registration: later [register] calls throw. */
    fun freeze() {
        isFrozen = true
    }

    private fun order(): List<PhoneApp> {
        val (settings, others) = byId.values.map { it.app }.partition { it.id == SETTINGS_ID }
        return others + settings
    }

    companion object {
        /** The phone's built-in Settings app, always last on the home screen. */
        val SETTINGS_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "settings")
    }
}

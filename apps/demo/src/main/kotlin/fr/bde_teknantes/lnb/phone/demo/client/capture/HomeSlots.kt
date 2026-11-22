package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.LnbPhoneClient
import net.minecraft.resources.ResourceLocation

/**
 * Where apps sit on the home screen, for scenarios that click an icon: by id, so that an app added to the demo doesn't
 * send them to the wrong one. The registry is internal to the phone: development code reaches it by reflection.
 */
internal object HomeSlots {
    /** The phone's Settings app. */
    val SETTINGS: ResourceLocation = ResourceLocation.fromNamespaceAndPath("lnbphone", "settings")

    /** Where the app [id] sits, in home order. */
    @Suppress("UNCHECKED_CAST")
    fun of(id: ResourceLocation): Int {
        val registry = LnbPhoneClient::class.java.getDeclaredField("apps").apply { isAccessible = true }.get(LnbPhoneClient)
        val apps = registry.javaClass.getMethod("getApps").invoke(registry) as List<PhoneApp>
        return apps.indexOfFirst { it.id == id }.also { check(it >= 0) { "$id isn't on the phone" } }
    }
}

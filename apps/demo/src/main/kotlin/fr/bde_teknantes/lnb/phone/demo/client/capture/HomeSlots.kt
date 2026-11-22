package fr.bde_teknantes.lnb.phone.demo.client.capture

import fr.bde_teknantes.lnb.phone.client.debug.CaptureHooks
import net.minecraft.resources.ResourceLocation

/**
 * Where apps sit on the home screen, for scenarios that click an icon: by id, so that an app added to the demo doesn't
 * send them to the wrong one.
 */
internal object HomeSlots {
    /** The phone's Settings app. */
    val SETTINGS: ResourceLocation = ResourceLocation.fromNamespaceAndPath("lnbphone", "settings")

    /** Where the app [id] sits, in home order. */
    fun of(id: ResourceLocation): Int = CaptureHooks.homeSlot(id).also { check(it >= 0) { "$id isn't on the phone" } }
}

package fr.bde_teknantes.lnb.phone.notifications

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * One notification, as the server stores it and the client mirrors it. Common code: both sides use it.
 *
 * A notification lives until the player opens its app (by any route: home icon, banner, peek), which removes it on
 * both sides; until an app's server side clears it; or until it is pushed out by newer ones past
 * [NotificationList.CAPACITY]. Every notification that exists is therefore unread: an app's badge is simply how many
 * of its notifications exist (art direction 6.1).
 *
 * @property id Unique on this server, increasing with each notification sent: newer is bigger.
 * @property appId The app that sent it, which the player opens to read it.
 * @property title Already limited by [NotificationLimits] (translatable components resolve in the player's language
 * on their client).
 * @property text As [title].
 * @property target Where in the app it leads ([fr.bde_teknantes.lnb.phone.api.AppTarget]), or null for the app's
 * first screen.
 * @property time When it was sent, in milliseconds since the epoch (server clock).
 * @property delivered Whether it has reached the player's client: true when the player was connected when it was
 * sent, or has logged in since. A notification sent while the player was away is not delivered until their next login,
 * whose sync tells the client which ones are new to it. This is not "read": reading means opening the app, which
 * removes the notification.
 */
data class PhoneNotification(
    val id: Long,
    val appId: ResourceLocation,
    val title: Component,
    val text: Component,
    val target: String?,
    val time: Long,
    val delivered: Boolean,
)

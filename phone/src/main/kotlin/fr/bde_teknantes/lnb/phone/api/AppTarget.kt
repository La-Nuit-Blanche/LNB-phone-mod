package fr.bde_teknantes.lnb.phone.api

/**
 * Where in an app a notification leads: a short string that only the app understands, such as `thread:4182`.
 *
 * The server attaches it when it sends the notification; the phone keeps it with the notification and, when the
 * player opens that notification, hands it back to the app, whose
 * [fr.bde_teknantes.lnb.phone.api.client.AppTargetScreens] picks the screens to show. A notification without a target,
 * or an app that doesn't handle targets, opens the app on its first screen.
 *
 * A target is stored on the server with the notification and sent over the network, so keep it to plain, small data:
 * an id or two, never the content itself (the app fetches that when it opens). At most [MAX_LENGTH] characters. It may
 * be read long after it was sent, by a newer version of the app: give it a prefix you can recognise and treat a target
 * you don't understand as none.
 *
 * Unlike the rest of the API, this lives on both sides: the server writes targets, the client reads them.
 */
object AppTarget {
    /** The longest target a notification can carry, in characters. */
    const val MAX_LENGTH = 256
}

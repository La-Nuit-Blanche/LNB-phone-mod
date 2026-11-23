package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.client.apps.PhoneNavigator
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification

/** What the phone shows when a notification arrives, as far as it matters to the notification. */
internal enum class PhoneView {
    /** Put away: the phone may peek. */
    CLOSED,

    /** Open on the home screen. */
    HOME,

    /** Open on the notification's own app. */
    SAME_APP,

    /** Open on another app. */
    OTHER_APP,
}

/**
 * The phone's state when a notification arrives. [targetOnScreen] is true only when the phone is open on the same
 * app and its shown screen says it already shows the notification's target ([fr.bde_teknantes.lnb.phone.api.client.AppScreen.showsTarget]).
 */
internal data class PhoneSituation(val view: PhoneView, val targetOnScreen: Boolean = false)

/** The player's settings that bear on notifications (Settings app, art direction 12; stored by the phone's settings). */
internal data class AlertSettings(val sounds: Boolean, val preview: Boolean)

internal enum class AlertVisual {
    /** Nothing moves; at most a badge waits on the home screen. */
    NONE,

    /** The banner slides down over the open phone (10.3). */
    BANNER,

    /** The closed phone peeks up from the bottom-right corner with the banner (10.2). */
    PEEK,
}

/**
 * What to do for a new notification.
 *
 * @property visual banner, peek or nothing.
 * @property chime whether to play the notification sound (15).
 * @property keep false when the notification is read at once (its target is on screen): remove it on both sides
 * ([ClientNotificationHandler.dismiss]) so it leaves no badge. When true, it stays as a badge (for an app this client
 * has) until the app is opened.
 */
internal data class Alert(val visual: AlertVisual, val chime: Boolean, val keep: Boolean = true) {
    companion object {
        /** Nothing seen or heard; the notification stays (a badge, if the app is installed). */
        val QUIET = Alert(AlertVisual.NONE, chime = false)
    }
}

/**
 * The rules of art direction 10 and flows P-8 to P-11 and P-14, as plain logic: given a notification that just
 * arrived and the phone's state, what happens.
 *
 * | Phone | Visual | Chime |
 * |---|---|---|
 * | closed, preview on | peek (P-8) | if sounds on |
 * | closed, preview off | none, the badge waits (P-14.3) | if sounds on |
 * | open on home (P-10), on another app (P-11), on the same app elsewhere | banner, whatever the preview setting (P-14.5) | if sounds on |
 * | open on the same app, target on screen | none, and the notification is read at once (10.3) | if sounds on |
 * | app not installed on this client | none | no |
 *
 * Notifications of the login sync never come here: they wait as badges, without a sound.
 */
internal object NotificationAlerts {
    fun decide(appInstalled: Boolean, situation: PhoneSituation, settings: AlertSettings): Alert {
        if (!appInstalled) return Alert.QUIET
        val chime = settings.sounds
        return when (situation.view) {
            PhoneView.CLOSED -> Alert(if (settings.preview) AlertVisual.PEEK else AlertVisual.NONE, chime)
            PhoneView.HOME, PhoneView.OTHER_APP -> Alert(AlertVisual.BANNER, chime)
            PhoneView.SAME_APP ->
                if (situation.targetOnScreen) Alert(AlertVisual.NONE, chime, keep = false) else Alert(AlertVisual.BANNER, chime)
        }
    }

    /**
     * Reads [notification]'s situation from [navigator]. A notification without a target is never "on screen": the
     * phone can't know whether the app's current screen is what its first screen would show.
     */
    fun situation(navigator: PhoneNavigator, notification: PhoneNotification): PhoneSituation {
        if (!navigator.isOpen) return PhoneSituation(PhoneView.CLOSED)
        val app = navigator.currentApp ?: return PhoneSituation(PhoneView.HOME)
        if (app.id != notification.appId) return PhoneSituation(PhoneView.OTHER_APP)
        val target = notification.target
        return PhoneSituation(PhoneView.SAME_APP, targetOnScreen = target != null && navigator.showsTarget(target))
    }

    /**
     * Opens [notification] where it leads: its app at its target (forward transition), then the phone if it was put
     * away. For the banner's click and for Up during a peek. Opening the app reads its notifications
     * ([ClientNotificationHandler.onAppOpened]). Returns false, with nothing changed, when the app isn't installed or
     * failed to start.
     *
     * Not from inside a navigation listener's callback (the navigator forbids navigating there).
     */
    fun open(navigator: PhoneNavigator, notification: PhoneNotification): Boolean {
        if (!navigator.openApp(notification.appId, notification.target)) return false
        if (!navigator.isOpen) navigator.open()
        return true
    }
}

package fr.bde_teknantes.lnb.phone.client.apps

import fr.bde_teknantes.lnb.phone.api.client.AppScreen
import fr.bde_teknantes.lnb.phone.api.client.Phone

/** The [Phone] handle of one screen: it acts on that screen's app, and does nothing once the screen is [closed]. */
internal class ScreenHost(
    private val navigator: PhoneNavigator,
    val session: AppSession,
    val screen: AppScreen,
) : Phone {
    var closed = false

    override fun push(screen: AppScreen) = navigator.push(this, screen)

    override fun pop() {
        navigator.pop(this)
    }

    override fun home() = navigator.home(this)

    override fun close() = navigator.close(this)
}

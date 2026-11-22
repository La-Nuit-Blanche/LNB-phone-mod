package fr.bde_teknantes.lnb.phone.api.client

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * An app on the phone: what the home screen shows for it, and how to start it.
 *
 * Register it in [RegisterPhoneAppsEvent]:
 *
 * ```kotlin
 * event.register(
 *     PhoneApp(
 *         id = ResourceLocation.fromNamespaceAndPath("mymod", "notes"),
 *         name = Component.translatable("app.mymod.notes"),
 *         icon = AppIcon.Glyph(TileColor.TEAL, ResourceLocation.fromNamespaceAndPath("mymod", "textures/gui/notes.png")),
 *     ) { NotesScreen() }
 * )
 * ```
 *
 * Everything in this package (`api.client`) exists only on the physical client: use it from a class of your mod
 * that a dedicated server never loads (see [RegisterPhoneAppsEvent]).
 *
 * @property id Identifies the app everywhere: on the home screen, in notifications sent from the server, in logs.
 * Use your mod id as the namespace so that two mods can't pick the same id. If two apps register the same id, the
 * first one wins and the second is left out (with an error in the log).
 * @property name The name shown on the home screen when the app is hovered, in the player's language. Use a
 * translatable component and give the key in both `en_us.json` and `fr_fr.json`. At most 16 characters in each
 * language: longer names are cut on the home screen.
 * @property icon The app's icon on the home screen and on its notifications.
 * @param rootScreen Creates the first screen of the app, each time the player opens it.
 */
class PhoneApp(
    val id: ResourceLocation,
    val name: Component,
    val icon: AppIcon,
    private val rootScreen: AppScreenFactory,
) {
    /** A new instance of the app's first screen. Called by the phone each time the app is opened. */
    fun createRootScreen(): AppScreen = rootScreen.create()

    override fun toString() = "PhoneApp($id)"
}

/**
 * Creates the first screen of an app. A lambda will do: `PhoneApp(id, name, icon) { NotesScreen() }`, or a
 * constructor reference: `PhoneApp(id, name, icon, ::NotesScreen)`.
 */
fun interface AppScreenFactory {
    /**
     * Returns a **new** screen each time: the phone throws a screen away when the app is left, and a screen is never
     * shown again once closed. Keep what must outlive a visit (a draft, a cache) outside the screen, in an object of
     * your own.
     */
    fun create(): AppScreen
}

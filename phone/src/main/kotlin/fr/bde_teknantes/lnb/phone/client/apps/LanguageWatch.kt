package fr.bde_teknantes.lnb.phone.client.apps

/**
 * Sends the open app home when the player changes the game's language. Widgets show plain strings, translated when the
 * screen was built, and the phone reopens on the screen it was left on: without this, Settings left open would come
 * back in the old language. Home, every name is shown anew; reopened, the app builds new screens in the new language.
 * What the app keeps outside its screens (drafts in a `TextState`, scroll positions) survives.
 *
 * Fed with the language after each resource reload, which is how the game applies a new language; client thread.
 */
internal class LanguageWatch(private val navigator: PhoneNavigator) {
    private var language: String? = null

    /** The game's resources were (re)loaded with [language] selected. */
    fun loaded(language: String) {
        val before = this.language
        this.language = language
        if (before != null && before != language) navigator.home()
    }
}

package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.api.AppTarget
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation

/**
 * The sizes a notification may have, enforced where it is created (the server API) and again when the saved file is
 * read, so that what is stored, saved and sent stays small whatever an app passes on from its players.
 *
 * Titles and texts are components, so that fixed wording is translated on each player's client. They are cut, never
 * refused: an app that forwards the start of a long post gets the start of the post. A banner shows about 23
 * characters of title and 46 of text (art direction 10.1), far less than these limits.
 */
object NotificationLimits {
    /** Characters of plain text a title may hold, all its parts and translation arguments together. */
    const val MAX_TITLE_LENGTH = 128

    /** Characters of plain text a text may hold, all its parts and translation arguments together. */
    const val MAX_TEXT_LENGTH = 320

    /** Longest app id, in characters (namespace, colon and path). */
    const val MAX_APP_ID_LENGTH = 128

    /**
     * Parts of one title or text: each component (nested, appended or passed as a translation argument) and each other
     * translation argument (a string, a number) is one. What comes after is dropped, or emptied where a translation
     * needs the argument's place.
     */
    const val MAX_PARTS = 32

    /** Arguments one translation keeps; later ones are dropped (a translation never needs that many). */
    const val MAX_ARGUMENTS = 16

    /** Longest translation key or keybind name kept; a longer one is dropped. */
    const val MAX_KEY_LENGTH = 256

    /** Characters of translation keys and keybind names in one title or text, all together; past it they are dropped. */
    const val MAX_KEY_CHARS = 512

    /** Checks [appId], whose meaning would change if it were cut: throws [IllegalArgumentException] when too long. */
    fun checkAppId(appId: ResourceLocation) {
        require(appId.toString().length <= MAX_APP_ID_LENGTH) {
            "App id $appId is longer than $MAX_APP_ID_LENGTH characters"
        }
    }

    /**
     * Checks [appId] and the [target] of a notification being sent: throws [IllegalArgumentException] when one is too
     * long. An empty target counts as none (the notification opens the app's first screen).
     */
    fun checkAddress(appId: ResourceLocation, target: String?): String? {
        checkAppId(appId)
        if (target.isNullOrEmpty()) return null
        return checkTarget(target)
    }

    /**
     * Checks a [target] to clear: neither empty (clearing the empty target would mean clearing the whole app, which is
     * another call) nor longer than [AppTarget.MAX_LENGTH], the bound the wire and the saved file share. Throws
     * [IllegalArgumentException] otherwise.
     */
    fun checkTarget(target: String): String {
        require(target.isNotEmpty()) { "A target to clear can't be empty: to clear all of an app's notifications, use clearApp" }
        require(target.length <= AppTarget.MAX_LENGTH) {
            "A notification target is at most ${AppTarget.MAX_LENGTH} characters; this one has ${target.length}"
        }
        return target
    }

    fun title(title: Component): Component = clamp(title, MAX_TITLE_LENGTH)

    fun text(text: Component): Component = clamp(text, MAX_TEXT_LENGTH)

    /**
     * A copy of [component] with at most [maxChars] characters of plain text (literal parts, string arguments and
     * fallbacks, in reading order; whatever comes after the budget is spent is cut), at most [MAX_PARTS] parts and
     * [MAX_ARGUMENTS] arguments per translation, at most [MAX_KEY_CHARS] characters of keys, and only content a client
     * can show by itself: plain text, translations and keybinds. Scores, selectors and NBT lookups, which only commands
     * resolve, become empty. No style is kept (colour, bold, font, click, hover...): the banner draws plain text, and a
     * later renderer must not honour a font or an obfuscation chosen by whoever wrote the text.
     *
     * The result's size is bounded whatever [component] is, so a notification always fits in the login sync.
     */
    fun clamp(component: Component, maxChars: Int): Component = Budget(maxChars).copy(component)

    private class Budget(var chars: Int) {
        var parts = MAX_PARTS
        var keyChars = MAX_KEY_CHARS

        fun copy(component: Component): MutableComponent {
            parts--
            val copy = contents(component)
            for (sibling in component.siblings) {
                if (parts <= 0) break
                copy.append(copy(sibling))
            }
            return copy
        }

        private fun contents(component: Component): MutableComponent = when (val contents = component.contents) {
            is PlainTextContents -> Component.literal(take(contents.text()))
            is TranslatableContents ->
                if (!takeKey(contents.key)) {
                    Component.empty()
                } else {
                    val args = contents.args.take(MAX_ARGUMENTS).map(::argument).toTypedArray()
                    val fallback = contents.fallback?.let(::take)
                    if (fallback != null) {
                        Component.translatableWithFallback(contents.key, fallback, *args)
                    } else {
                        Component.translatable(contents.key, *args)
                    }
                }
            is KeybindContents -> if (takeKey(contents.name)) Component.keybind(contents.name) else Component.empty()
            else -> Component.empty()
        }

        // Past the part budget an argument keeps its place, empty, so the translation's %2$s still finds an argument.
        // A boolean is turned into its text: saved or sent, Minecraft writes it as a byte and reads it back as 1 or 0.
        private fun argument(arg: Any?): Any = when {
            parts <= 0 -> ""
            arg is Component -> copy(arg)
            arg is Number -> arg.also { parts-- }
            else -> take(arg.toString()).also { parts-- }
        }

        private fun takeKey(key: String): Boolean {
            if (key.length > MAX_KEY_LENGTH || key.length > keyChars) return false
            keyChars -= key.length
            return true
        }

        /** The start of [text] that the budget still allows, never splitting a surrogate pair. */
        fun take(text: String): String {
            if (text.length <= chars) {
                chars -= text.length
                return text
            }
            var end = chars.coerceAtLeast(0)
            if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
            chars = 0
            return text.substring(0, end)
        }
    }
}

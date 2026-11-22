package fr.bde_teknantes.lnb.phone.notifications

import fr.bde_teknantes.lnb.phone.api.AppTarget
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.KeybindContents
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.ResourceLocation

/**
 * The sizes a notification may have, enforced where it is created (the server API), so that what is stored, saved and
 * sent stays small whatever an app passes on from its players.
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

    /** Components nested or appended in one title or text; the rest is dropped. */
    const val MAX_PARTS = 32

    /** Longest translation key or keybind name kept; a longer one is dropped. */
    const val MAX_KEY_LENGTH = 256

    /**
     * Checks [appId] and [target], whose meaning would change if they were cut: throws [IllegalArgumentException] when
     * one is too long. An empty target counts as none.
     */
    fun checkAddress(appId: ResourceLocation, target: String?): String? {
        require(appId.toString().length <= MAX_APP_ID_LENGTH) {
            "App id $appId is longer than $MAX_APP_ID_LENGTH characters"
        }
        if (target.isNullOrEmpty()) return null
        require(target.length <= AppTarget.MAX_LENGTH) {
            "A notification target is at most ${AppTarget.MAX_LENGTH} characters; this one has ${target.length}"
        }
        return target
    }

    fun title(title: Component): Component = clamp(title, MAX_TITLE_LENGTH)

    fun text(text: Component): Component = clamp(text, MAX_TEXT_LENGTH)

    /**
     * A copy of [component] with at most [maxChars] characters of plain text (literal parts, string arguments and
     * fallbacks, in reading order; whatever comes after the budget is spent is cut), at most [MAX_PARTS] parts, and
     * only content a client can show by itself: plain text, translations and keybinds. Click and hover events are
     * dropped (a banner is clicked as a whole and never hovered for a tooltip); so are scores, selectors and NBT
     * lookups, which only commands resolve.
     */
    fun clamp(component: Component, maxChars: Int): Component = Budget(maxChars).copy(component)

    private class Budget(var chars: Int) {
        var parts = MAX_PARTS

        fun copy(component: Component): MutableComponent {
            parts--
            val copy = contents(component)
            copy.setStyle(component.style.withClickEvent(null).withHoverEvent(null).withInsertion(null))
            for (sibling in component.siblings) {
                if (parts <= 0) break
                copy.append(copy(sibling))
            }
            return copy
        }

        private fun contents(component: Component): MutableComponent = when (val contents = component.contents) {
            is PlainTextContents -> Component.literal(take(contents.text()))
            is TranslatableContents ->
                if (contents.key.length > MAX_KEY_LENGTH) {
                    Component.empty()
                } else {
                    val args = contents.args.map(::argument).toTypedArray()
                    val fallback = contents.fallback?.let(::take)
                    if (fallback != null) {
                        Component.translatableWithFallback(contents.key, fallback, *args)
                    } else {
                        Component.translatable(contents.key, *args)
                    }
                }
            is KeybindContents -> if (contents.name.length > MAX_KEY_LENGTH) Component.empty() else Component.keybind(contents.name)
            else -> Component.empty()
        }

        // A boolean is turned into its text: saved or sent, Minecraft writes it as a byte and reads it back as 1 or 0.
        private fun argument(arg: Any?): Any = when (arg) {
            is Component -> if (parts > 0) copy(arg) else Component.empty()
            is Number -> arg
            else -> take(arg.toString())
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

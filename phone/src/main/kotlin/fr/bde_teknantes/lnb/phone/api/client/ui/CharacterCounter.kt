package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.api.TextLimits

/**
 * The character counter of a [TextArea] (art direction 8.7): what it reads and in which colour, for a text of [count]
 * characters against a [limit]. Characters are counted by [TextLimits], the rule an app's server uses too. Plain
 * logic, usable in tests.
 *
 * - up to `limit - 20` characters: `142/280` in [Colors.TEXT_2];
 * - the last 20 before the limit: `265/280` in [Colors.WARNING];
 * - past the limit: the excess as `-5`, in [Colors.DANGER], and the text can't be sent.
 */
object CharacterCounter {
    /** How many characters before the limit the counter turns amber. */
    val WARNING_ZONE = 20

    /** The counter's text: `142/280`, or `-5` past the limit. */
    fun label(count: Int, limit: Int): String = if (count > limit) "-${count - limit}" else "$count/$limit"

    /** The counter's colour. */
    fun color(count: Int, limit: Int): Int = when {
        count > limit -> Colors.DANGER
        count > limit - WARNING_ZONE -> Colors.WARNING
        else -> Colors.TEXT_2
    }

    /** Whether [text] can be sent: [TextLimits.canSend]. The counter and the send button then always agree. */
    fun canSend(text: String, limit: Int): Boolean = TextLimits.canSend(text, limit)

    /** Characters as limits count them: [TextLimits.count]. */
    fun count(text: String): Int = TextLimits.count(text)

    /**
     * Index in [text] (normalised, as a [TextState] holds it) where the characters past [limit] start, or null when
     * it is within the limit.
     */
    fun overflowStart(text: String, limit: Int): Int? =
        if (text.codePointCount(0, text.length) <= limit) null else text.offsetByCodePoints(0, limit)
}

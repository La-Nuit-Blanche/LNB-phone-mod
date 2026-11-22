package fr.bde_teknantes.lnb.phone.api

import java.text.Normalizer

/**
 * How the phone counts characters against a limit, such as the 280 of a post. On both sides: an app's server checks
 * what it receives with the same rule the client's counter shows, so the server never refuses what the player was
 * allowed to send.
 *
 * A character is a code point of the text in Unicode normalisation form C: `é` is one whether it was typed as one
 * character or pasted as `e` plus a combining accent, and an emoji outside the basic plane is one. The client's text
 * fields store text already normalised.
 *
 * The count is of the text as typed, spaces and line feeds included, so the counter and the send button always agree.
 * Send the text trimmed ([String.trim]): it can only get shorter, so what the client allowed always passes the server's
 * check.
 */
object TextLimits {
    /** [text] in normalisation form C, as text fields store it. */
    fun normalize(text: String): String = if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text else Normalizer.normalize(text, Normalizer.Form.NFC)

    /** Number of characters in [text], by the rule above. */
    fun count(text: String): Int {
        val normalized = normalize(text)
        return normalized.codePointCount(0, normalized.length)
    }

    /** Whether [text] is within [limit] characters. */
    fun fits(text: String, limit: Int): Boolean = count(text) <= limit

    /** Whether [text] can be sent: something besides spaces and line feeds, and within [limit]. */
    fun canSend(text: String, limit: Int): Boolean = text.isNotBlank() && fits(text, limit)
}

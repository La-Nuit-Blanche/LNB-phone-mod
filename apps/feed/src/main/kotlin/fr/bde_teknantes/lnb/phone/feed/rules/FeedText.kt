package fr.bde_teknantes.lnb.phone.feed.rules

import fr.bde_teknantes.lnb.phone.feed.Feed
import java.text.Normalizer

/**
 * The site's text rule for posts and replies (`docs/feed.md`, "Text"; vectors in `docs/feed-mod.md` 13.1), in pure
 * Kotlin, so that the memory backend accepts and stores exactly what the site would:
 *
 * 1. clean: CR LF and a lone CR become LF, a tab becomes a space, and the code points the phone can't type are
 *    dropped (U+0000 to U+001F except LF, U+007F to U+009F, U+00A7 and unpaired surrogates: the phone's
 *    `TextState.isAllowed`);
 * 2. normalise to NFC;
 * 3. trim as Kotlin's [String.trim] does (after step 1: LF, the space separators, U+2028 and U+2029; not U+FEFF);
 * 4. fold a run of three or more LF into two;
 * 5. count code points: 1 to [Feed.MAX_TEXT].
 *
 * Every step only removes or shortens, so a text the phone's `TextLimits.canSend(text.trim(), 280)` allows always
 * passes.
 */
object FeedText {
    sealed interface Check {
        /** Accepted: [text] is what is stored and shown. */
        data class Ok(val text: String) : Check {
            override fun toString() = "Ok(${text.codePointCount(0, text.length)} code points)"
        }

        /** Nothing left after cleaning: the site's `emptyText`. */
        data object Empty : Check

        /** More than [Feed.MAX_TEXT] code points after cleaning: the site's `tooLong`. */
        data class TooLong(val count: Int) : Check
    }

    private val BLANK_LINES = Regex("\n{3,}")

    /** Steps 1 to 4: the text as the site stores it. */
    fun clean(raw: String): String {
        val cleaned = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val codePoint = raw.codePointAt(i)
            i += Character.charCount(codePoint)
            when {
                codePoint == '\r'.code -> {
                    cleaned.append('\n')
                    // CR LF is one line feed.
                    if (i < raw.length && raw[i] == '\n') i++
                }
                codePoint == '\t'.code -> cleaned.append(' ')
                codePoint == '\n'.code || isAllowed(codePoint) -> cleaned.appendCodePoint(codePoint)
            }
        }
        val normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFC)
        return normalized.trim().replace(BLANK_LINES, "\n\n")
    }

    /** Code points of [text], as the site counts them (on text already cleaned). */
    fun count(text: String): Int = text.codePointCount(0, text.length)

    /** The whole rule. */
    fun check(raw: String): Check {
        val text = clean(raw)
        val count = count(text)
        return when {
            count == 0 -> Check.Empty
            count > Feed.MAX_TEXT -> Check.TooLong(count)
            else -> Check.Ok(text)
        }
    }

    /** The phone's `TextState.isAllowed`: what a player can type in a text field. */
    private fun isAllowed(codePoint: Int): Boolean =
        codePoint >= 0x20 && codePoint != 0x7F && codePoint !in 0x80..0x9F && codePoint != 0xA7 &&
            codePoint !in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code
}

package fr.bde_teknantes.lnb.phone.feed.rules

import fr.bde_teknantes.lnb.phone.api.TextLimits
import fr.bde_teknantes.lnb.phone.feed.Feed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The text vectors of `docs/feed-mod.md` 13.1, which the site's tests use too. */
class FeedTextTest {
    private fun accepted(raw: String, stored: String, count: Int) {
        val check = FeedText.check(raw)
        assertEquals(FeedText.Check.Ok(stored), check, "for ${raw.escaped()}")
        assertEquals(count, FeedText.count(stored))
    }

    private fun String.escaped() = map { if (it.code < 0x20 || it.code > 0x7E) "\\u%04x".format(it.code) else it.toString() }.joinToString("")

    @Test
    fun `the vectors that are accepted`() {
        accepted("  Salut  ", "Salut", 5)
        accepted("été", "été", 3)
        accepted("👍🏽", "👍🏽", 2)
        accepted("🇫🇷", "🇫🇷", 2)
        accepted("a".repeat(280), "a".repeat(280), 280)
        accepted("a".repeat(279) + "é", "a".repeat(279) + "é", 280)
        accepted("a\r\nb", "a\nb", 3)
        accepted("a\rb", "a\nb", 3)
        accepted("a\tb", "a b", 3)
        accepted("§cRouge", "cRouge", 6)
        accepted("a\u0000b\u0085c", "abc", 3)
        accepted("a\n\n\n\nb", "a\n\nb", 4)
        accepted("　bonjour ", "bonjour", 7)
        accepted("﻿hi", "﻿hi", 3)
        accepted("a​b", "a​b", 3)
    }

    @Test
    fun `the vectors that are refused`() {
        assertEquals(FeedText.Check.TooLong(281), FeedText.check("a".repeat(281)))
        assertEquals(FeedText.Check.Empty, FeedText.check("   \n   "))
        assertEquals(FeedText.Check.Empty, FeedText.check(""))
    }

    @Test
    fun `an unpaired surrogate is dropped, and a lone CR then LF still makes two lines`() {
        accepted("a\ud800b", "ab", 2)
        accepted("a\r\rb", "a\n\nb", 4)
    }

    @Test
    fun `whatever the phone lets a player send, the rule accepts`() {
        val samples = listOf(
            "a".repeat(280), " ".repeat(10) + "x".repeat(280) + "\n\n", "é".repeat(280), "👍".repeat(280),
            "line\n\n\n\nline", " spaced ", "tab\tbed",
        )
        for (raw in samples) {
            val sent = raw.trim()
            if (!TextLimits.canSend(sent, Feed.MAX_TEXT)) continue
            assertInstanceOf(FeedText.Check.Ok::class.java, FeedText.check(sent), "for ${raw.escaped()}")
        }
        assertTrue(TextLimits.canSend("é".repeat(280), Feed.MAX_TEXT))
    }
}

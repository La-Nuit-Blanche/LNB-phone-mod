package fr.bde_teknantes.lnb.phone.feed.client

import fr.bde_teknantes.lnb.phone.feed.client.FeedLang.ENGLISH
import fr.bde_teknantes.lnb.phone.feed.client.FeedLang.FRENCH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Times and counts (art direction 9.3, 13.1, 13.2) with the mod's real language files, at each boundary, in both
 * languages.
 */
class FeedFormatLanguagesTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-11-12T13:34:00Z")

    private fun ago(seconds: Long, strings: FeedStrings) = FeedFormat.relativeTime(now.minusSeconds(seconds), now, paris, strings)

    @Test
    fun `relative times at each step, in French and in English`() {
        val steps = listOf(
            0L to ("à l'instant" to "now"),
            59L to ("à l'instant" to "now"),
            60L to ("1 min" to "1m"),
            3599L to ("59 min" to "59m"),
            3600L to ("1 h" to "1h"),
            86_399L to ("23 h" to "23h"),
            86_400L to ("1 j" to "1d"),
            7 * 86_400L - 1 to ("6 j" to "6d"),
            7 * 86_400L to ("5 nov." to "5 Nov"),
        )
        for ((seconds, words) in steps) {
            assertEquals(words.first, ago(seconds, FRENCH), "$seconds s, French")
            assertEquals(words.second, ago(seconds, ENGLISH), "$seconds s, English")
        }
    }

    @Test
    fun `a post a little ahead of the client's clock reads as now`() {
        assertEquals("à l'instant", ago(-3600, FRENCH))
        assertEquals("now", ago(-1, ENGLISH))
    }

    @Test
    fun `every month has its short name, in both languages`() {
        val french = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
        val english = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        for (month in 1..12) {
            val at = Instant.parse("2026-%02d-15T12:00:00Z".format(month))
            val later = Instant.parse("2026-12-31T12:00:00Z")
            assertEquals("15 ${french[month - 1]}", FeedFormat.date(at, later, paris, FRENCH))
            assertEquals("15 ${english[month - 1]}", FeedFormat.date(at, later, paris, ENGLISH))
        }
    }

    @Test
    fun `the year shows when it isn't this year, as the player's zone counts years`() {
        val newYear = Instant.parse("2027-01-08T12:00:00Z")
        // 23:30 on 31 December in UTC is already 1 January in Paris.
        val eve = Instant.parse("2026-12-31T23:30:00Z")
        assertEquals("1 janv.", FeedFormat.relativeTime(eve, newYear, paris, FRENCH))
        assertEquals("31 déc. 2026", FeedFormat.relativeTime(eve, newYear, ZoneOffset.UTC, FRENCH))
        assertEquals("31 Dec 2026", FeedFormat.relativeTime(eve, newYear, ZoneOffset.UTC, ENGLISH))
    }

    @Test
    fun `the full timestamp is 24-hour with two digits, in both languages`() {
        val morning = Instant.parse("2026-11-03T08:05:00Z")
        assertEquals("09:05 · 3 nov.", FeedFormat.fullTime(morning, now, paris, FRENCH))
        assertEquals("09:05 · 3 Nov", FeedFormat.fullTime(morning, now, paris, ENGLISH))
        val midnight = Instant.parse("2026-11-11T23:00:00Z")
        assertEquals("00:00 · 12 nov.", FeedFormat.fullTime(midnight, now, paris, FRENCH))
        val evening = Instant.parse("2025-06-01T21:59:00Z")
        assertEquals("23:59 · 1 Jun 2025", FeedFormat.fullTime(evening, now, paris, ENGLISH))
    }

    @Test
    fun `counts - none at 0, as is under 1,000, then thousands with one decimal cut, a no-break space in French`() {
        val cases = listOf(
            0 to ("" to ""),
            -3 to ("" to ""),
            1 to ("1" to "1"),
            999 to ("999" to "999"),
            // `{n},{d} k` always writes its decimal (art direction 13.2).
            1000 to ("1,0 k" to "1.0k"),
            1099 to ("1,0 k" to "1.0k"),
            1250 to ("1,2 k" to "1.2k"),
            // Cut, not rounded: never more than there is.
            1999 to ("1,9 k" to "1.9k"),
            12_345 to ("12,3 k" to "12.3k"),
            999_999 to ("999,9 k" to "999.9k"),
            // No step past thousands in the art direction.
            1_000_000 to ("1000,0 k" to "1000.0k"),
        )
        for ((n, words) in cases) {
            assertEquals(words.first, FeedFormat.count(n, FRENCH), "$n, French")
            assertEquals(words.second, FeedFormat.count(n, ENGLISH), "$n, English")
        }
    }
}

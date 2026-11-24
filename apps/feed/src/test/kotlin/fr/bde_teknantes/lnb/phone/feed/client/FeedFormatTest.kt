package fr.bde_teknantes.lnb.phone.feed.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class FeedFormatTest {
    // The French strings of art direction 13.2, with the arguments in place.
    private val french = FeedStrings { id, args ->
        val format = when (id) {
            "time.now" -> "à l'instant"
            "time.minutes" -> "%s min"
            "time.hours" -> "%s h"
            "time.days" -> "%s j"
            "time.date" -> "%s %s"
            "time.date_year" -> "%s %s %s"
            "time.full" -> "%s · %s"
            "time.month.11" -> "nov."
            "time.month.10" -> "oct."
            "count.thousands" -> "%s,%s k"
            "count.thousands_whole" -> "%s k"
            else -> error("unexpected $id")
        }
        format.format(*args)
    }
    private val paris = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-11-12T13:34:00Z")

    private fun ago(seconds: Long) = FeedFormat.relativeTime(now.minusSeconds(seconds), now, paris, french)

    @Test
    fun `relative times follow the art direction's steps`() {
        assertEquals("à l'instant", ago(0))
        assertEquals("à l'instant", ago(59))
        assertEquals("à l'instant", ago(-30))
        assertEquals("1 min", ago(60))
        assertEquals("59 min", ago(3599))
        assertEquals("1 h", ago(3600))
        assertEquals("23 h", ago(86399))
        assertEquals("1 j", ago(86400))
        assertEquals("6 j", ago(7 * 86400 - 1))
        assertEquals("5 nov.", ago(7 * 86400))
        // 26 October, from the sample content's post 9.
        assertEquals("26 oct.", FeedFormat.relativeTime(Instant.parse("2026-10-26T09:34:00Z"), now, paris, french))
        assertEquals("26 oct. 2025", FeedFormat.relativeTime(Instant.parse("2025-10-26T09:34:00Z"), now, paris, french))
    }

    @Test
    fun `the full timestamp is 24-hour, in the player's zone`() {
        assertEquals("14:32 · 12 nov.", FeedFormat.fullTime(Instant.parse("2026-11-12T13:32:00Z"), now, paris, french))
    }

    @Test
    fun `counts hide 0 and shorten thousands without rounding up`() {
        assertEquals("", FeedFormat.count(0, french))
        assertEquals("999", FeedFormat.count(999, french))
        assertEquals("1 k", FeedFormat.count(1000, french))
        assertEquals("1,9 k", FeedFormat.count(1999, french))
        assertEquals("1,2 k", FeedFormat.count(1299, french))
        assertEquals("12,3 k", FeedFormat.count(12345, french))
    }
}

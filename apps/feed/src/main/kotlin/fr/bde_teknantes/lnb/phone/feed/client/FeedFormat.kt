package fr.bde_teknantes.lnb.phone.feed.client

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Looks up one of Feed's strings by its art direction ID (`time.minutes`, `feed.end`...), with its arguments: the
 * game's language on the client ([FeedClient]), the bare key in tests. Kept as a parameter so that the formatting
 * below is plain logic.
 */
fun interface FeedStrings {
    fun get(id: String, vararg args: Any): String
}

/**
 * How Feed writes times and counts (art direction 9.3 and 13.2). Plain logic: the clock, the zone and the strings are
 * given, so tests pin every case.
 */
object FeedFormat {
    private const val MINUTE = 60L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /**
     * The relative time of a post created at [createdAt], seen at [now] in [zone]: `à l'instant` under a minute (and
     * for a time a little ahead of the client's clock), minutes under an hour, hours under a day, days under a week,
     * then the short date (with the year when it isn't [now]'s year).
     */
    fun relativeTime(createdAt: Instant, now: Instant, zone: ZoneId, strings: FeedStrings): String {
        val seconds = Duration.between(createdAt, now).seconds
        return when {
            seconds < MINUTE -> strings.get("time.now")
            seconds < HOUR -> strings.get("time.minutes", seconds / MINUTE)
            seconds < DAY -> strings.get("time.hours", seconds / HOUR)
            seconds < 7 * DAY -> strings.get("time.days", seconds / DAY)
            else -> date(createdAt, now, zone, strings)
        }
    }

    /** `12 nov.` / `12 Nov`, with the year when it isn't [now]'s (`12 nov. 2025`). */
    fun date(at: Instant, now: Instant, zone: ZoneId, strings: FeedStrings): String {
        val day = ZonedDateTime.ofInstant(at, zone)
        val month = strings.get("time.month.${day.monthValue}")
        return if (day.year == ZonedDateTime.ofInstant(now, zone).year) {
            strings.get("time.date", day.dayOfMonth, month)
        } else {
            strings.get("time.date_year", day.dayOfMonth, month, day.year)
        }
    }

    /** The thread's full timestamp, `14:32 · 12 nov.` (24-hour in both languages). */
    fun fullTime(at: Instant, now: Instant, zone: ZoneId, strings: FeedStrings): String {
        val time = ZonedDateTime.ofInstant(at, zone)
        return strings.get("time.full", "%02d:%02d".format(time.hour, time.minute), date(at, now, zone, strings))
    }

    /**
     * A count of the action row: empty at 0 (the icon alone), as is under 1,000, then thousands with one decimal cut,
     * not rounded (`1,2 k` / `1.2k` for 1,299: never more than there is).
     */
    fun count(n: Int, strings: FeedStrings): String = when {
        n <= 0 -> ""
        n < 1000 -> n.toString()
        else -> strings.get("count.thousands", n / 1000, n % 1000 / 100)
    }
}

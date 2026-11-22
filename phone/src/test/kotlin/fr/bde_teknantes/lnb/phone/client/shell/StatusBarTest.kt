package fr.bde_teknantes.lnb.phone.client.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Section 4 of the art direction: clock, signal, battery. */
class StatusBarTest {
    // Worked out apart from the code's formula: each in-game hour is 1000 ticks, tick 0 is 06:00.
    private fun expectedClock(dayTime: Long): String {
        val ticks = Math.floorMod(dayTime, 24000L)
        val hour = (6 + ticks / 1000) % 24
        val minute = (ticks % 1000) * 60 / 1000
        return "%02d:%02d".format(hour, minute)
    }

    @Test
    fun `the clock over every tick of a day`() {
        for (tick in 0L until 24000L) assertEquals(expectedClock(tick), StatusBar.clock(tick, false), "tick $tick")
    }

    @Test
    fun `the clock's landmarks`() {
        assertEquals("06:00", StatusBar.clock(0, false))
        assertEquals("06:00", StatusBar.clock(16, false))
        assertEquals("06:01", StatusBar.clock(17, false))
        assertEquals("07:00", StatusBar.clock(1000, false))
        assertEquals("12:00", StatusBar.clock(6000, false))
        assertEquals("18:00", StatusBar.clock(12000, false))
        assertEquals("23:59", StatusBar.clock(17999, false))
        // Midnight wraps to 00:00, not 24:00.
        assertEquals("00:00", StatusBar.clock(18000, false))
        assertEquals("05:59", StatusBar.clock(23999, false))
        assertEquals("06:00", StatusBar.clock(24000, false))
    }

    @Test
    fun `days beyond the first, and day times before zero`() {
        for (day in listOf(1L, 2L, 7L, 100L, 1_000_000L, Long.MAX_VALUE / 24000L - 1)) {
            for (tick in listOf(0L, 1L, 6000L, 17999L, 18000L, 23999L)) {
                assertEquals(StatusBar.clock(tick, false), StatusBar.clock(day * 24000L + tick, false), "day $day, tick $tick")
            }
        }
        assertEquals("05:59", StatusBar.clock(-1, false))
        assertEquals("06:00", StatusBar.clock(-24000, false))
        assertEquals(expectedClock(Long.MAX_VALUE), StatusBar.clock(Long.MAX_VALUE, false))
        assertEquals(expectedClock(Long.MIN_VALUE), StatusBar.clock(Long.MIN_VALUE, false))
    }

    @Test
    fun `dimensions without a day cycle show no time`() {
        for (tick in listOf(0L, 6000L, 18000L, -5L, 123456789L)) assertEquals("--:--", StatusBar.clock(tick, true))
    }

    @Test
    fun `the clock is centred on its drawn width`() {
        // 4.1: "14:13" is 25 px wide, so sx = 51.
        assertEquals(51, StatusBar.clockX(25))
        assertEquals(51, StatusBar.clockX(26))
        assertEquals(64, StatusBar.clockX(0))
    }

    @Test
    fun `signal bars at each latency boundary`() {
        val expected = mapOf(
            0 to 4, 1 to 4, 149 to 4,
            150 to 3, 151 to 3, 299 to 3,
            300 to 2, 301 to 2, 599 to 2,
            600 to 1, 601 to 1, 5000 to 1, Int.MAX_VALUE to 1,
        )
        for ((ms, bars) in expected) assertEquals(bars, StatusBar.signalBars(ms, singleplayer = false), "$ms ms")
    }

    @Test
    fun `no bars in singleplayer or while the latency is unknown`() {
        assertEquals(0, StatusBar.signalBars(null, singleplayer = false))
        assertEquals(0, StatusBar.signalBars(null, singleplayer = true))
        for (ms in listOf(0, 100, 200, 400, 1000)) assertEquals(0, StatusBar.signalBars(ms, singleplayer = true))
    }

    @Test
    fun `the battery at every hunger value`() {
        // round(food * 8 / 20) never lands on a half for whole food values.
        val cells = listOf(0, 0, 1, 1, 2, 2, 2, 3, 3, 4, 4, 4, 5, 5, 6, 6, 6, 7, 7, 8, 8)
        for (food in 0..20) {
            val color = when {
                food >= 7 -> StatusBar.FOOD_GREEN
                food >= 4 -> StatusBar.FOOD_ORANGE
                else -> StatusBar.FOOD_RED
            }
            assertEquals(StatusBar.Battery(cells[food], color), StatusBar.battery(food, needsFood = true), "food $food")
        }
    }

    @Test
    fun `battery colours match the art direction`() {
        assertEquals(0xFF58C83C.toInt(), StatusBar.FOOD_GREEN)
        assertEquals(0xFFF28A2E.toInt(), StatusBar.FOOD_ORANGE)
        assertEquals(0xFFE5483C.toInt(), StatusBar.FOOD_RED)
    }

    @Test
    fun `hunger out of range is clamped, and players who never hunger see a full battery`() {
        assertEquals(StatusBar.Battery(0, StatusBar.FOOD_RED), StatusBar.battery(-3, needsFood = true))
        assertEquals(StatusBar.Battery(8, StatusBar.FOOD_GREEN), StatusBar.battery(25, needsFood = true))
        for (food in listOf(-1, 0, 3, 10, 20)) {
            assertEquals(StatusBar.Battery(8, StatusBar.FOOD_GREEN), StatusBar.battery(food, needsFood = false))
        }
    }
}

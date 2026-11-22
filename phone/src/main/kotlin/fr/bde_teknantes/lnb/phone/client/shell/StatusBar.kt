package fr.bde_teknantes.lnb.phone.client.shell

import java.util.Locale
import kotlin.math.roundToInt

/** What the status bar shows (art direction 4), computed from plain numbers so it can be tested without a world. */
internal object StatusBar {
    /** Screen-local positions: every element sits on rows 2..8. */
    const val ROW = 2
    const val SIGNAL_X = 4
    const val BATTERY_X = 111
    const val SIGNAL_WIDTH = 11
    const val ELEMENT_HEIGHT = 7
    const val BATTERY_WIDTH = 13

    /** The battery's fill area, battery-local: 8 x 3 at (2, 2). */
    const val FILL_X = 2
    const val FILL_Y = 2
    const val FILL_CELLS = 8
    const val FILL_HEIGHT = 3

    const val NO_TIME = "--:--"

    const val FOOD_GREEN = 0xFF58C83C.toInt()
    const val FOOD_ORANGE = 0xFFF28A2E.toInt()
    const val FOOD_RED = 0xFFE5483C.toInt()

    /**
     * In-game time as `HH:MM`, 24-hour (4.1): tick 0 of the day is 06:00. In a dimension where time stands still
     * (Nether, End), `--:--`.
     */
    fun clock(dayTime: Long, fixedTime: Boolean): String {
        if (fixedTime) return NO_TIME
        val ticks = Math.floorMod(dayTime, 24000L)
        val minutes = (ticks * 1440 / 24000 + 360) % 1440
        // Root locale: some locales would format the digits in another script, which the bitmap font lacks.
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    }

    /** The clock's left edge, screen-local: centred on its drawn width (the font's advance minus the last gap). */
    fun clockX(drawnWidth: Int): Int = Math.floorDiv(Device.SCREEN_WIDTH - drawnWidth, 2)

    /**
     * Lit signal bars from the player's own latency (4.2): under 150 ms 4, under 300 ms 3, under 600 ms 2, otherwise
     * 1. None in singleplayer, and none while the latency is unknown ([latencyMs] null).
     */
    fun signalBars(latencyMs: Int?, singleplayer: Boolean): Int = when {
        singleplayer || latencyMs == null -> 0
        latencyMs < 150 -> 4
        latencyMs < 300 -> 3
        latencyMs < 600 -> 2
        else -> 1
    }

    /** The battery gauge: how many of the 8 cells are filled, and their colour. */
    data class Battery(val cells: Int, val color: Int)

    /**
     * The battery from the player's hunger (4.3): `round(food * 8 / 20)` cells, green from 7 food, orange from 4, red
     * below. Players who don't get hungry (creative, spectator: [needsFood] false) see it full and green.
     */
    fun battery(food: Int, needsFood: Boolean): Battery {
        if (!needsFood) return Battery(FILL_CELLS, FOOD_GREEN)
        val clamped = food.coerceIn(0, 20)
        val color = when {
            clamped >= 7 -> FOOD_GREEN
            clamped >= 4 -> FOOD_ORANGE
            else -> FOOD_RED
        }
        return Battery((clamped * FILL_CELLS / 20f).roundToInt(), color)
    }
}

package fr.bde_teknantes.lnb.phone.client.shell

import kotlin.math.max

/** Where the open phone stands on the screen, chosen by each player in Settings (art direction 2.4 and 12). */
internal enum class Placement {
    /** Bottom-right, 6 px from the window's right and bottom edges, the world visible around it. The default. */
    CORNER,

    /** In the middle of the window, over a dimming scrim. */
    CENTRE,
}

/** A point in window GUI px. */
internal data class Point(val x: Int, val y: Int)

/** Where the device goes in a window of [width] x [height] GUI px. Plain arithmetic, testable without the game. */
internal object PhoneLayout {
    /**
     * The open device's top-left corner (art direction 2.4): `(W - 146, H - 234)` in the corner, `(floor((W - 140) / 2),
     * floor((H - 228) / 2))` in the centre.
     *
     * The document gives no rule for a window smaller than the device, which only happens at GUI scale 1 in a window
     * under 234 real pixels tall (Minecraft keeps the scaled window at 320 x 240 or more otherwise). There the device
     * is pinned to the top and left edges, so that its status bar and the top of the app stay visible and the bottom
     * of the device runs off the window.
     */
    fun devicePosition(placement: Placement, width: Int, height: Int): Point = when (placement) {
        Placement.CORNER -> Point(
            max(0, width - Device.WIDTH - Device.CORNER_MARGIN),
            max(0, height - Device.HEIGHT - Device.CORNER_MARGIN),
        )
        Placement.CENTRE -> Point(
            max(0, Math.floorDiv(width - Device.WIDTH, 2)),
            max(0, Math.floorDiv(height - Device.HEIGHT, 2)),
        )
    }

    /**
     * How far the closed phone rises when it peeks (art direction 10.2): 72 px, which shows the top bezel and the
     * screen's first 60 rows; 112 px in a window narrower than 476 px, where the hotbar and the food row reach under
     * the device and the banner must clear them.
     */
    fun peekRise(width: Int): Int = if (width < NARROW_PEEK_WIDTH) PEEK_RISE_NARROW else PEEK_RISE

    /**
     * The peeking device's top-left corner when it is [progress] of the way up (0: hidden under the window's bottom
     * edge, 1: fully risen), whole GUI px. Always in the corner, whatever the placement (2.4): `x = W - 146`.
     */
    fun peekPosition(width: Int, height: Int, progress: Float): Point = Point(
        devicePosition(Placement.CORNER, width, height).x,
        Motion.snap(height - peekRise(width) * progress.coerceIn(0f, 1f)),
    )

    const val PEEK_RISE = 72
    const val PEEK_RISE_NARROW = 112
    const val NARROW_PEEK_WIDTH = 476

    /** How dark the scrim is when the phone stands open in [placement]: full in the centre, none in the corner. */
    fun scrimAmount(placement: Placement): Float = if (placement == Placement.CENTRE) 1f else 0f

    /** The scrim's colour for an [amount] from 0 (none) to 1 (`#0000004D`). */
    fun scrimColor(amount: Float): Int {
        // Rounded like positions: half of 0x4D is 0x26, as in P-3.2.
        val alpha = Motion.snap((ShellColors.SCRIM ushr 24) * amount.coerceIn(0f, 1f))
        return alpha shl 24
    }
}

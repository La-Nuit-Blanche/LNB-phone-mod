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

    /** How dark the scrim is when the phone stands open in [placement]: full in the centre, none in the corner. */
    fun scrimAmount(placement: Placement): Float = if (placement == Placement.CENTRE) 1f else 0f

    /** The scrim's colour for an [amount] from 0 (none) to 1 (`#0000004D`). */
    fun scrimColor(amount: Float): Int {
        // Rounded like positions: half of 0x4D is 0x26, as in P-3.2.
        val alpha = Motion.snap((ShellColors.SCRIM ushr 24) * amount.coerceIn(0f, 1f))
        return alpha shl 24
    }
}

package fr.bde_teknantes.lnb.phone.api.client

/**
 * What an [AppScreen] draws on: the app viewport, the part of the phone's screen under the status bar.
 *
 * Coordinates are in **dp**, with (0, 0) at the top-left corner of the viewport, x to the right and y downwards. The
 * viewport is always [WIDTH] x [HEIGHT] dp, whatever the window size or GUI scale (2 dp make one GUI pixel). Anything
 * drawn outside it is cut off. Put the edges of rectangles on even dp values so they stay sharp at every GUI scale.
 */
interface AppCanvas {
    /** Width of the viewport in dp: [WIDTH]. */
    val width: Int

    /** Height of the viewport in dp: [HEIGHT]. */
    val height: Int

    companion object {
        /** Width of the app viewport in dp. */
        const val WIDTH = 256

        /** Height of the app viewport in dp. */
        const val HEIGHT = 370
    }
}

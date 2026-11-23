package fr.bde_teknantes.lnb.phone.client.shell

/**
 * Where the parts of the device sit, in GUI px (art direction 2.1). Device-local coordinates: (0, 0) is the device's
 * top-left pixel.
 */
internal object Device {
    const val WIDTH = 140
    const val HEIGHT = 228

    /** The screen, under the status bar and around the viewport: 128 x 196 at (6, 12). */
    const val SCREEN_X = 6
    const val SCREEN_Y = 12
    const val SCREEN_WIDTH = 128
    const val SCREEN_HEIGHT = 196

    /** The status bar: the screen's first 11 rows. */
    const val STATUS_HEIGHT = 11

    /** The app viewport: the rest of the screen, 128 x 185 GUI px (256 x 370 dp). */
    const val VIEWPORT_X = SCREEN_X
    const val VIEWPORT_Y = SCREEN_Y + STATUS_HEIGHT
    const val VIEWPORT_WIDTH = SCREEN_WIDTH
    const val VIEWPORT_HEIGHT = SCREEN_HEIGHT - STATUS_HEIGHT

    const val HOME_BUTTON_X = 64
    const val HOME_BUTTON_Y = 212
    const val HOME_BUTTON_SIZE = 12

    const val LED_X = 84
    const val LED_Y = 5
    const val LED_SIZE = 2

    /** Gap between the bottom-right device and the window's right and bottom edges (2.4). */
    const val CORNER_MARGIN = 6

    fun inScreen(dx: Int, dy: Int) = dx in SCREEN_X until SCREEN_X + SCREEN_WIDTH && dy in SCREEN_Y until SCREEN_Y + SCREEN_HEIGHT

    fun inViewport(dx: Double, dy: Double) =
        dx >= VIEWPORT_X && dx < VIEWPORT_X + VIEWPORT_WIDTH && dy >= VIEWPORT_Y && dy < VIEWPORT_Y + VIEWPORT_HEIGHT

    fun inHomeButton(dx: Int, dy: Int) =
        dx in HOME_BUTTON_X until HOME_BUTTON_X + HOME_BUTTON_SIZE && dy in HOME_BUTTON_Y until HOME_BUTTON_Y + HOME_BUTTON_SIZE

    fun inDevice(dx: Int, dy: Int) = dx in 0 until WIDTH && dy in 0 until HEIGHT
}

/** The shell palette (art direction 3), as ARGB. */
internal object ShellColors {
    const val SCREEN_RIM = 0xFF06080B.toInt()
    const val BODY_LO = 0xFF1D2129.toInt()
    const val STATUS_BG_HOME = 0x66000000
    const val STATUS_BG_APP = 0xFF12151B.toInt()
    const val STATUS_INK = 0xFFEEF1F5.toInt()
    const val ACCENT = 0xFF3A8DF0.toInt()
    const val BADGE_TEXT = 0xFFFFFFFF.toInt()
    const val SCRIM = 0x4D000000

    /** The label line and active page dot (6). */
    const val WHITE = 0xFFFFFFFF.toInt()
    const val PAGE_DOT_INACTIVE = 0x66FFFFFF

    /** App background (8.3 `bg`), the same as [STATUS_BG_APP] so status bar and app header make one band (8.2). */
    const val APP_BG = STATUS_BG_APP

    /** The GUI-scale message (8.1, `text-2`). */
    const val TEXT_2 = 0xFF96A0B0.toInt()
}

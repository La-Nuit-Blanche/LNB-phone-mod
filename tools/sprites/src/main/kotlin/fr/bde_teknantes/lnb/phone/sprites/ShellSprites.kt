package fr.bde_teknantes.lnb.phone.sprites

// Section 16.1: the pixel shell, 1 texel = 1 GUI px, drawn with nearest-neighbour. Every number below is the art
// direction's; the section that gives it is named next to it.

/** The folder of the pixel shell set, the vanilla GUI sprite atlas. */
const val SHELL_DIR = "sprites"

fun shellSprites(): List<Sprite> = listOf(
    Sprite("$SHELL_DIR/phone/frame", frame().toImage()),
    Sprite("$SHELL_DIR/phone/home_button", homeButton(Shell.homeRing, Shell.homeFill).toImage()),
    Sprite("$SHELL_DIR/phone/home_button_hover", homeButton(Shell.accent, Shell.homeFill).toImage()),
    Sprite("$SHELL_DIR/phone/home_button_pressed", homeButton(Shell.accent, Shell.bodyLo).toImage()),
    *(0..4).map { lit -> Sprite("$SHELL_DIR/status/signal_$lit", signal(lit).toImage()) }.toTypedArray(),
    Sprite("$SHELL_DIR/status/battery", battery().toImage()),
    Sprite("$SHELL_DIR/wallpaper/dusk", wallpaper().toImage()),
    Sprite("$SHELL_DIR/home/icon_frame", box(20, 20, TWO_STEP, TWO_STEP, BoxColours(Shell.outline, CLEAR)).toImage()),
    Sprite("$SHELL_DIR/home/icon_hover", box(24, 24, TWO_STEP, TWO_STEP, BoxColours(Shell.accent, CLEAR)).toImage()),
    Sprite("$SHELL_DIR/home/badge", badge(Shell.badge).toImage(), nineSliceMeta(9, 9, 4)),
    Sprite("$SHELL_DIR/home/badge_pulse", badge(Shell.badgeHi).toImage(), nineSliceMeta(9, 9, 4)),
    Sprite("$SHELL_DIR/inventory/button", inventoryButton(lit = false, hover = false).toImage()),
    Sprite("$SHELL_DIR/inventory/button_hover", inventoryButton(lit = false, hover = true).toImage()),
    Sprite("$SHELL_DIR/inventory/button_new", inventoryButton(lit = true, hover = false).toImage()),
    Sprite("$SHELL_DIR/inventory/button_new_hover", inventoryButton(lit = true, hover = true).toImage()),
) + Glyph.entries.map { Sprite("$SHELL_DIR/icon/${it.id}", it.draw().toImage()) }

/** The `.png.mcmeta` of a nine-slice GUI sprite (16.1), in the vanilla format. */
fun nineSliceMeta(width: Int, height: Int, border: Int): String =
    """{"gui": {"scaling": {"type": "nine_slice", "width": $width, "height": $height, "border": $border}}}""" + "\n"

// --- Corners (2.2) ---------------------------------------------------------------------------------------------------

/** Device corner, 3-step, top-left: O outline, H bevel, B body, `.` transparent. */
val THREE_STEP = rows(
    """
    . . . O O O
    . O O H H H
    . O H B B B
    O H B B B B
    O H B B B B
    O H B B B B
    """,
)

/** App icon, badge and hover ring corner, 2-step, top-left: F fill. */
val TWO_STEP = rows(
    """
    . . O O
    . O F F
    O F F F
    O F F F
    """,
)

/** The right-hand corners of the inventory tab, 1-step (11). */
val ONE_STEP = rows(
    """
    . O
    O H
    """,
)

/** [hi] lights the top and left edges, [lo] shades the bottom and right ones. */
data class BoxColours(val outline: Int, val body: Int, val hi: Int = body, val lo: Int = body)

/**
 * A beveled box: a 1 px outline, a 1 px bevel inside it, body elsewhere, and a stair at each corner. The corner grid
 * is given for the top-left and mirrored; the other corners follow 2.2: bottom-right uses the shade where top-left
 * uses the highlight, and top-right and bottom-left put body on the diagonal pixel where highlight meets shade.
 */
fun box(width: Int, height: Int, left: List<String>, right: List<String>, colours: BoxColours): PixelCanvas {
    val canvas = PixelCanvas(width, height)
    val (outline, body, hi, lo) = colours
    canvas.fill(body)
    canvas.fill(1, 1, width - 2, 1, hi)
    canvas.fill(1, 1, 1, height - 2, hi)
    canvas.fill(1, height - 2, width - 2, height - 2, lo)
    canvas.fill(width - 2, 1, width - 2, height - 2, lo)
    canvas.fill(0, 0, width - 1, 0, outline)
    canvas.fill(0, height - 1, width - 1, height - 1, outline)
    canvas.fill(0, 0, 0, height - 1, outline)
    canvas.fill(width - 1, 0, width - 1, height - 1, outline)
    for ((mirrorX, mirrorY) in listOf(false to false, true to false, false to true, true to true)) {
        val grid = if (mirrorX) right else left
        grid.map { row -> row.filterNot { it == ' ' } }.forEachIndexed { gy, row ->
            row.forEachIndexed { gx, char ->
                val x = if (mirrorX) width - 1 - gx else gx
                val y = if (mirrorY) height - 1 - gy else gy
                canvas[x, y] = when (char) {
                    '.' -> CLEAR
                    'O' -> outline
                    'B', 'F' -> body
                    // A bevel pixel above the diagonal belongs to the top (or bottom) edge, below it to the side.
                    'H' -> when {
                        gx > gy -> if (mirrorY) lo else hi
                        gx < gy -> if (mirrorX) lo else hi
                        mirrorX == mirrorY -> if (mirrorX) lo else hi
                        else -> body
                    }
                    else -> error("Unknown corner pixel $char")
                }
            }
        }
    }
    return canvas
}

// --- Device (2.1) ----------------------------------------------------------------------------------------------------

/** 140 x 228: outline, bevel, body, screen rim, speaker. The screen is transparent; LED and home areas are body. */
fun frame(): PixelCanvas {
    val canvas = box(140, 228, THREE_STEP, THREE_STEP, BoxColours(Shell.outline, Shell.body, Shell.bodyHi, Shell.bodyLo))
    canvas.fill(5, 11, 134, 208, Shell.screenRim)
    canvas.fill(6, 12, 133, 207, CLEAR)
    canvas.fill(61, 5, 78, 6, Shell.speaker)
    return canvas
}

/** 12 x 12 (2.3). Hover: ring `accent`. Pressed: ring `accent`, fill `body-lo`. `.` shows the body through. */
fun homeButton(ring: Int, fill: Int): PixelCanvas = PixelCanvas(12, 12).apply {
    grid(
        0, 0,
        rows(
            """
            . . . . R R R R . . . .
            . . R R F F F F R R . .
            . R F F F F F F F F R .
            . R F F F F F F F F R .
            R F F F F F F F F F F R
            R F F F F F F F F F F R
            R F F F F F F F F F F R
            R F F F F F F F F F F R
            . R F F F F F F F F R .
            . R F F F F F F F F R .
            . . R R F F F F R R . .
            . . . . R R R R . . . .
            """,
        ),
        mapOf('R' to ring, 'F' to fill),
    )
}

// --- Status bar (4) --------------------------------------------------------------------------------------------------

/** 11 x 7 (4.2): 4 bars 2 px wide, 1 px apart, heights 2, 3, 5, 7, bottom-aligned; the first [lit] are lit. */
fun signal(lit: Int): PixelCanvas = PixelCanvas(11, 7).apply {
    listOf(2, 3, 5, 7).forEachIndexed { bar, barHeight ->
        val x = bar * 3
        fill(x, 7 - barHeight, x + 1, 6, if (bar < lit) Shell.statusInk else Shell.statusDim)
    }
}

/** 13 x 7 (4.3), the frame only: the hunger fill is drawn by code in the `.` area at x 2..9, y 2..4. */
fun battery(): PixelCanvas = PixelCanvas(13, 7).apply {
    grid(
        0, 0,
        rows(
            """
            O O O O O O O O O O O O .
            O . . . . . . . . . . O .
            O . f f f f f f f f . O O
            O . f f f f f f f f . O O
            O . f f f f f f f f . O O
            O . . . . . . . . . . O .
            O O O O O O O O O O O O .
            """,
        ),
        mapOf('O' to Shell.statusInk),
    )
}

// --- Wallpaper (5) ---------------------------------------------------------------------------------------------------

private val STARS = listOf(
    2 to 24, 125 to 40, 1 to 88, 126 to 120, 2 to 146, 30 to 70, 64 to 100, 90 to 130, 50 to 150, 14 to 13, 99 to 14,
)
private val CRATERS = listOf(108 to 163, 109 to 163, 111 to 166, 108 to 167)
private val HILL_TOPS = listOf(174, 170, 168, 170, 176, 178, 172, 168, 166, 170, 174, 178, 180, 182, 180, 176)
private val DIRT_SPECKLES = listOf(1 to 2, 5 to 1, 3 to 5)

/** "Overworld dusk", 128 x 196, the whole screen. */
fun wallpaper(): PixelCanvas = PixelCanvas(128, 196).apply {
    fill(0, 0, 127, 39, Dusk.sky1)
    fill(0, 40, 127, 79, Dusk.sky2)
    fill(0, 80, 127, 119, Dusk.sky3)
    fill(0, 120, 127, 149, Dusk.sky4)
    fill(0, 150, 127, 195, Dusk.sky5)
    STARS.forEach { (x, y) -> this[x, y] = Dusk.star }
    fill(106, 161, 113, 168, Dusk.moon)
    CRATERS.forEach { (x, y) -> this[x, y] = Dusk.crater }
    HILL_TOPS.forEachIndexed { block, top ->
        fill(8 * block, top, 8 * block + 7, 185, Dusk.hill)
        fill(8 * block, top, 8 * block + 7, top, Dusk.hillEdge)
    }
    fill(0, 186, 127, 186, Dusk.grassTop)
    fill(0, 187, 127, 189, Dusk.grass)
    fill(0, 190, 127, 195, Dusk.dirt)
    // 8 x 8 cells from (0, 190); the dirt is 6 rows tall, so each cell's rows 0..5 show.
    for (cellX in 0 until 128 step 8) for ((dx, dy) in DIRT_SPECKLES) this[cellX + dx, 190 + dy] = Dusk.dirtSpeckle
}

// --- Home (6) --------------------------------------------------------------------------------------------------------

/**
 * 9 x 9 (6.1), outline `badge-lo`, 2-step corners; nine-slice with border 4 so the code can stretch it to 15 x 9 for
 * `9+`. The pulse frame swaps the fill for `badge-hi`.
 */
fun badge(fill: Int): PixelCanvas {
    val canvas = box(9, 9, TWO_STEP, TWO_STEP, BoxColours(Shell.badgeLo, fill))
    // The grid of 6.1, checked here so a change to the corner code can't silently move a pixel.
    val expected = PixelCanvas(9, 9).apply {
        grid(
            0, 0,
            rows(
                """
                . . O O O O O . .
                . O r r r r r O .
                O r r r r r r r O
                O r r r r r r r O
                O r r r r r r r O
                O r r r r r r r O
                O r r r r r r r O
                . O r r r r r O .
                . . O O O O O . .
                """,
            ),
            mapOf('O' to Shell.badgeLo, 'r' to fill),
        )
    }
    check(samePixels(canvas.toImage(), expected.toImage())) { "The badge no longer matches 6.1" }
    return canvas
}

// --- Inventory button (11) -------------------------------------------------------------------------------------------

/** 27 x 28, a miniature phone on a side tab: 3-step corners on the left, 1-step on the right. */
fun inventoryButton(lit: Boolean, hover: Boolean): PixelCanvas {
    val outline = if (hover) Shell.accent else Shell.outline
    val canvas = box(27, 28, THREE_STEP, ONE_STEP, BoxColours(outline, Shell.body, Shell.bodyHi, Shell.bodyLo))
    canvas.fill(6, 3, 20, 18, Shell.screenRim)
    if (lit) {
        canvas.fill(7, 4, 19, 17, Shell.accent)
        canvas.fill(7, 4, 19, 4, Shell.accentHi)
        for (x in listOf(9, 14)) for (y in listOf(7, 12)) canvas.fill(x, y, x + 2, y + 2, Shell.white)
    } else {
        canvas.fill(7, 4, 19, 17, Shell.screenOff)
        listOf(17 to 5, 18 to 5, 18 to 6).forEach { (x, y) -> canvas[x, y] = Shell.glare }
    }
    // Home ring: 5 x 5 at x 11..15, y 20..24 with its corners cut, centre 3 x 3.
    canvas.fill(12, 20, 14, 20, Shell.homeRing)
    canvas.fill(12, 24, 14, 24, Shell.homeRing)
    canvas.fill(11, 21, 11, 23, Shell.homeRing)
    canvas.fill(15, 21, 15, 23, Shell.homeRing)
    canvas.fill(12, 21, 14, 23, Shell.homeFill)
    return canvas
}

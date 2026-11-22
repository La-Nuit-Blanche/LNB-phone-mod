package fr.bde_teknantes.lnb.phone.sprites

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

// A contact sheet for checking the set by eye: the home screen assembled from the sprites the way the phone will
// draw it, then every sprite enlarged and labelled. Only for people; nothing ships from here.

private const val SHEET_WIDTH = 1500
private const val GAP = 24
private val SHEET_BG = Color(0x2A2E35)
private val LABEL = Color(0xD6DCE6)
private val APP_BG = Color(0x12151B)

fun contactSheet(shell: List<Sprite>, smooth: List<Sprite>, format: SmoothFormat): BufferedImage {
    val byPath = (shell + smooth).associateBy { it.path }
    fun image(path: String) = byPath.getValue(path).image

    val sections = mutableListOf<Pair<String, List<Entry>>>()
    sections += "Home screen assembled from the sprites, GUI scale 3 (clock and label line left out: they are text)" to
        listOf(Entry("home", scale(homePreview(::image), 3)), Entry("inventory, unread, with badge", scale(inventoryPreview(::image), 6)))
    sections += "Pixel shell set (16.1), enlarged with nearest-neighbour on a checkerboard" to shell.map { sprite ->
        val factor = when {
            sprite.image.width >= 100 -> 3
            sprite.image.width >= 20 -> 6
            else -> 10
        }
        Entry(sprite.path.removePrefix("sprites/") + "  x$factor", checker(scale(sprite.image, factor), factor))
    }
    val shadowPrefix = "$SMOOTH_DIR/${format.shadowDensity.folder}/"
    sections += "Shadows, ${format.shadowDensity.pxPerDp} px per dp, on a light grey so they show" to
        smooth.filter { it.path.startsWith(shadowPrefix) }.map { sprite ->
            Entry(sprite.path.removePrefix("$SMOOTH_DIR/"), onBackground(sprite.image, Color(0x8A93A3), 0xFFFFFF))
        }
    for (density in format.iconDensities) {
        val prefix = "$SMOOTH_DIR/${density.folder}/"
        val icons = smooth.filter { it.path.startsWith(prefix) }
        sections += "Icons ${density.folder} (${density.pxPerDp} px per dp), actual pixels, text-1 on bg" to icons.map {
            Entry(it.path.removePrefix(prefix), onBackground(it.image, APP_BG, 0xEEF1F5))
        }
    }
    // The low densities enlarged, where single pixels decide how an icon reads; above 2x they are plain to see.
    for (density in format.iconDensities.filter { it.pxPerDp <= 2.0 }) {
        val prefix = "$SMOOTH_DIR/${density.folder}/"
        // Every density enlarged to 6 screen px per dp, nearest-neighbour, so their pixels compare side by side.
        val factor = (6 / density.pxPerDp).toInt()
        sections += "Icons ${density.folder} enlarged x$factor, nearest-neighbour (every pixel visible)" to
            smooth.filter { it.path.startsWith(prefix) }.map {
                Entry(it.path.removePrefix(prefix), scale(onBackground(it.image, APP_BG, 0xEEF1F5, margin = 2), factor))
            }
    }

    // Lay out: rows of tiles, wrapped at the sheet width.
    val labelHeight = 18
    val titleHeight = 30
    data class Placed(val entry: Entry, val x: Int, val y: Int)
    val placed = mutableListOf<Placed>()
    val titles = mutableListOf<Pair<String, Int>>()
    var y = GAP
    for ((title, tiles) in sections) {
        titles += title to y
        y += titleHeight
        var x = GAP
        var rowHeight = 0
        for (tile in tiles) {
            // Labels are about 7 px a character at 12 px; leave room so they never run into the next tile.
            val width = maxOf(tile.image.width, tile.label.length * 7)
            if (x + width > SHEET_WIDTH - GAP && x > GAP) {
                x = GAP
                y += rowHeight + labelHeight + GAP
                rowHeight = 0
            }
            placed += Placed(tile, x, y)
            x += width + GAP
            rowHeight = maxOf(rowHeight, tile.image.height)
        }
        y += rowHeight + labelHeight + GAP * 2
    }

    val sheet = BufferedImage(SHEET_WIDTH, y, BufferedImage.TYPE_INT_ARGB)
    val g = sheet.createGraphics()
    g.color = SHEET_BG
    g.fillRect(0, 0, sheet.width, sheet.height)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = LABEL
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
    titles.forEach { (title, ty) -> g.drawString(title, GAP, ty + 18) }
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
    placed.forEach { (tile, px, py) ->
        g.drawImage(tile.image, px, py, null)
        g.color = LABEL
        g.drawString(tile.label, px, py + tile.image.height + 14)
    }
    g.dispose()
    return sheet
}

private class Entry(val label: String, val image: BufferedImage)

private fun scale(image: BufferedImage, factor: Int): BufferedImage {
    val out = BufferedImage(image.width * factor, image.height * factor, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, image.getRGB(x / factor, y / factor))
    return out
}

private fun checker(image: BufferedImage, cell: Int): BufferedImage {
    val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    for (y in 0 until out.height step cell) for (x in 0 until out.width step cell) {
        g.color = if ((x / cell + y / cell) % 2 == 0) Color(0x5A5F68) else Color(0x4A4E56)
        g.fillRect(x, y, cell, cell)
    }
    g.drawImage(image, 0, 0, null)
    g.dispose()
    return out
}

/** The sprite tinted like the game will (white times [tint]) over [background], with a margin. */
private fun onBackground(image: BufferedImage, background: Color, tint: Int, margin: Int = 8): BufferedImage {
    val out = BufferedImage(image.width + 2 * margin, image.height + 2 * margin, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.color = background
    g.fillRect(0, 0, out.width, out.height)
    g.drawImage(tinted(image, tint), margin, margin, null)
    g.dispose()
    return out
}

private fun tinted(image: BufferedImage, tint: Int): BufferedImage {
    val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until image.height) for (x in 0 until image.width) {
        val p = image.getRGB(x, y)
        fun channel(shift: Int) = ((p shr shift and 0xFF) * (tint shr shift and 0xFF) / 255) shl shift
        out.setRGB(x, y, (p and 0xFF000000.toInt()) or channel(16) or channel(8) or channel(0))
    }
    return out
}

private fun Graphics2D.draw(canvas: PixelCanvas, x: Int, y: Int) = drawImage(canvas.toImage(), x, y, null)

/** Section 7.1: tile, bands, frame, auto-shade, glyph. The phone does this in code; here only to preview. */
private fun appIcon(glyph: Glyph, frame: BufferedImage): PixelCanvas {
    val tile = glyph.tile
    val icon = PixelCanvas(20, 20)
    icon.fill(1, 1, 18, 18, tile.base)
    icon.fill(2, 1, 17, 1, tile.hi)
    icon.fill(2, 18, 17, 18, tile.lo)
    for (y in 0 until 20) for (x in 0 until 20) if (frame.getRGB(x, y) ushr 24 != 0) icon[x, y] = frame.getRGB(x, y)
    val g = glyph.draw()
    for (y in 0 until 16) for (x in 0 until 16) {
        val shadeSource = y > 0 && g[x, y - 1] ushr 24 != 0
        if (shadeSource && g[x, y] ushr 24 == 0) icon[x + 2, y + 2] = tile.lo
    }
    // The shade of the bottom row falls on y 18 of the tile, outside the glyph's own 16 rows.
    for (x in 0 until 16) if (g[x, 15] ushr 24 != 0) icon[x + 2, 18] = tile.lo
    for (y in 0 until 16) for (x in 0 until 16) if (g[x, y] ushr 24 != 0) icon[x + 2, y + 2] = g[x, y]
    return icon
}

/** The `3` of the Minecraft font, as the mockup drew it in the badge (badge-local, so it lands at (2, 1)). */
private val BADGE_THREE = rows(
    """
    .........
    ...333...
    ..3...3..
    ......3..
    ....33...
    ......3..
    ..3...3..
    ...333...
    """,
)

private fun homePreview(image: (String) -> BufferedImage): BufferedImage {
    val device = BufferedImage(140, 228, BufferedImage.TYPE_INT_ARGB)
    val g = device.createGraphics()
    val sx = 6
    val sy = 12
    g.drawImage(image("sprites/wallpaper/dusk"), sx, sy, null)
    g.color = Color(0, 0, 0, 0x66)
    g.fillRect(sx, sy, 128, 11)
    g.drawImage(image("sprites/status/signal_3"), sx + 4, sy + 2, null)
    g.drawImage(image("sprites/status/battery"), sx + 111, sy + 2, null)
    g.color = Color(0x58C83C)
    g.fillRect(sx + 111 + 2, sy + 2 + 2, 8, 3)
    val frame = image("sprites/home/icon_frame")
    Glyph.entries.forEachIndexed { column, glyph ->
        g.draw(appIcon(glyph, frame), sx + 6 + 24 * column, sy + 11 + 8)
    }
    // Settings hovered, Feed with a badge of 3 (sheet 1 of the mockups).
    val settings = Glyph.entries.size - 1
    g.drawImage(image("sprites/home/icon_hover"), sx + 6 + 24 * settings - 2, sy + 11 + 8 - 2, null)
    val badge = PixelCanvas(9, 9).apply {
        val source = image("sprites/home/badge")
        for (y in 0 until 9) for (x in 0 until 9) this[x, y] = source.getRGB(x, y)
        grid(0, 0, BADGE_THREE, mapOf('3' to Shell.white))
    }
    g.draw(badge, sx + 6 + 14, sy + 11 + 8 - 3)
    g.drawImage(image("sprites/phone/frame"), 0, 0, null)
    g.color = Color(Shell.screenRim)
    for ((cx, cy) in listOf(6 to 12, 133 to 12, 6 to 207, 133 to 207)) g.fillRect(cx, cy, 1, 1)
    g.color = Color(Shell.accent)
    g.fillRect(84, 5, 2, 2)
    g.drawImage(image("sprites/phone/home_button"), 64, 212, null)
    g.dispose()
    return device
}

private fun inventoryPreview(image: (String) -> BufferedImage): BufferedImage {
    val out = BufferedImage(30, 31, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(image("sprites/inventory/button_new"), 3, 3, null)
    val badge = PixelCanvas(9, 9).apply {
        val source = image("sprites/home/badge")
        for (y in 0 until 9) for (x in 0 until 9) this[x, y] = source.getRGB(x, y)
        grid(0, 0, BADGE_THREE, mapOf('3' to Shell.white))
    }
    g.draw(badge, 0, 0)
    g.dispose()
    return out
}

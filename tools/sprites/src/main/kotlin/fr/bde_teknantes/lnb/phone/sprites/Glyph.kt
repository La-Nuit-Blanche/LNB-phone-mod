package fr.bde_teknantes.lnb.phone.sprites

/**
 * The example app glyphs of 7.4, 16 x 16, pasted from the art direction. `D` is the tile's `lo`, so each glyph
 * names its tile; the phone draws tile, frame and auto-shade around it.
 */
enum class Glyph(val id: String, val tile: Tile, grid: String) {
    FEED(
        "feed", Tile.AZURE,
        """
        ................
        ................
        ..........WWW...
        .........WWWWW..
        .........WWKWWOO
        .........WWWWWO.
        W.......WWWWWW..
        WW.....WWWWWWW..
        .WWW.WWWWWWWWW..
        ..WWWWWWWWWWWW..
        ...WWWWWLLLWWW..
        ....WWWLLLWWW...
        .....WWWWWWW....
        .......O.O......
        ................
        ................
        """,
    ),
    MESSAGES(
        "messages", Tile.GREEN,
        """
        ................
        ................
        ..WWWWWWWWWWWW..
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        .WWWDDWDDWDDWWW.
        .WWWDDWDDWDDWWW.
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        ..WWWWWWWWWWWW..
        ..WWW...........
        ..WW............
        ..W.............
        ................
        ................
        """,
    ),
    WALLET(
        "wallet", Tile.VIOLET,
        """
        ................
        ................
        ................
        ....LLLLLLLLLL..
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWKKKKKK
        .WWWWWWWWWKKYYKK
        .WWWWWWWWWKKYYKK
        .WWWWWWWWWKKKKKK
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        .WWWWWWWWWWWWWW.
        ................
        ................
        """,
    ),
    QUESTS(
        "quests", Tile.ORANGE,
        """
        ................
        ......KKKK......
        ..WWWKKKKKKWWW..
        ..WWWWWWWWWWWW..
        ..WWWWWWWWWWWW..
        ..WWGGWKKKKKWW..
        ..WWGGWWWWWWWW..
        ..WWWWWWWWWWWW..
        ..WWGGWKKKKKWW..
        ..WWGGWWWWWWWW..
        ..WWWWWWWWWWWW..
        ..WWDDWKKKWWWW..
        ..WWDDWWWWWWWW..
        ..WWWWWWWWWWWW..
        ................
        ................
        """,
    ),
    SETTINGS(
        "settings", Tile.GRAPHITE,
        """
        ................
        ......WWWW......
        ..WW..WWWW..WW..
        ..WWWWWWWWWWWW..
        ...WWWWWWWWWW...
        ...WWWWWWWWWW...
        .WWWWW....WWWWW.
        .WWWWW....WWWWW.
        .WWWWW....WWWWW.
        .WWWWW....WWWWW.
        ...WWWWWWWWWW...
        ...WWWWWWWWWW...
        ..WWWWWWWWWWWW..
        ..WW..WWWW..WW..
        ......WWWW......
        ................
        """,
    ),
    ;

    val pixels: List<String> = rows(grid)

    fun draw(): PixelCanvas {
        require(pixels.size == 16 && pixels.all { it.length == 16 }) { "Glyph $id is not 16 x 16" }
        return PixelCanvas(16, 16).apply {
            grid(0, 0, pixels, mapOf('W' to Ink.W, 'L' to Ink.L, 'K' to Ink.K, 'D' to tile.lo) + ACCENTS)
        }
    }

    private companion object {
        val ACCENTS = mapOf('O' to Ink.O, 'Y' to Ink.Y, 'G' to Ink.G, 'R' to Ink.R, 'B' to Ink.B)
    }
}

/**
 * Feed's own home icon, `assets/lnbphone_feed/textures/gui/feed.png`: the same glyph as the phone's `icon/feed`
 * sample (7.4), drawn into Feed's folder so that the app doesn't depend on a sprite that belongs to the phone's
 * internals. Written by `--feed-out`; its path is relative to that folder.
 */
fun feedSprites(): List<Sprite> = listOf(Sprite("feed", Glyph.FEED.draw().toImage()))

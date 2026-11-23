package fr.bde_teknantes.lnb.phone.sprites

// Every colour the sprites use, named as in design/art-direction.md. Colours are ARGB ints.

const val CLEAR = 0x00000000

/** `#RRGGBB` (opaque) or `#RRGGBBAA`, the notation of the art direction, to ARGB. */
fun rgb(hex: String): Int {
    val digits = hex.removePrefix("#")
    require(digits.length == 6 || digits.length == 8) { "Bad colour $hex" }
    val rgb = digits.substring(0, 6).toInt(16)
    val alpha = if (digits.length == 8) digits.substring(6, 8).toInt(16) else 0xFF
    return (alpha shl 24) or rgb
}

/** Section 3, the shell palette (Slate). */
object Shell {
    val outline = rgb("#0A0C10")
    val body = rgb("#2C323D")
    val bodyHi = rgb("#434B5A")
    val bodyLo = rgb("#1D2129")
    val screenRim = rgb("#06080B")
    val speaker = rgb("#0A0C10")
    val homeRing = rgb("#4F586A")
    val homeFill = rgb("#23282F")
    val statusInk = rgb("#EEF1F5")
    val statusDim = rgb("#4F586A")
    val accent = rgb("#3A8DF0")
    val accentHi = rgb("#74B4FF")
    val badge = rgb("#E5483C")
    val badgeHi = rgb("#FF7A6E")
    val badgeLo = rgb("#7A1A13")
    val screenOff = rgb("#101317")
    val glare = rgb("#3A404B")
    val white = rgb("#FFFFFF")
}

/** Section 5, the "Overworld dusk" wallpaper. */
object Dusk {
    val sky1 = rgb("#141B30")
    val sky2 = rgb("#1A2440")
    val sky3 = rgb("#222F52")
    val sky4 = rgb("#2D3D66")
    val sky5 = rgb("#3D4F7C")
    val star = rgb("#C9D4F0")
    val moon = rgb("#E8ECF5")
    val crater = rgb("#B8C2D8")
    val hill = rgb("#1C2438")
    val hillEdge = rgb("#26304A")
    val grassTop = rgb("#3D7236")
    val grass = rgb("#2E5A2A")
    val dirt = rgb("#3A2A1E")
    val dirtSpeckle = rgb("#2C1F16")
}

/** Section 7.3, the tile palette app authors choose from. */
enum class Tile(hi: String, base: String, lo: String) {
    AZURE("#7CC0FF", "#3A8DF0", "#1F5FB0"),
    TEAL("#7FE3E0", "#1FA3A0", "#12706E"),
    GREEN("#9BEA7A", "#4DB33C", "#2D7A22"),
    ORANGE("#FFC48A", "#E57A24", "#A65116"),
    RED("#FF8F86", "#DB4237", "#9A2720"),
    PINK("#FFB0DA", "#E0609F", "#A03A70"),
    VIOLET("#C7A8FF", "#8457E6", "#56339E"),
    GRAPHITE("#5C6372", "#3A404B", "#23272E");

    val hi = rgb(hi)
    val base = rgb(base)
    val lo = rgb(lo)
}

/** Section 7.2, the glyph inks. `D` (tile lo) depends on the tile, so it is not here. */
object Ink {
    val W = rgb("#FFFFFF")
    val L = rgb("#D6DCE6")
    val K = rgb("#1B1E23")
    val O = rgb("#F28A2E")
    val Y = rgb("#F2B33A")
    val G = rgb("#5BD34A")
    val R = rgb("#E5483C")
    val B = rgb("#3A8DF0")
}

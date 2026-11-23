package fr.bde_teknantes.lnb.phone.sprites

import java.awt.geom.Path2D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Parses SVG path data (`M L H V C S A Z`, absolute and relative) into a Java2D path, so the line icons can be written
 * as the mockups wrote them. Elliptical arcs are converted to cubic Béziers; arc rotation is not supported (no icon
 * uses it).
 */
fun svgPath(data: String): Path2D.Double {
    val path = Path2D.Double()
    val tokens = tokenize(data)
    var i = 0
    var command = ' '
    var x = 0.0
    var y = 0.0
    var startX = 0.0
    var startY = 0.0
    // The second control point of the last cubic, for S.
    var lastControlX = 0.0
    var lastControlY = 0.0
    var lastWasCubic = false

    fun number(): Double = (tokens[i++] as Token.Number).value
    fun hasNumber() = i < tokens.size && tokens[i] is Token.Number

    while (i < tokens.size) {
        val token = tokens[i]
        if (token is Token.Command) {
            command = token.char
            i++
        } else if (command == ' ') {
            error("Path data starts with a number: $data")
        }
        val relative = command.isLowerCase()
        val ox = if (relative) x else 0.0
        val oy = if (relative) y else 0.0
        var cubic = false
        when (command.uppercaseChar()) {
            'M' -> {
                x = ox + number(); y = oy + number()
                path.moveTo(x, y)
                startX = x; startY = y
                // Further pairs after a moveto are linetos.
                command = if (relative) 'l' else 'L'
            }
            'L' -> {
                x = ox + number(); y = oy + number()
                path.lineTo(x, y)
            }
            'H' -> {
                x = ox + number()
                path.lineTo(x, y)
            }
            'V' -> {
                y = oy + number()
                path.lineTo(x, y)
            }
            'C' -> {
                val x1 = ox + number(); val y1 = oy + number()
                val x2 = ox + number(); val y2 = oy + number()
                x = ox + number(); y = oy + number()
                path.curveTo(x1, y1, x2, y2, x, y)
                lastControlX = x2; lastControlY = y2; cubic = true
            }
            'S' -> {
                val x1 = if (lastWasCubic) 2 * x - lastControlX else x
                val y1 = if (lastWasCubic) 2 * y - lastControlY else y
                val x2 = ox + number(); val y2 = oy + number()
                x = ox + number(); y = oy + number()
                path.curveTo(x1, y1, x2, y2, x, y)
                lastControlX = x2; lastControlY = y2; cubic = true
            }
            'A' -> {
                val rx = number(); val ry = number()
                val rotation = number()
                require(rotation == 0.0) { "Rotated arcs are not supported: $data" }
                val large = number() != 0.0
                val sweep = number() != 0.0
                val endX = ox + number(); val endY = oy + number()
                arc(path, x, y, rx, ry, large, sweep, endX, endY)
                x = endX; y = endY
            }
            'Z' -> {
                path.closePath()
                x = startX; y = startY
            }
            else -> error("Unsupported path command $command in $data")
        }
        lastWasCubic = cubic
        // Any command but Z repeats while numbers follow it ("h3v1h-3z", "l1 2 3 4").
        require(command.uppercaseChar() != 'Z' || !hasNumber()) { "Numbers after Z in $data" }
    }
    return path
}

private sealed interface Token {
    data class Command(val char: Char) : Token
    data class Number(val value: Double) : Token
}

private val NUMBER = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

private fun tokenize(data: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < data.length) {
        val char = data[i]
        when {
            char.isWhitespace() || char == ',' -> i++
            char.isLetter() -> {
                tokens += Token.Command(char)
                i++
            }
            else -> {
                // "1.5.5" is two numbers and "1-2" too: the regex stops where a number must end.
                val match = NUMBER.matchAt(data, i) ?: error("Bad path data at $i: $data")
                tokens += Token.Number(match.value.toDouble())
                i = match.range.last + 1
            }
        }
    }
    return tokens
}

/** Appends an SVG endpoint arc (no rotation) as cubic Béziers of at most 90 degrees each (SVG 1.1, F.6.5). */
private fun arc(
    path: Path2D.Double, x1: Double, y1: Double, rxIn: Double, ryIn: Double,
    large: Boolean, sweep: Boolean, x2: Double, y2: Double,
) {
    if (x1 == x2 && y1 == y2) return
    var rx = abs(rxIn)
    var ry = abs(ryIn)
    if (rx == 0.0 || ry == 0.0) {
        path.lineTo(x2, y2)
        return
    }
    val dx = (x1 - x2) / 2
    val dy = (y1 - y2) / 2
    // Radii too small to reach the end point are scaled up, as SVG requires.
    val lambda = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry)
    if (lambda > 1) {
        rx *= sqrt(lambda)
        ry *= sqrt(lambda)
    }
    val numerator = rx * rx * ry * ry - rx * rx * dy * dy - ry * ry * dx * dx
    val denominator = rx * rx * dy * dy + ry * ry * dx * dx
    var factor = sqrt(maxOf(0.0, numerator / denominator))
    if (large == sweep) factor = -factor
    val cxp = factor * rx * dy / ry
    val cyp = -factor * ry * dx / rx
    val cx = cxp + (x1 + x2) / 2
    val cy = cyp + (y1 + y2) / 2
    val theta1 = angle(1.0, 0.0, (dx - cxp) / rx, (dy - cyp) / ry)
    var delta = angle((dx - cxp) / rx, (dy - cyp) / ry, (-dx - cxp) / rx, (-dy - cyp) / ry)
    if (!sweep && delta > 0) delta -= 2 * PI
    if (sweep && delta < 0) delta += 2 * PI

    val segments = ceil(abs(delta) / (PI / 2) - 1e-9).toInt().coerceAtLeast(1)
    val step = delta / segments
    val k = 4.0 / 3.0 * StrictMath.tan(step / 4)
    var a = theta1
    repeat(segments) {
        val b = a + step
        val cosA = StrictMath.cos(a)
        val sinA = StrictMath.sin(a)
        val cosB = StrictMath.cos(b)
        val sinB = StrictMath.sin(b)
        path.curveTo(
            cx + rx * (cosA - k * sinA), cy + ry * (sinA + k * cosA),
            cx + rx * (cosB + k * sinB), cy + ry * (sinB - k * cosB),
            cx + rx * cosB, cy + ry * sinB,
        )
        a = b
    }
}

private fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
    val sign = if (ux * vy - uy * vx < 0) -1 else 1
    val cos = ((ux * vx + uy * vy) / (sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy))).coerceIn(-1.0, 1.0)
    return sign * StrictMath.acos(cos)
}

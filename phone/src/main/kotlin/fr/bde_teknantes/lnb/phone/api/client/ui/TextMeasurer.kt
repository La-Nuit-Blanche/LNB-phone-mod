package fr.bde_teknantes.lnb.phone.api.client.ui

import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import fr.bde_teknantes.lnb.phone.client.smooth.FontWeight
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.client.smooth.TextLayout

/**
 * Measures text in dp, the same way it will be drawn. Text measures the same at every GUI scale, so a layout computed
 * once holds everywhere.
 *
 * The [AppCanvas][fr.bde_teknantes.lnb.phone.api.client.AppCanvas] you draw on is one; outside drawing (to lay out a
 * list when data arrives, to hit-test a click) use [TextMetrics]. Code that lays text out can take a [TextMeasurer]
 * so that a test can hand it a fake one.
 */
interface TextMeasurer {
    /** Width of [text] on one line in [style], in dp. */
    fun measure(text: String, style: TextStyle): Float

    /** [text] if it fits in [maxWidth] dp, otherwise its longest start that fits, followed by an ellipsis (…). */
    fun truncate(text: String, style: TextStyle, maxWidth: Float): String

    /**
     * [text] broken into lines no wider than [maxWidth] dp: at spaces and line feeds, and inside a word only when the
     * word alone is wider than a line. French spaces before `!?:;` never break. An empty text is one empty line.
     */
    fun wrap(text: String, style: TextStyle, maxWidth: Float): List<String>

    /**
     * [wrap], but at most [maxLines] lines: when text is left out, the last line shown ends with an ellipsis. For
     * titles and texts that have a fixed number of lines (a dialog's title: 2).
     */
    fun wrap(text: String, style: TextStyle, maxWidth: Float, maxLines: Int): List<String>
}

/**
 * Measures text with the phone's fonts. Use it on the client thread, once the game has loaded its resources (any time
 * your screens run). Results are cached, so measuring the same text every frame costs next to nothing.
 */
object TextMetrics : TextMeasurer {
    private const val CACHE_SIZE = 2048

    // The font is part of the key: a resource reload brings new fonts, and the old entries simply age out.
    private data class Key(val kind: Int, val text: String, val font: FontMetrics, val size: Float, val width: Float, val lines: Int)

    /** Where the fonts come from: the phone's Inter faces, or fake metrics in tests. */
    @Volatile
    internal var fonts: (FontWeight) -> FontMetrics = { SmoothResources.font(it) }

    private val cache = object : LinkedHashMap<Key, Any>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Any>?) = size > CACHE_SIZE
    }

    override fun measure(text: String, style: TextStyle): Float {
        val font = font(style)
        return cache.getOrPut(Key(0, text, font, style.size, 0f, 0)) { TextLayout.width(text, font, style.size) } as Float
    }

    override fun truncate(text: String, style: TextStyle, maxWidth: Float): String {
        val font = font(style)
        return cache.getOrPut(Key(1, text, font, style.size, maxWidth, 0)) { TextLayout.truncate(text, font, style.size, maxWidth) } as String
    }

    override fun wrap(text: String, style: TextStyle, maxWidth: Float): List<String> = wrap(text, style, maxWidth, Int.MAX_VALUE)

    @Suppress("UNCHECKED_CAST")
    override fun wrap(text: String, style: TextStyle, maxWidth: Float, maxLines: Int): List<String> {
        val font = font(style)
        return cache.getOrPut(Key(2, text, font, style.size, maxWidth, maxLines)) {
            TextLayout.wrap(text, font, style.size, maxWidth, maxLines)
        } as List<String>
    }

    /** Lines with their source offsets, for text being edited. */
    @Suppress("UNCHECKED_CAST")
    internal fun lines(text: String, style: TextStyle, maxWidth: Float): List<TextLayout.Line> {
        val font = font(style)
        return cache.getOrPut(Key(3, text, font, style.size, maxWidth, 0)) { TextLayout.lines(text, font, style.size, maxWidth) } as List<TextLayout.Line>
    }

    internal fun font(style: TextStyle): FontMetrics = fonts(if (style.semibold) FontWeight.SEMIBOLD else FontWeight.REGULAR)
}

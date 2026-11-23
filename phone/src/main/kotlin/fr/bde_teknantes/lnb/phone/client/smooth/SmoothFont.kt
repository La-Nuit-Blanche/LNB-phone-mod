package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.platform.TextureUtil
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_MM_Var
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * One font face (an Inter weight, or the emoji font), opened with the FreeType library Minecraft already ships and
 * initialises (the same one its own TTF provider uses), rasterised by us at the exact pixel size each GUI scale needs.
 *
 * Metrics come from the font's design units, unhinted, so layout is linear in the size; kerning comes from the GPOS
 * table ([GposKerning]), ligatures from the GSUB table ([GsubLigatures], read only when asked for). A variable font
 * with a weight axis can be rasterised at any weight; its metrics are always those of its default instance. Used on
 * the render thread only.
 */
class SmoothFont private constructor(private var memory: ByteBuffer?, private var face: FT_Face?) : FontMetrics, AutoCloseable {
    private val unitsPerEm: Float
    override val ascender: Float
    override val descender: Float
    private val gpos: GposKerning

    private val glyphIds = Int2IntOpenHashMap().apply { defaultReturnValue(-1) }
    private val advances = Int2FloatOpenHashMap().apply { defaultReturnValue(Float.NaN) }
    private val glyphAdvances = Int2FloatOpenHashMap().apply { defaultReturnValue(Float.NaN) }
    private val kernings = Long2FloatOpenHashMap().apply { defaultReturnValue(Float.NaN) }

    /** The weight axis of a variable font: its index among the axes, their defaults, and its range. */
    private class WeightAxis(val index: Int, val defaults: LongArray, val min: Float, val max: Float)

    private val weightAxis: WeightAxis?

    /** The weight the face is set to now; NaN for its default instance. */
    private var appliedWeight = Float.NaN

    /** Ligatures from the GSUB table, read the first time they are asked for. */
    val ligatures: GsubLigatures by lazy { GsubLigatures(memory ?: throw IllegalStateException("Font already closed")) }

    init {
        val f = face!!
        unitsPerEm = (f.units_per_EM().toInt() and 0xFFFF).toFloat()
        require(unitsPerEm > 0f) { "Font has no units per em (a bitmap-only font?)" }
        ascender = f.ascender() / unitsPerEm
        descender = -f.descender() / unitsPerEm
        gpos = GposKerning(memory!!)
        weightAxis = readWeightAxis(f)
    }

    /** Whether [rasterizeGlyph] can draw this face at other weights than its default. */
    val hasWeightAxis: Boolean get() = weightAxis != null

    /** How a glyph is fitted to the pixel grid when rasterised. */
    enum class Hinting(internal val loadFlags: Int) {
        /** Outlines as designed: soft at small sizes, but glyphs keep their exact shape and spacing. */
        NONE(FreeType.FT_LOAD_NO_HINTING),

        /** FreeType's light mode: snaps heights (baseline, x-height, horizontal strokes) only, never widths. */
        LIGHT(1 shl 16),

        /** The font's own TrueType instructions, as Minecraft's TTF provider uses them. */
        NATIVE(FreeType.FT_LOAD_DEFAULT),
    }

    /** A rasterised glyph: coverage rows and the bitmap's offset from the pen position on the baseline. */
    class Bitmap(val width: Int, val height: Int, val left: Int, val top: Int, val coverage: ByteArray)

    override fun hasGlyph(codePoint: Int): Boolean = glyphId(codePoint) != 0

    fun glyphId(codePoint: Int): Int {
        val cached = glyphIds.get(codePoint)
        if (cached >= 0) return cached
        val id = FreeType.FT_Get_Char_Index(face(), codePoint.toLong())
        glyphIds.put(codePoint, id)
        return id
    }

    override fun advance(codePoint: Int): Float {
        val cached = advances.get(codePoint)
        if (!cached.isNaN()) return cached
        return glyphAdvance(glyphId(codePoint)).also { advances.put(codePoint, it) }
    }

    /** How far the pen moves after glyph [glyphId], in em, at the default instance (the same at every weight). */
    fun glyphAdvance(glyphId: Int): Float {
        val cached = glyphAdvances.get(glyphId)
        if (!cached.isNaN()) return cached
        // A variable font set to another weight would answer for that weight: metrics stay the default's, so a text
        // measures the same whatever weight it is drawn at.
        applyWeight(Float.NaN)
        val units = MemoryStack.stackPush().use { stack ->
            val out = stack.callocCLong(1)
            val failed = FreeTypeUtil.checkError(FreeType.FT_Get_Advance(face(), glyphId, FreeType.FT_LOAD_NO_SCALE, out), "Glyph advance")
            if (failed) 0L else out.get(0)
        }
        return (units / unitsPerEm).also { glyphAdvances.put(glyphId, it) }
    }

    override fun kerning(left: Int, right: Int): Float {
        val key = (left.toLong() shl 32) or (right.toLong() and 0xFFFFFFFFL)
        val cached = kernings.get(key)
        if (!cached.isNaN()) return cached
        return (gpos.kerning(glyphId(left), glyphId(right)) / unitsPerEm).also { kernings.put(key, it) }
    }

    /** [rasterizeGlyph] for the glyph of [codePoint]. */
    fun rasterize(codePoint: Int, sizePx: Float, shiftX: Float, hinting: Hinting): Bitmap? =
        rasterizeGlyph(glyphId(codePoint), sizePx, shiftX, hinting)

    /**
     * Rasterises glyph [glyphId] at [sizePx] pixels per em, shifted right by [shiftX] pixels (0 to 1, for sub-pixel
     * positioning), at [weight] on a variable font's weight axis (clamped to its range; NaN, or a font without the
     * axis, draws the default instance). Null when the glyph is empty (a space) or cannot be drawn as an 8-bit
     * coverage mask.
     */
    fun rasterizeGlyph(glyphId: Int, sizePx: Float, shiftX: Float, hinting: Hinting, weight: Float = Float.NaN): Bitmap? {
        val f = face()
        applyWeight(weight)
        FreeTypeUtil.checkError(FreeType.FT_Set_Char_Size(f, 0, (sizePx * 64f).roundToLong(), 72, 72), "Glyph size")
        MemoryStack.stackPush().use { stack ->
            val delta = FT_Vector.malloc(stack).set((shiftX * 64f).roundToLong(), 0)
            FreeType.FT_Set_Transform(f, null, delta)
        }
        // Outlines only, as Minecraft's own provider loads them: a font with embedded bitmaps (monochrome, or colour)
        // would otherwise hand back pixels in another format.
        val flags = FT_LOAD_NO_BITMAP or hinting.loadFlags
        if (FreeTypeUtil.checkError(FreeType.FT_Load_Glyph(f, glyphId, flags), "Glyph load")) return null
        val slot = f.glyph() ?: return null
        // Measured before it is rendered: a replaced font can have a glyph thousands of pixels wide at body size (a
        // tiny units-per-em, a huge outline), and rendering allocates its bitmap, twice, before the atlas could refuse.
        val limit = maxGlyphPx(sizePx) * 64L
        val metrics = slot.metrics()
        if (metrics.width() > limit || metrics.height() > limit || metrics.width() < 0 || metrics.height() < 0) return null
        if (FreeTypeUtil.checkError(FreeType.FT_Render_Glyph(slot, (hinting.loadFlags ushr 16) and 0xF), "Glyph render")) return null
        val bitmap = slot.bitmap()
        val width = bitmap.width()
        val height = bitmap.rows()
        if (width <= 0 || height <= 0 || width > maxGlyphPx(sizePx) + 2 || height > maxGlyphPx(sizePx) + 2) return null
        val pitch = bitmap.pitch()
        if (bitmap.pixel_mode().toInt() != FT_PIXEL_MODE_GRAY || abs(pitch) < width) return null
        val source = bitmap.buffer(abs(pitch) * height) ?: return null
        val coverage = ByteArray(width * height)
        for (row in 0 until height) {
            // A negative pitch means rows are stored bottom-up.
            val from = if (pitch >= 0) row * pitch else (height - 1 - row) * -pitch
            for (col in 0 until width) coverage[row * width + col] = source.get(from + col)
        }
        return Bitmap(width, height, slot.bitmap_left(), slot.bitmap_top(), coverage)
    }

    private fun face(): FT_Face = face ?: throw IllegalStateException("Font already closed")

    /** Sets a variable font to [weight] (NaN: its default) when it is not there already. */
    private fun applyWeight(weight: Float) {
        val axis = weightAxis ?: return
        // The default weight is the default instance however it is asked for: no coordinates to set back and forth
        // between drawing Regular text (400 for Noto Emoji) and reading metrics.
        val clamped = if (weight.isNaN()) Float.NaN else weight.coerceIn(axis.min, axis.max)
        val target = if (clamped * 65536f == axis.defaults[axis.index].toFloat()) Float.NaN else clamped
        if (target == appliedWeight || (target.isNaN() && appliedWeight.isNaN())) return
        MemoryStack.stackPush().use { stack ->
            val coords = stack.callocCLong(axis.defaults.size)
            for (i in axis.defaults.indices) coords.put(i, axis.defaults[i])
            if (!target.isNaN()) coords.put(axis.index, (target * 65536f).roundToLong())
            // A failure leaves the face as it was: drawn at its default weight, still the right glyph.
            if (FreeTypeUtil.checkError(FreeType.FT_Set_Var_Design_Coordinates(face(), coords), "Font weight")) return
        }
        appliedWeight = target
    }

    override fun close() {
        face?.let { f ->
            synchronized(FreeTypeUtil.LIBRARY_LOCK) { FreeTypeUtil.checkError(FreeType.FT_Done_Face(f), "Closing face") }
        }
        face = null
        memory?.let(MemoryUtil::memFree)
        memory = null
    }

    companion object {
        private const val FT_LOAD_NO_BITMAP = 1 shl 3
        private const val FT_PIXEL_MODE_GRAY = 2
        private const val WGHT = 0x77676874L // 'wght'

        /**
         * The widest or tallest glyph rasterised at [sizePx] per em, in pixels: four em, far beyond any letter or
         * emoji (an emoji is 1.27 em wide), and a few pixels for the smallest sizes.
         */
        internal fun maxGlyphPx(sizePx: Float): Int = (4f * sizePx).toInt() + 16

        /** The weight axis of a variable font, or null for a static font or one FreeType can't vary. */
        private fun readWeightAxis(face: FT_Face): WeightAxis? {
            if (!FreeType.FT_HAS_MULTIPLE_MASTERS(face)) return null
            return MemoryStack.stackPush().use { stack ->
                val out = stack.callocPointer(1)
                if (FreeTypeUtil.checkError(FreeType.FT_Get_MM_Var(face, out), "Font axes")) return null
                val mm = FT_MM_Var.create(out.get(0))
                try {
                    val count = mm.num_axis()
                    // A handful of axes in a real font; anything else is not worth trusting.
                    if (count !in 1..16) return null
                    val axes = mm.axis()
                    val defaults = LongArray(count) { axes.get(it).def() }
                    val index = (0 until count).firstOrNull { axes.get(it).tag() == WGHT } ?: return null
                    val min = axes.get(index).minimum() / 65536f
                    val max = axes.get(index).maximum() / 65536f
                    if (min <= max) WeightAxis(index, defaults, min, max) else null
                } finally {
                    FreeType.FT_Done_MM_Var(FreeTypeUtil.getLibrary(), mm)
                }
            }
        }

        /** Opens the font file [stream] gives (closing it); [name] is for error messages. */
        fun load(stream: InputStream, name: String): SmoothFont =
            fromMemory(stream.use { TextureUtil.readResource(it) }.also { it.flip() }, name)

        /**
         * Opens a font from [memory] (a buffer from `MemoryUtil.memAlloc`, which the font then owns and frees). On
         * any failure the buffer and the FreeType face are freed before the error goes up.
         */
        fun fromMemory(memory: ByteBuffer, name: String): SmoothFont {
            var face: FT_Face? = null
            try {
                face = synchronized(FreeTypeUtil.LIBRARY_LOCK) {
                    MemoryStack.stackPush().use { stack ->
                        val out = stack.callocPointer(1)
                        FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0L, out), "Opening $name")
                        FT_Face.create(out.get(0))
                    }
                }
                return SmoothFont(memory, face)
            } catch (e: Throwable) {
                face?.let { synchronized(FreeTypeUtil.LIBRARY_LOCK) { FreeType.FT_Done_Face(it) } }
                MemoryUtil.memFree(memory)
                throw e
            }
        }
    }
}

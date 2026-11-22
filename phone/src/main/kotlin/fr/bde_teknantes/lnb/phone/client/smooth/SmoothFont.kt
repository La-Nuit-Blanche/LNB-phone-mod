package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.platform.TextureUtil
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap
import net.minecraft.client.gui.font.providers.FreeTypeUtil
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.freetype.FT_Face
import org.lwjgl.util.freetype.FT_Vector
import org.lwjgl.util.freetype.FreeType
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * One Inter face, opened with the FreeType library Minecraft already ships and initialises (the same one its own TTF
 * provider uses), rasterised by us at the exact pixel size each GUI scale needs.
 *
 * Metrics come from the font's design units, unhinted, so layout is linear in the size; kerning comes from the GPOS
 * table ([GposKerning]). Used on the render thread only.
 */
class SmoothFont private constructor(private var memory: ByteBuffer?, private var face: FT_Face?) : FontMetrics, AutoCloseable {
    private val unitsPerEm: Float
    override val ascender: Float
    override val descender: Float
    private val gpos: GposKerning

    private val glyphIds = Int2IntOpenHashMap().apply { defaultReturnValue(-1) }
    private val advances = Int2FloatOpenHashMap().apply { defaultReturnValue(Float.NaN) }
    private val kernings = Long2FloatOpenHashMap().apply { defaultReturnValue(Float.NaN) }

    init {
        val f = face!!
        unitsPerEm = (f.units_per_EM().toInt() and 0xFFFF).toFloat()
        require(unitsPerEm > 0f) { "Font has no units per em (a bitmap-only font?)" }
        ascender = f.ascender() / unitsPerEm
        descender = -f.descender() / unitsPerEm
        gpos = GposKerning(memory!!)
    }

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
        val units = MemoryStack.stackPush().use { stack ->
            val out = stack.callocCLong(1)
            val failed = FreeTypeUtil.checkError(FreeType.FT_Get_Advance(face(), glyphId(codePoint), FreeType.FT_LOAD_NO_SCALE, out), "Glyph advance")
            if (failed) 0L else out.get(0)
        }
        return (units / unitsPerEm).also { advances.put(codePoint, it) }
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
     * positioning). Null when the glyph is empty (a space) or cannot be drawn as an 8-bit coverage mask.
     */
    fun rasterizeGlyph(glyphId: Int, sizePx: Float, shiftX: Float, hinting: Hinting): Bitmap? {
        val f = face()
        FreeTypeUtil.checkError(FreeType.FT_Set_Char_Size(f, 0, (sizePx * 64f).roundToLong(), 72, 72), "Glyph size")
        MemoryStack.stackPush().use { stack ->
            val delta = FT_Vector.malloc(stack).set((shiftX * 64f).roundToLong(), 0)
            FreeType.FT_Set_Transform(f, null, delta)
        }
        // Outlines only, as Minecraft's own provider loads them: a font with embedded bitmaps (monochrome, or colour)
        // would otherwise hand back pixels in another format.
        val flags = FreeType.FT_LOAD_RENDER or FT_LOAD_NO_BITMAP or hinting.loadFlags
        if (FreeTypeUtil.checkError(FreeType.FT_Load_Glyph(f, glyphId, flags), "Glyph render")) return null
        val slot = f.glyph() ?: return null
        val bitmap = slot.bitmap()
        val width = bitmap.width()
        val height = bitmap.rows()
        if (width <= 0 || height <= 0) return null
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

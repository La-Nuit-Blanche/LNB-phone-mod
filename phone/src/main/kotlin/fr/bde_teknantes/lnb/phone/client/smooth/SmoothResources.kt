package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.phone.LnbPhone
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.lwjgl.opengl.GL11
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Everything the smooth layer keeps between frames: the two Inter faces and the atlas of glyphs, shape tiles and
 * avatars rasterised at real pixel sizes. Keys are pixel sizes, not GUI scales, so a GUI scale change simply starts
 * using other entries. A resource reload drops it all (a resource pack may replace the fonts); everything is rebuilt
 * lazily from the next frame. Render thread only.
 */
object SmoothResources : ResourceManagerReloadListener {
    private val FONT_FILES = mapOf(
        FontWeight.REGULAR to ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "font/inter_regular.ttf"),
        FontWeight.SEMIBOLD to ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "font/inter_semibold.ttf"),
    )

    /** Retry delay for a skin whose pixels are not on the GPU yet. */
    private const val SKIN_RETRY_NANOS = 500_000_000L

    private val fonts = HashMap<FontWeight, SmoothFont>()
    private var atlasOrNull: MaskAtlas? = null
    private val glyphSets = HashMap<GlyphSetKey, GlyphSet>()
    private val shapes = Long2ObjectOpenHashMap<MaskAtlas.Region>()
    private val avatars = HashMap<Pair<ResourceLocation, Int>, MaskAtlas.Region>()
    private val skinFaces = HashMap<ResourceLocation, IntArray>()
    private val skinRetries = HashMap<ResourceLocation, Long>()

    /** Called just before the atlas is cleared, so quads already queued against it are drawn first. */
    internal var beforeAtlasClear: (() -> Unit)? = null

    /** Rasterised glyph: its atlas region (null for blank glyphs) and offset from the pen on the baseline. */
    class Glyph(val region: MaskAtlas.Region?, val left: Int, val top: Int)

    private data class GlyphSetKey(val weight: FontWeight, val sizePx: Float, val hinting: SmoothFont.Hinting, val phases: Int)

    private val NO_GLYPH = Glyph(null, 0, 0)

    val atlas: MaskAtlas get() = atlasOrNull ?: MaskAtlas().also { atlasOrNull = it }

    fun font(weight: FontWeight): SmoothFont =
        fonts.getOrPut(weight) { SmoothFont.load(Minecraft.getInstance().resourceManager, FONT_FILES.getValue(weight)) }

    /** The glyphs of one weight at [sizePx] per em, rasterised with [hinting] at [phases] sub-pixel positions. */
    fun glyphs(weight: FontWeight, sizePx: Float, hinting: SmoothFont.Hinting, phases: Int): GlyphSet =
        glyphSets.getOrPut(GlyphSetKey(weight, sizePx, hinting, phases)) { GlyphSet(weight, sizePx, hinting, phases) }

    /** Glyphs rasterised on demand. Look one set up per string, then [get] each glyph without allocating. */
    class GlyphSet internal constructor(
        private val weight: FontWeight,
        private val sizePx: Float,
        private val hinting: SmoothFont.Hinting,
        private val phases: Int,
    ) {
        private val glyphs = Int2ObjectOpenHashMap<Glyph>()

        /** [codePoint] shifted right by `phase / phases` px. */
        fun get(codePoint: Int, phase: Int): Glyph {
            val key = codePoint * phases + phase
            glyphs.get(key)?.let { return it }
            val bitmap = font(weight).rasterize(codePoint, sizePx, phase.toFloat() / phases, hinting)
            val glyph = if (bitmap == null) {
                NO_GLYPH
            } else {
                // May clear the atlas, and this set with it, before returning the region.
                val region = allocate(bitmap.width, bitmap.height)
                atlas.writeMask(region, bitmap.coverage)
                Glyph(region, bitmap.left, bitmap.top)
            }
            glyphs.put(key, glyph)
            return glyph
        }

        internal fun clear() = glyphs.clear()
    }

    /** A region holding one fully opaque white texel, for plain rectangles. */
    fun solid(): MaskAtlas.Region = shape(1, 1, 0f, 0)

    /**
     * Mask of a rounded rectangle [width] x [height] px with corner radius [radiusPx] (and an inner stroke of
     * [strokePx] px, or a fill when 0). Pass the full size for a mask drawn 1:1, or the size of a nine-slice tile.
     */
    fun shape(width: Int, height: Int, radiusPx: Float, strokePx: Int): MaskAtlas.Region {
        val radius8 = (radiusPx * 8f).roundToInt()
        val key = (width.toLong() shl 40) or (height.toLong() shl 24) or (radius8.toLong() shl 8) or strokePx.toLong()
        shapes.get(key)?.let { return it }
        val coverage = ShapeMask.roundedRect(width, height, radius8 / 8f, strokePx.toFloat())
        val region = allocate(width, height)
        atlas.writeMask(region, coverage)
        shapes.put(key, region)
        return region
    }

    /**
     * Size of the square tile a rounded rectangle is nine-sliced from: corners of `ceil(radius)` px (at least the
     * stroke) around a 1 px middle row and column that stretch.
     */
    fun cornerSize(radiusPx: Float, strokePx: Int): Int = max(ceil(radiusPx - 1e-3f).toInt(), strokePx)

    /**
     * The 8 x 8 face of [skin] with its hat layer, scaled to [sizePx] with nearest-neighbour and cut into an
     * anti-aliased circle; null while the skin's pixels are not uploaded yet.
     */
    fun avatar(skin: ResourceLocation, sizePx: Int): MaskAtlas.Region? {
        avatars[skin to sizePx]?.let { return it }
        val face = skinFace(skin) ?: return null
        val mask = ShapeMask.roundedRect(sizePx, sizePx, sizePx / 2f)
        val colors = IntArray(sizePx * sizePx) { i ->
            val x = (i % sizePx) * 8 / sizePx
            val y = (i / sizePx) * 8 / sizePx
            val c = face[y * 8 + x]
            val alpha = (c ushr 24) * (mask[i].toInt() and 0xFF) / 255
            (alpha shl 24) or (c and 0xFFFFFF)
        }
        val region = allocate(sizePx, sizePx)
        atlas.writeColors(region, colors)
        avatars[skin to sizePx] = region
        return region
    }

    /**
     * Reads the skin back from the GPU once and composites the hat over the face. Skins are uploaded asynchronously,
     * so an unfinished one (any face pixel not opaque: Minecraft makes the face area opaque when it loads a skin) is
     * retried later instead of being cached.
     */
    private fun skinFace(skin: ResourceLocation): IntArray? {
        skinFaces[skin]?.let { return it }
        val now = System.nanoTime()
        skinRetries[skin]?.let { if (now < it) return null }
        val texture = Minecraft.getInstance().textureManager.getTexture(skin)
        texture.bind()
        val width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
        val height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
        if (width < 64 || height < 32 || width % 64 != 0) {
            skinRetries[skin] = now + SKIN_RETRY_NANOS
            return null
        }
        val texel = width / 64
        val face = NativeImage(width, height, false).use { image ->
            image.downloadTexture(0, false)
            IntArray(64) { i ->
                val x = i % 8
                val y = i / 8
                val base = argb(image.getPixelRGBA((8 + x) * texel, (8 + y) * texel))
                val hat = argb(image.getPixelRGBA((40 + x) * texel, (8 + y) * texel))
                over(hat, base)
            }
        }
        if (face.any { it ushr 24 != 0xFF }) {
            skinRetries[skin] = now + SKIN_RETRY_NANOS
            return null
        }
        skinRetries.remove(skin)
        skinFaces[skin] = face
        return face
    }

    private fun argb(abgr: Int): Int = (abgr and 0xFF00FF00.toInt()) or ((abgr shr 16) and 0xFF) or ((abgr and 0xFF) shl 16)

    /** [top] composited over the opaque [bottom]. */
    private fun over(top: Int, bottom: Int): Int {
        val a = top ushr 24
        if (a == 0) return bottom or (0xFF shl 24)
        fun channel(shift: Int) = (((top shr shift) and 0xFF) * a + ((bottom shr shift) and 0xFF) * (255 - a) + 127) / 255
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun allocate(width: Int, height: Int): MaskAtlas.Region {
        atlas.allocate(width, height)?.let { return it }
        // Full: draw what is queued, then start over. Only what the current frames use comes back.
        beforeAtlasClear?.invoke()
        clearAtlas()
        return atlas.allocate(width, height) ?: throw IllegalArgumentException("${width}x$height does not fit in the smooth atlas")
    }

    private fun clearAtlas() {
        atlasOrNull?.clear()
        // Emptied rather than dropped: a canvas may hold a set while it draws a string.
        glyphSets.values.forEach(GlyphSet::clear)
        shapes.clear()
        avatars.clear()
    }

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        if (fonts.isNotEmpty()) LnbPhone.LOGGER.info("Resources reloaded: dropping the smooth layer's fonts and atlas")
        clearAtlas()
        fonts.values.forEach(SmoothFont::close)
        fonts.clear()
        skinFaces.clear()
        skinRetries.clear()
    }
}

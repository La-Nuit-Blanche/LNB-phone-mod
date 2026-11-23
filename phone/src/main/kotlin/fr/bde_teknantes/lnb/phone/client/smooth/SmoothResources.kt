package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.phone.LnbPhone
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.lwjgl.opengl.GL11
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.roundToInt

/**
 * Everything the smooth layer keeps between frames: the two Inter faces, the emoji face, and the atlas of glyphs, shape
 * tiles and avatars rasterised at real pixel sizes. Keys are pixel sizes, not GUI scales, so a GUI scale change simply
 * starts using other entries. A resource reload drops it all (a resource pack may replace the fonts); everything is
 * rebuilt lazily from the next frame. Render thread only.
 */
object SmoothResources : ResourceManagerReloadListener {
    private val FONT_FILES = mapOf(
        FontWeight.REGULAR to ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "font/inter_regular.ttf"),
        FontWeight.SEMIBOLD to ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "font/inter_semibold.ttf"),
    )
    private val EMOJI_FILE = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "font/noto_emoji.ttf")

    /** Retry delay for a skin whose pixels are not on the GPU yet. */
    private const val SKIN_RETRY_NANOS = 500_000_000L

    /** Two atlas clears closer than this mean it is thrashing. */
    private const val THRASH_NANOS = 5_000_000_000L

    private val fonts = HashMap<FontWeight, SmoothFont>()
    private val fontFailures = HashMap<FontWeight, IllegalStateException>()
    private val faces = HashMap<FontWeight, TextFace>()
    private var emojiFace: EmojiFace? = null
    private var emojiFailed = false
    private var emojiLoad: CompletableFuture<EmojiFace?>? = null
    private var emojiWaited = false

    /** How long the render thread waits, once, for the emoji face still loading. */
    private const val EMOJI_WAIT_MS = 50L
    private var warnedOversize = false
    private var guiScale = 2
    private var lastAtlasClear = 0L
    private var warnedThrash = false
    private var atlasOrNull: MaskAtlas? = null
    private val glyphSets = HashMap<GlyphSetKey, GlyphSet>()
    private val shapes = Long2ObjectOpenHashMap<MaskAtlas.Region>()
    private val avatars = HashMap<Pair<ResourceLocation, Int>, MaskAtlas.Region>()
    private val skinFaces = HashMap<ResourceLocation, IntArray>()
    private val skinRetries = HashMap<ResourceLocation, Long>()
    private val skinFirstSeen = HashMap<ResourceLocation, Long>()

    /** Called just before the atlas is cleared, so quads already queued against it are drawn first. */
    internal var beforeAtlasClear: (() -> Unit)? = null

    /** Rasterised glyph: its atlas region (null for blank glyphs) and offset from the pen on the baseline. */
    class Glyph(val region: MaskAtlas.Region?, val left: Int, val top: Int)

    private data class GlyphSetKey(val weight: FontWeight, val sizePx: Float, val hinting: SmoothFont.Hinting, val phases: Int, val emoji: Boolean)

    private val NO_GLYPH = Glyph(null, 0, 0)

    val atlas: MaskAtlas
        get() = atlasOrNull ?: MaskAtlas(atlasPageSize(guiScale), atlasPages(guiScale)).also { atlasOrNull = it }

    /**
     * Sizes the atlas for [scale]: glyphs and masks grow with the square of the GUI scale (scale 9 needs five times
     * the room of scale 4), so above 4 the pages are 2048 px. Called by the canvas at every begin; a change of page
     * size starts a new atlas.
     */
    internal fun useScale(scale: Int) {
        if (scale == guiScale) return
        val resize = atlasPageSize(scale) != atlasPageSize(guiScale)
        guiScale = scale
        if (resize) {
            clearAtlas()
            atlasOrNull = null
        }
    }

    /** 1024 px pages up to GUI scale 4 (4 MB each, up to 4), 2048 px above (16 MB each, up to 3). */
    internal fun atlasPageSize(scale: Int) = if (scale <= 4) 1024 else 2048

    internal fun atlasPages(scale: Int) = if (scale <= 4) 4 else 3

    /**
     * The face of [weight]. The file comes from the highest resource pack that has a working one: a pack (a server's
     * too) may replace Inter with a broken or unusable file, and then the next pack down is tried, ending with the
     * copy in the phone's own jar. Each broken file is logged once per resource reload.
     */
    fun font(weight: FontWeight): SmoothFont {
        RenderThread.check("Measuring or drawing smooth text")
        fonts[weight]?.let { return it }
        fontFailures[weight]?.let { throw it }
        val location = FONT_FILES.getValue(weight)
        val stack = Minecraft.getInstance().resourceManager.getResourceStack(location).asReversed()
        val load = { resource: Resource -> SmoothFont.load(resource.open(), "$location from ${resource.sourcePackId()}") }
        val font = Fallback.firstLoading(stack, load) { resource, e ->
            LnbPhone.LOGGER.error(
                "Unusable font {} in resource pack '{}', trying the next one down", location, resource.sourcePackId(), e,
            )
        }
        if (font == null) {
            // Not even the jar's copy: remember it rather than reading every pack again on every frame.
            val failure = IllegalStateException("No usable $location in any resource pack, the phone's own included")
            fontFailures[weight] = failure
            throw failure
        }
        fonts[weight] = font
        return font
    }

    /**
     * The text, measured and laid out in [weight]: Inter's metrics and the emoji face ([TextFace]). What measuring and
     * drawing use, so the two always agree. A new one once the emoji face arrives, so that nothing laid out while it
     * was loading (with boxes) is taken from a cache afterwards.
     */
    fun face(weight: FontWeight): TextFace {
        RenderThread.check("Measuring or drawing smooth text")
        collectEmoji()
        faces[weight]?.let { return it }
        return TextFace(font(weight)) { emoji() }.also { faces[weight] = it }
    }

    /**
     * The emoji face, from the highest resource pack with a working file, as for Inter. It is opened on a thread of
     * its own, started by [preloadEmoji] when the phone is about to open, or here the first time a text needs it; the
     * first time, this waits for it at most [EMOJI_WAIT_MS], and otherwise answers null (emoji are boxes for a frame or
     * two) until it is there. Unlike Inter, a missing or broken emoji font costs only the emoji: then this is null for
     * good (logged once per resource reload), as for characters no font has.
     */
    fun emoji(): EmojiFace? {
        RenderThread.check("Measuring or drawing emoji")
        collectEmoji()
        emojiFace?.let { return it }
        if (emojiFailed) return null
        val pending = emojiLoad ?: startEmojiLoad()
        if (!emojiWaited) {
            emojiWaited = true
            try {
                pending.get(EMOJI_WAIT_MS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                LnbPhone.LOGGER.warn("The emoji font is still loading after {} ms: emoji show as boxes until it is there", EMOJI_WAIT_MS)
            } catch (e: Exception) {
                // A failed load: collected just below.
            }
            collectEmoji()
        }
        return emojiFace
    }

    /** Starts opening the emoji face in the background (the file, its ligatures), so that the first emoji does not wait. */
    fun preloadEmoji() {
        RenderThread.check("Pre-loading the emoji font")
        collectEmoji()
        if (emojiFace == null && !emojiFailed && emojiLoad == null) startEmojiLoad()
    }

    private fun startEmojiLoad(): CompletableFuture<EmojiFace?> {
        // The pack list is read here, on the render thread; only opening the files happens on the loader's thread, a
        // thread of its own rather than the game's shared executor, whose queue may be long when the phone opens.
        val stack = Minecraft.getInstance().resourceManager.getResourceStack(EMOJI_FILE).asReversed()
        val executor = Executor { task -> Thread(task, "LNB Phone emoji font").apply { isDaemon = true }.start() }
        return CompletableFuture.supplyAsync({ loadEmoji(stack) }, executor).also { emojiLoad = it }
    }

    /** Takes the emoji face once its load is over, whatever ended it: a face, null, or any throwable (no emoji then). */
    private fun collectEmoji() {
        val pending = emojiLoad ?: return
        if (!pending.isDone) return
        emojiLoad = null
        val face = try {
            pending.getNow(null)
        } catch (e: Exception) {
            LnbPhone.LOGGER.error("Loading the emoji font {} failed: emoji show as boxes", EMOJI_FILE, e)
            null
        }
        if (face == null) emojiFailed = true else emojiFace = face
        // Faces handed out while it loaded have no emoji, and caches keyed by them hold boxes: from now on, new ones.
        faces.clear()
    }

    /**
     * The first working emoji font of [stack], best first, with its ligatures read; null (logged) when none works. A
     * font works when it draws U+1F600 as a single-colour outline: a colour font (bitmaps, or colour layers over empty
     * outlines) would pass a lookup and then draw blank emoji.
     */
    private fun loadEmoji(stack: List<Resource>): EmojiFace? {
        val load = { resource: Resource ->
            val font = SmoothFont.load(resource.open(), "$EMOJI_FILE from ${resource.sourcePackId()}")
            try {
                check(EmojiFace.usable(font)) { "No single-colour emoji in $EMOJI_FILE from ${resource.sourcePackId()}" }
                font.ligatures
                EmojiFace(font)
            } catch (e: Throwable) {
                font.close()
                throw e
            }
        }
        val face = Fallback.firstLoading(stack, load) { resource, e ->
            LnbPhone.LOGGER.error(
                "Unusable emoji font {} in resource pack '{}', trying the next one down", EMOJI_FILE, resource.sourcePackId(), e,
            )
        }
        if (face == null) LnbPhone.LOGGER.error("No usable {} in any resource pack, the phone's own included: emoji show as boxes", EMOJI_FILE)
        return face
    }

    /** The glyphs of one weight at [sizePx] per em, rasterised with [hinting] at [phases] sub-pixel positions. */
    fun glyphs(weight: FontWeight, sizePx: Float, hinting: SmoothFont.Hinting, phases: Int): GlyphSet {
        RenderThread.check("Drawing smooth text")
        return glyphSets.getOrPut(GlyphSetKey(weight, sizePx, hinting, phases, false)) { GlyphSet(weight, sizePx, hinting, phases, false) }
    }

    /**
     * The emoji face's glyphs at [sizePx] per em, drawn next to text of [weight] (so at its weight, [EmojiFace.weightOf]),
     * rasterised with [hinting] at [phases] sub-pixel positions. Looked up by glyph ([GlyphSet.glyph]).
     */
    fun emojiGlyphs(weight: FontWeight, sizePx: Float, hinting: SmoothFont.Hinting, phases: Int): GlyphSet {
        RenderThread.check("Drawing emoji")
        return glyphSets.getOrPut(GlyphSetKey(weight, sizePx, hinting, phases, true)) { GlyphSet(weight, sizePx, hinting, phases, true) }
    }

    /** Glyphs rasterised on demand. Look one set up per string, then [get] each glyph without allocating. */
    class GlyphSet internal constructor(
        private val weight: FontWeight,
        private val sizePx: Float,
        private val hinting: SmoothFont.Hinting,
        private val phases: Int,
        private val isEmoji: Boolean,
    ) {
        private val glyphs = Int2ObjectOpenHashMap<Glyph>()

        /**
         * [codePoint] shifted right by `phase / phases` px. Keyed by glyph, not code point: every character Inter
         * lacks is its glyph 0, the missing-glyph box, stored once.
         */
        fun get(codePoint: Int, phase: Int): Glyph = glyph(font(weight).glyphId(codePoint), phase)

        /** Glyph [glyphId] of this set's face (Inter's, or the emoji face's) shifted right by `phase / phases` px. */
        fun glyph(glyphId: Int, phase: Int): Glyph {
            val key = glyphId * phases + phase
            glyphs.get(key)?.let { return it }
            val font = if (isEmoji) emoji()?.font ?: return NO_GLYPH else font(weight)
            val variation = if (isEmoji) EmojiFace.weightOf(weight) else Float.NaN
            val bitmap = font.rasterizeGlyph(glyphId, sizePx, phase.toFloat() / phases, hinting, variation)
            // allocate() may clear the atlas, and this set with it, before returning the region.
            val region = bitmap?.let { allocate(it.width, it.height) }
            val glyph = if (bitmap == null || region == null) {
                NO_GLYPH
            } else {
                atlas.writeMask(region, bitmap.coverage)
                Glyph(region, bitmap.left, bitmap.top)
            }
            glyphs.put(key, glyph)
            return glyph
        }

        internal fun clear() = glyphs.clear()
    }

    /** Every character the phone's French and English text commonly uses. */
    private const val COMMON_CHARACTERS = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`" +
        "abcdefghijklmnopqrstuvwxyz{|}~àâäçéèêëîïôöùûüÿœæÀÂÄÇÉÈÊËÎÏÔÖÙÛÜŸŒÆ«»’…·–—"

    /** Kit shapes (art direction 8.5 to 8.7), in dp: width, height, radius, stroke. Their tiles do not depend on width. */
    private val KIT_SHAPES = listOf(
        floatArrayOf(64f, 40f, 10f, 0f), floatArrayOf(64f, 40f, 10f, 1f), floatArrayOf(64f, 40f, 10f, 2f),
        floatArrayOf(64f, 64f, 14f, 0f), floatArrayOf(64f, 64f, 14f, 1f),
        floatArrayOf(64f, 32f, 16f, 0f), floatArrayOf(64f, 32f, 16f, 1f), floatArrayOf(64f, 32f, 16f, 2f),
        floatArrayOf(36f, 20f, 10f, 0f), floatArrayOf(32f, 16f, 8f, 0f), floatArrayOf(48f, 3f, 1.5f, 0f),
        floatArrayOf(6f, 6f, 3f, 0f), floatArrayOf(8f, 8f, 4f, 0f), floatArrayOf(16f, 16f, 8f, 0f),
        floatArrayOf(28f, 28f, 14f, 0f), floatArrayOf(32f, 32f, 16f, 0f), floatArrayOf(48f, 48f, 24f, 0f),
    )

    /**
     * Loads the fonts and rasterises, for GUI scale [guiScale], the common characters in every style of the type
     * ramp and the kit's shape tiles, so the first frames of the phone do not hitch on them. Returns the time taken.
     */
    fun prewarm(guiScale: Int): Long {
        RenderThread.check("Pre-warming the smooth layer")
        val start = System.nanoTime()
        useScale(guiScale)
        val grid = PixelGrid(guiScale)
        val styles = listOf(
            TextStyles.TITLE_L, TextStyles.TITLE_M, TextStyles.BODY, TextStyles.BODY_STRONG, TextStyles.LABEL,
            TextStyles.CAPTION, TextStyles.CAPTION_STRONG, TextStyles.MICRO,
        )
        for (style in styles.distinctBy { it.weight to it.size }) {
            val set = glyphs(style.weight, grid.toPx(style.size), SmoothFont.Hinting.LIGHT, 1)
            COMMON_CHARACTERS.codePoints().forEach { set.get(it, 0) }
        }
        // The emoji face is opened in the background; no emoji is rasterised ahead: there are thousands, and the few a
        // screen shows come at a fraction of a millisecond each.
        preloadEmoji()
        for ((w, h, radiusDp, strokeDp) in KIT_SHAPES) {
            // As SmoothCanvas.roundRect sizes them, at an even dp position.
            val pw = grid.snap(w)
            val ph = grid.snap(h)
            val radius = minOf(grid.toPx(radiusDp), minOf(pw, ph) / 2f)
            val stroke = if (strokeDp > 0f) grid.strokePx(strokeDp) else 0
            val corner = NineSlice.cornerSize(radius, stroke)
            shape(NineSlice.tileSize(pw, corner), NineSlice.tileSize(ph, corner), radius, stroke)
        }
        solid()
        return System.nanoTime() - start
    }

    /** A region holding one fully opaque white texel, for plain rectangles. */
    fun solid(): MaskAtlas.Region = shape(1, 1, 0f, 0)!!

    /**
     * Mask of a rounded rectangle [width] x [height] px with corner radius [radiusPx] (and an inner stroke of
     * [strokePx] px, or a fill when 0). Pass the full size for a mask drawn 1:1, or the size of a nine-slice tile.
     * Null when it is larger than an atlas page.
     */
    fun shape(width: Int, height: Int, radiusPx: Float, strokePx: Int): MaskAtlas.Region? {
        RenderThread.check("Drawing a smooth shape")
        val radius8 = (radiusPx * 8f).roundToInt()
        val key = (width.toLong() shl 40) or (height.toLong() shl 24) or (radius8.toLong() shl 8) or strokePx.toLong()
        shapes.get(key)?.let { return it }
        val region = allocate(width, height) ?: return null
        val coverage = ShapeMask.roundedRect(width, height, radius8 / 8f, strokePx.toFloat())
        atlas.writeMask(region, coverage)
        shapes.put(key, region)
        return region
    }

    /**
     * The 8 x 8 face of [skin] with its hat layer, scaled to [sizePx] with nearest-neighbour and cut into an
     * anti-aliased circle; null while the skin's pixels are not uploaded yet.
     */
    fun avatar(skin: ResourceLocation, sizePx: Int): MaskAtlas.Region? {
        RenderThread.check("Drawing a player face")
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
        val region = allocate(sizePx, sizePx) ?: return null
        atlas.writeColors(region, colors)
        avatars[skin to sizePx] = region
        return region
    }

    /**
     * The skin's face with its hat, read back from the GPU once it is really there. Skin textures are registered
     * before their pixels arrive and show Steve's skin meanwhile (and nothing at all before that), so a read that is
     * not opaque, or that is exactly Steve's face for a skin that is not Steve's, is retried later instead of being
     * cached ([SkinReadiness]).
     */
    private fun skinFace(skin: ResourceLocation): IntArray? {
        skinFaces[skin]?.let { return it }
        val now = System.nanoTime()
        skinRetries[skin]?.let { if (now < it) return null }
        val firstSeen = skinFirstSeen.getOrPut(skin) { now }
        val face = readFace(skin)
        val placeholder = DefaultPlayerSkin.getDefaultTexture()
        val placeholderFace = if (skin == placeholder || face == null) null else placeholderFace(placeholder)
        val ready = SkinReadiness.isReady(
            face = face,
            placeholderFace = placeholderFace,
            waitedNanos = now - firstSeen,
        )
        if (!ready || face == null) {
            skinRetries[skin] = now + SKIN_RETRY_NANOS
            return null
        }
        skinRetries.remove(skin)
        skinFirstSeen.remove(skin)
        skinFaces[skin] = face
        return face
    }

    private fun placeholderFace(placeholder: ResourceLocation): IntArray? =
        skinFaces[placeholder] ?: readFace(placeholder)?.also { skinFaces[placeholder] = it }

    /** The texture's 8 x 8 face layer and hat layer as ARGB, or null when it is not a skin-sized texture. */
    private fun readFace(location: ResourceLocation): IntArray? {
        val texture = Minecraft.getInstance().textureManager.getTexture(location)
        texture.bind()
        val width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
        val height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
        if (width < 64 || height < 32 || width % 64 != 0) return null
        val texel = width / 64
        return NativeImage(width, height, false).use { image ->
            image.downloadTexture(0, false)
            val base = IntArray(64) { argb(image.getPixelRGBA((8 + it % 8) * texel, (8 + it / 8) * texel)) }
            val hat = IntArray(64) { argb(image.getPixelRGBA((40 + it % 8) * texel, (8 + it / 8) * texel)) }
            SkinReadiness.composite(base, hat)
        }
    }

    private fun argb(abgr: Int): Int = (abgr and 0xFF00FF00.toInt()) or ((abgr shr 16) and 0xFF) or ((abgr and 0xFF) shl 16)

    /** Room in the atlas, or null (logged once) for something larger than a page, which clearing would not help. */
    private fun allocate(width: Int, height: Int): MaskAtlas.Region? {
        if (!atlas.fits(width, height)) {
            if (!warnedOversize) {
                warnedOversize = true
                LnbPhone.LOGGER.warn("Smooth layer skipped a {}x{} px glyph or shape: larger than an atlas page", width, height)
            }
            return null
        }
        atlas.allocate(width, height)?.let { return it }
        // Full: draw what is queued, then start over. Only what the current frames use comes back.
        val now = System.nanoTime()
        if (now - lastAtlasClear < THRASH_NANOS && !warnedThrash) {
            // Filling up again within seconds means one screen needs more than the atlas holds: every frame redraws
            // everything from scratch.
            warnedThrash = true
            LnbPhone.LOGGER.warn(
                "Smooth atlas filled twice within {} s at GUI scale {}: a screen uses more glyphs and shapes than it holds",
                THRASH_NANOS / 1_000_000_000, guiScale,
            )
        }
        lastAtlasClear = now
        beforeAtlasClear?.invoke()
        clearAtlas()
        return atlas.allocate(width, height)
    }

    private fun clearAtlas() {
        atlasOrNull?.clear()
        // Emptied rather than dropped: a canvas may hold a set while it draws a string.
        glyphSets.values.forEach(GlyphSet::clear)
        shapes.clear()
        avatars.clear()
    }

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        if (fonts.isNotEmpty() || emojiFace != null) LnbPhone.LOGGER.info("Resources reloaded: dropping the smooth layer's fonts and atlas")
        clearAtlas()
        fonts.values.forEach(SmoothFont::close)
        fonts.clear()
        fontFailures.clear()
        faces.clear()
        emojiFace?.close()
        emojiFace = null
        emojiFailed = false
        emojiWaited = false
        // A face still being read comes from the packs before the reload: closed when it arrives, never used.
        emojiLoad?.thenAccept { it?.close() }
        emojiLoad = null
        skinFaces.clear()
        skinRetries.clear()
        skinFirstSeen.clear()
    }
}

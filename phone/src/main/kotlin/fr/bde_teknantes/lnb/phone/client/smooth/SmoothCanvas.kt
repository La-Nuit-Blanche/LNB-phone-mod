package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.systems.RenderSystem
import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The drawing surface of the smooth app layer: a rectangle measured in dp (2 dp = 1 GUI px) whose content is drawn
 * at the screen's real resolution, whatever the GUI scale.
 *
 * - Text is Inter, rasterised by FreeType at the exact pixel size of each style (13 dp is 19.5 px at GUI scale 3),
 *   laid out with the font's unhinted advances and GPOS kerning, each glyph placed on a whole pixel.
 * - Rounded rectangles, pills and circles are anti-aliased masks computed once at the real pixel size and nine-sliced
 *   on whole pixels, so their curves are as sharp at 1.5 px per dp as at 2.
 * - Every edge is snapped to the pixel grid with [PixelGrid]: what touches in dp touches on screen.
 *
 * Use it between [begin] and [end], inside a Minecraft screen's render. Coordinates are dp from the top-left of the
 * rectangle given to [begin]; colours are ARGB (`0xAARRGGBB`). [end] leaves the pose, scissor, shader, shader texture
 * and colour, blending and depth test as [begin] found them. Render thread only.
 */
class SmoothCanvas {
    private val clips = ArrayList<IntArray>()
    private val vector = Vector3f()
    private var drawing = false
    private var originX = 0
    private var originY = 0
    private var framebufferHeight = 0
    private var frameStart = 0L

    /** The pixel grid of the current frame. */
    var grid = PixelGrid(2)
        private set

    /** Size of the surface in dp. */
    var width = 0f
        private set
    var height = 0f
        private set

    /** How glyphs are fitted to pixels; see [SmoothFont.Hinting]. */
    var hinting = SmoothFont.Hinting.LIGHT

    /** Sub-pixel positions each glyph is rasterised at (1 = every glyph on a whole pixel). */
    var subpixelPhases = 1
        set(value) {
            require(value in 1..8) { "Between 1 and 8 phases" }
            field = value
        }

    /** CPU time of the last frame between [begin] and [end], and its draw calls. */
    var lastFrameNanos = 0L
        private set
    var lastFrameDrawCalls = 0
        private set

    // State found at begin(), put back at end().
    private var savedShader: ShaderInstance? = null
    private var savedTexture = 0
    private val savedColor = FloatArray(4)
    private var savedBlend = false
    private val savedBlendFunc = IntArray(4)
    private var savedScissor = false
    private val savedScissorBox = IntArray(4)
    private var savedDepthTest = true

    /**
     * Starts drawing into the rectangle at ([left], [top]), [widthGui] x [heightGui] GUI px, in the current pose.
     * The pose may translate (even by fractions, as during a slide), but must not scale: the origin is snapped to a
     * whole pixel and drawing happens in real pixels from there. Everything drawn is clipped to the rectangle.
     */
    fun begin(graphics: GuiGraphics, left: Int, top: Int, widthGui: Int, heightGui: Int) {
        check(active == null) { "begin() while a smooth canvas is already drawing" }
        active = this
        drawing = true
        frameStart = System.nanoTime()
        batch.resetStats()
        // What the GUI batched before us must be drawn under us. flush() switches the depth test on whatever it was
        // (in a world the HUD leaves it off), so put it back as found.
        savedDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        graphics.flush()
        setDepthTest(savedDepthTest)

        val window = Minecraft.getInstance().window
        val scale = window.guiScale.roundToInt().coerceAtLeast(1)
        if (scale != grid.guiScale) grid = PixelGrid(scale)
        framebufferHeight = window.height
        width = widthGui * 2f
        height = heightGui * 2f

        val pose = graphics.pose().last().pose()
        if (pose.m00() != 1f || pose.m11() != 1f) warnScaledPose()
        pose.transformPosition(vector.set(left.toFloat(), top.toFloat(), 0f))
        originX = (vector.x * scale).roundToInt()
        originY = (vector.y * scale).roundToInt()
        batch.matrix = Matrix4f().translation(0f, 0f, pose.m32()).scale(1f / scale, 1f / scale, 1f)

        saveState()
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        SmoothResources.beforeAtlasClear = batch::flush

        val viewport = intArrayOf(originX, originY, originX + grid.snap(width), originY + grid.snap(height))
        if (savedScissor) {
            // Already clipped by the caller: stay inside that too. GL boxes count rows from the bottom.
            intersect(viewport, savedScissorBox[0], framebufferHeight - savedScissorBox[1] - savedScissorBox[3],
                savedScissorBox[0] + savedScissorBox[2], framebufferHeight - savedScissorBox[1])
        }
        clips.clear()
        clips.add(viewport)
        applyScissor(viewport)
    }

    /** Draws what is still queued and restores the state [begin] found. */
    fun end() {
        check(drawing) { "end() without begin()" }
        batch.flush()
        SmoothResources.beforeAtlasClear = null
        restoreState()
        clips.clear()
        drawing = false
        active = null
        lastFrameDrawCalls = batch.drawCalls
        lastFrameNanos = System.nanoTime() - frameStart
    }

    // ---- Shapes ----

    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        solidQuad(originX + grid.snap(x), originY + grid.snap(y), originX + grid.snap(x + w), originY + grid.snap(y + h), color)
    }

    /** A horizontal hairline from [x0] to [x1], its top at [y], [thickness] dp rounded to whole pixels (min. 1). */
    fun hairline(x0: Float, x1: Float, y: Float, color: Int, thickness: Float = 1f) {
        val top = originY + grid.snap(y)
        solidQuad(originX + grid.snap(x0), top, originX + grid.snap(x1), top + grid.strokePx(thickness), color)
    }

    /** A vertical hairline from [y0] to [y1], its left edge at [x]. */
    fun verticalHairline(x: Float, y0: Float, y1: Float, color: Int, thickness: Float = 1f) {
        val left = originX + grid.snap(x)
        solidQuad(left, originY + grid.snap(y0), left + grid.strokePx(thickness), originY + grid.snap(y1), color)
    }

    /** A filled rounded rectangle; a [radius] of half the height or more makes a pill. */
    fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) = roundRect(x, y, w, h, radius, 0f, color)

    /** A stroke of [stroke] dp (whole pixels, min. 1) along the inside of a rounded rectangle's edge. */
    fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, stroke: Float, color: Int) {
        require(stroke > 0f) { "Stroke must be positive" }
        roundRect(x, y, w, h, radius, stroke, color)
    }

    /** A filled circle of [diameter] dp, its bounding box's top-left at ([x], [y]). */
    fun fillCircle(x: Float, y: Float, diameter: Float, color: Int) = roundRect(x, y, diameter, diameter, diameter, 0f, color)

    private fun roundRect(x: Float, y: Float, w: Float, h: Float, radiusDp: Float, strokeDp: Float, color: Int) {
        val l = originX + grid.snap(x)
        val t = originY + grid.snap(y)
        val r = originX + grid.snap(x + w)
        val b = originY + grid.snap(y + h)
        val pw = r - l
        val ph = b - t
        if (pw <= 0 || ph <= 0) return
        val radius = min(grid.toPx(radiusDp), min(pw, ph) / 2f)
        val stroke = if (strokeDp > 0f) grid.strokePx(strokeDp) else 0
        if (radius <= 0f && stroke == 0) return solidQuad(l, t, r, b, color)

        val corner = NineSlice.cornerSize(radius, stroke)
        if (NineSlice.fits(pw, ph, corner)) {
            val tile = SmoothResources.shape(2 * corner + 1, 2 * corner + 1, radius, stroke)
            NineSlice.forEachSlice(l, t, r, b, corner) { sl, st, sr, sb, u0, v0, u1, v1 ->
                regionQuad(tile, sl, st, sr, sb, u0, v0, u1, v1, color)
            }
        } else {
            // Too small for corners plus a middle (a 9 px circle at GUI scale 3): its own mask, drawn 1:1.
            regionQuad(SmoothResources.shape(pw, ph, radius, stroke), l, t, r, b, 0, 0, pw, ph, color)
        }
    }

    // ---- Text ----

    /** Width of [text] in [style], in dp. The same at every GUI scale. */
    fun measure(text: String, style: TextStyle): Float = TextLayout.width(text, SmoothResources.font(style.weight), style.size)

    /** [text] cut with an ellipsis to fit in [maxWidth] dp. */
    fun truncate(text: String, style: TextStyle, maxWidth: Float): String =
        TextLayout.truncate(text, SmoothResources.font(style.weight), style.size, maxWidth)

    /** [text] broken into lines of at most [maxWidth] dp. */
    fun wrap(text: String, style: TextStyle, maxWidth: Float): List<String> =
        TextLayout.wrap(text, SmoothResources.font(style.weight), style.size, maxWidth)

    /** Draws one line of [text] in the line box whose top-left is ([x], [y]). Returns its width in dp. */
    fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float {
        val font = SmoothResources.font(style.weight)
        val sizePx = grid.toPx(style.size)
        val baseline = originY + grid.snap(y + TextLayout.baseline(style, font))
        val start = originX + grid.toPx(x)
        val phases = subpixelPhases
        val glyphs = SmoothResources.glyphs(style.weight, sizePx, hinting, phases)
        val k = grid.pxPerDp
        return TextLayout.forEachGlyph(text, font, style.size) { cp, pen ->
            val px = start + pen * k
            var whole = floor(px).toInt()
            var phase = ((px - whole) * phases).roundToInt()
            if (phase == phases) {
                whole++
                phase = 0
            }
            val glyph = glyphs.get(cp, phase)
            val region = glyph.region
            if (region != null) {
                val gx = whole + glyph.left
                val gy = baseline - glyph.top
                regionQuad(region, gx, gy, gx + region.width, gy + region.height, 0, 0, region.width, region.height, color)
            }
        }
    }

    /** Draws [text] on one line, cut with an ellipsis at [maxWidth] dp. Returns the drawn width in dp. */
    fun drawTextTruncated(text: String, x: Float, y: Float, maxWidth: Float, style: TextStyle, color: Int): Float =
        drawText(truncate(text, style, maxWidth), x, y, style, color)

    /** Draws [text] wrapped at [maxWidth] dp, one line box of [style] per line. Returns the number of lines. */
    fun drawTextWrapped(text: String, x: Float, y: Float, maxWidth: Float, style: TextStyle, color: Int): Int {
        val lines = wrap(text, style, maxWidth)
        lines.forEachIndexed { i, line -> drawText(line, x, y + i * style.lineHeight, style, color) }
        return lines.size
    }

    // ---- Images ----

    /**
     * A white-on-transparent icon texture tinted with [color], drawn as a [size] dp square. Filtered linearly, since
     * the texture's pixels rarely match the screen's.
     */
    fun icon(texture: ResourceLocation, x: Float, y: Float, size: Float, color: Int) =
        image(texture, x, y, size, size, 0f, 0f, 1f, 1f, color, linear = true)

    /** Part [u0]..[u1] x [v0]..[v1] (0..1) of a texture, stretched over a rectangle in dp and tinted with [color]. */
    fun image(texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Int, linear: Boolean) {
        val tex = Minecraft.getInstance().textureManager.getTexture(texture)
        batch.quad(
            tex, linear,
            (originX + grid.snap(x)).toFloat(), (originY + grid.snap(y)).toFloat(),
            (originX + grid.snap(x + w)).toFloat(), (originY + grid.snap(y + h)).toFloat(),
            u0, v0, u1, v1, color,
        )
    }

    /**
     * A player's face (8 x 8 skin face plus hat, nearest-neighbour) in an anti-aliased circle of [size] dp. Use 16, 32
     * or 48 dp, which scale the face by a whole number at every GUI scale. While the skin is still loading, an
     * [placeholder] disc stands in.
     */
    fun playerFace(skin: ResourceLocation, x: Float, y: Float, size: Float, placeholder: Int = 0xFF3A4250.toInt()) {
        val l = originX + grid.snap(x)
        val t = originY + grid.snap(y)
        val s = grid.snap(x + size) - grid.snap(x)
        if (s <= 0) return
        val region = SmoothResources.avatar(skin, s) ?: return fillCircle(x, y, size, placeholder)
        regionQuad(region, l, t, l + s, t + s, 0, 0, s, s, -1)
    }

    // ---- Clipping ----

    /** Clips what follows to a rectangle in dp (inside the current clip) until the matching [popClip]. */
    fun pushClip(x: Float, y: Float, w: Float, h: Float) {
        val rect = intArrayOf(originX + grid.snap(x), originY + grid.snap(y), originX + grid.snap(x + w), originY + grid.snap(y + h))
        val current = clips.last()
        intersect(rect, current[0], current[1], current[2], current[3])
        batch.flush()
        clips.add(rect)
        applyScissor(rect)
    }

    fun popClip() {
        check(clips.size > 1) { "popClip() without pushClip()" }
        batch.flush()
        clips.removeAt(clips.size - 1)
        applyScissor(clips.last())
    }

    // ---- Internals ----

    private fun solidQuad(l: Int, t: Int, r: Int, b: Int, color: Int) {
        if (r <= l || b <= t) return
        regionQuad(SmoothResources.solid(), l, t, r, b, 0, 0, 1, 1, color)
    }

    /** Texels [u0]..[u1] x [v0]..[v1] of [region] onto pixels [l]..[r] x [t]..[b]. */
    private fun regionQuad(region: MaskAtlas.Region, l: Int, t: Int, r: Int, b: Int, u0: Int, v0: Int, u1: Int, v1: Int, color: Int) {
        val page = region.page
        val texel = 1f / page.size
        batch.quad(
            page.texture, false,
            l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(),
            (region.x + u0) * texel, (region.y + v0) * texel, (region.x + u1) * texel, (region.y + v1) * texel,
            color, page.uploader,
        )
    }

    private fun intersect(rect: IntArray, l: Int, t: Int, r: Int, b: Int) {
        rect[0] = max(rect[0], l)
        rect[1] = max(rect[1], t)
        rect[2] = max(rect[0], min(rect[2], r))
        rect[3] = max(rect[1], min(rect[3], b))
    }

    private fun applyScissor(rect: IntArray) {
        RenderSystem.enableScissor(rect[0], framebufferHeight - rect[3], rect[2] - rect[0], rect[3] - rect[1])
    }

    private fun saveState() {
        savedShader = RenderSystem.getShader()
        savedTexture = RenderSystem.getShaderTexture(0)
        RenderSystem.getShaderColor().copyInto(savedColor)
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND)
        savedBlendFunc[0] = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        savedBlendFunc[1] = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        savedBlendFunc[2] = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        savedBlendFunc[3] = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        savedScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, savedScissorBox)
    }

    private fun restoreState() {
        if (savedScissor) {
            RenderSystem.enableScissor(savedScissorBox[0], savedScissorBox[1], savedScissorBox[2], savedScissorBox[3])
        } else {
            RenderSystem.disableScissor()
        }
        RenderSystem.blendFuncSeparate(savedBlendFunc[0], savedBlendFunc[1], savedBlendFunc[2], savedBlendFunc[3])
        if (savedBlend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        RenderSystem.setShaderColor(savedColor[0], savedColor[1], savedColor[2], savedColor[3])
        RenderSystem.setShaderTexture(0, savedTexture)
        val shader = savedShader
        RenderSystem.setShader { shader }
        savedShader = null
        setDepthTest(savedDepthTest)
    }

    private fun setDepthTest(enabled: Boolean) {
        if (enabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
    }

    private companion object {
        // One vertex buffer for every canvas: its memory is native and canvases come and go with screens. Canvases
        // draw one after the other (app, then banner), never inside each other, so sharing it is safe.
        val batch by lazy { QuadBatch() }
        var active: SmoothCanvas? = null
        var warnedScaledPose = false

        fun warnScaledPose() {
            if (warnedScaledPose) return
            warnedScaledPose = true
            LnbPhone.LOGGER.warn("SmoothCanvas drawn under a scaled pose; the smooth layer ignores the scale")
        }
    }
}

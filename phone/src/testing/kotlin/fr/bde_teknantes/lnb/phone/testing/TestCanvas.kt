package fr.bde_teknantes.lnb.phone.testing

import fr.bde_teknantes.lnb.phone.api.client.AppCanvas
import fr.bde_teknantes.lnb.phone.api.client.Shadow
import fr.bde_teknantes.lnb.phone.api.client.ui.Icon
import fr.bde_teknantes.lnb.phone.api.client.ui.TextMetrics
import fr.bde_teknantes.lnb.phone.api.client.ui.TextStyle
import fr.bde_teknantes.lnb.phone.client.smooth.FontMetrics
import net.minecraft.resources.ResourceLocation

/**
 * A canvas that draws nothing and remembers, in viewport dp, the text and the filled shapes of a frame. It measures
 * with [TextMetrics], whose fonts the tester replaces with [MonospaceFont].
 */
internal class TestCanvas : AppCanvas() {
    val texts = ArrayList<DrawnText>()
    val shapes = ArrayList<DrawnShape>()
    private val offsets = ArrayDeque<Pair<Float, Float>>()
    private var dx = 0f
    private var dy = 0f
    private var clips = 0
    private var alphas = 0

    fun clear() {
        texts.clear()
        shapes.clear()
        offsets.clear()
        dx = 0f
        dy = 0f
        clips = 0
        alphas = 0
    }

    override val width = AppCanvas.WIDTH
    override val height = AppCanvas.HEIGHT
    override val pixelsPerDp = 1.5f

    override fun measure(text: String, style: TextStyle) = TextMetrics.measure(text, style)
    override fun truncate(text: String, style: TextStyle, maxWidth: Float) = TextMetrics.truncate(text, style, maxWidth)
    override fun wrap(text: String, style: TextStyle, maxWidth: Float) = TextMetrics.wrap(text, style, maxWidth)
    override fun wrap(text: String, style: TextStyle, maxWidth: Float, maxLines: Int) = TextMetrics.wrap(text, style, maxWidth, maxLines)

    private fun shape(kind: DrawnShape.Kind, x: Float, y: Float, w: Float, h: Float, color: Int) {
        shapes += DrawnShape(kind, dx + x, dy + y, w, h, color)
    }

    override fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) = shape(DrawnShape.Kind.RECT, x, y, w, h, color)

    override fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) =
        shape(DrawnShape.Kind.ROUND_RECT, x, y, w, h, color)

    override fun fillCircle(x: Float, y: Float, diameter: Float, color: Int) = shape(DrawnShape.Kind.CIRCLE, x, y, diameter, diameter, color)

    override fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, color: Int) {}
    override fun hairline(x0: Float, x1: Float, y: Float, color: Int) {}
    override fun verticalHairline(x: Float, y0: Float, y1: Float, color: Int) {}

    override fun drawText(text: String, x: Float, y: Float, style: TextStyle, color: Int): Float {
        texts += DrawnText(text, dx + x, dy + y, style, color)
        return measure(text, style)
    }

    override fun drawIcon(icon: Icon, x: Float, y: Float, color: Int, size: Float) {}
    override fun drawSpinner(x: Float, y: Float, size: Float, color: Int, turns: Float) {}
    override fun drawTexture(
        texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float,
        color: Int, u0: Float, v0: Float, u1: Float, v1: Float, linear: Boolean,
    ) {}
    override fun drawPlayerFace(skin: ResourceLocation, x: Float, y: Float, size: Float) {}
    override fun drawShadow(shadow: Shadow, x: Float, y: Float, w: Float, h: Float) {}
    override fun pushClip(x: Float, y: Float, w: Float, h: Float) {
        clips++
    }

    override fun popClip() {
        check(clips-- > 0) { "popClip without a pushClip" }
    }

    override fun pushOffset(dx: Float, dy: Float) {
        offsets.addLast(this.dx to this.dy)
        this.dx += dx
        this.dy += dy
    }

    override fun popOffset() {
        check(offsets.isNotEmpty()) { "popOffset without a pushOffset" }
        val (x, y) = offsets.removeLast()
        dx = x
        dy = y
    }

    override fun pushAlpha(alpha: Float) {
        alphas++
    }

    override fun popAlpha() {
        check(alphas-- > 0) { "popAlpha without a pushAlpha" }
    }

    /** Throws if the frame left a clip, an offset or an opacity pushed: the game would draw what follows wrongly. */
    fun checkBalanced() {
        check(clips == 0 && alphas == 0 && offsets.isEmpty()) {
            "The frame left pushed: $clips clips, ${offsets.size} offsets, $alphas opacities"
        }
    }
}

/** Every character half an em wide, no kerning: 6.5 dp in body text, whatever the character. */
internal object MonospaceFont : FontMetrics {
    override fun advance(codePoint: Int) = 0.5f
    override fun kerning(left: Int, right: Int) = 0f
    override val ascender = 0.8f
    override val descender = 0.2f
}

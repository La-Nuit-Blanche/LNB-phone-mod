package fr.bde_teknantes.lnb.phone.client.notifications

import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import fr.bde_teknantes.lnb.phone.api.client.PhoneApp
import fr.bde_teknantes.lnb.phone.client.icons.AppIconTextures
import fr.bde_teknantes.lnb.phone.client.smooth.FontWeight
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothResources
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles
import fr.bde_teknantes.lnb.phone.notifications.PhoneNotification
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.roundToInt

/** The banner's states (10.1). Hover and pressed exist only over the open phone, where it can be clicked. */
enum class BannerLook { NORMAL, HOVER, PRESSED }

/**
 * What one banner shows: the app's icon, the title and the text, resolved in the player's language and made drawable
 * ([BannerText]). Laid out on first draw, when the fonts are at hand.
 */
class BannerContent(val icon: AppIcon, title: String, text: String) {
    val title: String = BannerText.clean(title) { hasGlyph(FontWeight.SEMIBOLD, it) }
    val text: String = BannerText.clean(text) { hasGlyph(FontWeight.REGULAR, it) }

    private var laidOut: Pair<String, List<String>>? = null

    /** The title cut to its slot and the text on its two lines. Render thread (it reads the fonts). */
    fun layout(): Pair<String, List<String>> = laidOut ?: Pair(
        BannerLayout.title(title, SmoothResources.font(FontWeight.SEMIBOLD)),
        BannerLayout.textLines(text, SmoothResources.font(FontWeight.REGULAR)),
    ).also { laidOut = it }

    companion object {
        /** The banner of [notification], from [app] (the app it names, installed on this client). */
        fun of(app: PhoneApp, notification: PhoneNotification) =
            BannerContent(app.icon, notification.title.string, notification.text.string)

        private fun hasGlyph(weight: FontWeight, codePoint: Int) = SmoothResources.font(weight).glyphId(codePoint) != 0
    }
}

/**
 * Draws the notification banner of art direction 10.1 on the smooth layer: `shadow/sheet` under a 240 x 76 dp card
 * (radius 14, `surface-2`, 1 dp `banner-edge` inner stroke), the app's pixel icon at native size, the title and two
 * lines of text. The same drawing serves the open phone (10.3) and the peek (10.2); where it sits and how it moves is
 * the caller's ([BannerLayout], [BannerTimeline]).
 *
 * Render thread only. Holds a canvas of its own for [render]; [draw] uses the caller's.
 */
class NotificationBanner {
    private val canvas = SmoothCanvas()

    /**
     * Draws the banner with its top-left at ([x], [y]) dp in [canvas], which the caller has begun. Everything is
     * clipped by the canvas: give it the viewport (or the peeking screen) and the banner slides out of view cleanly.
     *
     * An [AppIcon.Item] icon draws its tile only here: the item itself is Minecraft item rendering, which [render]
     * adds after the canvas ends.
     *
     * @param progress how far out the banner is ([BannerTimeline.progress]): its shadow fades with it, since the
     * shadow reaches below the card and would otherwise show as a band at the top of the viewport while the banner is
     * hidden above it.
     */
    fun draw(canvas: SmoothCanvas, x: Float, y: Float, content: BannerContent, look: BannerLook, progress: Float = 1f) {
        val m = BannerLayout.SHADOW_MARGIN
        val shadowAlpha = (BannerLayout.shadowOpacity(progress) * 255f).roundToInt()
        if (shadowAlpha > 0) shadow(canvas, x - m, y - m, BannerLayout.WIDTH + 2 * m, BannerLayout.HEIGHT + 2 * m, (shadowAlpha shl 24) or 0xFFFFFF)

        // The pressed overlay and the edge are faint white over an opaque fill, so they are blended here into opaque
        // colours: the same pixels, and the canvas's shader on this branch drops fragments under 10 % alpha. The edge
        // goes back to real alpha once the banner is drawn with the fixed canvas.
        val fill = when (look) {
            BannerLook.NORMAL -> SURFACE_2
            BannerLook.HOVER -> SURFACE_3
            BannerLook.PRESSED -> over(OVERLAY_PRESSED, SURFACE_3)
        }
        canvas.fillRoundRect(x, y, BannerLayout.WIDTH, BannerLayout.HEIGHT, BannerLayout.RADIUS, fill)
        canvas.strokeRoundRect(x, y, BannerLayout.WIDTH, BannerLayout.HEIGHT, BannerLayout.RADIUS, BannerLayout.EDGE, over(BANNER_EDGE, fill))

        // Pixel art: nearest filtering, one texel per GUI px (2 dp).
        canvas.image(
            AppIconTextures.texture(content.icon),
            x + BannerLayout.ICON_X, y + BannerLayout.ICON_Y, BannerLayout.ICON_SIZE, BannerLayout.ICON_SIZE,
            0f, 0f, 1f, 1f, WHITE, linear = false,
        )

        val (title, lines) = content.layout()
        canvas.drawText(title, x + BannerLayout.TEXT_X, y + BannerLayout.TITLE_Y, TextStyles.TITLE_M, TEXT_1)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, x + BannerLayout.TEXT_X, y + BannerLayout.TEXT_Y + i * TextStyles.BODY.lineHeight, TextStyles.BODY, TEXT_2)
        }
    }

    /**
     * Draws the banner at ([x], [y]) dp inside the rectangle at ([left], [top]), [widthGui] x [heightGui] GUI px, which
     * clips it: the app viewport over the open phone, or the screen of the peeking phone. Begins and ends a canvas of
     * its own, so call it outside any other canvas (after the app's). [progress] as for [draw].
     */
    fun render(
        graphics: GuiGraphics, left: Int, top: Int, widthGui: Int, heightGui: Int,
        x: Float, y: Float, content: BannerContent, look: BannerLook, progress: Float = 1f,
    ) {
        canvas.begin(graphics, left, top, widthGui, heightGui)
        try {
            draw(canvas, x, y, content, look, progress)
        } finally {
            canvas.end()
        }
        val icon = content.icon as? AppIcon.Item ?: return
        // The item sits at (2, 2) GUI px on its 20 px tile, which is at (12, 18) dp in the banner; whole GUI px, as
        // the pixel shell rounds positions (14).
        val itemX = left + ((x + BannerLayout.ICON_X) / 2f).roundToInt() + 2
        val itemY = top + ((y + BannerLayout.ICON_Y) / 2f).roundToInt() + 2
        graphics.enableScissor(left, top, left + widthGui, top + heightGui)
        graphics.renderItem(icon.stack, itemX, itemY)
        graphics.disableScissor()
    }

    private fun shadow(canvas: SmoothCanvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        // Nine-slice from UVs: corners of SHADOW_BORDER dp kept, edges and middle stretched.
        val b = BannerLayout.SHADOW_BORDER
        val uv = b / BannerLayout.SHADOW_SPRITE
        val xs = floatArrayOf(x, x + b, x + w - b, x + w)
        val ys = floatArrayOf(y, y + b, y + h - b, y + h)
        val us = floatArrayOf(0f, uv, 1f - uv, 1f)
        for (row in 0..2) for (col in 0..2) {
            canvas.image(
                SHEET_SHADOW, xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                us[col], us[row], us[col + 1], us[row + 1], color, linear = true,
            )
        }
    }

    internal companion object {
        /** [top] (ARGB with alpha) over the opaque [bottom]: the opaque colour the eye gets. */
        fun over(top: Int, bottom: Int): Int {
            val a = top ushr 24
            fun channel(shift: Int) = (((top shr shift) and 0xFF) * a + ((bottom shr shift) and 0xFF) * (255 - a) + 127) / 255
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        private val SHEET_SHADOW: ResourceLocation = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "textures/gui/smooth/shadow/sheet.png")

        // App layer tokens (8.3), ARGB.
        private const val SURFACE_2 = 0xFF242A34.toInt()
        private const val SURFACE_3 = 0xFF313846.toInt()
        private const val OVERLAY_PRESSED = 0x14FFFFFF
        private const val BANNER_EDGE = 0x0FFFFFFF
        private const val TEXT_1 = 0xFFEEF1F5.toInt()
        private const val TEXT_2 = 0xFF96A0B0.toInt()
        private const val WHITE = -1
    }
}

package fr.bde_teknantes.lnb.phone.demo.client.smooth

import com.mojang.blaze3d.systems.RenderSystem
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothCanvas
import fr.bde_teknantes.lnb.phone.client.smooth.SmoothFont
import fr.bde_teknantes.lnb.phone.client.smooth.TextStyles
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import kotlin.math.max

/**
 * Test sheet for the smooth layer (task 3), laid out in 640 x 480 dp so it fits the window at GUI scale 4: a Feed
 * column at the real viewport width (header, two post cards, a clipping test) and, on the right, the type ramp, text
 * rendering variants, shapes, avatars, icons, and native masks next to the art direction's 2x sprites.
 */
class SmoothTestScreen(private val abandonOnce: Boolean = false) : Screen(Component.literal("Smooth layer test")) {
    private val canvas = SmoothCanvas()
    private var frames = 0
    private var firstFrameStart: GlStateSnapshot? = null

    override fun isPauseScreen() = false

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Over a world, leave it visible around the panels (the phone never darkens or blurs it either).
        if (minecraft!!.level == null) graphics.fill(0, 0, width, height, 0xFF0B0D11.toInt())
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (abandonOnce) checkAbandonedCanvas(graphics)
        renderBackground(graphics, mouseX, mouseY, partialTick)
        // Inside a scissor of the caller's, with an odd shader colour, to check the canvas puts both back.
        graphics.enableScissor(0, 0, width, height)
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.999f)
        val before = GlStateSnapshot.take(graphics)
        canvas.begin(graphics, 0, 0, width, height)
        canvas.fillRect(0f, 0f, 256f, 480f, BG)
        canvas.fillRect(264f, 0f, 376f, 480f, BG)
        feedColumn()
        typeRamp()
        textVariants()
        shapes()
        canvas.end()
        val after = GlStateSnapshot.take(graphics)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        graphics.disableScissor()
        if (frames == 0 && before != after) LnbPhone.LOGGER.error("Smooth canvas changed the render state: {} -> {}", before, after)
        if (frames == 0 && before == after) LnbPhone.LOGGER.info("Smooth canvas left the render state as it found it")
        if (frames == 0) checkFaintOverlay()
        minecraftTtfRow(graphics)
        if (abandonOnce && frames == 0) abandonCanvas(graphics)
        logCost()
    }

    /** An app failing mid-draw, caught above the canvas without reaching end(): its scissor is left on. */
    private fun abandonCanvas(graphics: GuiGraphics) {
        try {
            SmoothCanvas().begin(graphics, 8, 8, 40, 40)
            throw IllegalStateException("app failure")
        } catch (_: IllegalStateException) {
        }
    }

    /** On the next frame, before anything is drawn, the state must be back as it was at the start of the first. */
    private fun checkAbandonedCanvas(graphics: GuiGraphics) {
        val now = GlStateSnapshot.take(graphics)
        val first = firstFrameStart
        if (first == null) {
            firstFrameStart = now
        } else if (frames == 1) {
            val recovered = now.scissor == first.scissor && now.blend == first.blend && now.depthTest == first.depthTest
            if (recovered) {
                LnbPhone.LOGGER.info("Abandoned smooth canvas was ended at the end of the frame")
            } else {
                LnbPhone.LOGGER.error("Abandoned smooth canvas still clips the next frame: {} -> {}", first, now)
            }
        }
    }

    // ---- Feed column, x 0..256 ----

    private class Post(
        val skin: () -> ResourceLocation,
        val name: String,
        val handle: String?,
        val time: String,
        val text: String,
        val replies: String,
        val likes: String,
        val announcer: Boolean,
        val moreButton: Boolean,
    )

    private val posts = listOf(
        Post(
            { SampleSkins.ALEX }, "Alexandre Morel", "@Alex_Craft", "2 min",
            "Ça y est : la fête au château commence à 21:00, près du cœur de la forêt. Où êtes-vous ? Prenez des pâtes !",
            "3", "12", announcer = true, moreButton = false,
        ),
        Post(
            { minecraft!!.skinManager.getInsecureSkin(minecraft!!.gameProfile).texture() },
            "Marie-Éloïse de La Tour-Dupont", "@Mel_Builds", "1 h",
            "J'ouvre ma boutique ce soir à 21:00 près de la porte nord. Kits redstone, livres enchantés et teintures rares.",
            "", "8", announcer = false, moreButton = true,
        ),
    )

    private fun feedColumn() {
        val c = canvas
        // App header (art direction 8.7): back button, title, bottom hairline.
        c.icon(BACK_2X, 12f, 12f, 20f, TEXT_1)
        c.drawText("Feed", 44f, 11f, TextStyles.TITLE_L, TEXT_1)
        c.hairline(0f, 256f, 43f, HAIRLINE)

        var y = 44f
        for (post in posts) y = postCard(y, post)

        // Clipping: a long line and a disc cut at the clip rectangle, outlined in blue.
        val top = y + 12f
        c.strokeRoundRect(12f, top, 232f, 40f, 10f, 1f, STROKE)
        c.pushClip(24f, top + 4f, 208f, 32f)
        c.fillRect(24f, top + 4f, 208f, 32f, SURFACE)
        c.drawText("Ce texte dépasse du cadre et doit être coupé net au bord droit.", 30f, top + 11f, TextStyles.BODY, TEXT_1)
        c.fillCircle(212f, top - 8f, 40f, ACCENT_SOFT_2)
        c.popClip()
        c.drawText("pushClip(24, y+4, 208, 32)", 12f, top + 44f, TextStyles.CAPTION, TEXT_3)

        // The kit's faintest fills (art direction 8.3) on bg: overlay-hover 4%, overlay-pressed 8%, banner-edge 6%.
        for ((i, overlay) in FAINT_OVERLAYS.withIndex()) c.fillRect(FAINT_X + i * 36f, FAINT_Y, 28f, 16f, overlay)
        c.drawText("4 % · 8 % · 6 %", FAINT_X + 108f, FAINT_Y, TextStyles.CAPTION, TEXT_3)
    }

    /**
     * Reads back the 4% overlay and the bg beside it: the same pixel means faint fills are lost, as they were under
     * vanilla's shader, which discards everything under 10% alpha.
     */
    private fun checkFaintOverlay() {
        val grid = canvas.grid
        val y = minecraft!!.window.height - 1 - grid.snap(FAINT_Y + 8f)
        val overlay = readPixel(grid.snap(FAINT_X + 14f), y)
        val background = readPixel(grid.snap(FAINT_X - 6f), y)
        if (overlay == background) {
            LnbPhone.LOGGER.error("Smooth canvas lost a 4% overlay: {} on bg {}", Integer.toHexString(overlay), Integer.toHexString(background))
        } else {
            LnbPhone.LOGGER.info("Smooth canvas drew a 4% overlay: {} on bg {}", Integer.toHexString(overlay), Integer.toHexString(background))
        }
    }

    private fun readPixel(x: Int, y: Int): Int {
        val out = IntArray(1)
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, out)
        return out[0] and 0xFFFFFF
    }

    /** A post card per art direction 9.3; returns the y of the next cell. */
    private fun postCard(y0: Float, post: Post): Float {
        val c = canvas
        c.playerFace(post.skin(), 12f, y0 + 10f, 32f)

        val timeRight = if (post.moreButton) 214f else 244f
        val timeX = timeRight - c.measure(post.time, TextStyles.CAPTION)
        val nameMax = timeX - 8f - (if (post.announcer) 20f else 0f) - 56f
        val nameWidth = c.drawTextTruncated(post.name, 56f, y0 + 10f, nameMax, TextStyles.TITLE_M, TEXT_1)
        if (post.announcer) c.fillCircle(56f + nameWidth + 4f, y0 + 11f, 16f, ANNOUNCER)
        c.drawText(post.time, timeX, y0 + 11f, TextStyles.CAPTION, TEXT_2)
        if (post.moreButton) {
            // Hovered "more" button: disc plus three dots.
            c.fillCircle(220f, y0 + 5f, 28f, OVERLAY_PRESSED)
            for (i in -1..1) c.fillCircle(232f + i * 5f, y0 + 17.5f, 3f, TEXT_2)
        }

        var y = y0 + 28f
        post.handle?.let {
            c.drawText(it, 56f, y, TextStyles.CAPTION, TEXT_2)
            y += 16f
        }
        y += 2f
        y += c.drawTextWrapped(post.text, 56f, y, 188f, TextStyles.BODY, TEXT_1) * TextStyles.BODY.lineHeight
        y += 4f

        // Action row, 28 dp: reply (a drawn bubble stands in for the icon), then a liked, hovered heart.
        c.strokeRoundRect(57f, y + 8f, 14f, 12f, 4f, 1.5f, TEXT_2)
        if (post.replies.isNotEmpty()) c.drawText(post.replies, 78f, y + 6f, TextStyles.CAPTION, TEXT_2)
        c.fillCircle(114f, y, 28f, LIKE_SOFT)
        c.icon(HEART_2X, 120f, y + 6f, 16f, LIKE)
        c.drawText(post.likes, 142f, y + 6f, TextStyles.CAPTION, LIKE)
        y += 28f + 4f
        c.hairline(0f, 256f, y, HAIRLINE)
        return y + 1f
    }

    // ---- Right column, x 272..640 ----

    private fun typeRamp() {
        val ramp = listOf(
            "title-l" to TextStyles.TITLE_L, "title-m" to TextStyles.TITLE_M, "body" to TextStyles.BODY,
            "body-strong" to TextStyles.BODY_STRONG, "label" to TextStyles.LABEL, "caption" to TextStyles.CAPTION,
            "caption-strong" to TextStyles.CAPTION_STRONG, "micro" to TextStyles.MICRO,
        )
        var y = 6f
        for ((name, style) in ramp) {
            label(name, y + (style.lineHeight - 16f) / 2f)
            canvas.drawText("Ça œuvre où, Noël ? à 21:00", SAMPLE_X, y, style, TEXT_1)
            y += style.lineHeight + 2f
        }
    }

    private fun textVariants() {
        val sample = "Premier arrivé, premier servi ! Où ça ?"
        TEXT_VARIANTS.forEachIndexed { i, (name, hinting, phases) ->
            val y = VARIANTS_Y + i * 18f
            label(name, y + 1f)
            canvas.hinting = hinting
            canvas.subpixelPhases = phases
            canvas.drawText(sample, SAMPLE_X, y, TextStyles.BODY, TEXT_1)
        }
        canvas.hinting = SmoothFont.Hinting.LIGHT
        canvas.subpixelPhases = 1
        // The last row is drawn by Minecraft's own TTF provider, after the canvas: see minecraftTtfRow.
        label("MC ttf", VARIANTS_Y + TEXT_VARIANTS.size * 18f + 1f)
    }

    private fun shapes() {
        val c = canvas
        // Row 1: kit shapes.
        val r1 = 282f
        c.fillRoundRect(272f, r1 + 8f, 40f, 32f, 10f, SURFACE_3)
        c.strokeRoundRect(320f, r1 + 8f, 40f, 32f, 10f, 1f, STROKE)
        c.fillRoundRect(368f, r1 + 4f, 48f, 40f, 14f, SURFACE_2)
        button(424f, r1 + 8f, "Publier", primary = true)
        button(504f, r1 + 8f, "Annuler", primary = false)
        c.fillRoundRect(584f, r1 + 14f, 36f, 20f, 10f, ACCENT)
        c.fillCircle(602f, r1 + 16f, 16f, 0xFFFFFFFF.toInt())

        // Row 2: discs and badges, avatars, icons.
        val r2 = 338f
        c.fillCircle(272f, r2 + 21f, 6f, ACCENT)
        val badge = "12"
        val badgeWidth = max(16f, c.measure(badge, TextStyles.MICRO) + 8f)
        c.fillRoundRect(286f, r2 + 16f, badgeWidth, 16f, 8f, BADGE)
        c.drawText(badge, 286f + (badgeWidth - c.measure(badge, TextStyles.MICRO)) / 2f, r2 + 17f, TextStyles.MICRO, 0xFFFFFFFF.toInt())
        c.fillCircle(314f, r2 + 10f, 28f, ACCENT_SOFT_2)
        c.fillCircle(350f, r2, 48f, ACCENT)
        c.playerFace(SampleSkins.STEVE, 410f, r2 + 16f, 16f)
        c.playerFace(SampleSkins.ALEX, 434f, r2 + 8f, 32f)
        c.playerFace(minecraft!!.skinManager.getInsecureSkin(minecraft!!.gameProfile).texture(), 474f, r2, 48f)
        // Icons: the 2x sprite filtered down (art direction 8.1) next to the export made for this density, drawn 1:1.
        val density = when (c.grid.guiScale) {
            2 -> "1x"
            3 -> "1_5x"
            else -> "2x"
        }
        c.icon(HEART_2X, 532f, r2 + 16f, 16f, TEXT_1)
        c.image(spike("heart_$density"), 556f, r2 + 16f, 16f, 16f, 0f, 0f, 1f, 1f, TEXT_1, linear = false)
        c.icon(BACK_2X, 584f, r2 + 14f, 20f, TEXT_1)
        c.image(spike("back_$density"), 612f, r2 + 14f, 20f, 20f, 0f, 0f, 1f, 1f, TEXT_1, linear = false)

        // Row 3: native masks (left of each pair) against the 2 px per dp sprites of art direction 16.2 (right).
        val r3 = 394f
        val pill = spike("shape_pill")
        c.fillRoundRect(272f, r3 + 8f, 40f, 32f, 10f, TEXT_1)
        spriteNineSlice(spike("shape_r10"), 24f, 10f, 318f, r3 + 8f, 40f, 32f, TEXT_1)
        c.strokeRoundRect(366f, r3 + 8f, 40f, 32f, 10f, 1f, TEXT_1)
        spriteNineSlice(spike("shape_r10_stroke1"), 24f, 10f, 412f, r3 + 8f, 40f, 32f, TEXT_1)
        c.fillCircle(460f, r3, 48f, TEXT_1)
        c.image(pill, 512f, r3, 48f, 48f, 0f, 0f, 1f, 1f, TEXT_1, linear = true)
        c.fillCircle(568f, r3 + 21f, 6f, TEXT_1)
        c.image(pill, 578f, r3 + 21f, 6f, 6f, 0f, 0f, 1f, 1f, TEXT_1, linear = true)
        c.fillCircle(592f, r3 + 16f, 16f, TEXT_1)
        c.image(pill, 612f, r3 + 16f, 16f, 16f, 0f, 0f, 1f, 1f, TEXT_1, linear = true)
        c.drawText("Gauche : masque natif · droite : sprite 2 px/dp filtré", 272f, r3 + 52f, TextStyles.CAPTION, TEXT_3)

        val cost = "GUI ${c.grid.guiScale} · 1 dp = ${c.grid.pxPerDp} px · ${c.lastFrameNanos / 1000} µs, ${c.lastFrameDrawCalls} draws"
        c.drawText(cost, 272f, r3 + 68f, TextStyles.CAPTION, TEXT_3)
    }

    private fun button(x: Float, y: Float, text: String, primary: Boolean) {
        val c = canvas
        if (primary) c.fillRoundRect(x, y, 72f, 32f, 16f, ACCENT) else c.strokeRoundRect(x, y, 72f, 32f, 16f, 1f, STROKE)
        val w = c.measure(text, TextStyles.LABEL)
        c.drawText(text, x + (72f - w) / 2f, y + 8f, TextStyles.LABEL, if (primary) 0xFFFFFFFF.toInt() else TEXT_1)
    }

    /**
     * The art direction's way: a white sprite at 2 px per dp, nine-sliced with linear filtering. [border] is the
     * corner size on screen in dp, [textureBorder] the same corner in the sprite (dp at 2 px per dp).
     */
    private fun spriteNineSlice(
        texture: ResourceLocation, textureDp: Float, border: Float,
        x: Float, y: Float, w: Float, h: Float, color: Int, textureBorder: Float = border,
    ) {
        val xs = floatArrayOf(x, x + border, x + w - border, x + w)
        val ys = floatArrayOf(y, y + border, y + h - border, y + h)
        val b = textureBorder / textureDp
        val us = floatArrayOf(0f, b, 1f - b, 1f)
        for (row in 0..2) for (col in 0..2) {
            if (xs[col + 1] <= xs[col] || ys[row + 1] <= ys[row]) continue
            canvas.image(texture, xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row], us[col], us[row], us[col + 1], us[row + 1], color, linear = true)
        }
    }

    private fun label(text: String, y: Float) = canvas.drawText(text, 272f, y, TextStyles.CAPTION, TEXT_3)

    /** The same sample through Minecraft's TTF provider: one font definition per GUI scale, 13 dp = 6.5 GUI px. */
    private fun minecraftTtfRow(graphics: GuiGraphics) {
        val scale = canvas.grid.guiScale
        if (scale !in 2..4) return
        val font = ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "mc_ttf_gui$scale")
        val text = Component.literal("Premier arrivé, premier servi ! Où ça ?").withStyle(Style.EMPTY.withFont(font))
        val top = VARIANTS_Y + TEXT_VARIANTS.size * 18f
        graphics.drawString(this.font, text, (SAMPLE_X / 2f).toInt(), (top / 2f).toInt() + 1, TEXT_1, false)
    }

    private fun logCost() {
        frames++
        if (frames == 1 || frames % 60 == 0) {
            LnbPhone.LOGGER.info(
                "Smooth test frame {} at GUI {}: {} us, {} draw calls", frames, canvas.grid.guiScale,
                canvas.lastFrameNanos / 1000, canvas.lastFrameDrawCalls,
            )
        }
    }

    private companion object {
        const val SAMPLE_X = 364f
        const val VARIANTS_Y = 166f
        const val FAINT_X = 12f
        const val FAINT_Y = 456f
        val FAINT_OVERLAYS = intArrayOf(0x0AFFFFFF, 0x14FFFFFF, 0x0FFFFFFF)

        val TEXT_VARIANTS = listOf(
            Triple("none · 1 px", SmoothFont.Hinting.NONE, 1),
            Triple("none · ¼ px", SmoothFont.Hinting.NONE, 4),
            Triple("light · 1 px", SmoothFont.Hinting.LIGHT, 1),
            Triple("light · ¼ px", SmoothFont.Hinting.LIGHT, 4),
            Triple("native · 1 px", SmoothFont.Hinting.NATIVE, 1),
        )

        fun spike(name: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "textures/gui/spike/$name.png")

        val HEART_2X = spike("heart_2x")
        val BACK_2X = spike("back_2x")

        // Art direction 8.3, as ARGB.
        val BG = 0xFF12151B.toInt()
        val SURFACE = 0xFF1A1E26.toInt()
        val SURFACE_2 = 0xFF242A34.toInt()
        val SURFACE_3 = 0xFF313846.toInt()
        val STROKE = 0xFF3A4250.toInt()
        val HAIRLINE = 0xFF2A303B.toInt()
        val TEXT_1 = 0xFFEEF1F5.toInt()
        val TEXT_2 = 0xFF96A0B0.toInt()
        val TEXT_3 = 0xFF5C6575.toInt()
        val ACCENT = 0xFF3A8DF0.toInt()
        val ACCENT_SOFT_2 = 0x333A8DF0
        val LIKE = 0xFFF2545B.toInt()
        val LIKE_SOFT = 0x1FF2545B
        val ANNOUNCER = 0xFF7A5CE6.toInt()
        val BADGE = 0xFFE5483C.toInt()
        val OVERLAY_PRESSED = 0x14FFFFFF
    }
}

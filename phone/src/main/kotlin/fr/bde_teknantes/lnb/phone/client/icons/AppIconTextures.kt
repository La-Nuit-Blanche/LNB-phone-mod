package fr.bde_teknantes.lnb.phone.client.icons

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/**
 * App icons composed once ([IconComposer]) into small 20 x 20 textures, frame included, one per tile and glyph, to draw
 * anywhere with nearest filtering: on the home screen ([draw]) as on the notification banner (inside the smooth layer,
 * with [texture]). For an [AppIcon.Item], the texture is the bare tile and the item goes over it.
 *
 * Dropped on resource reload, since a resource pack may change the glyphs. Render thread only.
 */
object AppIconTextures : ResourceManagerReloadListener {
    private val textures = HashMap<Any, ResourceLocation>()
    private val warned = HashSet<ResourceLocation>()
    private var next = 0

    /** Draws [icon] at 20 x 20 GUI px with its top-left pixel at ([x], [y]), in the current pose (art direction 7). */
    fun draw(graphics: GuiGraphics, icon: AppIcon, x: Int, y: Int) {
        graphics.blit(texture(icon), x, y, 0f, 0f, IconComposer.SIZE, IconComposer.SIZE, IconComposer.SIZE, IconComposer.SIZE)
        // The item in place of a glyph, 16 x 16 at (2, 2). Item rendering draws 150 above the pose: callers putting
        // something over icons (badges) go higher.
        if (icon is AppIcon.Item) graphics.renderFakeItem(icon.stack, x + ITEM_AT, y + ITEM_AT)
    }

    private const val ITEM_AT = 2

    /** The composed texture of [icon], 20 x 20 texels. */
    fun texture(icon: AppIcon): ResourceLocation = textures.getOrPut(cacheKey(icon)) { upload(pixels(icon, ::glyphPixels)) }

    /** What [icon]'s texture is kept under: its tile and glyph, so that new [AppIcon] instances share it; an item's, its tile. */
    internal fun cacheKey(icon: AppIcon): Any = when (icon) {
        is AppIcon.Glyph -> icon.tile to icon.texture
        is AppIcon.Item -> icon.tile
    }

    /** [icon]'s 20 x 20 ARGB pixels, its glyph read with [readGlyph]; an item's is the bare tile, the item going over it. */
    internal fun pixels(icon: AppIcon, readGlyph: (ResourceLocation) -> IntArray?): IntArray =
        IconComposer.compose(icon.tile, (icon as? AppIcon.Glyph)?.let { readGlyph(it.texture) })

    /**
     * A glyph image's pixels as ARGB, row by row, from [abgr] (NativeImage's order) at each pixel; null when the image
     * isn't 16 x 16.
     */
    internal fun glyphFromImage(width: Int, height: Int, abgr: (Int, Int) -> Int): IntArray? {
        if (width != IconComposer.GLYPH_SIZE || height != IconComposer.GLYPH_SIZE) return null
        return IntArray(IconComposer.GLYPH_SIZE * IconComposer.GLYPH_SIZE) { i ->
            swapRedBlue(abgr(i % IconComposer.GLYPH_SIZE, i / IconComposer.GLYPH_SIZE))
        }
    }

    /** The glyph's 16 x 16 pixels as ARGB, or null (and a warning, once) when it is missing or the wrong size. */
    private fun glyphPixels(location: ResourceLocation): IntArray? {
        val image = try {
            Minecraft.getInstance().resourceManager.getResource(location).orElse(null)?.open()?.use(NativeImage::read)
        } catch (e: Exception) {
            warnOnce(location, "can't be read: $e")
            return null
        } ?: run {
            warnOnce(location, "is missing")
            return null
        }
        image.use {
            val pixels = glyphFromImage(it.width, it.height, it::getPixelRGBA)
            if (pixels == null) warnOnce(location, "is ${it.width} x ${it.height} pixels instead of 16 x 16")
            return pixels
        }
    }

    private fun upload(pixels: IntArray): ResourceLocation {
        val image = NativeImage(IconComposer.SIZE, IconComposer.SIZE, false)
        for (i in pixels.indices) image.setPixelRGBA(i % IconComposer.SIZE, i / IconComposer.SIZE, swapRedBlue(pixels[i]))
        val location = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "dynamic/app_icon_${next++}")
        Minecraft.getInstance().textureManager.register(location, DynamicTexture(image))
        return location
    }

    private fun warnOnce(location: ResourceLocation, problem: String) {
        if (warned.add(location)) LnbPhone.LOGGER.warn("App icon glyph {} {}; the icon shows its tile only", location, problem)
    }

    // NativeImage stores pixels as ABGR; the composer works in ARGB. The swap goes both ways.
    private fun swapRedBlue(color: Int): Int =
        (color and 0xFF00FF00.toInt()) or ((color shr 16) and 0xFF) or ((color and 0xFF) shl 16)

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val manager = Minecraft.getInstance().textureManager
        textures.values.forEach(manager::release)
        textures.clear()
        warned.clear()
    }
}

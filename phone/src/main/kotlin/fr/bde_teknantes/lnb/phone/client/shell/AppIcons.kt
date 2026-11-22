package fr.bde_teknantes.lnb.phone.client.shell

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import java.util.IdentityHashMap

/**
 * Draws app icons, 20 x 20 GUI px, pixel for pixel (art direction 7): on the home screen, and on the notification
 * banner. Each icon is composed once ([IconComposer]) into a small texture of its own, kept until resources reload.
 * Render thread only.
 */
internal object AppIcons : ResourceManagerReloadListener {
    private val FRAME = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "home/icon_frame")
    private val textures = IdentityHashMap<AppIcon, ResourceLocation>()
    private val failedGlyphs = HashSet<ResourceLocation>()
    private var next = 0

    /** Draws [icon] with its top-left pixel at ([x], [y]), in the current pose. */
    fun draw(graphics: GuiGraphics, icon: AppIcon, x: Int, y: Int) {
        graphics.blit(texture(icon), x, y, 0f, 0f, IconComposer.SIZE, IconComposer.SIZE, IconComposer.SIZE, IconComposer.SIZE)
        graphics.blitSprite(FRAME, x, y, IconComposer.SIZE, IconComposer.SIZE)
        if (icon is AppIcon.Item) {
            graphics.renderFakeItem(icon.stack, x + IconComposer.GLYPH_OFFSET, y + IconComposer.GLYPH_OFFSET)
        }
    }

    private fun texture(icon: AppIcon): ResourceLocation = textures.getOrPut(icon) {
        val glyph = (icon as? AppIcon.Glyph)?.let { readGlyph(it.texture) }
        val pixels = IconComposer.compose(icon.tile, glyph)
        val image = NativeImage(IconComposer.SIZE, IconComposer.SIZE, true)
        for (y in 0 until IconComposer.SIZE) for (x in 0 until IconComposer.SIZE) {
            image.setPixelRGBA(x, y, argbToAbgr(pixels[y * IconComposer.SIZE + x]))
        }
        val location = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "dynamic/app_icon_${next++}")
        Minecraft.getInstance().textureManager.register(location, DynamicTexture(image))
        location
    }

    /** The glyph's 16 x 16 pixels as ARGB, or null (logged once) when it can't be read: the tile is drawn bare. */
    private fun readGlyph(location: ResourceLocation): IntArray? {
        try {
            val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
                ?: throw IllegalStateException("no such resource")
            NativeImage.read(resource.open()).use { image ->
                if (image.width < IconComposer.GLYPH_SIZE || image.height < IconComposer.GLYPH_SIZE) {
                    throw IllegalStateException("${image.width} x ${image.height} pixels, smaller than 16 x 16")
                }
                if ((image.width != IconComposer.GLYPH_SIZE || image.height != IconComposer.GLYPH_SIZE) && failedGlyphs.add(location)) {
                    LnbPhone.LOGGER.warn("App glyph {} is {} x {}; the phone uses its top-left 16 x 16 pixels", location, image.width, image.height)
                }
                val size = IconComposer.GLYPH_SIZE
                return IntArray(size * size) { abgrToArgb(image.getPixelRGBA(it % size, it / size)) }
            }
        } catch (e: Exception) {
            if (failedGlyphs.add(location)) LnbPhone.LOGGER.error("Couldn't read app glyph {}; its icon shows the tile only", location, e)
            return null
        }
    }

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val textureManager = Minecraft.getInstance().textureManager
        textures.values.forEach(textureManager::release)
        textures.clear()
        failedGlyphs.clear()
    }

    // NativeImage keeps pixels as ABGR.
    private fun argbToAbgr(argb: Int): Int = (argb and 0xFF00FF00.toInt()) or ((argb shr 16) and 0xFF) or ((argb and 0xFF) shl 16)

    private fun abgrToArgb(abgr: Int): Int = argbToAbgr(abgr)
}

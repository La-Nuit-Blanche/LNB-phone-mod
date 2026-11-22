package fr.bde_teknantes.lnb.phone.client.icons

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.phone.LnbPhone
import fr.bde_teknantes.lnb.phone.api.client.AppIcon
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/**
 * App icons composed once ([IconComposer]) into small 20 x 20 textures, one per tile and glyph, to draw anywhere with
 * nearest filtering: on the notification banner (inside the smooth layer) as on the home screen. For an
 * [AppIcon.Item], the texture is the bare tile and the caller draws the item over it.
 *
 * Dropped on resource reload, since a resource pack may change the glyphs. Render thread only.
 */
object AppIconTextures : ResourceManagerReloadListener {
    private val textures = HashMap<Any, ResourceLocation>()
    private val warned = HashSet<ResourceLocation>()
    private var next = 0

    /** The composed texture of [icon], 20 x 20 texels. */
    fun texture(icon: AppIcon): ResourceLocation {
        val key: Any = when (icon) {
            is AppIcon.Glyph -> icon.tile to icon.texture
            is AppIcon.Item -> icon.tile
        }
        return textures.getOrPut(key) {
            val glyph = (icon as? AppIcon.Glyph)?.let { glyphPixels(it.texture) }
            upload(IconComposer.compose(icon.tile, glyph))
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
            if (it.width != IconComposer.GLYPH_SIZE || it.height != IconComposer.GLYPH_SIZE) {
                warnOnce(location, "is ${it.width} x ${it.height} pixels instead of 16 x 16")
                return null
            }
            return IntArray(IconComposer.GLYPH_SIZE * IconComposer.GLYPH_SIZE) { i ->
                swapRedBlue(it.getPixelRGBA(i % IconComposer.GLYPH_SIZE, i / IconComposer.GLYPH_SIZE))
            }
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

package fr.bde_teknantes.lnb.phone.demo.client.smooth

import com.mojang.blaze3d.platform.NativeImage
import fr.bde_teknantes.lnb.phone.demo.DemoApp
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation

/** Skins built from the art direction's sample faces (section 18.3), so test shots don't depend on downloads. */
object SampleSkins {
    private val STEVE_GRID = listOf("HHHHHHHH", "HHHHHHHH", "HSSSSSSH", "SSSSSSSS", "SWBSSBWS", "SSSNNSSS", "SSMSSMSS", "SSMMMMSS")
    private val STEVE_COLORS = mapOf('H' to 0x2F1F0F, 'S' to 0xB4846A, 'W' to 0xFFFFFF, 'B' to 0x523D89, 'N' to 0x87563C, 'M' to 0x6A4030)
    private val ALEX_GRID = listOf("OOOOOOOO", "OOOOOOOO", "OSSSSOOO", "SSSSSSSO", "SWGSSGWO", "SSSSSSSO", "SSSMMSSS", "SSSSSSSS")
    private val ALEX_COLORS = mapOf('O' to 0xD9772B, 'S' to 0xF2C8A0, 'W' to 0xFFFFFF, 'G' to 0x4E8A3A, 'M' to 0xC88E6F)

    val STEVE: ResourceLocation by lazy { register("steve", STEVE_GRID, STEVE_COLORS) }
    val ALEX: ResourceLocation by lazy { register("alex", ALEX_GRID, ALEX_COLORS) }

    private fun register(name: String, grid: List<String>, colors: Map<Char, Int>): ResourceLocation {
        val image = NativeImage(64, 64, true)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val rgb = colors.getValue(grid[y][x])
                // NativeImage is ABGR.
                val abgr = (0xFF shl 24) or ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)
                image.setPixelRGBA(8 + x, 8 + y, abgr)
            }
        }
        val location = ResourceLocation.fromNamespaceAndPath(DemoApp.ID, "dynamic/skin_$name")
        Minecraft.getInstance().textureManager.register(location, DynamicTexture(image))
        return location
    }
}

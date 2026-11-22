package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import fr.bde_teknantes.lnb.phone.LnbPhone
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.client.event.RegisterShadersEvent

/**
 * The smooth layer's shader: vanilla's `position_tex_color` without the cut that discards anything under 10% alpha,
 * which would erase the kit's 4 to 8% overlays, the tails of baked shadows and the faintest anti-aliased pixels.
 */
object SmoothShader {
    private val LOCATION = ResourceLocation.fromNamespaceAndPath(LnbPhone.ID, "smooth")

    private var instance: ShaderInstance? = null

    /** Registered with Minecraft's core shaders, so it is (re)loaded with them on every resource reload. */
    fun register(event: RegisterShadersEvent) {
        event.registerShader(ShaderInstance(event.resourceProvider, LOCATION, DefaultVertexFormat.POSITION_TEX_COLOR)) { instance = it }
    }

    /** The shader, or vanilla's (with its cut) if ours never loaded, rather than drawing nothing. */
    fun get(): ShaderInstance? = instance ?: GameRenderer.getPositionTexColorShader()
}

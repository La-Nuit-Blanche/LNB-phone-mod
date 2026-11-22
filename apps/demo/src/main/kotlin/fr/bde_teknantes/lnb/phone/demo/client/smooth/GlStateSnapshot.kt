package fr.bde_teknantes.lnb.phone.demo.client.smooth

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.GuiGraphics
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14

/**
 * The render state the smooth canvas promises to leave as it found it, read back from GL and RenderSystem, so the
 * test screen can check the promise instead of trusting it.
 */
data class GlStateSnapshot(
    val pose: Matrix4f,
    val scissor: Boolean,
    val scissorBox: List<Int>,
    val blend: Boolean,
    val blendFunc: List<Int>,
    val depthTest: Boolean,
    val shader: String?,
    val shaderTexture: Int,
    val shaderColor: List<Float>,
) {
    companion object {
        fun take(graphics: GuiGraphics): GlStateSnapshot {
            val box = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, it) }
            return GlStateSnapshot(
                pose = Matrix4f(graphics.pose().last().pose()),
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                scissorBox = box.toList(),
                blend = GL11.glIsEnabled(GL11.GL_BLEND),
                blendFunc = listOf(GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA)
                    .map(GL11::glGetInteger),
                depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                shader = RenderSystem.getShader()?.name,
                shaderTexture = RenderSystem.getShaderTexture(0),
                shaderColor = RenderSystem.getShaderColor().toList(),
            )
        }
    }
}

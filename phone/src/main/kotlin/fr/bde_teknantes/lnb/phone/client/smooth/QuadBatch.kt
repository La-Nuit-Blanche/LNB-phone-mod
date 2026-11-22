package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.texture.AbstractTexture
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11

/**
 * Collects tinted, textured quads in real pixels and draws them in as few calls as possible: one per run of quads
 * that share a texture. It has its own vertex memory, so code drawing through Minecraft's shared Tesselator between
 * two of our quads cannot corrupt a batch in progress.
 */
internal class QuadBatch : AutoCloseable {
    private val memory = ByteBufferBuilder(64 * 1024)
    private var builder: BufferBuilder? = null
    private var texture: AbstractTexture? = null
    private var linear: Boolean? = null
    private var beforeDraw: (() -> Unit)? = null

    /** Maps real pixels to the GUI's coordinates; set by the canvas for each frame. */
    var matrix = Matrix4f()

    /** Number of draw calls since the last [resetStats], for the cost report. */
    var drawCalls = 0
        private set

    /**
     * Queues a quad covering pixels [x0]..[x1] x [y0]..[y1] with texture coordinates [u0]..[u1] x [v0]..[v1] (0..1),
     * tinted by [argb]. [prepare] runs once before the run of quads using [tex] is drawn (atlas uploads).
     *
     * [linearFilter] filters [tex] linearly or not for these quads only: the texture's own filter is put back after
     * the draw, since it may be shared (a player's skin, which the tab list then draws). Null leaves the filter
     * alone, for the atlas's own pages.
     */
    fun quad(
        tex: AbstractTexture,
        linearFilter: Boolean?,
        x0: Float, y0: Float, x1: Float, y1: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        argb: Int,
        prepare: (() -> Unit)? = null,
    ) {
        if (tex !== texture || linearFilter != linear) {
            flush()
            texture = tex
            linear = linearFilter
            beforeDraw = prepare
        }
        val b = builder ?: BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR).also { builder = it }
        b.addVertex(matrix, x0, y0, 0f).setUv(u0, v0).setColor(argb)
        b.addVertex(matrix, x0, y1, 0f).setUv(u0, v1).setColor(argb)
        b.addVertex(matrix, x1, y1, 0f).setUv(u1, v1).setColor(argb)
        b.addVertex(matrix, x1, y0, 0f).setUv(u1, v0).setColor(argb)
    }

    /**
     * Queues a quad with free corners, for turned images: [corners] holds the pixel positions of the texture's
     * top-left, bottom-left, bottom-right and top-right corners, as x, y pairs.
     */
    fun quad(tex: AbstractTexture, linearFilter: Boolean, corners: FloatArray, u0: Float, v0: Float, u1: Float, v1: Float, argb: Int) {
        require(corners.size == 8) { "Four corners, as x, y pairs" }
        if (tex !== texture || linearFilter != linear) {
            flush()
            texture = tex
            linear = linearFilter
            beforeDraw = null
        }
        val b = builder ?: BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR).also { builder = it }
        b.addVertex(matrix, corners[0], corners[1], 0f).setUv(u0, v0).setColor(argb)
        b.addVertex(matrix, corners[2], corners[3], 0f).setUv(u0, v1).setColor(argb)
        b.addVertex(matrix, corners[4], corners[5], 0f).setUv(u1, v1).setColor(argb)
        b.addVertex(matrix, corners[6], corners[7], 0f).setUv(u1, v0).setColor(argb)
    }

    fun flush() {
        val b = builder ?: return
        builder = null
        val mesh = b.build() ?: return
        val tex = texture!!
        beforeDraw?.invoke()
        val filter = linear
        // Read from GL, not from AbstractTexture's fields: a texture loaded with "blur" in its .mcmeta has its filter
        // set by the upload, which those fields never hear of.
        var minFilter = 0
        var magFilter = 0
        if (filter != null) {
            RenderSystem.bindTexture(tex.id)
            minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
            magFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER)
            val wanted = if (filter) GL11.GL_LINEAR else GL11.GL_NEAREST
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, wanted)
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, wanted)
        }
        try {
            RenderSystem.setShader(SmoothShader::get)
            RenderSystem.setShaderTexture(0, tex.id)
            BufferUploader.drawWithShader(mesh)
            drawCalls++
        } finally {
            if (filter != null) {
                RenderSystem.bindTexture(tex.id)
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter)
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter)
            }
        }
    }

    /** Drops the quads queued since the last flush, without drawing them. */
    fun discard() {
        val b = builder ?: return
        builder = null
        texture = null
        b.build()?.close()
    }

    fun resetStats() {
        drawCalls = 0
    }

    override fun close() {
        builder = null
        memory.close()
    }
}

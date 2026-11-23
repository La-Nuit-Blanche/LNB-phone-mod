package fr.bde_teknantes.lnb.phone.client.smooth

import com.mojang.blaze3d.systems.RenderSystem

/** The smooth layer's caches and FreeType faces are not thread-safe: they belong to the render thread. */
internal object RenderThread {
    /** Throws, naming [what] and what to do instead, when called from any other thread. */
    fun check(what: String) {
        if (RenderSystem.isOnRenderThread()) return
        throw IllegalStateException(
            "$what was called on thread '${Thread.currentThread().name}', but the smooth layer (fonts, text measuring, " +
                "drawing) only works on the render thread. Measure and draw from your screen's render(), or move the " +
                "call there with Minecraft.getInstance().execute { ... } (a packet handler, for example).",
        )
    }
}

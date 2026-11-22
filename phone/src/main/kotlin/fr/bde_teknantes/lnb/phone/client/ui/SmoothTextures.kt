package fr.bde_teknantes.lnb.phone.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/**
 * Finds the export of a smooth-layer image made for a GUI scale: `textures/gui/smooth/<density>/<path>`, where the
 * density is the scale's pixels per dp (`1x` at GUI scale 2, `1_5x` at 3, ... `4_5x` at 9, in half steps). When that
 * density wasn't exported, the nearest one that was (the larger on a tie, which scales down more cleanly). Lookups are
 * cached until resources reload. Render thread.
 */
internal object SmoothTextures : ResourceManagerReloadListener {
    private data class Key(val namespace: String, val path: String, val guiScale: Int)

    private val cache = HashMap<Key, ResourceLocation>()

    fun find(namespace: String, path: String, guiScale: Int): ResourceLocation =
        cache.getOrPut(Key(namespace, path, guiScale)) {
            val resources = Minecraft.getInstance().resourceManager
            location(namespace, choose(guiScale) { resources.getResource(location(namespace, it, path)).isPresent }, path)
        }

    /** The density folder to load for [guiScale]: the best one that [exists], or the ideal one when none does. */
    fun choose(guiScale: Int, exists: (folder: String) -> Boolean): String {
        val candidates = candidates(guiScale)
        return candidates.firstOrNull(exists) ?: candidates.first()
    }

    /** Density folders to try for [guiScale], best first. Plain logic, for tests. */
    fun candidates(guiScale: Int): List<String> {
        val target = guiScale.coerceAtLeast(2)
        // Densities in half steps of px per dp, written as GUI scales: 2 is 1x, 3 is 1_5x...
        val scales = 2..maxOf(MAX_EXPORTED_SCALE, target)
        return scales.sortedWith(compareBy<Int> { kotlin.math.abs(it - target) }.thenByDescending { it }).map(::folder)
    }

    /** The folder of a GUI scale's density: `1x`, `1_5x`, `2x`... */
    fun folder(guiScale: Int): String = if (guiScale % 2 == 0) "${guiScale / 2}x" else "${guiScale / 2}_5x"

    private fun location(namespace: String, folder: String, path: String) =
        ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/smooth/$folder/$path")

    override fun onResourceManagerReload(resourceManager: ResourceManager) = cache.clear()

    /** The phone's sprite set is exported up to GUI scale 9 (`4_5x`). */
    private const val MAX_EXPORTED_SCALE = 9
}

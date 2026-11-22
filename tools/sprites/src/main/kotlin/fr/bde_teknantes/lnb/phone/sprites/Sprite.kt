package fr.bde_teknantes.lnb.phone.sprites

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * One generated image. [path] is relative to `assets/lnbphone/textures/gui/` and has no extension
 * (`sprites/phone/frame`); [mcmeta] is the text of its `.png.mcmeta`, or null for none. [antiAliased] images come out
 * of Java2D's anti-aliasing rather than from exact pixel numbers.
 */
class Sprite(val path: String, val image: BufferedImage, val mcmeta: String? = null, val antiAliased: Boolean = false)

/**
 * The folders under `textures/gui/` that belong to the generator alone: whatever it doesn't draw there is stale and
 * gets deleted. Hand-made assets go elsewhere.
 */
val GENERATED_DIRS = listOf(SHELL_DIR, SMOOTH_DIR)

/** What [writeSprites] did to one file. */
enum class WriteResult { CREATED, UPDATED, UNCHANGED, DELETED }

/**
 * Writes each sprite under [root] and deletes every other file of the [GENERATED_DIRS] (a renamed or removed sprite
 * would otherwise keep shipping). A PNG that already holds the same pixels is left alone, so a re-run only touches
 * the images whose numbers changed, even on a machine whose PNG compressor writes other bytes. Keys of the result
 * are file paths relative to [root], with `/`.
 */
fun writeSprites(root: File, sprites: List<Sprite>): Map<String, WriteResult> {
    val duplicates = sprites.groupBy { it.path }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Two sprites share a path: $duplicates" }
    val results = sortedMapOf<String, WriteResult>()
    for (sprite in sprites) {
        val png = File(root, "${sprite.path}.png")
        val meta = File(root, "${sprite.path}.png.mcmeta")
        png.parentFile.mkdirs()
        val existed = png.exists()
        var changed = false
        if (!existed || !samePixels(ImageIO.read(png), sprite.image)) {
            check(ImageIO.write(sprite.image, "png", png)) { "No PNG writer" }
            changed = true
        }
        if (sprite.mcmeta == null) {
            if (meta.delete()) changed = true
        } else if (!meta.exists() || meta.readText().replace("\r\n", "\n") != sprite.mcmeta) {
            // (A Windows checkout may have turned the line ending into CRLF; that is not a change.)
            meta.writeText(sprite.mcmeta)
            changed = true
        }
        results["${sprite.path}.png"] = when {
            !existed -> WriteResult.CREATED
            changed -> WriteResult.UPDATED
            else -> WriteResult.UNCHANGED
        }
    }
    for (stray in strayFiles(root, sprites)) {
        File(root, stray).delete()
        results[stray] = WriteResult.DELETED
    }
    // Folders emptied by the deletions go too.
    GENERATED_DIRS.map { File(root, it) }.filter { it.isDirectory }.forEach { dir ->
        dir.walkBottomUp().filter { it.isDirectory && it != dir && it.list().isNullOrEmpty() }.forEach { it.delete() }
    }
    return results
}

/** Files under the [GENERATED_DIRS] of [root] that none of [sprites] produces, relative to [root] with `/`. */
fun strayFiles(root: File, sprites: List<Sprite>): List<String> {
    val expected = sprites.flatMap { sprite ->
        listOfNotNull("${sprite.path}.png", sprite.mcmeta?.let { "${sprite.path}.png.mcmeta" })
    }.toSet()
    return GENERATED_DIRS.map { File(root, it) }.filter { it.isDirectory }
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .filter { it !in expected }
        .sorted()
}

fun samePixels(a: BufferedImage?, b: BufferedImage): Boolean {
    if (a == null || a.width != b.width || a.height != b.height) return false
    return argb(a).contentEquals(argb(b))
}

/**
 * The largest difference between two images of the same size, over every channel of every pixel (0 to 255), or
 * null when their sizes differ.
 */
fun maxChannelDifference(a: BufferedImage, b: BufferedImage): Int? {
    if (a.width != b.width || a.height != b.height) return null
    val pa = argb(a)
    val pb = argb(b)
    var max = 0
    for (i in pa.indices) for (shift in intArrayOf(0, 8, 16, 24)) {
        max = maxOf(max, abs((pa[i] ushr shift and 0xFF) - (pb[i] ushr shift and 0xFF)))
    }
    return max
}

fun argb(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

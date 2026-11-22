package fr.bde_teknantes.lnb.phone.sprites

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * One generated image. [path] is relative to `assets/lnbphone/textures/gui/` and has no extension
 * (`sprites/phone/frame`); [mcmeta] is the text of its `.png.mcmeta`, or null for none.
 */
class Sprite(val path: String, val image: BufferedImage, val mcmeta: String? = null)

/** What [writeSprites] did to one file. */
enum class WriteResult { CREATED, UPDATED, UNCHANGED }

/**
 * Writes each sprite under [root]. A PNG that already holds the same pixels is left alone, so a re-run only touches
 * the images whose numbers changed, even on a machine whose PNG compressor writes other bytes.
 */
fun writeSprites(root: File, sprites: List<Sprite>): Map<String, WriteResult> {
    val duplicates = sprites.groupBy { it.path }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Two sprites share a path: $duplicates" }
    return sprites.associate { sprite ->
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
        sprite.path to when {
            !existed -> WriteResult.CREATED
            changed -> WriteResult.UPDATED
            else -> WriteResult.UNCHANGED
        }
    }
}

fun samePixels(a: BufferedImage?, b: BufferedImage): Boolean {
    if (a == null || a.width != b.width || a.height != b.height) return false
    return argb(a).contentEquals(argb(b))
}

fun argb(image: BufferedImage): IntArray = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

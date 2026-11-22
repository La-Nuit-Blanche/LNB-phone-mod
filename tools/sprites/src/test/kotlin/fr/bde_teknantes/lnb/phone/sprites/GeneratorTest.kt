package fr.bde_teknantes.lnb.phone.sprites

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.imageio.ImageIO

class GeneratorTest {
    @Test
    fun `two runs write byte-identical files`(@TempDir first: File, @TempDir second: File) {
        writeSprites(first, allSprites())
        writeSprites(second, allSprites())
        val files = first.walkTopDown().filter { it.isFile }.map { it.relativeTo(first).path }.sorted().toList()
        assertEquals(files, second.walkTopDown().filter { it.isFile }.map { it.relativeTo(second).path }.sorted().toList())
        files.forEach { assertArrayEquals(File(first, it).readBytes(), File(second, it).readBytes(), it) }
    }

    @Test
    fun `a second run leaves every file alone`(@TempDir dir: File) {
        writeSprites(dir, allSprites())
        assertTrue(writeSprites(dir, allSprites()).values.all { it == WriteResult.UNCHANGED })
    }

    /** Fails when the generator was changed without re-running it, or a committed PNG was edited by hand. */
    @Test
    fun `the committed sprites are what the generator draws`() {
        val root = File(System.getProperty("lnbphone.guiTextures"))
        allSprites().forEach { sprite ->
            val png = File(root, "${sprite.path}.png")
            assertTrue(png.exists(), "${sprite.path}.png is missing: run ./gradlew :tools:sprites:generateSprites")
            assertTrue(samePixels(ImageIO.read(png), sprite.image), "${sprite.path}.png is stale: run generateSprites")
            val meta = File(root, "${sprite.path}.png.mcmeta")
            assertEquals(sprite.mcmeta, if (meta.exists()) meta.readText().replace("\r\n", "\n") else null, sprite.path)
        }
    }

    @Test
    fun `the smooth set follows its density`() {
        val sprites = smoothSprites(SmoothFormat(pxPerDp = 3.0)).associateBy { it.path }
        assertEquals(72, sprites.getValue("smooth/shape/r10").image.width)
        assertEquals(216, sprites.getValue("smooth/shadow/fab").image.width)
        assertEquals(48, sprites.getValue("smooth/icon16/heart").image.width)
    }

    @Test
    fun `svg paths parse compact numbers and arcs`() {
        val bounds = svgPath("M1.5 4.6v2.8a.6.6 0 0 0 .6.6h1.4").bounds2D
        assertEquals(1.5, bounds.minX, 1e-9)
        assertEquals(4.6, bounds.minY, 1e-9)
        assertEquals(3.5, bounds.maxX, 1e-9)
        assertEquals(8.0, bounds.maxY, 1e-9)
        // A half circle from (0, 0) to (10, 0), sweeping clockwise through y = -5 (SVG's y points down).
        val arc = svgPath("M0 0a5 5 0 0 1 10 0").bounds2D
        assertEquals(-5.0, arc.minY, 1e-6)
    }
}

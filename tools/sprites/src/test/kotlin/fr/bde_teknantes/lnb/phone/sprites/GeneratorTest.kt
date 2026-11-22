package fr.bde_teknantes.lnb.phone.sprites

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.imageio.ImageIO

/**
 * How far an anti-aliased image may drift, per channel (0 to 255), from what this machine draws. Java2D's
 * rasteriser is the same Java code in every OpenJDK 21, but CI runs another vendor on another OS; this absorbs a
 * rounding step or two of coverage while still catching any real change: a hand-edited pixel, a stroke 0.25 dp off
 * or a moved path all move edge pixels by tens of levels. The pixel shell set is compared exactly.
 */
private const val ANTI_ALIASING_TOLERANCE = 3

class GeneratorTest {
    @Test
    fun `two runs write byte-identical files`(@TempDir first: File, @TempDir second: File) {
        writeSprites(first, allSprites())
        writeSprites(second, allSprites())
        val files = files(first)
        assertEquals(files, files(second))
        files.forEach { assertArrayEquals(File(first, it).readBytes(), File(second, it).readBytes(), it) }
    }

    @Test
    fun `a second run leaves every file alone`(@TempDir dir: File) {
        writeSprites(dir, allSprites())
        assertTrue(writeSprites(dir, allSprites()).values.all { it == WriteResult.UNCHANGED })
    }

    @Test
    fun `a run deletes what it no longer draws, and only in its own folders`(@TempDir dir: File) {
        val stale = File(dir, "$SMOOTH_DIR/shape/r10.png").apply { parentFile.mkdirs(); writeText("old") }
        val staleMeta = File(dir, "$SHELL_DIR/phone/old.png.mcmeta").apply { parentFile.mkdirs(); writeText("{}") }
        val elsewhere = File(dir, "other/keep.png").apply { parentFile.mkdirs(); writeText("not ours") }
        val results = writeSprites(dir, allSprites())
        assertFalse(stale.exists())
        assertFalse(stale.parentFile.exists(), "the emptied folder is removed")
        assertFalse(staleMeta.exists())
        assertTrue(elsewhere.exists())
        assertEquals(WriteResult.DELETED, results["$SMOOTH_DIR/shape/r10.png"])
    }

    /** Fails when the generator was changed without re-running it, or a generated folder was edited by hand. */
    @Test
    fun `the committed sprites are exactly what the generator draws`() {
        val root = File(System.getProperty("lnbphone.guiTextures"))
        val sprites = allSprites()
        sprites.forEach { sprite ->
            val png = File(root, "${sprite.path}.png")
            assertTrue(png.exists(), "${sprite.path}.png is missing: run ./gradlew :tools:sprites:generateSprites")
            val committed = ImageIO.read(png)
            if (sprite.antiAliased) {
                val difference = maxChannelDifference(committed, sprite.image)
                assertNotNull(difference, "${sprite.path}.png has the wrong size: run generateSprites")
                assertTrue(difference!! <= ANTI_ALIASING_TOLERANCE, "${sprite.path}.png is off by $difference: run generateSprites")
            } else {
                assertTrue(samePixels(committed, sprite.image), "${sprite.path}.png is stale: run generateSprites")
            }
            val meta = File(root, "${sprite.path}.png.mcmeta")
            assertEquals(sprite.mcmeta, if (meta.exists()) meta.readText().replace("\r\n", "\n") else null, sprite.path)
        }
        assertEquals(emptyList<String>(), strayFiles(root, sprites), "files the generator doesn't draw: run generateSprites")
    }

    @Test
    fun `icons are drawn at every density`() {
        val paths = smoothSprites().associateBy { it.path }
        assertEquals(16, paths.getValue("smooth/1x/icon16/heart").image.width)
        assertEquals(24, paths.getValue("smooth/1_5x/icon16/heart").image.width)
        assertEquals(32, paths.getValue("smooth/2x/icon16/heart").image.width)
        assertEquals(42, paths.getValue("smooth/1_5x/icon28/person").image.width)
        assertEquals(144, paths.getValue("smooth/shadow/fab").image.width)
        val other = smoothSprites(SmoothFormat(iconDensities = listOf(Density("3x", 3.0)))).map { it.path }
        assertTrue("smooth/3x/spinner" in other && other.none { it.startsWith("smooth/2x/") })
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

    private fun files(root: File) =
        root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
}

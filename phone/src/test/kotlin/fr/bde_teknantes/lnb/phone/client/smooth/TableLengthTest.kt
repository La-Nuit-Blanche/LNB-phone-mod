package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.time.Duration

/**
 * Both table readers size their budget by the table's length: a length the table directory declares falsely, larger
 * than the file, must not buy a larger budget. And whatever the true size, there is a ceiling, so that one query of a
 * table asked at every frame stays cheap.
 */
class TableLengthTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private fun <T> allocated(block: () -> T): Pair<T, Long> {
        val id = Thread.currentThread().threadId()
        val before = threads.getThreadAllocatedBytes(id)
        val result = block()
        return result to threads.getThreadAllocatedBytes(id) - before
    }

    /** A font file holding only [table] under [tag], whose directory declares [declared] bytes (null: the truth). */
    private fun font(tag: String, table: ByteArray, declared: Long? = null): ByteArray {
        val out = ByteBuffer.allocate(28 + table.size)
        out.putInt(0x00010000).putShort(1).putShort(16).putShort(0).putShort(0)
        out.put(tag.toByteArray()).putInt(0).putInt(28).putInt((declared ?: table.size.toLong()).toInt())
        out.put(table)
        return out.array()
    }

    /**
     * A GSUB table with one `ccmp` lookup of [subtables] distinct subtables that all share one ligature set of
     * [ligatures] references to one ligature (glyph 1 then 55 gives 99): every query for glyph 1 scans them all.
     */
    private fun gsub(subtables: Int, ligatures: Int): ByteArray {
        val b = ByteBuffer.allocate(64 + 10 * subtables + 2 * ligatures)
        fun u16(at: Int, v: Int) = b.putShort(at, v.toShort())
        u16(0, 1); u16(2, 0); u16(4, 10); u16(6, 12); u16(8, 28)
        u16(10, 0)
        u16(12, 1); b.put(14, "ccmp".toByteArray()); u16(18, 8)
        u16(20, 0); u16(22, 1); u16(24, 0)
        u16(28, 1); u16(30, 4)
        val lookup = 32
        u16(lookup, 4); u16(lookup + 2, 0); u16(lookup + 4, subtables)
        val bodies = lookup + 6 + 2 * subtables
        val coverage = bodies + 8 * subtables
        val set = coverage + 6
        val ligature = set + 2 + 2 * ligatures
        for (i in 0 until subtables) {
            val body = bodies + 8 * i
            u16(lookup + 6 + 2 * i, body - lookup)
            u16(body, 1); u16(body + 2, coverage - body); u16(body + 4, 1); u16(body + 6, set - body)
        }
        u16(coverage, 1); u16(coverage + 2, 1); u16(coverage + 4, 1)
        u16(set, ligatures)
        for (i in 0 until ligatures) u16(set + 2 + 2 * i, ligature - set)
        u16(ligature, 99); u16(ligature + 2, 2); u16(ligature + 4, 55)
        return b.array().copyOf(ligature + 6)
    }

    /** A GPOS table whose `kern` feature lists [k] lookups, all one lookup of [m] references to one pair subtable. */
    private fun gpos(k: Int, m: Int): ByteArray {
        val featureList = 10
        val feature = featureList + 8
        val lookupList = feature + 4 + 2 * k
        val lookup = lookupList + 2 + 2 * k
        val body = lookup + 6 + 2 * m
        val g = ByteBuffer.allocate(body + 24)
        fun u16(at: Int, v: Int) = g.putShort(at, v.toShort())
        g.putInt(0, 0x00010000); u16(4, 0); u16(6, featureList); u16(8, lookupList)
        u16(featureList, 1); g.put(featureList + 2, "kern".toByteArray()); u16(featureList + 6, 8)
        u16(feature, 0); u16(feature + 2, k)
        for (i in 0 until k) u16(feature + 4 + 2 * i, i)
        u16(lookupList, k)
        for (i in 0 until k) u16(lookupList + 2 + 2 * i, lookup - lookupList)
        u16(lookup, 2); u16(lookup + 2, 0); u16(lookup + 4, m)
        for (i in 0 until m) u16(lookup + 6 + 2 * i, body - lookup)
        // Pair adjustment, format 1: glyph 1 then 2 kerns by -50.
        u16(body, 1); u16(body + 2, 12); u16(body + 4, 4); u16(body + 6, 0); u16(body + 8, 1); u16(body + 10, 18)
        u16(body + 12, 1); u16(body + 14, 1); u16(body + 16, 1)
        u16(body + 18, 1); u16(body + 20, 2); u16(body + 22, -50)
        return g.array()
    }

    @Test
    fun `a directory entry that runs past the file is no table at all`() {
        val table = gsub(2, 3)
        val honest = ByteBuffer.wrap(font("GSUB", table))
        assertEquals(28 to table.size, GposKerning.findTable(honest, "GSUB"))
        for (declared in listOf(0x7FFFFFF0L, table.size + 1L, 0xFFFFFFF0L)) {
            assertNull(GposKerning.findTable(ByteBuffer.wrap(font("GSUB", table, declared)), "GSUB"), "declared $declared")
        }
    }

    @Test
    fun `a substitution table lying about its length gets no ligatures, and costs nothing to ask`() {
        // About 130 KB: with its length declared as 2 GB it used to hold 208 million ligatures and take 135 ms a query.
        val table = gsub(8_000, 30_000)
        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            val (lied, used) = allocated { GsubLigatures(ByteBuffer.wrap(font("GSUB", table, 0x7FFFFFF0L))) }
            assertTrue(lied.isEmpty)
            assertTrue(used < 8L shl 20, "allocated $used")
            // Its true length gives it a budget it runs out of: nothing either.
            assertTrue(GsubLigatures(ByteBuffer.wrap(font("GSUB", table))).isEmpty)
        }
    }

    @Test
    fun `a kerning table lying about its length is read in little memory, never out of it`() {
        // About 128 KB: with its length declared as 2 GB it used to throw OutOfMemoryError after 0.8 s, an Error that
        // passed every catch on the way and, for a replaced Inter, crashed the game on the render thread.
        val table = gpos(k = 32_000, m = 32_000)
        assertTimeoutPreemptively(Duration.ofSeconds(10)) {
            for (declared in listOf(0x7FFFFFF0L, null)) {
                val (kerning, used) = allocated { GposKerning(ByteBuffer.wrap(font("GPOS", table, declared))) }
                assertTrue(used < 16L shl 20, "declared $declared: allocated $used")
                kerning.kerning(1, 2)
                kerning.kerning(1, 3)
            }
        }
    }

    @Test
    fun `a kerning lookup referenced many times is kept once, and still kerns`() {
        val kerning = GposKerning(ByteBuffer.wrap(font("GPOS", gpos(k = 50, m = 50))))
        assertEquals(-50, kerning.kerning(1, 2))
        assertEquals(0, kerning.kerning(1, 3))
    }

    @Test
    fun `whatever their true size, the budgets have a ceiling`() {
        val huge = 64 shl 20
        assertEquals(GsubLigatures.MAX_BUDGET, minOf(GsubLigatures.BUDGET_PER_BYTE * huge + GsubLigatures.BUDGET_BASE, GsubLigatures.MAX_BUDGET))
        // The real fonts are far below them.
        val noto = GsubLigatures(ByteBuffer.wrap(InterMetrics.bytes("noto_emoji.ttf")))
        assertTrue(noto.workUsed * 8 < GsubLigatures.MAX_BUDGET, "Noto used ${noto.workUsed}")
        val inter = GposKerning(ByteBuffer.wrap(InterMetrics.bytes("inter_semibold.ttf")))
        assertTrue(inter.workUsed * 8 < GposKerning.MAX_BUDGET, "Inter used ${inter.workUsed}")
    }

    @Test
    fun `a substitution table that fills its budget with entries to scan is still quick to query`() {
        // Ten distinct subtables sharing a set of 20,000 references: 200,000 entries scanned for every query of glyph
        // 1 that matches none, within an honest budget. The renderer asks once per grapheme (the face caches shapes),
        // but even uncached a query must stay well under a frame.
        val gsub = GsubLigatures(ByteBuffer.wrap(font("GSUB", gsub(10, 20_000))))
        assertEquals((99L shl 32) or 2L, gsub.ligature(intArrayOf(1, 55), 0, 2))
        repeat(20) { gsub.ligature(intArrayOf(1, 56), 0, 2) }
        val started = System.nanoTime()
        repeat(10) { gsub.ligature(intArrayOf(1, 56), 0, 2) }
        val perQuery = (System.nanoTime() - started) / 10 / 1000
        assertTrue(perQuery < 5_000, "$perQuery us a query")
    }
}

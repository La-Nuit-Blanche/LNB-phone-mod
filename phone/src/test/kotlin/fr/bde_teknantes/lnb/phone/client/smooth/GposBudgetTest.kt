package fr.bde_teknantes.lnb.phone.client.smooth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.ThrowingSupplier
import java.nio.ByteBuffer
import java.time.Duration

/**
 * Tables crafted to declare far more than their size: a few hundred kilobytes asking for gigabytes, or billions of
 * steps. Each must be parsed quickly and in little memory, either correctly (shared data parsed once) or not at all.
 */
class GposBudgetTest {
    /** A font file holding only [gpos], whose `kern` feature leads to the lookup list built by [lookups]. */
    private class Table {
        val bytes = ByteBuffer.allocate(4 shl 20)
        var size = 0

        fun u16(at: Int, v: Int) {
            bytes.putShort(at, v.toShort())
            size = maxOf(size, at + 2)
        }

        fun tag(at: Int, t: String) {
            t.forEachIndexed { i, c -> bytes.put(at + i, c.code.toByte()) }
            size = maxOf(size, at + 4)
        }

        /** The whole file: header, one GPOS record, then the table at offset 28. */
        fun font(): ByteArray {
            val out = ByteBuffer.allocate(28 + size)
            out.putInt(0x00010000).putShort(1).putShort(16).putShort(0).putShort(0)
            out.put("GPOS".toByteArray()).putInt(0).putInt(28).putInt(size)
            out.put(bytes.array(), 0, size)
            return out.array()
        }
    }

    private val g = 0 // offsets below are within the GPOS table

    /** Header, empty script list and a `kern` feature using lookups 0 until [lookupCount]; returns the lookup list's offset. */
    private fun Table.head(lookupCount: Int): Int {
        u16(g + 0, 1)
        u16(g + 4, 10) // script list
        u16(g + 6, 12) // feature list
        u16(g + 10, 0)
        u16(g + 12, 1)
        tag(g + 14, "kern")
        u16(g + 18, 8) // feature, from the feature list
        u16(g + 22, lookupCount)
        for (i in 0 until lookupCount) u16(g + 24 + 2 * i, i)
        val lookupList = 24 + 2 * lookupCount
        u16(g + 8, lookupList)
        return lookupList
    }

    private fun parse(bytes: ByteArray) = GposKerning(ByteBuffer.wrap(bytes))

    @Test
    fun `thirty thousand pair sets pointing at one set of 65535 pairs are parsed once`() {
        // Before: 30000 x 65535 pairs read and stored, about 16 GB. Shared, it is one set.
        val t = Table()
        val lookupList = t.head(1)
        t.u16(lookupList, 1)
        t.u16(lookupList + 2, 4)
        val lookup = lookupList + 4
        t.u16(lookup, 2)
        t.u16(lookup + 4, 1)
        t.u16(lookup + 6, 8)
        val p = lookup + 8
        val sets = 30000
        val coverage = 10 + 2 * sets
        val set = coverage + 10
        t.u16(p, 1)
        t.u16(p + 2, coverage)
        t.u16(p + 4, 0x4)
        t.u16(p + 8, sets)
        for (i in 0 until sets) t.u16(p + 10 + 2 * i, set)
        t.u16(p + coverage, 2) // one range: glyphs 1..30000
        t.u16(p + coverage + 2, 1)
        t.u16(p + coverage + 4, 1)
        t.u16(p + coverage + 6, sets)
        t.u16(p + coverage + 8, 0)
        t.u16(p + set, 65535)
        for (i in 0 until 65535) {
            t.u16(p + set + 2 + 4 * i, i)
            t.u16(p + set + 4 + 4 * i, -(i % 100) - 1)
        }
        val k = assertTimeoutPreemptively(Duration.ofSeconds(10), ThrowingSupplier { parse(t.font()) })
        assertFalse(k.isEmpty)
        assertEquals(-35, k.kerning(5, 1234))
        assertEquals(-35, k.kerning(29999, 1234))
        assertTrue(k.workUsed <= k.workAllowed)
    }

    @Test
    fun `thousands of overlapping pair sets each declaring 65535 pairs give no kerning`() {
        // Distinct offsets 4 bytes apart into one run of 0xFFFF: each "set" claims 65535 pairs. Before: about 2 GB.
        val t = Table()
        val lookupList = t.head(1)
        t.u16(lookupList, 1)
        t.u16(lookupList + 2, 4)
        val lookup = lookupList + 4
        t.u16(lookup, 2)
        t.u16(lookup + 4, 1)
        t.u16(lookup + 6, 8)
        val p = lookup + 8
        val sets = 4000
        val coverage = 10 + 2 * sets
        val run = coverage + 10
        t.u16(p, 1)
        t.u16(p + 2, coverage)
        t.u16(p + 4, 0x4)
        t.u16(p + 8, sets)
        for (i in 0 until sets) t.u16(p + 10 + 2 * i, run + 4 * i)
        t.u16(p + coverage, 2)
        t.u16(p + coverage + 2, 1)
        t.u16(p + coverage + 4, 1)
        t.u16(p + coverage + 6, sets)
        for (i in 0 until 70000) {
            t.u16(p + run + 4 * i, 0xFFFF)
            t.u16(p + run + 2 + 4 * i, 0xFFFF)
        }
        val k = assertTimeoutPreemptively(Duration.ofSeconds(10), ThrowingSupplier { parse(t.font()) })
        assertTrue(k.isEmpty)
        assertEquals(0, k.kerning(5, 0xFFFF))
    }

    @Test
    fun `twenty thousand lookups of twenty thousand subtables are read once`() {
        // Every lookup is the same lookup, every subtable the same subtable: 400 million entries. Before: OOM; then no
        // kerning (the budget ran out); now the one lookup and its one subtable, kept once, kern as they say.
        val n = 20000
        val t = Table()
        val lookupList = t.head(n)
        t.u16(lookupList, n)
        val lookup = 2 + 2 * n
        for (i in 0 until n) t.u16(lookupList + 2 + 2 * i, lookup)
        val l = lookupList + lookup
        t.u16(l, 2)
        t.u16(l + 4, n)
        val sub = 6 + 2 * n
        for (i in 0 until n) t.u16(l + 6 + 2 * i, sub)
        val p = l + sub
        t.u16(p, 1)
        t.u16(p + 2, 12)
        t.u16(p + 4, 0x4)
        t.u16(p + 8, 1)
        t.u16(p + 10, 18)
        t.u16(p + 12, 1) // coverage format 1: glyph 3
        t.u16(p + 14, 1)
        t.u16(p + 16, 3)
        t.u16(p + 18, 1) // set: (5, -50)
        t.u16(p + 20, 5)
        t.u16(p + 22, -50)
        val k = assertTimeoutPreemptively(Duration.ofSeconds(10), ThrowingSupplier { parse(t.font()) })
        assertEquals(-50, k.kerning(3, 5))
        assertTrue(k.workUsed < 200_000, "used ${k.workUsed}")
    }

    @Test
    fun `a real font stays far inside its budget`() {
        for (name in listOf("inter_regular.ttf", "inter_semibold.ttf")) {
            val k = GposKerning(ByteBuffer.wrap(InterMetrics.bytes(name)))
            assertFalse(k.isEmpty)
            assertTrue(k.workUsed * 4 < k.workAllowed, "$name used ${k.workUsed} of ${k.workAllowed}")
        }
    }
}

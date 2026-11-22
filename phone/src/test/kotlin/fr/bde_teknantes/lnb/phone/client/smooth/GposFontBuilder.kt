package fr.bde_teknantes.lnb.phone.client.smooth

import java.io.ByteArrayOutputStream

/**
 * Writes minimal font files holding only a GPOS table, so [GposKerning] can be tested on exact, hand-made data.
 * Offsets are computed from the order blocks are laid out in, the way the OpenType spec defines them (each relative
 * to its parent table).
 */
object GposFontBuilder {
    /** One pair adjustment subtable, as it appears in a lookup. */
    sealed interface PairPos

    /** Format 1: explicit pairs. [pairs] maps a first glyph to its (second glyph, X advance) list. */
    class Pairs(
        val pairs: Map<Int, List<Pair<Int, Int>>>,
        val valueFormat1: Int = 0x4,
        val valueFormat2: Int = 0,
    ) : PairPos

    /**
     * Format 2: classes. [firstClasses] and [secondClasses] map glyphs to classes; [values] is indexed
     * `[class1][class2]`. [coverage] lists the first glyphs covered.
     */
    class Classes(
        val coverage: List<Int>,
        val firstClasses: Map<Int, Int>,
        val secondClasses: Map<Int, Int>,
        val values: Array<IntArray>,
        val valueFormat1: Int = 0x4,
    ) : PairPos

    /** A lookup: its subtables, wrapped in extension (type 9) subtables when [extension]. */
    class Lookup(val subtables: List<PairPos>, val extension: Boolean = false)

    /** A font whose `kern` feature uses every lookup in [lookups], in order. */
    fun font(vararg lookups: Lookup): ByteArray = font(lookups.toList(), featureTag = "kern")

    fun font(lookups: List<Lookup>, featureTag: String): ByteArray {
        val gpos = gpos(lookups, featureTag)
        val out = Bytes()
        out.u32(0x00010000)
        out.u16(1) // numTables
        out.u16(16)
        out.u16(0)
        out.u16(0)
        out.tag("GPOS")
        out.u32(0) // checksum, unchecked
        out.u32(12 + 16)
        out.u32(gpos.size)
        out.bytes(gpos)
        return out.toByteArray()
    }

    /** A font with one table that is not GPOS. */
    fun fontWithoutGpos(): ByteArray {
        val out = Bytes()
        out.u32(0x00010000)
        out.u16(1)
        out.u16(16)
        out.u16(0)
        out.u16(0)
        out.tag("head")
        out.u32(0)
        out.u32(28)
        out.u32(4)
        out.u32(0)
        return out.toByteArray()
    }

    private fun gpos(lookups: List<Lookup>, featureTag: String): ByteArray {
        val scriptList = Bytes().apply { u16(0) }.toByteArray()
        val feature = Bytes().apply {
            u16(0) // featureParams
            u16(lookups.size)
            lookups.indices.forEach { u16(it) }
        }.toByteArray()
        val featureList = Bytes().apply {
            u16(1)
            tag(featureTag)
            u16(2 + 6)
            bytes(feature)
        }.toByteArray()
        val lookupList = lookupList(lookups)
        return Bytes().apply {
            u16(1)
            u16(0)
            u16(10) // scriptList
            u16(10 + scriptList.size)
            u16(10 + scriptList.size + featureList.size)
            bytes(scriptList)
            bytes(featureList)
            bytes(lookupList)
        }.toByteArray()
    }

    private fun lookupList(lookups: List<Lookup>): ByteArray {
        val tables = lookups.map(::lookup)
        return Bytes().apply {
            u16(lookups.size)
            var offset = 2 + 2 * lookups.size
            for (t in tables) {
                u16(offset)
                offset += t.size
            }
            tables.forEach(::bytes)
        }.toByteArray()
    }

    private fun lookup(lookup: Lookup): ByteArray {
        val subs = lookup.subtables.map { sub ->
            val body = pairPos(sub)
            if (!lookup.extension) body else Bytes().apply {
                u16(1) // format
                u16(2) // extensionLookupType
                u32(8)
                bytes(body)
            }.toByteArray()
        }
        return Bytes().apply {
            u16(if (lookup.extension) 9 else 2)
            u16(0) // flags
            u16(subs.size)
            var offset = 6 + 2 * subs.size
            for (s in subs) {
                u16(offset)
                offset += s.size
            }
            subs.forEach(::bytes)
        }.toByteArray()
    }

    private fun pairPos(sub: PairPos): ByteArray = when (sub) {
        is Pairs -> pairsFormat1(sub)
        is Classes -> classesFormat2(sub)
    }

    private fun valueRecord(out: Bytes, format: Int, xAdvance: Int) {
        // Fields in bit order; X advance (0x4) gets the value, every other present field a marker that must be ignored.
        for (bit in 0 until 8) {
            if (format and (1 shl bit) == 0) continue
            out.u16(if (bit == 2) xAdvance else 0x7777)
        }
    }

    private fun pairsFormat1(sub: Pairs): ByteArray {
        val firsts = sub.pairs.keys.sorted()
        val coverage = coverageFormat1(firsts)
        val sets = firsts.map { first ->
            Bytes().apply {
                val pairs = sub.pairs.getValue(first).sortedBy { it.first }
                u16(pairs.size)
                for ((second, value) in pairs) {
                    u16(second)
                    valueRecord(this, sub.valueFormat1, value)
                    valueRecord(this, sub.valueFormat2, 0)
                }
            }.toByteArray()
        }
        val header = 10 + 2 * firsts.size
        return Bytes().apply {
            u16(1)
            u16(header + sets.sumOf { it.size }) // coverage after the sets
            u16(sub.valueFormat1)
            u16(sub.valueFormat2)
            u16(firsts.size)
            var offset = header
            for (s in sets) {
                u16(offset)
                offset += s.size
            }
            sets.forEach(::bytes)
            bytes(coverage)
        }.toByteArray()
    }

    private fun classesFormat2(sub: Classes): ByteArray {
        val class1Count = sub.values.size
        val class2Count = sub.values.firstOrNull()?.size ?: 0
        val recordSize = 2 * Integer.bitCount(sub.valueFormat1 and 0xFF)
        val header = 16 + class1Count * class2Count * recordSize
        val coverage = coverageFormat2(sub.coverage)
        val first = classDefFormat2(sub.firstClasses)
        val second = classDefFormat1(sub.secondClasses)
        return Bytes().apply {
            u16(2)
            u16(header)
            u16(sub.valueFormat1)
            u16(0)
            u16(header + coverage.size)
            u16(header + coverage.size + first.size)
            u16(class1Count)
            u16(class2Count)
            for (row in sub.values) for (v in row) valueRecord(this, sub.valueFormat1, v)
            bytes(coverage)
            bytes(first)
            bytes(second)
        }.toByteArray()
    }

    private fun coverageFormat1(glyphs: List<Int>) = Bytes().apply {
        u16(1)
        u16(glyphs.size)
        glyphs.sorted().forEach(::u16)
    }.toByteArray()

    /** One range per glyph: exercises the range format without merging logic. */
    private fun coverageFormat2(glyphs: List<Int>) = Bytes().apply {
        u16(2)
        u16(glyphs.size)
        glyphs.sorted().forEachIndexed { i, g ->
            u16(g)
            u16(g)
            u16(i)
        }
    }.toByteArray()

    private fun classDefFormat2(classes: Map<Int, Int>) = Bytes().apply {
        u16(2)
        u16(classes.size)
        for ((g, c) in classes.toSortedMap()) {
            u16(g)
            u16(g)
            u16(c)
        }
    }.toByteArray()

    private fun classDefFormat1(classes: Map<Int, Int>) = Bytes().apply {
        u16(1)
        if (classes.isEmpty()) {
            u16(0)
            u16(0)
        } else {
            val start = classes.keys.min()
            val end = classes.keys.max()
            u16(start)
            u16(end - start + 1)
            for (g in start..end) u16(classes[g] ?: 0)
        }
    }.toByteArray()

    private class Bytes : ByteArrayOutputStream() {
        fun u16(v: Int) {
            write(v ushr 8 and 0xFF)
            write(v and 0xFF)
        }

        fun u32(v: Int) {
            u16(v ushr 16)
            u16(v and 0xFFFF)
        }

        fun tag(t: String) = write(t.toByteArray(Charsets.US_ASCII))

        fun bytes(b: ByteArray) = write(b)
    }
}

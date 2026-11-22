package fr.bde_teknantes.lnb.phone.client.smooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pair kerning read from a font's OpenType `GPOS` table, `kern` feature.
 *
 * Inter keeps its kerning only there (it has no legacy `kern` table), and neither FreeType's `FT_Get_Kerning` nor
 * Minecraft's TTF provider reads it. Without it, pairs such as "Te", "Vo" or "r." are visibly loose. Only what pair
 * kerning needs is read: lookups of type 2 (pair adjustment, formats 1 and 2), also when wrapped in type 9
 * (extension), and only the X advance of the first glyph.
 *
 * Values are in font units. [font] is the whole font file; it is read once, in the constructor.
 */
class GposKerning(font: ByteBuffer) {
    private val subtables: List<Subtable>

    /** Work units the parse used, and the budget it had (both 0 without a GPOS table); for tests. */
    internal var workUsed = 0L
        private set
    internal var workAllowed = 0L
        private set

    init {
        val buf = font.duplicate().order(ByteOrder.BIG_ENDIAN)
        // A resource pack can replace the font, and players keep the phone when they join other servers: a broken or
        // hostile table must cost the kerning, never the game. Every read is bounds-checked by the buffer, and the
        // parser's work and memory are bounded by the table's real size, so any failure ends up here.
        subtables = try {
            findTable(buf, "GPOS")?.let { (offset, length) ->
                val parser = Parser(buf, length)
                workAllowed = parser.allowed
                parser.readKernSubtables(offset).also { workUsed = parser.allowed - parser.left }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val isEmpty: Boolean get() = subtables.isEmpty()

    /** X advance adjustment between glyphs [left] and [right], in font units. */
    fun kerning(left: Int, right: Int): Int {
        var total = 0
        var lookup = -1
        var lookupDone = false
        for (sub in subtables) {
            if (sub.lookup != lookup) {
                lookup = sub.lookup
                lookupDone = false
            }
            if (lookupDone) continue
            val value = sub.adjust(left, right) ?: continue
            // The first subtable of a lookup that applies to the pair ends that lookup; later lookups still add up.
            total += value
            lookupDone = true
        }
        return total
    }

    private sealed class Subtable(val lookup: Int) {
        /** The adjustment when this subtable applies to the pair, else null. */
        abstract fun adjust(left: Int, right: Int): Int?

        /** The same data, as a subtable of [lookup]. */
        abstract fun inLookup(lookup: Int): Subtable
    }

    private class PairSets(lookup: Int, val coverage: Coverage, val pairs: Array<IntArray>, val values: Array<IntArray>) : Subtable(lookup) {
        override fun adjust(left: Int, right: Int): Int? {
            val index = coverage.indexOf(left)
            // A broken coverage table can list more glyphs than there are pair sets.
            if (index !in pairs.indices) return null
            val seconds = pairs[index]
            val found = seconds.binarySearch(right)
            return if (found >= 0) values[index][found] else null
        }

        override fun inLookup(lookup: Int) = PairSets(lookup, coverage, pairs, values)
    }

    private class ClassPairs(
        lookup: Int,
        val coverage: Coverage,
        val first: ClassDef,
        val second: ClassDef,
        val class2Count: Int,
        val values: IntArray,
    ) : Subtable(lookup) {
        override fun adjust(left: Int, right: Int): Int? {
            if (coverage.indexOf(left) < 0) return null
            val c1 = first.classOf(left)
            val c2 = second.classOf(right)
            if (c2 >= class2Count) return null
            return values.getOrElse(c1 * class2Count + c2) { 0 }
        }

        override fun inLookup(lookup: Int) = ClassPairs(lookup, coverage, first, second, class2Count, values)
    }

    /** Coverage table: glyph id to coverage index, by binary search over sorted ranges. */
    private class Coverage(val starts: IntArray, val ends: IntArray, val firstIndex: IntArray) {
        fun indexOf(glyph: Int): Int {
            var lo = 0
            var hi = starts.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    glyph < starts[mid] -> hi = mid - 1
                    glyph > ends[mid] -> lo = mid + 1
                    else -> return firstIndex[mid] + glyph - starts[mid]
                }
            }
            return -1
        }
    }

    /** Class definition table: glyph id to class, 0 when not listed. */
    private class ClassDef(val starts: IntArray, val ends: IntArray, val classes: IntArray) {
        fun classOf(glyph: Int): Int {
            var lo = 0
            var hi = starts.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    glyph < starts[mid] -> hi = mid - 1
                    glyph > ends[mid] -> lo = mid + 1
                    else -> return classes[mid]
                }
            }
            return 0
        }
    }

    /** Thrown when a table asks for more work or memory than its size can justify. */
    private class TooLarge : RuntimeException("GPOS table asks for more than its size allows")

    /**
     * Reads the `kern` lookups of one GPOS table of [length] bytes.
     *
     * A hostile table can point many offsets at one large structure, or overlap structures, to make a few hundred
     * kilobytes declare billions of entries. So every structure is parsed once per offset (shared ones are reused),
     * and every entry read or allocated is charged to a budget proportional to the table's size. A real table never
     * comes close (a test holds Inter under a quarter of it); a table that runs out gets no kerning at all.
     */
    private class Parser(private val b: ByteBuffer, length: Int) {
        val allowed = BUDGET_PER_BYTE * length.coerceAtLeast(0) + BUDGET_BASE
        private var budget = allowed
        val left: Long get() = budget
        private val coverages = HashMap<Int, Coverage>()
        private val classDefs = HashMap<Int, ClassDef>()
        private val pairSets = HashMap<Long, Pair<IntArray, IntArray>>()
        private val bodies = HashMap<Int, Subtable?>()

        private fun charge(units: Long) {
            budget -= units
            if (budget < 0) throw TooLarge()
        }

        private fun u16(at: Int): Int = b.getShort(at).toInt() and 0xFFFF
        private fun s16(at: Int): Int = b.getShort(at).toInt()
        private fun u32(at: Int): Int = b.getInt(at)

        private fun tag(at: Int) = String(CharArray(4) { (b.get(at + it).toInt() and 0xFF).toChar() })

        fun readKernSubtables(gpos: Int): List<Subtable> {
            val featureList = gpos + u16(gpos + 6)
            val lookupList = gpos + u16(gpos + 8)

            // Every lookup any `kern` feature uses, whatever the script: the phone only sets French and English.
            val lookups = sortedSetOf<Int>()
            val featureCount = u16(featureList)
            charge(featureCount.toLong())
            for (i in 0 until featureCount) {
                val record = featureList + 2 + 6 * i
                if (tag(record) != "kern") continue
                val feature = featureList + u16(record + 4)
                val count = u16(feature + 2)
                charge(count.toLong())
                for (j in 0 until count) lookups.add(u16(feature + 4 + 2 * j))
            }

            // OpenType applies lookups in lookup-list order, not in the feature's order.
            val result = ArrayList<Subtable>()
            for (index in lookups) {
                val lookup = lookupList + u16(lookupList + 2 + 2 * index)
                val type = u16(lookup)
                val count = u16(lookup + 4)
                charge(count.toLong())
                for (j in 0 until count) {
                    var sub = lookup + u16(lookup + 6 + 2 * j)
                    var subType = type
                    if (type == 9) {
                        subType = u16(sub + 2)
                        sub += u32(sub + 4)
                    }
                    if (subType != 2) continue
                    body(sub)?.let { result.add(it.inLookup(index)) }
                }
            }
            return result
        }

        /** The subtable at [at], parsed once however many lookups point at it. */
        private fun body(at: Int): Subtable? = if (at in bodies) bodies[at] else readPairPos(at).also { bodies[at] = it }

        private fun readPairPos(at: Int): Subtable? {
            val format = u16(at)
            val coverage = coverage(at + u16(at + 2))
            val format1 = u16(at + 4)
            val format2 = u16(at + 6)
            val size1 = 2 * Integer.bitCount(format1 and 0xFF)
            val size2 = 2 * Integer.bitCount(format2 and 0xFF)
            // Value record fields come in bit order: X placement (0x1), Y placement (0x2), then X advance (0x4).
            val xAdvance = if (format1 and 0x4 != 0) 2 * Integer.bitCount(format1 and 0x3) else -1

            return when (format) {
                1 -> {
                    val setCount = u16(at + 8)
                    charge(setCount.toLong())
                    val stride = 2 + size1 + size2
                    val pairs = arrayOfNulls<IntArray>(setCount)
                    val values = arrayOfNulls<IntArray>(setCount)
                    for (i in 0 until setCount) {
                        // Shared sets are shared arrays, not copies.
                        val (seconds, advances) = pairSet(at + u16(at + 10 + 2 * i), stride, xAdvance)
                        pairs[i] = seconds
                        values[i] = advances
                    }
                    @Suppress("UNCHECKED_CAST")
                    PairSets(0, coverage, pairs as Array<IntArray>, values as Array<IntArray>)
                }
                2 -> {
                    val first = classDef(at + u16(at + 8))
                    val second = classDef(at + u16(at + 10))
                    val class1Count = u16(at + 12)
                    val class2Count = u16(at + 14)
                    val stride = size1 + size2
                    // Without an X advance every pair kerns by 0 (and getOrElse gives 0): nothing to read.
                    val values = if (xAdvance < 0) {
                        IntArray(0)
                    } else {
                        val cells = class1Count.toLong() * class2Count
                        // Checked before allocating: the records must lie in the font, and be paid for.
                        if (at + 16L + stride * cells > b.limit()) throw IndexOutOfBoundsException("Class pair records run past the font")
                        charge(cells)
                        IntArray(cells.toInt()) { s16(at + 16 + stride * it + xAdvance) }
                    }
                    ClassPairs(0, coverage, first, second, class2Count, values)
                }
                else -> null
            }
        }

        /** A pair set's second glyphs and X advances, parsed once per offset and record layout. */
        private fun pairSet(at: Int, stride: Int, xAdvance: Int): Pair<IntArray, IntArray> {
            val key = (at.toLong() shl 16) or (stride.toLong() shl 8) or (xAdvance + 1).toLong()
            pairSets[key]?.let { return it }
            val count = u16(at)
            if (at + 2L + stride.toLong() * count > b.limit()) throw IndexOutOfBoundsException("Pair set runs past the font")
            charge(2L * count)
            val seconds = IntArray(count) { u16(at + 2 + stride * it) }
            val advances = IntArray(count) { if (xAdvance < 0) 0 else s16(at + 2 + stride * it + 2 + xAdvance) }
            return (seconds to advances).also { pairSets[key] = it }
        }

        private fun coverage(at: Int): Coverage = coverages.getOrPut(at) {
            when (u16(at)) {
                1 -> {
                    val count = u16(at + 2)
                    charge(2L * count)
                    val glyphs = IntArray(count) { u16(at + 4 + 2 * it) }
                    Coverage(glyphs, glyphs, IntArray(count) { it })
                }
                2 -> {
                    val count = u16(at + 2)
                    charge(3L * count)
                    Coverage(
                        IntArray(count) { u16(at + 4 + 6 * it) },
                        IntArray(count) { u16(at + 6 + 6 * it) },
                        IntArray(count) { u16(at + 8 + 6 * it) },
                    )
                }
                else -> Coverage(IntArray(0), IntArray(0), IntArray(0))
            }
        }

        private fun classDef(at: Int): ClassDef = classDefs.getOrPut(at) {
            when (u16(at)) {
                1 -> {
                    val start = u16(at + 2)
                    val count = u16(at + 4)
                    charge(2L * count)
                    val glyphs = IntArray(count) { start + it }
                    ClassDef(glyphs, glyphs, IntArray(count) { u16(at + 6 + 2 * it) })
                }
                2 -> {
                    val count = u16(at + 2)
                    charge(3L * count)
                    ClassDef(
                        IntArray(count) { u16(at + 4 + 6 * it) },
                        IntArray(count) { u16(at + 6 + 6 * it) },
                        IntArray(count) { u16(at + 8 + 6 * it) },
                    )
                }
                else -> ClassDef(IntArray(0), IntArray(0), IntArray(0))
            }
        }
    }

    internal companion object {
        /**
         * Work units (entries read or allocated) allowed per byte of GPOS table, plus a floor for tiny tables. Every
         * entry of a real table is backed by at least 2 of its bytes; the margin covers per-structure overhead.
         */
        const val BUDGET_PER_BYTE = 4L
        const val BUDGET_BASE = 4096L

        /** Offset and length of the table [tag], or null. */
        fun findTable(b: ByteBuffer, tag: String): Pair<Int, Int>? {
            val count = b.getShort(4).toInt() and 0xFFFF
            for (i in 0 until count) {
                val record = 12 + 16 * i
                val name = String(CharArray(4) { (b.get(record + it).toInt() and 0xFF).toChar() })
                if (name == tag) return b.getInt(record + 8) to b.getInt(record + 12)
            }
            return null
        }
    }
}

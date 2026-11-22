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

    init {
        val buf = font.duplicate().order(ByteOrder.BIG_ENDIAN)
        // A resource pack can replace the font: a broken table must cost the kerning, never the game. Every read is
        // bounds-checked by the buffer, so a bad offset or count ends up here.
        subtables = try {
            findTable(buf, "GPOS")?.let { readKernSubtables(buf, it) } ?: emptyList()
        } catch (e: IndexOutOfBoundsException) {
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

    private companion object {
        fun u16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt() and 0xFFFF
        fun s16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt()
        fun u32(b: ByteBuffer, at: Int): Int = b.getInt(at)

        fun findTable(b: ByteBuffer, tag: String): Int? {
            val count = u16(b, 4)
            for (i in 0 until count) {
                val record = 12 + 16 * i
                val name = String(CharArray(4) { (b.get(record + it).toInt() and 0xFF).toChar() })
                if (name == tag) return u32(b, record + 8)
            }
            return null
        }

        fun readKernSubtables(b: ByteBuffer, gpos: Int): List<Subtable> {
            val featureList = gpos + u16(b, gpos + 6)
            val lookupList = gpos + u16(b, gpos + 8)

            // Every lookup any `kern` feature uses, whatever the script: the phone only sets French and English.
            val lookups = sortedSetOf<Int>()
            for (i in 0 until u16(b, featureList)) {
                val record = featureList + 2 + 6 * i
                val tag = String(CharArray(4) { (b.get(record + it).toInt() and 0xFF).toChar() })
                if (tag != "kern") continue
                val feature = featureList + u16(b, record + 4)
                for (j in 0 until u16(b, feature + 2)) lookups.add(u16(b, feature + 4 + 2 * j))
            }

            // OpenType applies lookups in lookup-list order, not in the feature's order.
            val result = ArrayList<Subtable>()
            for (index in lookups) {
                val lookup = lookupList + u16(b, lookupList + 2 + 2 * index)
                val type = u16(b, lookup)
                for (j in 0 until u16(b, lookup + 4)) {
                    var sub = lookup + u16(b, lookup + 6 + 2 * j)
                    var subType = type
                    if (type == 9) {
                        subType = u16(b, sub + 2)
                        sub += u32(b, sub + 4)
                    }
                    if (subType != 2) continue
                    readPairPos(b, sub, index)?.let(result::add)
                }
            }
            return result
        }

        fun readPairPos(b: ByteBuffer, at: Int, lookup: Int): Subtable? {
            val format = u16(b, at)
            val coverage = readCoverage(b, at + u16(b, at + 2))
            val format1 = u16(b, at + 4)
            val format2 = u16(b, at + 6)
            val size1 = 2 * Integer.bitCount(format1 and 0xFF)
            val size2 = 2 * Integer.bitCount(format2 and 0xFF)
            // Value record fields come in bit order: X placement (0x1), Y placement (0x2), then X advance (0x4).
            val xAdvance = if (format1 and 0x4 != 0) 2 * Integer.bitCount(format1 and 0x3) else -1

            return when (format) {
                1 -> {
                    val setCount = u16(b, at + 8)
                    val pairs = arrayOfNulls<IntArray>(setCount)
                    val values = arrayOfNulls<IntArray>(setCount)
                    for (i in 0 until setCount) {
                        val set = at + u16(b, at + 10 + 2 * i)
                        val count = u16(b, set)
                        val stride = 2 + size1 + size2
                        pairs[i] = IntArray(count) { u16(b, set + 2 + stride * it) }
                        values[i] = IntArray(count) { if (xAdvance < 0) 0 else s16(b, set + 2 + stride * it + 2 + xAdvance) }
                    }
                    @Suppress("UNCHECKED_CAST")
                    PairSets(lookup, coverage, pairs as Array<IntArray>, values as Array<IntArray>)
                }
                2 -> {
                    val first = readClassDef(b, at + u16(b, at + 8))
                    val second = readClassDef(b, at + u16(b, at + 10))
                    val class1Count = u16(b, at + 12)
                    val class2Count = u16(b, at + 14)
                    val stride = size1 + size2
                    // Checked before allocating: two broken counts would otherwise ask for gigabytes.
                    if (at + 16L + stride.toLong() * class1Count * class2Count > b.limit()) {
                        throw IndexOutOfBoundsException("Class pair records run past the end of the font")
                    }
                    // Without an X advance every pair kerns by 0 (and getOrElse gives 0).
                    val values = if (xAdvance < 0) IntArray(0) else IntArray(class1Count * class2Count) {
                        s16(b, at + 16 + stride * it + xAdvance)
                    }
                    ClassPairs(lookup, coverage, first, second, class2Count, values)
                }
                else -> null
            }
        }

        fun readCoverage(b: ByteBuffer, at: Int): Coverage = when (u16(b, at)) {
            1 -> {
                val count = u16(b, at + 2)
                val glyphs = IntArray(count) { u16(b, at + 4 + 2 * it) }
                Coverage(glyphs, glyphs, IntArray(count) { it })
            }
            2 -> {
                val count = u16(b, at + 2)
                Coverage(
                    IntArray(count) { u16(b, at + 4 + 6 * it) },
                    IntArray(count) { u16(b, at + 6 + 6 * it) },
                    IntArray(count) { u16(b, at + 8 + 6 * it) },
                )
            }
            else -> Coverage(IntArray(0), IntArray(0), IntArray(0))
        }

        fun readClassDef(b: ByteBuffer, at: Int): ClassDef = when (u16(b, at)) {
            1 -> {
                val start = u16(b, at + 2)
                val count = u16(b, at + 4)
                val glyphs = IntArray(count) { start + it }
                ClassDef(glyphs, glyphs, IntArray(count) { u16(b, at + 6 + 2 * it) })
            }
            2 -> {
                val count = u16(b, at + 2)
                ClassDef(
                    IntArray(count) { u16(b, at + 4 + 6 * it) },
                    IntArray(count) { u16(b, at + 6 + 6 * it) },
                    IntArray(count) { u16(b, at + 8 + 6 * it) },
                )
            }
            else -> ClassDef(IntArray(0), IntArray(0), IntArray(0))
        }
    }
}

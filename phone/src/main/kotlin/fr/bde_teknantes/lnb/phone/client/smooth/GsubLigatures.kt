package fr.bde_teknantes.lnb.phone.client.smooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ligatures read from a font's OpenType `GSUB` table: how the emoji font composes a sequence of glyphs into one (a
 * thumbs-up and its skin tone, a family joined with ZWJ, a flag's two letters, a keycap, a subdivision flag's tags).
 *
 * Only what composing emoji needs is read: lookups of type 4 (ligature substitution) used by the `ccmp`, `liga` or
 * `rlig` features, also when wrapped in type 7 (extension). Lookup flags are ignored (emoji fonts use none), and
 * contextual lookups are not applied: what they do in Noto Emoji (an unknown flag pair shown as a generic flag) is left
 * to the caller, which draws the pair's two letters instead.
 *
 * [font] is the whole font file; it is read once, in the constructor. Glyph ids, as everywhere in OpenType.
 */
class GsubLigatures(font: ByteBuffer) {
    private val subtables: List<Subtable>

    /** Work units the parse used, and the budget it had (both 0 without a GSUB table); for tests. */
    internal var workUsed = 0L
        private set
    internal var workAllowed = 0L
        private set

    init {
        val buf = font.duplicate().order(ByteOrder.BIG_ENDIAN)
        // As for kerning (GposKerning): a resource pack may replace the font, so a broken or hostile table costs the
        // ligatures, never the game. Reads are bounds-checked by the buffer; work and memory are bounded by the
        // table's real size.
        subtables = try {
            GposKerning.findTable(buf, "GSUB")?.let { (offset, length) ->
                val parser = Parser(buf, length)
                workAllowed = parser.allowed
                parser.readLigatureSubtables(offset).also { workUsed = parser.allowed - parser.left }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        } catch (e: OutOfMemoryError) {
            // Not expected within the budget; if it happens all the same, the allocation that failed is not kept.
            emptyList()
        }
    }

    val isEmpty: Boolean get() = subtables.isEmpty()

    /** Number of ligatures read, for tests and logs. */
    val size: Int get() = subtables.sumOf { sub -> sub.sets.sumOf { it.size } }

    /**
     * The ligature that starts at `glyphs[from]` and uses only glyphs before [count]: the first one listed by the
     * first subtable that has one, as OpenType applies them. Packed as `(ligature glyph shl 32) or components`, or 0
     * when none applies.
     */
    fun ligature(glyphs: IntArray, from: Int, count: Int): Long {
        if (from !in 0 until count) return 0L
        for (sub in subtables) {
            val index = sub.coverage.indexOf(glyphs[from])
            if (index !in sub.sets.indices) continue
            for (lig in sub.sets[index]) {
                val rest = lig.components
                if (from + 1 + rest.size > count) continue
                var k = 0
                while (k < rest.size && glyphs[from + 1 + k] == rest[k]) k++
                if (k == rest.size) return (lig.glyph.toLong() shl 32) or (rest.size + 1).toLong()
            }
        }
        return 0L
    }

    /** One ligature: the glyph it becomes, and the components after the first. */
    private class Ligature(val glyph: Int, val components: IntArray)

    /** A ligature subtable: the first components it covers, and for each the ligatures that start with it. */
    private class Subtable(val coverage: Coverage, val sets: Array<Array<Ligature>>)

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

    /** Thrown when a table asks for more work or memory than its size can justify. */
    private class TooLarge : RuntimeException("GSUB table asks for more than its size allows")

    /**
     * Reads the ligature lookups of one GSUB table of [length] bytes, with the same defences as the kerning reader:
     * every structure is parsed once per offset (shared ones are reused) and every entry read or allocated is charged
     * to a budget proportional to the table's size, so overlapping offsets cannot multiply a small table into a huge
     * one. The budget also has a ceiling ([MAX_BUDGET]), since what a query may scan is bounded by it and queries come
     * from drawing: a big table, honest about its size, still can't make one emoji cost milliseconds. A table that runs
     * out gets no ligatures at all.
     */
    private class Parser(private val b: ByteBuffer, length: Int) {
        val allowed = minOf(BUDGET_PER_BYTE * length.coerceAtLeast(0) + BUDGET_BASE, MAX_BUDGET)
        private var budget = allowed
        val left: Long get() = budget
        private val coverages = HashMap<Int, Coverage>()
        private val ligatureSets = HashMap<Int, Array<Ligature>>()
        private val bodies = HashMap<Int, Subtable?>()

        private fun charge(units: Long) {
            budget -= units
            if (budget < 0) throw TooLarge()
        }

        private fun u16(at: Int): Int = b.getShort(at).toInt() and 0xFFFF
        private fun u32(at: Int): Int = b.getInt(at)

        private fun tag(at: Int) = String(CharArray(4) { (b.get(at + it).toInt() and 0xFF).toChar() })

        fun readLigatureSubtables(gsub: Int): List<Subtable> {
            val featureList = gsub + u16(gsub + 6)
            val lookupList = gsub + u16(gsub + 8)

            val lookups = sortedSetOf<Int>()
            val featureCount = u16(featureList)
            charge(featureCount.toLong())
            for (i in 0 until featureCount) {
                val record = featureList + 2 + 6 * i
                if (tag(record) !in FEATURES) continue
                val feature = featureList + u16(record + 4)
                val count = u16(feature + 2)
                charge(count.toLong())
                for (j in 0 until count) lookups.add(u16(feature + 4 + 2 * j))
            }

            // In lookup-list order, as OpenType applies them.
            val result = ArrayList<Subtable>()
            // A subtable that several lookups (or one lookup several times) point at is used once: a repeat can never
            // match what its first use did not, and each use would be scanned again for every ligature asked for.
            val seen = HashSet<Int>()
            val lookupCount = u16(lookupList)
            for (index in lookups) {
                if (index >= lookupCount) continue
                val lookup = lookupList + u16(lookupList + 2 + 2 * index)
                val type = u16(lookup)
                val count = u16(lookup + 4)
                charge(count.toLong())
                for (j in 0 until count) {
                    var sub = lookup + u16(lookup + 6 + 2 * j)
                    var subType = type
                    if (type == 7) {
                        subType = u16(sub + 2)
                        sub += u32(sub + 4)
                    }
                    if (subType != 4 || !seen.add(sub)) continue
                    body(sub)?.let(result::add)
                }
            }
            return result
        }

        /** The subtable at [at], parsed once however many lookups point at it. */
        private fun body(at: Int): Subtable? = if (at in bodies) bodies[at] else readLigatureSubst(at).also { bodies[at] = it }

        private fun readLigatureSubst(at: Int): Subtable? {
            charge(OBJECT_COST)
            if (u16(at) != 1) return null
            val coverage = coverage(at + u16(at + 2))
            val setCount = u16(at + 4)
            charge(setCount.toLong())
            val sets = Array(setCount) { ligatureSet(at + u16(at + 6 + 2 * it)) }
            // Asking for a ligature scans a set, so every reference to a set counts, shared or not: many subtables (or
            // many entries of one) sharing a big set would otherwise multiply a small table into minutes of scanning
            // for each emoji drawn. A real font's sets are not shared, so this costs it what its ligatures already did.
            charge(sets.sumOf { it.size.toLong() })
            return Subtable(coverage, sets)
        }

        /** A ligature set, parsed once per offset; ligatures longer than any real emoji sequence are skipped. */
        private fun ligatureSet(at: Int): Array<Ligature> = ligatureSets.getOrPut(at) {
            val count = u16(at)
            charge(count.toLong() + OBJECT_COST)
            val ligatures = ArrayList<Ligature>(count)
            for (i in 0 until count) {
                val lig = at + u16(at + 2 + 2 * i)
                val glyph = u16(lig)
                val components = u16(lig + 2)
                if (components < 2 || components > MAX_COMPONENTS) continue
                // Objects cost what they weigh, so memory is bounded by the budget like the work.
                charge(components.toLong() + OBJECT_COST)
                ligatures.add(Ligature(glyph, IntArray(components - 1) { u16(lig + 4 + 2 * it) }))
            }
            ligatures.toTypedArray()
        }

        private fun coverage(at: Int): Coverage = coverages.getOrPut(at) {
            charge(OBJECT_COST)
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
    }

    internal companion object {
        /** The features whose ligatures are applied: emoji fonts compose in `ccmp`, some in `liga` or `rlig`. */
        val FEATURES = setOf("ccmp", "liga", "rlig")

        /** Longer than any emoji sequence (a kiss with two skin tones is 10 components); longer ones are skipped. */
        const val MAX_COMPONENTS = 16

        /**
         * Work units allowed per byte of GSUB table, and a floor for tiny tables. More per byte than for kerning: a
         * ligature is a few bytes that become an object ([OBJECT_COST]), and Noto Emoji's 3,770 of them in 38 KB use
         * about 81,000 units, an eighth of what this allows it.
         */
        const val BUDGET_PER_BYTE = 16L
        const val BUDGET_BASE = 4096L

        /**
         * The most work any GSUB table may cost, whatever its size: twelve times what Noto Emoji uses, room for a
         * bigger emoji font, while a query, which scans no more entries than were paid for, stays around a millisecond
         * in the worst case, and memory in a few megabytes.
         */
        const val MAX_BUDGET = 1_000_000L

        /** What an object kept for good costs, in work units, as for kerning ([GposKerning.OBJECT_COST]). */
        const val OBJECT_COST = GposKerning.OBJECT_COST
    }
}

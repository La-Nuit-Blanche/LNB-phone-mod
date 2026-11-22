package fr.bde_teknantes.lnb.phone.client.smooth

import fr.bde_teknantes.lnb.phone.client.smooth.GsubFontBuilder.Ligature
import fr.bde_teknantes.lnb.phone.client.smooth.GsubFontBuilder.Lookup
import fr.bde_teknantes.lnb.phone.client.smooth.GsubFontBuilder.Subtable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.time.Duration
import kotlin.random.Random

/**
 * The GSUB reader on exact hand-made tables, and on tables broken in every way a crafted font can: truncated, corrupted,
 * every 16-bit field at an extreme, offsets pointing at themselves or each other, counts promising more than the table
 * holds, and many references to one big shared structure. The model is [GposBudgetTest]: a table must be read quickly
 * and in little memory, correctly or not at all, and then also *used* (asked for ligatures) quickly, since the renderer
 * asks for every emoji it draws.
 */
class GsubHostileTest {
    private fun parse(bytes: ByteArray) = GsubLigatures(ByteBuffer.wrap(bytes))

    private fun pack(glyph: Int, components: Int) = (glyph.toLong() shl 32) or components.toLong()

    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    /** Runs [block] on this thread and returns its result and what it allocated, in bytes. */
    private fun <T> measured(block: () -> T): Pair<T, Long> {
        val id = Thread.currentThread().threadId()
        val before = threads.getThreadAllocatedBytes(id)
        val result = block()
        return result to threads.getThreadAllocatedBytes(id) - before
    }

    /** Asks for the ligatures that start at a few glyphs, in a few contexts; whatever the table holds. */
    private fun use(gsub: GsubLigatures) {
        val probes = listOf(
            intArrayOf(1, 2, 3, 4), intArrayOf(10, 11, 12), intArrayOf(20, 21), intArrayOf(30, 31), intArrayOf(0, 0, 0),
            intArrayOf(65535, 65535), intArrayOf(5), intArrayOf(),
        )
        for (glyphs in probes) {
            for (from in -1..glyphs.size) gsub.ligature(glyphs, from, glyphs.size)
        }
    }

    private val ten = Subtable(
        coverage = listOf(10, 20, 30),
        sets = listOf(
            listOf(Ligature(100, listOf(11)), Ligature(101, listOf(11, 12))),
            listOf(Ligature(102, listOf(21))),
            listOf(Ligature(103, listOf(31))),
        ),
    )

    private fun small() = GsubFontBuilder.font(Lookup(listOf(ten)))

    // ---- Exact data ----

    @Test
    fun `reads a hand-made ligature table, the first listed ligature winning`() {
        val gsub = parse(small())
        assertFalse(gsub.isEmpty)
        assertEquals(4, gsub.size)
        assertEquals(pack(100, 2), gsub.ligature(intArrayOf(10, 11), 0, 2))
        // The longer one is listed second: OpenType applies the first match, and so does the reader.
        assertEquals(pack(100, 2), gsub.ligature(intArrayOf(10, 11, 12), 0, 3))
        assertEquals(pack(102, 2), gsub.ligature(intArrayOf(20, 21), 0, 2))
        assertEquals(pack(103, 2), gsub.ligature(intArrayOf(9, 30, 31), 1, 3))
        assertEquals(0L, gsub.ligature(intArrayOf(10, 12), 0, 2))
        assertEquals(0L, gsub.ligature(intArrayOf(10), 0, 1))
        assertEquals(0L, gsub.ligature(intArrayOf(10, 11), 0, 1), "components must lie before count")
        assertEquals(0L, gsub.ligature(intArrayOf(10, 11), 1, 2))
        assertEquals(0L, gsub.ligature(intArrayOf(10, 11), -1, 2))
        assertEquals(0L, gsub.ligature(intArrayOf(10, 11), 2, 2))
    }

    @Test
    fun `range coverage, extension lookups, several subtables and lookups all work`() {
        val ranges = Subtable(listOf(40, 41, 42, 60), listOf(listOf(Ligature(200, listOf(1))), listOf(Ligature(201, listOf(1))), listOf(Ligature(202, listOf(1))), listOf(Ligature(203, listOf(1)))), rangeCoverage = true)
        val gsub = parse(GsubFontBuilder.font(Lookup(listOf(ten)), Lookup(listOf(ranges), extension = true)))
        assertEquals(pack(100, 2), gsub.ligature(intArrayOf(10, 11), 0, 2))
        assertEquals(pack(201, 2), gsub.ligature(intArrayOf(41, 1), 0, 2))
        assertEquals(pack(203, 2), gsub.ligature(intArrayOf(60, 1), 0, 2))
        assertEquals(0L, gsub.ligature(intArrayOf(43, 1), 0, 2))
        // An earlier subtable wins over a later one for the same first glyph.
        val clash = Subtable(listOf(10), listOf(listOf(Ligature(999, listOf(11)))))
        val ordered = parse(GsubFontBuilder.font(Lookup(listOf(ten, clash))))
        assertEquals(pack(100, 2), ordered.ligature(intArrayOf(10, 11), 0, 2))
    }

    @Test
    fun `ccmp, liga and rlig features are applied, others are not`() {
        for (tag in listOf("ccmp", "liga", "rlig")) {
            assertFalse(parse(GsubFontBuilder.font(Lookup(listOf(ten)), featureTag = tag)).isEmpty, tag)
        }
        for (tag in listOf("calt", "ss01", "dlig", "kern", "CCMP", "")) {
            assertTrue(parse(GsubFontBuilder.font(Lookup(listOf(ten)), featureTag = tag.padEnd(4, ' '))).isEmpty, tag)
        }
    }

    @Test
    fun `ligatures of two to sixteen components are kept, one component and seventeen are skipped`() {
        val rest = { n: Int -> List(n) { 50 + it } }
        val sets = listOf(0, 1, 15, 16, 40).mapIndexed { i, n -> listOf(Ligature(i + 1, rest(n))) }
        val gsub = parse(GsubFontBuilder.font(Lookup(listOf(Subtable(listOf(7, 8, 9, 10, 11), sets)))))
        assertEquals(2, gsub.size)
        assertEquals(0L, gsub.ligature(intArrayOf(7) , 0, 1))
        assertEquals(pack(2, 2), gsub.ligature(intArrayOf(8) + rest(1).toIntArray(), 0, 2))
        assertEquals(pack(3, 16), gsub.ligature(intArrayOf(9) + rest(15).toIntArray(), 0, 16))
        assertEquals(0L, gsub.ligature(intArrayOf(10) + rest(16).toIntArray(), 0, 17))
        assertEquals(0L, gsub.ligature(intArrayOf(11) + rest(40).toIntArray(), 0, 41))
    }

    @Test
    fun `a font with no GSUB, an empty one, or tags that are not tables has no ligatures`() {
        assertTrue(parse(GposFontBuilder.fontWithoutGpos()).isEmpty)
        assertTrue(parse(ByteArray(0)).isEmpty)
        assertTrue(parse(ByteArray(11)).isEmpty)
        assertTrue(parse(GsubFontBuilder.font(Lookup(emptyList()))).isEmpty)
        assertTrue(parse(GsubFontBuilder.font(Lookup(listOf(Subtable(emptyList(), emptyList()))))).isEmpty.not())
    }

    // ---- Truncation and corruption ----

    @Test
    fun `truncated at every length, the hand-made font has all its ligatures or none, never an exception`() {
        val bytes = GsubFontBuilder.font(Lookup(listOf(ten)), Lookup(listOf(ten), extension = true))
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            for (n in 0..bytes.size) {
                val gsub = parse(bytes.copyOf(n))
                use(gsub)
                if (n < bytes.size) {
                    // Cut anywhere inside the table: reads past the end fail, so either nothing or what was complete.
                    assertTrue(gsub.size == 0 || gsub.size == 4 || gsub.size == 8, "n=$n size=${gsub.size}")
                } else {
                    assertEquals(8, gsub.size)
                }
            }
        }
    }

    @Test
    fun `the real font truncated at many lengths never throws, and a truncated table gives no ligatures at all or a prefix`() {
        val bytes = InterMetrics.bytes("noto_emoji.ttf")
        val (offset, length) = GposKerning.findTable(ByteBuffer.wrap(bytes), "GSUB")!!
        val full = parse(bytes).size
        val random = Random(3)
        // Every length would cost minutes (a parse reads a megabyte): the table's first and last kilobytes in steps of
        // 7, and 250 random lengths in between.
        val lengths = (offset until offset + 1024 step 7) + (offset + length - 1024 until offset + length step 7) +
            List(250) { offset + random.nextInt(length) } + listOf(0, 11, 12, 27, 28, offset, offset + 1, offset + 10, offset + length - 1)
        assertTimeoutPreemptively(Duration.ofSeconds(120)) {
            for (n in lengths) {
                val gsub = parse(bytes.copyOf(n.coerceAtMost(bytes.size)))
                assertTrue(gsub.size <= full, "n=$n")
                assertTrue(gsub.workUsed <= gsub.workAllowed)
            }
        }
    }

    @Test
    fun `random corruption of the real font's GSUB, aimed at its headers and offsets, stays within budget`() {
        val bytes = InterMetrics.bytes("noto_emoji.ttf")
        val (offset, length) = GposKerning.findTable(ByteBuffer.wrap(bytes), "GSUB")!!
        val random = Random(99)
        assertTimeoutPreemptively(Duration.ofSeconds(120)) {
            repeat(400) {
                val copy = bytes.copyOf()
                // The structure part (lists and subtable headers) is at the start of the table; the data after it.
                val span = if (it % 2 == 0) minOf(length, 4096) else length
                repeat(1 + random.nextInt(12)) { copy[offset + random.nextInt(span)] = random.nextInt(256).toByte() }
                val gsub = parse(copy)
                assertTrue(gsub.workUsed <= gsub.workAllowed)
                use(gsub)
            }
        }
    }

    // ---- Every field at an extreme ----

    @Test
    fun `every 16-bit field of the hand-made font set to an extreme is read quickly, in little memory, or not at all`() {
        val base = GsubFontBuilder.font(Lookup(listOf(ten)), Lookup(listOf(ten), extension = true))
        val extremes = listOf(0, 1, 2, 0x7FFF, 0x8000, 0xFFFE, 0xFFFF)
        var parses = 0
        var worst = 0L
        assertTimeoutPreemptively(Duration.ofSeconds(60)) {
            for (at in GsubFontBuilder.TABLE_START until base.size - 1 step 2) {
                for (value in extremes) {
                    val copy = base.copyOf()
                    copy[at] = (value ushr 8).toByte()
                    copy[at + 1] = value.toByte()
                    val (gsub, bytes) = measured { parse(copy) }
                    worst = maxOf(worst, bytes)
                    use(gsub)
                    assertTrue(gsub.workUsed <= gsub.workAllowed, "at $at value $value")
                    parses++
                }
            }
        }
        assertTrue(parses > 500, "$parses parses")
        assertTrue(worst < 8L shl 20, "worst parse allocated $worst bytes for a ${base.size}-byte font")
    }

    @Test
    fun `every 16-bit field near the start of the real font's GSUB set to an extreme is handled`() {
        val bytes = InterMetrics.bytes("noto_emoji.ttf")
        val (offset, length) = GposKerning.findTable(ByteBuffer.wrap(bytes), "GSUB")!!
        val extremes = listOf(0, 1, 0x7FFF, 0xFFFF)
        val random = Random(5)
        // The first 600 fields (headers, feature list, lookup list, first subtable headers), then 600 more anywhere.
        val positions = (0 until 600).map { offset + 2 * it } + List(600) { offset + 2 * random.nextInt(length / 2 - 1) }
        var worst = 0L
        assertTimeoutPreemptively(Duration.ofSeconds(180)) {
            for (at in positions) {
                for (value in extremes) {
                    val copy = bytes.copyOf()
                    copy[at] = (value ushr 8).toByte()
                    copy[at + 1] = value.toByte()
                    val (gsub, bytes) = measured { parse(copy) }
                    worst = maxOf(worst, bytes)
                    assertTrue(gsub.workUsed <= gsub.workAllowed, "at ${at - offset} value $value")
                    use(gsub)
                }
            }
        }
        // The real parse itself allocates a few megabytes (about 3,000 ligatures and their coverage).
        assertTrue(worst < 64L shl 20, "worst parse allocated $worst bytes")
    }

    // ---- Offsets pointing at themselves or each other, counts promising more than the table holds ----

    private fun patch(bytes: ByteArray, at: Int, value: Int, width: Int = 2): ByteArray {
        val copy = bytes.copyOf()
        for (i in 0 until width) copy[at + i] = (value ushr (8 * (width - 1 - i))).toByte()
        return copy
    }

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF shl 8) or (b[at + 1].toInt() and 0xFF)

    @Test
    fun `lookups, subtables, sets and coverage that point at themselves or each other are read once or not at all`() {
        val base = GsubFontBuilder.font(Lookup(listOf(ten)))
        val gsub0 = GsubFontBuilder.TABLE_START
        val lookupList = gsub0 + u16(base, gsub0 + 8)
        val lookup = lookupList + u16(base, lookupList + 2)
        val sub = lookup + u16(base, lookup + 6)
        val setsAt = sub + 6
        val cases = mapOf(
            "lookup list entry points at the list" to patch(base, lookupList + 2, 0),
            "lookup's subtable points at the lookup" to patch(base, lookup + 6, 0),
            "subtable's coverage points at the subtable" to patch(base, sub + 2, 0),
            "a ligature set points at the subtable" to patch(base, setsAt, 0),
            "every ligature set points at the first" to patch(patch(base, setsAt + 2, u16(base, setsAt)), setsAt + 4, u16(base, setsAt)),
            "a ligature points at its set" to patch(base, sub + u16(base, setsAt) + 2, 0),
        )
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            for ((name, bytes) in cases) {
                val gsub = parse(bytes)
                use(gsub)
                assertTrue(gsub.workUsed <= gsub.workAllowed, name)
            }
        }
    }

    @Test
    fun `extension subtables that point at themselves, at each other or backwards are skipped or read once`() {
        val base = GsubFontBuilder.font(Lookup(listOf(ten, ten), extension = true))
        val gsub0 = GsubFontBuilder.TABLE_START
        val lookupList = gsub0 + u16(base, gsub0 + 8)
        val lookup = lookupList + u16(base, lookupList + 2)
        val first = lookup + u16(base, lookup + 6)
        val second = lookup + u16(base, lookup + 8)
        val cases = mapOf(
            "extension offset 0 (itself)" to patch(base, first + 4, 0, 4),
            "extension offsets pointing at each other" to patch(patch(base, first + 4, second - first, 4), second + 4, first - second, 4),
            "extension offset negative" to patch(base, first + 4, -1000, 4),
            "extension offset huge" to patch(base, first + 4, 0x7FFFFFFF, 4),
            "extension offset negative huge" to patch(base, first + 4, Int.MIN_VALUE, 4),
            "extension of extension, itself, type 4" to patch(patch(base, first + 4, 0, 4), first + 2, 4),
            "extension type 7 inside" to patch(base, first + 2, 7),
        )
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            for ((name, bytes) in cases) {
                val gsub = parse(bytes)
                use(gsub)
                assertTrue(gsub.workUsed <= gsub.workAllowed, name)
            }
        }
    }

    @Test
    fun `counts that promise more than the table holds give no ligatures, quickly and in little memory`() {
        val base = small()
        val gsub0 = GsubFontBuilder.TABLE_START
        val featureList = gsub0 + u16(base, gsub0 + 6)
        val feature = featureList + u16(base, featureList + 6)
        val lookupList = gsub0 + u16(base, gsub0 + 8)
        val lookup = lookupList + u16(base, lookupList + 2)
        val sub = lookup + u16(base, lookup + 6)
        val coverage = sub + u16(base, sub + 2)
        val set = sub + u16(base, sub + 6)
        val ligature = set + u16(base, set + 2)
        val cases = mapOf(
            "feature count 65535" to patch(base, featureList, 0xFFFF),
            "feature's lookup count 65535" to patch(base, feature + 2, 0xFFFF),
            "lookup count 65535" to patch(base, lookupList, 0xFFFF),
            "lookup's subtable count 65535" to patch(base, lookup + 4, 0xFFFF),
            "set count 65535" to patch(base, sub + 4, 0xFFFF),
            "coverage count 65535" to patch(base, coverage + 2, 0xFFFF),
            "ligature count 65535" to patch(base, set, 0xFFFF),
            "component count 65535" to patch(base, ligature + 2, 0xFFFF),
            "table length 0xFFFFFFF0" to patch(base, 12 + 12, -16, 4),
            "table length 0" to patch(base, 12 + 12, 0, 4),
            "table offset past the end" to patch(base, 12 + 8, 0x7FFFFFF0, 4),
            "table count 65535" to patch(base, 4, 0xFFFF),
        )
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            for ((name, bytes) in cases) {
                val (gsub, used) = measured { parse(bytes) }
                assertTrue(used < 8L shl 20, "$name allocated $used")
                use(gsub)
                assertTrue(gsub.workUsed <= gsub.workAllowed, name)
            }
        }
    }

    // ---- Crafted to multiply ----

    /** A table built field by field: one lookup of [subtables] references to one subtable that has one set of [ligatures] references to one ligature. */
    private fun multiplier(subtables: Int, ligatures: Int, distinctSubtables: Boolean = false): ByteArray {
        val buf = ByteBuffer.allocate(4 shl 20)
        var end = 0
        fun u16(at: Int, v: Int) { buf.putShort(at, v.toShort()); end = maxOf(end, at + 2) }
        // Header (10), script list (2), feature list (2 + 6 + 6), lookup list.
        u16(0, 1); u16(2, 0)
        u16(4, 10); u16(6, 12); u16(8, 28)
        u16(10, 0)
        u16(12, 1); buf.put(14, 'c'.code.toByte()); buf.put(15, 'c'.code.toByte()); buf.put(16, 'm'.code.toByte()); buf.put(17, 'p'.code.toByte())
        u16(18, 8); u16(20, 0); u16(22, 1); u16(24, 0)
        // Lookup list at 28: one lookup at +4.
        u16(28, 1); u16(30, 4)
        val lookup = 28 + 4
        u16(lookup, 4); u16(lookup + 2, 0); u16(lookup + 4, subtables)
        val subsAt = lookup + 6 + 2 * subtables
        // Each subtable is 10 bytes (format, coverage offset, set count, one set offset), then they share one coverage
        // and one ligature set, whose ligatures all are one record.
        val bodySize = if (distinctSubtables) 10 else 0
        val bodies = subsAt
        val coverage = bodies + (if (distinctSubtables) 10 * subtables else 10)
        val set = coverage + 6
        val ligature = set + 2 + 2 * ligatures
        for (i in 0 until subtables) u16(lookup + 6 + 2 * i, bodies - lookup + if (distinctSubtables) 10 * i else 0)
        for (i in 0 until if (distinctSubtables) subtables else 1) {
            val b = bodies + 10 * i
            u16(b, 1); u16(b + 2, coverage - b); u16(b + 4, 1); u16(b + 6, set - b)
        }
        u16(coverage, 1); u16(coverage + 2, 1); u16(coverage + 4, 1)
        u16(set, ligatures)
        for (i in 0 until ligatures) u16(set + 2 + 2 * i, ligature - set)
        u16(ligature, 99); u16(ligature + 2, 2); u16(ligature + 4, 55)
        @Suppress("UNUSED_VARIABLE") val unused = bodySize
        val gsub = buf.array().copyOf(end)
        val font = ByteBuffer.allocate(28 + gsub.size)
        font.putInt(0x00010000).putShort(1).putShort(16).putShort(0).putShort(0)
        font.put("GSUB".toByteArray()).putInt(0).putInt(28).putInt(gsub.size)
        font.put(gsub)
        return font.array()
    }

    @Test
    fun `the multiplier table is well formed, and gives its ligature when small`() {
        val gsub = parse(multiplier(subtables = 2, ligatures = 3))
        assertFalse(gsub.isEmpty)
        assertEquals(pack(99, 2), gsub.ligature(intArrayOf(1, 55), 0, 2))
        assertEquals(0L, gsub.ligature(intArrayOf(1, 56), 0, 2))
    }

    @Test
    fun `many subtables sharing one big ligature set cost no more per lookup than the table's size allows`() {
        // 20,000 references to one subtable that holds one set of 30,000 references to one ligature: about 100 KB, a
        // budget of 400,000 units, and 600 million comparisons for each ligature the renderer asks for if the
        // reader scans every reference. Asked for a first glyph that has the set and a second that matches none.
        val bytes = multiplier(subtables = 20_000, ligatures = 30_000)
        assertTrue(bytes.size < 200_000, "${bytes.size} bytes")
        val gsub = parse(bytes)
        val started = System.nanoTime()
        repeat(5) { gsub.ligature(intArrayOf(1, 56), 0, 2) }
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(millis < 500, "5 ligature lookups took $millis ms on a ${bytes.size}-byte table (${gsub.size} ligatures)")
    }

    @Test
    fun `many distinct subtables sharing one big ligature set also stay quick to query`() {
        val bytes = multiplier(subtables = 8_000, ligatures = 30_000, distinctSubtables = true)
        val gsub = parse(bytes)
        val started = System.nanoTime()
        repeat(5) { gsub.ligature(intArrayOf(1, 56), 0, 2) }
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(millis < 500, "5 ligature lookups took $millis ms on a ${bytes.size}-byte table")
    }

    @Test
    fun `a real font stays far inside its budget`() {
        val gsub = parse(InterMetrics.bytes("noto_emoji.ttf"))
        assertTrue(gsub.workUsed * 4 < gsub.workAllowed, "used ${gsub.workUsed} of ${gsub.workAllowed}")
    }
}

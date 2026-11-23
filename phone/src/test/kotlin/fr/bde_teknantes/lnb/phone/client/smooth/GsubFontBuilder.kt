package fr.bde_teknantes.lnb.phone.client.smooth

import java.io.ByteArrayOutputStream

/**
 * Writes minimal font files holding only a GSUB table with ligature lookups, so [GsubLigatures] can be tested on exact,
 * hand-made data (and on that data broken field by field). Offsets follow the OpenType layout rules, each relative to
 * its parent table, as [GposFontBuilder] does for GPOS.
 */
object GsubFontBuilder {
    /** One ligature: the glyph it becomes, and the components after the first. */
    class Ligature(val glyph: Int, val rest: List<Int>)

    /** A ligature subtable: [coverage] lists the first glyphs, [sets] the ligatures of each, in coverage order. */
    class Subtable(val coverage: List<Int>, val sets: List<List<Ligature>>, val rangeCoverage: Boolean = false)

    /** A lookup of type 4, or wrapped in type 7 (extension) subtables when [extension]. */
    class Lookup(val subtables: List<Subtable>, val extension: Boolean = false)

    private class Bytes {
        private val out = ByteArrayOutputStream()
        val size get() = out.size()
        fun u16(v: Int) { out.write(v ushr 8 and 0xFF); out.write(v and 0xFF) }
        fun u32(v: Int) { u16(v ushr 16 and 0xFFFF); u16(v and 0xFFFF) }
        fun tag(t: String) { t.forEach { out.write(it.code) } }
        fun bytes(b: ByteArray) = out.write(b)
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    /** A font whose [featureTag] feature uses every lookup in [lookups], in order. */
    fun font(vararg lookups: Lookup, featureTag: String = "ccmp"): ByteArray = font(lookups.toList(), featureTag)

    fun font(lookups: List<Lookup>, featureTag: String): ByteArray {
        val gsub = gsub(lookups, featureTag)
        val out = Bytes()
        out.u32(0x00010000)
        out.u16(1)
        out.u16(16)
        out.u16(0)
        out.u16(0)
        out.tag("GSUB")
        out.u32(0)
        out.u32(12 + 16)
        out.u32(gsub.size)
        out.bytes(gsub)
        return out.toByteArray()
    }

    /** Where the GSUB table starts in a font made by [font]. */
    const val TABLE_START = 28

    private fun gsub(lookups: List<Lookup>, featureTag: String): ByteArray {
        val scriptList = Bytes().apply { u16(0) }.toByteArray()
        val feature = Bytes().apply {
            u16(0)
            u16(lookups.size)
            lookups.indices.forEach { u16(it) }
        }.toByteArray()
        val featureList = Bytes().apply {
            u16(1)
            tag(featureTag)
            u16(2 + 6)
            bytes(feature)
        }.toByteArray()
        val lookupBlocks = lookups.map(::lookup)
        val lookupList = Bytes().apply {
            u16(lookups.size)
            var offset = 2 + 2 * lookups.size
            for (block in lookupBlocks) {
                u16(offset)
                offset += block.size
            }
            lookupBlocks.forEach(::bytes)
        }.toByteArray()
        val header = 10
        return Bytes().apply {
            u32(0x00010000)
            u16(header)
            u16(header + scriptList.size)
            u16(header + scriptList.size + featureList.size)
            bytes(scriptList)
            bytes(featureList)
            bytes(lookupList)
        }.toByteArray()
    }

    private fun lookup(lookup: Lookup): ByteArray {
        val subs = lookup.subtables.map(::subtable)
        val headerSize = 6 + 2 * subs.size
        val out = Bytes()
        out.u16(if (lookup.extension) 7 else 4)
        out.u16(0)
        out.u16(subs.size)
        // With extension subtables: an 8-byte wrapper per subtable, then the subtables themselves after them.
        if (lookup.extension) {
            val wrappersEnd = headerSize + 8 * subs.size
            var wrapperOffset = headerSize
            for (i in subs.indices) {
                out.u16(wrapperOffset)
                wrapperOffset += 8
            }
            var bodyOffset = wrappersEnd
            for ((i, sub) in subs.withIndex()) {
                out.u16(1)
                out.u16(4)
                // Relative to the wrapper that starts here.
                out.u32(bodyOffset - (headerSize + 8 * i))
                bodyOffset += sub.size
            }
            subs.forEach(out::bytes)
        } else {
            var offset = headerSize
            for (sub in subs) {
                out.u16(offset)
                offset += sub.size
            }
            subs.forEach(out::bytes)
        }
        return out.toByteArray()
    }

    private fun subtable(sub: Subtable): ByteArray {
        val coverage = Bytes().apply {
            if (sub.rangeCoverage) {
                // One range per run of consecutive glyphs.
                val runs = ArrayList<Triple<Int, Int, Int>>()
                for ((index, glyph) in sub.coverage.withIndex()) {
                    val last = runs.lastOrNull()
                    if (last != null && last.second + 1 == glyph) runs[runs.size - 1] = Triple(last.first, glyph, last.third) else runs += Triple(glyph, glyph, index)
                }
                u16(2)
                u16(runs.size)
                for ((a, b, c) in runs) { u16(a); u16(b); u16(c) }
            } else {
                u16(1)
                u16(sub.coverage.size)
                sub.coverage.forEach(::u16)
            }
        }.toByteArray()
        val sets = sub.sets.map(::ligatureSet)
        val headerSize = 6 + 2 * sets.size
        val out = Bytes()
        out.u16(1)
        out.u16(headerSize + sets.sumOf { it.size })
        out.u16(sets.size)
        var offset = headerSize
        for (set in sets) {
            out.u16(offset)
            offset += set.size
        }
        sets.forEach(out::bytes)
        out.bytes(coverage)
        return out.toByteArray()
    }

    private fun ligatureSet(ligatures: List<Ligature>): ByteArray {
        val bodies = ligatures.map { lig ->
            Bytes().apply {
                u16(lig.glyph)
                u16(lig.rest.size + 1)
                lig.rest.forEach(::u16)
            }.toByteArray()
        }
        val out = Bytes()
        out.u16(ligatures.size)
        var offset = 2 + 2 * ligatures.size
        for (body in bodies) {
            out.u16(offset)
            offset += body.size
        }
        bodies.forEach(out::bytes)
        return out.toByteArray()
    }
}

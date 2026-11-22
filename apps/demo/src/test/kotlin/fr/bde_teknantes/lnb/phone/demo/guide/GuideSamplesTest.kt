package fr.bde_teknantes.lnb.phone.demo.guide

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Keeps the guide's code true: every code sample in `docs/writing-an-app.md` names the file it is lifted from, in a
 * `<!-- sample: <path from the repository root> -->` line right above it, and must still be found there. A sample's
 * lines (indentation and blank lines aside) must appear in the file one after the other and in the same order; a line
 * that is only `// ...` (or `# ...`) marks a cut, where the file may have more lines. The files are compiled or used by
 * the build, so a sample that passes here compiles.
 *
 * Shell commands and file trees (blocks marked `sh` or `text`, or with no language) need no source.
 */
class GuideSamplesTest {
    private val root = File(System.getProperty("lnbphone.repoRoot") ?: error("Run through Gradle: lnbphone.repoRoot is not set"))

    private class Sample(val line: Int, val language: String, val source: String?, val lines: List<String>)

    @Test
    fun `every sample of the guide is found in the file it names`() {
        val guide = root.resolve(GUIDE)
        val samples = samples(guide.readLines())
        assertTrue(samples.isNotEmpty(), "No samples found in $GUIDE")
        val problems = samples.mapNotNull(::check)
        if (problems.isNotEmpty()) fail<Unit>("${problems.size} sample(s) of $GUIDE don't match their source:\n\n" + problems.joinToString("\n\n"))
    }

    private fun check(sample: Sample): String? {
        val where = "$GUIDE line ${sample.line}"
        val path = sample.source ?: return "$where: a `${sample.language}` sample with no <!-- sample: path --> line above it"
        val file = root.resolve(path)
        if (!file.isFile) return "$where: $path doesn't exist"
        val source = normalise(file.readLines())
        var from = 0
        for (chunk in chunks(sample.lines)) {
            val at = indexOf(source, chunk, from)
            if (at < 0) return "$where: not found in $path, from:\n    " + chunk.joinToString("\n    ")
            from = at + chunk.size
        }
        return null
    }

    private fun samples(lines: List<String>): List<Sample> {
        val out = ArrayList<Sample>()
        var i = 0
        while (i < lines.size) {
            val fence = FENCE.matchEntire(lines[i].trim())
            if (fence == null) {
                i++
                continue
            }
            val language = fence.groupValues[1]
            val start = i
            val body = ArrayList<String>()
            i++
            while (i < lines.size && lines[i].trim() != "```") body += lines[i++]
            i++
            if (language in FREE) continue
            val marker = (start - 1 downTo 0).firstOrNull { lines[it].isNotBlank() }?.let { MARKER.matchEntire(lines[it].trim()) }
            out += Sample(start + 1, language, marker?.groupValues?.get(1), body)
        }
        return out
    }

    /** The sample's lines split at its cuts, each part without blank lines and indentation. */
    private fun chunks(lines: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var current = ArrayList<String>()
        for (line in lines) {
            val t = line.trim()
            when {
                t in CUTS -> {
                    if (current.isNotEmpty()) out += current
                    current = ArrayList()
                }
                t.isNotEmpty() -> current += t
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    private fun normalise(lines: List<String>) = lines.map(String::trim).filter(String::isNotEmpty)

    private fun indexOf(source: List<String>, chunk: List<String>, from: Int): Int =
        (from..source.size - chunk.size).firstOrNull { start -> chunk.indices.all { source[start + it] == chunk[it] } } ?: -1

    private companion object {
        const val GUIDE = "docs/writing-an-app.md"
        val FENCE = Regex("```([a-z]*)")
        val MARKER = Regex("<!-- sample: (\\S+) -->")
        val CUTS = setOf("// ...", "# ...")

        /** Blocks that quote no file: shell commands, file trees. */
        val FREE = setOf("", "sh", "text")
    }
}

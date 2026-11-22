package fr.bde_teknantes.lnb.phone.demo.guide

/**
 * Checks the code samples of a guide written in Markdown against the files they quote.
 *
 * Every fenced block (```` ``` ```` or `~~~`, three or more) whose language isn't one of [FREE] must have a
 * `<!-- sample: <path from the repository root> -->` line right above it (blank lines aside). Its lines, indentation
 * and blank lines aside, must appear in that file one after the other and in the same order. A line that is only
 * `// ...` or `# ...` marks a cut, where the file may have more lines; each part between cuts must hold at least one
 * line with a letter or a digit, since a part of brackets alone would match almost anywhere.
 */
object GuideSamples {
    /** Blocks that quote no file: shell commands, file trees. */
    val FREE = setOf("", "sh", "text")

    private val MARKER = Regex("<!-- sample: (\\S+) -->")
    private val CUTS = setOf("// ...", "# ...")

    class Sample(val line: Int, val language: String, val source: String?, val lines: List<String>)

    /** The paths the guide's samples name, for the build to watch. */
    fun sources(guide: List<String>): List<String> = guide.mapNotNull { MARKER.matchEntire(it.trim())?.groupValues?.get(1) }

    /**
     * The problems of [guide], each a sentence naming its line; empty when every sample matches. [read] gives a file's
     * lines by its path, or null when there is no such file.
     */
    fun problems(guide: List<String>, read: (String) -> List<String>?): List<String> {
        val problems = ArrayList<String>()
        val samples = samples(guide, problems)
        if (samples.isEmpty() && problems.isEmpty()) problems += "no samples found"
        samples.mapNotNullTo(problems) { check(it, read) }
        return problems
    }

    private fun samples(lines: List<String>, problems: MutableList<String>): List<Sample> {
        val out = ArrayList<Sample>()
        var i = 0
        while (i < lines.size) {
            val opening = fence(lines[i])
            if (opening == null) {
                i++
                continue
            }
            val (marks, info) = opening
            val language = info.substringBefore(' ').lowercase()
            val start = i
            val body = ArrayList<String>()
            i++
            while (i < lines.size && !closes(lines[i], marks)) body += lines[i++]
            if (i == lines.size) problems += "line ${start + 1}: a code block that never closes"
            i++
            if (language in FREE) continue
            val marker = (start - 1 downTo 0).firstOrNull { lines[it].isNotBlank() }?.let { MARKER.matchEntire(lines[it].trim()) }
            out += Sample(start + 1, language, marker?.groupValues?.get(1), body)
        }
        return out
    }

    /** The fence's marks (```` ``` ```` or `~~~`, maybe longer) and its info string, if [line] opens a block. */
    private fun fence(line: String): Pair<String, String>? {
        val t = line.trim()
        val c = t.firstOrNull()?.takeIf { it == '`' || it == '~' } ?: return null
        val marks = t.takeWhile { it == c }
        if (marks.length < 3) return null
        return marks to t.substring(marks.length).trim()
    }

    private fun closes(line: String, marks: String): Boolean {
        val t = line.trim()
        return t.length >= marks.length && t.all { it == marks[0] }
    }

    private fun check(sample: Sample, read: (String) -> List<String>?): String? {
        val where = "line ${sample.line}"
        val path = sample.source ?: return "$where: a `${sample.language}` sample with no <!-- sample: path --> line above it"
        val source = read(path)?.map(String::trim)?.filter(String::isNotEmpty) ?: return "$where: $path doesn't exist"
        var from = 0
        for (chunk in chunks(sample.lines)) {
            if (chunk.none { line -> line.any(Char::isLetterOrDigit) }) {
                return "$where: the part \"${chunk.joinToString(" ")}\" is too short to be found in its place; quote a line with words in it"
            }
            val at = indexOf(source, chunk, from)
            if (at < 0) return "$where: not found in $path, from:\n    " + chunk.joinToString("\n    ")
            from = at + chunk.size
        }
        return null
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

    private fun indexOf(source: List<String>, chunk: List<String>, from: Int): Int =
        (from..source.size - chunk.size).firstOrNull { start -> chunk.indices.all { source[start + it] == chunk[it] } } ?: -1
}

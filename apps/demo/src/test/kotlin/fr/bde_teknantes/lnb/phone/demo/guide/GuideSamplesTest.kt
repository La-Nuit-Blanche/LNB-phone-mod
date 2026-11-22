package fr.bde_teknantes.lnb.phone.demo.guide

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.File

/** Keeps the guide's code true: see [GuideSamples] for the rules a sample follows. */
class GuideSamplesTest {
    @Test
    fun `every sample of the guide is found in the file it names`() {
        val root = File(System.getProperty("lnbphone.repoRoot") ?: error("Run through Gradle: lnbphone.repoRoot is not set"))
        val problems = GuideSamples.problems(root.resolve(GUIDE).readLines()) { path ->
            root.resolve(path).takeIf(File::isFile)?.readLines()
        }
        if (problems.isNotEmpty()) fail<Unit>("$GUIDE has ${problems.size} problem(s):\n\n" + problems.joinToString("\n\n"))
    }

    // ---- The checker itself, on made-up guides ----

    private val source = listOf("fun a() {", "    one()", "}", "", "fun b() {", "    two()", "}")

    private fun problems(vararg guide: String) = GuideSamples.problems(guide.toList()) { if (it == "f.kt") source else null }

    @Test
    fun `a sample found in order passes, cuts and indentation aside`() {
        assertEquals(emptyList<String>(), problems("<!-- sample: f.kt -->", "```kotlin", "fun a() {", "// ...", "fun b() {", "two()", "```"))
    }

    @Test
    fun `a sample out of order, changed, unmarked or naming no file fails`() {
        assertEquals(1, problems("<!-- sample: f.kt -->", "```kotlin", "fun b() {", "// ...", "fun a() {", "```").size)
        assertEquals(1, problems("<!-- sample: f.kt -->", "```kotlin", "fun a() {", "three()", "```").size)
        assertEquals(1, problems("```kotlin", "fun a() {", "```").size)
        assertEquals(1, problems("<!-- sample: g.kt -->", "```kotlin", "fun a() {", "```").size)
    }

    @Test
    fun `lines skipped without a cut fail`() {
        assertEquals(1, problems("<!-- sample: f.kt -->", "```kotlin", "fun a() {", "}", "fun b() {", "```").size)
    }

    @Test
    fun `a part of brackets alone is refused`() {
        val found = problems("<!-- sample: f.kt -->", "```kotlin", "fun a() {", "// ...", "}", "```")
        assertTrue(found.single().contains("too short"), found.toString())
    }

    @Test
    fun `blocks with any info string or tildes are checked, and can't swallow the next one`() {
        assertEquals(1, problems("```Kotlin title=\"x\"", "nothing()", "```").size)
        assertEquals(1, problems("~~~json5", "{}", "~~~", "", "<!-- sample: f.kt -->", "```kotlin", "fun a() {", "```").size)
        assertEquals(1, problems("````kotlin", "```", "nothing()", "````").size)
    }

    @Test
    fun `shell blocks and file trees need no source`() {
        assertEquals(emptyList<String>(), problems("```sh", "./gradlew build", "```", "```text", "a/", "```", "```", "x", "```", "<!-- sample: f.kt -->", "```kotlin", "fun a() {", "```"))
    }

    private companion object {
        const val GUIDE = "docs/writing-an-app.md"
    }
}

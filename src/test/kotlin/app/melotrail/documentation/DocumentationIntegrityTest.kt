package app.melotrail.documentation

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentationIntegrityTest {
    private val repository = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    private val retiredGuides = listOf(
        "MIDI_IMPORT_PROCESS.md", "TRACK_PROCESS_WORKFLOW.md", "COMMERCIAL_PROVENANCE.md",
        "COMPATIBILITY_READERS.md", "SPRING_API_RETIREMENT.md"
    )

    @Test
    fun `all local Markdown links in the active documentation resolve`() {
        val documents = listOf("AGENTS.md", "README.md", "PLAN.md").map(repository::resolve) +
            Files.walk(repository.resolve("docs")).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.toList()
            }

        assertEquals(emptyList(), documents.flatMap(::brokenLinks))
    }

    @Test
    fun `link audit detects missing files while allowing anchors and external links`(@TempDir root: Path) {
        Files.writeString(root.resolve("present file.md"), "# Present\n")
        val guide = root.resolve("guide.md")
        Files.writeString(guide, """
            [Existing](present%20file.md#present)
            [Anchor](#heading)
            [External](https://example.invalid/absent)
            [Missing](absent.md)
            ![Missing image](absent.png)
        """.trimIndent())

        assertEquals(listOf("$guide -> absent.md", "$guide -> absent.png"), brokenLinks(guide))
    }

    @Test
    fun `obsolete documentation and its exclusive readers remain retired`() {
        retiredGuides.forEach { name ->
            assertFalse(Files.exists(repository.resolve("docs/$name")), "Retired guide restored: $name")
        }
        assertFalse(Files.exists(repository.resolve(
            "src/main/kotlin/app/melotrail/commercial/YoutubePolicyDocumentation.kt"
        )))
        listOf("src/main/kotlin", "desktopApp/src/main/kotlin").forEach { sourceRoot ->
            Files.walk(repository.resolve(sourceRoot)).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }.forEach { source ->
                    val text = Files.readString(source)
                    retiredGuides.forEach { name ->
                        assertFalse(text.contains("docs/$name"), "Retired documentation reader: $source -> $name")
                    }
                    assertFalse(text.contains("ImportHelpLinks"), "Unused legacy help owner restored: $source")
                }
            }
        }
    }

    @Test
    fun `active plans and acceptance evidence remain indexed`() {
        val index = Files.readString(repository.resolve("docs/plan/README.md"))
        listOf(
            "MIDI_CORE_TASKS.md", "EXECUTE_MIDI_CORE_TASKS_PROMPT.md", "MIDI_CORE_EXECUTION_LOG.md",
            "UI_MOCKUP_REDESIGN_PLAN.md", "UI_MOCKUP_TASKS.md", "EXECUTE_UI_MOCKUP_TASKS_PROMPT.md",
            "UI_MOCKUP_EXECUTION_LOG.md", "FUTURE_VIDEO_CREATOR.md", "MC040_DESKTOP_SMOKE_CHECKLIST.md",
            "MC045_MIDI_AUDITION_SMOKE.md", "MC047_PROPERTY_EVIDENCE.md", "MC048_DAW_MATRIX.md",
            "MC048I_ARRANGEMENT_UX_RUBRIC.md", "MC049_HOLDOUT_RUBRIC.md"
        ).forEach { name ->
            assertTrue(Files.isRegularFile(repository.resolve("docs/plan/$name")), "Missing active evidence: $name")
            assertTrue(index.contains("]($name)"), "Active evidence not indexed: $name")
        }
    }

    private fun brokenLinks(document: Path): List<String> =
        Regex("""!?\[[^\]]*]\(([^)\r\n]+)\)""").findAll(Files.readString(document)).mapNotNull { match ->
            val target = match.groupValues[1].removeSurrounding("<", ">").substringBefore('#')
            if (target.isEmpty() || Regex("""^[A-Za-z][A-Za-z0-9+.-]*:""").containsMatchIn(target)) {
                null
            } else {
                val resolved = document.parent.resolve(URI(target).path).normalize()
                if (Files.exists(resolved)) null else "$document -> $target"
            }
        }.toList()
}

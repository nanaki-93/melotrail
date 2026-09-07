package app.melotrail.documentation

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val documents = listOf("AGENTS.md", "README.md", "PLAN.md", "TASKS.md").map(repository::resolve) +
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
    fun `one task queue and concise reference set replace the retired planning suites`() {
        val index = Files.readString(repository.resolve("README.md"))
        listOf(
            "PLAN.md", "TASKS.md", "docs/ARCHITECTURE.md", "docs/MIDI_CONTRACT.md",
            "docs/UI_GUIDELINE.md", "docs/VALIDATION.md", "docs/TABI_VIDEO.md"
        ).forEach { path ->
            assertTrue(Files.isRegularFile(repository.resolve(path)), "Missing active reference: $path")
            assertTrue(index.contains("]($path)"), "Active reference not indexed: $path")
        }
        val referenceDocuments = Files.walk(repository.resolve("docs")).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .map { repository.resolve("docs").relativize(it).toString() }.sorted().toList()
        }
        assertEquals(listOf("ARCHITECTURE.md", "MIDI_CONTRACT.md", "TABI_VIDEO.md", "UI_GUIDELINE.md", "VALIDATION.md"), referenceDocuments)
        listOf("docs/FUNCTION_DOCUMENTATION_INVENTORY.json", "tools/check_documentation_coverage.py",
            "tools/measure_arrangement_ux.py", "worker/tests/test_documentation_coverage.py").forEach { path ->
            assertFalse(Files.exists(repository.resolve(path)), "Retired documentation machinery restored: $path")
        }
        assertFalse(Files.readString(repository.resolve("build.gradle.kts")).contains("checkDocumentationCoverage"))
    }

    @Test
    fun `consolidation preserves original UI references and recorded Logic captures`() {
        val referenceRoot = repository.resolve("docs/pictures/UI")
        val manifest = Json.parseToJsonElement(Files.readString(referenceRoot.resolve("reference-measurements.json"))).jsonObject
        val references = manifest.getValue("referenceManifest").jsonArray
        assertEquals(9, references.size)
        references.forEach { entry ->
            val row = entry.jsonObject
            val file = referenceRoot.resolve(row.getValue("file").jsonPrimitive.content)
            val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
                .joinToString("") { "%02x".format(it) }
            assertEquals(row.getValue("sha256").jsonPrimitive.content, digest, "Changed reference: $file")
        }
        val evidence = Files.readString(repository.resolve("docs/VALIDATION.md"))
        val captures = Regex("""\[([^\]]+\.png)]\(checks/[^)]+\) \| `([0-9a-f]{64})`""")
            .findAll(evidence).toList()
        assertEquals(6, captures.size)
        captures.forEach { match ->
            val file = repository.resolve("docs/checks/${match.groupValues[1]}")
            assertEquals(match.groupValues[2], sha256(file), "Changed Logic evidence: $file")
        }

        mapOf(
            "docs/pictures/App-pages.png" to "f8db766f5d19d9d4a6423da7575ac6a435789880d7dbf2590d8405f72b258afc",
            "docs/pictures/tabi.png" to "8c790543d85bcd5d8b8a681e98c699816376e03561e8cf35f9e626cb75eb8d2a",
            "docs/pictures/video/2135A4D8-3760-4F5F-989C-7EF5097ED4EE.jpeg" to "3743ec6b2ed2aeda45fb03a7da5173da21bd765cce86d9124dbecb0c011b0410",
            "docs/pictures/video/2466F94A-C5D7-4792-9AAD-BF15C315ED47.jpeg" to "b49346d676469301aad5d7f8b43e9e7fd4dde546ad19d203ace07441624f6fb1",
            "docs/pictures/video/925CC60E-0978-4AF3-8F34-70E412300B4C.jpeg" to "3ae878e3d00a286cb13bfe3bc56b30f702aded431f9cca514ffb07ae79d73fd5",
            "docs/pictures/video/Morning Lo-Fi Train Ride with Tabi.png" to "dc9541450b2aafebe713801e7b84bf445022557fcdaa2a2b66146b5b167558f2",
        ).forEach { (relativePath, digest) ->
            assertEquals(digest, sha256(repository.resolve(relativePath)), "Changed supplied reference: $relativePath")
        }
    }

    @Test
    fun `owned MIDI fixtures remain byte exact`() {
        mapOf(
            "src/test/resources/fixtures/m01-baseline/packages.zip.base64" to "25a6b7752308bb9e083f1d1b7433e22933b8a823e0ce5adeb0a59a7005c718e5",
            "src/test/resources/fixtures/midi-core/bass-golden.json" to "495351cab672565b9ffe5b5369ed9feacd360d66ddc26e862bfcd5e4e2f550e6",
            "src/test/resources/fixtures/midi-core/chords-golden.json" to "0c1648ab1aa01a3e6d622fa1e32b1494592d3366aed4ff7fb3a59c2b6b207258",
            "src/test/resources/fixtures/midi-core/drums-golden.json" to "5b6184ab6d86bbe6f7106e4010ea43746838751de55f183ad15d763858baaf8c",
            "src/test/resources/fixtures/project/midi-core-v1.json" to "8c1cad55dbcacd4b10707a02e8d470aaecce5f82e59ab46e867c6c24a2584e1e",
        ).forEach { (relativePath, digest) ->
            assertEquals(digest, sha256(repository.resolve(relativePath)), "Changed owned fixture: $relativePath")
        }
    }

    private fun sha256(file: Path): String =
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
            .joinToString("") { "%02x".format(it) }

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

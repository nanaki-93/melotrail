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
    private val planningDocuments = listOf("PLAN-AUDIO.md", "PLAN-VIDEO.md", "TASKS-AUDIO.md", "TASKS-VIDEO.md")
    private val retiredGuides = listOf(
        "MIDI_IMPORT_PROCESS.md", "TRACK_PROCESS_WORKFLOW.md", "COMMERCIAL_PROVENANCE.md",
        "COMPATIBILITY_READERS.md", "SPRING_API_RETIREMENT.md"
    )

    @Test
    fun `all local Markdown links in the active documentation resolve`() {
        val documents = (listOf("AGENTS.md", "README.md") + planningDocuments).map(repository::resolve) +
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
    fun `audio and video planning pairs and concise references replace the combined planning suite`() {
        val index = Files.readString(repository.resolve("README.md"))
        val rootPlanningDocuments = Files.list(repository).use { paths ->
            paths.map { it.fileName.toString() }
                .filter { it.endsWith(".md") && (it.startsWith("PLAN") || it.startsWith("TASKS")) }
                .sorted().toList()
        }
        assertEquals(planningDocuments.sorted(), rootPlanningDocuments)
        (planningDocuments + listOf(
            "docs/ARCHITECTURE.md", "docs/MIDI_CONTRACT.md",
            "docs/UI_GUIDELINE.md", "docs/VALIDATION.md", "docs/TABI_VIDEO.md"
        )).forEach { path ->
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
    fun `workstream queues keep their own features dependencies and execution authority`() {
        val agents = Files.readString(repository.resolve("AGENTS.md"))
        val build = Files.readString(repository.resolve("build.gradle.kts"))
        planningDocuments.forEach { name ->
            assertTrue(agents.contains("]($name)"), "Missing agent authority: $name")
            assertTrue(build.contains("\"$name\""), "Missing documentation test input: $name")
        }
        listOf("AUDIO" to (1..5).map { "AC$it" }, "VIDEO" to (1..6).map { "VG$it" }).forEach { (stream, features) ->
            val planName = "PLAN-$stream.md"
            val queueName = "TASKS-$stream.md"
            val plan = Files.readString(repository.resolve(planName))
            val queue = Files.readString(repository.resolve(queueName))
            val otherPrefix = if (stream == "AUDIO") "VG" else "AC"
            features.forEach { feature ->
                assertTrue(plan.contains("### Feature $feature —"), "Missing roadmap feature: $feature")
                assertTrue(queue.contains("## Feature $feature —"), "Missing queue feature: $feature")
            }
            assertFalse(Regex("(?m)^#{2,3} Feature $otherPrefix").containsMatchIn(plan + queue))
            assertTrue(plan.contains("]($queueName)"))
            assertTrue(queue.contains("]($planName)"))
            assertTrue(queue.contains("from the current $queueName"), "Wrong implementation prompt authority")
            val rows = queue.lineSequence().filter { Regex("^\\| (?:CORE|AC[0-9]+|VG[0-9]+|VG-OPT)-[0-9]+ \\|").containsMatchIn(it) }
                .map { line -> line.split('|').map(String::trim) }.toList()
            val ids = rows.map { it[1] }
            assertTrue(ids.isNotEmpty())
            assertEquals(ids.size, ids.toSet().size, "Duplicate IDs in $queueName")
            rows.forEach { row ->
                val id = row[1]
                assertTrue(id == "CORE-01" || features.any { id.startsWith("$it-") } ||
                    (stream == "VIDEO" && id.startsWith("VG-OPT-")), "Wrong workstream: $id")
                assertTrue(row[4] in setOf("TODO", "RUNNING", "REVIEW", "DONE", "WAITING_USER", "BLOCKED", "OPTIONAL"))
                if (row[3] != "—") row[3].split(", ").forEach { dependency ->
                    assertTrue(dependency in ids, "$queueName: $id has missing/cross-queue dependency $dependency")
                }
            }
        }
    }

    @Test
    fun `planning requires non interactive validation without retired UI only task gates`() {
        planningDocuments.forEach { name ->
            val text = Files.readString(repository.resolve(name))
            assertTrue(text.contains("## Non-interactive validation"), "Missing validation scope: $name")
            assertTrue(text.contains("docs/VALIDATION.md#non-interactive-validation"))
            listOf(":desktopApp:nativeDesktopCapture", ":desktopApp:nativeInstallSmoke",
                ":desktopApp:videoInstalledSmoke").forEach { command ->
                assertFalse(text.contains(command), "Interactive command remains in planning: $name -> $command")
            }
        }
        val audioRows = Files.readString(repository.resolve("TASKS-AUDIO.md"))
            .lineSequence().filter { it.startsWith("| AC") }.toList()
        listOf("AC4-02", "AC5-05").forEach { retiredId ->
            assertTrue(audioRows.none { it.contains(retiredId) }, "Retired UI gate or dependency: $retiredId")
        }
        val agents = Files.readString(repository.resolve("AGENTS.md"))
        assertTrue(agents.contains("headless and non-interactive"))
        assertTrue(agents.contains("docs/VALIDATION.md#non-interactive-validation"))
    }

    @Test
    fun `supplied UI and current TABI references and recorded Logic captures stay byte exact`() {
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
            // User-approved TABI refresh from f96a7f36430d45c576de511be94480e31762570b.
            "docs/pictures/video/tabi-assets/character-profile/tabi-character-profile.png" to "1c63aa8f579634eff5f8c6864e97882513feba381c1e2380ae66018800f9d238",
            "docs/pictures/video/tabi-assets/emotions/01-joy.png" to "375be0ae23ff2408dd9ad50638a0323b0ca00d1ea944e137128d960d5b18e753",
            "docs/pictures/video/tabi-assets/train-actions/01-drinking-coffee.png" to "f8ac8dcfcb1635a8913ea6a1eb2a8a52af428458203ec1770a3bd68d3ef09235",
            "docs/pictures/video/tabi-assets/walking/01-walk-right-step-a.png" to "9796ac1fd540ea85b21abbd04a27fef96d77a8e3362b98890be588b97fecb187",
            "docs/pictures/video/tabi-assets/country-outfits/japan/tabi-japan-scene.png" to "a45301d1154b6ce57dcd311781aa5249c33e19ade6c4e64f9e02d32e8ef7f816",
            "docs/pictures/video/tabi-eki-channel-banner-charcoal-stone-v6-upload.jpg" to "ff3a282b97a1ccca07d5f35a08feba5b66a8e66557a7ddda0e49bf357234f430",
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

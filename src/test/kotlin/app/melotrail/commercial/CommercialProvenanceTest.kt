package app.melotrail.commercial

import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SongPart
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommercialProvenanceTest {
    private val hash = "a".repeat(64)
    private val attested = SourceRightsAttestation(SourceRightsClaim.OWNED, "2026-08-17T00:00:00Z")

    @Test
    fun `decision table blocks unresolved sources and dependencies`() {
        val approved = CommercialDependency(CommercialDependencyKind.MODEL, "planner", "1", hash, CommercialTerm.PERMITTED, true, "MIT", "local")
        assertTrue(CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved))).ready)
        listOf(CommercialTerm.CONDITIONAL, CommercialTerm.UNKNOWN, CommercialTerm.BLOCKED).forEach { term ->
            assertFalse(CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved.copy(commercialTerm = term)))).ready)
        }
        assertFalse(CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, null)), listOf(approved))).ready)
        assertFalse(CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved), listOf("missing attribution"))).ready)
        assertFalse(CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved.copy(identity = "fake-model")))).ready)
    }

    @Test
    fun `release manifest is immutable hash bound and reports source tampering`() {
        val root = projectRoot()
        try {
            val dependency = CommercialDependency(CommercialDependencyKind.SOUND_LIBRARY, "starter", "1", hash, CommercialTerm.PERMITTED, true, "CC0", "local", attribution = "Credit starter")
            val service = CommercialProvenanceService()
            val first = service.export(root, listOf(dependency))
            val manifest = assertNotNull(first.manifest)
            val second = service.export(root, listOf(dependency))

            assertEquals(manifest, second.manifest)
            assertTrue(manifest.toString().contains("output/releases/release-"))
            assertFalse(first.readiness.ready, "missing settings and selected MIDI must remain explicit evidence gaps")
            assertTrue(service.verifyReleaseLineage(root, assertNotNull(first.releaseId)).closed)
            assertContains(Files.readString(assertNotNull(second.report)), "not legal advice")
            assertContains(Files.readString(assertNotNull(second.checklist)), "Resolve every listed evidence action")

            Files.writeString(root.resolve("source/A.mid"), "tampered")
            val verification = service.verifyReleaseLineage(root, assertNotNull(first.releaseId))
            assertFalse(verification.closed)
            assertTrue("source/A.mid" in verification.tamperedDependencies)
        } finally {
            delete(root)
        }
    }

    @Test
    fun `manifest tampering is detected through selected project reference`() {
        val root = projectRoot()
        try {
            val result = CommercialProvenanceService().export(root)
            val manifest = assertNotNull(result.manifest)
            Files.writeString(manifest, Files.readString(manifest).replace("source/A.mid", "source/B.mid"))

            val verification = CommercialProvenanceService().verifyReleaseLineage(root, assertNotNull(result.releaseId))

            assertFalse(verification.closed)
            assertTrue("selected release manifest" in verification.tamperedDependencies)
        } finally {
            delete(root)
        }
    }

    @Test
    fun `portable dependency details redact absolute paths and secrets`() {
        val dependency = CommercialDependency(CommercialDependencyKind.MODEL, "local-model", "1", hash, CommercialTerm.PERMITTED, true, "MIT", "/Users/name/model api_key=private")

        val portable = dependency.portable()

        assertFalse(portable.source.contains("/Users/name"))
        assertFalse(portable.source.contains("private"))
    }

    @Test
    fun `release documentation retains official links and dated review gate`() {
        YoutubePolicyDocumentation.requireReviewed(Path.of("docs/COMMERCIAL_PROVENANCE.md"), "2026-08-17")
        assertFalse(runCatching { YoutubePolicyDocumentation.requireReviewed(Path.of("docs/COMMERCIAL_PROVENANCE.md"), "2026-08-18") }.isSuccess)
    }

    private fun projectRoot(): Path {
        val root = Files.createTempDirectory("commercial-provenance")
        Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/raw")); Files.createDirectories(root.resolve("mix")); Files.createDirectories(root.resolve("output"))
        Files.writeString(root.resolve("source/A.mid"), "source")
        Files.writeString(root.resolve("midi/raw/A.mid"), "raw")
        Files.writeString(root.resolve("mix/repaired.wav"), "repair")
        Files.writeString(root.resolve("output/master.wav"), "master")
        Files.writeString(root.resolve("output/release.json"), "{\"inputArtifact\":\"mix/repaired.wav\"}")
        ProjectStore.write(root, Project(Project.CURRENT_VERSION, "Evidence", listOf(SongPart("A", "source/A.mid", midi = MidiReferences(raw = "midi/raw/A.mid"), sourceAttestation = attested)), renderFormat = RenderFormat()))
        return root
    }

    private fun delete(root: Path) {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }
}

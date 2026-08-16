package app.melotrail.commercial

import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Path

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
        assertFalse(CommercialReadinessEvaluator.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, SourceRightsAttestation(SourceRightsClaim.NOT_ESTABLISHED, "2026-08-17T00:00:00Z"))), listOf(approved))).ready)
    }

    @Test
    fun `manifest is deterministic hash bound and detects tampering`() {
        val root = Files.createTempDirectory("commercial-provenance")
        try {
            Files.createDirectories(root.resolve("source")); Files.createDirectories(root.resolve("midi/raw")); Files.createDirectories(root.resolve("output"))
            Files.writeString(root.resolve("source/A.mid"), "source")
            Files.writeString(root.resolve("midi/raw/A.mid"), "raw")
            Files.writeString(root.resolve("output/master.wav"), "master")
            Files.writeString(root.resolve("output/release.json"), "{}")
            ProjectStore.write(root, Project(Project.CURRENT_VERSION, "Evidence", listOf(Part("A", "source/A.mid", midi = MidiReferences(raw = "midi/raw/A.mid"), sourceAttestation = attested)), renderFormat = RenderFormat()))
            val dependency = CommercialDependency(CommercialDependencyKind.SOUND_LIBRARY, "starter", "1", hash, CommercialTerm.PERMITTED, true, "generated-original", "local", attribution = "Credit starter")
            val service = CommercialProvenanceService()
            val first = service.export(root, listOf(dependency))
            val firstText = Files.readString(checkNotNull(first.manifest))
            val second = service.export(root, listOf(dependency))
            assertTrue(first.readiness.ready)
            assertTrue(firstText == Files.readString(checkNotNull(second.manifest)))
            assertTrue(service.verify(root).ready)
            assertContains(Files.readString(checkNotNull(second.report)), "not legal advice")
            assertContains(Files.readString(checkNotNull(second.checklist)), "AI-use disclosure")
            val manifest = checkNotNull(second.manifest)
            Files.writeString(manifest, Files.readString(manifest).replace("source/A.mid", "../outside.mid"))
            assertFalse(runCatching { service.verify(root) }.isSuccess)
            service.export(root, listOf(dependency))
            Files.writeString(root.resolve("source/A.mid"), "tampered")
            assertFalse(runCatching { service.verify(root) }.isSuccess)
        } finally {
            Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `release documentation retains official links and dated review gate`() {
        YoutubePolicyDocumentation.requireReviewed(Path.of("docs/COMMERCIAL_PROVENANCE.md"), "2026-08-17")
        assertFalse(runCatching { YoutubePolicyDocumentation.requireReviewed(Path.of("docs/COMMERCIAL_PROVENANCE.md"), "2026-08-18") }.isSuccess)
    }
}

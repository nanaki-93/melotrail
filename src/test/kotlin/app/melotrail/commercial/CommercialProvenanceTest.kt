package app.melotrail.commercial

import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.DetailedArrangementSection
import app.melotrail.arrangement.PianoSourcePlan
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ReleaseSimilarityCritic
import app.melotrail.arrangement.ReleaseSimilarityReviewStatus
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SignatureMotifReleaseGateResult
import app.melotrail.arrangement.SignatureMotifOccurrenceReport
import app.melotrail.arrangement.SignatureMotifNoteLineage
import app.melotrail.arrangement.SignatureMotifLineageStatus
import app.melotrail.arrangement.MelodyNoteId
import app.melotrail.arrangement.SignatureMotifThresholds
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.SongSectionPurpose
import app.melotrail.arrangement.TransitionPlan
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val approved = CommercialDependency(CommercialDependencyKind.MODEL, "planner", "1", hash, CommercialTerm.PERMITTED, true, "MIT", "local", aiUseReview = aiReview())
        assertTrue(CommercialReadyGate.evaluate(CommercialReadinessInput(
            listOf(CommercialSource("A", hash, attested)), listOf(approved), requireStructuredAiUseReview = true
        )).ready)
        listOf(CommercialTerm.CONDITIONAL, CommercialTerm.UNKNOWN, CommercialTerm.BLOCKED).forEach { term ->
        assertFalse(CommercialReadyGate.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved.copy(commercialTerm = term)))).ready)
        }
        assertFalse(CommercialReadyGate.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, null)), listOf(approved))).ready)
        assertFalse(CommercialReadyGate.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved), listOf("missing attribution"))).ready)
        assertFalse(CommercialReadyGate.evaluate(CommercialReadinessInput(listOf(CommercialSource("A", hash, attested)), listOf(approved.copy(identity = "fake-model")))).ready)
        assertFalse(CommercialReadyGate.evaluate(CommercialReadinessInput(
            listOf(CommercialSource("A", hash, attested)), listOf(approved.copy(aiUseReview = null)), requireStructuredAiUseReview = true
        )).ready)
    }

    @Test
    fun `commercial readiness blocks a failed signature motif release gate`() {
        val approved = CommercialDependency(CommercialDependencyKind.MODEL, "planner", "1", hash, CommercialTerm.PERMITTED, true, "MIT", "local", aiUseReview = aiReview())
        val failedGate = SignatureMotifReleaseGateResult(
            sourceSha256 = hash, motifPhraseId = "p-00000", thresholds = SignatureMotifThresholds(),
            occurrenceReports = listOf(SignatureMotifOccurrenceReport("verse-1", 0.0, 0.0, 0.0, 0.0, 0.0, false,
                listOf(SignatureMotifNoteLineage(MelodyNoteId("m-$hash"), status = SignatureMotifLineageStatus.MISSING)))),
            clearOccurrenceCount = 0, passed = false, reasons = listOf("no-clear-surviving-occurrence")
        )

        assertFalse(CommercialReadyGate.evaluate(CommercialReadinessInput(
            listOf(CommercialSource("A", hash, attested)), listOf(approved), recognizabilityGate = failedGate
        )).ready)
    }

    @Test
    fun `commercial ready gate records a disclosure recommendation for material generative AI`() {
        val model = CommercialDependency(CommercialDependencyKind.MODEL, "arranger", "1", hash, CommercialTerm.PERMITTED, true, "MIT", "local", aiUseReview = aiReview())

        val result = CommercialReadyGate.evaluate(CommercialReadinessInput(
            listOf(CommercialSource("A", hash, attested)), listOf(model), requireStructuredAiUseReview = true
        ))

        assertTrue(result.ready)
        assertTrue(result.aiDisclosureRecommended)
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
            assertTrue(Files.isRegularFile(root.resolve("output/releases/${first.releaseId}/provenance.json")))
            assertTrue(Files.isRegularFile(root.resolve("output/releases/${first.releaseId}/youtube-release.json")))
            assertFalse(first.readiness.ready, "missing settings and selected MIDI must remain explicit evidence gaps")
            assertTrue(service.verifyReleaseLineage(root, assertNotNull(first.releaseId)).closed)
            assertContains(Files.readString(assertNotNull(second.report)), "not legal advice")
            assertContains(Files.readString(assertNotNull(second.checklist)), "aiDisclosureRecommended")
            assertContains(Files.readString(assertNotNull(second.checklist)), "HUMAN_REVIEW_REQUIRED")
            assertContains(Files.readString(assertNotNull(second.checklist)), "NOT_REQUESTED")

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
    fun `commercial release manifest retains advisory release similarity review`() {
        val root = projectRoot()
        try {
            val critic = ReleaseSimilarityCritic()
            val fingerprint = critic.fingerprint(DetailedArrangement(sections = listOf(
                DetailedArrangementSection(0, "A1", "A", SongSectionPurpose.INTRODUCTION, 0.3, listOf(PianoSourcePlan()), TransitionPlan())
            )), Tempo(80.0), TimeSignature(4, 4))
            val review = critic.review(fingerprint, emptyList())
            Files.writeString(root.resolve("output/release.json"), "{\"inputArtifact\":\"mix/repaired.wav\",\"similarityReview\":${Json.encodeToString(review)}}")

            val result = CommercialProvenanceService().export(root)
            val manifest = Json.decodeFromString<CommercialProvenanceManifest>(Files.readString(assertNotNull(result.manifest)))

            assertEquals(ReleaseSimilarityReviewStatus.NOT_COMPARED, manifest.similarityReview?.status)
            assertEquals(fingerprint.sha256, manifest.similarityReview?.fingerprint?.sha256)
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
        YoutubePolicyDocumentation.requireReviewed(Path.of("docs/COMMERCIAL_PROVENANCE.md"), "2026-08-25")
        assertFalse(runCatching { YoutubePolicyDocumentation.requireReviewed(Path.of("docs/COMMERCIAL_PROVENANCE.md"), "2026-08-26") }.isSuccess)
    }

    private fun aiReview(disclosureRequired: Boolean = true) = AiUseDisclosureReview("release-owner", "2026-08-25T00:00:00Z", disclosureRequired, "Reviewed against the selected release lineage.")

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

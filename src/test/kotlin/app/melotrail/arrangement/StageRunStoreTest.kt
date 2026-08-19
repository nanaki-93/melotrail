package app.melotrail.arrangement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StageRunStoreTest {
    @TempDir lateinit var root: Path

    @Test
    fun `all statuses enforce completed-only outputs and summaries remain path-free`() {
        val now = "2026-08-19T00:00:00Z"
        val output = output("output/master.wav", "master")
        assertFailsWith<IllegalArgumentException> { StageRunIndex(schemaVersion = 2) }
        assertFailsWith<IllegalArgumentException> {
            StageRunRecord(schemaVersion = 2, runId = "unknown", stage = StageId.MIXED, subject = StageSubject.Project,
                status = StageRunStatus.PENDING, createdAt = now)
        }
        assertFailsWith<IllegalArgumentException> {
            StageRunRecord(runId = "pending", stage = StageId.MASTERED, subject = StageSubject.Project,
                status = StageRunStatus.PENDING, createdAt = now, outputArtifacts = listOf(output))
        }
        val records = listOf(
            StageRunRecord(runId = "pending", stage = StageId.MIXED, subject = StageSubject.Project, status = StageRunStatus.PENDING, createdAt = now),
            StageRunRecord(runId = "working", stage = StageId.MIXED, subject = StageSubject.Project, status = StageRunStatus.PROCESSING, createdAt = now, startedAt = now),
            StageRunRecord(runId = "failed", stage = StageId.MIXED, subject = StageSubject.Project, status = StageRunStatus.FAILED, createdAt = now, finishedAt = now,
                failure = SafeFailure(SafeFailureCode.DEPENDENCY_UNAVAILABLE, "Configure the local renderer and retry.")),
            StageRunRecord(runId = "done", stage = StageId.MASTERED, subject = StageSubject.Project, status = StageRunStatus.COMPLETED, createdAt = now, finishedAt = now,
                outputArtifacts = listOf(output), selections = listOf(StageOutputSelection(output, now)))
        )
        val store = StageRunStore()
        var manifest = store.initialize(root)
        records.forEach { manifest = store.append(root, it) }

        assertEquals(records.map(StageRunRecord::runId), store.read(root, manifest).map(StageRunRecord::runId))
        assertEquals(1, store.summaries(root, manifest).single { it.runId == "done" }.outputCount)
        assertEquals("output/master.wav", store.selectedOutput(root, manifest, StageSubject.Project)?.artifact?.path)
        assertTrue(store.summaries(root, manifest).none { it.toString().contains("output/master.wav") })
    }

    @Test
    fun `tampered and missing completed outputs are rejected on read`() {
        val now = "2026-08-19T00:00:00Z"
        val artifact = output("output/master.wav", "master")
        val store = StageRunStore()
        val manifest = store.append(root, StageRunRecord(runId = "done", stage = StageId.MASTERED, subject = StageSubject.Project,
            status = StageRunStatus.COMPLETED, createdAt = now, finishedAt = now, outputArtifacts = listOf(artifact)))

        Files.writeString(root.resolve(artifact.path), "tampered")
        assertFailsWith<IllegalArgumentException> { store.read(root, manifest) }
        Files.delete(root.resolve(artifact.path))
        assertFailsWith<IllegalArgumentException> { store.read(root, manifest) }
    }

    @Test
    fun `failed index publish leaves an inspectable orphan and no visible run`() {
        val now = "2026-08-19T00:00:00Z"
        val delegate = FileAtomicStageRunPublisher
        var failIndex = false
        val publisher = object : AtomicStageRunPublisher {
            override fun write(path: Path, text: String) {
                if (failIndex && path.endsWith(StageRunStore.INDEX_FILE)) error("simulated index failure")
                delegate.write(path, text)
            }
            override fun writeNew(path: Path, text: String) = delegate.writeNew(path, text)
        }
        val store = StageRunStore(publisher)
        val manifest = store.initialize(root)
        failIndex = true
        assertFailsWith<IllegalStateException> {
            store.append(root, StageRunRecord(runId = "failed-index", stage = StageId.MIXED, subject = StageSubject.Project,
                status = StageRunStatus.FAILED, createdAt = now, finishedAt = now,
                failure = SafeFailure(SafeFailureCode.INTERRUPTED, "Retry the stage.")))
        }

        assertTrue(store.read(root, manifest).isEmpty())
        assertEquals(listOf("workflow-runs/runs/failed-index.json"), store.recoverableOrphans(root, manifest).map(ArtifactRef::path))
    }

    @Test
    fun `v3 mapper preserves raw clean technical-correction and feel selections deterministically`() {
        val hash = "a".repeat(64)
        val project = Project(version = 3, name = "legacy", renderFormat = RenderFormat(), parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(
            raw = "midi/raw/A.mid",
            clean = "midi/clean/A.mid",
            technicalCorrectionSelection = TechnicalCorrectionSelection.CORRECTED,
            technicalCorrection = TechnicalCorrectionReferences(
                WorkflowArtifactReference("midi/clean/A.mid", hash),
                WorkflowArtifactReference(TechnicalCorrectionArtifactPaths.output("A", hash), hash),
                WorkflowArtifactReference(TechnicalCorrectionArtifactPaths.report("A", hash), hash),
                hash
            ),
            aiFixSelection = MidiAiFixSelection.APPROVED,
            aiFix = MidiAiFixReferences(hash, approved = WorkflowArtifactReference(MidiAiFixArtifactPaths.approved("A"), hash)),
            analysisInput = MidiAnalysisInput.LOFI_FEEL,
            feel = MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, "midi/derived/A/lofi-80-swing-v1.mid", "midi/derived/A/report.json")
        ))))

        val mapped = LegacyV3StageRunMapper.map(project)

        assertEquals(listOf(StageId.SOURCE, StageId.EXTRACTED, StageId.CLEANED, StageId.CORRECTED, StageId.ENHANCED), mapped.map(LegacyStageRunInput::stage))
        assertEquals(StageId.ENHANCED, mapped.single { it.selected }.stage)
        assertEquals(mapped, LegacyV3StageRunMapper.map(project))
    }

    @Test
    fun `cache key normalizes input and subject dependency order and graph is subject aware`() {
        val now = "2026-08-19T00:00:00Z"
        val first = ArtifactRef("midi/clean/A.mid", "a".repeat(64))
        val second = ArtifactRef("midi/raw/A.mid", "b".repeat(64))
        fun record(inputs: List<ArtifactRef>, dependencies: List<StageSubject>) = StageRunRecord(
            runId = "cache-${inputs.first().sha256.take(4)}", stage = StageId.ANALYZED, subject = StageSubject.Part("A"),
            status = StageRunStatus.PENDING, inputArtifacts = inputs, subjectDependencies = dependencies,
            configurationSha256 = "c".repeat(64), contextSha256 = "d".repeat(64), createdAt = now
        )
        assertEquals(record(listOf(first, second), listOf(StageSubject.Project, StageSubject.Part("B"))).cacheKey(),
            record(listOf(second, first), listOf(StageSubject.Part("B"), StageSubject.Project)).cacheKey())
        assertTrue(StageId.EXPORTED in StageRunDependencyGraph.downstreamOf(StageId.CLEANED, StageSubject.Part("A")))
        assertTrue(StageRunDependencyGraph.downstreamOf(StageId.MIXED, StageSubject.Project).containsAll(setOf(StageId.MASTERED, StageId.EXPORTED)))
    }

    @Test
    fun `lineage follows exact hash-bound upstream outputs`() {
        val now = "2026-08-19T00:00:00Z"
        val source = output("source/A.mid", "source")
        val clean = output("midi/clean/A.mid", "clean")
        val store = StageRunStore()
        var manifest = store.append(root, StageRunRecord(runId = "source", stage = StageId.SOURCE, subject = StageSubject.Part("A"),
            status = StageRunStatus.COMPLETED, createdAt = now, finishedAt = now, outputArtifacts = listOf(source)))
        manifest = store.append(root, StageRunRecord(runId = "clean", stage = StageId.CLEANED, subject = StageSubject.Part("A"),
            status = StageRunStatus.COMPLETED, inputArtifacts = listOf(source), createdAt = now, finishedAt = now, outputArtifacts = listOf(clean)))

        assertEquals(listOf("source", "clean"), store.lineage(root, manifest, clean).map(StageRunRecord::runId))
    }

    private fun output(relative: String, content: String): ArtifactRef {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return ArtifactRef(relative, sha256(path))
    }
}

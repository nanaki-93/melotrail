package app.melotrail.project.adapter

import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreExportSnapshot
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.SourceMidiRecord
import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectTempo
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreArtifactStoreTest {
    @TempDir lateinit var root: Path

    @Test
    fun `target tree publishes verifies saves and reopens every artifact kind`() {
        val store = MidiCoreArtifactStore()
        val project = completeProject(store)

        val projectFile = store.saveProject(root, project)

        assertEquals(project, store.openProject(root))
        assertEquals(root.resolve("project.json"), projectFile)
        assertTrue(Files.isRegularFile(root.resolve("source/original.mid")))
        assertTrue(Files.isRegularFile(root.resolve("reports/import.json")))
        assertTrue(Files.isRegularFile(root.resolve("candidates/chords/intro-1/candidate-1.mid")))
        assertTrue(Files.isRegularFile(root.resolve("reports/candidates/candidate-1.json")))
        assertTrue(Files.isRegularFile(root.resolve("exports/export-1/complete-song.mid")))
        assertTrue(Files.isRegularFile(root.resolve("exports/export-1/manifest.json")))
    }

    @Test
    fun `traversal and symlink parents cannot escape the project root`() {
        val store = MidiCoreArtifactStore()
        val source = bytesFile(root.resolve("input.mid"), "source")
        val outside = Files.createTempDirectory("melotrail-artifact-outside")
        try {
            Files.createSymbolicLink(root.resolve("link"), outside)

            assertFailsWith<IllegalArgumentException> { ProjectRelativePath("../outside.mid") }
            assertFailsWith<IllegalArgumentException> {
                store.publishImmutable(root, ProjectRelativePath("link/escape.mid"), source)
            }
            val external = bytesFile(outside.resolve("escape.mid"), "source")
            assertFailsWith<IllegalArgumentException> {
                store.verify(root, ProjectArtifact(ProjectRelativePath("link/escape.mid"), digest(external)))
            }
        } finally {
            Files.walk(outside).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun `missing and digest-mismatched artifacts are rejected`() {
        val store = MidiCoreArtifactStore()
        val missing = ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64))
        assertFailsWith<IllegalArgumentException> { store.verify(root, missing) }

        val source = bytesFile(root.resolve("input.mid"), "source")
        val artifact = store.publishSource(root, source)
        Files.writeString(root.resolve(artifact.path.value), "tampered")

        assertFailsWith<IllegalArgumentException> { store.verify(root, artifact) }
    }

    @Test
    fun `immutable republish is idempotent but a different-content collision preserves the first bytes`() {
        val store = MidiCoreArtifactStore()
        val first = bytesFile(root.resolve("first.mid"), "first")
        val same = bytesFile(root.resolve("same.mid"), "first")
        val different = bytesFile(root.resolve("different.mid"), "different")

        val artifact = store.publishSource(root, first)
        assertEquals(artifact, store.publishSource(root, same))
        assertFailsWith<MidiCoreArtifactCollisionException> { store.publishSource(root, different) }
        assertContentEquals(Files.readAllBytes(first), Files.readAllBytes(root.resolve(artifact.path.value)))
    }

    @Test
    fun `partial project write preserves the last known-good document and recovery evidence`() {
        var fail = false
        val store = MidiCoreArtifactStore(AtomicWriteObserver { temporary, _ ->
            if (fail) {
                Files.writeString(temporary, "{\"partial\":")
                throw IOException("simulated interruption")
            }
        })
        val original = emptyProject("Original")
        store.saveProject(root, original)
        val before = Files.readAllBytes(root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        fail = true

        val failure = assertFailsWith<MidiCoreProjectSaveException> {
            store.saveProject(root, emptyProject("Replacement"))
        }

        assertContentEquals(before, Files.readAllBytes(root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(original, store.openProject(root))
        assertTrue(Files.isRegularFile(failure.recoveryEvidence))
        assertEquals("{\"partial\":", Files.readString(failure.recoveryEvidence))
    }

    @Test
    fun `missing referenced artifact prevents project replacement`() {
        val store = MidiCoreArtifactStore()
        val original = emptyProject("Original")
        store.saveProject(root, original)
        val hash = "a".repeat(64)
        val missingSource = SourceMidiRecord(
            "missing.mid",
            hash,
            1,
            480,
            ProjectArtifact(MidiCoreArtifactStore.SOURCE_MIDI, hash),
            ProjectArtifact(MidiCoreArtifactStore.IMPORT_REPORT, hash),
            emptyList(),
            0,
        )

        assertFailsWith<IllegalArgumentException> {
            store.saveProject(root, original.copy(sourceMidi = missingSource))
        }
        assertEquals(original, store.openProject(root))
    }

    @Test
    fun `reopen verifies referenced digests before returning state`() {
        val store = MidiCoreArtifactStore()
        val project = completeProject(store)
        store.saveProject(root, project)
        Files.writeString(root.resolve("candidates/chords/intro-1/candidate-1.mid"), "tampered")

        assertFailsWith<IllegalArgumentException> { store.openProject(root) }
    }

    private fun completeProject(store: MidiCoreArtifactStore): MidiCoreProject {
        val source = store.publishSource(root, bytesFile(root.resolve("input.mid"), "source-midi"))
        val importReport = store.publishImportReport(root, "{\"status\":\"accepted\"}")
        val candidateMidi = store.publishCandidateMidi(
            root,
            CandidateRole.CHORDS,
            "intro-1",
            "candidate-1",
            bytesFile(root.resolve("candidate.mid"), "candidate-midi"),
        )
        val candidateReport = store.publishCandidateReport(root, "candidate-1", "{\"accepted\":true}")
        val complete = store.publishExportFile(
            root,
            "export-1",
            ExportedFileKind.COMPLETE_SONG,
            bytesFile(root.resolve("complete.mid"), "complete-midi"),
        )
        val manifest = store.publishExportFile(
            root,
            "export-1",
            ExportedFileKind.MANIFEST,
            bytesFile(root.resolve("manifest-input.json"), "{}"),
        )
        val authorityHash = "b".repeat(64)
        return MidiCoreProject(
            ProjectId("project-1"),
            ProjectMetadata("Artifact fixture", "2026-08-27T00:00:00Z"),
            SourceMidiRecord(
                "input.mid",
                source.sha256,
                1,
                480,
                source,
                importReport,
                listOf(MidiTrackSummary(0, "Melody", listOf(MidiChannelSummary(0, 1, 60, 60, 0, listOf(MidiTrackRoleHint.MELODY))))),
                480,
            ),
            SelectedMelodyTrack(1, 0, "c".repeat(64)),
            ProjectAuthority(
                ProjectKey(0, "major"),
                ProjectTempo(500_000),
                ProjectMeter(4, 2),
                listOf(ProjectSectionDefinition("intro", "Intro")),
                listOf(ProjectSectionOccurrence("intro-1", "intro", "Intro", 0, 480)),
                listOf(AuthoritativeChordEvent("chord-1", "intro-1", "C", 0, 480)),
            ),
            listOf(MidiCoreCandidate(
                "candidate-1",
                CandidateRole.CHORDS,
                "intro-1",
                "chords-v1",
                authorityHash,
                42,
                candidateMidi,
                candidateReport,
                "2026-08-27T00:01:00Z",
            )),
            listOf(CandidateAcceptance("intro-1", CandidateRole.CHORDS, "candidate-1", locked = true)),
            listOf(MidiCoreExportSnapshot(
                "export-1",
                source.sha256,
                authorityHash,
                listOf(
                    ExportedSnapshotFile(ExportedFileKind.COMPLETE_SONG, complete),
                    ExportedSnapshotFile(ExportedFileKind.MANIFEST, manifest),
                ),
                "2026-08-27T00:02:00Z",
            )),
        )
    }

    private fun emptyProject(name: String) = MidiCoreProject(
        ProjectId("project-1"),
        ProjectMetadata(name, "2026-08-27T00:00:00Z"),
    )

    private fun bytesFile(path: Path, value: String): Path = path.also { file ->
        Files.createDirectories(requireNotNull(file.parent))
        Files.writeString(file, value)
    }

    private fun digest(path: Path): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

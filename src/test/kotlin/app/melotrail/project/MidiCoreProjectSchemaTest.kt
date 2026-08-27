package app.melotrail.project

import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class MidiCoreProjectSchemaTest {
    @Test
    fun `v1 project encodes and decodes all MIDI Core ownership records`() {
        val project = completeProject()
        val serialized = MidiCoreProjectSchema.encode(project)

        assertEquals(project, MidiCoreProjectSchema.decode(serialized))
        assertEquals(goldenFixture(), serialized)
        assertEquals(goldenFixture(), MidiCoreProjectSchema.encode(MidiCoreProjectSchema.decode(serialized)))
    }

    @Test
    fun `legacy and future project versions are classified unsupported without migration`() {
        val legacy = """{"version":4,"name":"old-audio-project"}"""
        val future = """{"schema":"melotrail-midi-core","version":2,"project":{}}"""
        val unknown = """{"schema":"another-product","version":1,"project":{}}"""

        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(legacy))
        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(future))
        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(unknown))
        assertFailsWith<UnsupportedMidiCoreProjectException> { MidiCoreProjectSchema.decode(legacy) }
        assertFailsWith<UnsupportedMidiCoreProjectException> { MidiCoreProjectSchema.decode(future) }
    }

    @Test
    fun `missing required fields and unconfined artifact paths are invalid`() {
        assertIs<MidiCoreProjectDocument.Invalid>(MidiCoreProjectSchema.inspect("""{"schema":"melotrail-midi-core","version":1}"""))
        assertIs<MidiCoreProjectDocument.Invalid>(MidiCoreProjectSchema.inspect("""{"schema":{},"version":1}"""))
        assertFailsWith<IllegalArgumentException> { ProjectRelativePath("../outside.mid") }
        assertFailsWith<IllegalArgumentException> { ProjectRelativePath("/absolute.mid") }
        assertFailsWith<IllegalArgumentException> { ProjectRelativePath("C:/outside.mid") }

        val malformed = MidiCoreProjectSchema.encode(completeProject()).replace("source/original.mid", "../outside.mid")
        assertIs<MidiCoreProjectDocument.Invalid>(MidiCoreProjectSchema.inspect(malformed))

        val unknown = MidiCoreProjectSchema.encode(completeProject()).replace("\"id\": \"project-1\"", "\"unknown\": true,\n        \"id\": \"project-1\"")
        assertIs<MidiCoreProjectDocument.Invalid>(MidiCoreProjectSchema.inspect(unknown))
    }

    @Test
    fun `derived records cannot exist before their source and authority`() {
        val project = completeProject()

        assertFailsWith<IllegalArgumentException> {
            project.copy(authority = null)
        }
        assertFailsWith<IllegalArgumentException> {
            project.copy(sourceMidi = null, selectedMelody = null)
        }
        assertFailsWith<IllegalArgumentException> {
            ProjectAuthority(
                ProjectKey(0, "major"), 500_000, 4, 2,
                listOf(ProjectSectionDefinition("late", "Late")),
                listOf(ProjectSectionOccurrence("late-1", "late", "Late", 1, 480)),
                emptyList(),
            )
        }
    }

    @Test
    fun `source import may await explicit melody selection`() {
        val complete = completeProject()
        val imported = MidiCoreProject(
            id = complete.id,
            metadata = complete.metadata,
            sourceMidi = complete.sourceMidi,
        )

        assertEquals(imported, MidiCoreProjectSchema.decode(MidiCoreProjectSchema.encode(imported)))
        assertFailsWith<IllegalArgumentException> {
            imported.copy(candidates = complete.candidates)
        }
    }

    private fun completeProject(): MidiCoreProject {
        val sourceHash = "a".repeat(64)
        val authorityHash = "b".repeat(64)
        return MidiCoreProject(
            id = ProjectId("project-1"),
            metadata = ProjectMetadata("MIDI Core fixture", "2026-08-27T00:00:00Z", "test-1"),
            sourceMidi = SourceMidiRecord(
                "fixture.mid",
                sourceHash,
                1,
                480,
                artifact("source/original.mid", sourceHash),
                artifact("reports/import.json", "1".repeat(64)),
                listOf(
                    MidiTrackSummary(0, "Conductor", emptyList()),
                    MidiTrackSummary(1, "Melody", listOf(MidiChannelSummary(0, 1, 60, 60, 0, listOf(MidiTrackRoleHint.MELODY)))),
                ),
                480,
            ),
            selectedMelody = SelectedMelodyTrack(1, 0, "c".repeat(64)),
            authority = ProjectAuthority(
                ProjectKey(0, "major"), 500_000, 4, 2,
                listOf(ProjectSectionDefinition("intro", "Intro")),
                listOf(ProjectSectionOccurrence("intro-1", "intro", "Intro", 0, 480)),
                listOf(AuthoritativeChordEvent("chord-1", "intro-1", "C", 0, 480)),
            ),
            candidates = listOf(MidiCoreCandidate(
                "candidate-1", CandidateRole.CHORDS, "intro-1", "chords-v1", authorityHash, 42,
                artifact("candidates/chords/intro-1/candidate-1.mid", "d".repeat(64)),
                artifact("reports/candidates/candidate-1.json", "e".repeat(64)), "2026-08-27T00:01:00Z",
            )),
            acceptances = listOf(CandidateAcceptance("intro-1", CandidateRole.CHORDS, "candidate-1", locked = true)),
            exportSnapshots = listOf(MidiCoreExportSnapshot(
                "export-1", sourceHash, authorityHash,
                listOf(
                    ExportedSnapshotFile(ExportedFileKind.COMPLETE_SONG, artifact("exports/export-1/complete-song.mid", "f".repeat(64))),
                    ExportedSnapshotFile(ExportedFileKind.MANIFEST, artifact("exports/export-1/manifest.json", "0".repeat(64))),
                ),
                "2026-08-27T00:02:00Z",
            )),
        )
    }

    private fun artifact(path: String, hash: String) = ProjectArtifact(ProjectRelativePath(path), hash)

    private fun goldenFixture(): String = requireNotNull(
        javaClass.getResource("/fixtures/project/midi-core-v1.json")
    ).readText().trimEnd()
}

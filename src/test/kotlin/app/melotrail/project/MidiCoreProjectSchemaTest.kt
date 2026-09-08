package app.melotrail.project

import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectTempo
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class MidiCoreProjectSchemaTest {
    @Test
    fun `v4 project encodes and decodes versioned arrangement authority`() {
        val project = completeProject()
        val serialized = MidiCoreProjectSchema.encode(project)

        assertEquals(project, MidiCoreProjectSchema.decode(serialized))
        assertEquals(4, kotlinx.serialization.json.Json.parseToJsonElement(serialized).jsonObject["version"]?.jsonPrimitive?.int)
        assertEquals(serialized, MidiCoreProjectSchema.encode(MidiCoreProjectSchema.decode(serialized)))
    }

    @Test
    fun `unsupported and future project versions are classified unsupported without migration`() {
        val legacy = """{"schema":"retired-project","version":1,"project":{}}"""
        val retired = """{"schema":"melotrail-midi-core","version":2,"project":{}}"""
        val previousCurrent = """{"schema":"melotrail-midi-core","version":3,"project":{}}"""
        val future = """{"schema":"melotrail-midi-core","version":5,"project":{}}"""
        val unknown = """{"schema":"another-product","version":1,"project":{}}"""

        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(legacy))
        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(retired))
        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(previousCurrent))
        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(future))
        assertIs<MidiCoreProjectDocument.Unsupported>(MidiCoreProjectSchema.inspect(unknown))
        assertFailsWith<UnsupportedMidiCoreProjectException> { MidiCoreProjectSchema.decode(legacy) }
        assertFailsWith<UnsupportedMidiCoreProjectException> { MidiCoreProjectSchema.decode(retired) }
        assertFailsWith<UnsupportedMidiCoreProjectException> { MidiCoreProjectSchema.decode(future) }
    }

    @Test
    fun `missing required fields and unconfined artifact paths are invalid`() {
        assertIs<MidiCoreProjectDocument.Invalid>(MidiCoreProjectSchema.inspect("""{"schema":"melotrail-midi-core","version":4}"""))
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
    fun `malformed arrangement plans fail before they can become authority`() {
        val serialized = MidiCoreProjectSchema.encode(completeProject())

        assertIs<MidiCoreProjectDocument.Invalid>(
            MidiCoreProjectSchema.inspect(serialized.replace("\"density\": 48", "\"density\": 101")),
        )
        assertIs<MidiCoreProjectDocument.Invalid>(
            MidiCoreProjectSchema.inspect(serialized.replace("\"version\": 1,", "\"version\": 2,")),
        )
    }

    @Test
    fun `candidate piano boundary digest round trips and malformed evidence is invalid`() {
        val digest = "9".repeat(64)
        val base = completeProject()
        val project = base.copy(candidates = base.candidates.map { it.copy(boundarySummarySha256 = digest) })
        val serialized = MidiCoreProjectSchema.encode(project)

        assertEquals(digest, MidiCoreProjectSchema.decode(serialized).candidates.single().boundarySummarySha256)
        assertIs<MidiCoreProjectDocument.Invalid>(
            MidiCoreProjectSchema.inspect(serialized.replace(digest, "not-a-digest")),
        )
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
                ProjectKey(0, "major"), ProjectTempo(500_000), ProjectMeter(4, 2),
                listOf(ProjectSectionDefinition("late", "Late")),
                listOf(ProjectSectionOccurrence("late-1", "late", "Late", 1, 480)),
                emptyList(),
            )
        }
    }

    @Test
    fun `arrangement plans must retain the exact authoritative occurrence identity`() {
        val project = completeProject()
        val wrongOccurrence = requireNotNull(project.arrangementPlan).copy(
            occurrences = requireNotNull(project.arrangementPlan).occurrences.map { it.copy(occurrenceId = "other-1") },
        )

        assertFailsWith<IllegalArgumentException> { project.copy(arrangementPlan = wrongOccurrence) }
    }

    @Test
    fun `source import and protected melody identity are atomic`() {
        val complete = completeProject()
        val imported = MidiCoreProject(
            id = complete.id,
            metadata = complete.metadata,
            sourceMidi = complete.sourceMidi,
            selectedMelody = complete.selectedMelody,
        )

        assertEquals(imported, MidiCoreProjectSchema.decode(MidiCoreProjectSchema.encode(imported)))
        assertFailsWith<IllegalArgumentException> {
            imported.copy(selectedMelody = null)
        }
        assertFailsWith<IllegalArgumentException> {
            MidiCoreProject(id = complete.id, metadata = complete.metadata, selectedMelody = complete.selectedMelody)
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
                ProjectKey(0, "major"), ProjectTempo(500_000), ProjectMeter(4, 2),
                listOf(ProjectSectionDefinition("intro", "Intro")),
                listOf(ProjectSectionOccurrence("intro-1", "intro", "Intro", 0, 480)),
                listOf(AuthoritativeChordEvent("chord-1", "intro-1", "C", 0, 480)),
            ),
            arrangementPlan = arrangementPlan(),
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

    private fun arrangementPlan() = MidiCoreArrangementPlan(
        MidiCoreArrangementPlan.VERSION,
        MidiCoreSharedGrooveIntent(MidiCoreGrooveFeel.STRAIGHT, MidiCoreGrooveSubdivision.EIGHTH, MidiCoreGrooveDrive.STEADY),
        listOf(
            MidiCoreOccurrenceArrangementPlan(
                "intro-1", MidiCoreArrangementPurpose.INTRO, "phrase-a", "intro", 1, 24,
                listOf(
                    MidiCoreRolePlanSettings(CandidateRole.CHORDS, MidiCoreRoleActivity.SPARSE, 48, MidiCoreRegisterPreference.MID),
                    MidiCoreRolePlanSettings(CandidateRole.BASS, MidiCoreRoleActivity.INACTIVE, 0, MidiCoreRegisterPreference.LOW),
                    MidiCoreRolePlanSettings(CandidateRole.DRUMS, MidiCoreRoleActivity.INACTIVE, 0, MidiCoreRegisterPreference.OPEN),
                ),
                MidiCoreBoundaryIntent.GRADUAL_ENTRY,
                MidiCoreBoundaryIntent.RELEASE,
            ),
        ),
    )

    private fun goldenFixture(): String = requireNotNull(
        javaClass.getResource("/fixtures/project/midi-core-v1.json")
    ).readText().trimEnd()
}

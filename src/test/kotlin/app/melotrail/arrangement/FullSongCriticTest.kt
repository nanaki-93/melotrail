package app.melotrail.arrangement

import app.melotrail.application.CanonicalChord
import app.melotrail.application.HarmonicTimelineEntry
import app.melotrail.application.MusicalOccurrence
import app.melotrail.application.WholeSongAnalysisProjection
import app.melotrail.harmony.ChordQuality
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullSongCriticTest {
    @TempDir lateinit var root: Path

    @Test fun `harmonic clash starts at the exact half-beat threshold and never modifies MIDI`() {
        val midi = write("pad.mid", listOf(0L to 240L to 61))
        val before = Files.readAllBytes(midi)

        val report = DeterministicFullSongCritic().criticize(input(artifact("pad", midi)))

        assertTrue(report.issues.any { it.category == FullSongIssueCategory.HARMONIC_CLASH })
        assertEquals(before.toList(), Files.readAllBytes(midi).toList())
        assertEquals(report.reportSha256, DeterministicFullSongCritic().criticize(input(artifact("pad", midi))).reportSha256)
    }

    @Test fun `bass leap is reported only above nineteen semitones without opposite step resolution`() {
        val exact = write("exact.mid", listOf(0L to 120L to 40, 120L to 240L to 59))
        val over = write("over.mid", listOf(0L to 120L to 40, 120L to 240L to 60))

        assertFalse(DeterministicFullSongCritic().criticize(input(artifact("bass", exact))).issues.any { it.category == FullSongIssueCategory.BASS_LEAP })
        assertTrue(DeterministicFullSongCritic().criticize(input(artifact("bass", over))).issues.any { it.category == FullSongIssueCategory.BASS_LEAP })
    }

    @Test fun `same-pitch half-beat cross-role overlap is a collision with deterministic evidence`() {
        val left = write("pad.mid", listOf(0L to 240L to 60))
        val right = write("strings.mid", listOf(0L to 240L to 60))

        val report = DeterministicFullSongCritic().criticize(input(artifact("pad", left), artifact("strings", right)))

        val issue = report.issues.single { it.category == FullSongIssueCategory.VOICE_COLLISION }
        assertEquals(0, issue.window.startTick)
        assertEquals(240, issue.window.endTick)
        assertEquals(listOf(FullSongCorrectionFamily.COLLISION_REMOVAL, FullSongCorrectionFamily.CHORD_REVOICING), issue.suggestedCorrections)
    }

    @Test fun `optional Qwen critic receives only deterministic evidence and cannot replace issues`() {
        val midi = write("pad.mid", listOf(0L to 240L to 61))
        val deterministic = DeterministicFullSongCritic().criticize(input(artifact("pad", midi)))
        var prompt = ""

        val advice = LocalQwenFullSongCriticAdvisor(LocalQwenClient { _, userPrompt ->
            prompt = userPrompt
            "{\"observations\":[\"Reduce the sustained pad under the melody.\"]}"
        }, "fake-critic-v1").advise(deterministic)

        assertEquals("fake-critic-v1", advice.modelIdentity)
        assertTrue(prompt.contains("HARMONIC_CLASH"))
        assertFalse(prompt.contains("pad.mid"))
        assertTrue(deterministic.issues.any { it.category == FullSongIssueCategory.HARMONIC_CLASH })
    }

    @Test fun `critic locates bass melody dependence and masking in the affected occurrence`() {
        val piano = write("piano.mid", listOf(0L to 240L to 60, 240L to 480L to 64, 480L to 720L to 67))
        val bass = write("bass.mid", listOf(0L to 240L to 48, 240L to 480L to 52, 480L to 720L to 55))

        val issues = DeterministicFullSongCritic().criticize(input(occurrenceArtifact(piano), artifact("bass", bass))).issues

        assertTrue(issues.any { it.category == FullSongIssueCategory.BASS_MELODY_DEPENDENCE && it.occurrenceId == "one" })
        assertTrue(issues.any { it.category == FullSongIssueCategory.MASKING && it.occurrenceId == "one" })
    }

    private fun input(vararg artifacts: FullSongCriticMidiArtifact): FullSongCriticInput = FullSongCriticInput(
        authority = WholeSongAnalysisProjection(
            contextSha256 = "a".repeat(64), projectKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), tempo = Tempo(120.0), meter = TimeSignature(4, 4), harmonyPpq = 480,
            occurrences = listOf(MusicalOccurrence("one", "A", SectionTypeId.VERSE, 0, 1, 0, 1920)),
            harmony = listOf(HarmonicTimelineEntry("one", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 0, 0, 1920)),
            selectedParts = emptyList(), analyzedFacts = emptyList(), melodyEvidence = emptyList(), approvedArrangement = WorkflowArtifactReference("arrangement.json", "b".repeat(64)), generatedRoles = emptyList()
        ),
        cohesionOccurrences = emptyList(), cohesionRoles = artifacts.toList(), approvedArrangement = DetailedArrangement(sections = emptyList()), approvedArrangementSha256 = "b".repeat(64), roleReports = emptyList(), inputSha256 = "c".repeat(64)
    )

    private fun artifact(role: String, path: Path) = FullSongCriticMidiArtifact(role, null, path, WorkflowArtifactReference(path.fileName.toString(), sha256(path)))
    private fun occurrenceArtifact(path: Path) = FullSongCriticMidiArtifact("piano", "one", path, WorkflowArtifactReference(path.fileName.toString(), sha256(path)))
    private fun write(name: String, notes: List<Pair<Pair<Long, Long>, Int>>): Path {
        val path = root.resolve(name); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        notes.forEach { (range, pitch) -> track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), range.first)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), range.second)) }
        MidiSystem.write(sequence, 1, path.toFile()); return path
    }
    private fun sha256(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

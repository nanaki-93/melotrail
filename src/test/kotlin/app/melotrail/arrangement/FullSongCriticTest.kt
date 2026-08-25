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

    @Test fun `critic keeps uncapped aggregate evidence when its displayed issue list is bounded`() {
        val path = root.resolve("unmatched.mid"); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        repeat(70) { index -> track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 48, 0), index * 480L)) }
        MidiSystem.write(sequence, 1, path.toFile())

        val report = DeterministicFullSongCritic().criticize(input(artifact("bass", path)))
        fun metric(name: String) = report.aggregateMetrics.single { it.name == name }.value.toInt()

        assertEquals(70, metric("issueCount"))
        assertEquals(64, metric("displayedIssueCount"))
        assertEquals(70, metric("blockingIssueCount"))
        assertEquals(64, report.issues.size)
        assertEquals(listOf("issue-truncated-6"), report.warnings)
    }

    @Test fun `critic retains every actionable issue for bounded enhancement batches`() {
        val midi = write("many-clashes.mid", (0 until 70).map { index ->
            val start = index * 480L
            start to start + 240L to 61
        })
        val source = input(artifact("pad", midi))
        val end = 70 * 480L
        val authority = source.authority.copy(
            occurrences = listOf(MusicalOccurrence("one", "A", SectionTypeId.VERSE, 0, 1, 0, end)),
            harmony = listOf(HarmonicTimelineEntry("one", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 0, 0, end))
        )

        val report = DeterministicFullSongCritic().criticize(source.copy(authority = authority))
        fun metric(name: String) = report.aggregateMetrics.single { it.name == name }.value.toInt()

        assertEquals(70, metric("actionableIssueCount"))
        assertEquals(64, metric("displayedIssueCount"))
        assertEquals(64, report.issues.size)
        assertEquals(70, report.actionableIssueEvidence.size)
        assertEquals((0 until 70).map { it * 480L }, report.actionableIssueEvidence.map { it.window.startTick })
    }

    @Test fun `anchor preservation is assessed in each canonical occurrence`() {
        val connected = write("connected.mid", listOf(0L to 240L to 60, 1_920L to 2_160L to 64))
        val first = write("first.mid", listOf(0L to 240L to 60))
        val second = write("second.mid", listOf(0L to 240L to 64))
        val base = input()
        val authority = base.authority.copy(
            occurrences = listOf(
                MusicalOccurrence("one", "A", SectionTypeId.VERSE, 0, 1, 0, 1_920),
                MusicalOccurrence("two", "B", SectionTypeId.VERSE, 1, 1, 1_920, 3_840)
            ),
            harmony = listOf(
                HarmonicTimelineEntry("one", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 0, 0, 1_920),
                HarmonicTimelineEntry("two", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 1, 1_920, 3_840)
            )
        )
        val criticInput = base.copy(
            authority = authority,
            cohesionOccurrences = listOf(
                FullSongCriticMidiArtifact("piano", "one", first, WorkflowArtifactReference("first.mid", sha256(first)), 0),
                FullSongCriticMidiArtifact("piano", "two", second, WorkflowArtifactReference("second.mid", sha256(second)), 1_920)
            ),
            melodyIdentity = MelodyIdentityBuilder.build(connected, 480),
            approvedArrangement = DetailedArrangement(sections = listOf(
                DetailedArrangementSection(0, "one", "A", SongSectionPurpose.DEVELOPMENT, 0.5, emptyList(), TransitionPlan()),
                DetailedArrangementSection(1, "two", "B", SongSectionPurpose.DEVELOPMENT, 0.5, emptyList(), TransitionPlan())
            ))
        )

        val issues = DeterministicFullSongCritic().criticize(criticInput).issues

        assertFalse(issues.any { it.category == FullSongIssueCategory.RECOGNIZABILITY_REGRESSION })
    }

    @Test fun `density compares the observed and arrangement targets on the same normalized scale`() {
        val bass = write("bass-density.mid", listOf(0L to 120L to 48, 960L to 1_080L to 48))
        val section = DetailedArrangementSection(
            0, "one", "A", SongSectionPurpose.DEVELOPMENT, 0.5,
            listOf(BassInstrumentPlan(role = DetailedBassRole.ROOT, density = 0.5, movement = DetailedBassMovement.STATIC, register = MusicalRegister.LOW, syncopation = 0.0)),
            TransitionPlan()
        )
        val criticInput = input(artifact("bass", bass)).copy(approvedArrangement = DetailedArrangement(sections = listOf(section)))

        val issues = DeterministicFullSongCritic().criticize(criticInput).issues

        assertFalse(issues.any { it.category == FullSongIssueCategory.DENSITY_MISMATCH })
    }

    @Test fun `syncopated hats do not make an otherwise steady kick pulse look incoherent`() {
        val drums = write("steady-kick-with-fill.mid", listOf(
            0L to 120L to 36, 960L to 1_080L to 36, 1_920L to 2_040L to 36, 2_880L to 3_000L to 36,
            240L to 360L to 42, 480L to 600L to 42, 720L to 840L to 38,
            3_840L to 3_960L to 36, 4_800L to 4_920L to 36, 5_760L to 5_880L to 36, 6_720L to 6_840L to 36
        ))
        val base = input(artifact("drums", drums))
        val authority = base.authority.copy(
            occurrences = listOf(
                MusicalOccurrence("one", "A", SectionTypeId.VERSE, 0, 1, 0, 3_840),
                MusicalOccurrence("two", "B", SectionTypeId.VERSE, 1, 2, 3_840, 7_680)
            ),
            harmony = listOf(
                HarmonicTimelineEntry("one", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 0, 0, 3_840),
                HarmonicTimelineEntry("two", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 1, 3_840, 7_680)
            )
        )

        val issues = DeterministicFullSongCritic().criticize(base.copy(authority = authority, approvedArrangement = twoSectionArrangement())).issues

        assertFalse(issues.any { it.category == FullSongIssueCategory.GROOVE_INCOHERENCE })
    }

    @Test fun `a consistent off-grid kick pulse is still reported at the section boundary`() {
        val drums = write("shifted-kick.mid", listOf(
            240L to 360L to 36, 1_200L to 1_320L to 36, 2_160L to 2_280L to 36, 3_120L to 3_240L to 36,
            3_840L to 3_960L to 36, 4_800L to 4_920L to 36, 5_760L to 5_880L to 36, 6_720L to 6_840L to 36
        ))
        val base = input(artifact("drums", drums))
        val authority = base.authority.copy(
            occurrences = listOf(
                MusicalOccurrence("one", "A", SectionTypeId.VERSE, 0, 1, 0, 3_840),
                MusicalOccurrence("two", "B", SectionTypeId.VERSE, 1, 2, 3_840, 7_680)
            ),
            harmony = listOf(
                HarmonicTimelineEntry("one", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 0, 0, 3_840),
                HarmonicTimelineEntry("two", SectionTypeId.VERSE, CanonicalChord(0, "C", ChordQuality.MAJOR), 1, 3_840, 7_680)
            )
        )

        val issue = DeterministicFullSongCritic().criticize(base.copy(authority = authority, approvedArrangement = twoSectionArrangement())).issues.single { it.category == FullSongIssueCategory.GROOVE_INCOHERENCE }

        assertEquals(240.0, issue.observed.single { it.name == "phaseDeltaTicks" }.value, 0.001)
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

    private fun twoSectionArrangement() = DetailedArrangement(sections = listOf(
        DetailedArrangementSection(0, "one", "A", SongSectionPurpose.DEVELOPMENT, 0.5, emptyList(), TransitionPlan()),
        DetailedArrangementSection(1, "two", "B", SongSectionPurpose.DEVELOPMENT, 0.5, emptyList(), TransitionPlan())
    ))

    private fun artifact(role: String, path: Path) = FullSongCriticMidiArtifact(role, null, path, WorkflowArtifactReference(path.fileName.toString(), sha256(path)))
    private fun occurrenceArtifact(path: Path) = FullSongCriticMidiArtifact("piano", "one", path, WorkflowArtifactReference(path.fileName.toString(), sha256(path)))
    private fun write(name: String, notes: List<Pair<Pair<Long, Long>, Int>>): Path {
        val path = root.resolve(name); val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        notes.forEach { (range, pitch) -> track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), range.first)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), range.second)) }
        MidiSystem.write(sequence, 1, path.toFile()); return path
    }
    private fun sha256(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

package app.melotrail.arrangement

import app.melotrail.harmony.ChordQuality
import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MidiHarmonyFitterTest {
    @TempDir lateinit var root: Path

    @Test
    fun `intro verse chorus bridge and outro retain their distinct authoritative progressions`() {
        val input = midi("five-sections.mid", 9_600) { track ->
            track.note(0, 0, 60, 90, 480)
            track.note(0, 1_920, 62, 90, 2_400)
            track.note(0, 3_840, 62, 90, 4_320)
            track.note(0, 5_760, 64, 90, 6_240)
            track.note(0, 7_680, 65, 90, 8_160)
        }
        val authority = context(
            "intro-verse-chorus-bridge-outro",
            listOf(0 to ChordQuality.MAJOR, 2 to ChordQuality.MINOR, 7 to ChordQuality.MAJOR, 9 to ChordQuality.MINOR, 5 to ChordQuality.MAJOR)
        )
        val beforeAuthority = authority

        val result = fit(input, authority)

        assertEquals(beforeAuthority, authority)
        assertEquals(listOf(60, 62, 62, 64, 65), notes(result).map(Note::pitch))
        assertEquals(listOf(0, 2, 7, 9, 5), result.fitting.context.harmonicSpans.map(MelodyHarmonyFitSpan::rootChromatic))
        assertTrue(result.fitting.outputNotes.all { it.eligibility == MelodyHarmonyEligibility.CHORD_TONE })
    }

    @Test
    fun `exposed clash is repaired while legitimate weak scale passing tone survives without authority mutation`() {
        val input = midi("clash-and-passing.mid", 3_840) { track ->
            track.note(0, 0, 63, 90, 360)
            track.note(0, 720, 62, 80, 840)
            track.note(0, 840, 64, 85, 1_200)
            track.note(0, 1_920, 62, 90, 2_400)
        }
        val authority = context("verse-chorus", listOf(0 to ChordQuality.MAJOR, 2 to ChordQuality.MINOR))
        val before = Files.readAllBytes(input)

        val result = fit(input, authority)

        assertContentEquals(before, Files.readAllBytes(input))
        assertEquals(listOf(64, 62, 64, 62), notes(result).map(Note::pitch))
        assertEquals(MelodyHarmonyEligibility.WEAK_SCALE_PASSING_TONE, result.fitting.outputNotes.single { it.startTick == 720L }.eligibility)
        assertTrue(result.fitting.noteDecisions.single { it.before.startTick == 0L }.reasons.contains(MelodyHarmonyFitReason.REPAIRED_TO_ACTIVE_CHORD_TONE))
        assertEquals("C major", authority.projectKey.displayName)
    }

    @Test
    fun `sustain and transcription tail is shortened before next chord with a tempo PPQ derived gap`() {
        val input = midi("sustain-tail.mid", 3_840) { track ->
            track.control(0, 0, 64, 127)
            track.note(0, 0, 60, 95, 1_200)
            track.control(0, 2_400, 64, 0)
        }
        val result = fit(input, context("tail", listOf(0 to ChordQuality.MAJOR, 2 to ChordQuality.MINOR)))

        assertEquals(listOf(Note(0, 1_890, 60, 95)), notes(result))
        assertEquals(30L, result.fitting.gapPolicy.derivedGapTicks)
        val boundary = result.fitting.boundaries.single { it.tick == 1_920L }
        assertEquals(MelodyHarmonyBoundaryDecision.SHORTENED_INCOMPATIBLE_NOTE, boundary.decision)
        assertEquals(MelodyHarmonyControllerBehavior.MATERIALIZED_BY_MONOPHONIC_PREPARATION_AND_REMOVED_FROM_OUTPUT, boundary.controllerBehavior)
    }

    @Test
    fun `required chord-boundary release does not consume the bounded pitch repair budget`() {
        val input = midi("repairs-and-release.mid", 7_680) { track ->
            track.note(0, 0, 65, 90, 480)
            track.note(0, 480, 64, 90, 1_950)
            track.note(0, 1_950, 60, 90, 3_850)
            track.note(0, 3_850, 60, 90, 5_000)
            track.note(0, 5_000, 64, 90, 5_780)
            track.note(0, 5_780, 67, 90, 7_000)
            track.note(0, 7_000, 64, 90, 7_200)
        }

        val result = fit(input, context("repairs-and-release", listOf(
            0 to ChordQuality.MAJOR_7,
            9 to ChordQuality.MINOR_7,
            5 to ChordQuality.MAJOR_7,
            7 to ChordQuality.DOMINANT_7
        )))

        assertEquals(listOf(64, 64, 60, 60, 64, 67, 65), notes(result).map(Note::pitch))
        assertEquals(5_730L, notes(result)[4].end)
        assertTrue(result.fitting.issues.isEmpty())
    }

    @Test
    fun `sub-sixteenth transcription ornaments do not consume the recognizable pitch repair budget`() {
        val input = midi("repair-ornaments.mid", 1_920) { track ->
            track.note(0, 0, 65, 90, 480)
            track.note(0, 500, 65, 80, 530)
            track.note(0, 540, 65, 80, 570)
            track.note(0, 580, 65, 80, 610)
        }

        val result = fit(input, context("repair-ornaments", listOf(0 to ChordQuality.MAJOR_7)))

        assertEquals(listOf(64, 64, 64, 64), notes(result).map(Note::pitch))
        assertTrue(result.fitting.issues.isEmpty())
    }

    @Test
    fun `equal nearest chord tones use an explicit later contour note rather than guessing`() {
        val input = midi("contour-tie-break.mid", 1_920) { track ->
            track.note(0, 240, 62, 90, 480)
            track.note(0, 720, 60, 90, 960)
        }

        val result = fit(input, context("contour-tie-break", listOf(0 to ChordQuality.MAJOR)))

        assertEquals(listOf(60, 60), notes(result).map(Note::pitch))
        assertTrue(result.fitting.issues.isEmpty())
    }

    @Test
    fun `at most half of recognizable notes may receive bounded pitch repairs`() {
        val input = midi("half-repair-budget.mid", 1_920) { track ->
            track.note(0, 0, 65, 90, 360)
            track.note(0, 480, 65, 90, 840)
            track.note(0, 960, 64, 90, 1_320)
            track.note(0, 1_440, 67, 90, 1_800)
        }

        val result = fit(input, context("half-repair-budget", listOf(0 to ChordQuality.MAJOR_7)))

        assertEquals(listOf(64, 64, 64, 67), notes(result).map(Note::pitch))
        assertTrue(result.fitting.issues.isEmpty())
    }

    @Test
    fun `valid common tone tie remains intact with explicit boundary evidence`() {
        val input = midi("common-tone.mid", 3_840) { track -> track.note(0, 0, 60, 90, 2_400) }
        val result = fit(input, context("common-tone", listOf(0 to ChordQuality.MAJOR, 9 to ChordQuality.MINOR)))

        assertEquals(listOf(Note(0, 2_400, 60, 90)), notes(result))
        assertEquals(MelodyHarmonyEligibility.COMMON_TONE_TIE, result.fitting.outputNotes.single().eligibility)
        assertEquals(MelodyHarmonyBoundaryDecision.PRESERVED_COMMON_TONE_TIE, result.fitting.boundaries.single { it.tick == 1_920L }.decision)
    }

    @Test
    fun `stepwise explicit suspension remains only with recorded resolution evidence`() {
        val input = midi("suspension.mid", 3_840) { track ->
            track.note(0, 0, 60, 90, 2_000)
            track.note(0, 2_000, 62, 90, 2_400)
        }
        val result = fit(input, context("suspension", listOf(0 to ChordQuality.MAJOR, 2 to ChordQuality.MINOR)))

        assertEquals(listOf(Note(0, 2_000, 60, 90), Note(2_000, 2_400, 62, 90)), notes(result))
        assertEquals(MelodyHarmonyEligibility.DELIBERATE_SUSPENSION, result.fitting.outputNotes.first().eligibility)
        val boundary = result.fitting.boundaries.single { it.tick == 1_920L }
        assertEquals(MelodyHarmonyBoundaryDecision.PRESERVED_DELIBERATE_SUSPENSION, boundary.decision)
        assertEquals("n-00001", boundary.resolutionNoteId)
    }

    @Test
    fun `active user authored chromatic chord is authorized even outside project scale`() {
        val input = midi("chromatic-chord.mid", 1_920) { track -> track.note(0, 0, 61, 90, 480) }
        val result = fit(input, context("chromatic", listOf(1 to ChordQuality.MAJOR)))

        assertEquals(listOf(Note(0, 480, 61, 90)), notes(result))
        assertTrue(result.fitting.noteDecisions.single().reasons.contains(MelodyHarmonyFitReason.AUTHORIZED_CHROMATIC_CHORD_TONE))
    }

    @Test
    fun `ambiguous equal nearest repair blocks with durable report rather than guessing`() {
        val input = midi("ambiguous.mid", 1_920) { track -> track.note(0, 0, 62, 90, 480) }
        val prepared = prepare(input)
        val request = MelodyHarmonyFitRequest(root, prepared.midi, prepared.report, context("ambiguous", listOf(0 to ChordQuality.MAJOR)))

        val failure = assertFailsWith<IllegalArgumentException> { MidiHarmonyFitter().fit(request) }
        val reportPath = Files.list(root.resolve("analysis/harmony-fit/A/ambiguous")).use { stream -> stream.findFirst().orElseThrow() }
        val report = Json.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(reportPath))

        assertTrue(failure.message.orEmpty().contains("blocked"))
        assertEquals(MelodyHarmonyFitStatus.BLOCKED, report.status)
        assertTrue(report.issues.any { it.kind == MelodyHarmonyFitIssueKind.AMBIGUOUS_NEAREST_PITCH })
        assertEquals(null, report.output)
    }

    @Test
    fun `excessive movement and edit budget each block instead of publishing a weakened repair`() {
        val far = midi("far.mid", 1_920) { track -> track.note(0, 0, 0, 90, 480) }
        val farPrepared = prepare(far)
        val farRequest = MelodyHarmonyFitRequest(root, farPrepared.midi, farPrepared.report, context("far", listOf(11 to ChordQuality.MAJOR)))
        assertFailsWith<IllegalArgumentException> { MidiHarmonyFitter().fit(farRequest) }
        val farReport = readReport("far")

        val many = midi("many.mid", 7_680) { track ->
            (0 until 4).forEach { bar -> track.note(0, bar * 1_920L, 63, 90, bar * 1_920L + 480L) }
        }
        val manyPrepared = prepare(many)
        val manyRequest = MelodyHarmonyFitRequest(root, manyPrepared.midi, manyPrepared.report, context("many", List(4) { 0 to ChordQuality.MAJOR }))
        assertFailsWith<IllegalArgumentException> { MidiHarmonyFitter().fit(manyRequest) }
        val manyReport = readReport("many")

        assertTrue(farReport.issues.any { it.kind == MelodyHarmonyFitIssueKind.EXCESSIVE_PITCH_MOVEMENT })
        assertTrue(manyReport.issues.any { it.kind == MelodyHarmonyFitIssueKind.EXCESSIVE_EDIT_BUDGET })
        assertEquals(null, farReport.output)
        assertEquals(null, manyReport.output)
    }

    private fun fit(input: Path, authority: MelodyHarmonyFitContext): MelodyHarmonyFitArtifact {
        val prepared = prepare(input)
        return MidiHarmonyFitter().fit(MelodyHarmonyFitRequest(root, prepared.midi, prepared.report, authority))
    }

    private fun prepare(input: Path): MonophonicMelodyPreparationArtifact = MidiMonophonicMelodyPreparer().prepare(root, "A", reference(input))

    private fun readReport(occurrenceId: String): MelodyHarmonyFitReport {
        val reportPath = Files.list(root.resolve("analysis/harmony-fit/A/$occurrenceId")).use { stream -> stream.findFirst().orElseThrow() }
        return Json.decodeFromString(MelodyHarmonyFitReport.serializer(), Files.readString(reportPath))
    }

    private fun context(occurrenceId: String, chords: List<Pair<Int, ChordQuality>>): MelodyHarmonyFitContext = MelodyHarmonyFitContext(
        authorityContextSha256 = "a".repeat(64), partId = "A", occurrenceId = occurrenceId,
        projectKey = MusicalKey(PitchClass.of(PitchSpelling.C), ScaleModeId.MAJOR), tempoBpm = 120.0,
        meterNumerator = 4, meterDenominator = 4, ppq = 480,
        harmonicSpans = chords.mapIndexed { index, (root, quality) ->
            MelodyHarmonyFitSpan(index.toLong(), index * 1_920L, (index + 1) * 1_920L, root, listOf("C", "D", "G", "A", "F")[index % 5], quality)
        }
    )

    private fun midi(name: String, tickLength: Long, populate: (TrackBuilder) -> Unit): Path {
        val path = root.resolve("midi/input/$name")
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        populate(TrackBuilder(track))
        track.add(MidiEvent(MetaMessage().also { it.setMessage(0x2F, byteArrayOf(), 0) }, tickLength))
        MidiSystem.write(sequence, 1, path.toFile())
        return path
    }

    private fun reference(path: Path): MelodyPreparationArtifactReference {
        val sequence = MidiSystem.getSequence(path.toFile())
        val noteCount = sequence.tracks.sumOf { track -> (0 until track.size()).count { index ->
            val message = track[index].message as? ShortMessage
            message?.command == ShortMessage.NOTE_ON && message.data2 > 0
        } }
        return MelodyPreparationArtifactReference(root.relativize(path).toString(), hash(path), sequence.resolution, noteCount)
    }

    private fun notes(artifact: MelodyHarmonyFitArtifact): List<Note> = notes(MidiSystem.getSequence(root.resolve(artifact.midi.path).toFile()))

    private fun notes(sequence: Sequence): List<Note> {
        val active = mutableMapOf<Int, Pair<Long, Int>>()
        val output = mutableListOf<Note>()
        sequence.tracks.single().let { track -> (0 until track.size()).forEach { index ->
            val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
            when {
                message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active[message.data1] = event.tick to message.data2
                message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                    val start = active.remove(message.data1) ?: error("unmatched test note")
                    output += Note(start.first, event.tick, message.data1, start.second)
                }
            }
        } }
        return output.sortedBy(Note::start)
    }

    private fun hash(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private data class Note(val start: Long, val end: Long, val pitch: Int, val velocity: Int)

    private class TrackBuilder(private val track: javax.sound.midi.Track) {
        fun note(channel: Int, start: Long, pitch: Int, velocity: Int, end: Long) { noteOn(channel, start, pitch, velocity); noteOff(channel, end, pitch) }
        fun noteOn(channel: Int, tick: Long, pitch: Int, velocity: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, channel, pitch, velocity), tick)) }
        fun noteOff(channel: Int, tick: Long, pitch: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, channel, pitch, 0), tick)) }
        fun control(channel: Int, tick: Long, controller: Int, value: Int) { track.add(MidiEvent(ShortMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value), tick)) }
    }
}

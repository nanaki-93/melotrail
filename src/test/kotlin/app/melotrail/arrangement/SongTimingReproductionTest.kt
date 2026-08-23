package app.melotrail.arrangement

import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.AudioFormat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import kotlin.math.roundToLong

class SongTimingReproductionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `selected lofi analysis and piano render use the same artifact identity`() = runBlocking {
        val clean = root.resolve("midi/clean/A.mid")
        writeStraightEighths(clean)
        Files.createDirectories(root.resolve("source"))
        Files.copy(clean, root.resolve("source/A.mid"))
        val raw = root.resolve("midi/raw/A.mid")
        Files.createDirectories(raw.parent)
        Files.copy(clean, raw)
        val cleanup = MidiCleanupOptions()
        val quality = MidiQualityReporter().report("A", raw, clean, cleanup)
        val qualityPath = MidiQualityReportStore.write(root, quality)
        val qualityReference = root.relativize(qualityPath).toString()
        val lofi = root.resolve("midi/feel/A-lofi.mid")
        Files.createDirectories(lofi.parent)
        val feelReport = MidiLoFiFeelTransformer().transform(clean, lofi, "A").report
        val feelReportPath = MidiFeelReportStore.write(root, feelReport)
        val selectedAnalysis = MidiPartAnalyzer().analyze(lofi, "A")
        val renderer = CapturingRenderer()
        val project = Project(
            Project.CURRENT_VERSION,
            "timing-reproduction",
            listOf(Part("A", "source/A.mid", midi = MidiReferences(
                raw = "midi/raw/A.mid", clean = "midi/clean/A.mid", cleanup = cleanup,
                quality = qualityReference, cleanApproval = MidiQualityReportStore.approval(root, qualityReference, quality),
                analysisInput = MidiAnalysisInput.LOFI_FEEL,
                feel = MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, "midi/feel/A-lofi.mid", root.relativize(feelReportPath).toString())
            ))),
            renderFormat = RenderFormat(8_000, 1, 24),
            workflow = criticBypassWorkflow(),
            envelope = ProjectV4Envelope(structureOccurrences = listOf(StructureOccurrence("A-1", "A")))
        )
        val arrangement = DetailedArrangement(sections = listOf(
            DetailedArrangementSection(0, "A-1", "A", SongSectionPurpose.DEVELOPMENT, 0.3, listOf(PianoSourcePlan()), TransitionPlan())
        ))

        StemRenderingMixer(renderer, TestSoundLibrary.root()).render(root, project, arrangement, mapOf("A" to selectedAnalysis))

        val selectedNoteOns = noteOns(MidiSystem.getSequence(lofi.toFile()))
        val renderedNoteOns = noteOns(checkNotNull(renderer.piano))
        println("Task 074 selected source: clean=${sha256(Files.readAllBytes(clean))}, selected-lofi=${sha256(Files.readAllBytes(lofi))}, selectedTempo=${selectedAnalysis.tempoMap.single().bpm}, selectedPpq=${selectedAnalysis.ppq}, selectedNoteOns=$selectedNoteOns, renderedPianoNoteOns=$renderedNoteOns")
        assertNotEquals(sha256(Files.readAllBytes(clean)), sha256(Files.readAllBytes(lofi)))
        assertEquals(480, selectedAnalysis.ppq)
        assertEquals(80.0, selectedAnalysis.tempoMap.single().bpm)
        assertEquals(listOf(0L, 278L, 480L, 758L), renderedNoteOns)
        assertEquals(80.0, tempo(checkNotNull(renderer.piano)))
        assertEquals(selectedNoteOns, renderedNoteOns)
    }

    private fun criticBypassWorkflow(): ProjectWorkflowReferences {
        val inputHash = "c".repeat(64)
        val relative = CriticArtifactPaths.report(inputHash)
        val report = root.resolve(relative)
        Files.createDirectories(requireNotNull(report.parent))
        Files.writeString(report, "critic")
        return ProjectWorkflowReferences(FullSongEnhancementSelection.BYPASS, signatureMotif = null,
            critic = CriticWorkflowReferences(inputHash, WorkflowArtifactReference(relative, sha256(Files.readAllBytes(report)))),
            fullSongEnhancement = FullSongEnhancementReferences(inputHash, null, "d".repeat(64)))
    }

    @Test
    fun `observed independent rounded section starts drift from authoritative lane clock`() {
        val timeline = SongTimeline.create(SongTimelineFixtures.write(root))
        val bStart = timeline.occurrence("B-1").startTick
        val anchors = mapOf(
            "piano" to listOf(0L, 7_680L, bStart),
            "bass" to listOf(0L, 7_680L, bStart),
            "drums" to listOf(0L, 7_680L, bStart),
            "pad" to listOf(0L, 7_680L, bStart),
            "strings" to listOf(0L, 7_680L, bStart),
            "transitions" to listOf(15_360L)
        )
        val absoluteFrames = anchors.mapValues { (_, ticks) -> ticks.map { timeline.framesAt(it, 8_000) } }
        val independentlyRoundedBStart = (timeline.secondsAt(7_680L) * 8_000).roundToLong() * 2L +
            (timeline.secondsAt(18_240L) - timeline.secondsAt(15_360L)).times(8_000).roundToLong()

        println("Task 073 arranged-MIDI drift fixture: canonicalPpq=${timeline.canonicalPpq}, piano/bass/drums/pad/strings B-start=${absoluteFrames.filterKeys { it != "transitions" }.values.map { it.last() }.distinct()}, transition-start=${absoluteFrames.getValue("transitions")}, authoritativeBStart=${timeline.framesAt(bStart, 8_000)}, independentlyRoundedBStart=$independentlyRoundedBStart")
        assertEquals(1, absoluteFrames.filterKeys { it != "transitions" }.values.map { it.last() }.distinct().size)
        assertTrue(absoluteFrames.getValue("transitions").single() < absoluteFrames.getValue("piano").last())
        assertNotEquals(timeline.framesAt(bStart, 8_000), independentlyRoundedBStart)
    }

    private fun writeStraightEighths(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        tempo(track, 120)
        signature(track, 4, 4)
        note(track, 0, 120, 60)
        note(track, 240, 360, 62)
        note(track, 480, 600, 64)
        note(track, 720, 840, 65)
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun noteOns(sequence: Sequence): List<Long> = sequence.tracks.flatMap { track ->
        (0 until track.size()).mapNotNull { index ->
            val event = track[index]
            (event.message as? ShortMessage)?.takeIf { it.command == ShortMessage.NOTE_ON && it.data2 > 0 }?.let { event.tick }
        }
    }.sorted()

    private fun note(track: javax.sound.midi.Track, start: Long, end: Long, pitch: Int) {
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 100), start))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), end))
    }

    private fun tempo(track: javax.sound.midi.Track, bpm: Int) {
        val micros = 60_000_000 / bpm
        track.add(MidiEvent(MetaMessage().apply { setMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }, 0))
    }

    private fun signature(track: javax.sound.midi.Track, numerator: Int, denominator: Int) {
        track.add(MidiEvent(MetaMessage().apply { setMessage(0x58, byteArrayOf(numerator.toByte(), Integer.numberOfTrailingZeros(denominator).toByte(), 24, 8), 4) }, 0))
    }

    private fun tempo(sequence: Sequence): Double {
        val message = sequence.tracks.first().let { track -> (0 until track.size()).map(track::get).first { (it.message as? MetaMessage)?.type == 0x51 }.message as MetaMessage }
        val data = message.data
        val micros = ((data[0].toInt() and 0xff) shl 16) or ((data[1].toInt() and 0xff) shl 8) or (data[2].toInt() and 0xff)
        return 60_000_000.0 / micros
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class CapturingRenderer : InstrumentRenderer {
        var piano: Sequence? = null
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            if (instrument == LogicalInstrument.PIANO) piano = MidiSystem.getSequence(midi.toFile())
            val audio = AudioBuffer(FloatArray(expectedFrames.toInt()), AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), expectedFrames.toDouble() / format.sampleRate)
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, 0.0, "fake", "test", "", "")
        }
    }
}

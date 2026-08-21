package app.melotrail.application

import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MidiAiFixReferences
import app.melotrail.arrangement.MidiAiFixSelection
import app.melotrail.arrangement.MidiAiFixArtifactPaths
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.MidiFeelProfile
import app.melotrail.arrangement.MidiFeelReferences
import app.melotrail.arrangement.MidiFeelReportStore
import app.melotrail.arrangement.MidiLoFiFeelTransformer
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiQualityReporter
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.WorkflowArtifactReference
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PartPreviewApplicationServiceTest {
    @TempDir lateinit var root: Path

    @Test fun `valid wav source is reused without mutation`() = runTest {
        val source = writeWav(root.resolve("parts/A.wav"), 16)
        project("parts/A.wav")
        val before = Files.readAllBytes(source)

        val result = service().resolve(PreviewRequest(root, "A"))

        val resolved = assertIs<PreviewResult.Resolved>(result)
        assertEquals(source, resolved.artifact)
        assertTrue(resolved.reused)
        assertEquals("Original audio", resolved.source?.label)
        assertEquals(sha256(source), resolved.source?.sha256)
        assertTrue(before.contentEquals(Files.readAllBytes(source)))
    }

    @Test fun `mp3 decode is cached invalidated and corrupt cache is replaced`() = runTest {
        val source = root.resolve("parts/A.mp3").also { Files.createDirectories(it.parent); Files.writeString(it, "mp3-one") }
        project("parts/A.mp3")
        val decoder = FakeMp3Decoder()
        val service = service(decoder)

        val first = assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A")))
        val second = assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A")))
        assertFalse(first.reused); assertTrue(second.reused); assertEquals(1, decoder.calls)
        Files.writeString(first.artifact, "corrupt")
        assertFalse(assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A"))).reused)
        Files.writeString(source, "mp3-two")
        assertFalse(assertIs<PreviewResult.Resolved>(service.resolve(PreviewRequest(root, "A"))).reused)
        assertEquals(3, decoder.calls)
    }

    @Test fun `missing clean MIDI analysis is a typed prerequisite`() = runTest {
        val midi = root.resolve("parts/A.mid").also { Files.createDirectories(it.parent); Files.write(it, byteArrayOf()) }
        ProjectStore.write(root, Project(name = "test", renderFormat = RenderFormat(), parts = listOf(app.melotrail.arrangement.Part("A", "parts/A.mid", importPending = true))))

        val result = service().resolve(PreviewRequest(root, "A"))

        assertIs<PreviewResult.Prerequisite>(result)
    }

    @Test fun `audio-origin MIDI comparison renders the requested derived representation rather than source audio`() = runTest {
        val sourceMidi = root.resolve("source/input.mid"); writeMidi(sourceMidi, 60)
        val sourceAudio = writeWav(root.resolve("source/A.wav"), 16)
        val raw = root.resolve("midi/raw/A.mid"); Files.createDirectories(raw.parent); Files.copy(sourceMidi, raw)
        val clean = root.resolve("midi/clean/A.mid"); Files.createDirectories(clean.parent); Files.copy(sourceMidi, clean)
        val approvedReference = MidiAiFixArtifactPaths.approved("A")
        val approved = root.resolve(approvedReference); writeMidi(approved, 64)
        val derived = MidiFeelReportStore.derivedPath(root, "A", MidiFeelProfile.LOFI_80_SWING_V1)
        val feelReport = MidiLoFiFeelTransformer().transform(approved, derived, "A").report
        val reportPath = MidiFeelReportStore.write(root, feelReport)
        val cleanup = MidiCleanupOptions()
        val quality = MidiQualityReporter().report("A", raw, clean, cleanup)
        val qualityPath = MidiQualityReportStore.write(root, quality)
        val feelReferences = MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, root.relativize(derived).toString(), root.relativize(reportPath).toString())
        assertTrue(MidiFeelReportStore.isCurrent(root, "A", approvedReference, feelReferences))
        val originalArtifacts = listOf(sourceMidi, sourceAudio, raw, clean, approved).associateWith(Files::readAllBytes)
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "preview",
            parts = listOf(Part("A", "source/A.wav", midi = MidiReferences(
                raw = "midi/raw/A.mid",
                clean = "midi/clean/A.mid",
                cleanup = cleanup,
                quality = root.relativize(qualityPath).toString(),
                cleanApproval = MidiQualityReportStore.approval(root, root.relativize(qualityPath).toString(), quality),
                aiFixSelection = MidiAiFixSelection.APPROVED,
                aiFix = MidiAiFixReferences(sha256(clean), approved = WorkflowArtifactReference(approvedReference, sha256(approved))),
                analysisInput = MidiAnalysisInput.LOFI_FEEL,
                feel = feelReferences
            ))),
            renderFormat = RenderFormat()
        )
        ProjectStore.write(root, project)
        val resolvedBase = SelectedMidiArtifactResolver().resolve(root, project.copy(parts = project.parts.map { it.copy(midi = it.midi!!.copy(analysisInput = MidiAnalysisInput.CURRENT)) }), "A")
        assertEquals(approvedReference, resolvedBase.projectRelativePath)
        assertTrue(MidiFeelReportStore.isCurrent(root, "A", resolvedBase.projectRelativePath, feelReferences))
        val renderer = CapturingRenderer()

        val result = DefaultPartPreviewApplicationService(renderer).resolve(PreviewRequest(root, "A", midiSource = PreviewMidiSource.LOFI_FEEL))

        val prerequisite = assertIs<PreviewResult.Prerequisite>(result)
        assertTrue(prerequisite.message.contains("renderer"))
        assertEquals(derived, renderer.input)
        originalArtifacts.forEach { (path, bytes) -> assertTrue(bytes.contentEquals(Files.readAllBytes(path)), "$path must remain immutable") }
    }

    private fun project(file: String) = ProjectStore.write(root, Project(name = "test", renderFormat = RenderFormat(), parts = listOf(app.melotrail.arrangement.Part("A", file, importPending = true))))
    private fun service(decoder: PreviewMp3Decoder = FakeMp3Decoder()) = DefaultPartPreviewApplicationService(FakeRenderer(), decoder)

    private class FakeMp3Decoder : PreviewMp3Decoder {
        override val configurationFingerprint = "fake-v1"
        var calls = 0
        override suspend fun decode(source: Path, output: Path) { calls++; writeWav(output, 24) }
    }
    private class FakeRenderer : InstrumentRenderer {
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult = error("not used")
    }

    private class CapturingRenderer : InstrumentRenderer {
        var input: Path? = null
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            input = midi
            error("renderer unavailable")
        }
    }

    private fun writeMidi(path: Path, pitch: Int) {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 96), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 480))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

private fun writeWav(path: Path, bits: Int): Path {
    Files.createDirectories(path.parent)
    val bytes = if (bits == 24) 3 else 2
    val data = ByteArray(bytes * 8)
    Files.newOutputStream(path).use { out ->
        fun i(value: Int) { out.write(value); out.write(value shr 8); out.write(value shr 16); out.write(value shr 24) }
        fun s(value: Int) { out.write(value); out.write(value shr 8) }
        out.write("RIFF".toByteArray()); i(40 + data.size); out.write("WAVEfmt ".toByteArray()); i(18); s(1); s(1); i(44_100); i(44_100 * bytes); s(bytes); s(bits); s(bits); out.write(byteArrayOf(0, 0)); out.write("data".toByteArray()); i(data.size); out.write(data)
    }
    return path
}

package app.melotrail.arrangement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SelectedMidiArtifactResolverTest {
    @TempDir lateinit var root: Path

    @Test
    fun `resolves current Original and Lo-fi identities without changing repaired evidence`() {
        val clean = root.resolve("midi/clean/A.mid"); writeMidi(clean)
        Files.createDirectories(root.resolve("source")); Files.copy(clean, root.resolve("source/A.mid"))
        val lofi = root.resolve("midi/derived/A/lofi-80-swing-v1.mid")
        val report = MidiLoFiFeelTransformer().transform(clean, lofi, "A").report
        val reportPath = MidiFeelReportStore.write(root, report)
        val originalBytes = Files.readAllBytes(clean)
        val original = project(MidiAnalysisInput.REPAIRED, null)
        val lofiProject = project(MidiAnalysisInput.LOFI_FEEL, MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, "midi/derived/A/lofi-80-swing-v1.mid", root.relativize(reportPath).toString()))

        val resolver = SelectedMidiArtifactResolver()
        assertEquals(SelectedMidiArtifactKind.REPAIRED, resolver.resolve(root, original, "A").kind)
        val selected = resolver.resolve(root, lofiProject, "A")
        assertEquals(SelectedMidiArtifactKind.LOFI_FEEL, selected.kind)
        assertEquals(report.outputSha256, selected.sha256)
        assertEquals(80.0, selected.timing.tempoMap.single().bpm)
        assertTrue(Files.readAllBytes(clean).contentEquals(originalBytes))

        // A valid replacement file is still rejected: report fingerprints, not names,
        // bind the selected Lo-fi artifact to the repaired input and derived output.
        writeMidi(lofi)
        assertFailsWith<IllegalArgumentException> { resolver.resolve(root, lofiProject, "A") }
    }

    @Test
    fun `rejects escaped malformed and stale selected references`() {
        val clean = root.resolve("midi/clean/A.mid"); writeMidi(clean)
        Files.createDirectories(root.resolve("source")); Files.copy(clean, root.resolve("source/A.mid"))
        val resolver = SelectedMidiArtifactResolver()
        assertFailsWith<IllegalArgumentException> { resolver.resolve(root, project(MidiAnalysisInput.REPAIRED, null).copy(parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "../outside.mid")))), "A") }
        Files.writeString(clean, "not-midi")
        assertFailsWith<IllegalArgumentException> { resolver.resolve(root, project(MidiAnalysisInput.REPAIRED, null), "A") }
    }

    private fun project(input: MidiAnalysisInput, feel: MidiFeelReferences?) = Project(
        Project.CURRENT_VERSION, "resolver", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid", analysisInput = input, feel = feel))), renderFormat = RenderFormat()
    )

    private fun writeMidi(path: Path) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 240))
        MidiSystem.write(sequence, 1, path.toFile())
    }
}

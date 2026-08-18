package app.melotrail.arrangement

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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SelectedMidiArtifactResolverTest {
    @TempDir lateinit var root: Path

    @Test
    fun `resolves every optional branch without changing cleaned evidence`() {
        val clean = root.resolve("midi/clean/A.mid"); writeMidi(clean)
        Files.createDirectories(root.resolve("source")); Files.copy(clean, root.resolve("source/A.mid"))
        val draft = root.resolve(MidiAiFixArtifactPaths.draft("A")); writeMidi(draft, pitch = 62)
        val approved = root.resolve(MidiAiFixArtifactPaths.approved("A")); Files.createDirectories(approved.parent); Files.copy(draft, approved)
        val ai = MidiAiFixReferences(
            sha256(clean),
            WorkflowArtifactReference(MidiAiFixArtifactPaths.draft("A"), sha256(draft)),
            WorkflowArtifactReference(MidiAiFixArtifactPaths.approved("A"), sha256(approved))
        )
        val lofi = root.resolve("midi/derived/A/lofi-80-swing-v1.mid")
        val report = MidiLoFiFeelTransformer().transform(approved, lofi, "A").report
        val reportPath = MidiFeelReportStore.write(root, report)
        val originalBytes = Files.readAllBytes(clean)
        val skipped = project(MidiAnalysisInput.CURRENT, null, MidiAiFixSelection.SKIP, ai)
        val approvedProject = project(MidiAnalysisInput.CURRENT, null, MidiAiFixSelection.APPROVED, ai)
        val lofiProject = project(
            MidiAnalysisInput.LOFI_FEEL,
            MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, "midi/derived/A/lofi-80-swing-v1.mid", root.relativize(reportPath).toString()),
            MidiAiFixSelection.APPROVED,
            ai
        )

        val resolver = SelectedMidiArtifactResolver()
        assertEquals(SelectedMidiArtifactKind.CLEANED, resolver.resolve(root, skipped, "A").kind)
        val fixed = resolver.resolve(root, approvedProject, "A")
        assertEquals(SelectedMidiArtifactKind.APPROVED_AI_FIX, fixed.kind)
        assertEquals(SelectedMidiBaseKind.APPROVED_AI_FIX, fixed.baseKind)
        val selected = resolver.resolve(root, lofiProject, "A")
        assertEquals(SelectedMidiArtifactKind.LOFI_FEEL, selected.kind)
        assertEquals(SelectedMidiBaseKind.APPROVED_AI_FIX, selected.baseKind)
        assertEquals(report.outputSha256, selected.sha256)
        assertEquals(80.0, selected.timing.tempoMap.single().bpm)
        assertTrue(Files.readAllBytes(clean).contentEquals(originalBytes))

        // A valid replacement file is still rejected: report fingerprints, not names,
        // bind the selected Lo-fi artifact to the cleaned input and derived output.
        writeMidi(lofi)
        assertFailsWith<IllegalArgumentException> { resolver.resolve(root, lofiProject, "A") }

        // An unapproved draft is retained evidence but SKIP still selects cleaned MIDI.
        val draftOnly = project(MidiAnalysisInput.CURRENT, null, MidiAiFixSelection.SKIP, ai.copy(approved = null))
        assertEquals(SelectedMidiArtifactKind.CLEANED, resolver.resolve(root, draftOnly, "A").kind)
    }

    @Test
    fun `rejects escaped malformed and stale selected references`() {
        val clean = root.resolve("midi/clean/A.mid"); writeMidi(clean)
        Files.createDirectories(root.resolve("source")); Files.copy(clean, root.resolve("source/A.mid"))
        val resolver = SelectedMidiArtifactResolver()
        assertFailsWith<IllegalArgumentException> { resolver.resolve(root, project(MidiAnalysisInput.CURRENT, null).copy(parts = listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "../outside.mid")))), "A") }
        Files.writeString(clean, "not-midi")
        assertFailsWith<IllegalArgumentException> { resolver.resolve(root, project(MidiAnalysisInput.CURRENT, null), "A") }
    }

    @Test
    fun `rejects stale or missing approved AI fix instead of falling back to cleaned MIDI`() {
        val clean = root.resolve("midi/clean/A.mid"); writeMidi(clean)
        Files.createDirectories(root.resolve("source")); Files.copy(clean, root.resolve("source/A.mid"))
        val approved = root.resolve(MidiAiFixArtifactPaths.approved("A")); writeMidi(approved, pitch = 64)
        val reference = WorkflowArtifactReference(MidiAiFixArtifactPaths.approved("A"), sha256(approved))
        val selected = project(
            MidiAnalysisInput.CURRENT,
            null,
            MidiAiFixSelection.APPROVED,
            MidiAiFixReferences(sha256(clean), approved = reference)
        )

        writeMidi(approved, pitch = 65)
        assertFailsWith<IllegalArgumentException> { SelectedMidiArtifactResolver().resolve(root, selected, "A") }
        assertFailsWith<IllegalArgumentException> {
            SelectedMidiArtifactResolver().resolve(root, selected.copy(parts = listOf(selected.parts.single().copy(midi = selected.parts.single().midi?.copy(aiFix = null)))), "A")
        }
    }

    private fun project(
        input: MidiAnalysisInput,
        feel: MidiFeelReferences?,
        aiSelection: MidiAiFixSelection = MidiAiFixSelection.SKIP,
        aiFix: MidiAiFixReferences? = null
    ): Project {
        val clean = root.resolve("midi/clean/A.mid")
        val raw = root.resolve("midi/raw/A.mid")
        if (!Files.exists(raw)) {
            Files.createDirectories(raw.parent)
            Files.copy(clean, raw)
        }
        val options = MidiCleanupOptions()
        val quality = MidiQualityReporter().report("A", raw, clean, options)
        val qualityPath = MidiQualityReportStore.write(root, quality)
        val qualityReference = root.relativize(qualityPath).toString()
        return Project(
            Project.CURRENT_VERSION,
            "resolver",
            listOf(Part("A", "source/A.mid", midi = MidiReferences(
                raw = "midi/raw/A.mid", clean = "midi/clean/A.mid", cleanup = options,
                quality = qualityReference, cleanApproval = MidiQualityReportStore.approval(root, qualityReference, quality),
                aiFixSelection = aiSelection, aiFix = aiFix, analysisInput = input, feel = feel
            ))),
            renderFormat = RenderFormat()
        )
    }

    private fun writeMidi(path: Path, pitch: Int = 60) {
        Files.createDirectories(requireNotNull(path.parent))
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, pitch, 90), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, pitch, 0), 240))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

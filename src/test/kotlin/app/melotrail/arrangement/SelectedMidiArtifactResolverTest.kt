package app.melotrail.arrangement

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val input = WorkflowArtifactReference(MidiAiFixArtifactPaths.approved("A"), sha256(approved))
        val context = MidiFeelArtifactPaths.contextSha256(input.sha256, MidiFeelProfile.LOFI_80_SWING_V1)
        val lofi = MidiFeelReportStore.derivedPath(root, "A", context)
        val report = MidiLoFiFeelTransformer().transform(approved, lofi, "A").report
        val reportPath = MidiFeelReportStore.write(root, report)
        val originalBytes = Files.readAllBytes(clean)
        val skipped = project(MidiAnalysisInput.CURRENT, null, MidiAiFixSelection.SKIP, ai)
        val approvedProject = project(MidiAnalysisInput.CURRENT, null, MidiAiFixSelection.APPROVED, ai)
        val lofiProject = project(
            MidiAnalysisInput.LOFI_FEEL,
            MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, input,
                WorkflowArtifactReference(root.relativize(lofi).toString(), sha256(lofi)),
                WorkflowArtifactReference(root.relativize(reportPath).toString(), sha256(reportPath)), context),
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

    @Test
    fun `resolves the complete selected chain and rejects a stale Feel branch`() {
        val clean = root.resolve("midi/clean/A.mid"); writeMidi(clean, 60)
        Files.createDirectories(root.resolve("source")); Files.copy(clean, root.resolve("source/A.mid"))
        val transposed = root.resolve("midi/transposed/A.mid"); writeMidi(transposed, 61)
        val transposedRef = WorkflowArtifactReference("midi/transposed/A.mid", sha256(transposed))

        val correctedPath = root.resolve(TechnicalCorrectionArtifactPaths.output("A", transposedRef.sha256)); writeMidi(correctedPath, 62)
        val correctedRef = WorkflowArtifactReference(root.relativize(correctedPath).toString(), sha256(correctedPath))
        val correctionReport = root.resolve(TechnicalCorrectionArtifactPaths.report("A", transposedRef.sha256)).also {
            Files.createDirectories(requireNotNull(it.parent)); Files.writeString(it, "reviewed")
        }
        val correction = TechnicalCorrectionReferences(transposedRef, correctedRef,
            WorkflowArtifactReference(root.relativize(correctionReport).toString(), sha256(correctionReport)), "c".repeat(64))

        val approvedPath = root.resolve(MidiAiFixArtifactPaths.approved("A")); writeMidi(approvedPath, 63)
        val approvedRef = WorkflowArtifactReference(MidiAiFixArtifactPaths.approved("A"), sha256(approvedPath))
        val aiFix = MidiAiFixReferences(correctedRef.sha256, approved = approvedRef)

        val enhancementContext = "d".repeat(64)
        val enhancedPath = root.resolve(EnhancementArtifactPaths.output("A", enhancementContext)); writeMidi(enhancedPath, 64)
        val enhancedRef = WorkflowArtifactReference(EnhancementArtifactPaths.output("A", enhancementContext), sha256(enhancedPath))
        val enhancementReport = EnhancementEditReport(
            subjectHash = "e".repeat(64), inputSha256 = approvedRef.sha256, outputSha256 = enhancedRef.sha256,
            contextSha256 = enhancementContext, intensity = EnhancementIntensity.SUBTLE, processorId = "fixture", processorVersion = "1",
            placeholder = true, appliedEdits = listOf(EnhancementEdit(EnhancementEditKind.VELOCITY, "m-" + "f".repeat(64), 1)), message = "bounded fixture edit"
        )
        val enhancementReportPath = root.resolve(EnhancementArtifactPaths.report("A", enhancementContext)).also {
            Files.createDirectories(requireNotNull(it.parent)); Files.writeString(it, Json { encodeDefaults = true }.encodeToString(enhancementReport))
        }
        val enhancement = EnhancementReferences(EnhancementIntensity.SUBTLE, approvedRef, enhancedRef,
            WorkflowArtifactReference(EnhancementArtifactPaths.report("A", enhancementContext), sha256(enhancementReportPath)), enhancementContext)

        val feelContext = MidiFeelArtifactPaths.contextSha256(enhancedRef.sha256, MidiFeelProfile.LOFI_80_SWING_V1)
        val feelPath = MidiFeelReportStore.derivedPath(root, "A", feelContext)
        val feelReport = MidiLoFiFeelTransformer().transform(enhancedPath, feelPath, "A").report
        val feelReportPath = MidiFeelReportStore.write(root, feelReport)
        val feel = MidiFeelReferences(MidiFeelProfile.LOFI_80_SWING_V1, enhancedRef,
            WorkflowArtifactReference(root.relativize(feelPath).toString(), sha256(feelPath)),
            WorkflowArtifactReference(root.relativize(feelReportPath).toString(), sha256(feelReportPath)), feelContext)

        val seed = project(MidiAnalysisInput.CURRENT, null)
        val chainedMidi = requireNotNull(seed.parts.single().midi).copy(
            transposed = transposedRef.file,
            technicalCorrectionSelection = TechnicalCorrectionSelection.CORRECTED,
            technicalCorrection = correction,
            aiFixSelection = MidiAiFixSelection.APPROVED,
            aiFix = aiFix,
            enhancementSelection = EnhancementSelection.ENHANCED,
            enhancement = enhancement,
            analysisInput = MidiAnalysisInput.LOFI_FEEL,
            feel = feel
        )
        val chained = seed.copy(parts = listOf(seed.parts.single().copy(midi = chainedMidi)))
        val resolver = SelectedMidiArtifactResolver()

        assertEquals(SelectedMidiArtifactKind.ENHANCED, resolver.resolveBeforeFeel(root, chained, "A").kind)
        assertEquals(SelectedMidiArtifactKind.LOFI_FEEL, resolver.resolve(root, chained, "A").kind)
        assertEquals(feel.derived.sha256, resolver.resolve(root, chained, "A").sha256)
        assertFailsWith<IllegalArgumentException> {
            resolver.resolve(root, chained.copy(parts = listOf(chained.parts.single().copy(midi = chainedMidi.copy(aiFixSelection = MidiAiFixSelection.SKIP)))), "A")
        }

        val noOpOutput = root.resolve(EnhancementArtifactPaths.output("A", "a".repeat(64))).also { Files.createDirectories(requireNotNull(it.parent)); Files.copy(approvedPath, it) }
        val noOpRef = WorkflowArtifactReference(EnhancementArtifactPaths.output("A", "a".repeat(64)), sha256(noOpOutput))
        val noOpReport = EnhancementEditReport(
            subjectHash = "b".repeat(64), inputSha256 = approvedRef.sha256, outputSha256 = noOpRef.sha256,
            contextSha256 = "a".repeat(64), intensity = EnhancementIntensity.SUBTLE, processorId = "fixture", processorVersion = "1",
            placeholder = true, message = "no musical edit"
        )
        val noOpReportPath = root.resolve(EnhancementArtifactPaths.report("A", "a".repeat(64))).also {
            Files.createDirectories(requireNotNull(it.parent)); Files.writeString(it, Json { encodeDefaults = true }.encodeToString(noOpReport))
        }
        val noOp = EnhancementReferences(EnhancementIntensity.SUBTLE, approvedRef, noOpRef,
            WorkflowArtifactReference(EnhancementArtifactPaths.report("A", "a".repeat(64)), sha256(noOpReportPath)), "a".repeat(64))
        val noOpProject = chained.copy(parts = listOf(chained.parts.single().copy(midi = chainedMidi.copy(
            enhancementSelection = EnhancementSelection.NO_OP, enhancement = noOp, analysisInput = MidiAnalysisInput.CURRENT, feel = null
        ))))
        assertEquals(SelectedMidiArtifactKind.NO_OP, resolver.resolve(root, noOpProject, "A").kind)
        assertEquals(approvedRef.sha256, resolver.resolve(root, noOpProject, "A").sha256)
        assertFailsWith<IllegalArgumentException> {
            resolver.resolve(root, noOpProject.copy(parts = listOf(noOpProject.parts.single().copy(midi = noOpProject.parts.single().midi!!.copy(enhancementSelection = EnhancementSelection.ENHANCED)))), "A")
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

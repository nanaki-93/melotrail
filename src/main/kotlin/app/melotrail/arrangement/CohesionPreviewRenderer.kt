package app.melotrail.arrangement

import app.melotrail.application.OperationProgress
import app.melotrail.application.ProgressSink
import app.melotrail.application.DefaultSourceSongCriticApplicationService
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import kotlin.math.roundToLong

/** Renders one gain-matched, whole-song A/B pair before aggregate Cohesion approval. */
class FullSongCohesionPreviewRenderer(
    private val renderer: InstrumentRenderer,
    private val libraryRoot: Path,
    private val mixer: DeterministicStemMixer = DeterministicStemMixer()
) {
    suspend fun render(root: Path, input: EnsembleCohesionInput, progress: ProgressSink = ProgressSink.None): CohesionPreviewReferences {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        val workflow = requireNotNull(project.workflow.cohesion).also {
            require(it.inputSha256 == input.inputHash) { "Cohesion preview input is stale" }
        }
        val format = requireNotNull(project.renderFormat)
        val approvedMelody = DefaultSourceSongCriticApplicationService().requireApprovedMelody(root)
        require(input.occurrences.all { it.evidence.sourceHash == approvedMelody.connectedMidi.sha256 }) {
            "Cohesion preview input is not bound to the approved connected full melody. Regenerate Cohesion."
        }
        val canonicalPiano = root.resolve(approvedMelody.connectedMidi.file).normalize()
        require(canonicalPiano.startsWith(root) && Files.isRegularFile(canonicalPiano) && sha256(canonicalPiano) == approvedMelody.connectedMidi.sha256) {
            "Approved connected full melody is missing or stale. Rerun Source Song Critic and approve the current melody."
        }
        val expectedFrames = (MidiSystem.getSequence(canonicalPiano.toFile()).microsecondLength / 1_000_000.0 * format.sampleRate).roundToLong()
        require(expectedFrames > 0) { "Cohesion preview timeline is empty" }

        val totalStems = (1 + input.generatedRoles.size) * 2
        val baseline = renderVariant(root, project, input, canonicalPiano, enhanced = false, format, expectedFrames, progress, 0, totalStems)
        val enhanced = renderVariant(root, project, input, canonicalPiano, enhanced = true, format, expectedFrames, progress, 1 + input.generatedRoles.size, totalStems)
        return CohesionPreviewReferences(
            WorkflowArtifactReference(root.relativize(baseline).toString().replace('\\', '/'), sha256(baseline)),
            WorkflowArtifactReference(root.relativize(enhanced).toString().replace('\\', '/'), sha256(enhanced))
        )
    }

    private suspend fun renderVariant(
        root: Path,
        project: Project,
        input: EnsembleCohesionInput,
        piano: Path,
        enhanced: Boolean,
        format: RenderFormat,
        expectedFrames: Long,
        progress: ProgressSink,
        completedStems: Int,
        totalStems: Int
    ): Path {
        val registry = InstrumentRegistryLoader(libraryRoot).load()
        val roles = listOf("piano") + input.generatedRoles.map(GeneratedRoleEvidence::role)
        val stems = mutableListOf<Path>()
        try {
            val tracks = roles.mapIndexed { index, role ->
                val logical = LogicalInstrument.parse(role)
                val midi = when {
                    role == "piano" -> piano
                    !enhanced -> root.resolve("midi/generated/$role.mid")
                    else -> root.resolve(requireNotNull(project.workflow.cohesion?.roles?.singleOrNull { it.role == role }).result.file)
                }
                require(Files.isRegularFile(midi)) { "Cohesion preview is missing $role MIDI" }
                progress.report(OperationProgress(
                    operation = "cohesion", stageIndex = 5, stageCount = 5,
                    message = "Rendering ${if (enhanced) "enhanced" else "baseline"} Cohesion preview: $role (${completedStems + index + 1}/$totalStems)"
                ))
                val stem = root.resolve("cohesion/runs/${input.inputHash}/preview/.${if (enhanced) "enhanced" else "baseline"}-$role-${UUID.randomUUID()}.wav")
                Files.createDirectories(requireNotNull(stem.parent))
                renderer.render(midi, registry.resolveApprovedRole(project, logical).id, stem, format, expectedFrames)
                stems.add(stem)
                MixTrack(
                    role,
                    WAVDecoder(ErrorReporter.NoOp).decode(stem),
                    gainDb = GAINS.getValue(role),
                    generated = role != "piano"
                )
            }
            progress.report(OperationProgress(
                operation = "cohesion", stageIndex = 5, stageCount = 5,
                message = "Mixing ${if (enhanced) "enhanced" else "baseline"} Cohesion preview"
            ))
            val mixed = mixer.mix(tracks, MixSettings(requiredFormat = format, peakCeiling = 0.95))
            val target = root.resolve(if (enhanced) CohesionRoleArtifactPaths.enhancedPreview(input.inputHash) else CohesionRoleArtifactPaths.baselinePreview(input.inputHash))
            return mixer.writeWav(mixed, target)
        } finally {
            stems.forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        val GAINS = mapOf("piano" to 0.0, "bass" to -6.0, "drums" to -8.0, "pad" to -10.0, "strings" to -10.0)
    }
}

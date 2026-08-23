package app.melotrail.arrangement

import app.melotrail.application.OperationProgress
import app.melotrail.application.ProgressSink
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
        val expectedFrames = input.occurrences.sumOf { occurrence ->
            val part = project.parts.single { it.id == occurrence.evidence.partId }
            val analysis = Json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(requireNotNull(part.analysis).file)))
            analysis.durationSeconds
        }.times(format.sampleRate).roundToLong()
        require(expectedFrames > 0) { "Cohesion preview timeline is empty" }

        val baselinePiano = assemblePiano(root, input, enhanced = false)
        val enhancedPiano = assemblePiano(root, input, enhanced = true)
        return try {
            val totalStems = (1 + input.generatedRoles.size) * 2
            val baseline = renderVariant(root, project, input, baselinePiano, enhanced = false, format, expectedFrames, progress, 0, totalStems)
            val enhanced = renderVariant(root, project, input, enhancedPiano, enhanced = true, format, expectedFrames, progress, 1 + input.generatedRoles.size, totalStems)
            CohesionPreviewReferences(
                WorkflowArtifactReference(root.relativize(baseline).toString().replace('\\', '/'), sha256(baseline)),
                WorkflowArtifactReference(root.relativize(enhanced).toString().replace('\\', '/'), sha256(enhanced))
            )
        } finally {
            Files.deleteIfExists(baselinePiano)
            Files.deleteIfExists(enhancedPiano)
        }
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

    private fun assemblePiano(root: Path, input: EnsembleCohesionInput, enhanced: Boolean): Path {
        val workflow = requireNotNull(ProjectStore.read(root).workflow.cohesion)
        val ppq = input.occurrences.first().evidence.ppq
        val sequence = Sequence(Sequence.PPQ, ppq)
        var offset = 0L
        input.occurrences.forEach { occurrence ->
            val source = if (!enhanced) {
                SelectedMidiArtifactResolver().resolve(root, ProjectStore.read(root), occurrence.evidence.partId).path
            } else {
                root.resolve(requireNotNull(workflow.occurrences.singleOrNull { it.instanceId == occurrence.instanceId }).result.file)
            }
            val part = MidiSystem.getSequence(source.toFile())
            require(part.divisionType == Sequence.PPQ && part.resolution == ppq) { "Cohesion preview occurrence PPQ is incompatible" }
            part.tracks.forEach { sourceTrack ->
                val target = sequence.createTrack()
                (0 until sourceTrack.size()).map(sourceTrack::get)
                    .filterNot { (it.message as? MetaMessage)?.type == 0x2F }
                    .filter { it.tick <= occurrence.evidence.durationTicks }
                    .forEach { target.add(MidiEvent(it.message.copy(), offset + it.tick)) }
            }
            offset += occurrence.evidence.durationTicks
        }
        sequence.createTrack().add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), offset))
        val path = root.resolve("cohesion/runs/${input.inputHash}/preview/.${if (enhanced) "enhanced" else "baseline"}-piano-${UUID.randomUUID()}.mid")
        Files.createDirectories(requireNotNull(path.parent))
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0) { "Could not assemble Cohesion preview piano" }
        return path
    }

    private fun MidiMessage.copy(): MidiMessage = clone() as MidiMessage

    private companion object {
        val GAINS = mapOf("piano" to 0.0, "bass" to -6.0, "drums" to -8.0, "pad" to -10.0, "strings" to -10.0)
    }
}

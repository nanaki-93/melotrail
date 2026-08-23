package app.melotrail.application

import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.ImportEvidence
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.TranscriptionCleanupProfile
import app.melotrail.arrangement.MidiQualityReport
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiQualityReporter
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.MidiNormalizationPolicy
import app.melotrail.arrangement.MidiNormalizationReport
import app.melotrail.arrangement.MidiNormalizer
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiProjectKeyTransposer
import app.melotrail.arrangement.MidiTranspositionReport
import app.melotrail.arrangement.MidiTranspositionReportStore
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SourceKeyEvidence
import app.melotrail.arrangement.toMusicalKeyOrNull
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageSubject
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionReport
import app.melotrail.preparation.InputInspectionRequest
import app.melotrail.preparation.InputInspectionResult
import app.melotrail.preparation.InspectionSourceIdentity
import app.melotrail.profile.BundledCompositionProfileCatalog
import app.melotrail.profile.CompositionProfileCatalog
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The small, worker-backed portion of the Task 013 import graph.  It receives
 * only project-relative stage inputs and publishes every generated artifact
 * through [StageRunner]; worker paths never enter project metadata.
 */
class AutomaticImportProcessors(
    private val inspection: InputInspectionBoundary,
    private val midiPreparation: MidiPreparationService,
    private val qualityReporter: MidiQualityReporter = MidiQualityReporter(),
    private val defaultCleanup: MidiCleanupOptions = MidiCleanupOptions(),
    private val compositionProfiles: CompositionProfileCatalog = BundledCompositionProfileCatalog.load(),
    private val normalizer: MidiNormalizer = MidiNormalizer(),
    private val transposer: MidiProjectKeyTransposer = MidiProjectKeyTransposer()
) {
    fun registry(): StageProcessorRegistry = StageProcessorRegistry(listOf(extracted, cleaned, normalized, transposed))

    private val extracted = object : StageProcessor {
        override val definition = StageDefinition(
            stage = StageId.EXTRACTED,
            subjectKind = StageSubjectKind.PART,
            dependencies = setOf(StageId.SOURCE)
        )

        override suspend fun process(request: StageProcessingRequest): StageProcessorResult {
            val part = part(request)
            val source = source(request, part.file)
            val extension = part.file.substringAfterLast('.', "").lowercase()
            val report = inspect(request, part.id, part.file, source, expectedContainer(extension))
            val raw = request.temporaryRoot.resolve("raw.mid")
            if (extension in MIDI_EXTENSIONS) Files.copy(source, raw) else midiPreparation.transcribe(source, raw)
            requireMidi(raw, "Extraction")
            return StageProcessorResult(
                outputs = listOf(TemporaryStageArtifact(raw, "midi/raw/${part.id}.mid")),
                reports = listOf(TemporaryStageArtifact(writeJson(request.temporaryRoot.resolve("inspection.json"), report), "prepared/${part.id}/report.json"))
            )
        }

        override fun onPublished(request: StageProcessingRequest, outputs: List<ArtifactRef>, reports: List<ArtifactRef>) {
            val raw = outputs.singleOrNull() ?: error("Extraction must publish exactly one raw MIDI artifact")
            val project = ProjectStore.read(request.root)
            val part = project.parts.singleOrNull { it.id == partId(request) } ?: error("Imported part disappeared before extraction completed")
            require(part.importPending && part.midi == null && part.importEvidence == null) { "Extraction target is no longer pending" }
            val source = request.root.resolve(part.file)
            require(Files.isRegularFile(source)) { "Imported source is missing" }
            val updated = project.copy(
                parts = project.parts.map {
                    if (it.id != part.id) it else it.copy(
                        midi = MidiReferences(raw = raw.path),
                        importEvidence = ImportEvidence(sha256(source), raw.sha256),
                        importPending = false
                    )
                }
            )
            ProjectStore.write(request.root, updated)
        }
    }

    private val cleaned = object : StageProcessor {
        override val definition = StageDefinition(
            stage = StageId.CLEANED,
            subjectKind = StageSubjectKind.PART,
            dependencies = setOf(StageId.EXTRACTED)
        )

        override suspend fun process(request: StageProcessingRequest): StageProcessorResult {
            val part = part(request)
            val raw = requireNotNull(part.midi?.raw) { "Extracted MIDI is required before cleanup" }
            val rawPath = source(request, raw)
            val clean = request.temporaryRoot.resolve("clean.mid")
            val cleanup = if (part.file.substringAfterLast('.', "").lowercase() in MIDI_EXTENSIONS) defaultCleanup
            else TranscriptionCleanupProfile.DEFAULT.toMidiCleanupOptions()
            midiPreparation.clean(rawPath, clean, cleanup)
            requireMidi(clean, "MIDI cleanup")
            val report = qualityReporter.report(part.id, rawPath, clean, cleanup)
            return StageProcessorResult(
                outputs = listOf(TemporaryStageArtifact(clean, "midi/clean/${part.id}-${request.runId}.mid")),
                reports = listOf(TemporaryStageArtifact(writeJson(request.temporaryRoot.resolve("quality.json"), report), "midi/quality/${part.id}-${request.runId}.json"))
            )
        }

        override fun onPublished(request: StageProcessingRequest, outputs: List<ArtifactRef>, reports: List<ArtifactRef>) {
            val clean = outputs.singleOrNull() ?: error("Cleanup must publish exactly one MIDI artifact")
            val quality = reports.singleOrNull() ?: error("Cleanup must publish exactly one quality report")
            val project = ProjectStore.read(request.root)
            val part = project.parts.singleOrNull { it.id == partId(request) } ?: error("Imported part disappeared before cleanup completed")
            val midi = requireNotNull(part.midi)
            val raw = requireNotNull(midi.raw)
            val report = readQuality(request.root.resolve(quality.path))
            require(report.partId == part.id && report.raw.sha256 == sha256(request.root.resolve(raw)) && report.clean.sha256 == clean.sha256) {
                "Cleanup quality report does not match the published MIDI"
            }
            val approval = if (report.approvalRequired) null else MidiQualityReportStore.approval(request.root, quality.path, report)
            val updatedMidi = midi.copy(
                clean = clean.path,
                cleanup = report.cleanup,
                quality = quality.path,
                normalized = null,
                normalization = null,
                transposed = null,
                transposition = null,
                cleanApproval = approval,
                analysisInput = MidiAnalysisInput.CURRENT
            )
            ProjectStore.write(request.root, project.copy(parts = project.parts.map {
                if (it.id == part.id) it.copy(midi = updatedMidi) else it
            }))
        }
    }

    private val normalized = object : StageProcessor {
        override val definition = StageDefinition(
            stage = StageId.NORMALIZED,
            subjectKind = StageSubjectKind.PART,
            dependencies = setOf(StageId.CLEANED)
        )

        override suspend fun process(request: StageProcessingRequest): StageProcessorResult {
            val project = ProjectStore.read(request.root)
            val part = project.parts.singleOrNull { it.id == partId(request) } ?: throw IllegalArgumentException("Stage part is not registered")
            val clean = requireNotNull(part.midi?.clean) { "Clean MIDI is required before normalization" }
            val input = source(request, clean)
            val output = request.temporaryRoot.resolve("normalized.mid")
            val config = MidiNormalizationPolicy.resolve(project, compositionProfiles)
            val report = normalizer.normalize(part.id, input, output, config)
            return StageProcessorResult(
                outputs = listOf(TemporaryStageArtifact(output, "midi/normalized/${part.id}-${request.runId}.mid")),
                reports = listOf(TemporaryStageArtifact(writeJson(request.temporaryRoot.resolve("normalization.json"), report), "midi/normalization/${part.id}-${request.runId}.json"))
            )
        }

        override fun onPublished(request: StageProcessingRequest, outputs: List<ArtifactRef>, reports: List<ArtifactRef>) {
            val output = outputs.singleOrNull() ?: error("Normalization must publish exactly one MIDI artifact")
            val reportRef = reports.singleOrNull() ?: error("Normalization must publish exactly one report")
            val project = ProjectStore.read(request.root)
            val part = project.parts.singleOrNull { it.id == partId(request) } ?: error("Imported part disappeared before normalization completed")
            val midi = requireNotNull(part.midi)
            val clean = requireNotNull(midi.clean)
            val report = readNormalization(request.root.resolve(reportRef.path))
            require(report.partId == part.id && report.input.sha256 == sha256(request.root.resolve(clean)) && report.output.sha256 == output.sha256) {
                "Normalization report does not match published MIDI"
            }
            val detected = MidiPartAnalyzer().analyze(request.root.resolve(output.path), part.id).key
            val detectedKey = detected?.toMusicalKeyOrNull()
            val evidence = SourceKeyEvidence(
                detectedKey = detectedKey,
                confidence = detected?.confidence ?: 0.0,
                algorithmVersion = detectedKey?.let { SourceKeyEvidence.ALGORITHM_VERSION },
                inputSha256 = detectedKey?.let { output.sha256 }
            )
            ProjectStore.write(request.root, project.copy(parts = project.parts.map {
                if (it.id == part.id) it.copy(sourceKeyEvidence = evidence, midi = midi.copy(
                    normalized = output.path, normalization = reportRef.path, transposed = null, transposition = null
                )) else it
            }))
        }
    }

    private val transposed = object : StageProcessor {
        override val definition = StageDefinition(StageId.TRANSPOSED, StageSubjectKind.PART, dependencies = setOf(StageId.NORMALIZED))

        override suspend fun process(request: StageProcessingRequest): StageProcessorResult {
            val project = ProjectStore.read(request.root)
            val part = project.parts.singleOrNull { it.id == partId(request) } ?: throw IllegalArgumentException("Stage part is not registered")
            val midi = requireNotNull(part.midi) { "Normalized MIDI is required before transposition" }
            val normalized = requireNotNull(midi.normalized) { "Normalize MIDI before transposition" }
            val sourceKey = part.sourceKeyEvidence?.effectiveKey
                ?: throw InputRequiredException("Confirm the detected source key before transposition.")
            val projectKey = project.envelope.compositionSettings?.takeIf { it.complete }?.key
                ?: throw InputRequiredException("Complete project Setup before transposition.")
            val output = request.temporaryRoot.resolve("transposed.mid")
            val report = transposer.transpose(part.id, source(request, normalized), output, sourceKey, projectKey)
            return StageProcessorResult(
                outputs = listOf(TemporaryStageArtifact(output, "midi/transposed/${part.id}-${request.runId}.mid")),
                reports = listOf(TemporaryStageArtifact(
                    MidiTranspositionReportStore.write(request.temporaryRoot.resolve("transposition.json"), report),
                    "midi/transposition/${part.id}-${request.runId}.json"
                ))
            )
        }

        override fun onPublished(request: StageProcessingRequest, outputs: List<ArtifactRef>, reports: List<ArtifactRef>) {
            val output = outputs.singleOrNull() ?: error("Transposition must publish exactly one MIDI artifact")
            val reportRef = reports.singleOrNull() ?: error("Transposition must publish exactly one report")
            val project = ProjectStore.read(request.root)
            val part = project.parts.singleOrNull { it.id == partId(request) } ?: error("Imported part disappeared before transposition completed")
            val midi = requireNotNull(part.midi)
            val normalized = requireNotNull(midi.normalized)
            val sourceKey = requireNotNull(part.sourceKeyEvidence?.effectiveKey)
            val projectKey = requireNotNull(project.envelope.compositionSettings?.takeIf { it.complete }?.key)
            require(MidiTranspositionReportStore.isCurrent(request.root, part.id, request.root.resolve(normalized), request.root.resolve(output.path), sourceKey, projectKey, reportRef.path)) {
                "Transposition report does not match the published MIDI"
            }
            ProjectStore.write(request.root, project.copy(parts = project.parts.map {
                if (it.id == part.id) it.copy(analysis = null, midi = midi.copy(transposed = output.path, transposition = reportRef.path)) else it
            }, workflow = project.workflow.markCurrent(app.melotrail.arrangement.WorkflowArtifact.TRANSPOSED_MIDI)))
        }
    }

    private suspend fun inspect(
        request: StageProcessingRequest,
        id: String,
        relative: String,
        source: Path,
        expected: InputContainer
    ): InputInspectionReport {
        val identity = InspectionSourceIdentity(relative, sha256(source))
        val result = inspection.inspect(InputInspectionRequest(request.root, id, identity).also { it.requireValid() })
        val report = (result as? InputInspectionResult.Inspected)?.report ?: run {
            val rejected = result as? InputInspectionResult.Rejected
            throw InputRequiredException(rejected?.error?.message ?: "Input inspection did not return a report")
        }
        require(report.partId == id && report.source == identity && report.detectedInput.container == expected) {
            "Input inspection did not match the preserved source"
        }
        report.requireValid()
        return report
    }

    private fun part(request: StageProcessingRequest) = ProjectStore.read(request.root).parts.singleOrNull { it.id == partId(request) }
        ?: throw IllegalArgumentException("Stage part is not registered")

    private fun partId(request: StageProcessingRequest): String = (request.subject as? StageSubject.Part)?.partId
        ?: throw IllegalArgumentException("Automatic import stages require a part subject")

    private fun source(request: StageProcessingRequest, relative: String): Path {
        val root = request.root.toAbsolutePath().normalize()
        val path = root.resolve(relative).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())) { "Stage input is not project-local" }
        return path
    }

    private fun expectedContainer(extension: String): InputContainer = when (extension) {
        in MIDI_EXTENSIONS -> InputContainer.MIDI
        "wav", "wave" -> InputContainer.RIFF_WAVE
        "mp3" -> InputContainer.MPEG_AUDIO
        else -> throw IllegalArgumentException("Unsupported import extension")
    }

    private fun requireMidi(path: Path, label: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 14L) { "$label did not produce MIDI" }
        Files.newInputStream(path).use { require(it.readNBytes(4).decodeToString() == "MThd") { "$label did not produce MIDI" } }
        MidiSystem.getSequence(path.toFile())
    }

    private fun writeJson(path: Path, value: Any): Path {
        val text = when (value) {
            is InputInspectionReport -> JSON.encodeToString(value)
            is MidiQualityReport -> JSON.encodeToString(value)
            is MidiNormalizationReport -> JSON.encodeToString(value)
            else -> error("Unsupported stage report")
        }
        Files.writeString(path, text)
        return path
    }

    private fun readQuality(path: Path): MidiQualityReport = JSON.decodeFromString(MidiQualityReport.serializer(), Files.readString(path))
    private fun readNormalization(path: Path): MidiNormalizationReport = JSON.decodeFromString(MidiNormalizationReport.serializer(), Files.readString(path)).also(MidiNormalizationReport::requireValid)

    private companion object {
        val JSON = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        val MIDI_EXTENSIONS = setOf("mid", "midi")
        fun sha256(path: Path): String = app.melotrail.arrangement.sha256(path)
    }
}

package app.melotrail.application

import app.melotrail.arrangement.AnalysisKind
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiAnalysisStore
import app.melotrail.arrangement.MidiCleanupOptions
import app.melotrail.arrangement.MidiPartAnalyzer
import app.melotrail.arrangement.MidiQualityRecommendation
import app.melotrail.arrangement.MidiQualityReport
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.MidiQualityReporter
import app.melotrail.arrangement.MidiQualityWarning
import app.melotrail.arrangement.MidiReferences
import app.melotrail.arrangement.MidiAnalysisInput
import app.melotrail.arrangement.MidiFeelProfile
import app.melotrail.arrangement.MidiFeelReferences
import app.melotrail.arrangement.MidiFeelReport
import app.melotrail.arrangement.MidiFeelReportStore
import app.melotrail.arrangement.MidiLoFiFeelTransformer
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.PartAnalysis
import app.melotrail.arrangement.PartAnalysisReference
import app.melotrail.arrangement.PartAnalysisStore
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionError
import app.melotrail.preparation.InputInspectionErrorCode
import app.melotrail.preparation.InputInspectionPaths
import app.melotrail.preparation.InputInspectionReportStore
import app.melotrail.preparation.InputInspectionRequest
import app.melotrail.preparation.InputInspectionResult
import app.melotrail.preparation.InspectionSourceIdentity
import app.melotrail.preparation.PreparationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** UI- and CLI-neutral boundary for the local, file-backed arranger project. */
interface ProjectApplicationService {
    fun open(root: Path): ProjectSnapshot
    /** Optional explicit migration boundary; older adapters remain read-only. */
    fun migrateV2(root: Path): ProjectSnapshot = throw UnsupportedOperationException("This project service does not support schema-v2 migration.")
    fun create(request: CreateProjectRequest): ProjectSnapshot
    suspend fun importPart(request: ImportPartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    suspend fun retryMidiCleanup(request: RetryMidiCleanupRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    fun approveMidiRepair(root: Path, partId: String): ProjectSnapshot
    fun selectMidiFeel(request: SelectMidiFeelRequest): ProjectSnapshot
    suspend fun inspectPart(request: InspectPartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    suspend fun analyzePart(request: AnalyzePartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    fun updatePart(request: UpdatePartRoleRequest): ProjectSnapshot
    fun saveStructure(request: SaveStructureRequest): ProjectSnapshot
}

data class CreateProjectRequest(
    val root: Path,
    val name: String? = null,
    val renderFormat: RenderFormat = RenderFormat()
)

data class ImportPartRequest(
    val root: Path,
    val id: String,
    val source: Path,
    val role: String = "",
    val transcribe: Boolean = false,
    val cleanup: MidiCleanupOptions = MidiCleanupOptions()
)

/** A retry may choose only an already validated named cleanup profile. */
data class RetryMidiCleanupRequest(
    val root: Path,
    val partId: String,
    val cleanup: MidiCleanupOptions
)

/** Fixed named profile only. This is deliberately not a tempo/swing control surface. */
data class SelectMidiFeelRequest(val root: Path, val partId: String, val input: MidiAnalysisInput)

data class AnalyzePartRequest(val root: Path, val partId: String)
data class InspectPartRequest(val root: Path, val partId: String)
data class UpdatePartRoleRequest(val root: Path, val partId: String, val role: String)
data class SaveStructureRequest(val root: Path, val partIds: List<String>)

data class OperationProgress(
    val operation: String,
    val stageIndex: Int,
    val stageCount: Int,
    val message: String,
    val artifact: Path? = null
)

fun interface ProgressSink {
    fun report(progress: OperationProgress)

    data object None : ProgressSink {
        override fun report(progress: OperationProgress) = Unit
    }
}

/** Worker boundary needed by the MIDI-first import workflow. */
interface MidiPreparationService {
    suspend fun transcribe(input: Path, output: Path)
    suspend fun clean(input: Path, output: Path)
    /** Kept as a default bridge for existing worker fakes while cleanup options become persisted provenance. */
    suspend fun clean(input: Path, output: Path, options: MidiCleanupOptions) = clean(input, output)
}

/** Worker boundary retained for readable legacy v1 projects. */
fun interface LegacyPartAnalysisService {
    suspend fun analyze(source: Path): PartAnalysis
}

data class ProjectSnapshot(
    val root: Path,
    val version: Int,
    val name: String,
    val renderFormat: RenderFormat?,
    val parts: List<PartSummary>,
    val structure: List<StructureSectionSummary>,
    val readiness: ProjectReadiness
)

data class PartSummary(
    val id: String,
    val role: String,
    val sourceFile: String,
    val sourceName: String,
    val sourceType: PartSourceType,
    val analysis: PartAnalysisSummary?,
    val preparation: PartPreparationSummary = PartPreparationSummary(
        sourcePreserved = false,
        inspected = false,
        preparedAudio = false,
        rawMidi = false,
        cleanMidi = false,
        analyzed = false,
        ready = false,
        warnings = emptyList()
    )
)

/** Truthful, artifact-derived input state for one canonical project part. */
data class PartPreparationSummary(
    val sourcePreserved: Boolean,
    val inspected: Boolean,
    val preparedAudio: Boolean,
    val rawMidi: Boolean,
    val cleanMidi: Boolean,
    val analyzed: Boolean,
    val ready: Boolean,
    val warnings: List<String>,
    val midiQuality: MidiQualitySummary = MidiQualitySummary.legacyUnknown(),
    val midiFeel: MidiFeelSummary = MidiFeelSummary()
)

data class MidiFeelSummary(
    val selected: MidiAnalysisInput = MidiAnalysisInput.REPAIRED,
    val available: Boolean = false,
    val report: MidiFeelReport? = null
)

enum class MidiQualityStatus { CURRENT, APPROVAL_REQUIRED, LEGACY_UNKNOWN, STALE_OR_INVALID }

data class MidiQualitySummary(
    val status: MidiQualityStatus,
    val cleanup: MidiCleanupOptions? = null,
    val warnings: List<MidiQualityWarning> = emptyList(),
    val recommendations: List<MidiQualityRecommendation> = emptyList(),
    /** Current canonical report only; it contains no external source path. */
    val report: MidiQualityReport? = null
) {
    companion object {
        fun legacyUnknown() = MidiQualitySummary(MidiQualityStatus.LEGACY_UNKNOWN)
    }
}

enum class PartSourceType { MIDI, AUDIO, UNKNOWN }
enum class PartAnalysisStatus { NONE, LEGACY_AUDIO, MIDI }

data class PartAnalysisSummary(
    val status: PartAnalysisStatus,
    val file: String,
    val bars: Int? = null,
    val durationSeconds: Double? = null,
    val key: String? = null
)

data class StructureSectionSummary(
    val index: Int,
    val partId: String,
    val occurrence: Int,
    val instanceId: String,
    val durationSeconds: Double?
)

data class ProjectReadiness(
    val cleanMidiReady: Boolean,
    val analysesReady: Boolean,
    val structureReady: Boolean,
    val songPlanAvailable: Boolean,
    val arrangementAvailable: Boolean,
    val generatedMidiAvailable: Boolean,
    val stemsAvailable: Boolean,
    val dryMixAvailable: Boolean,
    val loFiMixAvailable: Boolean,
    val masterAvailable: Boolean,
    val midiQualityReportsReady: Boolean = false,
    /** A final release is complete only when metadata accompanies the validated master. */
    val releaseAvailable: Boolean = false,
    /** Durable invalidation evidence; availability above remains file-derived. */
    val staleArtifacts: Set<app.melotrail.arrangement.WorkflowArtifact> = emptySet(),
    val cohesionApprovalRequired: Boolean = false
)

class DefaultProjectApplicationService(
    private val midiPreparation: MidiPreparationService,
    private val legacyPartAnalysis: LegacyPartAnalysisService,
    private val midiPartAnalyzer: MidiPartAnalyzer = MidiPartAnalyzer(),
    private val midiQualityReporter: MidiQualityReporter = MidiQualityReporter(),
    private val midiFeelTransformer: MidiLoFiFeelTransformer = MidiLoFiFeelTransformer(),
    private val inputInspection: InputInspectionBoundary = object : InputInspectionBoundary {
        override suspend fun inspect(request: InputInspectionRequest): InputInspectionResult =
            InputInspectionResult.Rejected(InputInspectionError(
                InputInspectionErrorCode.MEASUREMENT_FAILED,
                "Input inspection is not configured."
            ))
    }
) : ProjectApplicationService {
    override fun open(root: Path): ProjectSnapshot {
        val normalizedRoot = root.normalizeRoot()
        require(Files.isRegularFile(normalizedRoot.resolve(ProjectStore.FILE_NAME))) {
            "Project file not found: ${normalizedRoot.resolve(ProjectStore.FILE_NAME)}"
        }
        return snapshot(normalizedRoot, readValidProject(normalizedRoot))
    }

    override fun migrateV2(root: Path): ProjectSnapshot = mutate(root) { normalizedRoot ->
        snapshot(normalizedRoot, ProjectStore.migrateV2(normalizedRoot))
    }

    override fun create(request: CreateProjectRequest): ProjectSnapshot = mutate(request.root) { root ->
        require(!Files.exists(root) || Files.isDirectory(root)) { "Project path is not a directory: $root" }
        require(!Files.exists(root.resolve(ProjectStore.FILE_NAME))) { "Project already exists: ${root.resolve(ProjectStore.FILE_NAME)}" }
        val name = request.name ?: root.fileName?.toString().orEmpty()
        require(name.isNotBlank()) { "Project directory must have a name" }
        listOf("source", "midi/raw", "midi/clean", "midi/quality", "midi/derived", "midi/feel", "midi/generated").forEach { Files.createDirectories(root.resolve(it)) }
        val project = ProjectStore.create(root, name, request.renderFormat)
        snapshot(root, project)
    }

    override suspend fun importPart(request: ImportPartRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        require(PART_ID.matches(request.id)) { "Part ID must contain only letters, numbers, underscores, or hyphens: ${request.id}" }
        val source = request.source.toAbsolutePath().normalize()
        require(Files.isRegularFile(source)) { "Input file not found: $source" }
        val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
        require(extension in SUPPORTED_EXTENSIONS) { "Unsupported input file extension: ${if (extension.isEmpty()) "(none)" else extension}" }
        val isMidi = extension in MIDI_EXTENSIONS
        require(!(isMidi && request.transcribe)) { "--transcribe is only valid for audio input" }
        val project = readValidProject(root)
        require(project.parts.none { it.id == request.id }) { "Part ID already exists: ${request.id}" }
        if (project.version == 1 && !isMidi && !request.transcribe) {
            val relativeFile = "parts/${request.id}.$extension"
            val destination = safeDestination(root, relativeFile)
            require(source != destination) { "Input and destination paths must differ" }
            require(!Files.exists(destination)) { "Part destination already exists: $destination" }
            progress.report(OperationProgress("import-part", 1, 2, "Copying source", destination))
            Files.createDirectories(checkNotNull(destination.parent))
            Files.copy(source, destination)
            val updated = project.copy(parts = project.parts + Part(request.id, relativeFile, request.role))
            ProjectStore.write(root, updated)
            progress.report(OperationProgress("import-part", 2, 2, "Registered legacy audio part", destination))
            return@mutateSuspend snapshot(root, updated)
        }

        require(isMidi || request.transcribe) { "Audio input requires --transcribe so immutable raw MIDI can be prepared" }
        val relativeFile = "source/${request.id}.$extension"
        val destination = safeDestination(root, relativeFile)
        require(source != destination && !(Files.exists(destination) && Files.isSameFile(source, destination))) {
            "Input and destination paths must differ"
        }
        if (Files.exists(destination)) {
            require(Files.isRegularFile(destination) && Files.mismatch(source, destination) == -1L) {
                "Part destination already exists with different source content: $destination"
            }
            progress.report(OperationProgress("import-part", 1, 4, "Reusing preserved source", destination))
        } else {
            progress.report(OperationProgress("import-part", 1, 4, "Copying source", destination))
            Files.createDirectories(checkNotNull(destination.parent))
            Files.copy(source, destination)
        }

        val raw = "midi/raw/${request.id}.mid"
        val rawPath = safeDestination(root, raw)
        val rawWork = temporaryMidi(rawPath)
        try {
            if (Files.isRegularFile(rawPath)) {
                requireMidiArtifact(rawPath, "Existing raw MIDI")
                progress.report(OperationProgress("import-part", 2, 3, "Reusing immutable raw MIDI", rawPath))
            } else {
                if (isMidi) {
                    progress.report(OperationProgress("import-part", 2, 3, "Publishing immutable raw MIDI", rawPath))
                    Files.copy(destination, rawWork)
                } else {
                    progress.report(OperationProgress("import-part", 2, 3, "Transcribing audio to raw MIDI", rawPath))
                    midiPreparation.transcribe(destination, rawWork)
                }
                requireMidiArtifact(rawWork, if (isMidi) "MIDI import" else "Transcription")
                atomicReplace(rawWork, rawPath, if (isMidi) "raw MIDI import" else "transcription")
            }
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Part '${request.id}' was not registered. Source preserved at $destination; raw MIDI publication failed: ${exception.message}",
                exception
            )
        } finally {
            Files.deleteIfExists(rawWork)
        }

        val updated = project.copy(
            parts = project.parts + Part(request.id, relativeFile, request.role, midi = MidiReferences(raw = raw)),
            workflow = project.workflow.invalidate(WorkflowChange.SOURCE_OR_RAW)
        )
        val saved = if (project.version == 1) ProjectStore.upgrade(root, project, updated.parts) else updated.also { ProjectStore.write(root, it) }
        progress.report(OperationProgress("import-part", 3, 3, "Registered raw MIDI; run Repair MIDI before analysis", root.resolve(ProjectStore.FILE_NAME)))
        snapshot(root, saved)
    }

    override suspend fun retryMidiCleanup(request: RetryMidiCleanupRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        request.cleanup.requireValid()
        val project = readValidProject(root)
        require(project.version >= Project.MIDI_FIRST_VERSION) {
            "Part '${request.partId}' is legacy and has no retryable MIDI cleanup provenance. Re-import it to establish MIDI quality review."
        }
        val part = project.parts.find { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${request.partId}' has no raw MIDI artifact to repair." }
        val rawReference = requireNotNull(midi.raw) {
            "Part '${request.partId}' predates explicit raw MIDI evidence. Re-import it before repairing."
        }
        val rawPath = safeDestination(root, rawReference)
        val cleanPath = safeDestination(root, "midi/clean/${request.partId}.mid")
        requireMidiArtifact(rawPath, "Raw MIDI")

        val cleanWork = temporaryMidi(cleanPath)
        val cleanBackup = cleanPath.takeIf(Files::isRegularFile)?.let(::temporaryMidi)
        val reportPath = MidiQualityReportStore.path(root, request.partId)
        val reportBackup = reportPath.takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
        cleanBackup?.let { Files.copy(cleanPath, it, StandardCopyOption.REPLACE_EXISTING) }
        var cleanPublished = false
        var reportPublished = false
        try {
            progress.report(OperationProgress("repair-midi", 1, 3, "Repairing MIDI with ${request.cleanup.profile.name.lowercase().replace('_', '-')}", cleanPath))
            midiPreparation.clean(rawPath, cleanWork, request.cleanup)
            requireMidiArtifact(cleanWork, "MIDI cleanup")
            val report = midiQualityReporter.report(request.partId, rawPath, cleanWork, request.cleanup)
            atomicReplace(cleanWork, cleanPath, "MIDI cleanup")
            cleanPublished = true
            val publishedReport = MidiQualityReportStore.write(root, report)
            reportPublished = true
            val qualityReference = root.relativize(publishedReport).toString().replace('\\', '/')
            val updated = project.copy(parts = project.parts.map {
                if (it.id == request.partId) it.copy(
                    analysis = null,
                    midi = midi.copy(
                        clean = root.relativize(cleanPath).toString().replace('\\', '/'),
                        cleanup = request.cleanup,
                        quality = qualityReference,
                        approvedRepair = !report.approvalRequired,
                        analysisInput = MidiAnalysisInput.REPAIRED,
                        feel = null
                    )
                ) else it
            }, workflow = project.workflow.invalidate(WorkflowChange.REPAIRED_MIDI).markCurrent(WorkflowArtifact.MIDI_REPAIR))
            ProjectStore.write(root, updated)
            progress.report(OperationProgress("repair-midi", 3, 3, "Saved MIDI repair report; analyze this part again", publishedReport))
            snapshot(root, updated)
        } catch (failure: Exception) {
            if (cleanPublished) runCatching {
                if (cleanBackup == null) Files.deleteIfExists(cleanPath)
                else atomicReplace(cleanBackup, cleanPath, "MIDI cleanup rollback")
            }
            if (reportPublished) runCatching {
                if (reportBackup == null) Files.deleteIfExists(reportPath)
                else atomicWrite(reportPath, reportBackup)
            }
            throw failure
        } finally {
            Files.deleteIfExists(cleanWork)
            cleanBackup?.let(Files::deleteIfExists)
        }
    }

    override fun approveMidiRepair(root: Path, partId: String): ProjectSnapshot = mutate(root) { projectRoot ->
        val project = readValidProject(projectRoot)
        val part = project.parts.find { it.id == partId } ?: throw IllegalArgumentException("Part not found: $partId")
        val midi = requireNotNull(part.midi) { "Part '$partId' has no MIDI repair." }
        val raw = requireNotNull(midi.raw) { "Part '$partId' predates explicit MIDI repair evidence." }
        val clean = requireNotNull(midi.clean) { "Part '$partId' has no repaired MIDI." }
        val cleanup = requireNotNull(midi.cleanup) { "Part '$partId' has no MIDI repair report." }
        val quality = requireNotNull(midi.quality) { "Part '$partId' has no MIDI repair report." }
        MidiQualityReportStore.requireCurrent(projectRoot, partId, raw, clean, cleanup, quality)
        val report = MidiQualityReportStore.read(projectRoot, quality)
        require(report.approvalRequired) { "Part '$partId' does not require MIDI repair approval." }
        val updated = project.copy(parts = project.parts.map { if (it.id == partId) it.copy(midi = midi.copy(approvedRepair = true)) else it })
        ProjectStore.write(projectRoot, updated)
        snapshot(projectRoot, updated)
    }

    override fun selectMidiFeel(request: SelectMidiFeelRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        require(project.version >= Project.MIDI_FIRST_VERSION) { "Lo-fi Feel requires a MIDI-first project." }
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${part.id}' has no repaired MIDI." }
        val clean = requireNotNull(midi.clean) { "Part '${part.id}' has no repaired MIDI. Run Repair MIDI first." }
        if (midi.raw != null) {
            MidiQualityReportStore.requireCurrent(root, part.id, midi.raw, clean, requireNotNull(midi.cleanup), requireNotNull(midi.quality))
            val repair = MidiQualityReportStore.read(root, requireNotNull(midi.quality))
            require(!repair.approvalRequired || midi.approvedRepair) { "Part '${part.id}' MIDI repair requires approval before selecting Lo-fi Feel." }
        }
        val selectedMidi = when (request.input) {
            MidiAnalysisInput.REPAIRED -> midi.copy(analysisInput = MidiAnalysisInput.REPAIRED)
            MidiAnalysisInput.LOFI_FEEL -> {
                val profile = MidiFeelProfile.LOFI_80_SWING_V1
                val derived = MidiFeelReportStore.derivedPath(root, part.id, profile)
                val reportPath = MidiFeelReportStore.reportPath(root, part.id, profile)
                val existing = midi.feel?.takeIf { it.profile == profile && MidiFeelReportStore.isCurrent(root, part.id, clean, it) }
                val feel = existing ?: publishMidiFeel(root, part.id, clean, derived, reportPath, profile)
                midi.copy(analysisInput = MidiAnalysisInput.LOFI_FEEL, feel = feel)
            }
        }
        if (selectedMidi == midi) return@mutate snapshot(root, project)
        val updated = project.copy(
            parts = project.parts.map { if (it.id == part.id) it.copy(analysis = null, midi = selectedMidi) else it },
            workflow = project.workflow.invalidate(WorkflowChange.MIDI_FEEL).markCurrent(WorkflowArtifact.MIDI_FEEL)
        )
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override suspend fun analyzePart(request: AnalyzePartRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val analysisPath = if (project.version >= Project.MIDI_FIRST_VERSION) {
            val midi = requireNotNull(part.midi)
            val cleanReference = requireNotNull(midi.clean) { "Part '${part.id}' has no repaired MIDI. Run Repair MIDI before analysis." }
            if (midi.raw != null) MidiQualityReportStore.requireCurrent(
                root, part.id, midi.raw, cleanReference, requireNotNull(midi.cleanup), requireNotNull(midi.quality)
            )
            if (midi.raw != null) {
                val report = MidiQualityReportStore.read(root, requireNotNull(midi.quality))
                require(!report.approvalRequired || midi.approvedRepair) { "Part '${part.id}' MIDI repair requires approval before analysis." }
            }
            val analysisReference = when (midi.analysisInput) {
                MidiAnalysisInput.REPAIRED -> cleanReference
                MidiAnalysisInput.LOFI_FEEL -> requireNotNull(midi.feel).also { MidiFeelReportStore.requireCurrent(root, part.id, cleanReference, it) }.derived
            }
            val analysisMidi = safeDestination(root, analysisReference)
            progress.report(OperationProgress("analyze-part", 1, 2, "Analyzing ${if (midi.analysisInput == MidiAnalysisInput.LOFI_FEEL) "Lo-fi Feel" else "repaired"} MIDI", analysisMidi))
            MidiAnalysisStore.write(root, project, request.partId, midiPartAnalyzer.analyze(analysisMidi, request.partId))
        } else {
            val source = root.resolve(part.file).normalize()
            progress.report(OperationProgress("analyze-part", 1, 2, "Analyzing source audio", source))
            PartAnalysisStore.write(root, project, request.partId, legacyPartAnalysis.analyze(source))
        }
        progress.report(OperationProgress("analyze-part", 2, 2, "Saved analysis", analysisPath))
        snapshot(root, readValidProject(root))
    }

    override suspend fun inspectPart(request: InspectPartRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        require(part.file.startsWith("source/")) {
            "Part '${part.id}' has no canonical source/ artifact and cannot be inspected."
        }
        val sourcePath = safeDestination(root, part.file)
        require(Files.isRegularFile(sourcePath)) { "Part source is missing: ${part.file}" }
        val source = InspectionSourceIdentity(part.file, sha256(sourcePath))
        val inspectionRequest = InputInspectionRequest(root, part.id, source).also { it.requireValid() }

        val existing = runCatching { InputInspectionReportStore.read(root, part.id) }.getOrNull()
        if (existing?.source == source) {
            progress.report(OperationProgress("inspect-part", 1, 1, "Reusing current inspection report", InputInspectionPaths.report(root, part.id)))
            return@mutateSuspend snapshot(root, project)
        }

        progress.report(OperationProgress("inspect-part", 1, 2, "Inspecting preserved source", sourcePath))
        val result = inputInspection.inspect(inspectionRequest)
        val report = when (result) {
            is InputInspectionResult.Inspected -> result.report
            is InputInspectionResult.Rejected -> {
                result.error.requireValid()
                throw IllegalStateException("Input inspection failed (${result.error.code}): ${result.error.message}")
            }
        }
        require(report.partId == part.id && report.source == source) {
            "Input inspection returned a report for a different project source."
        }
        report.requireValid()
        InputInspectionReportStore.write(root, report)
        progress.report(OperationProgress("inspect-part", 2, 2, "Saved inspection report", InputInspectionPaths.report(root, part.id)))
        snapshot(root, project)
    }

    override fun updatePart(request: UpdatePartRoleRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        require(project.parts.any { it.id == request.partId }) { "Part not found: ${request.partId}" }
        val updated = project.copy(parts = project.parts.map { if (it.id == request.partId) it.copy(role = request.role) else it })
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    override fun saveStructure(request: SaveStructureRequest): ProjectSnapshot = mutate(request.root) { root ->
        val project = readValidProject(root)
        val knownIds = project.parts.map { it.id }.toSet()
        request.partIds.forEach { id -> require(id in knownIds) { "Unknown part ID in structure: $id" } }
        val updated = project.copy(
            structure = request.partIds.toList(),
            workflow = project.workflow.invalidate(WorkflowChange.STRUCTURE)
        )
        ProjectStore.write(root, updated)
        snapshot(root, updated)
    }

    private fun readValidProject(root: Path): Project {
        require(Files.isRegularFile(root.resolve(ProjectStore.FILE_NAME))) { "Project file not found: ${root.resolve(ProjectStore.FILE_NAME)}" }
        return ProjectStore.read(root).also { it.requireValid(root) }
    }

    private fun snapshot(root: Path, project: Project): ProjectSnapshot {
        val summaries = project.parts.map { part -> part.summary(root) }
        val durationById = summaries.associate { it.id to it.analysis?.durationSeconds }
        val occurrences = mutableMapOf<String, Int>()
        val structure = project.structure.mapIndexed { index, partId ->
            val occurrence = (occurrences[partId] ?: 0) + 1
            occurrences[partId] = occurrence
            StructureSectionSummary(index, partId, occurrence, "$partId$occurrence", durationById[partId])
        }
        fun current(artifact: WorkflowArtifact) = artifact !in project.workflow.stale
        return ProjectSnapshot(
            root = root,
            version = project.version,
            name = project.name,
            renderFormat = project.renderFormat,
            parts = summaries,
            structure = structure,
            readiness = ProjectReadiness(
                cleanMidiReady = summaries.isNotEmpty() && summaries.all { it.preparation.cleanMidi },
                analysesReady = summaries.isNotEmpty() && summaries.all { it.preparation.analyzed },
                structureReady = project.structure.isNotEmpty(),
                songPlanAvailable = Files.isRegularFile(root.resolve("song_plan.json")) && current(WorkflowArtifact.COHESION),
                arrangementAvailable = Files.isRegularFile(root.resolve("arrangement.json")) && current(WorkflowArtifact.ARRANGEMENT),
                generatedMidiAvailable = Files.isDirectory(root.resolve("midi/generated")) && current(WorkflowArtifact.GENERATED_MIDI) && Files.list(root.resolve("midi/generated")).use { it.anyMatch { Files.isRegularFile(it) } },
                stemsAvailable = Files.isDirectory(root.resolve("stems")) && current(WorkflowArtifact.STEMS) && Files.list(root.resolve("stems")).use { it.anyMatch { Files.isRegularFile(it) } },
                dryMixAvailable = Files.isRegularFile(root.resolve("mix/dry.wav")) && current(WorkflowArtifact.DRY_MIX),
                loFiMixAvailable = Files.isRegularFile(root.resolve("mix/lofi.wav")) && current(WorkflowArtifact.AUDIO_TEXTURE),
                masterAvailable = Files.isRegularFile(root.resolve("output/master.wav")) && current(WorkflowArtifact.MASTER),
                midiQualityReportsReady = summaries.isNotEmpty() && summaries.all { it.preparation.midiQuality.status == MidiQualityStatus.CURRENT },
                releaseAvailable = Files.isRegularFile(root.resolve("output/release.json")) && current(WorkflowArtifact.RELEASE),
                staleArtifacts = project.workflow.stale,
                cohesionApprovalRequired = project.workflow.cohesion?.let { !it.approved && WorkflowArtifact.COHESION !in project.workflow.stale } == true
            )
        )
    }

    private fun Part.summary(root: Path): PartSummary {
        val sourcePath = Path.of(file)
        val sourceType = sourceType(file)
        val sourcePreserved = sourcePath.startsWith("source") && isProjectFile(root, file)
        val source = if (sourcePreserved) InspectionSourceIdentity(file, sha256(root.resolve(file))) else null
        val report = runCatching { InputInspectionReportStore.read(root, id) }.getOrNull()
        val inspected = source != null && report?.source == source
        val warnings = when {
            report == null && Files.isRegularFile(InputInspectionPaths.report(root, id)) -> listOf("Inspection report is invalid; inspect again.")
            report != null && source != null && report.source != source -> listOf("Inspection report is stale; inspect again.")
            inspected -> report.warnings
            else -> emptyList()
        }
        val rawMidi = midi?.raw?.let { isMidiArtifact(root, it) } ?: false
        val cleanMidi = midi?.clean?.let { isMidiArtifact(root, it) } ?: false
        val quality = midiQuality(root, this, cleanMidi)
        val analyzed = analysis?.let { runCatching { it.summary(root) }.isSuccess } ?: false
        val preparedAudio = sourceType == PartSourceType.AUDIO && inspected &&
            report?.preparation == PreparationStatus.CLEANED && isWaveArtifact(InputInspectionPaths.cleanWav(root, id))
        val feel = midiFeel(root, this, cleanMidi)
        val preparation = PartPreparationSummary(
            sourcePreserved = sourcePreserved,
            inspected = inspected,
            preparedAudio = preparedAudio,
            rawMidi = rawMidi,
            cleanMidi = cleanMidi,
            analyzed = analyzed,
            ready = sourcePreserved && inspected && cleanMidi && analyzed && quality.status == MidiQualityStatus.CURRENT,
            warnings = warnings + quality.warnings.map { it.message },
            midiQuality = quality,
            midiFeel = feel
        )
        return PartSummary(id, role, file, sourcePath.fileName.toString(), sourceType, analysis?.summary(root), preparation)
    }

    private fun midiQuality(root: Path, part: Part, cleanMidi: Boolean): MidiQualitySummary {
        val midi = part.midi ?: return MidiQualitySummary.legacyUnknown()
        if (midi.raw == null && midi.cleanup == null && midi.quality == null) return MidiQualitySummary.legacyUnknown()
        if (midi.cleanup == null || midi.quality == null || !cleanMidi) return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        val rawReference = requireNotNull(midi.raw)
        val report = runCatching { MidiQualityReportStore.read(root, midi.quality) }.getOrNull()
            ?: return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        if (!MidiQualityReportStore.isCurrent(root, part.id, rawReference, requireNotNull(midi.clean), midi.cleanup, midi.quality)) {
            return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        }
        val status = if (report.approvalRequired && !midi.approvedRepair) MidiQualityStatus.APPROVAL_REQUIRED else MidiQualityStatus.CURRENT
        return MidiQualitySummary(status, report.cleanup, report.warnings, report.recommendations, report)
    }

    private fun midiFeel(root: Path, part: Part, cleanMidi: Boolean): MidiFeelSummary {
        val midi = part.midi ?: return MidiFeelSummary()
        val references = midi.feel ?: return MidiFeelSummary(midi.analysisInput)
        val clean = midi.clean ?: return MidiFeelSummary(midi.analysisInput)
        val report = runCatching { MidiFeelReportStore.read(root, references.report) }.getOrNull()
        val current = cleanMidi && MidiFeelReportStore.isCurrent(root, part.id, clean, references)
        return MidiFeelSummary(midi.analysisInput, current, report?.takeIf { current })
    }

    private fun isProjectFile(root: Path, reference: String): Boolean = runCatching {
        val path = safeDestination(root, reference)
        Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())
    }.getOrDefault(false)

    private fun isMidiArtifact(root: Path, reference: String): Boolean = runCatching {
        val path = safeDestination(root, reference)
        Files.isRegularFile(path) && Files.size(path) >= 14 &&
            Files.newInputStream(path).use { it.readNBytes(4).decodeToString() == "MThd" }
    }.getOrDefault(false)

    private fun isWaveArtifact(path: Path): Boolean = runCatching {
        Files.isRegularFile(path) && Files.newInputStream(path).use {
            val header = it.readNBytes(12)
            header.size == 12 && header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                header.copyOfRange(8, 12).decodeToString() == "WAVE"
        }
    }.getOrDefault(false)

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun PartAnalysisReference.summary(root: Path): PartAnalysisSummary {
        val path = root.resolve(file).normalize()
        return when (kind) {
            AnalysisKind.MIDI -> {
                val analysis = json.decodeFromString(MidiAnalysis.serializer(), Files.readString(path))
                PartAnalysisSummary(PartAnalysisStatus.MIDI, file, analysis.bars, analysis.durationSeconds, analysis.key?.let { "${it.tonic} ${it.mode}" })
            }
            AnalysisKind.AUDIO, null -> {
                val analysis = json.decodeFromString(PartAnalysis.serializer(), Files.readString(path))
                PartAnalysisSummary(PartAnalysisStatus.LEGACY_AUDIO, file, null, analysis.duration, listOfNotNull(analysis.keyRoot, analysis.keyMode).takeIf { it.isNotEmpty() }?.joinToString(" "))
            }
        }
    }

    private fun mutate(root: Path, action: (Path) -> ProjectSnapshot): ProjectSnapshot {
        val normalizedRoot = root.normalizeRoot()
        val lock = locks.computeIfAbsent(normalizedRoot) { Mutex() }
        require(lock.tryLock()) { "Another project mutation is already running: $normalizedRoot" }
        return try { action(normalizedRoot) } finally { lock.unlock() }
    }

    private suspend fun mutateSuspend(root: Path, action: suspend (Path) -> ProjectSnapshot): ProjectSnapshot {
        val normalizedRoot = root.normalizeRoot()
        val lock = locks.computeIfAbsent(normalizedRoot) { Mutex() }
        require(lock.tryLock()) { "Another project mutation is already running: $normalizedRoot" }
        return try { withContext(Dispatchers.IO) { action(normalizedRoot) } } finally { lock.unlock() }
    }

    private fun Path.normalizeRoot(): Path = toAbsolutePath().normalize()

    private fun safeDestination(projectRoot: Path, reference: String): Path {
        val relative = Path.of(reference)
        require(!relative.isAbsolute && reference.isNotBlank()) { "Project destination must be relative: $reference" }
        val destination = projectRoot.resolve(relative).normalize()
        require(destination.startsWith(projectRoot)) { "Project destination escapes the project root: $reference" }
        val realRoot = projectRoot.toRealPath()
        var existing = destination.parent
        while (existing != null && !Files.exists(existing)) existing = existing.parent
        if (existing != null) require(existing.toRealPath().startsWith(realRoot)) {
            "Project destination escapes the project root through a symlink: $reference"
        }
        return destination
    }

    private fun requireMidiArtifact(path: Path, stage: String) {
        require(Files.isRegularFile(path) && Files.size(path) >= 14) { "$stage did not create a MIDI file: $path" }
        val header = Files.newInputStream(path).use { it.readNBytes(4).decodeToString() }
        require(header == "MThd") { "$stage did not create a MIDI file: $path" }
    }

    private fun temporaryMidi(target: Path): Path = target.resolveSibling(".${target.fileName}.prepare-${UUID.randomUUID()}.mid")

    private fun atomicReplace(source: Path, target: Path, stage: String) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publish is not supported for $stage output '$target'", error)
        }
    }

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        val temporary = target.resolveSibling(".${target.fileName}.restore-${UUID.randomUUID()}.tmp")
        try {
            Files.write(temporary, bytes)
            atomicReplace(temporary, target, "MIDI quality report rollback")
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun publishMidiFeel(root: Path, partId: String, cleanReference: String, derived: Path, reportPath: Path, profile: MidiFeelProfile): MidiFeelReferences {
        val work = temporaryMidi(derived)
        val oldDerived = derived.takeIf(Files::isRegularFile)?.let { Files.readAllBytes(it) }
        val oldReport = reportPath.takeIf(Files::isRegularFile)?.let { Files.readAllBytes(it) }
        var derivedPublished = false
        var reportPublished = false
        try {
            val result = midiFeelTransformer.transform(safeDestination(root, cleanReference), work, partId, profile)
            requireMidiArtifact(work, "Lo-fi Feel")
            atomicReplace(work, derived, "Lo-fi Feel MIDI")
            derivedPublished = true
            val report = MidiFeelReportStore.write(root, result.report)
            reportPublished = true
            return MidiFeelReferences(profile, root.relativize(derived).toString().replace('\\', '/'), root.relativize(report).toString().replace('\\', '/'))
        } catch (failure: Exception) {
            if (derivedPublished) runCatching { restoreArtifact(derived, oldDerived, "Lo-fi Feel MIDI rollback") }
            if (reportPublished) runCatching { restoreArtifact(reportPath, oldReport, "Lo-fi Feel report rollback") }
            throw failure
        } finally { Files.deleteIfExists(work) }
    }

    private fun restoreArtifact(target: Path, old: ByteArray?, stage: String) {
        if (old == null) Files.deleteIfExists(target) else {
            val temporary = target.resolveSibling(".${target.fileName}.restore-${UUID.randomUUID()}.tmp")
            try { Files.write(temporary, old); atomicReplace(temporary, target, stage) } finally { Files.deleteIfExists(temporary) }
        }
    }

    private fun sourceType(file: String): PartSourceType = when (file.substringAfterLast('.', "").lowercase()) {
        in MIDI_EXTENSIONS -> PartSourceType.MIDI
        "wav", "wave", "mp3" -> PartSourceType.AUDIO
        else -> PartSourceType.UNKNOWN
    }

    private companion object {
        val locks = ConcurrentHashMap<Path, Mutex>()
        val json = Json { ignoreUnknownKeys = false }
        val PART_ID = Regex("[A-Za-z0-9_-]+")
        val MIDI_EXTENSIONS = setOf("mid", "midi")
        val SUPPORTED_EXTENSIONS = MIDI_EXTENSIONS + setOf("wav", "wave", "mp3")
    }
}

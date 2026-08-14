package ai.music.workstation.application

import ai.music.workstation.arrangement.AnalysisKind
import ai.music.workstation.arrangement.MidiAnalysis
import ai.music.workstation.arrangement.MidiAnalysisStore
import ai.music.workstation.arrangement.MidiCleanupOptions
import ai.music.workstation.arrangement.MidiPartAnalyzer
import ai.music.workstation.arrangement.MidiQualityRecommendation
import ai.music.workstation.arrangement.MidiQualityReport
import ai.music.workstation.arrangement.MidiQualityReportStore
import ai.music.workstation.arrangement.MidiQualityReporter
import ai.music.workstation.arrangement.MidiQualityWarning
import ai.music.workstation.arrangement.MidiReferences
import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.PartAnalysis
import ai.music.workstation.arrangement.PartAnalysisReference
import ai.music.workstation.arrangement.PartAnalysisStore
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.RenderFormat
import ai.music.workstation.preparation.InputInspectionBoundary
import ai.music.workstation.preparation.InputInspectionError
import ai.music.workstation.preparation.InputInspectionErrorCode
import ai.music.workstation.preparation.InputInspectionPaths
import ai.music.workstation.preparation.InputInspectionReportStore
import ai.music.workstation.preparation.InputInspectionRequest
import ai.music.workstation.preparation.InputInspectionResult
import ai.music.workstation.preparation.InspectionSourceIdentity
import ai.music.workstation.preparation.PreparationStatus
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
    fun create(request: CreateProjectRequest): ProjectSnapshot
    suspend fun importPart(request: ImportPartRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
    suspend fun retryMidiCleanup(request: RetryMidiCleanupRequest, progress: ProgressSink = ProgressSink.None): ProjectSnapshot
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
    val midiQuality: MidiQualitySummary = MidiQualitySummary.legacyUnknown()
)

enum class MidiQualityStatus { CURRENT, LEGACY_UNKNOWN, STALE_OR_INVALID }

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
    val releaseAvailable: Boolean = false
)

class DefaultProjectApplicationService(
    private val midiPreparation: MidiPreparationService,
    private val legacyPartAnalysis: LegacyPartAnalysisService,
    private val midiPartAnalyzer: MidiPartAnalyzer = MidiPartAnalyzer(),
    private val midiQualityReporter: MidiQualityReporter = MidiQualityReporter(),
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

    override fun create(request: CreateProjectRequest): ProjectSnapshot = mutate(request.root) { root ->
        require(!Files.exists(root) || Files.isDirectory(root)) { "Project path is not a directory: $root" }
        require(!Files.exists(root.resolve(ProjectStore.FILE_NAME))) { "Project already exists: ${root.resolve(ProjectStore.FILE_NAME)}" }
        val name = request.name ?: root.fileName?.toString().orEmpty()
        require(name.isNotBlank()) { "Project directory must have a name" }
        listOf("source", "midi/raw", "midi/clean", "midi/generated").forEach { Files.createDirectories(root.resolve(it)) }
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
        request.cleanup.requireValid()

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

        require(isMidi || request.transcribe) { "Audio input requires --transcribe so a clean MIDI artifact can be prepared" }
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

        val raw = if (isMidi) null else "midi/raw/${request.id}.mid"
        val clean = "midi/clean/${request.id}.mid"
        val rawPath = raw?.let { safeDestination(root, it) }
        val cleanPath = safeDestination(root, clean)
        // Preserve the first failed attempt for diagnosis. An explicit retry writes
        // to a sibling temporary path, then atomically replaces only derived MIDI.
        val rawWork = rawPath?.takeIf(Files::exists)?.let(::temporaryMidi)
        val rawOutput = rawWork ?: rawPath
        val cleanWork = cleanPath.takeIf(Files::exists)?.let(::temporaryMidi)
        val cleanOutput = cleanWork ?: cleanPath
        try {
            if (rawPath != null && rawOutput != null) {
                progress.report(OperationProgress("import-part", 2, 4, "Transcribing audio", rawPath))
                midiPreparation.transcribe(destination, rawOutput)
                requireMidiArtifact(rawOutput, "Transcription")
            }
            progress.report(OperationProgress("import-part", 3, 4, "Cleaning MIDI", cleanPath))
            midiPreparation.clean(rawOutput ?: safeDestination(root, relativeFile), cleanOutput, request.cleanup)
            requireMidiArtifact(cleanOutput, "MIDI cleanup")
            rawWork?.let { atomicReplace(it, checkNotNull(rawPath), "Transcription") }
            cleanWork?.let { atomicReplace(it, cleanPath, "MIDI cleanup") }
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Part '${request.id}' was not registered. Source preserved at $destination; temporary MIDI preparation failed: ${exception.message}",
                exception
            )
        } finally {
            rawWork?.let(Files::deleteIfExists)
            cleanWork?.let(Files::deleteIfExists)
        }

        val rawReference = raw ?: relativeFile
        val report = midiQualityReporter.report(request.id, safeDestination(root, rawReference), cleanPath, request.cleanup)
        val reportPath = MidiQualityReportStore.write(root, report)
        val reportReference = root.relativize(reportPath).toString().replace('\\', '/')
        val updated = project.copy(parts = project.parts + Part(request.id, relativeFile, request.role, midi = MidiReferences(raw, clean, request.cleanup, reportReference)))
        val saved = if (project.version == 1) ProjectStore.upgrade(root, project, updated.parts) else updated.also { ProjectStore.write(root, it) }
        progress.report(OperationProgress("import-part", 4, 4, "Registered part", root.resolve(ProjectStore.FILE_NAME)))
        snapshot(root, saved)
    }

    override suspend fun retryMidiCleanup(request: RetryMidiCleanupRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        request.cleanup.requireValid()
        val project = readValidProject(root)
        require(project.version == Project.CURRENT_VERSION) {
            "Part '${request.partId}' is legacy and has no retryable MIDI cleanup provenance. Re-import it to establish MIDI quality review."
        }
        val part = project.parts.find { it.id == request.partId }
            ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val midi = requireNotNull(part.midi) { "Part '${request.partId}' has no clean MIDI artifact to retry." }
        val rawReference = midi.raw ?: part.file
        val rawPath = safeDestination(root, rawReference)
        val cleanPath = safeDestination(root, midi.clean)
        requireMidiArtifact(rawPath, "Raw MIDI")
        requireMidiArtifact(cleanPath, "Existing clean MIDI")

        val cleanWork = temporaryMidi(cleanPath)
        val cleanBackup = temporaryMidi(cleanPath)
        val reportPath = MidiQualityReportStore.path(root, request.partId)
        val reportBackup = reportPath.takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
        Files.copy(cleanPath, cleanBackup, StandardCopyOption.REPLACE_EXISTING)
        var cleanPublished = false
        var reportPublished = false
        try {
            progress.report(OperationProgress("retry-midi-cleanup", 1, 3, "Cleaning MIDI with ${request.cleanup.profile.name.lowercase().replace('_', '-')}", cleanPath))
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
                    midi = midi.copy(cleanup = request.cleanup, quality = qualityReference)
                ) else it
            })
            ProjectStore.write(root, updated)
            progress.report(OperationProgress("retry-midi-cleanup", 3, 3, "Saved MIDI quality report; analyze this part again", publishedReport))
            snapshot(root, updated)
        } catch (failure: Exception) {
            if (cleanPublished) runCatching { atomicReplace(cleanBackup, cleanPath, "MIDI cleanup rollback") }
            if (reportPublished) runCatching {
                if (reportBackup == null) Files.deleteIfExists(reportPath)
                else atomicWrite(reportPath, reportBackup)
            }
            throw failure
        } finally {
            Files.deleteIfExists(cleanWork)
            Files.deleteIfExists(cleanBackup)
        }
    }

    override suspend fun analyzePart(request: AnalyzePartRequest, progress: ProgressSink): ProjectSnapshot = mutateSuspend(request.root) { root ->
        val project = readValidProject(root)
        val part = project.parts.find { it.id == request.partId } ?: throw IllegalArgumentException("Part not found: ${request.partId}")
        val analysisPath = if (project.version == Project.CURRENT_VERSION) {
            val cleanMidi = root.resolve(requireNotNull(part.midi).clean).normalize()
            progress.report(OperationProgress("analyze-part", 1, 2, "Analyzing clean MIDI", cleanMidi))
            MidiAnalysisStore.write(root, project, request.partId, midiPartAnalyzer.analyze(cleanMidi, request.partId))
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
        val updated = project.copy(structure = request.partIds.toList())
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
                songPlanAvailable = Files.isRegularFile(root.resolve("song_plan.json")),
                arrangementAvailable = Files.isRegularFile(root.resolve("arrangement.json")),
                generatedMidiAvailable = Files.isDirectory(root.resolve("midi/generated")) && Files.list(root.resolve("midi/generated")).use { it.anyMatch { Files.isRegularFile(it) } },
                stemsAvailable = Files.isDirectory(root.resolve("stems")) && Files.list(root.resolve("stems")).use { it.anyMatch { Files.isRegularFile(it) } },
                dryMixAvailable = Files.isRegularFile(root.resolve("mix/dry.wav")),
                loFiMixAvailable = Files.isRegularFile(root.resolve("mix/lofi.wav")),
                masterAvailable = Files.isRegularFile(root.resolve("output/master.wav")),
                midiQualityReportsReady = summaries.isNotEmpty() && summaries.all { it.preparation.midiQuality.status == MidiQualityStatus.CURRENT },
                releaseAvailable = Files.isRegularFile(root.resolve("output/release.json"))
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
        val cleanMidi = midi?.let { isMidiArtifact(root, it.clean) } ?: false
        val quality = midiQuality(root, this, cleanMidi)
        val analyzed = analysis?.let { runCatching { it.summary(root) }.isSuccess } ?: false
        val preparedAudio = sourceType == PartSourceType.AUDIO && inspected &&
            report?.preparation == PreparationStatus.CLEANED && isWaveArtifact(InputInspectionPaths.cleanWav(root, id))
        val preparation = PartPreparationSummary(
            sourcePreserved = sourcePreserved,
            inspected = inspected,
            preparedAudio = preparedAudio,
            rawMidi = rawMidi,
            cleanMidi = cleanMidi,
            analyzed = analyzed,
            ready = sourcePreserved && inspected && cleanMidi && analyzed && quality.status != MidiQualityStatus.STALE_OR_INVALID,
            warnings = warnings + quality.warnings.map { it.message },
            midiQuality = quality
        )
        return PartSummary(id, role, file, sourcePath.fileName.toString(), sourceType, analysis?.summary(root), preparation)
    }

    private fun midiQuality(root: Path, part: Part, cleanMidi: Boolean): MidiQualitySummary {
        val midi = part.midi ?: return MidiQualitySummary.legacyUnknown()
        if (midi.cleanup == null && midi.quality == null) return MidiQualitySummary.legacyUnknown()
        if (midi.cleanup == null || midi.quality == null || !cleanMidi) return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        val rawReference = midi.raw ?: part.file
        val report = runCatching { MidiQualityReportStore.read(root, midi.quality) }.getOrNull()
            ?: return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        if (!MidiQualityReportStore.isCurrent(root, part.id, rawReference, midi.clean, midi.cleanup, midi.quality)) {
            return MidiQualitySummary(MidiQualityStatus.STALE_OR_INVALID)
        }
        return MidiQualitySummary(MidiQualityStatus.CURRENT, report.cleanup, report.warnings, report.recommendations, report)
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

package ai.music.workstation.application

import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.preparation.ApplyInputCleanupRequest
import ai.music.workstation.preparation.DeterministicInputCleanupPlanner
import ai.music.workstation.preparation.InputCleanupApplicationService
import ai.music.workstation.preparation.InputCleanupMode
import ai.music.workstation.preparation.InputCleanupPlan
import ai.music.workstation.preparation.InputInspectionPaths
import ai.music.workstation.preparation.InputInspectionReport
import ai.music.workstation.preparation.InputInspectionReportStore
import ai.music.workstation.preparation.InputContainer
import ai.music.workstation.preparation.RunTranscriptionQualityGateRequest
import ai.music.workstation.preparation.TranscriptionInputArtifact
import ai.music.workstation.preparation.TranscriptionQualityGateResult
import ai.music.workstation.preparation.TranscriptionQualityGateService
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * UI-neutral access to the bounded input-preparation operations.  It exposes
 * only canonical project artifacts and enum selections; adapters never supply
 * worker paths or cleanup parameters.
 */
interface AudioPreparationApplicationService {
    fun load(projectRoot: Path, partId: String): AudioPreparationSnapshot
    suspend fun inspect(projectRoot: Path, partId: String, progress: ProgressSink = ProgressSink.None): AudioPreparationOperation
    suspend fun applyCleanup(projectRoot: Path, partId: String, mode: InputCleanupMode, confirmedSafeCleanup: Boolean): AudioPreparationOperation
    suspend fun transcribe(projectRoot: Path, partId: String, selectedInput: TranscriptionInputArtifact): AudioPreparationOperation
}

enum class AudioPreparationAvailability { NOT_INSPECTED, STALE, AVAILABLE, NOT_AUDIO }

/** A report-derived read model. It deliberately contains no absolute paths. */
data class AudioPreparationSnapshot(
    val partId: String,
    val availability: AudioPreparationAvailability,
    val report: InputInspectionReport? = null,
    val safeCleanupPlan: InputCleanupPlan? = null,
    val decodedWavAvailable: Boolean = false,
    val cleanWavAvailable: Boolean = false
)

data class AudioPreparationOperation(
    val project: ProjectSnapshot,
    val preparation: AudioPreparationSnapshot
)

class DefaultAudioPreparationApplicationService(
    private val projects: ProjectApplicationService,
    private val cleanup: InputCleanupApplicationService,
    private val transcription: TranscriptionQualityGateService
) : AudioPreparationApplicationService {
    override fun load(projectRoot: Path, partId: String): AudioPreparationSnapshot {
        val root = projectRoot.toAbsolutePath().normalize()
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        val part = project.parts.find { it.id == partId } ?: throw IllegalArgumentException("Part not found: $partId")
        if (!part.file.startsWith("source/")) return AudioPreparationSnapshot(partId, AudioPreparationAvailability.NOT_AUDIO)
        val source = root.resolve(part.file).normalize()
        if (!source.startsWith(root) || !Files.isRegularFile(source)) return AudioPreparationSnapshot(partId, AudioPreparationAvailability.STALE)
        val report = runCatching { InputInspectionReportStore.read(root, partId) }.getOrNull()
            ?: return AudioPreparationSnapshot(partId, AudioPreparationAvailability.NOT_INSPECTED)
        val current = runCatching { sourceFingerprint(source) }.getOrNull()
        if (report.source.relativePath != part.file || report.source.sha256 != current) {
            return AudioPreparationSnapshot(partId, AudioPreparationAvailability.STALE)
        }
        val safePlan = if (report.detectedInput.container == InputContainer.MIDI) null else
            DeterministicInputCleanupPlanner.select(report, InputCleanupMode.SAFE_CLEANUP)
                .takeIf { it.mode == InputCleanupMode.SAFE_CLEANUP }
        val decoded = projectWav(root, InputInspectionPaths.decodedWav(root, partId))
        val clean = report.cleanup?.output?.let { output ->
            val path = InputInspectionPaths.cleanWav(root, partId)
            projectWav(root, path) && runCatching { sourceFingerprint(path) == output.sha256 }.getOrDefault(false)
        } == true
        return AudioPreparationSnapshot(partId, AudioPreparationAvailability.AVAILABLE, report, safePlan, decoded, clean)
    }

    override suspend fun inspect(projectRoot: Path, partId: String, progress: ProgressSink): AudioPreparationOperation {
        val project = projects.inspectPart(InspectPartRequest(projectRoot, partId), progress)
        return AudioPreparationOperation(project, load(project.root, partId))
    }

    override suspend fun applyCleanup(
        projectRoot: Path,
        partId: String,
        mode: InputCleanupMode,
        confirmedSafeCleanup: Boolean
    ): AudioPreparationOperation {
        val before = load(projectRoot, partId)
        require(before.availability == AudioPreparationAvailability.AVAILABLE) { "Inspect the current source before choosing cleanup." }
        val report = checkNotNull(before.report)
        val plan = DeterministicInputCleanupPlanner.select(report, mode)
        cleanup.apply(ApplyInputCleanupRequest(projectRoot, partId, plan, confirmedSafeCleanup))
        val project = projects.open(projectRoot)
        return AudioPreparationOperation(project, load(project.root, partId))
    }

    override suspend fun transcribe(
        projectRoot: Path,
        partId: String,
        selectedInput: TranscriptionInputArtifact
    ): AudioPreparationOperation {
        val result = transcription.run(RunTranscriptionQualityGateRequest(projectRoot, partId, selectedInput))
        if (result is TranscriptionQualityGateResult.Failed) {
            throw IllegalStateException("Transcription stopped during ${result.stage.name.lowercase().replace('_', ' ')}.")
        }
        val project = projects.open(projectRoot)
        return AudioPreparationOperation(project, load(project.root, partId))
    }

    private fun sourceFingerprint(path: Path): String {
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

    private fun projectWav(root: Path, path: Path): Boolean = runCatching {
        path.startsWith(root) && Files.isRegularFile(path) &&
            Files.newInputStream(path).use { input ->
                val header = input.readNBytes(12)
                header.size == 12 && header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
                    header.copyOfRange(8, 12).decodeToString() == "WAVE"
            }
    }.getOrDefault(false)
}

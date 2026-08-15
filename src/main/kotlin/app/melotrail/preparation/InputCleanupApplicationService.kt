package app.melotrail.preparation

import app.melotrail.worker.AudioCleanupCommand
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerStatus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/** Worker-free seam for focused application-service tests. */
fun interface AudioCleanupBoundary {
    suspend fun cleanup(request: AudioCleanupRequest): AudioCleanupResult
}

data class AudioCleanupRequest(val input: Path, val output: Path, val operations: List<CleanupPlanOperation>)

data class AudioCleanupResult(
    val sampleRate: Int,
    val channels: Int,
    val frames: Long,
    val before: CleanupMetrics,
    val after: CleanupMetrics,
    val appliedOperations: List<CleanupOperationType>,
    val skippedOperations: List<CleanupOperationType>,
    val warnings: List<String>,
    val toolVersions: Map<String, String>
) {
    fun requireValid() = CleanupOutputArtifact(
        sha256 = "0".repeat(64), sampleRate = sampleRate, channels = channels, frames = frames,
        before = before, after = after, appliedOperations = appliedOperations, skippedOperations = skippedOperations,
        warnings = warnings, toolVersions = toolVersions
    ).requireValid()
}

/** The typed worker adapter accepts only the known Task 040 response schema. */
class WorkerAudioCleanupBoundary(private val workerClient: WorkerClient) : AudioCleanupBoundary {
    private val json = Json { ignoreUnknownKeys = false }

    override suspend fun cleanup(request: AudioCleanupRequest): AudioCleanupResult {
        request.operations.forEach { it.requireValid() }
        val response = workerClient.execute(AudioCleanupCommand(
            request.input.toString(), request.output.toString(), request.operations.map { it.asWorkerOperation() }
        ))
        require(response.status == WorkerStatus.COMPLETED) { "Audio cleanup worker did not complete." }
        val output = response.output?.let(::JsonObject) ?: throw IllegalStateException("Audio cleanup worker returned no output.")
        return try {
            val sampleRate = output["sampleRate"]!!.jsonPrimitive.content.toInt()
            val channels = output["channels"]!!.jsonPrimitive.content.toInt()
            val frames = output["frames"]!!.jsonPrimitive.content.toLong()
            AudioCleanupResult(
                sampleRate, channels, frames,
                metrics(output["before"]!!.jsonObject), metrics(output["after"]!!.jsonObject),
                operations(output["appliedOperations"]!!.jsonArray), operations(output["skippedOperations"]!!.jsonArray),
                output["warnings"]!!.jsonArray.map { it.jsonPrimitive.content },
                output["toolVersions"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
            ).also { it.requireValid() }
        } catch (error: Exception) {
            throw IllegalStateException("Audio cleanup worker returned invalid output.", error)
        }
    }

    private fun operations(values: kotlinx.serialization.json.JsonArray): List<CleanupOperationType> = values.map {
        CleanupOperationType.valueOf(it.jsonObject["type"]!!.jsonPrimitive.content.uppercase())
    }
    private fun metrics(value: JsonObject) = CleanupMetrics(
        value["peak"]!!.jsonPrimitive.content.toDouble(), value["rms"]!!.jsonPrimitive.content.toDouble(),
        value["dcOffset"]!!.jsonPrimitive.content.toDouble(), value["clippedRunCount"]!!.jsonPrimitive.content.toLong(),
        value["clippedFrameCount"]!!.jsonPrimitive.content.toLong(), value["maxFrameJump"]!!.jsonPrimitive.content.toDouble(),
        value["humConfidence"]!!.jsonPrimitive.content.toDouble(), value["noiseConfidence"]!!.jsonPrimitive.content.toDouble()
    )
}

data class ApplyInputCleanupRequest(
    val projectRoot: Path,
    val partId: String,
    val plan: InputCleanupPlan,
    val confirmedSafeCleanup: Boolean = false
)

data class InputCleanupApplyResult(val report: InputInspectionReport, val reused: Boolean)

/**
 * Publishes a verified `prepared/<part>/clean.wav` only after the worker output
 * and source fingerprint agree. The caller must explicitly confirm safe cleanup.
 */
class InputCleanupApplicationService(private val worker: AudioCleanupBoundary) {
    suspend fun apply(request: ApplyInputCleanupRequest): InputCleanupApplyResult = withContext(Dispatchers.IO) {
        val root = request.projectRoot.toAbsolutePath().normalize()
        val lock = locks.computeIfAbsent(root) { Mutex() }
        lock.withLock {
        request.plan.requireValid()
        InputInspectionPaths.requirePartId(request.partId)
        require(request.plan.partId == request.partId) { "Cleanup plan part ID does not match request." }
        if (request.plan.mode == InputCleanupMode.SAFE_CLEANUP) require(request.confirmedSafeCleanup) {
            "Safe cleanup requires explicit confirmation."
        }
        val report = InputInspectionReportStore.read(root, request.partId)
        require(report.source == request.plan.source) { "Cleanup plan is stale; inspect the source again." }
        val source = projectSource(root, report.source)
        val sourceHash = sha256(source)
        require(sourceHash == report.source.sha256) { "Inspection is stale; preserved source content changed." }
        require(report.detectedInput.container != InputContainer.MIDI) { "MIDI input cannot be audio-cleaned." }

        val clean = InputInspectionPaths.cleanWav(root, request.partId)
        val existing = report.cleanup
        if (existing?.plan == request.plan && existing.output != null && isCurrentClean(clean, existing.output)) {
            return@withLock InputCleanupApplyResult(report, reused = true)
        }
        if (request.plan.mode == InputCleanupMode.INSPECT_ONLY) {
            val updated = report.copy(cleanup = CleanupPlanRecord(request.plan), preparation = PreparationStatus.INSPECT_ONLY)
            InputInspectionReportStore.write(root, updated)
            return@withLock InputCleanupApplyResult(updated, reused = false)
        }

        require(report.detectedInput.container == InputContainer.RIFF_WAVE) { "Safe cleanup requires a decoded RIFF/WAVE input." }
        Files.createDirectories(clean.parent)
        val temporary = clean.resolveSibling(".${clean.fileName}.cleanup-${UUID.randomUUID()}.wav")
        try {
            val result = worker.cleanup(AudioCleanupRequest(source, temporary, request.plan.operations))
            result.requireValid()
            val artifact = readPcm24Wav(temporary)
            require(artifact.sampleRate == result.sampleRate && artifact.channels == result.channels && artifact.frames == result.frames) {
                "Audio cleanup worker output format does not match its metrics."
            }
            require(sha256(source) == sourceHash) { "Preserved source changed during cleanup." }
            atomicPublish(temporary, clean)
            val output = CleanupOutputArtifact(
                sha256 = sha256(clean), sampleRate = artifact.sampleRate, channels = artifact.channels, frames = artifact.frames,
                before = result.before, after = result.after, appliedOperations = result.appliedOperations,
                skippedOperations = result.skippedOperations, warnings = result.warnings, toolVersions = result.toolVersions
            ).also { it.requireValid() }
            val updated = report.copy(cleanup = CleanupPlanRecord(request.plan, output), preparation = PreparationStatus.CLEANED)
            InputInspectionReportStore.write(root, updated)
            InputCleanupApplyResult(updated, reused = false)
        } finally {
            Files.deleteIfExists(temporary)
        }
        }
    }

    private companion object {
        val locks = ConcurrentHashMap<Path, Mutex>()
    }

    private fun projectSource(root: Path, identity: InspectionSourceIdentity): Path {
        val path = root.resolve(identity.relativePath).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path)) { "Cleanup source is not a project-local file." }
        require(path.toRealPath().startsWith(root.toRealPath())) { "Cleanup source escapes the project through a symlink." }
        return path
    }

    private fun isCurrentClean(path: Path, output: CleanupOutputArtifact): Boolean = runCatching {
        val header = readPcm24Wav(path)
        header.sampleRate == output.sampleRate && header.channels == output.channels && header.frames == output.frames && sha256(path) == output.sha256
    }.getOrDefault(false)

    private fun atomicPublish(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publish is not supported for cleanup output.", error)
        }
    }
}

private data class Pcm24WavHeader(val sampleRate: Int, val channels: Int, val frames: Long)

private fun readPcm24Wav(path: Path): Pcm24WavHeader {
    val bytes = Files.readAllBytes(path)
    require(bytes.size >= 44 && String(bytes, 0, 4, StandardCharsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WAVE") {
        "Cleanup output is not a RIFF/WAVE file."
    }
    var position = 12; var sampleRate = 0; var channels = 0; var blockAlign = 0; var bits = 0; var dataSize = -1
    while (position + 8 <= bytes.size) {
        val id = String(bytes, position, 4, StandardCharsets.US_ASCII)
        val size = ByteBuffer.wrap(bytes, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        position += 8
        require(size >= 0 && position + size <= bytes.size) { "Cleanup output has an invalid WAV chunk." }
        if (id == "fmt ") {
            require(size >= 16) { "Cleanup output has an invalid WAV format chunk." }
            val format = ByteBuffer.wrap(bytes, position, 16).order(ByteOrder.LITTLE_ENDIAN)
            require(format.short.toInt() == 1) { "Cleanup output must be PCM WAV." }
            channels = format.short.toInt(); sampleRate = format.int; format.int; blockAlign = format.short.toInt(); bits = format.short.toInt()
        } else if (id == "data") dataSize = size
        position += size + (size and 1)
    }
    require(sampleRate in 1..384_000 && channels in 1..32 && bits == 24 && blockAlign == channels * 3 && dataSize > 0 && dataSize % blockAlign == 0) {
        "Cleanup output must be non-empty PCM-24 WAV with a valid frame layout."
    }
    return Pcm24WavHeader(sampleRate, channels, dataSize.toLong() / blockAlign)
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

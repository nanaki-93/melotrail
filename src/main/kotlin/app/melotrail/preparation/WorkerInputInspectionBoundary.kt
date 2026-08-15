package app.melotrail.preparation

import app.melotrail.worker.InputInspectionCommand
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files

/** Maps the worker's path-free measurement payload into the stable inspection contract. */
class WorkerInputInspectionBoundary(
    private val workerClient: WorkerClient
) : InputInspectionBoundary {
    private val json = Json { ignoreUnknownKeys = false }

    override suspend fun inspect(request: InputInspectionRequest): InputInspectionResult {
        request.requireValid()
        val root = request.projectRoot.toAbsolutePath().normalize()
        val source = root.resolve(request.source.relativePath).normalize()
        if (!source.startsWith(root) || !Files.isRegularFile(source) || runCatching { !source.toRealPath().startsWith(root.toRealPath()) }.getOrDefault(true)) {
            return InputInspectionResult.Rejected(InputInspectionError(InputInspectionErrorCode.INVALID_REQUEST, "Inspection source is not a project-local file."))
        }
        val response = workerClient.execute(InputInspectionCommand(source.toString()))
        if (response.status != WorkerStatus.COMPLETED) {
            return InputInspectionResult.Rejected(InputInspectionError(
                errorCode(response.error?.type),
                "Input inspection worker did not complete."
            ))
        }
        return try {
            val payload = json.decodeFromJsonElement(WorkerInspectionPayload.serializer(), response.output?.let(::JsonObject)
                ?: return InputInspectionResult.Rejected(InputInspectionError(InputInspectionErrorCode.INVALID_MEASUREMENT, "Input inspection worker returned no measurements.")))
            val report = InputInspectionReport(
                partId = request.partId,
                source = request.source,
                detectedInput = payload.detectedInput(),
                durationSeconds = payload.durationSeconds,
                audioFormat = payload.audioFormat,
                measurements = payload.measurements,
                warnings = payload.warnings,
                toolVersions = payload.toolVersions,
                preparation = payload.preparation
            )
            report.requireValid()
            InputInspectionResult.Inspected(report)
        } catch (_: Exception) {
            InputInspectionResult.Rejected(InputInspectionError(
                InputInspectionErrorCode.INVALID_MEASUREMENT,
                "Input inspection worker returned invalid measurements."
            ))
        }
    }

    private fun errorCode(type: String?): InputInspectionErrorCode = when (type) {
        "InputInspectionValidationError" -> InputInspectionErrorCode.INVALID_CONTAINER
        "InputInspectionDecodeError" -> InputInspectionErrorCode.DECODING_FAILED
        else -> InputInspectionErrorCode.MEASUREMENT_FAILED
    }
}

@Serializable
private data class WorkerInspectionPayload(
    val container: InputContainer,
    val codec: String,
    val extension: String,
    val durationSeconds: Double,
    val audioFormat: DetectedAudioFormat? = null,
    val measurements: AudioInspectionMeasurements? = null,
    val warnings: List<String> = emptyList(),
    val toolVersions: Map<String, String> = emptyMap(),
    val preparation: PreparationStatus = PreparationStatus.INSPECT_ONLY
) {
    fun detectedInput() = DetectedInput(container, codec, extension)
}

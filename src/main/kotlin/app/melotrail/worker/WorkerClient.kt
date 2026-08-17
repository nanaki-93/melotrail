package app.melotrail.worker

import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class WorkerRuntimeStatus(
    val reachable: Boolean,
    val transcriptionAvailable: Boolean,
    val version: String? = null,
    val mp3ExportAvailable: Boolean = false
)

/**
 * Small HTTP client for the standalone Python worker.
 *
 * There is no process management here: the Python worker is started separately
 * (for example with `make worker`). Each WorkerCommand maps directly to one
 * Python endpoint.
 */
class WorkerClient(
    private val baseUrl: String = "http://127.0.0.1:8081",
    private val timeout: Duration = 10.minutes,
    private val logger: Logger,
    private val errorReporter: ErrorReporter
) : AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true }
    private var lastJobId = 0L

    suspend fun execute(command: WorkerCommand): WorkerResponse = withContext(Dispatchers.IO) {
        val jobId = generateJobId()

        try {
            val endpoint = endpointFor(command)
            val body = buildRequest(command, jobId)
            val response = post(endpoint, body)

            if (response.code !in 200..299) {
                return@withContext WorkerResponse(
                    jobId = jobId,
                    status = WorkerStatus.ERROR,
                    error = errorFrom(response.body)
                        ?: WorkerError("HttpError", "Worker request failed with HTTP ${response.code}")
                )
            }

            json.decodeFromString<WorkerResponse>(response.body)
        } catch (e: Exception) {
            logger.error("WorkerClient", "Worker request failed: ${e.message}")
            WorkerResponse(
                jobId = jobId,
                status = WorkerStatus.ERROR,
                error = WorkerError("WorkerError", e.message ?: "Unknown error")
            )
        }
    }

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = request("GET", "/health", null)
            if (response.code !in 200..299) return@withContext false
            json.parseToJsonElement(response.body)
                .jsonObject["status"]?.jsonPrimitive?.content == "ok"
        } catch (_: Exception) {
            false
        }
    }

    /** Bounded health metadata for local readiness UI; it contains no paths or source content. */
    suspend fun runtimeStatus(): WorkerRuntimeStatus = withContext(Dispatchers.IO) {
        try {
            val response = request("GET", "/health", null)
            if (response.code !in 200..299) return@withContext WorkerRuntimeStatus(false, false)
            val body = json.parseToJsonElement(response.body).jsonObject
            val reachable = body["status"]?.jsonPrimitive?.content == "ok"
            val transcription = body["transcriptionRuntime"]?.jsonPrimitive?.booleanOrNull == true
            WorkerRuntimeStatus(
                reachable, transcription, body["version"]?.jsonPrimitive?.contentOrNull,
                body["mp3ExportRuntime"]?.jsonPrimitive?.booleanOrNull == true
            )
        } catch (_: Exception) {
            WorkerRuntimeStatus(false, false)
        }
    }

    /**
     * Kept as a compatibility helper. Starting/stopping is intentionally
     * outside the Kotlin process now.
     */
    suspend fun start(): Result<Unit> =
        if (healthCheck()) Result.success(Unit)
        else Result.failure(IllegalStateException("Python worker is not running at $baseUrl"))

    suspend fun stop(): Result<Unit> = Result.success(Unit)

    fun isRunning(): Boolean = false

    override fun close() = Unit

    internal fun endpointFor(command: WorkerCommand): String = when (command) {
        is AnalyzeCommand -> "/analyze"
        is ApplyDSPCommand -> "/apply_dsp"
        is RepairCommand -> "/repair"
        is MasterCommand -> "/master"
        is MP3ConvertCommand -> "/mp3_convert"
        is MP3ExportCommand -> "/mp3_export"
        is TranscribeCommand -> "/transcribe"
        is MidiCleanCommand -> "/midi-clean"
        is InputInspectionCommand -> "/inspect-input"
        is AudioCleanupCommand -> "/cleanup"
        is HealthCheck -> "/health"
    }

    internal fun buildRequest(command: WorkerCommand, jobId: String): JsonObject =
        buildJsonObject {
            put("jobId", jobId)
            when (command) {
                is AnalyzeCommand -> {
                    put("path", command.path)
                    put("options", buildJsonObject {
                        put("detectBPM", command.options.detectBPM)
                        put("detectKey", command.options.detectKey)
                        put("detectLoudness", command.options.detectLoudness)
                        put("detectOnsets", command.options.detectOnsets)
                        put("detectBeats", command.options.detectBeats)
                        put("detectSections", command.options.detectSections)
                    })
                }
                is ApplyDSPCommand -> {
                    put("path", command.path)
                    put("settings", json.encodeToJsonElement(command.settings))
                    command.outputFormat?.let { put("outputFormat", it) }
                }
                is RepairCommand -> {
                    put("path", command.path)
                    command.outputPath?.let { put("outputPath", it) }
                    put("repairs", buildJsonArray {
                        command.repairs.forEach { repair ->
                            add(buildJsonObject {
                                put("type", repair.type)
                                put("params", repair.params.toJson())
                            })
                        }
                    })
                }
                is MasterCommand -> {
                    put("path", command.path)
                    command.outputPath?.let { put("outputPath", it) }
                    put("settings", command.settings.toJson())
                }
                is MP3ConvertCommand -> {
                    put("path", command.path)
                    put("outputPath", command.outputPath)
                }
                is MP3ExportCommand -> {
                    put("path", command.path)
                    put("outputPath", command.outputPath)
                    put("bitrateKbps", command.bitrateKbps)
                }
                is TranscribeCommand -> {
                    put("path", command.path)
                    put("outputPath", command.outputPath)
                    put("instrument", command.instrument)
                }
                is MidiCleanCommand -> {
                    put("path", command.path)
                    put("outputPath", command.outputPath)
                    put("version", command.version)
                    put("profile", command.profile)
                    command.quantize?.let { put("quantize", it) }
                    put("strength", command.strength)
                    put("minNoteMs", command.minNoteMs)
                    put("minVelocity", command.minVelocity)
                    put("normalizeVelocity", command.normalizeVelocity)
                    put("cleanSustain", command.cleanSustain)
                }
                is InputInspectionCommand -> put("path", command.path)
                is AudioCleanupCommand -> {
                    put("path", command.path)
                    put("outputPath", command.outputPath)
                    put("operations", buildJsonArray {
                        command.operations.forEach { operation ->
                            add(buildJsonObject {
                                put("type", operation.wireType)
                                when (operation) {
                                    AudioCleanupOperation.DcRemoval -> Unit
                                    is AudioCleanupOperation.ClipRepair -> put("params", buildJsonObject { put("threshold", operation.threshold) })
                                    is AudioCleanupOperation.Declick -> put("params", buildJsonObject { put("threshold", operation.threshold) })
                                    is AudioCleanupOperation.HumRemoval -> put("params", buildJsonObject { put("frequencyHz", operation.frequencyHz) })
                                    is AudioCleanupOperation.NoiseReduction -> put("params", buildJsonObject { put("strength", operation.strength) })
                                }
                            })
                        }
                    })
                }
                is HealthCheck -> Unit
            }
        }

    private fun Map<String, Any>.toJson(): JsonObject = buildJsonObject {
        forEach { (key, value) ->
            put(key, value.toJsonElement())
        }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this.toString())
        is String -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject {
            this@toJsonElement.forEach { (key, value) ->
                if (key != null) put(key.toString(), value.toJsonElement())
            }
        }
        is Iterable<*> -> buildJsonArray { forEach { add(it.toJsonElement()) } }
        else -> JsonPrimitive(toString())
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun post(path: String, body: JsonObject): HttpResponse =
        request("POST", path, json.encodeToString(body))

    private fun errorFrom(body: String): WorkerError? = runCatching {
        val error = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject ?: return null
        val type = error["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val message = error["message"]?.jsonPrimitive?.contentOrNull ?: return null
        WorkerError(type, message)
    }.getOrNull()

    private fun request(method: String, path: String, body: String?): HttpResponse {
        val url = URI.create(baseUrl.trimEnd('/') + path).toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = timeout.inWholeMilliseconds.toInt()
        connection.readTimeout = timeout.inWholeMilliseconds.toInt()
        connection.setRequestProperty("Accept", "application/json")

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        return HttpResponse(code, responseBody)
    }

    private fun generateJobId(): String =
        "job-${System.currentTimeMillis()}-${lastJobId++}"
}

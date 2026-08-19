package app.melotrail.worker

import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

data class WorkerRuntimeStatus(
    val reachable: Boolean,
    val transcriptionAvailable: Boolean,
    val version: String? = null,
    val mp3ExportAvailable: Boolean = false
)

interface WorkerGateway {
    val unavailableMessage: String get() = "Python worker is not running"
    suspend fun execute(command: WorkerCommand): WorkerResponse
    suspend fun healthCheck(): Boolean
}

data class WorkerHttpResponse(val code: Int, val body: String)

interface WorkerHttpTransport {
    fun request(method: String, path: String, body: String?, timeout: Duration): WorkerHttpResponse
}

/**
 * Small HTTP client for the standalone Python worker.
 *
 * There is no process management here: the Python worker is started separately
 * (for example with `make worker`). Blocking HTTP is always dispatched to IO.
 */
class WorkerClient(
    private val baseUrl: String = "http://127.0.0.1:8081",
    private val timeout: Duration = 10.minutes,
    private val logger: Logger,
    private val errorReporter: ErrorReporter,
    private val transport: WorkerHttpTransport = UrlConnectionWorkerTransport(baseUrl)
) : WorkerGateway, AutoCloseable {

    private val json = Json { ignoreUnknownKeys = true }
    private val nextJobId = AtomicLong()

    override val unavailableMessage: String = "Python worker is not running at $baseUrl"

    override suspend fun execute(command: WorkerCommand): WorkerResponse = withContext(Dispatchers.IO) {
        val jobId = "job-${nextJobId.incrementAndGet()}"
        try {
            val response = transport.request(
                method = "POST",
                path = WorkerProtocol.endpointFor(command),
                body = json.encodeToString(WorkerProtocol.requestFor(command, jobId)),
                timeout = timeout
            )
            WorkerResponseMapper.fromHttp(jobId, response)
        } catch (exception: Exception) {
            logger.error("WorkerClient", "Worker request failed: ${exception.message}")
            WorkerResponse(
                jobId = jobId,
                status = WorkerStatus.ERROR,
                error = WorkerError("WorkerError", exception.message ?: "Unknown error")
            )
        }
    }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val response = transport.request("GET", "/health", null, timeout)
            response.code in 200..299 && json.parseToJsonElement(response.body)
                .jsonObject["status"]?.jsonPrimitive?.content == "ok"
        }.getOrDefault(false)
    }

    /** Bounded health metadata for local readiness UI; it contains no paths or source content. */
    suspend fun runtimeStatus(): WorkerRuntimeStatus = withContext(Dispatchers.IO) {
        runCatching {
            val response = transport.request("GET", "/health", null, timeout)
            if (response.code !in 200..299) return@runCatching WorkerRuntimeStatus(false, false)
            val body = json.parseToJsonElement(response.body).jsonObject
            WorkerRuntimeStatus(
                reachable = body["status"]?.jsonPrimitive?.content == "ok",
                transcriptionAvailable = body["transcriptionRuntime"]?.jsonPrimitive?.booleanOrNull == true,
                version = body["version"]?.jsonPrimitive?.contentOrNull,
                mp3ExportAvailable = body["mp3ExportRuntime"]?.jsonPrimitive?.booleanOrNull == true
            )
        }.getOrDefault(WorkerRuntimeStatus(false, false))
    }

    /** Capability negotiation is deliberately limited to the pinned Clean MIDI contract. */
    suspend fun supportsMidiCleanup(requestVersion: Int, profile: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val response = transport.request("GET", "/health", null, timeout)
            if (response.code !in 200..299) return@runCatching false
            val capability = json.parseToJsonElement(response.body).jsonObject["midiCleanup"]?.jsonObject ?: return@runCatching false
            capability["requestVersion"]?.jsonPrimitive?.longOrNull == requestVersion.toLong() &&
                capability["profiles"]?.jsonArray?.any { it.jsonPrimitive.contentOrNull == profile } == true
        }.getOrDefault(false)
    }

    override fun close() = Unit
}

internal object WorkerResponseMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromHttp(jobId: String, response: WorkerHttpResponse): WorkerResponse {
        if (response.code in 200..299) {
            return runCatching { json.decodeFromString<WorkerResponse>(response.body) }
                .getOrElse { exception ->
                    WorkerResponse(
                        jobId = jobId,
                        status = WorkerStatus.ERROR,
                        error = WorkerError("ResponseMappingError", exception.message ?: "Invalid worker response")
                    )
                }
        }

        val error = runCatching {
            val objectBody = json.parseToJsonElement(response.body).jsonObject
            val errorBody = objectBody["error"]?.jsonObject ?: return@runCatching null
            val type = errorBody["type"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val message = errorBody["message"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            WorkerError(type, message)
        }.getOrNull()

        return WorkerResponse(
            jobId = jobId,
            status = WorkerStatus.ERROR,
            error = error ?: WorkerError("HttpError", "Worker request failed with HTTP ${response.code}")
        )
    }
}

private class UrlConnectionWorkerTransport(private val baseUrl: String) : WorkerHttpTransport {
    override fun request(method: String, path: String, body: String?, timeout: Duration): WorkerHttpResponse {
        val connection = URI.create(baseUrl.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
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
        return WorkerHttpResponse(code, responseBody)
    }
}

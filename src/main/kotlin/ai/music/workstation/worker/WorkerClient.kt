package ai.music.workstation.worker

import ai.music.workstation.logging.Logger
import ai.music.workstation.errors.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class WorkerClient(
    private val baseUrl: String = "http://localhost:8081",
    private val timeout: Duration = 10.minutes,
    private val logger: Logger,
    private val errorReporter: ErrorReporter
) : AutoCloseable {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .writeTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

    private val json = workerJson

    private var isWorkerStarted: Boolean = false

    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isWorkerStarted) {
                logger.warning("WorkerClient", "Worker already running")
                return@runCatching
            }

            val request = Request.Builder()
                .url("$baseUrl/api/worker/start")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    isWorkerStarted = true
                    logger.info("WorkerClient", "Worker started successfully at $baseUrl")
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw IllegalStateException("Worker start failed: $errorBody")
                }
            }
        }
    }

    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isWorkerStarted) {
                logger.warning("WorkerClient", "Worker is not running")
                return@runCatching
            }

            val request = Request.Builder()
                .url("$baseUrl/api/worker/stop")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    isWorkerStarted = false
                    logger.info("WorkerClient", "Worker stopped")
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    throw IllegalStateException("Worker stop failed: $errorBody")
                }
            }
        }
    }

    suspend fun execute(command: WorkerCommand): WorkerResponse = withContext(Dispatchers.IO) {
        if (!isWorkerStarted) {
            val error = WorkerError("WorkerNotRunning", "Worker is not running")
            return@withContext WorkerResponse(
                jobId = "",
                status = WorkerStatus.ERROR,
                error = error
            )
        }

        val request = WorkerRequest(
            command = command.commandName,
            jobId = generateJobId(),
            input = buildCommandInput(command),
            params = emptyMap()
        )

        val jsonStr = json.encodeToString(WorkerRequest.serializer(), request)

        val body = jsonStr.toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url("$baseUrl/api/worker/command")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response")
            json.decodeFromString<WorkerResponse>(responseBody)
        }
    }

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/worker/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isRunning(): Boolean = isWorkerStarted

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    private fun buildCommandInput(command: WorkerCommand): Map<String, String> {
        return when (command) {
            is AnalyzeCommand -> mapOf(
                "path" to command.path,
                "options" to command.options.toString()
            )
            is ApplyDSPCommand -> mapOf(
                "path" to command.path,
                "settings" to command.settings.toString(),
                "outputFormat" to (command.outputFormat ?: "")
            )
            is RepairCommand -> mapOf(
                "path" to command.path,
                "repairs" to command.repairs.toString(),
                "outputPath" to (command.outputPath ?: "")
            )
            is MasterCommand -> mapOf(
                "path" to command.path,
                "settings" to command.settings.toString(),
                "outputPath" to (command.outputPath ?: "")
            )
            is MP3ConvertCommand -> mapOf(
                "path" to command.path,
                "output_path" to command.outputPath
            )
            is HealthCheck -> emptyMap()
            else -> emptyMap()
        }
    }

    private fun generateJobId(): String = "job-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt(10000)}"
}

package app.melotrail.queue

import app.melotrail.logging.Logger
import app.melotrail.worker.WorkerClient
import app.melotrail.worker.WorkerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

interface QueueStore {
    fun save(jobs: List<Job>)
    fun load(): List<Job>
}

class FileQueueStore(private val queuePath: Path) : QueueStore {
    private val json = Json { ignoreUnknownKeys = true }

    init {
        Files.createDirectories(queuePath.parent)
    }

    override fun save(jobs: List<Job>) {
        val serializer = serializer<List<Job>>()
        Files.writeString(queuePath, json.encodeToString(serializer, jobs))
    }

    override fun load(): List<Job> {
        if (!Files.exists(queuePath)) return emptyList()
        return try {
            val serializer = serializer<List<Job>>()
            json.decodeFromString(serializer, Files.readString(queuePath))
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class ProcessingQueue(
    private val workerClient: WorkerClient,
    private val queueStore: QueueStore,
    private val logger: Logger
) {
    private val _jobs = MutableStateFlow<List<Job>>(queueStore.load())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private var processingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    val queueSize: Int
        get() = _jobs.value.count { it.status == JobStatus.QUEUED || it.status == JobStatus.RUNNING }

    suspend fun addJob(job: Job): Job {
        val updated = _jobs.value + job
        _jobs.value = updated
        queueStore.save(updated)
        return job
    }

    suspend fun cancelJob(jobId: String): Boolean {
        val updated = _jobs.value.map {
            if (it.id == jobId && it.status == JobStatus.RUNNING) {
                it.copy(status = JobStatus.CANCELLED, completedAt = Clock.System.now().toString())
            } else {
                it
            }
        }
        _jobs.value = updated
        queueStore.save(updated)
        return true
    }

    suspend fun retryJob(jobId: String): Boolean {
        val updated = _jobs.value.map {
            if (it.id == jobId && it.status == JobStatus.FAILED && it.canRetry()) {
                it.copy(status = JobStatus.PENDING, retryCount = it.retryCount + 1, progress = 0.0)
            } else {
                it
            }
        }
        _jobs.value = updated
        queueStore.save(updated)
        return true
    }

    suspend fun clearCompleted(): Int {
        val completed = _jobs.value.count { it.status == JobStatus.COMPLETED }
        val updated = _jobs.value.filter { it.status != JobStatus.COMPLETED }
        _jobs.value = updated
        queueStore.save(updated)
        return completed
    }

    fun startProcessing() {
        _isProcessing.value = true
        scope.launch { processNextJob() }
    }

    suspend fun stopProcessing() {
        _isProcessing.value = false
    }

    fun getJob(jobId: String): Job? = _jobs.value.find { it.id == jobId }

    fun getJobsByStatus(status: JobStatus): List<Job> = _jobs.value.filter { it.status == status }

    fun getPendingJobs(): List<Job> = _jobs.value.filter { it.status == JobStatus.PENDING || it.status == JobStatus.QUEUED }

    private suspend fun processNextJob() {
        val pending = getPendingJobs().firstOrNull() ?: run {
            _isProcessing.value = false
            return
        }

        processingJob = pending
        val updated = _jobs.value.map {
            if (it.id == pending.id) it.copy(status = JobStatus.RUNNING, startedAt = Clock.System.now().toString())
            else it
        }
        _jobs.value = updated

        logger.info("ProcessingQueue", "Processing job: ${pending.id}")

        try {
            // Execute via worker client
            // val response = workerClient.execute(/* command */)
            // onJobComplete(pending.id, response)
        } catch (e: Exception) {
            onJobError(pending.id, e.message ?: "Unknown error")
        }
    }

    private fun onJobProgress(jobId: String, progress: Double) {
        val updated = _jobs.value.map {
            if (it.id == jobId) it.copy(progress = progress)
            else it
        }
        _jobs.value = updated
    }

    private fun onJobComplete(jobId: String, response: WorkerResponse) {
        val output = response.output?.mapValues { (k, v) -> v.toString() } ?: emptyMap()
        val updated = _jobs.value.map {
            if (it.id == jobId) it.withResult(output)
            else it
        }
        _jobs.value = updated
        queueStore.save(updated)
        scope.launch { processNextJob() }
    }

    private fun onJobError(jobId: String, error: String) {
        val updated = _jobs.value.map {
            if (it.id == jobId) it.withError(error)
            else it
        }
        _jobs.value = updated
        queueStore.save(updated)
        scope.launch { processNextJob() }
    }
}

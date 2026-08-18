package app.melotrail.worker

import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.DefaultLogger
import app.melotrail.server.config.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

@Serializable
enum class WorkerJobStatus {
    @SerialName("QUEUED") QUEUED,
    @SerialName("RUNNING") RUNNING,
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("FAILED") FAILED,
    @SerialName("CANCELLED") CANCELLED
}

@Serializable
enum class WorkerJobType {
    @SerialName("ANALYZE") ANALYZE,
    @SerialName("APPLY_DSP") APPLY_DSP,
    @SerialName("REPAIR") REPAIR,
    @SerialName("MASTER") MASTER,
    @SerialName("MP3_CONVERT") MP3_CONVERT,
    @SerialName("MP3_EXPORT") MP3_EXPORT,
    @SerialName("TRANSCRIBE") TRANSCRIBE,
    @SerialName("MIDI_CLEAN") MIDI_CLEAN,
    @SerialName("INSPECT_INPUT") INSPECT_INPUT,
    @SerialName("AUDIO_CLEANUP") AUDIO_CLEANUP
}

@Serializable
data class WorkerJob(
    @SerialName("id") val id: String,
    @SerialName("type") val type: WorkerJobType,
    @SerialName("status") val status: WorkerJobStatus = WorkerJobStatus.QUEUED,
    @SerialName("result") val result: Map<String, String>? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("progress") val progress: Double = 0.0,
    @SerialName("createdAt") val createdAt: String = Clock.System.now().toString(),
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("completedAt") val completedAt: String? = null
)

data class WorkerJobProgress(
    val jobId: String,
    val status: WorkerJobStatus,
    val progress: Double,
    val message: String? = null
)

/** Thread-safe, in-memory lifecycle record for local Python-worker requests. */
@Component
class WorkerJobQueue {
    private val jobs = mutableListOf<WorkerJob>()
    private val lock = Any()

    fun add(job: WorkerJob): WorkerJob = synchronized(lock) { jobs.add(job); job }

    fun get(jobId: String): WorkerJob? = synchronized(lock) { jobs.find { it.id == jobId } }

    fun update(jobId: String, update: (WorkerJob) -> WorkerJob): WorkerJob? = synchronized(lock) {
        val index = jobs.indexOfFirst { it.id == jobId }
        if (index < 0) null else update(jobs[index]).also { jobs[index] = it }
    }

    fun recent(limit: Int): List<WorkerJob> = synchronized(lock) {
        jobs.sortedByDescending { it.createdAt }.take(limit.coerceAtLeast(0))
    }
}

/**
 * Kotlin application boundary for Python-worker health and submitted work.
 * It never starts, stops, or otherwise supervises the Python process.
 */
@Service
class WorkerJobService internal constructor(
    private val workerClient: WorkerGateway,
    private val queue: WorkerJobQueue,
    private val scope: CoroutineScope
) {
    private val nextJobId = AtomicLong()

    @Autowired
    constructor(config: ServerConfig, queue: WorkerJobQueue) : this(
        workerClient = WorkerClient(
            config.workerBaseUrl,
            logger = DefaultLogger(),
            errorReporter = ErrorReporter(DefaultLogger())
        ),
        queue = queue,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    )

    fun isHealthy(): Boolean = runBlocking { workerClient.healthCheck() }

    fun availability(): Result<Unit> =
        if (isHealthy()) Result.success(Unit)
        else Result.failure(IllegalStateException(workerClient.unavailableMessage))

    fun submit(command: WorkerCommand): WorkerJob {
        val job = queue.add(WorkerJob(
            id = "worker-job-${nextJobId.incrementAndGet()}",
            type = command.jobType()
        ))
        scope.launch { execute(job.id, command) }
        return job
    }

    fun cancel(jobId: String): WorkerJob? = queue.update(jobId) { job ->
        if (job.status in setOf(WorkerJobStatus.QUEUED, WorkerJobStatus.RUNNING)) {
            job.copy(status = WorkerJobStatus.CANCELLED, progress = 1.0, completedAt = now())
        } else job
    }

    fun get(jobId: String): WorkerJob? = queue.get(jobId)
    fun recent(limit: Int): List<WorkerJob> = queue.recent(limit)

    fun progress(jobId: String): WorkerJobProgress? = get(jobId)?.let { job ->
        WorkerJobProgress(job.id, job.status, job.progress, job.error)
    }

    private suspend fun execute(jobId: String, command: WorkerCommand) {
        val running = queue.update(jobId) { job ->
            if (job.status == WorkerJobStatus.CANCELLED) job
            else job.copy(status = WorkerJobStatus.RUNNING, progress = 0.1, startedAt = now())
        } ?: return
        if (running.status == WorkerJobStatus.CANCELLED) return

        val response = workerClient.execute(command)
        queue.update(jobId) { job ->
            if (job.status == WorkerJobStatus.CANCELLED) return@update job
            when (response.status) {
                WorkerStatus.COMPLETED, WorkerStatus.OK -> job.copy(
                    status = WorkerJobStatus.COMPLETED,
                    progress = 1.0,
                    result = response.output?.mapValues { (_, value) -> value.toString() }.orEmpty(),
                    completedAt = now()
                )
                WorkerStatus.CANCELLED -> job.copy(
                    status = WorkerJobStatus.CANCELLED,
                    progress = 1.0,
                    completedAt = now()
                )
                WorkerStatus.IN_PROGRESS -> job.copy(
                    status = WorkerJobStatus.RUNNING,
                    progress = response.progress?.coerceIn(0.0, 1.0) ?: job.progress
                )
                WorkerStatus.ERROR -> job.copy(
                    status = WorkerJobStatus.FAILED,
                    progress = 1.0,
                    error = response.error?.message ?: "Worker command failed",
                    completedAt = now()
                )
            }
        }
    }

    private fun WorkerCommand.jobType(): WorkerJobType = when (this) {
        is AnalyzeCommand -> WorkerJobType.ANALYZE
        is ApplyDSPCommand -> WorkerJobType.APPLY_DSP
        is RepairCommand -> WorkerJobType.REPAIR
        is MasterCommand -> WorkerJobType.MASTER
        is MP3ConvertCommand -> WorkerJobType.MP3_CONVERT
        is MP3ExportCommand -> WorkerJobType.MP3_EXPORT
        is TranscribeCommand -> WorkerJobType.TRANSCRIBE
        is MidiCleanCommand -> WorkerJobType.MIDI_CLEAN
        is InputInspectionCommand -> WorkerJobType.INSPECT_INPUT
        is AudioCleanupCommand -> WorkerJobType.AUDIO_CLEANUP
    }

    private fun now(): String = Clock.System.now().toString()
}

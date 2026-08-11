package ai.music.workstation.server.service

import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.logging.Logger
import ai.music.workstation.queue.Job
import ai.music.workstation.queue.JobStatus
import ai.music.workstation.queue.JobType
import ai.music.workstation.server.config.ServerConfig
import ai.music.workstation.worker.*
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

@Service
class WorkerService(
    private val config: ServerConfig,
    private val processingQueue: ProcessingQueueSimple
) {
    private val logger: Logger = DefaultLogger()
    private val errorReporter = ErrorReporter(logger)
    private val workerClient = AtomicReference<WorkerClient?>(null)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startWorker(): Result<Unit> = runBlocking {
        if (workerClient.get()?.isRunning() == true) {
            Result.failure(Exception("Worker is already running"))
        } else {
            val client = WorkerClient(config.workerBaseUrl, 10.minutes, logger, errorReporter)
            client.start()
            workerClient.set(client)
            Result.success(Unit)
        }
    }

    fun stopWorker(): Result<Unit> = runBlocking {
        workerClient.getAndSet(null)?.let { it.stop(); it.close() }
        Result.success(Unit)
    }

    fun isHealthy() = workerClient.get()?.isRunning() == true

    fun <T : WorkerCommand> submitCommand(command: T): Job {
        val job = Job(
            id = "job-${System.currentTimeMillis()}-${(0..9999).random()}",
            type = when (command) {
                is AnalyzeCommand -> JobType.ANALYZE
                is ApplyDSPCommand -> JobType.APPLY_DSP
                is RepairCommand, is MasterCommand -> JobType.CUSTOM
                else -> JobType.CUSTOM
            },
            status = JobStatus.QUEUED,
            request = when (command) {
                is AnalyzeCommand -> mapOf("path" to command.path)
                is ApplyDSPCommand -> mapOf("path" to command.path)
                is RepairCommand -> mapOf("path" to command.path)
                is MasterCommand -> mapOf("path" to command.path)
                else -> emptyMap()
            }
        )
        processingQueue.addJob(job)
        scope.launch { executeJob(job, command) }
        return job
    }

    private suspend fun <T : WorkerCommand> executeJob(job: Job, command: T) {
        processingQueue.updateJob(job.id) {
            it.copy(status = JobStatus.RUNNING, startedAt = kotlinx.datetime.Clock.System.now().toString())
        }
        try {
            val client = workerClient.get() ?: throw Exception("Worker is not running")
            val response = client.execute(command)
            val finalStatus = when (response.status) {
                WorkerStatus.COMPLETED -> JobStatus.COMPLETED
                WorkerStatus.ERROR -> JobStatus.FAILED
                WorkerStatus.CANCELLED -> JobStatus.CANCELLED
                else -> JobStatus.FAILED
            }
            processingQueue.updateJob(job.id) {
                it.copy(
                    status = finalStatus,
                    error = response.error?.message,
                    completedAt = kotlinx.datetime.Clock.System.now().toString()
                )
            }
        } catch (e: Exception) {
            processingQueue.updateJob(job.id) {
                it.copy(status = JobStatus.FAILED, error = e.message,
                    completedAt = kotlinx.datetime.Clock.System.now().toString())
            }
        }
    }

    fun getJob(jobId: String): Job? = processingQueue.getJob(jobId)
    fun getRecentJobs(limit: Int): List<Job> = processingQueue.getRecentJobs(limit)
}

class ProcessingQueueSimple {
    private val jobs = mutableListOf<Job>()
    private val lock = Any()

    fun addJob(job: Job): Job = synchronized(lock) { jobs.add(job); job }
    fun getJob(jobId: String): Job? = synchronized(lock) { jobs.find { it.id == jobId } }
    fun updateJob(jobId: String, update: (Job) -> Job): Job? = synchronized(lock) {
        val index = jobs.indexOfFirst { it.id == jobId }
        if (index >= 0) jobs[index] = update(jobs[index])
        if (index >= 0) jobs[index] else null
    }
    fun getRecentJobs(limit: Int): List<Job> = synchronized(lock) {
        jobs.sortedByDescending { it.createdAt }.take(limit)
    }
}

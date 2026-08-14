package ai.music.workstation.server.service

import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.logging.Logger
import ai.music.workstation.queue.Job
import ai.music.workstation.queue.JobStatus
import ai.music.workstation.queue.JobType
import ai.music.workstation.worker.*
import kotlinx.coroutines.*
import org.springframework.stereotype.Service

@Service
class WorkerService(
    private val config: ai.music.workstation.server.config.ServerConfig
) {
    private val logger: Logger = DefaultLogger()
    private val errorReporter = ErrorReporter(logger)
    private val workerClient = WorkerClient(config.workerBaseUrl, logger = logger, errorReporter = errorReporter)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processingQueue: ProcessingQueueSimple = ProcessingQueueSimple()

    fun startWorker(): Result<Unit> = runBlocking {
        workerClient.start()
    }

    fun stopWorker(): Result<Unit> = runBlocking {
        workerClient.stop()
    }

    fun isHealthy(): Boolean = runBlocking {
        workerClient.healthCheck()
    }

    fun <T : WorkerCommand> submitCommand(command: T): Job {
        val job = Job(
            id = "job-${System.currentTimeMillis()}-${(0..9999).random()}",
            type = when (command) {
                is AnalyzeCommand -> JobType.ANALYZE
                is ApplyDSPCommand -> JobType.APPLY_DSP
                else -> JobType.CUSTOM
            },
            status = JobStatus.QUEUED,
            request = mapOf("path" to commandPath(command))
        )
        processingQueue.addJob(job)
        scope.launch { executeJob(job, command) }
        return job
    }

    private suspend fun <T : WorkerCommand> executeJob(job: Job, command: T) {
        processingQueue.updateJob(job.id) {
            it.copy(status = JobStatus.RUNNING, startedAt = now(), progress = 0.1)
        }

        try {
            val response = workerClient.execute(command)
            if (response.status == WorkerStatus.COMPLETED) {
                val result = response.output?.mapValues { (_, value) -> value.toString() } ?: emptyMap()
                processingQueue.updateJob(job.id) {
                    it.copy(
                        status = JobStatus.COMPLETED,
                        progress = 1.0,
                        result = result,
                        completedAt = now()
                    )
                }
            } else {
                processingQueue.updateJob(job.id) {
                    it.copy(
                        status = JobStatus.FAILED,
                        progress = 1.0,
                        error = response.error?.message ?: "Worker command failed",
                        completedAt = now()
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("WorkerService", "Worker execution failed: ${e.message}")
            processingQueue.updateJob(job.id) {
                it.copy(
                    status = JobStatus.FAILED,
                    progress = 1.0,
                    error = e.message ?: "Worker execution failed",
                    completedAt = now()
                )
            }
        }
    }

    fun getJob(jobId: String): Job? = processingQueue.getJob(jobId)
    fun getRecentJobs(limit: Int): List<Job> = processingQueue.getRecentJobs(limit)

    private fun commandPath(command: WorkerCommand): String = when (command) {
        is AnalyzeCommand -> command.path
        is ApplyDSPCommand -> command.path
        is RepairCommand -> command.path
        is MasterCommand -> command.path
        is MP3ConvertCommand -> command.path
        is MP3ExportCommand -> command.path
        is TranscribeCommand -> command.path
        is MidiCleanCommand -> command.path
        is InputInspectionCommand -> command.path
        is HealthCheck -> ""
    }

    private fun now(): String = kotlinx.datetime.Clock.System.now().toString()
}

class ProcessingQueueSimple {
    private val jobs = mutableListOf<Job>()
    private val lock = Any()

    fun addJob(job: Job): Job = synchronized(lock) {
        jobs.add(job)
        job
    }

    fun getJob(jobId: String): Job? = synchronized(lock) {
        jobs.find { it.id == jobId }
    }

    fun updateJob(jobId: String, update: (Job) -> Job): Job? = synchronized(lock) {
        val index = jobs.indexOfFirst { it.id == jobId }
        if (index >= 0) jobs[index] = update(jobs[index])
        if (index >= 0) jobs[index] else null
    }

    fun getRecentJobs(limit: Int): List<Job> = synchronized(lock) {
        jobs.sortedByDescending { it.createdAt }.take(limit)
    }
}

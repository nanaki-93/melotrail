package app.melotrail.server.service

import app.melotrail.worker.WorkerJobProgress
import app.melotrail.worker.WorkerJobService
import app.melotrail.worker.WorkerJobStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Service
/** Spring SSE adapter over worker-owned job progress. */
class WorkerProgressService(private val workerJobs: WorkerJobService) {
    private val emitters = ConcurrentHashMap<String, MutableSet<SseEmitter>>()

    fun subscribe(jobId: String, emitter: SseEmitter) {
        emitters.computeIfAbsent(jobId) { ConcurrentHashMap.newKeySet() }.add(emitter)
        emitter.onCompletion { remove(jobId, emitter) }
        emitter.onTimeout { remove(jobId, emitter) }
        emitter.onError { remove(jobId, emitter) }
        send(jobId, emitter)
    }

    @Scheduled(fixedDelay = 500)
    fun poll() {
        emitters.keys.toList().forEach { jobId ->
            val job = workerJobs.progress(jobId) ?: run { cleanup(jobId); return@forEach }
            val dead = mutableListOf<SseEmitter>()
            emitters[jobId]?.forEach { emitter ->
                try {
                    emitter.send(SseEmitter.event().name("progress").data(progress(job)))
                } catch (_: IOException) {
                    dead += emitter
                }
            }
            dead.forEach { remove(jobId, it) }
            if (job.status in setOf(WorkerJobStatus.COMPLETED, WorkerJobStatus.FAILED, WorkerJobStatus.CANCELLED)) {
                cleanup(jobId)
            }
        }
    }

    private fun send(jobId: String, emitter: SseEmitter) {
        workerJobs.progress(jobId)?.let {
            try { emitter.send(SseEmitter.event().name("progress").data(progress(it))) }
            catch (_: IOException) { remove(jobId, emitter) }
        }
    }

    private fun progress(job: WorkerJobProgress) = mapOf(
        "jobId" to job.jobId,
        "status" to job.status.name.lowercase(),
        "progress" to job.progress.toFloat(),
        "message" to job.message
    )

    private fun remove(jobId: String, emitter: SseEmitter) {
        emitters[jobId]?.remove(emitter)
        if (emitters[jobId]?.isEmpty() == true) emitters.remove(jobId)
    }

    private fun cleanup(jobId: String) {
        emitters.remove(jobId)?.forEach { it.complete() }
    }
}

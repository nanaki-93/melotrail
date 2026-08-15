package app.melotrail.server.api

import app.melotrail.model.DSPSettings
import app.melotrail.server.dto.*
import app.melotrail.server.service.WorkerProgressService
import app.melotrail.server.service.WorkerService
import app.melotrail.worker.*
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/worker")
class WorkerController(
    private val workerService: WorkerService,
    private val progressService: WorkerProgressService
) {
    @GetMapping("/health")
    fun health() = mapOf(
        "status" to if (workerService.isHealthy()) "healthy" else "unhealthy",
        "workerRunning" to workerService.isHealthy()
    )

    // The Python worker is intentionally managed outside Spring.
    // These endpoints remain harmless compatibility endpoints.
    @PostMapping("/start")
    fun start() = workerService.startWorker().fold(
        onSuccess = { ResponseEntity.ok(mapOf("message" to "Python worker is available")) },
        onFailure = { ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to (it.message ?: "Worker unavailable"))) }
    )

    @PostMapping("/stop")
    fun stop() = ResponseEntity.ok(mapOf("message" to "Worker lifecycle is managed by the Python server"))

    @PostMapping("/analyze")
    fun analyze(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> =
        submit(AnalyzeCommand(
            path = requirePath(request),
            options = AnalyzeOptions()
        ))

    @PostMapping("/apply_dsp")
    fun applyDsp(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> =
        submit(ApplyDSPCommand(
            path = requirePath(request),
            settings = request.dspSettings ?: DSPSettings(),
            outputFormat = request.outputFormat
        ))

    @PostMapping("/repair")
    fun repair(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> =
        submit(RepairCommand(
            path = requirePath(request),
            repairs = request.repairs.orEmpty().map { RepairSpec(it.type, it.params) },
            outputPath = request.outputPath
        ))

    @PostMapping("/master")
    fun master(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> =
        submit(MasterCommand(
            path = requirePath(request),
            settings = request.settings.orEmpty(),
            outputPath = request.outputPath
        ))

    @PostMapping("/mp3_convert")
    fun mp3Convert(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> =
        submit(MP3ConvertCommand(
            path = requirePath(request),
            outputPath = request.outputPath ?: throw IllegalArgumentException("Missing outputPath")
        ))

    /**
     * Backwards-compatible control endpoint. Real worker commands now have
     * dedicated endpoints above.
     */
    @PostMapping("/command")
    fun command(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> =
        when (request.command) {
            "pause" -> ResponseEntity.ok(WorkerCommandResponse(true, "Job paused", request.jobId))
            "resume" -> ResponseEntity.ok(WorkerCommandResponse(true, "Job resumed", request.jobId))
            "cancel" -> ResponseEntity.ok(WorkerCommandResponse(true, "Job cancelled", request.jobId))
            else -> ResponseEntity.badRequest().body(
                WorkerCommandResponse(false, "Unknown control command: ${request.command}")
            )
        }

    @GetMapping("/job/{jobId}")
    fun getJob(@PathVariable jobId: String): ResponseEntity<Any> =
        workerService.getJob(jobId)?.let { ResponseEntity.ok<Any>(JobDTO.fromJob(it)) }
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Job not found"))

    @GetMapping("/job/{jobId}/progress", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun progress(@PathVariable jobId: String): ResponseEntity<SseEmitter> {
        if (workerService.getJob(jobId) == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        val emitter = SseEmitter(0L)
        progressService.subscribe(jobId, emitter)
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .body(emitter)
    }

    @GetMapping("/jobs")
    fun recentJobs(@RequestParam(defaultValue = "10") limit: Int) =
        workerService.getRecentJobs(limit).map { JobDTO.fromJob(it) }

    private fun submit(command: WorkerCommand): ResponseEntity<Any> {
        val job = workerService.submitCommand(command)
        return ResponseEntity.accepted().body(
            mapOf("jobId" to job.id, "status" to job.status.name.lowercase())
        )
    }

    private fun requirePath(request: WorkerCommandRequest): String =
        request.path ?: request.projectId ?: throw IllegalArgumentException("Missing path")
}

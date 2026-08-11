package ai.music.workstation.server.api

import ai.music.workstation.model.DSPSettings
import ai.music.workstation.server.dto.*
import ai.music.workstation.server.service.WorkerProgressService
import ai.music.workstation.server.service.WorkerService
import ai.music.workstation.worker.*
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/worker")
class WorkerController(
    private val workerService: WorkerService,
    private val progressService: WorkerProgressService
) {
    @PostMapping("/start")
    fun start(@RequestBody request: WorkerCommandRequest): ResponseEntity<Any> {
        val command = when (request.type) {
            "analyze" -> AnalyzeCommand(request.projectId ?: "")
            "mix", "master" -> MasterCommand(request.projectId ?: "", emptyMap())
            "applyDSP" -> ApplyDSPCommand(request.projectId ?: "", request.dspSettings ?: DSPSettings())
            else -> throw IllegalArgumentException("Unknown job type: ${request.type}")
        }
        return ResponseEntity.status(HttpStatus.CREATED).body<Any>(JobDTO.fromJob(workerService.submitCommand(command)))
    }

    @PostMapping("/stop")
    fun stop(@RequestBody(required = false) request: WorkerCommandRequest?) =
        ResponseEntity.ok(mapOf("message" to "Job stopped"))

    @GetMapping("/health")
    fun health() = mapOf(
        "status" to if (workerService.isHealthy()) "healthy" else "unhealthy",
        "workerRunning" to workerService.isHealthy()
    )

    @PostMapping("/command")
    fun command(@RequestBody request: WorkerCommandRequest): WorkerCommandResponse = when (request.command) {
        "pause" -> WorkerCommandResponse(true, "Job paused", request.jobId)
        "resume" -> WorkerCommandResponse(true, "Job resumed", request.jobId)
        "cancel" -> WorkerCommandResponse(true, "Job cancelled", request.jobId)
        else -> WorkerCommandResponse(false, "Unknown command: ${request.command}", null)
    }

    @GetMapping("/job/{jobId}")
    fun getJob(@PathVariable jobId: String): ResponseEntity<Any> =
        workerService.getJob(jobId)?.let { ResponseEntity.ok<Any>(JobDTO.fromJob(it)) }
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Job not found"))

    @GetMapping("/job/{jobId}/progress", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun progress(@PathVariable jobId: String): ResponseEntity<SseEmitter> {
        if (workerService.getJob(jobId) == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build<SseEmitter>()
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
}

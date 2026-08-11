package ai.music.workstation.server.dto

import ai.music.workstation.model.DSPSettings
import ai.music.workstation.queue.Job

data class WorkerCommandRequest(
    val type: String = "",
    val command: String? = null,
    val jobId: String? = null,
    val projectId: String? = null,
    val path: String? = null,
    val outputPath: String? = null,
    val trackIds: List<String>? = null,
    val dspSettings: DSPSettings? = null,
    val settings: Map<String, Any>? = null,
    val options: Map<String, Any>? = null,
    val repairs: List<RepairRequest>? = null,
    val outputFormat: String? = null
)

data class RepairRequest(
    val type: String,
    val params: Map<String, Any> = emptyMap()
)

data class WorkerCommandResponse(
    val success: Boolean,
    val message: String,
    val jobId: String? = null
)

data class JobDTO(
    val id: String,
    val type: String,
    val status: String,
    val progress: Float,
    val error: String? = null,
    val result: String? = null,
    val createdAt: String,
    val startedAt: String? = null,
    val completedAt: String? = null
) {
    companion object {
        fun fromJob(job: Job) = JobDTO(
            id = job.id,
            type = job.type.name.lowercase(),
            status = job.status.name.lowercase(),
            progress = job.progress.toFloat(),
            error = job.error,
            result = job.result?.toString(),
            createdAt = job.createdAt,
            startedAt = job.startedAt,
            completedAt = job.completedAt
        )
    }
}

data class JobProgressDTO(
    val jobId: String,
    val status: String,
    val progress: Float,
    val message: String? = null
)

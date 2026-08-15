package app.melotrail.queue

import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JobStatus {
    @SerialName("PENDING") PENDING,
    @SerialName("QUEUED") QUEUED,
    @SerialName("RUNNING") RUNNING,
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("FAILED") FAILED,
    @SerialName("CANCELLED") CANCELLED
}

@Serializable
enum class JobType {
    @SerialName("ANALYZE") ANALYZE,
    @SerialName("APPLY_DSP") APPLY_DSP,
    @SerialName("GENERATE_LAYER") GENERATE_LAYER,
    @SerialName("SEPARATE_STEMS") SEPARATE_STEMS,
    @SerialName("EXPORT") EXPORT,
    @SerialName("CUSTOM") CUSTOM
}

@Serializable
data class Job(
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: JobType,
    @SerialName("status")
    var status: JobStatus = JobStatus.PENDING,
    @SerialName("priority")
    val priority: Int = 0,
    @SerialName("request")
    val request: Map<String, String> = emptyMap(),
    @SerialName("result")
    val result: Map<String, String>? = null,
    @SerialName("error")
    val error: String? = null,
    @SerialName("progress")
    var progress: Double = 0.0,
    @SerialName("logs")
    val logs: List<String> = emptyList(),
    @SerialName("createdAt")
    val createdAt: String = Clock.System.now().toString(),
    @SerialName("startedAt")
    val startedAt: String? = null,
    @SerialName("completedAt")
    val completedAt: String? = null,
    @SerialName("retryCount")
    var retryCount: Int = 0,
    @SerialName("maxRetries")
    val maxRetries: Int = 3
) {
    fun withProgress(progress: Double): Job = copy(progress = progress)
    fun withError(error: String): Job = copy(status = JobStatus.FAILED, error = error)
    fun withResult(result: Map<String, String>): Job = copy(status = JobStatus.COMPLETED, result = result)
    fun canRetry(): Boolean = retryCount < maxRetries && status == JobStatus.FAILED
}

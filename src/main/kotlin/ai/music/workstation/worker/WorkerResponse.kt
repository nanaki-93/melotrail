package ai.music.workstation.worker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WorkerRequest(
    @SerialName("version")
    val version: Int = 1,
    @SerialName("command")
    val command: String,
    @SerialName("jobId")
    val jobId: String,
    @SerialName("input")
    val input: Map<String, String> = emptyMap(),
    @SerialName("params")
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class WorkerResponse(
    @SerialName("version")
    val version: Int = 1,
    @SerialName("jobId")
    val jobId: String,
    @SerialName("status")
    val status: WorkerStatus,
    @SerialName("output")
    val output: Map<String, String>? = null,
    @SerialName("error")
    val error: WorkerError? = null,
    @SerialName("progress")
    val progress: Double? = null,
    @SerialName("message")
    val message: String? = null
)

@Serializable
data class WorkerError(
    @SerialName("type")
    val type: String,
    @SerialName("message")
    val message: String
)

@Serializable
enum class WorkerStatus {
    @SerialName("COMPLETED") COMPLETED,
    @SerialName("IN_PROGRESS") IN_PROGRESS,
    @SerialName("ERROR") ERROR,
    @SerialName("CANCELLED") CANCELLED
}

val workerJson = Json {
    ignoreUnknownKeys = true
    useArrayPolymorphism = false
}

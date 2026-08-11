package ai.music.workstation.worker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class WorkerRequest(
    @SerialName("version")
    val version: Int = 1,
    @SerialName("command")
    val command: String,
    @SerialName("jobId")
    val jobId: String,
    @SerialName("input")
    val input: Map<String, JsonElement> = emptyMap(),
    @SerialName("params")
    val params: Map<String, JsonElement> = emptyMap()
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
    val output: Map<String, JsonElement>? = null,
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

@Serializable(with = WorkerStatusSerializer::class)
enum class WorkerStatus {
    COMPLETED,
    IN_PROGRESS,
    ERROR,
    CANCELLED,
    OK;

    companion object {
        fun fromString(s: String): WorkerStatus = when (s.lowercase()) {
            "completed", "ok" -> COMPLETED
            "in_progress" -> IN_PROGRESS
            "error" -> ERROR
            "cancelled" -> CANCELLED
            else -> ERROR
        }
    }
}

class WorkerStatusSerializer : kotlinx.serialization.KSerializer<WorkerStatus> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor = 
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("WorkerStatus", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: WorkerStatus) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): WorkerStatus {
        val str = decoder.decodeString()
        return WorkerStatus.fromString(str)
    }
}

val workerJson = Json {
    ignoreUnknownKeys = true
    useArrayPolymorphism = false
}

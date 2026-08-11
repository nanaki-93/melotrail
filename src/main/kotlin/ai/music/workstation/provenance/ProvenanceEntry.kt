package ai.music.workstation.provenance

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable(ProvenanceEntrySerializer::class)
sealed class ProvenanceEntry {
    abstract val timestamp: Instant
    abstract val operation: String
    abstract val user: String?

    @Serializable
    @SerialName("GENERATION")
    data class GenerationEntry(
        override val timestamp: Instant,
        override val operation: String = "GENERATION",
        override val user: String? = null,
        val jobId: String = "",
        val model: String = "",
        val modelVersion: String = "",
        val modelHash: String? = null,
        val instrument: String = "",
        val style: String = "",
        val prompt: String = "",
        val seed: Long = 0L,
        val parameters: Map<String, String> = emptyMap(),
        val inputHash: String = "",
        val inputPath: String = "",
        val outputPath: String = "",
        val outputHash: String = "",
        val duration: Double = 0.0,
        val status: GenerationStatus = GenerationStatus.PENDING
    ) : ProvenanceEntry()

    @Serializable
    @SerialName("DSP")
    data class DSPEntry(
        override val timestamp: Instant,
        override val operation: String = "DSP",
        override val user: String? = null,
        val presetName: String? = null,
        val dspType: String = "LOFI",
        val settings: Map<String, String> = emptyMap(),
        val targetTrack: String = "",
        val inputHash: String = "",
        val outputPath: String = "",
        val outputHash: String = ""
    ) : ProvenanceEntry()

    @Serializable
    @SerialName("EXPORT")
    data class ExportEntry(
        override val timestamp: Instant,
        override val operation: String = "EXPORT",
        override val user: String? = null,
        val format: String = "WAV",
        val sampleRate: Int = 44100,
        val bitDepth: Int = 16,
        val filename: String = "",
        val outputPath: String = "",
        val inputHash: String = "",
        val outputHash: String = "",
        val loudnessReport: Map<String, Double>? = null
    ) : ProvenanceEntry()

    @Serializable
    @SerialName("REPAIR")
    data class RepairEntry(
        override val timestamp: Instant,
        override val operation: String = "REPAIR",
        override val user: String? = null,
        val repairType: String = "PITCH",
        val method: String = "DETERMINISTIC",
        val targetTrack: String = "",
        val regionStart: Double? = null,
        val regionEnd: Double? = null,
        val inputHash: String = "",
        val outputPath: String = "",
        val outputHash: String = ""
    ) : ProvenanceEntry()

    @Serializable
    @SerialName("IMPORT")
    data class ImportEntry(
        override val timestamp: Instant,
        override val operation: String = "IMPORT",
        override val user: String? = null,
        val filename: String = "",
        val path: String = "",
        val format: String = "WAV",
        val sampleRate: Int = 44100,
        val channels: Int = 1,
        val duration: Double = 0.0,
        val inputHash: String = ""
    ) : ProvenanceEntry()

    @Serializable
    @SerialName("VERSION")
    data class VersionEntry(
        override val timestamp: Instant,
        override val operation: String = "VERSION",
        override val user: String? = null,
        val versionName: String = "",
        val description: String? = null,
        val baseVersionId: String? = null
    ) : ProvenanceEntry()

    @Serializable
    @SerialName("USER_ACTION")
    data class UserActionEntry(
        override val timestamp: Instant,
        override val operation: String = "USER_ACTION",
        override val user: String? = null,
        val action: String = "",
        val details: Map<String, String> = emptyMap()
    ) : ProvenanceEntry()
}

enum class GenerationStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

object ProvenanceEntrySerializer : KSerializer<ProvenanceEntry> {
    private val typeKey = "operation"
    
    private val serializersMap = mapOf<String, KSerializer<ProvenanceEntry>>(
        "GENERATION" to ProvenanceEntry.GenerationEntry.serializer() as KSerializer<ProvenanceEntry>,
        "DSP" to ProvenanceEntry.DSPEntry.serializer() as KSerializer<ProvenanceEntry>,
        "EXPORT" to ProvenanceEntry.ExportEntry.serializer() as KSerializer<ProvenanceEntry>,
        "REPAIR" to ProvenanceEntry.RepairEntry.serializer() as KSerializer<ProvenanceEntry>,
        "IMPORT" to ProvenanceEntry.ImportEntry.serializer() as KSerializer<ProvenanceEntry>,
        "VERSION" to ProvenanceEntry.VersionEntry.serializer() as KSerializer<ProvenanceEntry>,
        "USER_ACTION" to ProvenanceEntry.UserActionEntry.serializer() as KSerializer<ProvenanceEntry>
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ProvenanceEntry")

    override fun serialize(encoder: Encoder, value: ProvenanceEntry) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("This serializer works only with JSON")
        val json = jsonEncoder.json
        val element = serializersMap[value.operation]
            ?: throw IllegalStateException("Unknown provenance entry type: ${value.operation}")
        @Suppress("UNCHECKED_CAST")
        val jsonElement = json.encodeToJsonElement(element as KSerializer<ProvenanceEntry>, value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    override fun deserialize(decoder: Decoder): ProvenanceEntry {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("This serializer works only with JSON")
        val json = jsonDecoder.json
        val jsonElement = jsonDecoder.decodeJsonElement() as JsonObject
        
        val operation = jsonElement[typeKey]?.let {
            it.toString().trim('"')
        } ?: "UNKNOWN"
        
        val serializer = serializersMap[operation]
            ?: throw IllegalStateException("Unknown provenance entry type: $operation")
        
        return json.decodeFromJsonElement(serializer, jsonElement)
    }
}

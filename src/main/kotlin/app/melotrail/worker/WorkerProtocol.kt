package app.melotrail.worker

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The sole Kotlin mapping of supported Python-worker commands to HTTP requests. */
object WorkerProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    fun endpointFor(command: WorkerCommand): String = when (command) {
        is AnalyzeCommand -> "/analyze"
        is ApplyDSPCommand -> "/apply_dsp"
        is RepairCommand -> "/repair"
        is MasterCommand -> "/master"
        is MP3ConvertCommand -> "/mp3_convert"
        is MP3ExportCommand -> "/mp3_export"
        is CodecPreviewCommand -> "/codec_preview"
        is TranscribeCommand -> "/transcribe"
        is CleanMidiCommand -> "/midi-clean"
        is InputInspectionCommand -> "/inspect-input"
        is AudioCleanupCommand -> "/cleanup"
    }

    fun requestFor(command: WorkerCommand, jobId: String): JsonObject = buildJsonObject {
        put("jobId", jobId)
        when (command) {
            is AnalyzeCommand -> {
                put("path", command.path)
                put("version", command.version)
                put("options", buildJsonObject {
                    put("detectBPM", command.options.detectBPM)
                    put("detectKey", command.options.detectKey)
                    put("detectLoudness", command.options.detectLoudness)
                    put("detectOnsets", command.options.detectOnsets)
                    put("detectBeats", command.options.detectBeats)
                    put("detectSections", command.options.detectSections)
                })
            }
            is ApplyDSPCommand -> {
                put("path", command.path)
                put("settings", json.encodeToJsonElement(command.settings))
                command.outputFormat?.let { put("outputFormat", it) }
            }
            is RepairCommand -> {
                put("path", command.path)
                command.outputPath?.let { put("outputPath", it) }
                put("repairs", buildJsonArray {
                    command.repairs.forEach { repair ->
                        add(buildJsonObject {
                            put("type", repair.type)
                            put("params", repair.params.toJson())
                        })
                    }
                })
            }
            is MasterCommand -> {
                put("path", command.path)
                command.outputPath?.let { put("outputPath", it) }
                put("settings", command.settings.toJson())
            }
            is MP3ConvertCommand -> {
                put("path", command.path)
                put("outputPath", command.outputPath)
            }
            is MP3ExportCommand -> {
                put("path", command.path)
                put("outputPath", command.outputPath)
                put("bitrateKbps", command.bitrateKbps)
            }
            is CodecPreviewCommand -> {
                put("path", command.path)
                put("codec", command.codec)
                put("encodedPath", command.encodedPath)
                put("decodedPath", command.decodedPath)
                put("bitrateKbps", command.bitrateKbps)
            }
            is TranscribeCommand -> {
                put("path", command.path)
                put("outputPath", command.outputPath)
                put("instrument", command.instrument)
            }
            is CleanMidiCommand -> {
                put("path", command.path)
                put("outputPath", command.outputPath)
                put("version", command.version)
                put("profile", command.profile)
                command.quantize?.let { put("quantize", it) }
                put("strength", command.strength)
                put("minNoteMs", command.minNoteMs)
                put("minVelocity", command.minVelocity)
                put("normalizeVelocity", command.normalizeVelocity)
                put("cleanSustain", command.cleanSustain)
                put("preserveGraceNotes", command.preserveGraceNotes)
                put("graceNoteMaxMs", command.graceNoteMaxMs)
                put("graceVelocityMax", command.graceVelocityMax)
                put("duplicateOnsetWindowMs", command.duplicateOnsetWindowMs)
            }
            is InputInspectionCommand -> put("path", command.path)
            is AudioCleanupCommand -> {
                put("path", command.path)
                put("outputPath", command.outputPath)
                put("operations", buildJsonArray {
                    command.operations.forEach { operation ->
                        add(buildJsonObject {
                            put("type", operation.wireType)
                            when (operation) {
                                AudioCleanupOperation.DcRemoval -> Unit
                                is AudioCleanupOperation.ClipRepair -> put("params", buildJsonObject { put("threshold", operation.threshold) })
                                is AudioCleanupOperation.Declick -> put("params", buildJsonObject { put("threshold", operation.threshold) })
                                is AudioCleanupOperation.HumRemoval -> put("params", buildJsonObject { put("frequencyHz", operation.frequencyHz) })
                                is AudioCleanupOperation.NoiseReduction -> put("params", buildJsonObject { put("strength", operation.strength) })
                            }
                        })
                    }
                })
            }
        }
    }

    private fun Map<String, Any>.toJson(): JsonObject = buildJsonObject {
        forEach { (key, value) -> put(key, value.toJsonElement()) }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this.toString())
        is String -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject {
            this@toJsonElement.forEach { (key, value) -> if (key != null) put(key.toString(), value.toJsonElement()) }
        }
        is Iterable<*> -> buildJsonArray { forEach { add(it.toJsonElement()) } }
        else -> JsonPrimitive(toString())
    }
}

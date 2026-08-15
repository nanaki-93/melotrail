package app.melotrail.provenance

import app.melotrail.errors.AppError
import app.melotrail.errors.ErrorCategory
import app.melotrail.model.ErrorReporter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class ProvenanceLog(
    private val path: Path,
    private val errorReporter: ErrorReporter
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var _record: ProvenanceRecord = loadOrCreateDefault()

    fun getRecord(): ProvenanceRecord = _record

    fun loadOrCreateDefault(): ProvenanceRecord {
        return try {
            if (Files.exists(path)) {
                val jsonStr = Files.readString(path)
                json.decodeFromString<ProvenanceRecord>(jsonStr)
            } else {
                ProvenanceRecord()
            }
        } catch (e: Exception) {
            errorReporter.report("Failed to load provenance: ${e.message}", e)
            ProvenanceRecord()
        }
    }

    fun save() {
        try {
            Files.createDirectories(path.parent)
            val jsonStr = json.encodeToString(_record)
            Files.writeString(path, jsonStr)
        } catch (e: Exception) {
            errorReporter.report("Failed to save provenance: ${e.message}", e)
        }
    }

    fun appendImportEntry(
        filename: String,
        relativePath: String,
        format: String,
        sampleRate: Int,
        channels: Int,
        duration: Double,
        hash: String,
        user: String? = null
    ) {
        val entry = ProvenanceEntry.ImportEntry(
            timestamp = Clock.System.now(),
            filename = filename,
            path = relativePath,
            format = format,
            sampleRate = sampleRate,
            channels = channels,
            duration = duration,
            inputHash = hash,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun appendExportEntry(
        format: String,
        sampleRate: Int,
        bitDepth: Int,
        filename: String,
        relativePath: String,
        inputHash: String,
        outputHash: String,
        loudnessReport: Map<String, Double>? = null,
        user: String? = null
    ) {
        val entry = ProvenanceEntry.ExportEntry(
            timestamp = Clock.System.now(),
            format = format,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            filename = filename,
            outputPath = relativePath,
            inputHash = inputHash,
            outputHash = outputHash,
            loudnessReport = loudnessReport,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun appendDSPEntry(
        presetName: String?,
        dspType: String,
        settings: Map<String, String>,
        targetTrack: String,
        inputHash: String,
        outputPath: String,
        outputHash: String,
        user: String? = null
    ) {
        val entry = ProvenanceEntry.DSPEntry(
            timestamp = Clock.System.now(),
            presetName = presetName,
            dspType = dspType,
            settings = settings,
            targetTrack = targetTrack,
            inputHash = inputHash,
            outputPath = outputPath,
            outputHash = outputHash,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun appendRepairEntry(
        repairType: String,
        method: String,
        targetTrack: String,
        regionStart: Double?,
        regionEnd: Double?,
        inputHash: String,
        outputPath: String,
        outputHash: String,
        user: String? = null
    ) {
        val entry = ProvenanceEntry.RepairEntry(
            timestamp = Clock.System.now(),
            repairType = repairType,
            method = method,
            targetTrack = targetTrack,
            regionStart = regionStart,
            regionEnd = regionEnd,
            inputHash = inputHash,
            outputPath = outputPath,
            outputHash = outputHash,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun appendGenerationEntry(
        jobId: String,
        model: String,
        modelVersion: String,
        modelHash: String?,
        instrument: String,
        style: String,
        prompt: String,
        seed: Long,
        parameters: Map<String, String>,
        inputHash: String,
        inputPath: String,
        outputPath: String,
        outputHash: String,
        duration: Double,
        status: GenerationStatus,
        user: String? = null
    ) {
        val entry = ProvenanceEntry.GenerationEntry(
            timestamp = Clock.System.now(),
            jobId = jobId,
            model = model,
            modelVersion = modelVersion,
            modelHash = modelHash,
            instrument = instrument,
            style = style,
            prompt = prompt,
            seed = seed,
            parameters = parameters,
            inputHash = inputHash,
            inputPath = inputPath,
            outputPath = outputPath,
            outputHash = outputHash,
            duration = duration,
            status = status,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun appendVersionEntry(
        versionName: String,
        description: String?,
        baseVersionId: String?,
        user: String? = null
    ) {
        val entry = ProvenanceEntry.VersionEntry(
            timestamp = Clock.System.now(),
            versionName = versionName,
            description = description,
            baseVersionId = baseVersionId,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun appendUserActionEntry(
        action: String,
        details: Map<String, String> = emptyMap(),
        user: String? = null
    ) {
        val entry = ProvenanceEntry.UserActionEntry(
            timestamp = Clock.System.now(),
            action = action,
            details = details,
            user = user
        )
        _record = _record.withEntry(entry)
        save()
    }

    fun getEntriesByType(type: String): List<ProvenanceEntry> {
        return _record.filterByType(type)
    }

    fun getEntriesByDateRange(start: Instant, end: Instant): List<ProvenanceEntry> {
        return _record.filterByDateRange(start, end)
    }

    fun getLatestEntry(): ProvenanceEntry? {
        return _record.entries.lastOrNull()
    }

    fun getEntryCount(): Int = _record.entries.size
}

fun computeSHA256(content: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(content)
    return hash.joinToString("") { "%02x".format(it) }
}

fun computeSHA256File(path: Path): String {
    val content = Files.readAllBytes(path)
    return computeSHA256(content)
}

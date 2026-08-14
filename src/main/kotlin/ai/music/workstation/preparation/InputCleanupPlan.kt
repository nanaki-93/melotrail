package ai.music.workstation.preparation

import ai.music.workstation.worker.AudioCleanupOperation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A small, persisted decision record. It never contains worker paths or model text. */
@Serializable
data class InputCleanupPlan(
    val version: Int = CURRENT_VERSION,
    val partId: String,
    val source: InspectionSourceIdentity,
    val mode: InputCleanupMode = InputCleanupMode.INSPECT_ONLY,
    val operations: List<CleanupPlanOperation> = emptyList(),
    val evidence: AudioInspectionMeasurements,
    val confidence: Double,
    val warnings: List<String> = emptyList(),
    val transcriptionInput: TranscriptionInputArtifact = TranscriptionInputArtifact.SOURCE
) {
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported cleanup plan version: $version" }
        InputInspectionPaths.requirePartId(partId)
        source.requireValid(); evidence.requireValid()
        require(confidence.isFinite() && confidence in 0.0..1.0) { "Cleanup-plan confidence must be between zero and one." }
        require(warnings.size <= MAX_WARNINGS) { "Too many cleanup-plan warnings." }
        warnings.forEach { requireSafeCleanupText(it, "Cleanup-plan warning") }
        require(operations.distinctBy { it.type }.size == operations.size) { "Cleanup operations must not be duplicated." }
        operations.forEach { it.requireValid() }
        if (mode == InputCleanupMode.INSPECT_ONLY) {
            require(operations.isEmpty()) { "Inspect-only plans cannot select cleanup operations." }
            require(transcriptionInput == TranscriptionInputArtifact.SOURCE) { "Inspect-only plans must retain the source transcription input." }
        } else {
            require(operations.isNotEmpty()) { "Safe-cleanup plans require measured operations." }
            require(transcriptionInput == TranscriptionInputArtifact.CLEAN_WAV) { "Safe-cleanup plans must select clean.wav for transcription." }
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
        private const val MAX_WARNINGS = 32
    }
}

@Serializable enum class InputCleanupMode { INSPECT_ONLY, SAFE_CLEANUP }
@Serializable enum class TranscriptionInputArtifact { SOURCE, CLEAN_WAV }
@Serializable enum class CleanupOperationType { DC_REMOVAL, CLIP_REPAIR, DECLICK, HUM_REMOVAL, NOISE_REDUCTION }

/** Fixed, bounded settings only. Null fields are forbidden unless the operation has no setting. */
@Serializable
data class CleanupPlanOperation(
    val type: CleanupOperationType,
    val threshold: Double? = null,
    val frequencyHz: Int? = null,
    val strength: Double? = null
) {
    fun requireValid() = when (type) {
        CleanupOperationType.DC_REMOVAL -> require(threshold == null && frequencyHz == null && strength == null) { "DC removal has no settings." }
        CleanupOperationType.CLIP_REPAIR -> require(threshold != null && threshold in 0.95..1.0 && frequencyHz == null && strength == null) { "Clip-repair settings are invalid." }
        CleanupOperationType.DECLICK -> require(threshold != null && threshold in 0.5..0.99 && frequencyHz == null && strength == null) { "Declick settings are invalid." }
        CleanupOperationType.HUM_REMOVAL -> require(frequencyHz in setOf(50, 60) && threshold == null && strength == null) { "Hum-removal settings are invalid." }
        CleanupOperationType.NOISE_REDUCTION -> require(strength != null && strength in 0.05..0.5 && threshold == null && frequencyHz == null) { "Noise-reduction settings are invalid." }
    }

    fun asWorkerOperation(): AudioCleanupOperation = when (type) {
        CleanupOperationType.DC_REMOVAL -> AudioCleanupOperation.DcRemoval
        CleanupOperationType.CLIP_REPAIR -> AudioCleanupOperation.ClipRepair(checkNotNull(threshold))
        CleanupOperationType.DECLICK -> AudioCleanupOperation.Declick(checkNotNull(threshold))
        CleanupOperationType.HUM_REMOVAL -> AudioCleanupOperation.HumRemoval(checkNotNull(frequencyHz))
        CleanupOperationType.NOISE_REDUCTION -> AudioCleanupOperation.NoiseReduction(checkNotNull(strength))
    }
}

/** The clean artifact is recorded by relative identity only, never an external path. */
@Serializable
data class CleanupPlanRecord(
    val plan: InputCleanupPlan,
    val output: CleanupOutputArtifact? = null
) {
    fun requireValid() {
        plan.requireValid()
        if (plan.mode == InputCleanupMode.INSPECT_ONLY) require(output == null) { "Inspect-only cleanup cannot have output." }
        output?.requireValid()
    }
}

@Serializable
data class CleanupOutputArtifact(
    val relativePath: String = "clean.wav",
    val sha256: String,
    val sampleRate: Int,
    val channels: Int,
    val frames: Long,
    val before: CleanupMetrics,
    val after: CleanupMetrics,
    val appliedOperations: List<CleanupOperationType>,
    val skippedOperations: List<CleanupOperationType> = emptyList(),
    val warnings: List<String> = emptyList(),
    val toolVersions: Map<String, String> = emptyMap()
) {
    fun requireValid() {
        require(relativePath == "clean.wav") { "Cleanup output path must be clean.wav." }
        require(Regex("[0-9a-f]{64}").matches(sha256)) { "Cleanup output fingerprint is invalid." }
        require(sampleRate in 1..384_000 && channels in 1..32 && frames > 0) { "Cleanup output format is invalid." }
        before.requireValid(); after.requireValid()
        require(appliedOperations.distinct().size == appliedOperations.size && skippedOperations.distinct().size == skippedOperations.size) { "Cleanup operation records must not be duplicated." }
        warnings.forEach { requireSafeCleanupText(it, "Cleanup warning") }
        toolVersions.forEach { (name, value) ->
            require(Regex("[A-Za-z0-9._-]{1,64}").matches(name)) { "Cleanup tool name is invalid." }
            requireSafeCleanupText(value, "Cleanup tool version")
        }
    }
}

@Serializable
data class CleanupMetrics(
    val peak: Double,
    val rms: Double,
    val dcOffset: Double,
    val clippedRunCount: Long,
    val clippedFrameCount: Long,
    val maxFrameJump: Double,
    val humConfidence: Double,
    val noiseConfidence: Double
) {
    fun requireValid() {
        listOf(peak, rms, dcOffset, maxFrameJump, humConfidence, noiseConfidence).forEach { require(it.isFinite()) { "Cleanup metric must be finite." } }
        require(peak >= 0 && rms >= 0 && maxFrameJump >= 0 && humConfidence in 0.0..1.0 && noiseConfidence in 0.0..1.0) { "Cleanup metric is outside its range." }
        require(clippedRunCount >= 0 && clippedFrameCount >= 0) { "Cleanup frame count cannot be negative." }
    }
}

/** Deterministic default and test oracle. Declick is not selected without its own measured evidence. */
object DeterministicInputCleanupPlanner {
    fun select(report: InputInspectionReport, mode: InputCleanupMode = InputCleanupMode.INSPECT_ONLY, rankedTypes: List<CleanupOperationType>? = null): InputCleanupPlan {
        report.requireValid()
        require(report.detectedInput.container != InputContainer.MIDI) { "MIDI input has no audio cleanup plan." }
        val evidence = requireNotNull(report.measurements) { "Audio cleanup requires inspection measurements." }
        val candidates = measuredCandidates(evidence)
        val ordered = rankedTypes?.takeIf { it.distinct().size == it.size && it.toSet() == candidates.toSet() } ?: candidates
        val operations = if (mode == InputCleanupMode.SAFE_CLEANUP && report.detectedInput.container == InputContainer.RIFF_WAVE) {
            ordered.map(::operation)
        } else emptyList()
        val warnings = buildList {
            if (mode == InputCleanupMode.SAFE_CLEANUP && report.detectedInput.container != InputContainer.RIFF_WAVE) add("Safe cleanup requires a decoded RIFF/WAVE input.")
            if (mode == InputCleanupMode.SAFE_CLEANUP && candidates.isEmpty()) add("No measured evidence meets the conservative cleanup thresholds.")
        }
        return InputCleanupPlan(
            partId = report.partId, source = report.source, mode = if (operations.isEmpty()) InputCleanupMode.INSPECT_ONLY else mode,
            operations = operations, evidence = evidence, confidence = candidates.maxOfOrNull { confidence(it, evidence) } ?: 0.0,
            warnings = warnings, transcriptionInput = if (operations.isEmpty()) TranscriptionInputArtifact.SOURCE else TranscriptionInputArtifact.CLEAN_WAV
        ).also { it.requireValid() }
    }

    fun measuredCandidates(evidence: AudioInspectionMeasurements): List<CleanupOperationType> = buildList {
        if (kotlin.math.abs(evidence.dcOffset) >= 0.005) add(CleanupOperationType.DC_REMOVAL)
        if (evidence.clippedRunCount > 0 && evidence.clippedFrameCount > 0) add(CleanupOperationType.CLIP_REPAIR)
        if (evidence.hum.confidence >= 0.15 && evidence.hum.evidence in setOf(EvidenceLevel.MODERATE, EvidenceLevel.HIGH)) add(CleanupOperationType.HUM_REMOVAL)
        if (evidence.noise.confidence >= 0.15 && evidence.noise.evidence in setOf(EvidenceLevel.MODERATE, EvidenceLevel.HIGH)) add(CleanupOperationType.NOISE_REDUCTION)
    }

    private fun operation(type: CleanupOperationType) = when (type) {
        CleanupOperationType.DC_REMOVAL -> CleanupPlanOperation(type)
        CleanupOperationType.CLIP_REPAIR -> CleanupPlanOperation(type, threshold = 0.999)
        CleanupOperationType.DECLICK -> CleanupPlanOperation(type, threshold = 0.9)
        CleanupOperationType.HUM_REMOVAL -> CleanupPlanOperation(type, frequencyHz = 60)
        CleanupOperationType.NOISE_REDUCTION -> CleanupPlanOperation(type, strength = 0.35)
    }
    private fun confidence(type: CleanupOperationType, evidence: AudioInspectionMeasurements): Double = when (type) {
        CleanupOperationType.DC_REMOVAL -> (kotlin.math.abs(evidence.dcOffset) / 0.02).coerceAtMost(1.0)
        CleanupOperationType.CLIP_REPAIR -> 1.0
        CleanupOperationType.HUM_REMOVAL -> evidence.hum.confidence
        CleanupOperationType.NOISE_REDUCTION -> evidence.noise.confidence
        CleanupOperationType.DECLICK -> 0.0
    }
}

/** Optional model output may reorder supplied candidates only; every other response falls back. */
object CleanupCandidateRanking {
    private val json = Json { ignoreUnknownKeys = false }
    fun parseOrNull(text: String?, candidates: List<CleanupOperationType>): List<CleanupOperationType>? {
        if (text == null) return null
        return try {
            val objectValue = json.parseToJsonElement(text).jsonObject
            if (objectValue.keys != setOf("version", "operationTypes") || objectValue["version"]?.jsonPrimitive?.content?.toIntOrNull() != 1) return null
            val ranked = objectValue["operationTypes"]?.jsonArray?.map { CleanupOperationType.valueOf(it.jsonPrimitive.content) } ?: return null
            if (ranked.distinct().size != ranked.size || ranked.toSet() != candidates.toSet()) null else ranked
        } catch (_: Exception) { null }
    }
}

private fun requireSafeCleanupText(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 500 && value.none { it.isISOControl() }) { "$label is invalid." }
    require(!Regex("(?:^|\\s)(?:[A-Za-z]:[\\\\/]|/|~[/\\\\])").containsMatchIn(value)) { "$label must not contain an external path." }
}

package app.melotrail.arrangement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** A fingerprinted project artifact. Absolute paths and aliases are never persisted. */
@Serializable
data class ArtifactRef(val path: String, val sha256: String) {
    init {
        val relative = runCatching { Path.of(path) }.getOrElse { throw IllegalArgumentException("Artifact path is invalid", it) }
        val canonical = relative.normalize().toString().replace('\\', '/')
        require(path.isNotBlank() && !relative.isAbsolute && '\\' !in path && ':' !in path &&
            relative.none { it.toString() == ".." } && canonical == path) {
            "Artifact path must be canonical and project-relative"
        }
        require(SHA256.matches(sha256)) { "Artifact fingerprint is invalid" }
    }
}

@Serializable
enum class StageId {
    @SerialName("source") SOURCE,
    @SerialName("extracted") EXTRACTED,
    @SerialName("cleaned") CLEANED,
    @SerialName("normalized") NORMALIZED,
    @SerialName("transposed") TRANSPOSED,
    @SerialName("corrected") CORRECTED,
    @SerialName("enhanced") ENHANCED,
    @SerialName("analyzed") ANALYZED,
    @SerialName("structured") STRUCTURED,
    @SerialName("cohesion") COHESION,
    @SerialName("arranged") ARRANGED,
    @SerialName("generated") GENERATED,
    @SerialName("rendered") RENDERED,
    @SerialName("mixed") MIXED,
    @SerialName("mastered") MASTERED,
    @SerialName("exported") EXPORTED;

    val isPartStage: Boolean get() = ordinal <= ANALYZED.ordinal
}

@Serializable
sealed interface StageSubject {
    @Serializable @SerialName("project") data object Project : StageSubject
    @Serializable @SerialName("part") data class Part(val partId: String) : StageSubject {
        init { require(SAFE_ID.matches(partId)) { "Stage part ID is invalid" } }
    }
    @Serializable @SerialName("occurrence") data class Occurrence(val occurrenceId: String) : StageSubject {
        init { require(SAFE_ID.matches(occurrenceId)) { "Stage occurrence ID is invalid" } }
    }
}

@Serializable
enum class StageRunStatus { PENDING, PROCESSING, COMPLETED, FAILED }

/** An allow-listed, presentation-safe failure. Do not persist exception text, paths, or model output. */
@Serializable
enum class SafeFailureCode {
    INPUT_INVALID,
    DEPENDENCY_UNAVAILABLE,
    PROCESSOR_REJECTED,
    OUTPUT_INVALID,
    INTERRUPTED,
    INTERNAL
}

@Serializable
data class SafeFailure(val code: SafeFailureCode, val recoveryAction: String) {
    init {
        require(recoveryAction.isNotBlank() && recoveryAction.length <= 240 && recoveryAction.none { it.isISOControl() }) {
            "Failure recovery action is invalid"
        }
        require('/' !in recoveryAction && '\\' !in recoveryAction) { "Failure recovery action must not expose paths" }
    }
}

@Serializable
data class ProcessorIdentity(val id: String, val version: String) {
    init {
        require(SAFE_ID.matches(id) && SAFE_VERSION.matches(version)) { "Processor identity is invalid" }
    }
}

@Serializable
data class ModelIdentity(val provider: String, val model: String, val version: String? = null) {
    init {
        require(SAFE_ID.matches(provider) && SAFE_ID.matches(model) && (version == null || SAFE_VERSION.matches(version))) {
            "Model identity is invalid"
        }
    }
}

@Serializable
data class StageApproval(val kind: String, val approvedAt: String) {
    init {
        require(SAFE_ID.matches(kind)) { "Stage approval kind is invalid" }
        parseTimestamp(approvedAt, "approval")
    }
}

/** An explicit selection must always name one completed output of its own record. */
@Serializable
data class StageOutputSelection(val artifact: ArtifactRef, val selectedAt: String) {
    init { parseTimestamp(selectedAt, "selection") }
}

/**
 * Immutable one-shot stage record. A retry is a new record; Task 012 owns
 * scheduling and state transitions. This makes failed/stale attempts evidence
 * without ever making them selectable output.
 */
@Serializable
data class StageRunRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val runId: String,
    val stage: StageId,
    val subject: StageSubject,
    val status: StageRunStatus,
    val inputArtifacts: List<ArtifactRef> = emptyList(),
    val subjectDependencies: List<StageSubject> = emptyList(),
    val configurationSha256: String? = null,
    val contextSha256: String? = null,
    val processor: ProcessorIdentity? = null,
    val model: ModelIdentity? = null,
    val seed: Long? = null,
    val createdAt: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val outputArtifacts: List<ArtifactRef> = emptyList(),
    val reportArtifacts: List<ArtifactRef> = emptyList(),
    val approvals: List<StageApproval> = emptyList(),
    val selections: List<StageOutputSelection> = emptyList(),
    val failure: SafeFailure? = null
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "Unsupported stage-run schema version: $schemaVersion" }
        require(SAFE_RUN_ID.matches(runId)) { "Stage run ID is invalid" }
        require(stage.isPartStage == (subject is StageSubject.Part)) {
            "Stage '$stage' has an incompatible subject"
        }
        require(configurationSha256 == null || SHA256.matches(configurationSha256)) { "Configuration fingerprint is invalid" }
        require(contextSha256 == null || SHA256.matches(contextSha256)) { "Context fingerprint is invalid" }
        requireUnique(inputArtifacts, "input artifacts")
        requireUnique(outputArtifacts, "output artifacts")
        requireUnique(reportArtifacts, "report artifacts")
        require(subjectDependencies.distinct().size == subjectDependencies.size) { "Stage subject dependencies must be unique" }
        parseTimestamp(createdAt, "created")
        startedAt?.let { require(parseTimestamp(it, "started") >= parseTimestamp(createdAt, "created")) { "Stage run starts before creation" } }
        finishedAt?.let {
            require(parseTimestamp(it, "finished") >= parseTimestamp(startedAt ?: createdAt, "start")) { "Stage run finishes before it starts" }
        }
        when (status) {
            StageRunStatus.PENDING -> require(startedAt == null && finishedAt == null && failure == null && outputArtifacts.isEmpty()) {
                "Pending stage runs cannot have output, timing, or failure"
            }
            StageRunStatus.PROCESSING -> require(startedAt != null && finishedAt == null && failure == null && outputArtifacts.isEmpty()) {
                "Processing stage runs cannot have output or failure"
            }
            StageRunStatus.COMPLETED -> require(finishedAt != null && failure == null && outputArtifacts.isNotEmpty()) {
                "Completed stage runs require finished timing and output"
            }
            StageRunStatus.FAILED -> require(finishedAt != null && failure != null && outputArtifacts.isEmpty() && selections.isEmpty()) {
                "Failed stage runs require a safe failure and cannot select output"
            }
        }
        require(status == StageRunStatus.COMPLETED || selections.isEmpty()) { "Only completed stage runs may select output" }
        require(selections.all { it.artifact in outputArtifacts }) { "Selections must reference a completed output" }
    }

    fun cacheKey(): String = sha256Hex(buildString {
        append(stage.name).append('|').append(subject.key()).append('|')
        inputArtifacts.sortedBy(ArtifactRef::path).forEach { append(it.path).append('=').append(it.sha256).append(';') }
        append('|')
        subjectDependencies.map(StageSubject::key).sorted().forEach { append(it).append(';') }
        append('|').append(configurationSha256.orEmpty()).append('|').append(contextSha256.orEmpty()).append('|')
        processor?.let { append(it.id).append('@').append(it.version) }
        append('|')
        model?.let { append(it.provider).append('/').append(it.model).append('@').append(it.version.orEmpty()) }
        append('|').append(seed ?: "")
    })

    companion object { const val SCHEMA_VERSION = 1 }
}

@Serializable
data class StageRunIndexEntry(val runId: String, val record: ArtifactRef) {
    init { require(SAFE_RUN_ID.matches(runId)) { "Stage run index ID is invalid" } }
}

@Serializable
data class StageRunIndex(val schemaVersion: Int = 1, val runs: List<StageRunIndexEntry> = emptyList()) {
    init {
        require(schemaVersion == 1) { "Unsupported stage-run index version: $schemaVersion" }
        require(runs.map(StageRunIndexEntry::runId).distinct().size == runs.size) { "Stage run IDs must be unique" }
    }
}

@Serializable
data class ProjectStageRunManifestReference(
    val index: ArtifactRef? = null,
    /** Task 002 compatibility payload; it is never written and is materialized only by explicit migration. */
    @Transient val legacyRuns: List<LegacyManifestRunInput> = emptyList()
) {
    fun requireCanonical() {
        index?.let { require(StageRunStore.isIndexPath(it.path)) { "Stage-run index path is not canonical" } }
    }
}

data class LegacyManifestRunInput(val stage: String, val status: String, val artifacts: List<WorkflowArtifactReference>)

/** Read-only compatibility input retained only until an explicit project migration publishes run files. */
data class LegacyStageRunInput(
    val stage: StageId,
    val subject: StageSubject.Part,
    val artifactPath: String,
    val selected: Boolean
)

/** Pure mapper for supported v3 references; hashing and filesystem publication stay in [StageRunStore]. */
object LegacyV3StageRunMapper {
    fun map(project: Project): List<LegacyStageRunInput> = project.parts.flatMap { part ->
        val subject = StageSubject.Part(part.id)
        val midi = part.midi
        buildList {
            add(LegacyStageRunInput(StageId.SOURCE, subject, part.file, selected = false))
            midi?.raw?.let { add(LegacyStageRunInput(StageId.EXTRACTED, subject, it, selected = false)) }
            midi?.clean?.let {
                add(LegacyStageRunInput(StageId.CLEANED, subject, it,
                    selected = midi.normalized == null && midi.aiFixSelection == MidiAiFixSelection.SKIP && midi.analysisInput == MidiAnalysisInput.CURRENT))
            }
            midi?.normalized?.let {
                add(LegacyStageRunInput(StageId.NORMALIZED, subject, it,
                    selected = midi.aiFixSelection == MidiAiFixSelection.SKIP && midi.analysisInput == MidiAnalysisInput.CURRENT))
            }
            midi?.technicalCorrection?.output?.file?.let {
                add(LegacyStageRunInput(StageId.CORRECTED, subject, it,
                    selected = midi.technicalCorrectionSelection == TechnicalCorrectionSelection.CORRECTED && midi.analysisInput == MidiAnalysisInput.CURRENT))
            }
            midi?.feel?.derived?.let {
                add(LegacyStageRunInput(StageId.ENHANCED, subject, it,
                    selected = midi.analysisInput == MidiAnalysisInput.LOFI_FEEL))
            }
        }
    }
}

data class StageRunSummary(
    val runId: String,
    val stage: StageId,
    val subject: StageSubject,
    val status: StageRunStatus,
    val outputCount: Int,
    val failure: SafeFailureCode?
)

object StageRunDependencyGraph {
    private val partStages = listOf(StageId.SOURCE, StageId.EXTRACTED, StageId.CLEANED, StageId.NORMALIZED,
        StageId.TRANSPOSED, StageId.CORRECTED, StageId.ENHANCED, StageId.ANALYZED)
    private val projectStages = listOf(StageId.STRUCTURED, StageId.COHESION, StageId.ARRANGED, StageId.GENERATED,
        StageId.RENDERED, StageId.MIXED, StageId.MASTERED, StageId.EXPORTED)

    fun downstreamOf(stage: StageId, subject: StageSubject): Set<StageId> = when (subject) {
        is StageSubject.Part -> partStages.drop(partStages.indexOf(stage).takeIf { it >= 0 }?.plus(1) ?: partStages.size).toSet() + projectStages
        StageSubject.Project, is StageSubject.Occurrence -> projectStages.drop(projectStages.indexOf(stage).takeIf { it >= 0 }?.plus(1) ?: projectStages.size).toSet()
    }
}

internal fun StageSubject.key(): String = when (this) {
    StageSubject.Project -> "project"
    is StageSubject.Part -> "part:$partId"
    is StageSubject.Occurrence -> "occurrence:$occurrenceId"
}

internal fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun requireUnique(references: List<ArtifactRef>, label: String) {
    require(references.map(ArtifactRef::path).distinct().size == references.size) { "Stage $label must have unique paths" }
}

private fun parseTimestamp(value: String, label: String): Instant = try {
    Instant.parse(value)
} catch (error: Exception) {
    throw IllegalArgumentException("Stage $label timestamp is invalid", error)
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SAFE_RUN_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}")
private val SAFE_VERSION = Regex("[A-Za-z0-9._-]{1,80}")
private val SHA256 = Regex("[0-9a-f]{64}")

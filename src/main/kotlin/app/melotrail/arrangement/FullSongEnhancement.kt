package app.melotrail.arrangement

import app.melotrail.application.WholeSongAnalysisProjection
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** The complete, code-owned vocabulary accepted from a full-song advisor. */
@Serializable
enum class FullSongEnhancementOperationKind {
    REVOICE_CHORD, SIMPLIFY_BASS_LEAP, REDUCE_DENSITY, REMOVE_COLLISION,
    ADJUST_TIMING, ADJUST_VELOCITY, ADJUST_DURATION, CORRECT_CHORD_CLASH,
    ADJUST_TRANSITION_NOTE
}

@Serializable
data class FullSongEnhancementNote(
    val id: String,
    val track: Int,
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long
) {
    init {
        require(NOTE_ID.matches(id) && track >= 0 && channel in 0..15 && pitch in 0..127 && velocity in 1..127 && startTick >= 0 && endTick > startTick) {
            "Full-song enhancement note identity is invalid"
        }
    }
    companion object { internal val NOTE_ID = Regex("n-[0-9a-f]{64}") }
}

/** A target is one physical MIDI file; windows remain global song ticks. */
@Serializable
data class FullSongEnhancementTarget(
    val id: String,
    val role: String,
    val occurrenceId: String? = null,
    val offsetTicks: Long = 0,
    val input: WorkflowArtifactReference,
    val notes: List<FullSongEnhancementNote>
) {
    init {
        require(ID.matches(id) && ROLE.matches(role) && (occurrenceId == null || ID.matches(occurrenceId)) && offsetTicks >= 0 &&
            notes.map(FullSongEnhancementNote::id).distinct().size == notes.size) { "Full-song enhancement target is invalid" }
    }
    companion object { private val ID = Regex("[A-Za-z0-9_-]{1,80}"); private val ROLE = Regex("[a-z][a-z0-9_-]{0,63}") }
}

/** One bounded correction batch. Targets remain complete; omitted actionable windows are explicit rather than silently editable. */
@Serializable
data class FullSongEnhancementInput(
    val schemaVersion: Int = SCHEMA_VERSION,
    val inputSha256: String,
    val contextSha256: String,
    val criticInputSha256: String,
    val criticReportSha256: String,
    val authority: WholeSongAnalysisProjection,
    val issues: List<FullSongIssue>,
    val targets: List<FullSongEnhancementTarget>,
    val totalActionableIssueCount: Int = issues.size,
    val batchIndex: Int = 0,
    val batchCount: Int = 1,
    val policy: FullSongEnhancementPolicy = FullSongEnhancementPolicy()
) {
    init {
        require(schemaVersion == SCHEMA_VERSION && HASH.matches(inputSha256) && HASH.matches(contextSha256) && HASH.matches(criticInputSha256) && HASH.matches(criticReportSha256) &&
            issues.size <= MAX_ACTIONABLE_ISSUES && issues.all { it.severity == FullSongIssueSeverity.ACTIONABLE } &&
            issues == issues.sortedWith(FullSongCriticReport.ISSUE_ORDER) && targets.map(FullSongEnhancementTarget::id).distinct().size == targets.size &&
            totalActionableIssueCount >= issues.size && batchIndex in 0 until batchCount &&
            batchCount == maxOf(1, (totalActionableIssueCount + MAX_ACTIONABLE_ISSUES - 1) / MAX_ACTIONABLE_ISSUES)) {
            "Full-song enhancement input is invalid"
        }
    }
    companion object { const val SCHEMA_VERSION = 1; const val MAX_ACTIONABLE_ISSUES = 32; private val HASH = Regex("[0-9a-f]{64}") }
}

/** Code-owned policy. Its floors make a small target intentionally immutable. */
@Serializable
data class FullSongEnhancementPolicy(val version: Int = VERSION, val totalFraction: Int = 5, val additionDeletionFraction: Int = 2) {
    init { require(version == VERSION && totalFraction == 5 && additionDeletionFraction == 2) { "Full-song enhancement policy is invalid" } }
    fun totalBudget(noteCount: Int): Int = noteCount * totalFraction / 100
    fun additionDeletionBudget(noteCount: Int): Int = noteCount * additionDeletionFraction / 100
    companion object { const val VERSION = 1 }
}

/** One typed intention; Kotlin resolves it to exact MIDI changes after validation. */
@Serializable
data class FullSongEnhancementOperation(
    val kind: FullSongEnhancementOperationKind,
    val issueId: String,
    val targetId: String,
    val noteId: String,
    val relatedNoteId: String? = null,
    val pitch: Int? = null,
    val tickDelta: Long? = null,
    val velocityDelta: Int? = null,
    val durationDelta: Long? = null
) {
    init {
        require(ISSUE_ID.matches(issueId) && TARGET_ID.matches(targetId) && FullSongEnhancementNote.NOTE_ID.matches(noteId) &&
            (relatedNoteId == null || FullSongEnhancementNote.NOTE_ID.matches(relatedNoteId)) &&
            (pitch == null || pitch in 0..127) && (tickDelta == null || tickDelta in -9_600L..9_600L) &&
            (velocityDelta == null || velocityDelta in -126..126) && (durationDelta == null || durationDelta in -9_600L..9_600L)) {
            "Full-song enhancement operation is invalid"
        }
    }
    companion object { private val ISSUE_ID = Regex("[0-9a-f]{32}"); private val TARGET_ID = Regex("[A-Za-z0-9_-]{1,80}") }
}

/** Strict, hash-bound planner response. No arbitrary paths, code, or DSP values exist in this DTO. */
@Serializable
data class FullSongEnhancementPlan(
    val schemaVersion: Int = SCHEMA_VERSION,
    val inputSha256: String,
    val contextSha256: String,
    val criticInputSha256: String,
    val criticReportSha256: String,
    val modelIdentity: String,
    val operations: List<FullSongEnhancementOperation>
) {
    init {
        require(schemaVersion == SCHEMA_VERSION && HASH.matches(inputSha256) && HASH.matches(contextSha256) && HASH.matches(criticInputSha256) && HASH.matches(criticReportSha256) &&
            MODEL.matches(modelIdentity) && operations.size <= MAX_TOTAL_OPERATIONS && operations.map { it.targetId to it.noteId }.distinct().size == operations.size) {
            "Full-song enhancement plan is invalid"
        }
    }
    companion object { const val SCHEMA_VERSION = 1; const val MAX_TOTAL_OPERATIONS = 2_048; private val HASH = Regex("[0-9a-f]{64}"); private val MODEL = Regex("[A-Za-z0-9._:-]{1,120}") }
}

@Serializable
data class FullSongEnhancementApplicationReport(
    val inputSha256: String,
    val contextSha256: String,
    val criticReportSha256: String,
    val planSha256: String,
    val addressedIssueIds: List<String>,
    val unaddressedIssueIds: List<String>,
    val changedNotes: Int,
    val additions: Int,
    val deletions: Int,
    val beforeCriticalIssueCount: Int = 0,
    val afterCriticalIssueCount: Int = 0,
    val beforeBlockingIssueCount: Int = 0,
    val afterBlockingIssueCount: Int = 0,
    val beforeActionableIssueCount: Int = 0,
    val afterActionableIssueCount: Int = 0,
    val recognizabilityPreserved: Boolean = false,
    val improvement: FullSongEnhancementImprovement = FullSongEnhancementImprovement.NO_OP,
    val automaticallyAccepted: Boolean = false,
    val warnings: List<String> = emptyList()
)

/** Evidence-backed candidate outcome; only improved candidates can enter review. */
@Serializable enum class FullSongEnhancementImprovement { NO_OP, REGRESSION, PARTIAL, GENUINE }

fun interface FullSongEnhancementPlanner { fun plan(input: FullSongEnhancementInput): String }

object FullSongEnhancementPlanParser {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
    fun parse(response: String): FullSongEnhancementPlan = try {
        require(response.isNotBlank() && response.trimStart().startsWith('{')) { "Full-song enhancement model response must be JSON only." }
        json.decodeFromString(response)
    } catch (error: Exception) {
        throw IllegalArgumentException("Full-song enhancement model response is malformed or contains unsupported fields.", error)
    }
}

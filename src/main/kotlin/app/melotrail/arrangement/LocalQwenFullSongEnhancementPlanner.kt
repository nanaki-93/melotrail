package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local-Qwen adapter for the bounded Full-Song Enhance stage. The model chooses
 * only operations; application-owned identities are stamped after validation.
 */
class LocalQwenFullSongEnhancementPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient(),
    private val modelIdentity: String = System.getenv("QWEN_MODEL")?.takeIf { it.matches(MODEL_ID) } ?: "qwen-local"
) : FullSongEnhancementPlanner {
    override fun plan(input: FullSongEnhancementInput): String {
        val response = requestQwenWithAutomaticRetries(client, SYSTEM_PROMPT, json.encodeToString(modelInput(input))) { text ->
            val modelPlan = json.decodeFromString<ModelPlan>(text)
            validate(modelPlan.operations, input)
            json.encodeToString(FullSongEnhancementPlan(
                inputSha256 = input.inputSha256,
                contextSha256 = input.contextSha256,
                criticInputSha256 = input.criticInputSha256,
                criticReportSha256 = input.criticReportSha256,
                modelIdentity = modelIdentity,
                operations = modelPlan.operations
            ))
        }
        return response
    }

    /**
     * Qwen needs the editable note identities, not the entire canonical song.
     * Keeping this request local to Critic windows prevents a long arrangement
     * from exhausting the context window before the model can produce a plan.
     */
    private fun modelInput(input: FullSongEnhancementInput): ModelInput {
        val eligibleNotes = input.targets.asSequence()
            .flatMap { target ->
                target.notes.asSequence()
                    .filter { note -> input.issues.any { issue -> issueAppliesTo(issue, target, note) } }
                    .map { note -> target to note }
            }
            .sortedWith(compareBy<Pair<FullSongEnhancementTarget, FullSongEnhancementNote>> { it.second.startTick }
                .thenBy { it.first.id }
                .thenBy { it.second.id })
            .take(MAX_MODEL_NOTES)
            .toList()
        val notesByTarget = eligibleNotes.groupBy({ it.first.id }, { it.second })
        return ModelInput(
            issues = input.issues.map(::ModelIssue),
            targets = input.targets.mapNotNull { target ->
                notesByTarget[target.id]?.let { notes ->
                    ModelTarget(
                        id = target.id,
                        role = target.role,
                        occurrenceId = target.occurrenceId,
                        notes = notes.map(::ModelNote)
                    )
                }
            }
        )
    }

    private fun issueAppliesTo(
        issue: FullSongIssue,
        target: FullSongEnhancementTarget,
        note: FullSongEnhancementNote
    ): Boolean =
        (issue.targetRole == target.role || issue.targetRole == "ensemble") &&
            (issue.occurrenceId == null || issue.occurrenceId == target.occurrenceId) &&
            note.startTick >= issue.window.startTick && note.endTick <= issue.window.endTick

    private fun validate(operations: List<FullSongEnhancementOperation>, input: FullSongEnhancementInput) {
        require(operations.size <= 256 && operations.map { it.targetId to it.noteId }.distinct().size == operations.size) {
            "Qwen returned duplicate or excessive Full-Song Enhance operations."
        }
        val issues = input.issues.associateBy(FullSongIssue::id)
        val targets = input.targets.associateBy(FullSongEnhancementTarget::id)
        operations.forEach { operation ->
            val issue = requireNotNull(issues[operation.issueId]) { "Qwen referenced an unknown Critic issue '${operation.issueId}'." }
            val target = requireNotNull(targets[operation.targetId]) { "Qwen referenced an unknown enhancement target '${operation.targetId}'." }
            val note = requireNotNull(target.notes.singleOrNull { it.id == operation.noteId }) {
                "Qwen referenced note '${operation.noteId}' outside target '${operation.targetId}'."
            }
            require(issue.targetRole == target.role || issue.targetRole == "ensemble") { "Qwen targeted a role not named by its Critic issue." }
            require(issue.occurrenceId == null || issue.occurrenceId == target.occurrenceId) { "Qwen targeted an occurrence not named by its Critic issue." }
            require(note.startTick >= issue.window.startTick && note.endTick <= issue.window.endTick) { "Qwen targeted a note outside the Critic issue window." }
            when (operation.kind) {
                FullSongEnhancementOperationKind.REVOICE_CHORD,
                FullSongEnhancementOperationKind.SIMPLIFY_BASS_LEAP,
                FullSongEnhancementOperationKind.CORRECT_CHORD_CLASH -> require(operation.pitch != null) {
                    "Qwen omitted the replacement pitch for a pitch operation."
                }
                else -> Unit
            }
        }
    }

    @Serializable private data class ModelPlan(val operations: List<FullSongEnhancementOperation> = emptyList())

    @Serializable private data class ModelInput(
        val issues: List<ModelIssue>,
        val targets: List<ModelTarget>
    )

    @Serializable private data class ModelIssue(
        val id: String,
        val category: FullSongIssueCategory,
        val targetRole: String,
        val occurrenceId: String? = null,
        val startTick: Long,
        val endTick: Long,
        val reasonCode: String,
        val suggestedCorrections: List<FullSongCorrectionFamily>
    ) {
        constructor(issue: FullSongIssue) : this(
            id = issue.id,
            category = issue.category,
            targetRole = issue.targetRole,
            occurrenceId = issue.occurrenceId,
            startTick = issue.window.startTick,
            endTick = issue.window.endTick,
            reasonCode = issue.reasonCode,
            suggestedCorrections = issue.suggestedCorrections
        )
    }

    @Serializable private data class ModelTarget(
        val id: String,
        val role: String,
        val occurrenceId: String? = null,
        val notes: List<ModelNote>
    )

    @Serializable private data class ModelNote(
        val id: String,
        val pitch: Int,
        val velocity: Int,
        val startTick: Long,
        val endTick: Long
    ) {
        constructor(note: FullSongEnhancementNote) : this(
            id = note.id,
            pitch = note.pitch,
            velocity = note.velocity,
            startTick = note.startTick,
            endTick = note.endTick
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        val MODEL_ID = Regex("[A-Za-z0-9._:-]{1,120}")
        const val MAX_MODEL_NOTES = 48
        const val SYSTEM_PROMPT = """
            Return exactly one JSON object and no markdown, prose, commands, paths, hashes, or extra keys.
            Return only {"operations":[...]}. Every operation must use one supplied Critic issue ID, target ID, and note ID.
            Use only these uppercase kinds: REVOICE_CHORD, SIMPLIFY_BASS_LEAP, REDUCE_DENSITY, REMOVE_COLLISION,
            ADJUST_TIMING, ADJUST_VELOCITY, ADJUST_DURATION, CORRECT_CHORD_CLASH, ADJUST_TRANSITION_NOTE.
            Keep every edit inside its Critic window and target role/occurrence. Do not alter structure, tempo, meter,
            channels, files, or instruments. Respect the supplied five-percent total and two-percent add/delete budgets.
            Use an empty operations array when no safe bounded correction is justified.
        """
    }
}

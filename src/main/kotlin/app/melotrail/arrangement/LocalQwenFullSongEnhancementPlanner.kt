package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Local-Qwen adapter for the bounded Full-Song Enhance stage. The model chooses
 * only operations; application-owned identities are stamped after validation.
 */
class LocalQwenFullSongEnhancementPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient(),
    private val modelIdentity: String = System.getenv("QWEN_MODEL")?.takeIf { it.matches(MODEL_ID) } ?: "qwen-local"
) : FullSongEnhancementPlanner {
    override fun plan(input: FullSongEnhancementInput): String {
        val constrainedClient = if (client is JsonSchemaLocalQwenClient) {
            LocalQwenClient { systemPrompt, userPrompt -> client.completeJsonSchema(systemPrompt, userPrompt, RESPONSE_SCHEMA) }
        } else client
        val response = requestQwenWithAutomaticRetries(constrainedClient, SYSTEM_PROMPT, json.encodeToString(modelInput(input))) { text ->
            val modelPlan = json.decodeFromString<ModelPlan>(text)
            // A Critic window may name the same note in more than one issue. The
            // model often responds with one edit per issue, while the executor
            // deliberately permits only one edit per note. Preserve the first
            // proposed edit deterministically; the normal authority and budget
            // validation below still has to accept it.
            val modelOperations = canonicalize(modelPlan.operations, input)
            val operations = modelOperations.ifEmpty { deterministicMaskingFallback(input) }
            validate(operations, input)
            json.encodeToString(FullSongEnhancementPlan(
                inputSha256 = input.inputSha256,
                contextSha256 = input.contextSha256,
                criticInputSha256 = input.criticInputSha256,
                criticReportSha256 = input.criticReportSha256,
                modelIdentity = if (modelOperations.isEmpty() && operations.isNotEmpty()) FALLBACK_IDENTITY else modelIdentity,
                operations = operations
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
            totalActionableIssueCount = input.totalActionableIssueCount,
            batchIndex = input.batchIndex,
            batchCount = input.batchCount,
            issues = input.issues.map(::ModelIssue),
            targets = input.targets.mapNotNull { target ->
                notesByTarget[target.id]?.let { notes ->
                    ModelTarget(
                        id = target.id,
                        role = target.role,
                        occurrenceId = target.occurrenceId,
                        totalNoteCount = target.notes.size,
                        maxOperations = input.policy.totalBudget(target.notes.size),
                        maxAddDeleteOperations = input.policy.additionDeletionBudget(target.notes.size),
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
            occurrenceMatches(issue, target) &&
            note.startTick >= issue.window.startTick && note.endTick <= issue.window.endTick

    /** A whole-song role target is safely scoped by the immutable Critic window. */
    private fun occurrenceMatches(issue: FullSongIssue, target: FullSongEnhancementTarget): Boolean =
        issue.occurrenceId == null || target.occurrenceId == null || issue.occurrenceId == target.occurrenceId

    /**
     * A masking report identifies an accompaniment note that is at least as
     * loud as the protected melody. If Qwen chooses no safe edit, lower the
     * overlapping accompaniment by the measured delta plus one. This is an
     * expression-only correction: no note timing, pitch, harmony, or anchor is
     * changed, and the ordinary target budget still applies.
     */
    private fun deterministicMaskingFallback(input: FullSongEnhancementInput): List<FullSongEnhancementOperation> = input.issues
        .asSequence()
        .filter { issue -> issue.category == FullSongIssueCategory.MASKING }
        .flatMap { issue -> input.targets.asSequence()
            .filter { target -> target.role == issue.targetRole && occurrenceMatches(issue, target) && input.policy.totalBudget(target.notes.size) > 0 }
            .flatMap { target -> target.notes.asSequence()
                .filter { note -> overlaps(note, issue) && note.velocity > 1 }
                .map { note -> issue to (target to note) }
            }
        }
        .sortedWith(compareBy<Pair<FullSongIssue, Pair<FullSongEnhancementTarget, FullSongEnhancementNote>>> { it.first.window.startTick }
            .thenBy { it.second.first.id }.thenBy { it.second.second.startTick }.thenBy { it.second.second.id })
        .map { (issue, targetAndNote) ->
            val (target, note) = targetAndNote
            val observedDelta = issue.observed.singleOrNull { it.name == "velocityDelta" }?.value?.toInt()?.coerceAtLeast(0) ?: 0
            FullSongEnhancementOperation(
                kind = FullSongEnhancementOperationKind.ADJUST_VELOCITY,
                issueId = issue.id,
                targetId = target.id,
                noteId = note.id,
                velocityDelta = -minOf(note.velocity - 1, observedDelta + 1)
            )
        }
        .distinctBy { operation -> operation.targetId to operation.noteId }
        .groupBy(FullSongEnhancementOperation::targetId)
        .flatMap { (targetId, operations) ->
            val target = requireNotNull(input.targets.singleOrNull { it.id == targetId })
            operations.take(input.policy.totalBudget(target.notes.size))
        }
        .take(256)
        .toList()

    private fun overlaps(note: FullSongEnhancementNote, issue: FullSongIssue): Boolean =
        note.startTick < issue.window.endTick && note.endTick > issue.window.startTick

    private fun operationIsScopedToIssue(operation: FullSongEnhancementOperation, note: FullSongEnhancementNote, issue: FullSongIssue): Boolean =
        if (operation.kind == FullSongEnhancementOperationKind.ADJUST_VELOCITY) overlaps(note, issue)
        else note.startTick >= issue.window.startTick && note.endTick <= issue.window.endTick

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
            require(occurrenceMatches(issue, target)) { "Qwen targeted an occurrence not named by its Critic issue." }
            require(operationIsScopedToIssue(operation, note, issue)) { "Qwen targeted a note outside the Critic issue window." }
            operation.tickDelta?.let { delta ->
                require(note.startTick + delta >= target.offsetTicks && note.startTick + delta >= issue.window.startTick && note.endTick + delta <= issue.window.endTick) {
                    "Qwen moved a note outside its Critic issue window."
                }
            }
            operation.durationDelta?.let { delta ->
                require(note.endTick + delta > note.startTick) { "Qwen proposed a non-positive note duration." }
            }
            operation.velocityDelta?.let { delta ->
                require(note.velocity + delta in 1..127) { "Qwen proposed a velocity outside MIDI range." }
            }
            operation.pitch?.let { pitch ->
                if (target.role == "piano") {
                    require(kotlin.math.abs(pitch - note.pitch) <= 2) {
                        "Qwen moved a piano melody note by more than two semitones."
                    }
                }
                require(chordAllows(input, note.startTick, pitch)) {
                    "Qwen proposed a pitch outside the active canonical chord."
                }
            }
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

    private fun canonicalize(
        proposed: List<FullSongEnhancementOperation>,
        input: FullSongEnhancementInput
    ): List<FullSongEnhancementOperation> {
        val targets = input.targets.associateBy(FullSongEnhancementTarget::id)
        val acceptedPerTarget = mutableMapOf<String, Int>()
        val acceptedDeletesPerTarget = mutableMapOf<String, Int>()
        return proposed.distinctBy { it.targetId to it.noteId }.mapNotNull { operation ->
            val target = targets[operation.targetId] ?: return@mapNotNull operation
            val totalBudget = input.policy.totalBudget(target.notes.size)
            if ((acceptedPerTarget[target.id] ?: 0) >= totalBudget) return@mapNotNull null
            val deletes = operation.kind in setOf(
                FullSongEnhancementOperationKind.REDUCE_DENSITY,
                FullSongEnhancementOperationKind.REMOVE_COLLISION
            )
            if (deletes && (acceptedDeletesPerTarget[target.id] ?: 0) >= input.policy.additionDeletionBudget(target.notes.size)) {
                return@mapNotNull null
            }
            val note = target.notes.singleOrNull { it.id == operation.noteId }
            val bounded = if (target.role == "piano" && operation.pitch != null && note != null) {
                val candidates = (note.pitch - 2..note.pitch + 2).filter { pitch ->
                    pitch in 0..127 && chordAllows(input, note.startTick, pitch)
                }
                if (candidates.isEmpty()) return@mapNotNull null
                operation.copy(pitch = candidates.minWith(compareBy<Int> { kotlin.math.abs(it - note.pitch) }
                    .thenBy { kotlin.math.abs(it - operation.pitch) }.thenBy { it }))
            } else operation
            acceptedPerTarget[target.id] = (acceptedPerTarget[target.id] ?: 0) + 1
            if (deletes) acceptedDeletesPerTarget[target.id] = (acceptedDeletesPerTarget[target.id] ?: 0) + 1
            bounded
        }
    }

    private fun chordAllows(input: FullSongEnhancementInput, tick: Long, pitch: Int): Boolean =
        input.authority.harmony.singleOrNull { tick in it.startTick until it.endTick }?.let { chord ->
            pitch % 12 in chord.chord.quality.intervals.map { (it + chord.chord.rootChromatic) % 12 }
        } == true

    @Serializable private data class ModelPlan(val operations: List<FullSongEnhancementOperation> = emptyList())

    @Serializable private data class ModelInput(
        val totalActionableIssueCount: Int,
        val batchIndex: Int,
        val batchCount: Int,
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
        val totalNoteCount: Int,
        val maxOperations: Int,
        val maxAddDeleteOperations: Int,
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
        // Explanatory model-only fields have no executable meaning. The emitted
        // operation is still validated against the authoritative critic input.
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
        val MODEL_ID = Regex("[A-Za-z0-9._:-]{1,120}")
        const val MAX_MODEL_NOTES = 48
        const val FALLBACK_IDENTITY = "deterministic-masking-fallback-v1"
        val RESPONSE_SCHEMA = Json.parseToJsonElement(
            """{
              "type":"object",
              "additionalProperties":false,
              "properties":{
                "operations":{"type":"array","maxItems":256,"items":{
                  "type":"object",
                  "additionalProperties":false,
                  "properties":{
                    "kind":{"type":"string","enum":["REVOICE_CHORD","SIMPLIFY_BASS_LEAP","REDUCE_DENSITY","REMOVE_COLLISION","ADJUST_TIMING","ADJUST_VELOCITY","ADJUST_DURATION","CORRECT_CHORD_CLASH","ADJUST_TRANSITION_NOTE"]},
                    "issueId":{"type":"string"},"targetId":{"type":"string"},"noteId":{"type":"string"},
                    "relatedNoteId":{"type":"string"},"pitch":{"type":"integer"},"tickDelta":{"type":"integer"},
                    "velocityDelta":{"type":"integer"},"durationDelta":{"type":"integer"}
                  },
                  "required":["kind","issueId","targetId","noteId"]
                }}
              },
              "required":["operations"]
            }"""
        ).jsonObject
        const val SYSTEM_PROMPT = """
            Return exactly one JSON object and no markdown, prose, commands, paths, hashes, or extra keys.
            Return only {"operations":[...]}. Every operation must use one supplied Critic issue ID, target ID, and note ID.
            Return at most one operation for each target ID and note ID pair, even when several issues name that note.
            Use only these uppercase kinds: REVOICE_CHORD, SIMPLIFY_BASS_LEAP, REDUCE_DENSITY, REMOVE_COLLISION,
            ADJUST_TIMING, ADJUST_VELOCITY, ADJUST_DURATION, CORRECT_CHORD_CLASH, ADJUST_TRANSITION_NOTE.
            Keep every edit inside its Critic window and target role/occurrence. Do not alter structure, tempo, meter,
            channels, files, or instruments. Respect the supplied five-percent total and two-percent add/delete budgets.
            Piano replacement pitches must stay within two semitones of the supplied note. Every replacement pitch must
            be a chord tone of the active harmony; use density, timing, duration, or velocity edits when no safe pitch is supplied.
            Use an empty operations array when no safe bounded correction is justified.
        """
    }
}

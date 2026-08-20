package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Strict, fakeable local-model adapter. The model receives serialized musical
 * context and at most 512 note summaries; it never receives a path, command,
 * instrument name, or writable capability.
 */
class LocalQwenEnhancementPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient(),
    private val identity: EnhancementModelIdentity,
    private val processorId: String = "local-qwen-enhancement",
    private val processorVersion: String = "1",
    private val templateVersion: String = "enhancement-v2"
) : EnhancementPlanner {
    override fun plan(context: MusicalProcessingContext): EnhancementPlan {
        context.requireValid()
        require(context.intensity != EnhancementIntensity.OFF) { "Off enhancement must not invoke a model" }
        require(identity.license.lowercase() !in setOf("unknown", "unlicensed", "none")) {
            "A known local-model license is required before enhancement can be commercial-ready."
        }
        val policy = EnhancementPolicy.forIntensity(context.intensity)
        val response = client.complete(SYSTEM_PROMPT, json.encodeToString(EnhancementModelInput(context, policy)))
        val modelPlan = try { json.decodeFromString<ModelPlan>(response) }
        catch (error: Exception) { throw IllegalArgumentException("Local model returned malformed enhancement JSON", error) }
        return EnhancementPlan(
            // These values bind the accepted plan to the exact application-owned
            // input. A model cannot be relied on to copy long hashes verbatim.
            version = context.version,
            subjectHash = subjectHash(context),
            inputSha256 = context.correctedInputSha256,
            contextSha256 = context.contextSha256,
            processorId = processorId,
            processorVersion = processorVersion,
            placeholder = false,
            model = identity,
            goals = modelPlan.goals.map(::goalFromWire).toSet(),
            templateVersion = templateVersion,
            edits = modelPlan.edits.mapNotNull { editFromWire(it, policy) }
        ).also { it.requireValid(context, policy) }
    }

    private fun goalFromWire(value: String): EnhancementGoal = MODEL_GOALS[value]
        ?: throw IllegalArgumentException("Local model returned an unsupported enhancement goal")

    /** Invalid model proposals are omitted rather than clamped or applied. */
    private fun editFromWire(edit: ModelEdit, policy: EnhancementPolicy): EnhancementEdit? {
        val kind = MODEL_EDIT_KINDS[edit.kind]
            ?: throw IllegalArgumentException("Local model returned an unsupported enhancement edit kind")
        val withinPolicy = when (kind) {
            EnhancementEditKind.VELOCITY -> kotlin.math.abs(edit.value) <= policy.maximumVelocityDelta
            EnhancementEditKind.TIMING -> kotlin.math.abs(edit.value) <= policy.maximumTimingShiftMs
            EnhancementEditKind.PITCH -> kotlin.math.abs(edit.value) <= 2
        }
        if (!withinPolicy) return null
        return EnhancementEdit(kind, edit.noteId, edit.value, goalFromWire(edit.goal), edit.reason)
    }

    @Serializable private data class EnhancementModelInput(
        val context: MusicalProcessingContext,
        val policy: EnhancementPolicyWire
    ) {
        constructor(context: MusicalProcessingContext, policy: EnhancementPolicy) : this(
            context, EnhancementPolicyWire(policy.maximumOperations, policy.maximumEdits, policy.maximumTimingShiftMs, policy.maximumVelocityDelta)
        )
    }
    @Serializable private data class EnhancementPolicyWire(
        val maximumOperations: Int, val maximumEdits: Int, val maximumTimingShiftMs: Int, val maximumVelocityDelta: Int
    )
    @Serializable private data class ModelPlan(
        // Optional legacy fields are ignored. Keeping them accepted makes a
        // running local model's prior response shape harmless during upgrades.
        val version: Int? = null,
        val subjectHash: String? = null,
        val inputSha256: String? = null,
        val contextSha256: String? = null,
        val goals: List<String> = emptyList(),
        val edits: List<ModelEdit> = emptyList()
    )
    @Serializable private data class ModelEdit(
        val kind: String,
        val noteId: String,
        val value: Long,
        val goal: String = "flow_contour",
        val reason: String = "bounded musical adjustment"
    )

    @OptIn(ExperimentalSerializationApi::class)
    private companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        const val SYSTEM_PROMPT = """
            Return exactly one JSON object and no markdown, prose, commands, paths, or extra keys.
            Return only goals and edits. Do not return version, subjectHash, inputSha256, or contextSha256.
            Select only these goals:
            phrase_ending, flow_contour, chord_clash, passing_note, repetition_reduction.
            Each edit must be {"kind":"velocity|timing|pitch","noteId":"n-00000","value":signed integer,
            "goal":"one allowed goal","reason":"brief bounded rationale"}. Edits may target only supplied note IDs.
            For every edit, abs(value) must be at most maximumVelocityDelta for velocity, maximumTimingShiftMs for timing,
            or 2 for pitch. Omit an edit that cannot meet its limit.
            Do not add or remove notes, alter harmony, tempo, meter, duration, channels, files, or instruments.
            Respect the supplied policy. Prefer zero edits when no bounded improvement is justified.
        """
        val MODEL_GOALS = mapOf(
            "phrase_ending" to EnhancementGoal.PHRASE_ENDING,
            "flow_contour" to EnhancementGoal.FLOW_CONTOUR,
            "chord_clash" to EnhancementGoal.CHORD_CLASH,
            "passing_note" to EnhancementGoal.PASSING_NOTE,
            "repetition_reduction" to EnhancementGoal.REPETITION_REDUCTION
        )
        val MODEL_EDIT_KINDS = mapOf(
            "velocity" to EnhancementEditKind.VELOCITY,
            "timing" to EnhancementEditKind.TIMING,
            "pitch" to EnhancementEditKind.PITCH
        )
    }
}

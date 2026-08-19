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
            version = modelPlan.version,
            subjectHash = modelPlan.subjectHash,
            inputSha256 = modelPlan.inputSha256,
            contextSha256 = modelPlan.contextSha256,
            processorId = processorId,
            processorVersion = processorVersion,
            placeholder = false,
            model = identity,
            goals = modelPlan.goals.toSet(),
            templateVersion = templateVersion,
            edits = modelPlan.edits
        ).also { it.requireValid(context, policy) }
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
        val version: Int,
        val subjectHash: String,
        val inputSha256: String,
        val contextSha256: String,
        val goals: List<EnhancementGoal> = emptyList(),
        val edits: List<EnhancementEdit> = emptyList()
    )

    @OptIn(ExperimentalSerializationApi::class)
    private companion object {
        val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
        const val SYSTEM_PROMPT = """
            Return exactly one JSON object and no markdown, prose, commands, paths, or extra keys.
            Echo version, subjectHash, inputSha256, and contextSha256 exactly. Select only these goals:
            phrase_ending, flow_contour, chord_clash, passing_note, repetition_reduction.
            Edits may target only supplied note IDs and use velocity, timing, or pitch. `value` is a signed delta.
            Do not add or remove notes, alter harmony, tempo, meter, duration, channels, files, or instruments.
            Respect the supplied policy. Prefer zero edits when no bounded improvement is justified.
        """
    }
}

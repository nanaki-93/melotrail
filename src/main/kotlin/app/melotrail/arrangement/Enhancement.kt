package app.melotrail.arrangement

import app.melotrail.harmony.ChordProgression
import app.melotrail.music.MusicalKey
import app.melotrail.profile.CompositionProfileCatalog
import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import app.melotrail.profile.ResolvedCompositionProfile
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage

/** The only persisted choices for the bounded creative-enhancement boundary. */
@Serializable
enum class EnhancementIntensity { OFF, SUBTLE, BALANCED, CREATIVE }

/** The selected musical representation is never inferred from an artifact's presence. */
@Serializable
enum class EnhancementSelection { PENDING, CORRECTED, ENHANCED }

/** Code-owned melody edit vocabulary. Tempo, meter, structure and paths remain outside the model contract. */
@Serializable
enum class EnhancementEditKind { VELOCITY, TIMING, PITCH, DURATION, ADD_NOTE, REMOVE_NOTE }

/** The model may select only these bounded musical intentions; Kotlin owns the edit mechanics. */
@Serializable
enum class EnhancementGoal { PHRASE_ENDING, FLOW_CONTOUR, CHORD_CLASH, PASSING_NOTE, REPETITION_REDUCTION }

@Serializable
data class EnhancementEdit(
    val kind: EnhancementEditKind,
    val noteId: String,
    val value: Long = 0,
    val goal: EnhancementGoal = EnhancementGoal.FLOW_CONTOUR,
    val reason: String = "bounded musical adjustment",
    /** ADD_NOTE payload. Existing-note edits must leave these fields null. */
    val pitch: Int? = null,
    val velocity: Int? = null,
    val startTick: Long? = null,
    val durationTicks: Long? = null,
    val channel: Int? = null,
    val anchorNoteId: String? = null
) {
    init {
        require(ENHANCEMENT_EDIT_ID.matches(noteId)) { "Enhancement note ID is invalid" }
        require(anchorNoteId == null || ENHANCEMENT_NOTE_ID.matches(anchorNoteId)) { "Enhancement anchor note ID is invalid" }
        require(value in -9_600L..9_600L) { "Enhancement edit value is outside the hard safety range" }
        pitch?.let { require(it in 0..127) { "Enhancement pitch is invalid" } }
        velocity?.let { require(it in 1..127) { "Enhancement velocity is invalid" } }
        startTick?.let { require(it >= 0) { "Enhancement start tick is invalid" } }
        durationTicks?.let { require(it > 0) { "Enhancement duration is invalid" } }
        channel?.let { require(it in 0..15) { "Enhancement channel is invalid" } }
        require(reason.isNotBlank() && reason.length <= 160 && reason.none { it.isISOControl() }) { "Enhancement edit reason is invalid" }
    }
}

/** Bounded limits are selected in code; a planner cannot loosen them. */
data class EnhancementPolicy(
    val intensity: EnhancementIntensity,
    val maximumOperations: Int,
    val maximumEdits: Int,
    val maximumIdentityDistancePercent: Int,
    val maximumTimingShiftMs: Int,
    val maximumVelocityDelta: Int
) {
    init {
        require(maximumOperations in 0..64 && maximumEdits in 0..64) { "Enhancement edit budget is invalid" }
        require(maximumIdentityDistancePercent in 0..100 && maximumTimingShiftMs in 0..80 && maximumVelocityDelta in 0..127) {
            "Enhancement identity budget is invalid"
        }
    }

    companion object {
        fun forIntensity(intensity: EnhancementIntensity, profile: ResolvedCompositionProfile? = null): EnhancementPolicy {
            val timing = profile?.timingToleranceMs ?: 20
            val velocity = profile?.velocityTolerance ?: 12
            return when (intensity) {
                EnhancementIntensity.OFF -> EnhancementPolicy(intensity, 0, 0, 0, 0, 0)
                EnhancementIntensity.SUBTLE -> EnhancementPolicy(intensity, 4, 8, 5, timing.coerceAtMost(12), velocity.coerceAtMost(8))
                EnhancementIntensity.BALANCED -> EnhancementPolicy(intensity, 8, 16, 12, timing.coerceAtMost(28), velocity.coerceAtMost(20))
                EnhancementIntensity.CREATIVE -> EnhancementPolicy(intensity, 16, 32, 25, timing.coerceAtMost(48), velocity.coerceAtMost(36))
            }
        }
    }
}

@Serializable
data class EnhancementChordContext(
    val sectionId: String,
    val chords: List<String>
) {
    init {
        require(ENHANCEMENT_ID.matches(sectionId) && chords.size <= 32 && chords.all { it.matches(Regex("[A-G][#b]?[a-zA-Z0-9+()/-]{0,31}")) }) {
            "Enhancement chord context is invalid"
        }
    }
}

@Serializable
data class EnhancementProfileContext(
    val profile: CompositionProfileRef,
    val mood: MoodRef,
    val resolvedHash: String,
    val enhancementAmountPercent: Int,
    val timingToleranceMs: Int,
    val velocityTolerance: Int
) {
    init {
        profile.requireValid(); mood.requireValid()
        require(ENHANCEMENT_HASH.matches(resolvedHash) && enhancementAmountPercent in 0..100 && timingToleranceMs in 0..80 && velocityTolerance in 0..127) {
            "Enhancement profile context is invalid"
        }
    }
}

/** A bounded, path-free description of one note. It is the only note-level model input. */
@Serializable
data class EnhancementNoteSummary(
    val id: String,
    val channel: Int,
    val pitch: Int,
    val velocity: Int,
    val startTick: Long,
    val endTick: Long,
    val phrase: Int
) {
    init {
        require(ENHANCEMENT_NOTE_ID.matches(id) && channel in 0..15 && pitch in 0..127 && velocity in 1..127 &&
            startTick >= 0 && endTick > startTick && phrase in 0..255) { "Enhancement note summary is invalid" }
    }
}

/**
 * Complete, path-free, deterministic planner context.  [contextSha256] is a
 * hash of this value with that one field blank, so every input below affects
 * cache identity while paths and prompts never become protocol fields.
 */
@Serializable
data class MusicalProcessingContext(
    val version: Int = VERSION,
    val projectKey: MusicalKey,
    val scalePitchClasses: List<Int>,
    val sectionChords: List<EnhancementChordContext>,
    val bpm: Int,
    val ppq: Int = 480,
    val meterNumerator: Int,
    val meterDenominator: Int,
    val profile: EnhancementProfileContext,
    val sectionId: String,
    val partId: String,
    val correctedInputSha256: String,
    val intensity: EnhancementIntensity,
    val seed: Long,
    val pipelineVersion: String,
    val notes: List<EnhancementNoteSummary> = emptyList(),
    val contextSha256: String
) {
    fun requireValid() {
        require(version == VERSION && projectKey.isExecutable && scalePitchClasses == projectKey.scalePitchClasses().map { it.chromatic }) {
            "Enhancement key/scale context is invalid"
        }
        require(sectionChords.map(EnhancementChordContext::sectionId).distinct().size == sectionChords.size) { "Enhancement sections are duplicated" }
        require(bpm in 30..240 && ppq in 24..9_600 && meterNumerator in 1..12 && meterDenominator in setOf(1, 2, 4, 8, 16)) { "Enhancement tempo or meter is invalid" }
        require(ENHANCEMENT_ID.matches(sectionId) && ENHANCEMENT_ID.matches(partId) && ENHANCEMENT_HASH.matches(correctedInputSha256) &&
            ENHANCEMENT_VERSION.matches(pipelineVersion) && ENHANCEMENT_HASH.matches(contextSha256)) { "Enhancement identity is invalid" }
        require(notes.size <= 512 && notes.map(EnhancementNoteSummary::id).distinct().size == notes.size) { "Enhancement note context is invalid" }
        require(contextSha256 == MusicalProcessingContextHasher.hash(this)) { "Enhancement context hash does not match its contents" }
        profile.let { /* constructor validates it */ }
    }

    fun cacheKey(): String = contextSha256

    companion object { const val VERSION = 3 }
}

/** Strict wire plan. A non-placeholder run must identify its licensed model. */
@Serializable
data class EnhancementModelIdentity(val provider: String, val model: String, val version: String, val license: String) {
    init {
        require(ENHANCEMENT_ID.matches(provider) && ENHANCEMENT_ID.matches(model) && ENHANCEMENT_VERSION.matches(version) && ENHANCEMENT_LICENSE.matches(license)) {
            "Enhancement model identity is invalid"
        }
    }
}

@Serializable
data class EnhancementPlan(
    val version: Int = MusicalProcessingContext.VERSION,
    val subjectHash: String,
    val inputSha256: String,
    val contextSha256: String,
    val processorId: String,
    val processorVersion: String,
    val placeholder: Boolean,
    val model: EnhancementModelIdentity? = null,
    val goals: Set<EnhancementGoal> = emptySet(),
    val templateVersion: String = "enhancement-v1",
    val edits: List<EnhancementEdit> = emptyList()
) {
    fun requireValid(context: MusicalProcessingContext, policy: EnhancementPolicy) = EnhancementPlanValidator.requireValid(this, context, policy)
}

@Serializable
data class EnhancementEditReport(
    val version: Int = MusicalProcessingContext.VERSION,
    val subjectHash: String,
    val inputSha256: String,
    val outputSha256: String?,
    val contextSha256: String,
    val intensity: EnhancementIntensity,
    val processorId: String,
    val processorVersion: String,
    val placeholder: Boolean,
    val model: EnhancementModelIdentity? = null,
    val appliedEdits: List<EnhancementEdit> = emptyList(),
    val acceptedPlanSha256: String? = null,
    val identityDistancePercent: Int = 0,
    val anchorsRetained: Boolean = true,
    val message: String
) {
    fun requireValid() {
        require(version == MusicalProcessingContext.VERSION && ENHANCEMENT_HASH.matches(subjectHash) && ENHANCEMENT_HASH.matches(inputSha256) &&
            (outputSha256 == null || ENHANCEMENT_HASH.matches(outputSha256)) && ENHANCEMENT_HASH.matches(contextSha256) &&
            ENHANCEMENT_ID.matches(processorId) && ENHANCEMENT_VERSION.matches(processorVersion) && message.isNotBlank() && message.length <= 240) {
            "Enhancement report is invalid"
        }
        require(placeholder || model != null) { "Non-placeholder enhancement reports require model identity, version, and license" }
        require(acceptedPlanSha256 == null || ENHANCEMENT_HASH.matches(acceptedPlanSha256)) { "Enhancement accepted-plan hash is invalid" }
        require(identityDistancePercent in 0..100 && anchorsRetained) { "Enhancement identity evidence is invalid" }
    }
}

fun interface EnhancementPlanner { fun plan(context: MusicalProcessingContext): EnhancementPlan }
fun interface EnhancementPlanApplier { fun apply(input: Path, output: Path?, context: MusicalProcessingContext, plan: EnhancementPlan): EnhancementEditReport }

/**
 * Application-facing orchestration with the one important short circuit: Off
 * resolves the corrected artifact without invoking either the planner or an
 * enhancer.  Callers retain prior enhanced evidence independently.
 */
class EnhancementExecutionService(
    private val planner: EnhancementPlanner,
    private val applier: EnhancementPlanApplier
) {
    fun enhance(input: Path, output: Path?, context: MusicalProcessingContext): EnhancementEditReport {
        context.requireValid()
        if (context.intensity == EnhancementIntensity.OFF) {
            require(output == null) { "Off enhancement may not publish an artifact" }
            return EnhancementEditReport(
                subjectHash = subjectHash(context), inputSha256 = context.correctedInputSha256, outputSha256 = null,
                contextSha256 = context.contextSha256, intensity = context.intensity, processorId = "off", processorVersion = "1",
                placeholder = true, message = "Off selected corrected MIDI; no enhancement processor was called."
            ).also(EnhancementEditReport::requireValid)
        }
        val plan = planner.plan(context)
        plan.requireValid(context, EnhancementPolicy.forIntensity(context.intensity))
        return applier.apply(input, requireNotNull(output) { "A non-Off enhancement requires an output destination" }, context, plan)
            .also(EnhancementEditReport::requireValid)
    }
}

object EnhancementPlanValidator {
    fun requireValid(plan: EnhancementPlan, context: MusicalProcessingContext, policy: EnhancementPolicy) {
        context.requireValid()
        require(plan.version == context.version && plan.subjectHash == subjectHash(context) && plan.inputSha256 == context.correctedInputSha256 && plan.contextSha256 == context.contextSha256) {
            "Enhancement plan does not echo its exact subject, input, and context"
        }
        require(ENHANCEMENT_ID.matches(plan.processorId) && ENHANCEMENT_VERSION.matches(plan.processorVersion)) { "Enhancement processor identity is invalid" }
        require(plan.placeholder || plan.model != null) { "Non-placeholder enhancement plans require model identity, version, and license" }
        require(ENHANCEMENT_VERSION.matches(plan.templateVersion)) { "Enhancement prompt-template version is invalid" }
        require(plan.goals.size <= policy.maximumOperations) { "Enhancement plan exceeds its goal budget" }
        require(plan.edits.size <= policy.maximumEdits && plan.edits.map(EnhancementEdit::noteId).distinct().size == plan.edits.size) { "Enhancement plan exceeds its edit budget" }
        require(plan.edits.map(EnhancementEdit::kind).distinct().size <= policy.maximumOperations) { "Enhancement plan exceeds its operation budget" }
        plan.edits.forEach { edit ->
            when (edit.kind) {
                EnhancementEditKind.VELOCITY -> require(kotlin.math.abs(edit.value) <= policy.maximumVelocityDelta) { "Enhancement velocity edit exceeds policy" }
                EnhancementEditKind.TIMING -> require(kotlin.math.abs(edit.value) <= policy.maximumTimingShiftMs) { "Enhancement timing edit exceeds policy" }
                EnhancementEditKind.PITCH -> require(kotlin.math.abs(edit.value) <= 2) { "Enhancement pitch edit exceeds the bounded range" }
                EnhancementEditKind.DURATION -> require(edit.value > 0) { "Enhancement duration must be positive" }
                EnhancementEditKind.REMOVE_NOTE -> require(ENHANCEMENT_NOTE_ID.matches(edit.noteId) && edit.value == 0L) { "Enhancement removal must reference an existing note" }
                EnhancementEditKind.ADD_NOTE -> require(
                    ENHANCEMENT_ADDED_NOTE_ID.matches(edit.noteId) && edit.value == 0L && edit.pitch != null && edit.velocity != null &&
                        edit.startTick != null && edit.durationTicks != null && edit.channel != null && edit.anchorNoteId != null
                ) { "Enhancement addition is incomplete" }
            }
        }
        require(policy.intensity != EnhancementIntensity.OFF || plan.edits.isEmpty()) { "Off enhancement cannot contain edits" }
    }
}

/** Strict local-model boundary: malformed JSON and unknown keys are rejected before planning can affect MIDI. */
object EnhancementPlanCodec {
    fun decode(value: String, context: MusicalProcessingContext, policy: EnhancementPolicy): EnhancementPlan = try {
        JSON.decodeFromString<EnhancementPlan>(value).also { it.requireValid(context, policy) }
    } catch (error: Exception) {
        throw IllegalArgumentException("Enhancement plan is malformed or outside the allowed contract", error)
    }
}

/** The MVP boundary is deliberately transparent: it produces zero edits and labels itself as a placeholder. */
class TransparentNoOpEnhancementProcessor : EnhancementPlanner, EnhancementPlanApplier {
    override fun plan(context: MusicalProcessingContext): EnhancementPlan = EnhancementPlan(
        subjectHash = subjectHash(context), inputSha256 = context.correctedInputSha256, contextSha256 = context.contextSha256,
        processorId = "transparent-noop", processorVersion = "1", placeholder = true
    ).also { it.requireValid(context, EnhancementPolicy.forIntensity(context.intensity)) }

    override fun apply(input: Path, output: Path?, context: MusicalProcessingContext, plan: EnhancementPlan): EnhancementEditReport {
        val policy = EnhancementPolicy.forIntensity(context.intensity)
        plan.requireValid(context, policy)
        require(enhancementSha256(input) == context.correctedInputSha256) { "Corrected MIDI changed before enhancement" }
        require(plan.placeholder && plan.edits.isEmpty()) { "The transparent MVP processor only accepts its no-op plan" }
        if (context.intensity == EnhancementIntensity.OFF) {
            require(output == null) { "Off enhancement may not publish an artifact" }
            return report(context, plan, null, "Off selected corrected MIDI; no enhancement processor was called.")
        }
        val destination = requireNotNull(output) { "A non-Off enhancement requires an output destination" }
        Files.createDirectories(requireNotNull(destination.parent))
        Files.copy(input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        require(enhancementSha256(destination) == context.correctedInputSha256) { "Transparent enhancement output is not byte-identical" }
        return report(context, plan, context.correctedInputSha256, "MVP placeholder: no musical edits were applied.")
    }

    private fun report(context: MusicalProcessingContext, plan: EnhancementPlan, output: String?, message: String) = EnhancementEditReport(
        subjectHash = subjectHash(context), inputSha256 = context.correctedInputSha256, outputSha256 = output,
        contextSha256 = context.contextSha256, intensity = context.intensity, processorId = plan.processorId,
        processorVersion = plan.processorVersion, placeholder = true, appliedEdits = emptyList(), message = message
    ).also(EnhancementEditReport::requireValid)
}

data class EnhancementSelectionResult(
    val selected: EnhancementSelection,
    val selectedArtifact: WorkflowArtifactReference,
    /** Prior enhanced references stay intact when the musician selects Off. */
    val retainedEnhancedEvidence: WorkflowArtifactReference?
)

object EnhancementSelectionPolicy {
    fun select(intensity: EnhancementIntensity, corrected: WorkflowArtifactReference, existingEnhanced: WorkflowArtifactReference?): EnhancementSelectionResult =
        if (intensity == EnhancementIntensity.OFF) EnhancementSelectionResult(EnhancementSelection.CORRECTED, corrected, existingEnhanced)
        else EnhancementSelectionResult(EnhancementSelection.ENHANCED, requireNotNull(existingEnhanced) { "Enhancement output is required for a non-Off selection" }, existingEnhanced)
}

/** Builds a complete context from canonical project data and one already-validated corrected artifact. */
object MusicalProcessingContextFactory {
    fun build(
        project: Project,
        partId: String,
        correctedInput: Path,
        intensity: EnhancementIntensity = EnhancementIntensity.SUBTLE,
        seed: Long = 0L,
        pipelineVersion: String = "enhancement-v1",
        profiles: CompositionProfileCatalog
    ): MusicalProcessingContext {
        val settings = requireNotNull(project.envelope.compositionSettings?.takeIf { it.complete }) { "Complete project Setup before enhancement." }
        val part = project.parts.singleOrNull { it.id == partId } ?: throw IllegalArgumentException("Unknown enhancement part '$partId'")
        val resolved = profiles.resolve(requireNotNull(settings.profile), requireNotNull(settings.mood))
        val bare = MusicalProcessingContext(
            projectKey = settings.key,
            scalePitchClasses = settings.key.scalePitchClasses().map { it.chromatic },
            sectionChords = project.envelope.harmony?.progressions.orEmpty().sortedBy { it.sectionType.value }.map(::chords),
            bpm = settings.tempo.bpm.toInt(),
            ppq = runCatching { MidiSystem.getSequence(correctedInput.toFile()).resolution }
                .getOrDefault(480),
            meterNumerator = settings.timeSignature.numerator,
            meterDenominator = settings.timeSignature.denominator,
            profile = profileContext(resolved),
            sectionId = part.sectionType.value,
            partId = part.id,
            correctedInputSha256 = enhancementSha256(correctedInput),
            intensity = intensity,
            seed = seed,
            pipelineVersion = pipelineVersion,
            notes = runCatching {
                val ppq = MidiSystem.getSequence(correctedInput.toFile()).resolution
                enhancementNoteSummaries(correctedInput, ppq * 4L / settings.timeSignature.denominator)
            }.getOrElse { emptyList() },
            contextSha256 = "0".repeat(64)
        )
        return bare.copy(contextSha256 = MusicalProcessingContextHasher.hash(bare)).also(MusicalProcessingContext::requireValid)
    }

    private fun chords(progression: ChordProgression) = EnhancementChordContext(
        progression.sectionType.value,
        progression.events.sortedBy { it.order }.map { "${it.root}${it.quality.symbolSuffix}" }
    )
    private fun profileContext(profile: ResolvedCompositionProfile) = EnhancementProfileContext(
        profile.profile, profile.mood, profile.resolvedHash, profile.enhancementAmountPercent, profile.timingToleranceMs, profile.velocityTolerance
    )
}

object MusicalProcessingContextHasher {
    fun hash(context: MusicalProcessingContext): String = enhancementSha256(
        JSON.encodeToString(context.copy(contextSha256 = "")).toByteArray(StandardCharsets.UTF_8)
    )
}

internal fun subjectHash(context: MusicalProcessingContext): String = enhancementSha256("${context.partId}|${context.sectionId}".toByteArray(StandardCharsets.UTF_8))
private fun enhancementNoteSummaries(path: Path, canonicalBeatTicks: Long): List<EnhancementNoteSummary> {
    val identity = MelodyIdentityBuilder.build(path, canonicalBeatTicks)
    require(identity.notes.size <= 512) { "Corrected MIDI exceeds the bounded enhancement note limit" }
    return identity.notes.map { note ->
        EnhancementNoteSummary(note.id.value, note.channel, note.pitch, note.velocity, note.originalStartTick, note.originalEndTick,
            note.phraseId.removePrefix("p-").toInt().coerceAtMost(255))
    }
}
private fun enhancementSha256(path: Path): String = Files.newInputStream(path).use { input ->
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    digest.digest().joinToString("") { "%02x".format(it) }
}
private fun enhancementSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

@OptIn(ExperimentalSerializationApi::class)
private val JSON = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }
private val ENHANCEMENT_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val ENHANCEMENT_NOTE_ID = Regex("m-[0-9a-f]{64}")
private val ENHANCEMENT_ADDED_NOTE_ID = Regex("add-[0-9]{5}")
private val ENHANCEMENT_EDIT_ID = Regex("(?:m-[0-9a-f]{64}|add-[0-9]{5})")
private val ENHANCEMENT_VERSION = Regex("[A-Za-z0-9._-]{1,80}")
private val ENHANCEMENT_LICENSE = Regex("[A-Za-z0-9._+-]{1,80}")
private val ENHANCEMENT_HASH = Regex("[0-9a-f]{64}")

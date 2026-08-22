package app.melotrail.arrangement

import app.melotrail.application.ArrangementGenerationProjection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.math.roundToInt

/**
 * A whole-song musical outline. It deliberately contains no paths, notes,
 * renderer settings, or other executable/renderable behaviour.
 */
@Serializable
data class SongPlan(
    val version: Int,
    /** Retained by the active planner protocol; structured planning writes an empty value. */
    val style: String,
    val energyCurve: List<Double>,
    val sections: List<SongPlanSection>,
    val climaxIndex: Int,
    val ending: SongEnding,
    /** Binds the plan to typed profile/mood/key/meter context, never a style prompt. */
    val contextHash: String? = null
) {
    fun validate(input: SongPlanningInput): SongPlanValidationResult = SongPlanValidator.validate(this, input)

    fun requireValid(input: SongPlanningInput) {
        val validation = validate(input)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class SongPlanSection(
    val index: Int,
    val instanceId: String,
    val partId: String,
    /** One-based occurrence of this source part in the user-controlled structure. */
    val occurrence: Int,
    val purpose: SongSectionPurpose,
    val instrumentProgression: List<String>,
    val transitionIntent: SongTransitionIntent,
    /** Fingerprint of this stable Structure occurrence. */
    val occurrenceHash: String? = null,
    /** Controlled sound requests. */
    val soundIntents: List<InstrumentIntent> = emptyList()
)

@Serializable
enum class SongSectionPurpose {
    @SerialName("introduction") INTRODUCTION,
    @SerialName("development") DEVELOPMENT,
    @SerialName("climax") CLIMAX,
    @SerialName("release") RELEASE,
    @SerialName("conclusion") CONCLUSION
}

@Serializable
enum class SongTransitionIntent {
    @SerialName("none") NONE,
    @SerialName("build") BUILD,
    @SerialName("release") RELEASE
}

@Serializable
enum class SongEnding {
    @SerialName("resolved") RESOLVED,
    @SerialName("fade") FADE,
    @SerialName("open") OPEN
}

@Serializable
data class SongPlanningConstraints(
    val maxInstrumentsPerSection: Int = 3,
    val maxNewInstrumentsPerSection: Int = 1
) {
    fun requireValid() {
        require(maxInstrumentsPerSection in 1..LogicalInstrument.entries.size) {
            "Song-plan max instruments per section must be from 1 to ${LogicalInstrument.entries.size}"
        }
        require(maxNewInstrumentsPerSection in 1..maxInstrumentsPerSection) {
            "Song-plan max new instruments per section must be from 1 to max instruments per section"
        }
    }
}

/** Input to a global planner. Project file and instrument-library paths never cross this boundary. */
data class SongPlanningInput(
    val projectName: String,
    val projectVersion: Int,
    val analyses: Map<String, MidiAnalysis>,
    val structure: List<SectionInstance>,
    val allowedInstruments: List<String>,
    /** Legacy v1 input only. New callers supply [soundContext] and [requestedIntents]. */
    @Deprecated("Use structured soundContext and requestedIntents")
    val style: String? = null,
    val constraints: SongPlanningConstraints = SongPlanningConstraints(),
    val soundContext: ArrangementSoundContext? = null,
    val requestedIntents: List<InstrumentIntent> = emptyList(),
    /**
     * The only project-musical context accepted by the current arrangement
     * protocol. It is built from canonical v4 data, never from model output.
     */
    val canonicalProjection: ArrangementGenerationProjection? = null
) {
    val resolvedStyle: String
        get() = if (soundContext != null) "" else style?.trim().takeUnless { it.isNullOrEmpty() } ?: "unspecified"

    fun sectionsWithIdentity(): List<SongPlanningSectionInstance> =
        SongPlanningSectionInstances.create(structure)

    fun requireValid() {
        val errors = mutableListOf<String>()
        if (projectName.isBlank()) errors += "Project name must not be blank"
        if (projectVersion != Project.CURRENT_VERSION) {
            errors += "Global song planning requires project version ${Project.CURRENT_VERSION} with MIDI analyses"
        }
        if (structure.isEmpty()) errors += "Song structure must not be empty"
        structure.forEachIndexed { position, section ->
            if (section.index != position) errors += "Structure section ${position + 1} has index ${section.index}; expected $position"
            if (section.partId.isBlank()) errors += "Structure section ${position + 1} has a blank part ID"
        }
        val expectedPartIds = structure.map { it.partId }.toSet()
        if (analyses.keys != expectedPartIds) {
            errors += "MIDI analyses must exist exactly for every referenced structure part"
        }
        analyses.forEach { (partId, analysis) ->
            if (analysis.version != 1) errors += "MIDI analysis for '$partId' has unsupported version ${analysis.version}"
            if (analysis.partId != partId) errors += "MIDI analysis key '$partId' does not match analysis part ID '${analysis.partId}'"
            if (!analysis.energy.isFinite() || analysis.energy !in 0.0..1.0) {
                errors += "MIDI analysis energy for '$partId' must be between 0 and 1"
            }
        }
        val supported = LogicalInstrument.entries.map { it.wireName }.toSet()
        if (allowedInstruments.isEmpty()) errors += "At least piano must be allowed for song planning"
        if (allowedInstruments.any { it !in supported }) {
            errors += "Song planning uses unsupported instruments: ${allowedInstruments.filter { it !in supported }.distinct().sorted().joinToString(", ")}"
        }
        if (allowedInstruments.groupingBy { it.lowercase() }.eachCount().values.any { it > 1 }) {
            errors += "Allowed song-planning instruments must not contain duplicates"
        }
        if ("piano" !in allowedInstruments) errors += "Song planning requires piano in the allowed instruments"
        if (resolvedStyle.length > MAX_STYLE_LENGTH) errors += "Song-planning style must be at most $MAX_STYLE_LENGTH characters"
        if (!isSafeMusicalText(resolvedStyle)) errors += "Song-planning style must not contain paths, commands, or code-like text"
        soundContext?.let { context ->
            try { context.requireValid() } catch (error: IllegalArgumentException) { errors += error.message.orEmpty() }
            if (style != null) errors += "Structured song planning does not accept a style string"
            if (requestedIntents.isEmpty()) errors += "Structured song planning requires at least one instrument intent"
            requestedIntents.forEach { intent ->
                try { intent.requireValid() } catch (error: IllegalArgumentException) { errors += error.message.orEmpty() }
                if (intent.profile != context.profile || intent.mood != context.mood) errors += "Instrument intent profile and mood must match planning context"
            }
            if (requestedIntents.map(InstrumentIntent::role).distinct().size != requestedIntents.size) {
                errors += "Structured song-planning roles must not contain duplicates"
            }
            val expectedLogical = requestedIntents.map { LegacyLogicalInstrumentRoles.logicalFor(it.role) }.toSet()
            if (!expectedLogical.all { it in allowedInstruments }) errors += "Allowed instruments must include compatibility aliases for requested roles"
        }
        canonicalProjection?.let { projection ->
            if (projection.contextSha256.isBlank() || projection.inputSha256.length != 64) {
                errors += "Arrangement projection fingerprints are invalid"
            }
            val expected = projection.occurrences.mapIndexed { index, occurrence ->
                Triple(index, occurrence.partId, occurrence.occurrenceId)
            }
            if (structure.map { Triple(it.index, it.partId, it.instanceId) } != expected) {
                errors += "Song-planning structure must exactly match the canonical occurrence projection"
            }
            if (analyses.keys != projection.analyzedFacts.map { it.partId }.toSet()) {
                errors += "Song-planning analyses must exactly match the canonical arrangement projection"
            }
            if (projection.harmony.map { it.occurrenceId }.distinct() != projection.occurrences.map { it.occurrenceId }) {
                errors += "Arrangement projection harmony must cover every occurrence exactly once"
            }
        }
        try {
            constraints.requireValid()
        } catch (error: IllegalArgumentException) {
            errors += error.message.orEmpty()
        }
        require(errors.isEmpty()) { errors.joinToString("; ") }
    }

    private companion object {
        const val MAX_STYLE_LENGTH = 160
    }

    fun contextHash(): String? = canonicalProjection?.inputSha256 ?: soundContext?.let { context ->
        java.security.MessageDigest.getInstance("SHA-256").digest(
            Json { encodeDefaults = true }.encodeToString(ArrangementSoundContext.serializer(), context).toByteArray(Charsets.UTF_8)
        ).joinToString("") { "%02x".format(it) }
    }

    /**
     * Section purpose is a planner-derived value, so it is bound only after a
     * section has a purpose. A purpose-specific request is absent from other
     * sections; an unrestricted request is rebound to the current section.
     */
    fun intentsFor(purpose: SongSectionPurpose): List<InstrumentIntent> = requestedIntents
        .filter { it.sectionPurpose == null || it.sectionPurpose == purpose }
        .map { it.copy(sectionPurpose = purpose) }

    fun occurrenceHash(section: SongPlanningSectionInstance): String = section.occurrenceHash
}

@Serializable
data class SongPlanningSectionInstance(
    val index: Int,
    val instanceId: String,
    val partId: String,
    val occurrence: Int,
    val occurrenceHash: String,
    val variationOverrides: StructureVariationOverrides = StructureVariationOverrides()
)

/**
 * Adapts persisted occurrence identity to planning.  Planning never creates
 * occurrence IDs: they must already have been saved by Structure.
 */
object SongPlanningSectionInstances {
    fun create(structure: List<SectionInstance>): List<SongPlanningSectionInstance> {
        val occurrences = mutableMapOf<String, Int>()
        return structure.map { section ->
            val occurrence = (occurrences[section.partId] ?: 0) + 1
            occurrences[section.partId] = occurrence
            require(section.instanceId.isNotBlank()) { "Structure occurrence ${section.index + 1} is missing its persisted ID" }
            SongPlanningSectionInstance(section.index, section.instanceId, section.partId, occurrence,
                occurrenceFingerprint(section.index, section.instanceId, section.partId, occurrence), section.variationOverrides)
        }
    }
}

private fun occurrenceFingerprint(index: Int, instanceId: String, partId: String, occurrence: Int): String =
    java.security.MessageDigest.getInstance("SHA-256").digest("$index|$instanceId|$partId|$occurrence".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

data class SongPlanValidationResult(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/** Validates the persisted schema and binds every model-controlled field to the user-controlled input. */
object SongPlanValidator {
    fun validate(plan: SongPlan, input: SongPlanningInput): SongPlanValidationResult {
        val errors = mutableListOf<String>()
        try {
            input.requireValid()
        } catch (error: IllegalArgumentException) {
            errors += error.message.orEmpty()
            return SongPlanValidationResult(errors)
        }

        if (plan.version !in setOf(1, SongPlan.CURRENT_VERSION)) errors += "Unsupported song-plan version: ${plan.version}"
        if (input.soundContext != null || input.canonicalProjection != null) {
            if (plan.version != SongPlan.CURRENT_VERSION) errors += "Structured song plan must use version ${SongPlan.CURRENT_VERSION}"
            if (plan.contextHash != input.contextHash()) errors += "Song-plan context hash does not match the requested context"
        } else if (plan.version == 1 && plan.contextHash != null) errors += "Planner-protocol v1 must not contain a context hash"
        if (plan.style != input.resolvedStyle) errors += "Song-plan style must match the requested style"
        if (plan.energyCurve.size != input.structure.size) errors += "Song-plan energy curve count does not match requested structure"
        plan.energyCurve.forEachIndexed { index, energy ->
            if (!energy.isFinite() || energy !in 0.0..1.0) errors += "Song-plan energy at index $index must be between 0 and 1"
        }
        if (plan.sections.size != input.structure.size) errors += "Song-plan section count does not match requested structure"
        val expectedSections = input.sectionsWithIdentity()
        plan.sections.forEachIndexed { position, section ->
            val expected = expectedSections.getOrNull(position) ?: return@forEachIndexed
            if (section.index != expected.index) errors += "Song-plan section ${position + 1} has index ${section.index}; expected ${expected.index}"
            if (section.instanceId != expected.instanceId) errors += "Song-plan section ${position + 1} has unexpected instance ID '${section.instanceId}'"
            if (section.partId != expected.partId) errors += "Song-plan section ${position + 1} has unexpected part ID '${section.partId}'"
            if (section.occurrence != expected.occurrence) errors += "Song-plan section ${position + 1} has occurrence ${section.occurrence}; expected ${expected.occurrence}"
            if ((input.soundContext != null || input.canonicalProjection != null) && section.occurrenceHash != input.occurrenceHash(expected)) {
                errors += "Song-plan section ${position + 1} occurrence hash does not match the saved Structure"
            }
            validateInstruments(position, section.instrumentProgression, input, errors)
            validateSoundIntents(position, section, input, errors)
            if (position == plan.sections.lastIndex && section.transitionIntent != SongTransitionIntent.NONE) {
                errors += "Final song-plan section must use transition intent none"
            }
        }
        if (plan.climaxIndex !in input.structure.indices) {
            errors += "Song-plan climax index must be within the requested structure"
        } else if (plan.sections.getOrNull(plan.climaxIndex)?.purpose != SongSectionPurpose.CLIMAX) {
            errors += "Song-plan climax index must identify the climax section"
        }
        val climaxCount = plan.sections.count { it.purpose == SongSectionPurpose.CLIMAX }
        if (climaxCount != 1) errors += "Song plan must contain exactly one climax section"
        return SongPlanValidationResult(errors)
    }

    private fun validateSoundIntents(position: Int, section: SongPlanSection, input: SongPlanningInput, errors: MutableList<String>) {
        if (input.soundContext == null) return
        if (section.soundIntents.map(InstrumentIntent::role).distinct().size != section.soundIntents.size) {
            errors += "Song-plan section ${position + 1} contains duplicate roles"
        }
        section.soundIntents.forEach { intent ->
            try { intent.requireValid() } catch (error: IllegalArgumentException) { errors += error.message.orEmpty() }
            if (LegacyLogicalInstrumentRoles.logicalFor(intent.role) !in section.instrumentProgression) {
                errors += "Song-plan section ${position + 1} role '${intent.role.name.lowercase()}' has no compatibility alias"
            }
        }
        val expected = input.intentsFor(section.purpose)
            .filter { LegacyLogicalInstrumentRoles.logicalFor(it.role) in section.instrumentProgression }
            .toSet()
        if (section.soundIntents.toSet() != expected) {
            errors += "Song-plan section ${position + 1} sound intents do not match the requested roles for its instruments"
        }
    }

    private fun validateInstruments(
        position: Int,
        instruments: List<String>,
        input: SongPlanningInput,
        errors: MutableList<String>
    ) {
        if (instruments.isEmpty() || instruments.firstOrNull() != "piano") {
            errors += "Song-plan section ${position + 1} must begin with piano"
        }
        if (instruments.size > input.constraints.maxInstrumentsPerSection) {
            errors += "Song-plan section ${position + 1} exceeds the instrument limit"
        }
        if (instruments.groupingBy { it.lowercase() }.eachCount().values.any { it > 1 }) {
            errors += "Song-plan section ${position + 1} contains duplicate instruments"
        }
        instruments.filter { it !in input.allowedInstruments }.distinct().forEach { instrument ->
            errors += "Song-plan section ${position + 1} uses instrument '$instrument', which is not allowed"
        }
    }
}

/** Separate from ArrangementPlanner: it creates a reviewable global outline, never a render plan. */
interface GlobalSongPlanner {
    fun plan(input: SongPlanningInput): SongPlan
}

/** Stable local fallback and test oracle for whole-song planning. */
class DeterministicGlobalSongPlanner : GlobalSongPlanner {
    override fun plan(input: SongPlanningInput): SongPlan {
        input.requireValid()
        val sections = input.sectionsWithIdentity()
        val climaxIndex = climaxIndex(sections, input.analyses)
        val energyCurve = sections.map { section -> energy(section.index, climaxIndex, sections.lastIndex, input.analyses.getValue(section.partId).energy) }
        val nonPiano = input.allowedInstruments.filter { it != "piano" }
        var introduced = 0
        val planSections = sections.mapIndexed { position, section ->
            val target = if (position == 0) 0 else (energyCurve[position] * nonPiano.size).roundToInt()
            introduced = if (position <= climaxIndex) {
                minOf(target, introduced + input.constraints.maxNewInstrumentsPerSection, nonPiano.size)
            } else {
                minOf(introduced, target, nonPiano.size)
            }
            val instruments = listOf("piano") + nonPiano.take(
                minOf(introduced, input.constraints.maxInstrumentsPerSection - 1)
            )
            SongPlanSection(
                index = section.index,
                instanceId = section.instanceId,
                partId = section.partId,
                occurrence = section.occurrence,
                purpose = purpose(position, climaxIndex, sections.lastIndex),
                instrumentProgression = instruments,
                transitionIntent = transition(position, climaxIndex, sections.lastIndex),
                occurrenceHash = if (input.soundContext != null || input.canonicalProjection != null) input.occurrenceHash(section) else null,
                soundIntents = input.soundContext?.let { input.intentsFor(purpose(position, climaxIndex, sections.lastIndex))
                    .filter { LegacyLogicalInstrumentRoles.logicalFor(it.role) in instruments } }.orEmpty()
            )
        }
        return SongPlan(
            version = if (input.soundContext == null && input.canonicalProjection == null) 1 else SongPlan.CURRENT_VERSION,
            style = input.resolvedStyle,
            energyCurve = energyCurve,
            sections = planSections,
            climaxIndex = climaxIndex,
            ending = SongEnding.RESOLVED,
            contextHash = input.contextHash()
        ).also { it.requireValid(input) }
    }

    private fun climaxIndex(sections: List<SongPlanningSectionInstance>, analyses: Map<String, MidiAnalysis>): Int {
        if (sections.size == 1) return 0
        return (0 until sections.lastIndex).maxBy { index ->
            analyses.getValue(sections[index].partId).energy + index.toDouble() / sections.lastIndex * POSITION_WEIGHT
        }
    }

    private fun energy(index: Int, climax: Int, last: Int, partEnergy: Double): Double {
        val arc = when {
            last == 0 -> 0.5
            index <= climax -> index.toDouble() / maxOf(climax, 1)
            else -> 1.0 - (index - climax).toDouble() / (last - climax + 1)
        }
        return (BASE_ENERGY + partEnergy * PART_ENERGY_WEIGHT + arc * ARC_WEIGHT).coerceIn(0.0, 1.0)
    }

    private fun purpose(index: Int, climax: Int, last: Int): SongSectionPurpose = when {
        index == climax -> SongSectionPurpose.CLIMAX
        index == 0 -> SongSectionPurpose.INTRODUCTION
        index == last -> SongSectionPurpose.CONCLUSION
        index > climax -> SongSectionPurpose.RELEASE
        else -> SongSectionPurpose.DEVELOPMENT
    }

    private fun transition(index: Int, climax: Int, last: Int): SongTransitionIntent = when {
        index == last -> SongTransitionIntent.NONE
        index + 1 == climax -> SongTransitionIntent.BUILD
        index == climax -> SongTransitionIntent.RELEASE
        else -> SongTransitionIntent.NONE
    }

    private companion object {
        const val BASE_ENERGY = 0.20
        const val PART_ENERGY_WEIGHT = 0.35
        const val ARC_WEIGHT = 0.35
        const val POSITION_WEIGHT = 0.12
    }
}

private fun isSafeMusicalText(value: String): Boolean =
    value.none { it.isISOControl() } && !UNSAFE_MUSICAL_TEXT.containsMatchIn(value)

private val UNSAFE_MUSICAL_TEXT = Regex(
    """[\\/]|\.\.|[;|&`$<>{}]|(?i)\b(rm|curl|wget|sh|bash|python|java|cmd|powershell|exec|function|class)\b"""
)

/** Local Qwen adapter. Its response is parsed only as strict, validated SongPlan data. */
class LocalQwenGlobalSongPlanner(
    private val client: LocalQwenClient = LmStudioQwenClient()
) : GlobalSongPlanner {
    override fun plan(input: SongPlanningInput): SongPlan {
        input.requireValid()
        return requestQwenWithAutomaticRetries(client, SYSTEM_PROMPT, createUserPrompt(input)) { output ->
            val plan = try {
                strictJson.decodeFromString<SongPlan>(output)
            } catch (error: Exception) {
                throw IllegalArgumentException("Qwen returned invalid song-plan JSON: ${error.message}", error)
            }
            val normalizedPlan = bindApplicationOwnedFields(plan, input)
            val validation = normalizedPlan.validate(input)
            require(validation.isValid) { "Invalid Qwen song plan: ${validation.errors.joinToString("; ")}" }
            normalizedPlan
        }
    }

    /**
     * Qwen owns musical choices only. Structure identity, fingerprints, style,
     * and instrument intents are application-owned, so they are rebound after
     * parsing. This prevents imperfect copies of long hashes or
     * `sectionPurpose: null` from invalidating an otherwise valid plan.
     */
    private fun bindApplicationOwnedFields(plan: SongPlan, input: SongPlanningInput): SongPlan {
        val structured = input.soundContext != null || input.canonicalProjection != null
        val requestedSections = input.sectionsWithIdentity()
        return plan.copy(
            version = if (structured) SongPlan.CURRENT_VERSION else plan.version,
            style = input.resolvedStyle,
            contextHash = if (structured) input.contextHash() else plan.contextHash,
            sections = plan.sections.mapIndexed { position, modelSection ->
                val boundIdentity = requestedSections.getOrNull(position)?.let { expected ->
                    modelSection.copy(
                        index = expected.index,
                        instanceId = expected.instanceId,
                        partId = expected.partId,
                        occurrence = expected.occurrence,
                        occurrenceHash = if (structured) input.occurrenceHash(expected) else modelSection.occurrenceHash
                    )
                } ?: modelSection
                val soundIntents = input.soundContext?.let {
                    input.intentsFor(boundIdentity.purpose)
                        .filter { LegacyLogicalInstrumentRoles.logicalFor(it.role) in boundIdentity.instrumentProgression }
                }.orEmpty()
                boundIdentity.copy(soundIntents = soundIntents)
            }
        )
    }

    private fun createUserPrompt(input: SongPlanningInput): String = """
        Project metadata:
        ${promptJson.encodeToString(QwenProjectMetadata(input.projectName, input.projectVersion))}

        Versioned MIDI part analyses:
        ${promptJson.encodeToString(input.analyses.toSortedMap().map { QwenAnalysis(it.key, it.value) })}

        Canonical arrangement projection (authoritative key, tempo, meter, repeated occurrences, per-occurrence harmony,
        selected-MIDI hashes, melody evidence, profile, mood, and input hash):
        ${promptJson.encodeToString(input.canonicalProjection)}

        Requested sections:
        ${promptJson.encodeToString(input.sectionsWithIdentity())}

        Allowed instruments:
        ${promptJson.encodeToString(input.allowedInstruments)}

        Style:
        ${promptJson.encodeToString(input.resolvedStyle)}

        Structured planning context (present only for role-based requests):
        ${promptJson.encodeToString(input.soundContext)}

        Required context hash (present only for role-based requests):
        ${promptJson.encodeToString(input.contextHash())}

        Controlled requested sound intents (present only for role-based requests):
        ${promptJson.encodeToString(input.requestedIntents)}

        Compatibility aliases for controlled roles (use these to decide which intents belong in a section):
        melody and harmony = piano; bass = bass; drums = drums; counter-melody = strings; texture and ambience = pad.

        Bounded constraints:
        ${promptJson.encodeToString(input.constraints)}

        Response requirements:
        - version must be ${if (input.soundContext == null && input.canonicalProjection == null) 1 else SongPlan.CURRENT_VERSION}.
        - style must equal the supplied Style string exactly.
        - energyCurve and sections must each contain exactly ${input.structure.size} entries in the supplied order.
        - Include index, instanceId, partId, and occurrence for every section exactly as supplied. A changed, missing,
          duplicated, unknown, or reordered occurrence is rejected.
        - instrumentProgression must start with piano and contain only Allowed instruments.
        - When a canonical arrangement projection or structured planning context is present, set contextHash and every occurrenceHash
          to null. Set soundIntents to [] in every section. The application binds hashes, identities, and supplied role requests after
          you choose each section's purpose and instrumentProgression. Do not create instruments or IDs.
          Do not invent traits, stable IDs, paths, filenames, engine settings, or free-form sound text.
        - Choose climaxIndex. Section purposes are derived from it: first is introduction, its index is climax, final is
          conclusion, positions before it are development, and positions after it are release.
        - When the section count and per-section limits permit it, use every Allowed instrument in at least one section.
        - climaxIndex must point to the one section whose purpose is climax.
        Return the complete object described by the system response schema and no other text.
    """.trimIndent()

    @Serializable private data class QwenProjectMetadata(val name: String, val version: Int)
    @Serializable private data class QwenAnalysis(val partId: String, val analysis: MidiAnalysis)

    private companion object {
        val strictJson = Json { ignoreUnknownKeys = false }
        val promptJson = Json { encodeDefaults = true }
        const val SYSTEM_PROMPT = """
            You are a whole-song musical planner. You do not generate notes, MIDI events, audio, code, commands,
            file paths, sample data, renderer settings, or executable behavior. Return JSON only, without markdown
            or prose.

            The required response schema is exactly:
            {
              "version": "use the version required by the user input",
              "style": "the exact supplied style string",
              "energyCurve": [0.0],
              "sections": [{
                "index": 0,
                "instanceId": "A1",
                "partId": "A",
                "occurrence": 1,
                "purpose": "introduction",
                "instrumentProgression": ["piano"],
                "transitionIntent": "none",
                "occurrenceHash": null,
                "soundIntents": []
              }],
              "climaxIndex": 0,
              "ending": "resolved",
              "contextHash": null
            }
            All shown fields are required. The illustrative version field is replaced by the exact version required by the user input.
            Do not add fields. energyCurve and sections must have one entry per supplied
            requested section, not merely the single illustrative entry above. Each section has exactly index, instanceId,
            partId, occurrence, purpose, instrumentProgression, and transitionIntent.
            Include every supplied section index, instanceId, partId, occurrence, occurrenceHash, contextHash, and the
            soundIntents field. For structured planning contextHash and occurrenceHash must be null and soundIntents must be [].
            The application binds controlled identity, hashes, and sound requests. Choose one climaxIndex; section purposes must
            follow the required arc.
            Use only supplied logical instruments,
            start each progression with piano, and use every supplied logical instrument somewhere in the song when the supplied
            section count and limits permit it. Use one climax, and make the final transitionIntent none. Allowed purpose
            values: introduction, development, climax, release, conclusion. Allowed transitionIntent values: none, build,
            release. Allowed ending values: resolved, fade, open. Energy values must be finite numbers from 0 through 1.
            A sound intent uses only its supplied role, profile, mood, controlled trait IDs, performance capabilities, license
            policy, and optional stable pinned ID. Never include samples, SFZ files, paths, renderer arguments, commands, or code.
        """
    }
}

/** Atomic project-root persistence for the standalone song-plan artifact. */
object SongPlanStore {
    const val FILE_NAME = "song_plan.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun write(projectRoot: Path, input: SongPlanningInput, plan: SongPlan): Path {
        plan.requireValid(input)
        val target = projectRoot.toAbsolutePath().normalize().resolve(FILE_NAME)
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(plan), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    fun read(projectRoot: Path, input: SongPlanningInput): SongPlan {
        val target = projectRoot.toAbsolutePath().normalize().resolve(FILE_NAME)
        val plan = json.decodeFromString<SongPlan>(Files.readString(target, StandardCharsets.UTF_8))
        plan.requireValid(input)
        return plan
    }
}

package ai.music.workstation.arrangement

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
    val style: String,
    val energyCurve: List<Double>,
    val sections: List<SongPlanSection>,
    val climaxIndex: Int,
    val ending: SongEnding
) {
    fun validate(input: SongPlanningInput): SongPlanValidationResult = SongPlanValidator.validate(this, input)

    fun requireValid(input: SongPlanningInput) {
        val validation = validate(input)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 1
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
    val transitionIntent: SongTransitionIntent
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
    val style: String? = null,
    val constraints: SongPlanningConstraints = SongPlanningConstraints()
) {
    val resolvedStyle: String
        get() = style?.trim().takeUnless { it.isNullOrEmpty() } ?: "unspecified"

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
}

@Serializable
data class SongPlanningSectionInstance(
    val index: Int,
    val instanceId: String,
    val partId: String,
    val occurrence: Int
)

/** Derives stable occurrence identities; neither a model nor a user supplies these values. */
object SongPlanningSectionInstances {
    fun create(structure: List<SectionInstance>): List<SongPlanningSectionInstance> {
        val occurrences = mutableMapOf<String, Int>()
        return structure.map { section ->
            val occurrence = (occurrences[section.partId] ?: 0) + 1
            occurrences[section.partId] = occurrence
            SongPlanningSectionInstance(section.index, "${section.partId}$occurrence", section.partId, occurrence)
        }
    }
}

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

        if (plan.version != SongPlan.CURRENT_VERSION) errors += "Unsupported song-plan version: ${plan.version}"
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
            validateInstruments(position, section.instrumentProgression, input, errors)
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
                transitionIntent = transition(position, climaxIndex, sections.lastIndex)
            )
        }
        return SongPlan(
            version = SongPlan.CURRENT_VERSION,
            style = input.resolvedStyle,
            energyCurve = energyCurve,
            sections = planSections,
            climaxIndex = climaxIndex,
            ending = SongEnding.RESOLVED
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
        val output = client.complete(SYSTEM_PROMPT, createUserPrompt(input))
        val plan = try {
            strictJson.decodeFromString<SongPlan>(output)
        } catch (error: Exception) {
            throw IllegalArgumentException("Qwen returned invalid song-plan JSON: ${error.message}", error)
        }
        val validation = plan.validate(input)
        require(validation.isValid) { "Invalid Qwen song plan: ${validation.errors.joinToString("; ")}" }
        return plan
    }

    private fun createUserPrompt(input: SongPlanningInput): String = """
        Project metadata:
        ${promptJson.encodeToString(QwenProjectMetadata(input.projectName, input.projectVersion))}

        Versioned MIDI part analyses:
        ${promptJson.encodeToString(input.analyses.toSortedMap().map { QwenAnalysis(it.key, it.value) })}

        Requested sections:
        ${promptJson.encodeToString(input.sectionsWithIdentity())}

        Allowed instruments:
        ${promptJson.encodeToString(input.allowedInstruments)}

        Style:
        ${promptJson.encodeToString(input.resolvedStyle)}

        Bounded constraints:
        ${promptJson.encodeToString(input.constraints)}

        Response requirements:
        - version must be 1.
        - style must equal the supplied Style string exactly.
        - energyCurve and sections must each contain exactly ${input.structure.size} entries in the supplied order.
        - Copy index, instanceId, partId, and occurrence from each Requested sections entry exactly.
        - instrumentProgression must start with piano and contain only Allowed instruments.
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
              "version": 1,
              "style": "the exact supplied style string",
              "energyCurve": [0.0],
              "sections": [{
                "index": 0,
                "instanceId": "A1",
                "partId": "A",
                "occurrence": 1,
                "purpose": "introduction",
                "instrumentProgression": ["piano"],
                "transitionIntent": "none"
              }],
              "climaxIndex": 0,
              "ending": "resolved"
            }
            All shown fields are required. Do not add fields. energyCurve and sections must have one entry per supplied
            requested section, not merely the single illustrative entry above. Each section has exactly index, instanceId,
            partId, occurrence, purpose, instrumentProgression, and transitionIntent.
            Preserve every supplied section index, instanceId, partId, and occurrence exactly. Use only supplied logical instruments,
            start each progression with piano, and use every supplied logical instrument somewhere in the song when the supplied
            section count and limits permit it. Use one climax, and make the final transitionIntent none. Allowed purpose
            values: introduction, development, climax, release, conclusion. Allowed transitionIntent values: none, build,
            release. Allowed ending values: resolved, fade, open. Energy values must be finite numbers from 0 through 1.
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

package ai.music.workstation.arrangement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Version 1 of the local arrangement.json format. */
@Serializable
data class Arrangement(
    val version: Int = CURRENT_VERSION,
    val sections: List<ArrangementSection> = emptyList()
) {
    fun validate(
        validPartIds: Collection<String>,
        expectedStructure: List<SectionInstance>? = null
    ): ArrangementValidationResult = ArrangementValidator.validate(this, validPartIds, expectedStructure)

    fun requireValid(
        validPartIds: Collection<String>,
        expectedStructure: List<SectionInstance>? = null
    ) {
        val validation = validate(validPartIds, expectedStructure)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class ArrangementSection(
    val index: Int,
    val partId: String,
    val instruments: List<InstrumentPlan>,
    val transitionOut: TransitionPlan = TransitionPlan()
)

@Serializable
data class InstrumentPlan(
    val name: String,
    val mode: InstrumentMode,
    val role: String? = null,
    val density: Double? = null
)

@Serializable
enum class InstrumentMode {
    @SerialName("source") SOURCE,
    @SerialName("generated") GENERATED
}

@Serializable
data class TransitionPlan(
    val type: TransitionType = TransitionType.NONE,
    val bars: Int = 0
)

/** Only no-op transitions are supported until rendering adds transition effects. */
@Serializable
enum class TransitionType {
    @SerialName("none") NONE
}

data class ArrangementValidationResult(
    val errors: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/** Validates arrangement data before it is stored or handed to rendering. */
object ArrangementValidator {
    fun validate(
        arrangement: Arrangement,
        validPartIds: Collection<String>,
        expectedStructure: List<SectionInstance>? = null
    ): ArrangementValidationResult {
        val errors = mutableListOf<String>()
        val knownPartIds = validPartIds.toSet()

        if (arrangement.version != Arrangement.CURRENT_VERSION) {
            errors += "Unsupported arrangement version: ${arrangement.version}"
        }

        arrangement.sections.forEachIndexed { position, section ->
            val label = "Section ${position + 1}"
            if (section.index != position) {
                errors += "$label has index ${section.index}; expected $position"
            }
            if (section.partId.isBlank()) {
                errors += "$label part ID must not be blank"
            } else if (section.partId !in knownPartIds) {
                errors += "$label references unknown part ID '${section.partId}'"
            }

            val duplicateInstrumentNames = section.instruments
                .groupingBy { it.name.lowercase() }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            if (duplicateInstrumentNames.isNotEmpty()) {
                errors += "$label contains duplicate instrument names: ${duplicateInstrumentNames.sorted().joinToString(", ")}"
            }

            if (section.instruments.none { it.mode == InstrumentMode.SOURCE }) {
                errors += "$label must retain a source instrument"
            }
            section.instruments.forEach { instrument ->
                validateInstrument(label, instrument, errors)
            }
            validateTransition(label, section.transitionOut, errors)
        }

        expectedStructure?.let { structure ->
            if (arrangement.sections.size != structure.size) {
                errors += "Arrangement section count does not match requested structure"
            }
            arrangement.sections.zip(structure).forEach { (section, expected) ->
                if (section.index != expected.index || section.partId != expected.partId) {
                    errors += "Section ${section.index} does not match requested structure entry ${expected.index}"
                }
            }
        }

        return ArrangementValidationResult(errors)
    }

    private fun validateInstrument(
        sectionLabel: String,
        instrument: InstrumentPlan,
        errors: MutableList<String>
    ) {
        if (instrument.name.isBlank()) {
            errors += "$sectionLabel instrument name must not be blank"
        }
        instrument.density?.let { density ->
            if (!density.isFinite() || density !in 0.0..1.0) {
                errors += "$sectionLabel instrument '${instrument.name}' density must be between 0 and 1"
            }
        }
        if (instrument.mode == InstrumentMode.SOURCE && instrument.density != null) {
            errors += "$sectionLabel source instrument '${instrument.name}' must not set density"
        }
        if (instrument.mode == InstrumentMode.GENERATED && instrument.density == null) {
            errors += "$sectionLabel generated instrument '${instrument.name}' must set density"
        }
    }

    private fun validateTransition(
        sectionLabel: String,
        transition: TransitionPlan,
        errors: MutableList<String>
    ) {
        if (transition.bars != 0) {
            errors += "$sectionLabel transition '${transition.type.name.lowercase()}' must use 0 bars"
        }
    }
}

/** Inputs used by the deterministic planning boundary; audio is never read or changed here. */
data class ArrangementInput(
    val project: Project,
    val analyses: Map<String, PartAnalysis> = emptyMap(),
    val structure: List<SectionInstance>,
    val requestedInstruments: List<String> = emptyList(),
    val style: String? = null
) {
    fun requireValid() {
        val errors = mutableListOf<String>()
        val knownPartIds = project.parts.map { it.id }.toSet()
        if (project.version != Project.CURRENT_VERSION) {
            errors += "Unsupported project version: ${project.version}"
        }
        if (knownPartIds.isEmpty()) {
            errors += "Project must contain at least one part"
        }
        if (structure.isEmpty()) {
            errors += "Structure must not be empty"
        }
        structure.forEachIndexed { position, section ->
            if (section.index != position) {
                errors += "Structure section ${position + 1} has index ${section.index}; expected $position"
            }
            if (section.partId !in knownPartIds) {
                errors += "Structure section ${position + 1} references unknown part ID '${section.partId}'"
            }
        }
        analyses.keys.filterNot { it in knownPartIds }.forEach { unknownPartId ->
            errors += "Analysis references unknown part ID '$unknownPartId'"
        }
        if (requestedInstruments.any { it.isBlank() }) {
            errors += "Requested instrument names must not be blank"
        }
        val duplicateRequestedNames = requestedInstruments
            .groupingBy { it.lowercase() }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateRequestedNames.isNotEmpty()) {
            errors += "Duplicate requested instruments: ${duplicateRequestedNames.sorted().joinToString(", ")}"
        }
        require(errors.isEmpty()) { errors.joinToString("; ") }
    }
}

/** Keeps arrangement planning independent from rendering and model integrations. */
interface ArrangementPlanner {
    fun plan(input: ArrangementInput): Arrangement
}

/**
 * Produces a stable, render-free arrangement. The first requested instrument labels the
 * unmodified source; later requested instruments are fixed generated placeholders.
 */
class DeterministicArrangementPlanner : ArrangementPlanner {
    override fun plan(input: ArrangementInput): Arrangement {
        input.requireValid()
        val sourceName = input.requestedInstruments.firstOrNull() ?: SOURCE_INSTRUMENT_NAME
        val generated = input.requestedInstruments.drop(1).map { instrumentName ->
            InstrumentPlan(
                name = instrumentName,
                mode = InstrumentMode.GENERATED,
                role = if (instrumentName.equals("bass", ignoreCase = true)) "root_fifth" else "supporting",
                density = GENERATED_DENSITY
            )
        }
        val arrangement = Arrangement(
            sections = input.structure.map { section ->
                ArrangementSection(
                    index = section.index,
                    partId = section.partId,
                    instruments = listOf(
                        InstrumentPlan(name = sourceName, mode = InstrumentMode.SOURCE)
                    ) + generated
                )
            }
        )
        arrangement.requireValid(input.project.parts.map { it.id }, input.structure)
        return arrangement
    }

    private companion object {
        const val SOURCE_INSTRUMENT_NAME = "source"
        const val GENERATED_DENSITY = 0.3
    }
}

/** Persists validated arrangement data as project-root-relative arrangement.json. */
object ArrangementStore {
    private const val ARRANGEMENT_FILE = "arrangement.json"
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun write(projectRoot: Path, project: Project, arrangement: Arrangement): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireValid(root)
        arrangement.requireValid(project.parts.map { it.id })

        val arrangementPath = root.resolve(ARRANGEMENT_FILE)
        Files.writeString(
            arrangementPath,
            json.encodeToString(arrangement),
            StandardCharsets.UTF_8
        )
        return arrangementPath
    }
}

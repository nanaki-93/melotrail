package ai.music.workstation.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Stable, render-free variation decisions for every occurrence in a song plan.
 * This document deliberately contains no MIDI paths, notes, source copies, or renderer settings.
 */
@Serializable
data class SectionVariationPlan(
    val version: Int = CURRENT_VERSION,
    val sections: List<SectionVariation>
) {
    fun validate(input: SongPlanningInput, songPlan: SongPlan): SectionVariationValidationResult =
        SectionVariationValidator.validate(this, input, songPlan)

    fun requireValid(input: SongPlanningInput, songPlan: SongPlan) {
        val validation = validate(input, songPlan)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class SectionVariation(
    val index: Int,
    val instanceId: String,
    val partId: String,
    val occurrence: Int,
    /** Copied from the validated global plan; variation never invents section intent. */
    val purpose: SongSectionPurpose,
    /** Copied from the validated global plan and bounded to 0.0..1.0. */
    val energy: Double,
    val instruments: List<SectionVariationInstrument>,
    /** Reference to the global plan's transition intent, not synthesized transition data. */
    val transitionIntent: SongTransitionIntent
)

@Serializable
data class SectionVariationInstrument(
    val name: String,
    val role: String,
    val density: Double
)

data class SectionVariationValidationResult(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/**
 * Expands explicit global progression into bounded musical roles and densities.
 * The global song plan is authoritative for section identity, purpose, energy,
 * selected instruments, and transition intent. This stage only fills detail.
 */
object DeterministicSectionVariationPlanner {
    fun plan(input: SongPlanningInput, songPlan: SongPlan): SectionVariationPlan {
        songPlan.requireValid(input)
        val variations = songPlan.sections.mapIndexed { position, section ->
            val fallingEnergy = position > 0 && songPlan.energyCurve[position] < songPlan.energyCurve[position - 1]
            SectionVariation(
                index = section.index,
                instanceId = section.instanceId,
                partId = section.partId,
                occurrence = section.occurrence,
                purpose = section.purpose,
                energy = songPlan.energyCurve[position],
                instruments = section.instrumentProgression.map { instrument ->
                    instrumentDetail(instrument, section, songPlan.energyCurve[position], fallingEnergy)
                },
                transitionIntent = section.transitionIntent
            )
        }
        return SectionVariationPlan(sections = variations).also { it.requireValid(input, songPlan) }
    }

    private fun instrumentDetail(
        instrument: String,
        section: SongPlanSection,
        energy: Double,
        fallingEnergy: Boolean
    ): SectionVariationInstrument {
        if (instrument == "piano") return SectionVariationInstrument("piano", "source", 1.0)

        val densityOffset = when {
            section.purpose == SongSectionPurpose.CONCLUSION || fallingEnergy -> -0.12
            section.occurrence > 1 -> 0.08
            else -> 0.0
        }
        val density = (energy + densityOffset).coerceIn(MIN_GENERATED_DENSITY, 1.0)
        return SectionVariationInstrument(instrument, roleFor(instrument, section, fallingEnergy), density)
    }

    private fun roleFor(instrument: String, section: SongPlanSection, fallingEnergy: Boolean): String = when (instrument) {
        "bass" -> when {
            fallingEnergy || section.purpose == SongSectionPurpose.CONCLUSION -> "sustained"
            section.occurrence % 2 == 0 -> "root_fifth"
            else -> "root"
        }
        "drums" -> when {
            section.purpose == SongSectionPurpose.INTRODUCTION || fallingEnergy -> "minimal"
            section.purpose == SongSectionPurpose.CLIMAX -> "build"
            section.occurrence % 2 == 0 -> "soft_lofi"
            else -> "standard_groove"
        }
        "pad", "strings" -> if (fallingEnergy || section.purpose == SongSectionPurpose.CONCLUSION) "sustained" else "texture"
        else -> error("Song plan contains unsupported instrument '$instrument'")
    }

    private const val MIN_GENERATED_DENSITY = 0.10
}

/** Validates a persisted variation document against its exact global-plan source. */
object SectionVariationValidator {
    fun validate(
        variationPlan: SectionVariationPlan,
        input: SongPlanningInput,
        songPlan: SongPlan
    ): SectionVariationValidationResult {
        val errors = mutableListOf<String>()
        try {
            songPlan.requireValid(input)
        } catch (error: IllegalArgumentException) {
            return SectionVariationValidationResult(listOf(error.message.orEmpty()))
        }
        if (variationPlan.version != SectionVariationPlan.CURRENT_VERSION) {
            errors += "Unsupported section-variation version: ${variationPlan.version}"
        }
        if (variationPlan.sections.size != songPlan.sections.size) {
            errors += "Section-variation count does not match song plan"
        }
        variationPlan.sections.forEachIndexed { position, variation ->
            val expected = songPlan.sections.getOrNull(position) ?: return@forEachIndexed
            val label = "Section variation ${position + 1}"
            if (variation.index != expected.index) errors += "$label has index ${variation.index}; expected ${expected.index}"
            if (variation.instanceId != expected.instanceId) errors += "$label has unexpected instance ID '${variation.instanceId}'"
            if (variation.partId != expected.partId) errors += "$label has unexpected part ID '${variation.partId}'"
            if (variation.occurrence != expected.occurrence) errors += "$label has occurrence ${variation.occurrence}; expected ${expected.occurrence}"
            if (variation.purpose != expected.purpose) errors += "$label purpose must match song plan"
            if (variation.energy != songPlan.energyCurve.getOrNull(position)) errors += "$label energy must match song plan"
            if (variation.transitionIntent != expected.transitionIntent) errors += "$label transition intent must match song plan"
            if (variation.instruments.map { it.name } != expected.instrumentProgression) {
                errors += "$label instruments must match the explicit song-plan progression"
            }
            validateInstrumentDetails(label, variation.instruments, errors)
        }
        validateRepeatedVariation(variationPlan.sections, errors)
        return SectionVariationValidationResult(errors)
    }

    private fun validateInstrumentDetails(
        label: String,
        instruments: List<SectionVariationInstrument>,
        errors: MutableList<String>
    ) {
        if (instruments.firstOrNull()?.name != "piano") errors += "$label must retain piano first"
        if (instruments.groupingBy { it.name.lowercase() }.eachCount().values.any { it > 1 }) {
            errors += "$label contains duplicate instruments"
        }
        instruments.forEach { instrument ->
            if (instrument.name !in LogicalInstrument.entries.map { it.wireName }) {
                errors += "$label uses unsupported instrument '${instrument.name}'"
            }
            if (!instrument.density.isFinite() || instrument.density !in 0.0..1.0) {
                errors += "$label instrument '${instrument.name}' density must be between 0 and 1"
            }
            val allowedRoles = ROLES_BY_INSTRUMENT[instrument.name]
            if (instrument.role !in allowedRoles.orEmpty()) {
                errors += "$label instrument '${instrument.name}' uses unsupported role '${instrument.role}'"
            }
            if (instrument.name == "piano" && (instrument.role != "source" || instrument.density != 1.0)) {
                errors += "$label piano must use source role and density 1.0"
            }
        }
    }

    private fun validateRepeatedVariation(sections: List<SectionVariation>, errors: MutableList<String>) {
        sections.groupBy { it.partId }.filterValues { it.size > 1 }.forEach { (partId, occurrences) ->
            val variationAvailable = occurrences.any { it.instruments.size > 1 }
            val musicalSignatures = occurrences.map { variation ->
                variation.instruments.joinToString("|") { "${it.name}:${it.role}:${it.density}" }
            }.toSet()
            if (variationAvailable && musicalSignatures.size == 1) {
                errors += "Repeated part '$partId' has available supporting layers but identical variation details"
            }
        }
    }

    private val ROLES_BY_INSTRUMENT = mapOf(
        "piano" to setOf("source"),
        "bass" to setOf("root", "root_fifth", "octave", "sustained"),
        "drums" to setOf("minimal", "soft_lofi", "standard_groove", "half_time", "build"),
        "pad" to setOf("sustained", "texture"),
        "strings" to setOf("sustained", "texture")
    )
}

/** Atomic project-root persistence; source MIDI is never part of this artifact. */
object SectionVariationStore {
    const val FILE_NAME = "section_variations.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun write(projectRoot: Path, input: SongPlanningInput, songPlan: SongPlan, variations: SectionVariationPlan): Path {
        variations.requireValid(input, songPlan)
        val target = projectRoot.toAbsolutePath().normalize().resolve(FILE_NAME)
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(variations), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    fun read(projectRoot: Path, input: SongPlanningInput, songPlan: SongPlan): SectionVariationPlan {
        val target = projectRoot.toAbsolutePath().normalize().resolve(FILE_NAME)
        val variations = json.decodeFromString<SectionVariationPlan>(Files.readString(target, StandardCharsets.UTF_8))
        variations.requireValid(input, songPlan)
        return variations
    }
}

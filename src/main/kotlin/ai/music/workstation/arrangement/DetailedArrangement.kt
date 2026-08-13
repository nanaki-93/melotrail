package ai.music.workstation.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Version 3 is the MIDI-first arrangement decision document.  It deliberately
 * contains roles and bounded pattern controls, never note events or render paths.
 */
@Serializable
data class DetailedArrangement(
    val version: Int = CURRENT_VERSION,
    val sections: List<DetailedArrangementSection>
) {
    fun validate(input: DetailedArrangementInput): DetailedArrangementValidationResult =
        DetailedArrangementValidator.validate(this, input)

    fun requireValid(input: DetailedArrangementInput) {
        val validation = validate(input)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 3
    }
}

@Serializable
data class DetailedArrangementSection(
    val index: Int,
    val instanceId: String,
    val partId: String,
    val role: SongSectionPurpose,
    val energy: Double,
    val instruments: List<DetailedInstrumentPlan>,
    val transitionOut: TransitionPlan
)

/** The discriminator makes each generated instrument's controls structurally distinct. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@kotlinx.serialization.json.JsonClassDiscriminator("kind")
sealed class DetailedInstrumentPlan {
    abstract val name: String
    abstract val mode: InstrumentMode
}

@Serializable
@SerialName("piano")
data class PianoSourcePlan(
    override val name: String = "piano",
    override val mode: InstrumentMode = InstrumentMode.SOURCE
) : DetailedInstrumentPlan()

@Serializable
@SerialName("bass")
data class BassInstrumentPlan(
    override val name: String = "bass",
    override val mode: InstrumentMode = InstrumentMode.GENERATED,
    val role: DetailedBassRole,
    val density: Double,
    val movement: DetailedBassMovement,
    val register: MusicalRegister,
    val syncopation: Double
) : DetailedInstrumentPlan()

@Serializable
@SerialName("drums")
data class DrumsInstrumentPlan(
    override val name: String = "drums",
    override val mode: InstrumentMode = InstrumentMode.GENERATED,
    val role: DrumsRole,
    val density: Double,
    val kickDensity: Double,
    val snarePattern: SnarePattern,
    val hiHatDensity: Double,
    val swing: Double,
    val fillLastBar: Boolean
) : DetailedInstrumentPlan()

@Serializable
@SerialName("pad")
data class PadInstrumentPlan(
    override val name: String = "pad",
    override val mode: InstrumentMode = InstrumentMode.GENERATED,
    val role: SustainedRole,
    val density: Double,
    val register: MusicalRegister
) : DetailedInstrumentPlan()

@Serializable
@SerialName("strings")
data class StringsInstrumentPlan(
    override val name: String = "strings",
    override val mode: InstrumentMode = InstrumentMode.GENERATED,
    val role: SustainedRole,
    val density: Double,
    val register: MusicalRegister
) : DetailedInstrumentPlan()

@Serializable
enum class DetailedBassRole {
    @SerialName("root") ROOT,
    @SerialName("root_fifth") ROOT_FIFTH,
    @SerialName("octave") OCTAVE,
    @SerialName("sustained") SUSTAINED
}

@Serializable
enum class DetailedBassMovement {
    @SerialName("static") STATIC,
    @SerialName("root_motion") ROOT_MOTION,
    @SerialName("leaping") LEAPING,
    @SerialName("octaves") OCTAVES
}

@Serializable
enum class DrumsRole {
    @SerialName("sparse") SPARSE,
    @SerialName("groove") GROOVE
}

@Serializable
enum class SnarePattern {
    @SerialName("sparse") SPARSE,
    @SerialName("half_time") HALF_TIME,
    @SerialName("backbeat") BACKBEAT
}

@Serializable
enum class SustainedRole {
    @SerialName("sustained") SUSTAINED,
    @SerialName("texture") TEXTURE
}

@Serializable
enum class MusicalRegister {
    @SerialName("low") LOW,
    @SerialName("mid") MID,
    @SerialName("high") HIGH
}

data class DetailedArrangementInput(
    val planningInput: SongPlanningInput,
    val songPlan: SongPlan,
    val variations: SectionVariationPlan
) {
    fun requireValid() {
        songPlan.requireValid(planningInput)
        variations.requireValid(planningInput, songPlan)
    }
}

data class DetailedArrangementValidationResult(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object DetailedArrangementValidator {
    fun validate(arrangement: DetailedArrangement, input: DetailedArrangementInput): DetailedArrangementValidationResult {
        val errors = mutableListOf<String>()
        try {
            input.requireValid()
        } catch (error: IllegalArgumentException) {
            return DetailedArrangementValidationResult(listOf(error.message.orEmpty()))
        }
        if (arrangement.version != DetailedArrangement.CURRENT_VERSION) {
            errors += "Unsupported detailed arrangement version: ${arrangement.version}"
        }
        if (arrangement.sections.size != input.variations.sections.size) {
            errors += "Detailed arrangement section count does not match song plan"
        }
        arrangement.sections.forEachIndexed { position, section ->
            val expected = input.variations.sections.getOrNull(position) ?: return@forEachIndexed
            val label = "Detailed arrangement section ${position + 1}"
            if (section.index != expected.index) errors += "$label has index ${section.index}; expected ${expected.index}"
            if (section.instanceId != expected.instanceId) errors += "$label has unexpected instance ID '${section.instanceId}'"
            if (section.partId != expected.partId) errors += "$label has unexpected part ID '${section.partId}'"
            if (section.role != expected.purpose) errors += "$label role must match song-plan purpose"
            if (!section.energy.isFinite() || section.energy !in 0.0..1.0) {
                errors += "$label energy must be a finite number from 0 through 1"
            } else if (section.energy != expected.energy) {
                errors += "$label energy must match section variation"
            }
            val expectedNames = expected.instruments.map { it.name }
            if (section.instruments.map { it.name } != expectedNames) {
                errors += "$label instruments must match the section variation exactly"
            }
            validateInstruments(label, section.instruments, expected.instruments, errors)
            validateTransition(label, section.transitionOut, expected.transitionIntent, position == input.variations.sections.lastIndex, errors)
        }
        return DetailedArrangementValidationResult(errors)
    }

    private fun validateInstruments(
        label: String,
        instruments: List<DetailedInstrumentPlan>,
        expected: List<SectionVariationInstrument>,
        errors: MutableList<String>
    ) {
        if (instruments.groupingBy { it.name.lowercase() }.eachCount().values.any { it > 1 }) {
            errors += "$label contains duplicate instruments"
        }
        val piano = instruments.filterIsInstance<PianoSourcePlan>()
        if (piano.size != 1 || instruments.count { it.mode == InstrumentMode.SOURCE } != 1 || piano.singleOrNull()?.name != "piano") {
            errors += "$label must contain exactly one piano source plan"
        }
        instruments.forEachIndexed { index, instrument ->
            val variation = expected.getOrNull(index)
            if (variation == null || variation.name != instrument.name) return@forEachIndexed
            when (instrument) {
                is PianoSourcePlan -> if (instrument.mode != InstrumentMode.SOURCE || variation.role != "source") {
                    errors += "$label piano must use source mode and source role"
                }
                is BassInstrumentPlan -> {
                    if (instrument.name != "bass" || instrument.mode != InstrumentMode.GENERATED || instrument.role.wireName != variation.role) {
                        errors += "$label bass role or mode is invalid"
                    }
                    bounded(label, "bass density", instrument.density, errors)
                    bounded(label, "bass syncopation", instrument.syncopation, errors)
                }
                is DrumsInstrumentPlan -> {
                    if (instrument.name != "drums" || instrument.mode != InstrumentMode.GENERATED || instrument.role.wireName != variation.role) {
                        errors += "$label drums role or mode is invalid"
                    }
                    bounded(label, "drums density", instrument.density, errors)
                    bounded(label, "kick density", instrument.kickDensity, errors)
                    bounded(label, "hi-hat density", instrument.hiHatDensity, errors)
                    if (!instrument.swing.isFinite() || instrument.swing !in 0.0..0.5) errors += "$label swing must be a finite number from 0 through 0.5"
                }
                is PadInstrumentPlan -> {
                    if (instrument.name != "pad" || instrument.mode != InstrumentMode.GENERATED || instrument.role.wireName != variation.role) {
                        errors += "$label pad role or mode is invalid"
                    }
                    bounded(label, "pad density", instrument.density, errors)
                }
                is StringsInstrumentPlan -> {
                    if (instrument.name != "strings" || instrument.mode != InstrumentMode.GENERATED || instrument.role.wireName != variation.role) {
                        errors += "$label strings role or mode is invalid"
                    }
                    bounded(label, "strings density", instrument.density, errors)
                }
            }
        }
    }

    private fun validateTransition(
        label: String,
        transition: TransitionPlan,
        intent: SongTransitionIntent,
        isFinal: Boolean,
        errors: MutableList<String>
    ) {
        ArrangementValidator.validate(Arrangement(version = 2, sections = listOf(
            ArrangementSection(0, "validation", listOf(InstrumentPlan("source", InstrumentMode.SOURCE)), transition)
        )), setOf("validation")).errors.forEach { error -> errors += "$label: $error" }
        val expectedType = when (intent) {
            SongTransitionIntent.NONE -> TransitionType.NONE
            SongTransitionIntent.BUILD -> TransitionType.BRIDGE
            SongTransitionIntent.RELEASE -> TransitionType.CROSSFADE
        }
        if (transition.type != expectedType) errors += "$label transition must match '$intent' intent"
        if (isFinal && transition.type != TransitionType.NONE) errors += "$label final transition must be none"
    }

    private fun bounded(label: String, name: String, value: Double, errors: MutableList<String>) {
        if (!value.isFinite() || value !in 0.0..1.0) errors += "$label $name must be a finite number from 0 through 1"
    }

    private val DetailedBassRole.wireName: String get() = name.lowercase()
    private val DrumsRole.wireName: String get() = name.lowercase()
    private val SustainedRole.wireName: String get() = name.lowercase()
}

interface DetailedArrangementPlanner {
    fun plan(input: DetailedArrangementInput): DetailedArrangement
}

/** Stable local defaults. Generators, not this document, turn these choices into MIDI events. */
class DeterministicDetailedArrangementPlanner : DetailedArrangementPlanner {
    override fun plan(input: DetailedArrangementInput): DetailedArrangement {
        input.requireValid()
        return DetailedArrangement(sections = input.variations.sections.mapIndexed { index, section ->
            DetailedArrangementSection(
                index = section.index,
                instanceId = section.instanceId,
                partId = section.partId,
                role = section.purpose,
                energy = section.energy,
                instruments = section.instruments.map { instrument -> detail(instrument, section.energy) },
                transitionOut = transition(section.transitionIntent, section.energy, index == input.variations.sections.lastIndex)
            )
        }).also { it.requireValid(input) }
    }

    private fun detail(instrument: SectionVariationInstrument, energy: Double): DetailedInstrumentPlan = when (instrument.name) {
        "piano" -> PianoSourcePlan()
        "bass" -> BassInstrumentPlan(
            role = DetailedBassRole.entries.first { it.wireName == instrument.role }, density = instrument.density,
            movement = when (instrument.role) { "root" -> DetailedBassMovement.ROOT_MOTION; "root_fifth" -> DetailedBassMovement.LEAPING; "octave" -> DetailedBassMovement.OCTAVES; else -> DetailedBassMovement.STATIC },
            register = register(energy), syncopation = (instrument.density * 0.4).coerceIn(0.0, 1.0)
        )
        "drums" -> DrumsInstrumentPlan(
            role = DrumsRole.entries.first { it.wireName == instrument.role }, density = instrument.density,
            kickDensity = instrument.density, snarePattern = if (instrument.role == "sparse") SnarePattern.SPARSE else SnarePattern.BACKBEAT,
            hiHatDensity = (instrument.density * 0.8).coerceIn(0.0, 1.0), swing = 0.0, fillLastBar = energy >= 0.7
        )
        "pad" -> PadInstrumentPlan(role = SustainedRole.entries.first { it.wireName == instrument.role }, density = instrument.density, register = register(energy))
        "strings" -> StringsInstrumentPlan(role = SustainedRole.entries.first { it.wireName == instrument.role }, density = instrument.density, register = register(energy))
        else -> error("Unsupported variation instrument '${instrument.name}'")
    }

    private fun transition(intent: SongTransitionIntent, energy: Double, isFinal: Boolean): TransitionPlan = when {
        isFinal || intent == SongTransitionIntent.NONE -> TransitionPlan()
        intent == SongTransitionIntent.BUILD -> TransitionPlan(TransitionType.BRIDGE, bars = 1, bridge = BridgePlan(energy, listOf(BridgeElement.MELODY_PICKUP)))
        else -> TransitionPlan(TransitionType.CROSSFADE, crossfadeMs = 180)
    }

    private fun register(energy: Double): MusicalRegister = when {
        energy < 0.34 -> MusicalRegister.LOW
        energy > 0.72 -> MusicalRegister.HIGH
        else -> MusicalRegister.MID
    }

    private val DetailedBassRole.wireName: String get() = name.lowercase()
    private val DrumsRole.wireName: String get() = name.lowercase()
    private val SustainedRole.wireName: String get() = name.lowercase()
}

/** Strict local-model boundary: JSON is data only, then checked against the persisted planning artifacts. */
class LocalQwenDetailedArrangementPlanner(private val client: LocalQwenClient = LmStudioQwenClient()) : DetailedArrangementPlanner {
    override fun plan(input: DetailedArrangementInput): DetailedArrangement {
        input.requireValid()
        val output = client.complete(SYSTEM_PROMPT, createUserPrompt(input))
        val arrangement = try {
            strictJson.decodeFromString<DetailedArrangement>(output)
        } catch (error: Exception) {
            throw IllegalArgumentException("Qwen returned invalid detailed-arrangement JSON: ${error.message}", error)
        }
        val validation = arrangement.validate(input)
        require(validation.isValid) { "Invalid Qwen detailed arrangement: ${validation.errors.joinToString("; ")}" }
        return arrangement
    }

    private fun createUserPrompt(input: DetailedArrangementInput): String = """
        Validated global song plan:
        ${promptJson.encodeToString(input.songPlan)}

        Validated repeated-section variations:
        ${promptJson.encodeToString(input.variations)}

        Return a version 3 arrangement that preserves every supplied section identity, role, energy, instrument name, and variation role exactly.
    """.trimIndent()

    private companion object {
        val strictJson = Json { ignoreUnknownKeys = false }
        val promptJson = Json { encodeDefaults = true }
        const val SYSTEM_PROMPT = """
            You are a MIDI-first arrangement planner. Return JSON only, without markdown or prose. You never provide notes,
            MIDI events, frequencies, sample data, file paths, code, commands, renderer configuration, sample rates, or output paths.
            The document is schema version 3 with top-level version and sections only. A section has index, instanceId, partId,
            role, energy, instruments, transitionOut. Instrument objects use kind=piano|bass|drums|pad|strings. Piano is exactly
            name=piano and mode=source. Generated plans use mode=generated and only their own typed fields: bass(role,density,
            movement,register,syncopation); drums(role,density,kickDensity,snarePattern,hiHatDensity,swing,fillLastBar);
            pad/strings(role,density,register). Densities and syncopation are finite 0..1; swing is finite 0..0.5.
            Use transitionOut none for intent none, bridge for build, and crossfade for release. Do not add fields.
        """
    }
}

/** V3 persistence is isolated from legacy v1/v2 readers until MIDI generators consume it. */
object DetailedArrangementStore {
    const val APPROVED_FILE = "arrangement.json"
    const val DRAFT_FILE = "arrangement.draft.json"
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }

    fun writeApproved(projectRoot: Path, input: DetailedArrangementInput, arrangement: DetailedArrangement): Path =
        write(projectRoot, APPROVED_FILE, input, arrangement)

    fun writeDraft(projectRoot: Path, input: DetailedArrangementInput, arrangement: DetailedArrangement): Path =
        write(projectRoot, DRAFT_FILE, input, arrangement)

    fun readDraft(projectRoot: Path, input: DetailedArrangementInput): DetailedArrangement =
        read(projectRoot, DRAFT_FILE, input)

    fun approve(projectRoot: Path, input: DetailedArrangementInput): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        val draft = readDraft(root, input) // Validate before touching the approved file.
        val temporary = root.resolve(".arrangement.approving.json.tmp")
        Files.writeString(temporary, json.encodeToString(draft), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        moveAtomically(temporary, root.resolve(APPROVED_FILE))
        return root.resolve(APPROVED_FILE)
    }

    private fun write(projectRoot: Path, name: String, input: DetailedArrangementInput, arrangement: DetailedArrangement): Path {
        arrangement.requireValid(input)
        val root = projectRoot.toAbsolutePath().normalize()
        Files.createDirectories(root)
        val target = root.resolve(name)
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(arrangement), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        moveAtomically(temporary, target)
        return target
    }

    private fun read(projectRoot: Path, name: String, input: DetailedArrangementInput): DetailedArrangement {
        val target = projectRoot.toAbsolutePath().normalize().resolve(name)
        val arrangement = json.decodeFromString<DetailedArrangement>(Files.readString(target, StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        return arrangement
    }

    private fun moveAtomically(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

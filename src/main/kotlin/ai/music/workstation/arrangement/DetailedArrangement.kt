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
    val role: StringsRole,
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
    @SerialName("minimal") MINIMAL,
    @SerialName("soft_lofi") SOFT_LOFI,
    @SerialName("standard_groove") STANDARD_GROOVE,
    @SerialName("half_time") HALF_TIME,
    @SerialName("build") BUILD
}

@Serializable
enum class SnarePattern {
    @SerialName("beats_2_4") BEATS_2_4,
    @SerialName("beat_3") BEAT_3,
    @SerialName("none") NONE
}

@Serializable
enum class SustainedRole {
    @SerialName("sustained") SUSTAINED,
    @SerialName("texture") TEXTURE
}

/** Bounded composition choices; legacy roles keep existing approved v3 plans readable. */
@Serializable
enum class StringsRole {
    @SerialName("sustained_harmony") SUSTAINED_HARMONY,
    @SerialName("climax_reinforcement") CLIMAX_REINFORCEMENT,
    @SerialName("long_notes") LONG_NOTES,
    @SerialName("simple_countermelody") SIMPLE_COUNTERMELODY,
    @SerialName("sustained") LEGACY_SUSTAINED,
    @SerialName("texture") LEGACY_TEXTURE
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
            validateInstruments(label, section.role, section.instruments, expected.instruments, errors)
            validateTransition(label, section.transitionOut, expected.transitionIntent, position == input.variations.sections.lastIndex, errors)
        }
        return DetailedArrangementValidationResult(errors)
    }

    private fun validateInstruments(
        label: String,
        sectionRole: SongSectionPurpose,
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
                    if (!instrument.syncopation.isFinite() || instrument.syncopation !in 0.0..0.25) {
                        errors += "$label bass syncopation must be a finite number from 0 through 0.25"
                    }
                    if (instrument.register != MusicalRegister.LOW) errors += "$label bass register must be low"
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
                    if (instrument.name != "strings" || instrument.mode != InstrumentMode.GENERATED || !instrument.role.matches(variation.role, sectionRole)) {
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

    private fun StringsRole.matches(variationRole: String, sectionRole: SongSectionPurpose): Boolean = when (variationRole) {
        "sustained" -> this in setOf(StringsRole.SUSTAINED_HARMONY, StringsRole.LONG_NOTES, StringsRole.LEGACY_SUSTAINED)
        "texture" -> this != StringsRole.CLIMAX_REINFORCEMENT || sectionRole == SongSectionPurpose.CLIMAX
        else -> false
    }
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
                instruments = section.instruments.map { instrument -> detail(instrument, section.energy, section.purpose) },
                transitionOut = transition(
                    section.transitionIntent,
                    section.energy,
                    section.instruments.map { it.name }.toSet(),
                    input.variations.sections.getOrNull(index + 1)?.instruments?.map { it.name }?.toSet().orEmpty(),
                    index == input.variations.sections.lastIndex
                )
            )
        }).also { it.requireValid(input) }
    }

    private fun detail(instrument: SectionVariationInstrument, energy: Double, purpose: SongSectionPurpose): DetailedInstrumentPlan = when (instrument.name) {
        "piano" -> PianoSourcePlan()
        "bass" -> BassInstrumentPlan(
            role = DetailedBassRole.entries.first { it.wireName == instrument.role }, density = instrument.density,
            movement = when (instrument.role) { "root" -> DetailedBassMovement.ROOT_MOTION; "root_fifth" -> DetailedBassMovement.LEAPING; "octave" -> DetailedBassMovement.OCTAVES; else -> DetailedBassMovement.STATIC },
            register = MusicalRegister.LOW, syncopation = (instrument.density * 0.2).coerceIn(0.0, 0.25)
        )
        "drums" -> DrumsInstrumentPlan(
            role = DrumsRole.entries.first { it.wireName == instrument.role }, density = instrument.density,
            kickDensity = instrument.density,
            snarePattern = when (instrument.role) {
                "minimal" -> SnarePattern.NONE
                "half_time" -> SnarePattern.BEAT_3
                else -> SnarePattern.BEATS_2_4
            },
            hiHatDensity = (instrument.density * 0.8).coerceIn(0.0, 1.0), swing = 0.0, fillLastBar = energy >= 0.7
        )
        "pad" -> PadInstrumentPlan(role = SustainedRole.entries.first { it.wireName == instrument.role }, density = instrument.density, register = register(energy))
        "strings" -> StringsInstrumentPlan(role = stringsRole(instrument.role, energy, purpose), density = instrument.density, register = register(energy))
        else -> error("Unsupported variation instrument '${instrument.name}'")
    }

    private fun transition(
        intent: SongTransitionIntent,
        energy: Double,
        outgoing: Set<String>,
        incoming: Set<String>,
        isFinal: Boolean
    ): TransitionPlan = when {
        isFinal || intent == SongTransitionIntent.NONE -> TransitionPlan()
        intent == SongTransitionIntent.BUILD -> TransitionPlan(
            TransitionType.BRIDGE,
            bars = 1,
            bridge = BridgePlan(energy, bridgeElements(outgoing + incoming))
        )
        else -> TransitionPlan(TransitionType.CROSSFADE, crossfadeMs = 180)
    }

    private fun bridgeElements(instruments: Set<String>): List<BridgeElement> = when {
        "drums" in instruments && "bass" in instruments -> listOf(BridgeElement.DRUM_FILL, BridgeElement.BASS_PICKUP)
        "drums" in instruments -> listOf(BridgeElement.DRUM_FILL)
        "pad" in instruments -> listOf(BridgeElement.PAD_SWELL)
        "bass" in instruments -> listOf(BridgeElement.BASS_PICKUP)
        else -> listOf(BridgeElement.MELODY_PICKUP)
    }

    private fun register(energy: Double): MusicalRegister = when {
        energy < 0.34 -> MusicalRegister.LOW
        energy > 0.72 -> MusicalRegister.HIGH
        else -> MusicalRegister.MID
    }

    private fun stringsRole(variationRole: String, energy: Double, purpose: SongSectionPurpose): StringsRole = when (variationRole) {
        "sustained" -> StringsRole.LONG_NOTES
        "texture" -> if (purpose == SongSectionPurpose.CLIMAX && energy >= 0.8) StringsRole.CLIMAX_REINFORCEMENT else StringsRole.SUSTAINED_HARMONY
        else -> error("Unsupported strings variation role '$variationRole'")
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

        MIDI analysis facts by part (facts only; do not copy them into the response):
        ${promptJson.encodeToString(input.planningInput.analyses.toSortedMap().map { (partId, analysis) ->
            DetailedPlanningAnalysis(partId, analysis.pitchRange, analysis.melodicRange, analysis.noteDensity, analysis.rhythmicDensity)
        })}

        Response requirements:
        - Return exactly ${input.variations.sections.size} sections in the supplied order.
        - Copy every section index, instanceId, partId, role, and energy exactly.
        - Keep the exact instrument order, names, modes, and variation roles supplied for each section.
        - Fill only the instrument-specific fields required by the system response schema.
        - Map transitionIntent none to transitionOut type none, build to bridge, and release to crossfade.
        Return the complete version 3 object described by the system response schema and no other text.
    """.trimIndent()

    @Serializable
    private data class DetailedPlanningAnalysis(
        val partId: String,
        val pitchRange: MidiIntRange?,
        val melodicRange: Int?,
        val noteDensity: Double,
        val rhythmicDensity: Double
    )

    private companion object {
        val strictJson = Json { ignoreUnknownKeys = false }
        val promptJson = Json { encodeDefaults = true }
        const val SYSTEM_PROMPT = """
            You are a MIDI-first arrangement planner. Return JSON only, without markdown or prose. You never provide notes,
            MIDI events, frequencies, sample data, file paths, code, commands, renderer configuration, sample rates, or output paths.
            The document has exactly these top-level fields:
            {"version":3,"sections":[SECTION_OBJECTS]}
            Every section object has exactly index, instanceId, partId, role, energy, instruments, and transitionOut.

            Instrument objects are a tagged union. Use exactly one of these shapes and no extra fields:
            piano:   {"kind":"piano","name":"piano","mode":"source"}
            bass:    {"kind":"bass","name":"bass","mode":"generated","role":"root","density":0.4,"movement":"root_motion","register":"low","syncopation":0.1}
            drums:   {"kind":"drums","name":"drums","mode":"generated","role":"soft_lofi","density":0.4,"kickDensity":0.4,"snarePattern":"beats_2_4","hiHatDensity":0.3,"swing":0.1,"fillLastBar":false}
            pad:     {"kind":"pad","name":"pad","mode":"generated","role":"texture","density":0.4,"register":"mid"}
            strings: {"kind":"strings","name":"strings","mode":"generated","role":"sustained_harmony","density":0.4,"register":"mid"}

            Allowed bass roles: root, root_fifth, octave, sustained. Allowed bass movement: static, root_motion, leaping, octaves.
            Allowed drum roles: minimal, soft_lofi, standard_groove, half_time, build. Allowed snarePattern: beats_2_4, beat_3, none.
            Allowed pad roles: sustained, texture. Allowed strings roles: sustained_harmony, climax_reinforcement, long_notes,
            simple_countermelody. Allowed register: low, mid, high. Densities are finite 0..1, bass syncopation is finite
            0..0.25, and drum swing is finite 0..0.5. Bass must use register low. Strings are voiced above the source
            piano range where practical: choose high when a strings section's MIDI analysis has a high pitchRange.max;
            dense source material can still force conservative silence if no complete voicing fits above it.

            transitionOut is also a union. Use exactly one of these shapes:
            none:      {"type":"none","bars":0,"crossfadeMs":0}
            crossfade: {"type":"crossfade","bars":0,"crossfadeMs":180}
            bridge:    {"type":"bridge","bars":1,"crossfadeMs":0,"bridge":{"energy":0.5,"elements":["bass_pickup"]}}
            Bridge bars are 1 or 2. Bridge energy is finite 0..1. Bridge elements may contain only bass_pickup, drum_fill,
            pad_swell, melody_pickup. Choose elements that have a matching generated instrument in the outgoing or incoming
            section: use drum_fill for a drum entry, pad_swell for a pad entry/release, and bass_pickup only where bass is active.
            Do not use bass_pickup for every bridge; use a drum_fill plus bass_pickup together for a strong build when both are
            active. Map transition intent none to none, build to bridge, release to crossfade. The final section must use none.
            Do not add fields.
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

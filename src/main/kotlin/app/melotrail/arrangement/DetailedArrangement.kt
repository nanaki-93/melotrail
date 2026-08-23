package app.melotrail.arrangement

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
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
 * Version 4 is the MIDI-first arrangement decision document.  It deliberately
 * contains roles and bounded pattern controls, never note events or render paths.
 */
@Serializable
data class DetailedArrangement(
    val version: Int = CURRENT_VERSION,
    val sections: List<DetailedArrangementSection>,
    /** Historical target-order evidence only; new arrangement planners never populate it. */
    val cohesion: ArrangementCohesionReferences? = null
) {
    fun validate(input: DetailedArrangementInput): DetailedArrangementValidationResult =
        DetailedArrangementValidator.validate(this, input)

    fun requireValid(input: DetailedArrangementInput) {
        val validation = validate(input)
        require(validation.isValid) { validation.errors.joinToString("; ") }
    }

    companion object {
        const val CURRENT_VERSION = 4
    }
}

/**
 * Immutable evidence that a newly generated arrangement consumes reviewed
 * Cohesion boundary decisions.  The decision stays in Cohesion; Arrangement
 * stores only its stable identity plus approved decision and bridge-MIDI digests.
 */
@Serializable
data class ArrangementCohesionReferences(
    val inputSha256: String,
    val boundaries: List<ArrangementCohesionBoundaryReference>
) {
    init {
        require(SHA_256.matches(inputSha256)) { "Arrangement cohesion input fingerprint is invalid" }
        require(boundaries.map { it.outgoingInstanceId to it.incomingInstanceId }.distinct().size == boundaries.size) {
            "Arrangement cohesion boundary identities must be unique"
        }
    }
}

@Serializable
data class ArrangementCohesionBoundaryReference(
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val approvedSha256: String,
    val bridgeSha256: String
) {
    init {
        require(SAFE_ID.matches(outgoingInstanceId) && SAFE_ID.matches(incomingInstanceId) && outgoingInstanceId != incomingInstanceId) {
            "Arrangement cohesion boundary identity is invalid"
        }
        require(SHA_256.matches(approvedSha256) && SHA_256.matches(bridgeSha256)) { "Arrangement cohesion boundary fingerprint is invalid" }
    }
}

/** Trusted in-memory pairing of approved Cohesion data with its persisted references. */
data class ApprovedArrangementCohesion(
    val references: ArrangementCohesionReferences,
    val plan: TransitionCohesionPlan
) {
    fun requireValid(input: SongPlanningInput) {
        val expected = input.sectionsWithIdentity().zipWithNext().map { (outgoing, incoming) -> outgoing.instanceId to incoming.instanceId }
        require(references.inputSha256 == plan.inputHash) { "Arrangement cohesion input fingerprint is stale" }
        require(references.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } == expected) {
            "Arrangement cohesion boundaries do not match the saved Structure"
        }
        require(plan.boundaries.map { it.outgoingInstanceId to it.incomingInstanceId } == expected) {
            "Approved Cohesion plan does not match the saved Structure"
        }
    }

    fun transitionOut(index: Int, isFinal: Boolean): TransitionPlan {
        if (isFinal) return TransitionPlan()
        val bridge = plan.boundaries.getOrNull(index) ?: error("Approved Cohesion is missing boundary ${index + 1}")
        return TransitionPlan(
            type = TransitionType.BRIDGE,
            bars = bridge.bars,
            bridge = BridgePlan(
                energy = when (bridge.energyContour) {
                    EnergyContour.HOLD -> 0.5
                    EnergyContour.RISE -> 0.7
                    EnergyContour.FALL -> 0.3
                },
                elements = when (bridge.bridgeType) {
                    BridgeType.DRUM_FILL, BridgeType.BUILD, BridgeType.CONTINUITY -> listOf(BridgeElement.DRUM_FILL)
                    BridgeType.BASS_WALK -> listOf(BridgeElement.BASS_PICKUP)
                    BridgeType.PAD_SUSTAIN, BridgeType.CHORD_MOTION -> listOf(BridgeElement.PAD_SWELL)
                }
            )
        )
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA_256 = Regex("[0-9a-f]{64}")

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
    val syncopation: Double,
    /** A typed algorithm from the pattern library, never a raw MIDI reference. */
    @Required val pattern: BassPatternId = BassPatternId.SUSTAINED_ROOT
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
    val fillLastBar: Boolean,
    /** A curated in-code groove, never a filename or event list. */
    @Required val pattern: DrumGroovePatternId = DrumGroovePatternId.DUSTY_STRAIGHT,
    @Required val grooveCharacter: GrooveCharacter = GrooveCharacter.STRAIGHT,
    @Required val fillPlacement: DrumFillPlacement = DrumFillPlacement.NONE
) : DetailedInstrumentPlan()

@Serializable
@SerialName("pad")
data class PadInstrumentPlan(
    override val name: String = "pad",
    override val mode: InstrumentMode = InstrumentMode.GENERATED,
    val role: SustainedRole,
    val density: Double,
    val register: MusicalRegister,
    @Required val pattern: PadVoicingPatternId = PadVoicingPatternId.SUSTAINED
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

/** Bounded composition choices supported by the active detailed-arrangement protocol. */
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

/** Producer-controlled feel remains an allow-listed musical descriptor. */
@Serializable
enum class GrooveCharacter {
    @SerialName("straight") STRAIGHT,
    @SerialName("laid_back") LAID_BACK,
    @SerialName("swung") SWUNG,
    @SerialName("half_time") HALF_TIME,
    @SerialName("building") BUILDING
}

/** Drum fills are bounded to a section boundary; no arbitrary tick positions are accepted. */
@Serializable
enum class DrumFillPlacement {
    @SerialName("none") NONE,
    @SerialName("last_bar") LAST_BAR
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
            if (arrangement.cohesion != null) errors += "$label has historical target-order Cohesion references; regenerate this arrangement."
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
                    if (instrument.name != "bass" || instrument.mode != InstrumentMode.GENERATED) {
                        errors += "$label bass role or mode is invalid"
                    }
                    bounded(label, "bass density", instrument.density, errors)
                    if (!instrument.syncopation.isFinite() || instrument.syncopation !in 0.0..0.25) {
                        errors += "$label bass syncopation must be a finite number from 0 through 0.25"
                    }
                    if (instrument.register != MusicalRegister.LOW) errors += "$label bass register must be low"
                }
                is DrumsInstrumentPlan -> {
                    if (instrument.name != "drums" || instrument.mode != InstrumentMode.GENERATED) {
                        errors += "$label drums role or mode is invalid"
                    }
                    bounded(label, "drums density", instrument.density, errors)
                    bounded(label, "kick density", instrument.kickDensity, errors)
                    bounded(label, "hi-hat density", instrument.hiHatDensity, errors)
                    if (!instrument.swing.isFinite() || instrument.swing !in 0.0..0.5) errors += "$label swing must be a finite number from 0 through 0.5"
                }
                is PadInstrumentPlan -> {
                    if (instrument.name != "pad" || instrument.mode != InstrumentMode.GENERATED) {
                        errors += "$label pad role or mode is invalid"
                    }
                    bounded(label, "pad density", instrument.density, errors)
                }
                is StringsInstrumentPlan -> {
                    if (instrument.name != "strings" || instrument.mode != InstrumentMode.GENERATED ||
                        (instrument.role == StringsRole.CLIMAX_REINFORCEMENT && sectionRole != SongSectionPurpose.CLIMAX)) {
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
            register = MusicalRegister.LOW, syncopation = (instrument.density * 0.2).coerceIn(0.0, 0.25),
            pattern = when (instrument.role) {
                "root" -> BassPatternId.SUSTAINED_ROOT
                "root_fifth" -> BassPatternId.ROOT_FIFTH
                "octave" -> BassPatternId.OCTAVE
                else -> BassPatternId.SUSTAINED_ROOT
            }
        )
        "drums" -> DrumsInstrumentPlan(
            role = DrumsRole.entries.first { it.wireName == instrument.role }, density = instrument.density,
            kickDensity = instrument.density,
            snarePattern = when (instrument.role) {
                "minimal" -> SnarePattern.NONE
                "half_time" -> SnarePattern.BEAT_3
                else -> SnarePattern.BEATS_2_4
            },
            hiHatDensity = (instrument.density * 0.8).coerceIn(0.0, 1.0), swing = 0.0, fillLastBar = energy >= 0.7,
            pattern = when (instrument.role) {
                "half_time" -> DrumGroovePatternId.HALF_TIME_POCKET
                "build" -> DrumGroovePatternId.LIFT_BUILD
                "soft_lofi" -> DrumGroovePatternId.LAZY_SWING
                else -> DrumGroovePatternId.DUSTY_STRAIGHT
            },
            grooveCharacter = when (instrument.role) {
                "half_time" -> GrooveCharacter.HALF_TIME
                "build" -> GrooveCharacter.BUILDING
                "soft_lofi" -> GrooveCharacter.SWUNG
                else -> GrooveCharacter.STRAIGHT
            },
            fillPlacement = if (energy >= 0.7) DrumFillPlacement.LAST_BAR else DrumFillPlacement.NONE
        )
        "pad" -> PadInstrumentPlan(
            role = SustainedRole.entries.first { it.wireName == instrument.role }, density = instrument.density, register = register(energy),
            pattern = if (instrument.role == "sustained") PadVoicingPatternId.SUSTAINED else PadVoicingPatternId.COMMON_TONE
        )
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
        return requestQwenWithAutomaticRetries(client, SYSTEM_PROMPT, createUserPrompt(input)) { output ->
            val modelArrangement = try {
                strictJson.decodeFromString<DetailedArrangement>(output)
            } catch (error: Exception) {
                throw IllegalArgumentException("Qwen returned invalid detailed-arrangement JSON: ${error.message}", error)
            }
            val arrangement = bindLockedArrangementFields(modelArrangement, input)
            val validation = arrangement.validate(input)
            require(validation.isValid) { "Invalid Qwen detailed arrangement: ${validation.errors.joinToString("; ")}" }
            arrangement
        }
    }

    /**
     * The song plan and variation plan own section identity, energy, and the
     * exact instrument list. Qwen supplies only the bounded role, density,
     * pattern, groove, fill, and transition controls for each allowed instrument. Ignore extra instruments rather
     * than repeatedly asking it to undo a non-executable orchestration choice;
     * missing or invalid required instruments still fail validation and retry.
     */
    private fun bindLockedArrangementFields(
        arrangement: DetailedArrangement,
        input: DetailedArrangementInput
    ): DetailedArrangement = arrangement.copy(
        version = DetailedArrangement.CURRENT_VERSION,
        cohesion = null,
        sections = arrangement.sections.mapIndexed { position, modelSection ->
            val expected = input.variations.sections.getOrNull(position) ?: return@mapIndexed modelSection
            val byName = modelSection.instruments.associateBy(DetailedInstrumentPlan::name)
            modelSection.copy(
                index = expected.index,
                instanceId = expected.instanceId,
                partId = expected.partId,
                role = expected.purpose,
                energy = expected.energy,
                instruments = expected.instruments.mapNotNull { byName[it.name] }
            )
        }
    )

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
        - The application binds every section index, instanceId, partId, role, energy, and exact instrument list from the validated variations.
        - Return one complete control object for every locked instrument, in the supplied order. Do not add instruments; extra instruments are discarded.
        - Choose only the allow-listed role, density, pattern, groove, swing, fill, and transition fields in the system response schema.
        - Map transitionIntent none to transitionOut type none, build to bridge, and release to crossfade.

        Locked instrument values (copy these literal values exactly; they are not creative decisions):
        ${lockedInstrumentFields(input)}

        Return the complete version 4 object described by the system response schema and no other text.
    """.trimIndent()

    private fun lockedInstrumentFields(input: DetailedArrangementInput): String = input.variations.sections.joinToString("\n") { section ->
        "- Section ${section.index + 1}: " + section.instruments.joinToString(", ") { instrument ->
            "${instrument.name} { mode=${if (instrument.name == "piano") "source" else "generated"}, role=${instrument.role} }"
        }
    }

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
            {"version":4,"sections":[SECTION_OBJECTS]}
            Every section object has exactly index, instanceId, partId, role, energy, instruments, and transitionOut.

            Instrument objects are a tagged union. Use exactly one of these shapes and no extra fields:
            piano:   {"kind":"piano","name":"piano","mode":"source"}
            bass:    {"kind":"bass","name":"bass","mode":"generated","role":"root","density":0.4,"movement":"root_motion","register":"low","syncopation":0.1,"pattern":"sustained-root"}
            drums:   {"kind":"drums","name":"drums","mode":"generated","role":"soft_lofi","density":0.4,"kickDensity":0.4,"snarePattern":"beats_2_4","hiHatDensity":0.3,"swing":0.1,"fillLastBar":false,"pattern":"lazy-swing","grooveCharacter":"swung","fillPlacement":"none"}
            pad:     {"kind":"pad","name":"pad","mode":"generated","role":"texture","density":0.4,"register":"mid","pattern":"common-tone"}
            strings: {"kind":"strings","name":"strings","mode":"generated","role":"sustained_harmony","density":0.4,"register":"mid"}

            Allowed bass roles: root, root_fifth, octave, sustained. Allowed bass movement: static, root_motion, leaping, octaves.
            Allowed bass patterns: sustained-root, root-fifth, octave, walk-to-next-root, diatonic-approach.
            Allowed drum roles: minimal, soft_lofi, standard_groove, half_time, build. Allowed snarePattern: beats_2_4, beat_3, none.
            Allowed pad roles: sustained, texture. Allowed strings roles: sustained_harmony, climax_reinforcement, long_notes,
            simple_countermelody. Allowed register: low, mid, high. Densities are finite 0..1, bass syncopation is finite
            0..0.25, and drum swing is finite 0..0.5. Allowed drum patterns: dusty-straight, lazy-swing, half-time-pocket,
            lift-build. Allowed grooveCharacter: straight, laid_back, swung, half_time, building. fillPlacement is none or
            last_bar and must agree with fillLastBar. Allowed pad patterns: sustained, close, open, common-tone, minimal.
            Bass must use register low. Strings are voiced above the source
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

/** Strict canonical detailed-arrangement persistence. */
object DetailedArrangementStore {
    const val APPROVED_FILE = "arrangement_plan.json"
    const val DRAFT_FILE = "arrangement_plan.draft.json"
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

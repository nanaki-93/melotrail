package app.melotrail.arrangement

import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.MoodRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bounded articulation choices shared by global and detailed arrangement
 * planning. They describe musical behaviour, never a renderer or sample.
 */
@Serializable
enum class ArrangementArticulation {
    @SerialName("source") SOURCE,
    @SerialName("sustained") SUSTAINED,
    @SerialName("legato") LEGATO,
    @SerialName("pulsed") PULSED,
    @SerialName("detached") DETACHED
}

/** How a role may relate to the accepted occurrence-indexed source groove. */
@Serializable
enum class GrooveTimingPolicy {
    /** Preserve the accepted source timing without adding a new offset. */
    @SerialName("source-exact") SOURCE_EXACT,
    /** Follow the source map with a small role-specific allowance. */
    @SerialName("follow-source-subtle") FOLLOW_SOURCE_SUBTLE,
    /** Follow the source map with the wider, still bounded drum allowance. */
    @SerialName("follow-source-standard") FOLLOW_SOURCE_STANDARD,
    /** Keep sustained or texture roles on the authoritative grid. */
    @SerialName("grid-only") GRID_ONLY
}

/** Producer-controlled groove character; it never authorizes a new timing grid. */
@Serializable
enum class GrooveCharacter {
    @SerialName("straight") STRAIGHT,
    @SerialName("laid_back") LAID_BACK,
    @SerialName("swung") SWUNG,
    @SerialName("half_time") HALF_TIME,
    @SerialName("building") BUILDING
}

/** Intended relation to the last accepted sustained-role voicing at a boundary. */
@Serializable
enum class CrossSectionVoicingIntent {
    @SerialName("none") NONE,
    @SerialName("carry") CARRY,
    @SerialName("expand") EXPAND,
    @SerialName("release") RELEASE
}

/** Versioned QP-011 planning limits; QP-012 applies them to generated MIDI. */
object ArrangementGrooveLimits {
    const val VERSION = 1
    const val SUBTLE_MAXIMUM_ADDITIONAL_DEVIATION_TICKS = 12
    const val STANDARD_MAXIMUM_ADDITIONAL_DEVIATION_TICKS = 24
    const val INTRODUCTION_MAXIMUM_SWING = 0.12
    const val MAXIMUM_SWING = 0.25
}

/**
 * Closed timing and character limits for one musical role. QP-012 consumes
 * these limits against generated MIDI and the accepted full-song groove map.
 */
@Serializable
data class RoleGrooveIntent(
    val timingPolicy: GrooveTimingPolicy,
    val character: GrooveCharacter,
    val maximumAdditionalDeviationTicks: Int,
    val maximumSwing: Double
) {
    /** Reject an allowance that would detach the role from its approved timing policy. */
    fun requireValid(role: ArrangementRole) {
        require(maximumAdditionalDeviationTicks in 0..ArrangementGrooveLimits.STANDARD_MAXIMUM_ADDITIONAL_DEVIATION_TICKS) { "Role groove deviation limit is invalid" }
        require(maximumSwing.isFinite() && maximumSwing in 0.0..ArrangementGrooveLimits.MAXIMUM_SWING) { "Role groove swing limit is invalid" }
        if (role in setOf(ArrangementRole.BASS, ArrangementRole.DRUMS)) {
            require(timingPolicy in setOf(GrooveTimingPolicy.FOLLOW_SOURCE_SUBTLE, GrooveTimingPolicy.FOLLOW_SOURCE_STANDARD)) {
                "Bass and drums must follow the accepted source groove map"
            }
        } else {
            require(timingPolicy in setOf(GrooveTimingPolicy.SOURCE_EXACT, GrooveTimingPolicy.GRID_ONLY)) {
                "Pitched non-rhythm roles may not invent a new groove policy"
            }
            require(maximumAdditionalDeviationTicks == 0 && maximumSwing == 0.0) {
                "Only bass and drums may receive additional groove deviation"
            }
        }
        if (timingPolicy == GrooveTimingPolicy.FOLLOW_SOURCE_SUBTLE) {
            require(maximumAdditionalDeviationTicks <= ArrangementGrooveLimits.SUBTLE_MAXIMUM_ADDITIONAL_DEVIATION_TICKS &&
                maximumSwing <= ArrangementGrooveLimits.INTRODUCTION_MAXIMUM_SWING) { "Subtle groove limits are invalid" }
        }
        if (timingPolicy == GrooveTimingPolicy.FOLLOW_SOURCE_STANDARD) {
            require(maximumAdditionalDeviationTicks in ArrangementGrooveLimits.SUBTLE_MAXIMUM_ADDITIONAL_DEVIATION_TICKS + 1..ArrangementGrooveLimits.STANDARD_MAXIMUM_ADDITIONAL_DEVIATION_TICKS &&
                maximumSwing <= ArrangementGrooveLimits.MAXIMUM_SWING) { "Standard groove limits are invalid" }
        }
    }
}

/**
 * Musical intent for one role. It is deliberately separate from logical
 * instrument assignment so replacing a local instrument cannot change the
 * approved musical behaviour.
 */
@Serializable
data class RoleMusicalIntent(
    val role: ArrangementRole,
    val density: Double,
    val register: MusicalRegister,
    val articulation: ArrangementArticulation,
    val groove: RoleGrooveIntent,
    val voicing: CrossSectionVoicingIntent
) {
    /** Verify the role-level controls remain inside the closed planning vocabulary. */
    fun requireValid() {
        require(density.isFinite() && density in 0.0..1.0) { "Role musical-intent density is invalid" }
        groove.requireValid(role)
        if (role == ArrangementRole.BASS) require(register == MusicalRegister.LOW) { "Bass musical intent must use low register" }
        if (role !in setOf(ArrangementRole.TEXTURE, ArrangementRole.AMBIENCE, ArrangementRole.COUNTER_MELODY)) {
            require(voicing == CrossSectionVoicingIntent.NONE) { "Only sustained roles may carry voicing intent" }
        }
    }
}

/** The final accepted pad/string voicings available before one occurrence starts. */
@Serializable
data class AcceptedPadStringVoicing(
    val pad: List<Int> = emptyList(),
    val strings: List<Int> = emptyList()
) {
    /** Verify each accepted boundary voicing is ordered, bounded MIDI evidence. */
    fun requireValid() {
        listOf(pad, strings).forEach { pitches ->
            require(pitches.size <= MAXIMUM_VOICES && pitches.all { it in 0..127 } && pitches == pitches.distinct().sorted()) {
                "Accepted pad/string voicing is invalid"
            }
        }
    }

    companion object {
        const val MAXIMUM_VOICES = 6
    }
}

/** Accepted voicing evidence for one leading contiguous occurrence. */
@Serializable
data class AcceptedOccurrenceVoicing(
    val instanceId: String,
    val voicing: AcceptedPadStringVoicing
) {
    /** Verify that this evidence names one safe persisted occurrence. */
    fun requireValid() {
        require(IDENTIFIER.matches(instanceId)) { "Accepted occurrence voicing instance ID is invalid" }
        voicing.requireValid()
    }
}

/** Comparable versioned evidence for one actual cross-section sustained-role voicing handoff. */
@Serializable
data class SustainedVoicingMovement(
    val version: Int = CURRENT_VERSION,
    val totalSemitoneMotion: Long,
    val maximumVoiceMotion: Int,
    val commonToneCount: Int,
    val enteringVoiceCount: Int,
    val exitingVoiceCount: Int
) {
    /** Reject negative or unversioned movement evidence before it enters a role-validation report. */
    fun requireValid() {
        require(version == CURRENT_VERSION && totalSemitoneMotion >= 0 && maximumVoiceMotion >= 0 && commonToneCount >= 0 &&
            enteringVoiceCount >= 0 && exitingVoiceCount >= 0) { "Sustained voicing movement is invalid" }
    }

    companion object { const val CURRENT_VERSION = 1 }
}

/** Deterministic sustained-voicing assignment and score policy shared by pad, strings, and role validation. */
object SustainedVoicingContinuity {
    const val VERSION = 1
    private const val ENTRY_OR_EXIT_PENALTY = 6L
    private const val REGISTER_DIRECTION_PENALTY = 24L

    /** Measure ordered voice movement, common tones, and cardinality changes without matching voices by raw array index. */
    fun measure(previous: List<Int>, current: List<Int>): SustainedVoicingMovement {
        requireVoicing(previous); requireVoicing(current)
        val pairCount = minOf(previous.size, current.size)
        val matched = if (pairCount == 0) emptyList() else minimumOrderedPairs(previous, current)
        val movements = matched.map { (before, after) -> kotlin.math.abs(after - before) }
        val entering = (current.size - pairCount).coerceAtLeast(0)
        val exiting = (previous.size - pairCount).coerceAtLeast(0)
        val common = previous.groupingBy { it % 12 }.eachCount().entries.sumOf { (pitchClass, count) -> minOf(count, current.count { it % 12 == pitchClass }) }
        return SustainedVoicingMovement(
            totalSemitoneMotion = movements.sum().toLong() + (entering + exiting) * ENTRY_OR_EXIT_PENALTY,
            maximumVoiceMotion = movements.maxOrNull() ?: 0,
            commonToneCount = common,
            enteringVoiceCount = entering,
            exitingVoiceCount = exiting
        ).also(SustainedVoicingMovement::requireValid)
    }

    /** Score candidates by actual movement first, then retain common tones and an explicit intended register direction. */
    fun selectionScore(previous: List<Int>?, current: List<Int>, intendedRange: IntRange): Long {
        requireVoicing(current)
        if (previous.isNullOrEmpty()) return current.sumOf { kotlin.math.abs(it - (intendedRange.first + intendedRange.last) / 2) }.toLong()
        val movement = measure(previous, current)
        val previousCenter = previous.average()
        val currentCenter = current.average()
        val intendedCenter = (intendedRange.first + intendedRange.last) / 2.0
        val directionPenalty = when {
            intendedCenter > previousCenter && currentCenter <= previousCenter -> REGISTER_DIRECTION_PENALTY
            intendedCenter < previousCenter && currentCenter >= previousCenter -> REGISTER_DIRECTION_PENALTY
            else -> 0L
        }
        return movement.totalSemitoneMotion * 100L - movement.commonToneCount * 10L + directionPenalty
    }

    /** Pair the smaller voicing into the larger one while preserving ascending voice order. */
    private fun minimumOrderedPairs(previous: List<Int>, current: List<Int>): List<Pair<Int, Int>> =
        if (previous.size <= current.size) choosePairs(previous, current) else choosePairs(current, previous).map { (after, before) -> before to after }

    /** Exhaustively select the small ordered assignment space, avoiding a greedy voice-identity reset. */
    private fun choosePairs(shorter: List<Int>, longer: List<Int>): List<Pair<Int, Int>> {
        var best: List<Pair<Int, Int>>? = null
        /** Visit each order-preserving assignment exactly once and retain the smallest total semitone movement. */
        fun search(shortIndex: Int, longIndex: Int, pairs: List<Pair<Int, Int>>) {
            if (shortIndex == shorter.size) {
                if (best == null || pairs.sumOf { (left, right) -> kotlin.math.abs(right - left) } < best!!.sumOf { (left, right) -> kotlin.math.abs(right - left) }) best = pairs
                return
            }
            val remaining = shorter.size - shortIndex
            for (candidate in longIndex..longer.size - remaining) {
                search(shortIndex + 1, candidate + 1, pairs + (shorter[shortIndex] to longer[candidate]))
            }
        }
        search(0, 0, emptyList())
        return requireNotNull(best)
    }

    /** Keep metric inputs inside the same bounded, ordered sustained-voicing domain used by the planner. */
    private fun requireVoicing(pitches: List<Int>) {
        require(pitches.size <= AcceptedPadStringVoicing.MAXIMUM_VOICES && pitches.all { it in 0..127 } && pitches == pitches.distinct().sorted()) {
            "Sustained voicing is invalid"
        }
    }
}

/**
 * Versioned, occurrence-bound musical intent emitted by the global planner.
 * It is data for review and detailed planning; it contains no note events.
 */
@Serializable
data class SectionMusicalIntent(
    val version: Int = CURRENT_VERSION,
    val profile: CompositionProfileRef? = null,
    val mood: MoodRef? = null,
    val purpose: SongSectionPurpose,
    val energy: Double,
    val grooveMapSha256: String? = null,
    val roles: List<RoleMusicalIntent>,
    val previousAcceptedVoicing: AcceptedPadStringVoicing = AcceptedPadStringVoicing()
) {
    /** Verify this occurrence intent remains bound to its typed musical context. */
    fun requireValid() {
        require(version == CURRENT_VERSION) { "Unsupported section musical-intent version: $version" }
        require((profile == null) == (mood == null)) { "Section musical intent must provide both profile and mood or neither" }
        profile?.requireValid(); mood?.requireValid()
        require(energy.isFinite() && energy in 0.0..1.0) { "Section musical-intent energy is invalid" }
        require(grooveMapSha256 == null || SHA_256.matches(grooveMapSha256)) { "Section musical-intent groove-map hash is invalid" }
        require(roles.isNotEmpty() && roles.map(RoleMusicalIntent::role).distinct().size == roles.size) {
            "Section musical-intent roles are invalid"
        }
        roles.forEach(RoleMusicalIntent::requireValid)
        previousAcceptedVoicing.requireValid()
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Deterministically resolves the user-authorized context into reviewable
 * occurrence intent. Qwen receives this intent but never owns its values.
 */
object SectionMusicalIntentPlanner {
    /** Resolve every occurrence in order, carrying only accepted prior sustained voicings forward. */
    fun create(
        input: SongPlanningInput,
        sections: List<SongPlanSection>,
        energyCurve: List<Double>
    ): List<SectionMusicalIntent> {
        require(sections.size == energyCurve.size) { "Section musical intent requires matching sections and energy" }
        val acceptedByOccurrence = input.acceptedOccurrenceVoicings.associateBy(AcceptedOccurrenceVoicing::instanceId)
        var previous = AcceptedPadStringVoicing()
        return sections.mapIndexed { index, section ->
            val intent = createOne(
                input = input,
                section = section,
                energy = energyCurve[index],
                fallingEnergy = index > 0 && energyCurve[index] < energyCurve[index - 1],
                previousAcceptedVoicing = previous
            )
            acceptedByOccurrence[section.instanceId]?.let { accepted ->
                previous = AcceptedPadStringVoicing(
                    pad = accepted.voicing.pad.ifEmpty { previous.pad },
                    strings = accepted.voicing.strings.ifEmpty { previous.strings }
                )
            }
            intent
        }
    }

    /** Resolve one occurrence after its prior accepted voicing has been established. */
    private fun createOne(
        input: SongPlanningInput,
        section: SongPlanSection,
        energy: Double,
        fallingEnergy: Boolean,
        previousAcceptedVoicing: AcceptedPadStringVoicing
    ): SectionMusicalIntent = SectionMusicalIntent(
        profile = (input.planningSoundContext ?: input.soundContext)?.profile,
        mood = (input.planningSoundContext ?: input.soundContext)?.mood,
        purpose = section.purpose,
        energy = energy,
        grooveMapSha256 = input.grooveMapHash(),
        roles = section.instrumentProgression.map { instrument -> roleIntent(input, instrument, section, energy, fallingEnergy) },
        previousAcceptedVoicing = previousAcceptedVoicing
    ).also(SectionMusicalIntent::requireValid)

    /** Translate one logical assignment into independent, bounded musical behaviour. */
    private fun roleIntent(
        input: SongPlanningInput,
        instrument: String,
        section: SongPlanSection,
        energy: Double,
        fallingEnergy: Boolean
    ): RoleMusicalIntent {
        val selected = input.intentsFor(section.purpose).singleOrNull {
            LegacyLogicalInstrumentRoles.logicalFor(it.role) == instrument
        }
        val role = selected?.role ?: LegacyLogicalInstrumentRoles.roleFor(instrument)
        val articulation = selected?.articulationTraits?.let { traits ->
            when {
                SoundTrait.LEGATO in traits -> ArrangementArticulation.LEGATO
                SoundTrait.SUSTAINED in traits -> ArrangementArticulation.SUSTAINED
                SoundTrait.STACCATO in traits || SoundTrait.SHORT in traits -> ArrangementArticulation.DETACHED
                else -> defaultArticulation(role, section.purpose)
            }
        } ?: defaultArticulation(role, section.purpose)
        val density = when (instrument) {
            "piano" -> 1.0
            else -> (energy + when {
                fallingEnergy || section.purpose == SongSectionPurpose.CONCLUSION -> -0.12
                section.occurrence > 1 -> 0.08
                else -> 0.0
            }).coerceIn(0.10, 1.0)
        }
        return RoleMusicalIntent(
            role = role,
            density = density,
            register = registerFor(role, energy),
            articulation = articulation,
            groove = grooveFor(role, section.purpose),
            voicing = voicingFor(role, section.purpose)
        )
    }

    /** Choose the deterministic articulation only when the user did not constrain one. */
    private fun defaultArticulation(role: ArrangementRole, purpose: SongSectionPurpose): ArrangementArticulation = when (role) {
        ArrangementRole.MELODY, ArrangementRole.HARMONY -> ArrangementArticulation.SOURCE
        ArrangementRole.BASS, ArrangementRole.DRUMS -> if (purpose == SongSectionPurpose.INTRODUCTION) ArrangementArticulation.DETACHED else ArrangementArticulation.PULSED
        ArrangementRole.TEXTURE, ArrangementRole.AMBIENCE -> ArrangementArticulation.SUSTAINED
        ArrangementRole.COUNTER_MELODY -> ArrangementArticulation.LEGATO
    }

    /** Choose a safe register from the role and approved energy, keeping bass low. */
    private fun registerFor(role: ArrangementRole, energy: Double): MusicalRegister = when (role) {
        ArrangementRole.BASS -> MusicalRegister.LOW
        ArrangementRole.MELODY, ArrangementRole.HARMONY -> MusicalRegister.MID
        else -> when {
            energy < 0.34 -> MusicalRegister.LOW
            energy > 0.72 -> MusicalRegister.HIGH
            else -> MusicalRegister.MID
        }
    }

    /** Bind every rhythm role to the accepted groove map with its own allowance. */
    private fun grooveFor(role: ArrangementRole, purpose: SongSectionPurpose): RoleGrooveIntent = when (role) {
        ArrangementRole.BASS -> RoleGrooveIntent(
            GrooveTimingPolicy.FOLLOW_SOURCE_SUBTLE,
            if (purpose == SongSectionPurpose.RELEASE) GrooveCharacter.LAID_BACK else GrooveCharacter.STRAIGHT,
            ArrangementGrooveLimits.SUBTLE_MAXIMUM_ADDITIONAL_DEVIATION_TICKS,
            0.0
        )
        ArrangementRole.DRUMS -> RoleGrooveIntent(
            GrooveTimingPolicy.FOLLOW_SOURCE_STANDARD,
            when (purpose) {
                SongSectionPurpose.INTRODUCTION -> GrooveCharacter.LAID_BACK
                SongSectionPurpose.CLIMAX -> GrooveCharacter.BUILDING
                SongSectionPurpose.RELEASE -> GrooveCharacter.HALF_TIME
                else -> GrooveCharacter.STRAIGHT
            },
            ArrangementGrooveLimits.STANDARD_MAXIMUM_ADDITIONAL_DEVIATION_TICKS,
            if (purpose == SongSectionPurpose.INTRODUCTION) ArrangementGrooveLimits.INTRODUCTION_MAXIMUM_SWING else 0.0
        )
        ArrangementRole.MELODY, ArrangementRole.HARMONY -> RoleGrooveIntent(GrooveTimingPolicy.SOURCE_EXACT, GrooveCharacter.STRAIGHT, 0, 0.0)
        else -> RoleGrooveIntent(GrooveTimingPolicy.GRID_ONLY, GrooveCharacter.STRAIGHT, 0, 0.0)
    }

    /** State how an optional sustained role should relate to the previous accepted voicing. */
    private fun voicingFor(role: ArrangementRole, purpose: SongSectionPurpose): CrossSectionVoicingIntent = when (role) {
        ArrangementRole.TEXTURE, ArrangementRole.AMBIENCE, ArrangementRole.COUNTER_MELODY -> when (purpose) {
            SongSectionPurpose.CLIMAX -> CrossSectionVoicingIntent.EXPAND
            SongSectionPurpose.RELEASE, SongSectionPurpose.CONCLUSION -> CrossSectionVoicingIntent.RELEASE
            else -> CrossSectionVoicingIntent.CARRY
        }
        else -> CrossSectionVoicingIntent.NONE
    }
}

private val IDENTIFIER = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA_256 = Regex("[0-9a-f]{64}")

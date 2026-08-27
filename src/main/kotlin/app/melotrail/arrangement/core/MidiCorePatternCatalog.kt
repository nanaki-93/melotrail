package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole

/** Stable target pattern families; no family names identify an audio asset. */
enum class MidiCorePatternFamily {
    BASS,
    CHORD_RHYTHM,
    DRUM_GROOVE,
    DRUM_FILL,
}

/** The bounded bass vocabulary consumed by the MIDI Core bass generator. */
enum class MidiCoreBassPatternId(val id: String) {
    SUSTAINED_ROOT("bass.sustained-root"),
    ROOT_FIFTH("bass.root-fifth"),
    OCTAVE("bass.octave"),
    WALK_TO_NEXT_ROOT("bass.walk-to-next-root"),
    DIATONIC_APPROACH("bass.diatonic-approach"),
}

/** The bounded chord-attack vocabulary consumed by the MIDI Core chord generator. */
enum class MidiCoreChordRhythmPatternId(val id: String) {
    SUSTAINED("chords.rhythm.sustained"),
    LAID_BACK_QUARTERS("chords.rhythm.laid-back-quarters"),
    LATE_ENTRY("chords.rhythm.late-entry"),
    DUSTY_OFFBEATS("chords.rhythm.dusty-offbeats"),
    BROKEN_SYNCOPATION("chords.rhythm.broken-syncopation"),
    BRIDGE_HALF_TIME("chords.rhythm.bridge-half-time"),
}

/** The bounded complete groove vocabulary consumed by the MIDI Core drum generator. */
enum class MidiCoreDrumGroovePatternId(val id: String) {
    DUSTY_STRAIGHT("drums.dusty-straight"),
    LAZY_SWING("drums.lazy-swing"),
    HALF_TIME_POCKET("drums.half-time-pocket"),
    LIFT_BUILD("drums.lift-build"),
}

/** The bounded section-ending fill vocabulary consumed by the MIDI Core drum generator. */
enum class MidiCoreDrumFillPatternId(val id: String) {
    SOFT_TWO_STROKE("drums.fill.soft-two-stroke"),
    DUSTY_SNARE_ROLL("drums.fill.dusty-snare-roll"),
    KICK_SNARE_TURNAROUND("drums.fill.kick-snare-turnaround"),
    BRIDGE_HALF_TIME_BREAK("drums.fill.bridge-half-time-break"),
}

/** A reviewable beat-relative attack in a sixteen-step quarter-note bar. */
data class MidiCoreChordRhythmStep(
    val sixteenth: Int,
    val durationSixteenths: Int,
    val velocityOffset: Int,
) {
    init {
        require(sixteenth in 0..15 && durationSixteenths in 1..16 && sixteenth + durationSixteenths <= 16) {
            "Chord-rhythm step is outside its authored bar"
        }
        require(velocityOffset in -127..127) { "Chord-rhythm velocity offset is invalid" }
    }
}

/** A reviewable drum attack in a sixteen-step quarter-note bar. */
data class MidiCoreDrumStep(
    val hit: MidiCoreDrumHit,
    val sixteenth: Int,
    val velocityOffset: Int,
) {
    init {
        require(sixteenth in 0..15) { "Drum step position is invalid" }
        require(velocityOffset in -127..127) { "Drum velocity offset is invalid" }
    }
}

/** Closed GM-oriented starter attacks; target logic never selects a kit file. */
enum class MidiCoreDrumHit {
    KICK,
    SNARE,
    CLOSED_HAT,
    OPEN_HAT,
}

/** One complete authored chord-rhythm variant. */
data class MidiCoreChordRhythmPattern(
    val id: MidiCoreChordRhythmPatternId,
    val displayName: String,
    val steps: List<MidiCoreChordRhythmStep>,
) {
    init {
        require(displayName.isNotBlank()) { "Chord-rhythm display name must not be blank" }
        require(steps.isNotEmpty() && steps == steps.sortedBy(MidiCoreChordRhythmStep::sixteenth)) {
            "Chord-rhythm steps must be non-empty and ordered"
        }
    }
}

/** One complete authored drum-groove or section-fill variant. */
data class MidiCoreDrumPattern(
    val id: String,
    val family: MidiCorePatternFamily,
    val displayName: String,
    val steps: List<MidiCoreDrumStep>,
) {
    init {
        require(family in setOf(MidiCorePatternFamily.DRUM_GROOVE, MidiCorePatternFamily.DRUM_FILL)) {
            "Drum pattern family is invalid"
        }
        require(displayName.isNotBlank() && steps.isNotEmpty() && steps == steps.sortedWith(
            compareBy<MidiCoreDrumStep> { it.sixteenth }.thenBy { it.hit.ordinal },
        )) { "Drum pattern steps must be non-empty and ordered" }
        require(id.matches(PATTERN_ID)) { "Drum pattern ID is invalid" }
    }

    private companion object {
        val PATTERN_ID = Regex("[a-z][a-z0-9-]{0,47}(\\.[a-z][a-z0-9-]{0,47})+")
    }
}

/** Stable inventory row exposed to arrangement UI and evidence checks. */
data class MidiCorePatternInventoryEntry(
    val id: String,
    val role: CandidateRole,
    val family: MidiCorePatternFamily,
    val displayName: String,
)

/** The curated, deterministic MIDI-only pattern catalog for the three target roles. */
object MidiCorePatternCatalog {
    /** Version of the in-code pattern inventory included in generator identity. */
    const val VERSION = 1

    /** Complete chord-rhythm variants, copied as musical steps rather than legacy pattern objects. */
    val chordRhythms: List<MidiCoreChordRhythmPattern> = listOf(
        MidiCoreChordRhythmPattern(
            MidiCoreChordRhythmPatternId.SUSTAINED,
            "Sustained",
            listOf(MidiCoreChordRhythmStep(0, 16, 0)),
        ),
        MidiCoreChordRhythmPattern(
            MidiCoreChordRhythmPatternId.LAID_BACK_QUARTERS,
            "Laid-back quarters",
            listOf(
                MidiCoreChordRhythmStep(0, 3, -2), MidiCoreChordRhythmStep(4, 3, -5),
                MidiCoreChordRhythmStep(8, 3, 0), MidiCoreChordRhythmStep(12, 3, -4),
            ),
        ),
        MidiCoreChordRhythmPattern(
            MidiCoreChordRhythmPatternId.LATE_ENTRY,
            "Late entry",
            listOf(
                MidiCoreChordRhythmStep(4, 3, -5), MidiCoreChordRhythmStep(8, 3, 0),
                MidiCoreChordRhythmStep(12, 3, -4),
            ),
        ),
        MidiCoreChordRhythmPattern(
            MidiCoreChordRhythmPatternId.DUSTY_OFFBEATS,
            "Dusty offbeats",
            listOf(
                MidiCoreChordRhythmStep(2, 2, -6), MidiCoreChordRhythmStep(6, 2, -3),
                MidiCoreChordRhythmStep(10, 2, -5), MidiCoreChordRhythmStep(14, 2, -1),
            ),
        ),
        MidiCoreChordRhythmPattern(
            MidiCoreChordRhythmPatternId.BROKEN_SYNCOPATION,
            "Broken syncopation",
            listOf(
                MidiCoreChordRhythmStep(0, 3, 0), MidiCoreChordRhythmStep(6, 2, -5),
                MidiCoreChordRhythmStep(10, 2, -3), MidiCoreChordRhythmStep(14, 2, -1),
            ),
        ),
        MidiCoreChordRhythmPattern(
            MidiCoreChordRhythmPatternId.BRIDGE_HALF_TIME,
            "Bridge half-time",
            listOf(MidiCoreChordRhythmStep(0, 6, -5), MidiCoreChordRhythmStep(8, 6, -2)),
        ),
    )

    /** Complete drum grooves retain every authored attack; density chooses among these variants. */
    val drumGrooves: List<MidiCoreDrumPattern> = listOf(
        drumPattern(
            MidiCoreDrumGroovePatternId.DUSTY_STRAIGHT.id,
            MidiCorePatternFamily.DRUM_GROOVE,
            "Dusty straight",
            listOf(
                drumStep(MidiCoreDrumHit.KICK, 0, 8), drumStep(MidiCoreDrumHit.KICK, 8, 2),
                drumStep(MidiCoreDrumHit.SNARE, 4, 5), drumStep(MidiCoreDrumHit.SNARE, 12, 7),
                *hats(0, 2, -10),
            ),
        ),
        drumPattern(
            MidiCoreDrumGroovePatternId.LAZY_SWING.id,
            MidiCorePatternFamily.DRUM_GROOVE,
            "Lazy swing",
            listOf(
                drumStep(MidiCoreDrumHit.KICK, 0, 8), drumStep(MidiCoreDrumHit.KICK, 10, 1),
                drumStep(MidiCoreDrumHit.SNARE, 4, 5), drumStep(MidiCoreDrumHit.SNARE, 12, 7),
                *hats(0, 2, -12),
            ),
        ),
        drumPattern(
            MidiCoreDrumGroovePatternId.HALF_TIME_POCKET.id,
            MidiCorePatternFamily.DRUM_GROOVE,
            "Half-time pocket",
            listOf(
                drumStep(MidiCoreDrumHit.KICK, 0, 8), drumStep(MidiCoreDrumHit.KICK, 10, 1),
                drumStep(MidiCoreDrumHit.SNARE, 8, 7), *hats(0, 2, -11),
            ),
        ),
        drumPattern(
            MidiCoreDrumGroovePatternId.LIFT_BUILD.id,
            MidiCorePatternFamily.DRUM_GROOVE,
            "Lift build",
            listOf(
                drumStep(MidiCoreDrumHit.KICK, 0, 5), drumStep(MidiCoreDrumHit.KICK, 8, 5),
                drumStep(MidiCoreDrumHit.SNARE, 4, 4), drumStep(MidiCoreDrumHit.SNARE, 12, 6),
                *hats(0, 1, -9),
            ),
        ),
    )

    /** Section-ending fills are separate from the complete groove and are never inferred from a filename. */
    val drumFills: List<MidiCoreDrumPattern> = listOf(
        drumPattern(
            MidiCoreDrumFillPatternId.SOFT_TWO_STROKE.id,
            MidiCorePatternFamily.DRUM_FILL,
            "Soft two-stroke",
            listOf(drumStep(MidiCoreDrumHit.SNARE, 14, -6), drumStep(MidiCoreDrumHit.SNARE, 15, 2)),
        ),
        drumPattern(
            MidiCoreDrumFillPatternId.DUSTY_SNARE_ROLL.id,
            MidiCorePatternFamily.DRUM_FILL,
            "Dusty snare roll",
            listOf(
                drumStep(MidiCoreDrumHit.SNARE, 12, 0), drumStep(MidiCoreDrumHit.SNARE, 13, 4),
                drumStep(MidiCoreDrumHit.SNARE, 14, 8), drumStep(MidiCoreDrumHit.SNARE, 15, 12),
            ),
        ),
        drumPattern(
            MidiCoreDrumFillPatternId.KICK_SNARE_TURNAROUND.id,
            MidiCorePatternFamily.DRUM_FILL,
            "Kick-snare turnaround",
            listOf(
                drumStep(MidiCoreDrumHit.KICK, 12, -2), drumStep(MidiCoreDrumHit.SNARE, 14, 4),
                drumStep(MidiCoreDrumHit.SNARE, 15, 9),
            ),
        ),
        drumPattern(
            MidiCoreDrumFillPatternId.BRIDGE_HALF_TIME_BREAK.id,
            MidiCorePatternFamily.DRUM_FILL,
            "Bridge half-time break",
            listOf(
                drumStep(MidiCoreDrumHit.OPEN_HAT, 8, -10), drumStep(MidiCoreDrumHit.KICK, 10, -4),
                drumStep(MidiCoreDrumHit.SNARE, 12, 1), drumStep(MidiCoreDrumHit.KICK, 14, -2),
                drumStep(MidiCoreDrumHit.SNARE, 15, 7),
            ),
        ),
    )

    /** Return every target pattern ID in stable family/ID order for evidence and UI choices. */
    fun inventory(): List<MidiCorePatternInventoryEntry> = buildList {
        chordRhythms.forEach { pattern ->
            add(MidiCorePatternInventoryEntry(pattern.id.id, CandidateRole.CHORDS, MidiCorePatternFamily.CHORD_RHYTHM, pattern.displayName))
        }
        MidiCoreBassPatternId.entries.forEach { pattern ->
            add(MidiCorePatternInventoryEntry(pattern.id, CandidateRole.BASS, MidiCorePatternFamily.BASS, pattern.name.lowercase().replace('_', ' ')))
        }
        drumGrooves.forEach { pattern ->
            add(MidiCorePatternInventoryEntry(pattern.id, CandidateRole.DRUMS, pattern.family, pattern.displayName))
        }
        drumFills.forEach { pattern ->
            add(MidiCorePatternInventoryEntry(pattern.id, CandidateRole.DRUMS, pattern.family, pattern.displayName))
        }
    }.sortedWith(compareBy<MidiCorePatternInventoryEntry> { it.role.ordinal }.thenBy { it.family.ordinal }.thenBy { it.id })

    /** Return all IDs allowed for one role, including drum fills for the drum role. */
    fun allowedPatternIds(role: CandidateRole): List<String> = inventory()
        .filter { it.role == role }
        .map(MidiCorePatternInventoryEntry::id)

    /** Verify that a requested pattern belongs to the selected target role. */
    fun requireAllowed(role: CandidateRole, patternId: String) {
        require(patternId in allowedPatternIds(role)) {
            "Pattern '$patternId' is not allowed for ${role.name.lowercase()}"
        }
    }

    /** Resolve an authored chord-rhythm variant without exposing the legacy library. */
    fun chordRhythm(patternId: String): MidiCoreChordRhythmPattern = chordRhythms.singleOrNull { it.id.id == patternId }
        ?: throw IllegalArgumentException("Unknown chord-rhythm pattern '$patternId'")

    /** Resolve an authored bass pattern identity. */
    fun bassPattern(patternId: String): MidiCoreBassPatternId = MidiCoreBassPatternId.entries.singleOrNull { it.id == patternId }
        ?: throw IllegalArgumentException("Unknown bass pattern '$patternId'")

    /** Resolve an authored complete drum groove. */
    fun drumGroove(patternId: String): MidiCoreDrumPattern = drumGrooves.singleOrNull { it.id == patternId }
        ?: throw IllegalArgumentException("Unknown drum groove pattern '$patternId'")

    /** Resolve an authored drum fill. */
    fun drumFill(patternId: String): MidiCoreDrumPattern = drumFills.singleOrNull { it.id == patternId }
        ?: throw IllegalArgumentException("Unknown drum fill pattern '$patternId'")

    /** Build a sorted target drum pattern from authored attacks. */
    private fun drumPattern(
        id: String,
        family: MidiCorePatternFamily,
        displayName: String,
        steps: List<MidiCoreDrumStep>,
    ) = MidiCoreDrumPattern(
        id,
        family,
        displayName,
        steps.sortedWith(compareBy<MidiCoreDrumStep> { it.sixteenth }.thenBy { it.hit.ordinal }),
    )

    /** Build one authored drum step with a bounded velocity accent. */
    private fun drumStep(hit: MidiCoreDrumHit, sixteenth: Int, velocityOffset: Int) =
        MidiCoreDrumStep(hit, sixteenth, velocityOffset)

    /** Expand one authored hat cadence into the target sixteenth grid. */
    private fun hats(start: Int, step: Int, velocityOffset: Int): Array<MidiCoreDrumStep> =
        (start..15 step step).map { MidiCoreDrumStep(MidiCoreDrumHit.CLOSED_HAT, it, velocityOffset) }.toTypedArray()
}

/** Musical performance intent used by MIDI generators; it does not select an audio patch. */
enum class MidiCorePerformanceArticulation {
    SUSTAINED,
    PULSED,
    MUTED_PLUCKED,
    GRID_PERCUSSIVE,
}

/** Bounded role profile containing only MIDI performance intent and register limits. */
data class MidiCorePerformanceProfile(
    val id: String,
    val role: CandidateRole,
    val articulation: MidiCorePerformanceArticulation,
    val noteLengthNumerator: Int,
    val noteLengthDenominator: Int,
    val velocity: Int,
    val register: IntRange,
) {
    init {
        require(id.matches(PROFILE_ID)) { "Performance profile ID is invalid" }
        require(noteLengthNumerator > 0 && noteLengthDenominator > 0) { "Performance note length is invalid" }
        require(velocity in 1..127 && !register.isEmpty() && register.first in 0..127 && register.last in 0..127) {
            "Performance profile MIDI bounds are invalid"
        }
    }

    /** Stable canonical representation included in the generation context hash. */
    val canonicalSerialization: String
        get() = listOf(
            id, role.name, articulation.name, noteLengthNumerator.toString(), noteLengthDenominator.toString(),
            velocity.toString(), register.first.toString(), register.last.toString(),
        ).joinToString("|")

    private companion object {
        val PROFILE_ID = Regex("[a-z][a-z0-9_.-]{0,119}")
    }
}

/** Curated role performance profiles; they contain no instrument, renderer, or sound-library choice. */
object MidiCorePerformanceProfileCatalog {
    /** Version of the target performance vocabulary. */
    const val VERSION = 1

    /** All supported role profiles in stable ID order. */
    val profiles: List<MidiCorePerformanceProfile> = listOf(
        MidiCorePerformanceProfile("chords.sustained", CandidateRole.CHORDS, MidiCorePerformanceArticulation.SUSTAINED, 1, 1, 64, 48..84),
        MidiCorePerformanceProfile("chords.pulsed", CandidateRole.CHORDS, MidiCorePerformanceArticulation.PULSED, 3, 4, 62, 48..84),
        MidiCorePerformanceProfile("bass.sustained-sub-like", CandidateRole.BASS, MidiCorePerformanceArticulation.SUSTAINED, 1, 1, 78, 28..55),
        MidiCorePerformanceProfile("bass.muted-plucked", CandidateRole.BASS, MidiCorePerformanceArticulation.MUTED_PLUCKED, 3, 4, 74, 28..55),
        MidiCorePerformanceProfile("drums.dusty", CandidateRole.DRUMS, MidiCorePerformanceArticulation.GRID_PERCUSSIVE, 1, 16, 76, 0..127),
        MidiCorePerformanceProfile("drums.lifted", CandidateRole.DRUMS, MidiCorePerformanceArticulation.GRID_PERCUSSIVE, 1, 16, 82, 0..127),
    ).sortedBy(MidiCorePerformanceProfile::id)

    /** Return stable profile IDs allowed for one role. */
    fun allowedProfileIds(role: CandidateRole): List<String> = profiles.filter { it.role == role }.map(MidiCorePerformanceProfile::id)

    /** Resolve a profile by ID and verify that it belongs to the requested role. */
    fun requireForRole(role: CandidateRole, profileId: String): MidiCorePerformanceProfile = profiles.singleOrNull { it.id == profileId && it.role == role }
        ?: throw IllegalArgumentException("Performance profile '$profileId' is not allowed for ${role.name.lowercase()}")
}

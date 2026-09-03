package app.melotrail.arrangement.core

import app.melotrail.project.CandidateRole

/**
 * A readable, versioned MIDI-only arrangement choice. Styles choose musical
 * behavior for every generated role; they never select an instrument or an
 * audio asset.
 */
data class MidiCoreArrangementStyle(
    val id: String,
    val displayName: String,
    val summary: String,
    val roles: List<MidiCoreArrangementStyleRole>,
) {
    init {
        require(ID.matches(id)) { "Arrangement style ID is invalid" }
        require(displayName.isNotBlank() && summary.isNotBlank()) { "Arrangement style text must not be blank" }
        require(roles.map(MidiCoreArrangementStyleRole::role) == CandidateRole.entries) {
            "An arrangement style must define Chords, Bass, and Drums in stable order"
        }
        roles.forEach { choice ->
            MidiCorePerformanceProfileCatalog.requireForRole(choice.role, choice.performanceProfileId)
            MidiCorePatternCatalog.requireAllowed(choice.role, choice.patternId)
        }
    }

    fun role(role: CandidateRole): MidiCoreArrangementStyleRole = requireNotNull(roles.singleOrNull { it.role == role })

    private companion object {
        val ID = Regex("[a-z][a-z0-9-]{2,47}")
    }
}

/** One role's bounded profile, pattern, and section-energy behavior within a style. */
data class MidiCoreArrangementStyleRole(
    val role: CandidateRole,
    val performanceProfileId: String,
    val patternId: String,
    val sectionPolicy: MidiCoreSectionPolicy,
) {
    init {
        require(performanceProfileId.isNotBlank() && patternId.isNotBlank()) { "Style role choices must not be blank" }
        if (role != CandidateRole.DRUMS) require(sectionPolicy.fillPatternId == null) {
            "Only a drum style role may choose a fill"
        }
    }
}

/** Stable curated style catalog used by instant previews and later draft generation. */
object MidiCoreArrangementStyleCatalog {
    /** Increment only when a bundle's meaning or ordering intentionally changes. */
    const val VERSION = 1

    /** Catalog order is intentional: calm foundations first, then more energetic choices. */
    val styles: List<MidiCoreArrangementStyle> = listOf(
        style(
            "open-sky",
            "Open Sky",
            "Long harmonic space, a grounded bass line, and an unhurried pulse.",
            chords = role("chords.sustained", "chords.rhythm.sustained", energy = 0.34, density = 0.42),
            bass = role("bass.sustained-sub-like", "bass.sustained-root", energy = 0.40, density = 0.42),
            drums = role("drums.dusty", "drums.dusty-straight", energy = 0.32, density = 0.36, fill = "drums.fill.soft-two-stroke"),
        ),
        style(
            "late-night",
            "Late Night",
            "Offbeat chord movement, a relaxed bass response, and a lazy pocket.",
            chords = role("chords.pulsed", "chords.rhythm.dusty-offbeats", energy = 0.48, density = 0.56),
            bass = role("bass.muted-plucked", "bass.root-fifth", energy = 0.52, density = 0.56),
            drums = role("drums.dusty", "drums.lazy-swing", energy = 0.48, density = 0.52, fill = "drums.fill.dusty-snare-roll"),
        ),
        style(
            "steady-road",
            "Steady Road",
            "Laid-back chords, octave motion, and a clear forward groove.",
            chords = role("chords.pulsed", "chords.rhythm.laid-back-quarters", energy = 0.58, density = 0.62),
            bass = role("bass.sustained-sub-like", "bass.octave", energy = 0.60, density = 0.64),
            drums = role("drums.dusty", "drums.dusty-straight", energy = 0.60, density = 0.62, fill = "drums.fill.kick-snare-turnaround"),
        ),
        style(
            "rising-room",
            "Rising Room",
            "Syncopated harmony, active bass movement, and a brighter lift.",
            chords = role("chords.pulsed", "chords.rhythm.broken-syncopation", energy = 0.74, density = 0.74),
            bass = role("bass.muted-plucked", "bass.octave", energy = 0.76, density = 0.76),
            drums = role("drums.lifted", "drums.lift-build", energy = 0.78, density = 0.78, fill = "drums.fill.dusty-snare-roll"),
        ),
        style(
            "wide-bridge",
            "Wide Bridge",
            "A spacious half-time turn with a deliberate transition into the next idea.",
            chords = role("chords.sustained", "chords.rhythm.bridge-half-time", energy = 0.52, density = 0.48),
            bass = role("bass.sustained-sub-like", "bass.walk-to-next-root", energy = 0.54, density = 0.54),
            drums = role("drums.lifted", "drums.half-time-pocket", energy = 0.56, density = 0.52, fill = "drums.fill.bridge-half-time-break"),
        ),
    )

    init {
        require(styles.size in 4..6) { "The MIDI Core style catalog must contain four to six styles" }
        require(styles.map(MidiCoreArrangementStyle::id).distinct().size == styles.size) { "Arrangement style IDs must be unique" }
    }

    fun require(styleId: String): MidiCoreArrangementStyle = styles.singleOrNull { it.id == styleId }
        ?: throw IllegalArgumentException("Unknown arrangement style '$styleId'")

    private fun style(
        id: String,
        name: String,
        summary: String,
        chords: MidiCoreArrangementStyleRole,
        bass: MidiCoreArrangementStyleRole,
        drums: MidiCoreArrangementStyleRole,
    ) = MidiCoreArrangementStyle(id, name, summary, listOf(chords, bass, drums))

    private fun role(
        profile: String,
        pattern: String,
        energy: Double,
        density: Double,
        fill: String? = null,
    ): MidiCoreArrangementStyleRole {
        val candidateRole = when {
            profile.startsWith("chords.") -> CandidateRole.CHORDS
            profile.startsWith("bass.") -> CandidateRole.BASS
            profile.startsWith("drums.") -> CandidateRole.DRUMS
            else -> error("Style profile role cannot be inferred")
        }
        return MidiCoreArrangementStyleRole(
            candidateRole,
            profile,
            pattern,
            MidiCoreSectionPolicy(energy = energy, density = density, fillPatternId = fill),
        )
    }
}

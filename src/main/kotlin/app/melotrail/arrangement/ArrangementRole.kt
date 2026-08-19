package app.melotrail.arrangement

import app.melotrail.profile.CompositionProfileRef
import app.melotrail.profile.LicensePreference
import app.melotrail.profile.MoodRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Musical work requested from an arrangement layer.  These identifiers are
 * intentionally independent of the local instrument registry and renderer.
 */
@Serializable
enum class ArrangementRole {
    @SerialName("melody") MELODY,
    @SerialName("harmony") HARMONY,
    @SerialName("bass") BASS,
    @SerialName("drums") DRUMS,
    @SerialName("counter-melody") COUNTER_MELODY,
    @SerialName("texture") TEXTURE,
    @SerialName("ambience") AMBIENCE
}

/** Bounded requirements a future registry resolver may verify. */
@Serializable
enum class PerformanceCapability {
    @SerialName("pitched") PITCHED,
    @SerialName("percussive") PERCUSSIVE,
    @SerialName("sustain") SUSTAIN,
    @SerialName("polyphonic") POLYPHONIC,
    @SerialName("counter-melody") COUNTER_MELODY
}

/** Versioned, closed desired-character vocabulary.  It is never free prose. */
@Serializable
enum class SoundTrait {
    @SerialName("soft") SOFT,
    @SerialName("hard") HARD,
    @SerialName("warm") WARM,
    @SerialName("dark") DARK,
    @SerialName("bright") BRIGHT,
    @SerialName("muted") MUTED,
    @SerialName("sustained") SUSTAINED,
    @SerialName("short") SHORT,
    @SerialName("legato") LEGATO,
    @SerialName("staccato") STACCATO,
    @SerialName("brushed") BRUSHED,
    @SerialName("airy") AIRY
}

/** Typed project context supplied to planners; no filename or style prompt is allowed here. */
@Serializable
data class ArrangementSoundContext(
    val profile: CompositionProfileRef,
    val mood: MoodRef,
    val keyId: String,
    val meterNumerator: Int,
    val meterDenominator: Int,
    val resolvedProfileSha256: String
) {
    fun requireValid() {
        profile.requireValid(); mood.requireValid()
        require(KEY_ID.matches(keyId)) { "Arrangement key ID is invalid" }
        require(meterNumerator in 1..12 && meterDenominator in setOf(1, 2, 4, 8, 16)) { "Arrangement meter is invalid" }
        require(SHA_256.matches(resolvedProfileSha256)) { "Resolved profile hash is invalid" }
    }

    companion object {
        val KEY_ID = Regex("[A-G](?:#|b)?-[a-z][a-z0-9-]{0,63}")
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

/**
 * Controlled instrument-selection request.  It describes desired sound only;
 * Task 022B is responsible for resolving it to an engine-backed stable ID.
 */
@Serializable
data class InstrumentIntent(
    val vocabularyVersion: Int = CURRENT_VOCABULARY_VERSION,
    val role: ArrangementRole,
    val profile: CompositionProfileRef,
    val mood: MoodRef,
    val sectionPurpose: SongSectionPurpose? = null,
    val attackTraits: Set<SoundTrait> = emptySet(),
    val toneTraits: Set<SoundTrait> = emptySet(),
    val articulationTraits: Set<SoundTrait> = emptySet(),
    val requiredCapabilities: Set<PerformanceCapability> = emptySet(),
    val licensePreference: LicensePreference = LicensePreference.PREFER_NO_ATTRIBUTION,
    val pinnedInstrumentId: String? = null,
    val userOwned: Boolean = false
) {
    fun requireValid() {
        require(vocabularyVersion == CURRENT_VOCABULARY_VERSION) { "Unsupported instrument-intent vocabulary version: $vocabularyVersion" }
        profile.requireValid(); mood.requireValid()
        require(attackTraits.all { it in ATTACK_TRAITS }) { "Instrument intent contains an invalid attack trait" }
        require(toneTraits.all { it in TONE_TRAITS }) { "Instrument intent contains an invalid tone trait" }
        require(articulationTraits.all { it in ARTICULATION_TRAITS }) { "Instrument intent contains an invalid articulation trait" }
        require(pinnedInstrumentId == null || STABLE_ID.matches(pinnedInstrumentId)) { "Pinned instrument ID is invalid" }
        require(!userOwned || pinnedInstrumentId != null) { "User-owned instrument intent requires a pinned stable ID" }
    }

    companion object {
        const val CURRENT_VOCABULARY_VERSION = 1
        private val STABLE_ID = Regex("[a-z][a-z0-9-]{0,47}")
        private val ATTACK_TRAITS = setOf(SoundTrait.SOFT, SoundTrait.HARD, SoundTrait.BRUSHED)
        private val TONE_TRAITS = setOf(SoundTrait.WARM, SoundTrait.DARK, SoundTrait.BRIGHT, SoundTrait.MUTED, SoundTrait.AIRY)
        private val ARTICULATION_TRAITS = setOf(SoundTrait.SUSTAINED, SoundTrait.SHORT, SoundTrait.LEGATO, SoundTrait.STACCATO)
    }
}

/** UI/application input before profile context is bound by the project authority. */
data class ArrangementRoleSelection(
    val role: ArrangementRole,
    val attackTraits: Set<SoundTrait> = emptySet(),
    val toneTraits: Set<SoundTrait> = emptySet(),
    val articulationTraits: Set<SoundTrait> = emptySet(),
    val requiredCapabilities: Set<PerformanceCapability> = emptySet(),
    val pinnedInstrumentId: String? = null,
    val userOwned: Boolean = false
) {
    fun bind(context: ArrangementSoundContext, purpose: SongSectionPurpose? = null): InstrumentIntent = InstrumentIntent(
        role = role, profile = context.profile, mood = context.mood, sectionPurpose = purpose,
        attackTraits = attackTraits, toneTraits = toneTraits, articulationTraits = articulationTraits,
        requiredCapabilities = requiredCapabilities, pinnedInstrumentId = pinnedInstrumentId, userOwned = userOwned
    ).also(InstrumentIntent::requireValid)
}

/** Read-only aliases for v1 logical plans and stems. Remove after the 022B registry cutover. */
object LegacyLogicalInstrumentRoles {
    private val roleByLogical = mapOf(
        "piano" to ArrangementRole.MELODY,
        "bass" to ArrangementRole.BASS,
        "drums" to ArrangementRole.DRUMS,
        "pad" to ArrangementRole.TEXTURE,
        "strings" to ArrangementRole.COUNTER_MELODY
    )
    private val logicalByRole = roleByLogical.entries.associate { (logical, role) -> role to logical }

    fun roleFor(logicalInstrument: String): ArrangementRole = roleByLogical[logicalInstrument]
        ?: throw IllegalArgumentException("Unsupported legacy logical instrument: $logicalInstrument")

    /** Compatibility adapter only; registry resolution must replace this in Task 022B. */
    fun logicalFor(role: ArrangementRole): String = logicalByRole[role]
        ?: when (role) {
            ArrangementRole.HARMONY -> "piano"
            ArrangementRole.AMBIENCE -> "pad"
            else -> error("No legacy logical instrument alias for $role")
        }
}

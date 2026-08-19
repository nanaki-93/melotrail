package app.melotrail.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val CATALOG_ID = Regex("[a-z][a-z0-9-]{0,47}")
private val SAFE_CATALOG_TEXT = Regex("(?i).*(\\.sfz|\\.wav|samples?[/\\\\]).*")

@Serializable
data class CompositionProfileRef(val id: String, val version: Int) {
    fun requireValid() {
        require(CATALOG_ID.matches(id) && version > 0) { "Composition profile reference is invalid" }
    }
}

@Serializable
data class MoodRef(val id: String, val version: Int) {
    fun requireValid() {
        require(CATALOG_ID.matches(id) && version > 0) { "Mood reference is invalid" }
    }
}

/** Roles describe musical jobs; they deliberately do not select a local instrument or engine. */
@Serializable
enum class MusicalRole {
    @SerialName("harmonic-bed") HARMONIC_BED,
    @SerialName("low-end") LOW_END,
    @SerialName("rhythm") RHYTHM,
    @SerialName("atmosphere") ATMOSPHERE,
    @SerialName("melody-support") MELODY_SUPPORT
}

/** Bounded vocabulary consumed by a later instrument resolver, never by a renderer. */
@Serializable
enum class InstrumentCharacteristic {
    @SerialName("warm") WARM,
    @SerialName("muted") MUTED,
    @SerialName("acoustic") ACOUSTIC,
    @SerialName("soft-attack") SOFT_ATTACK,
    @SerialName("rounded-low-end") ROUNDED_LOW_END,
    @SerialName("brushed-rhythm") BRUSHED_RHYTHM,
    @SerialName("airy") AIRY,
    @SerialName("sustained") SUSTAINED,
    @SerialName("dark") DARK
}

@Serializable
enum class ProfileCapability {
    @SerialName("midi-feel") MIDI_FEEL,
    @SerialName("style-processing") STYLE_PROCESSING
}

@Serializable
enum class LicensePreference {
    @SerialName("prefer-no-attribution") PREFER_NO_ATTRIBUTION
}

@Serializable
data class MeterDefinition(val numerator: Int, val denominator: Int) {
    fun requireValid() {
        require(numerator in 1..12 && denominator in setOf(1, 2, 4, 8, 16)) { "Supported meter is invalid" }
    }
}

@Serializable
data class IntParameterBounds(val minimum: Int, val maximum: Int, val default: Int) {
    fun requireValid(label: String) {
        require(minimum <= maximum && default in minimum..maximum) { "$label bounds are invalid" }
    }

    fun clamp(value: Int): Int = value.coerceIn(minimum, maximum)
}

@Serializable
data class GrooveHumanizationBounds(
    val tempoBpm: IntParameterBounds,
    val swingPercent: IntParameterBounds,
    val humanizationMs: IntParameterBounds
) {
    fun requireValid() {
        tempoBpm.requireValid("Tempo")
        swingPercent.requireValid("Swing")
        humanizationMs.requireValid("Humanization")
        require(tempoBpm.minimum in 30..240 && tempoBpm.maximum in 30..240) { "Tempo must be 30..240 BPM" }
        require(swingPercent.minimum in 50..75 && swingPercent.maximum in 50..75) { "Swing must be 50..75 percent" }
        require(humanizationMs.minimum in 0..80 && humanizationMs.maximum in 0..80) { "Humanization must be 0..80 ms" }
    }
}

@Serializable
data class CorrectionEnhancementTolerance(
    val timingShiftMs: IntParameterBounds,
    val velocityChange: IntParameterBounds,
    val enhancementAmountPercent: IntParameterBounds
) {
    fun requireValid() {
        timingShiftMs.requireValid("Correction timing")
        velocityChange.requireValid("Correction velocity")
        enhancementAmountPercent.requireValid("Enhancement")
        require(timingShiftMs.minimum >= 0 && timingShiftMs.maximum <= 80) { "Correction timing must be 0..80 ms" }
        require(velocityChange.minimum >= 0 && velocityChange.maximum <= 127) { "Correction velocity must be 0..127" }
        require(enhancementAmountPercent.minimum >= 0 && enhancementAmountPercent.maximum <= 100) { "Enhancement amount must be 0..100" }
    }
}

@Serializable
data class WeightedCharacteristic(val characteristic: InstrumentCharacteristic, val weight: Double) {
    fun requireValid(allowNegative: Boolean = false) {
        require(weight.isFinite() && if (allowNegative) weight in -1.0..1.0 else weight in 0.0..1.0) {
            "Instrument characteristic weight is invalid"
        }
    }
}

@Serializable
data class RoleInstrumentCriteria(
    val role: MusicalRole,
    val desiredCharacteristics: List<WeightedCharacteristic>
) {
    fun requireValid() {
        require(desiredCharacteristics.isNotEmpty()) { "Instrument role criteria must contain desired characteristics" }
        require(desiredCharacteristics.map(WeightedCharacteristic::characteristic).distinct().size == desiredCharacteristics.size) {
            "Instrument role criteria contains duplicate characteristics"
        }
        desiredCharacteristics.forEach { it.requireValid() }
    }
}

@Serializable
data class StyleProcessingPolicy(
    val capability: ProfileCapability,
    val permittedEffects: List<String>
) {
    fun requireValid() {
        require(capability == ProfileCapability.STYLE_PROCESSING) { "Style processing policy requires style-processing capability" }
        require(permittedEffects.isNotEmpty() && permittedEffects.distinct().size == permittedEffects.size) { "Style processing effects are invalid" }
        require(permittedEffects.all { CATALOG_ID.matches(it) }) { "Style processing effect IDs are invalid" }
    }
}

@Serializable
data class CompositionProfile(
    val ref: CompositionProfileRef,
    val label: String,
    val description: String,
    val defaultMood: MoodRef,
    val capabilities: List<ProfileCapability>,
    val supportedMeters: List<MeterDefinition>,
    val supportedMoods: List<MoodRef>,
    val roles: List<RoleInstrumentCriteria>,
    val groove: GrooveHumanizationBounds,
    val tolerance: CorrectionEnhancementTolerance,
    val cohesionVocabulary: List<String>,
    val styleProcessing: StyleProcessingPolicy? = null,
    val licensePreference: LicensePreference = LicensePreference.PREFER_NO_ATTRIBUTION
) {
    fun requireValid() {
        ref.requireValid(); defaultMood.requireValid()
        requireSafeText(label, "Profile label"); requireSafeText(description, "Profile description")
        require(capabilities.isNotEmpty() && capabilities.distinct().size == capabilities.size) { "Profile capabilities are invalid" }
        require(supportedMeters.isNotEmpty() && supportedMeters.distinct().size == supportedMeters.size) { "Supported meters are invalid" }
        supportedMeters.forEach(MeterDefinition::requireValid)
        require(supportedMoods.isNotEmpty() && supportedMoods.distinct().size == supportedMoods.size) { "Supported moods are invalid" }
        supportedMoods.forEach(MoodRef::requireValid)
        require(defaultMood in supportedMoods) { "Profile default mood must be supported" }
        require(roles.isNotEmpty() && roles.map(RoleInstrumentCriteria::role).distinct().size == roles.size) { "Profile roles are invalid" }
        roles.forEach(RoleInstrumentCriteria::requireValid)
        groove.requireValid(); tolerance.requireValid()
        require(cohesionVocabulary.isNotEmpty() && cohesionVocabulary.size <= 16 && cohesionVocabulary.distinct().size == cohesionVocabulary.size) {
            "Cohesion vocabulary is invalid"
        }
        cohesionVocabulary.forEach { require(CATALOG_ID.matches(it)) { "Cohesion vocabulary contains an invalid term" } }
        styleProcessing?.requireValid()
        styleProcessing?.let { require(it.capability in capabilities) { "Style processing policy claims an undeclared capability" } }
    }
}

@Serializable
data class MoodModifier(
    val tempoBpmDelta: Int? = null,
    val swingPercentDelta: Int? = null,
    val humanizationMsDelta: Int? = null,
    val timingToleranceMsDelta: Int? = null,
    val enhancementAmountPercentDelta: Int? = null,
    val affinityAdjustments: List<RoleAffinityAdjustment> = emptyList()
) {
    fun requireValid() {
        require(tempoBpmDelta == null || tempoBpmDelta in -30..30) { "Mood tempo modifier is invalid" }
        require(swingPercentDelta == null || swingPercentDelta in -15..15) { "Mood swing modifier is invalid" }
        require(humanizationMsDelta == null || humanizationMsDelta in -40..40) { "Mood humanization modifier is invalid" }
        require(timingToleranceMsDelta == null || timingToleranceMsDelta in -40..40) { "Mood correction modifier is invalid" }
        require(enhancementAmountPercentDelta == null || enhancementAmountPercentDelta in -50..50) { "Mood enhancement modifier is invalid" }
        require(affinityAdjustments.map { it.role to it.characteristic }.distinct().size == affinityAdjustments.size) {
            "Mood affinity adjustments contain duplicates"
        }
        affinityAdjustments.forEach(RoleAffinityAdjustment::requireValid)
    }
}

@Serializable
data class RoleAffinityAdjustment(val role: MusicalRole, val characteristic: InstrumentCharacteristic, val delta: Double) {
    fun requireValid() = require(delta.isFinite() && delta in -1.0..1.0) { "Mood affinity adjustment is invalid" }
}

@Serializable
data class MoodDefinition(
    val ref: MoodRef,
    val profile: CompositionProfileRef,
    val label: String,
    val description: String,
    val modifier: MoodModifier = MoodModifier()
) {
    fun requireValid() {
        ref.requireValid(); profile.requireValid()
        requireSafeText(label, "Mood label"); requireSafeText(description, "Mood description")
        modifier.requireValid()
    }
}

@Serializable
data class CompositionProfileCatalogResource(
    val version: Int,
    val profiles: List<CompositionProfile>,
    val moods: List<MoodDefinition>
)

private fun requireSafeText(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 240 && !SAFE_CATALOG_TEXT.matches(value) && !value.contains('/') && !value.contains('\\')) {
        "$label is invalid"
    }
}

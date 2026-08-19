package app.melotrail.profile

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

data class CompositionProfileSummary(
    val ref: CompositionProfileRef,
    val label: String,
    val description: String,
    val defaultMood: MoodRef
)

data class MoodSummary(val ref: MoodRef, val label: String, val description: String)

@Serializable
data class ResolvedRoleAffinity(val role: MusicalRole, val desiredCharacteristics: List<WeightedCharacteristic>)

@Serializable
data class ResolvedCompositionProfile(
    val profile: CompositionProfileRef,
    val mood: MoodRef,
    val supportedMeters: List<MeterDefinition>,
    val tempoBpm: Int,
    val swingPercent: Int,
    val humanizationMs: Int,
    val timingToleranceMs: Int,
    val velocityTolerance: Int,
    val enhancementAmountPercent: Int,
    val instrumentAffinity: List<ResolvedRoleAffinity>,
    val cohesionVocabulary: List<String>,
    val styleProcessing: StyleProcessingPolicy?,
    val licensePreference: LicensePreference,
    val resolvedHash: String
)

/** Application-owned boundary: a UI or a test can substitute this catalog without filesystem access. */
interface CompositionProfileCatalog {
    fun profiles(): List<CompositionProfileSummary>
    fun moods(profile: CompositionProfileRef): List<MoodSummary>
    fun resolve(profile: CompositionProfileRef, mood: MoodRef? = null): ResolvedCompositionProfile
}

/** Validates versioned catalog data once and resolves its bounded parameters deterministically. */
class ValidatedCompositionProfileCatalog(
    resource: CompositionProfileCatalogResource,
    availableCapabilities: Set<ProfileCapability> = ProfileCapability.entries.toSet()
) : CompositionProfileCatalog {
    private val profiles: Map<CompositionProfileRef, CompositionProfile>
    private val moods: Map<MoodRef, MoodDefinition>

    init {
        require(resource.version == RESOURCE_VERSION) { "Unsupported composition profile catalog version: ${resource.version}" }
        require(resource.profiles.isNotEmpty()) { "Composition profile catalog is empty" }
        resource.profiles.forEach(CompositionProfile::requireValid)
        resource.moods.forEach(MoodDefinition::requireValid)
        require(resource.profiles.map(CompositionProfile::ref).distinct().size == resource.profiles.size) { "Composition profile catalog contains duplicate IDs or versions" }
        require(resource.moods.map(MoodDefinition::ref).distinct().size == resource.moods.size) { "Mood catalog contains duplicate IDs or versions" }
        profiles = resource.profiles.associateBy(CompositionProfile::ref)
        moods = resource.moods.associateBy(MoodDefinition::ref)
        resource.profiles.forEach { profile ->
            require(profile.capabilities.all { it in availableCapabilities }) {
                "Profile '${profile.ref.id}' claims unavailable capabilities"
            }
            profile.supportedMoods.forEach { mood ->
                val definition = moods[mood] ?: throw IllegalArgumentException("Profile '${profile.ref.id}' references unsupported mood '${mood.id}'")
                require(definition.profile == profile.ref) { "Mood '${mood.id}' belongs to another profile" }
            }
        }
        resource.moods.forEach { mood ->
            require(mood.profile in profiles) { "Mood '${mood.ref.id}' references an unknown profile" }
            mood.modifier.affinityAdjustments.forEach { adjustment ->
                require(profiles.getValue(mood.profile).roles.any { it.role == adjustment.role }) {
                    "Mood '${mood.ref.id}' adjusts an unsupported role '${adjustment.role}'"
                }
            }
        }
    }

    override fun profiles(): List<CompositionProfileSummary> = profiles.values
        .sortedWith(compareBy<CompositionProfile> { it.ref.id }.thenBy { it.ref.version })
        .map { CompositionProfileSummary(it.ref, it.label, it.description, it.defaultMood) }

    override fun moods(profile: CompositionProfileRef): List<MoodSummary> = profileFor(profile).supportedMoods
        .map { moods.getValue(it) }
        .sortedWith(compareBy<MoodDefinition> { it.ref.id }.thenBy { it.ref.version })
        .map { MoodSummary(it.ref, it.label, it.description) }

    override fun resolve(profile: CompositionProfileRef, mood: MoodRef?): ResolvedCompositionProfile {
        val definition = profileFor(profile)
        val moodDefinition = moods[mood ?: definition.defaultMood]
            ?: throw IllegalArgumentException("Unknown mood '${mood?.id}' version ${mood?.version}")
        require(moodDefinition.profile == profile && moodDefinition.ref in definition.supportedMoods) {
            "Mood '${moodDefinition.ref.id}' is not supported by profile '${profile.id}' version ${profile.version}"
        }
        val modifier = moodDefinition.modifier
        val affinity = definition.roles.sortedBy { it.role.name }.map { role ->
            val changes = modifier.affinityAdjustments.filter { it.role == role.role }.associateBy { it.characteristic }
            val base = role.desiredCharacteristics.associate { it.characteristic to it.weight }.toMutableMap()
            changes.forEach { (characteristic, adjustment) ->
                base[characteristic] = ((base[characteristic] ?: 0.0) + adjustment.delta).coerceIn(0.0, 1.0)
            }
            ResolvedRoleAffinity(role.role, base.entries.sortedBy { it.key.name }.map { WeightedCharacteristic(it.key, it.value) })
        }
        val unresolved = ResolvedCompositionProfile(
            profile = definition.ref,
            mood = moodDefinition.ref,
            supportedMeters = definition.supportedMeters.sortedWith(compareBy<MeterDefinition> { it.numerator }.thenBy { it.denominator }),
            tempoBpm = definition.groove.tempoBpm.clamp(definition.groove.tempoBpm.default + (modifier.tempoBpmDelta ?: 0)),
            swingPercent = definition.groove.swingPercent.clamp(definition.groove.swingPercent.default + (modifier.swingPercentDelta ?: 0)),
            humanizationMs = definition.groove.humanizationMs.clamp(definition.groove.humanizationMs.default + (modifier.humanizationMsDelta ?: 0)),
            timingToleranceMs = definition.tolerance.timingShiftMs.clamp(definition.tolerance.timingShiftMs.default + (modifier.timingToleranceMsDelta ?: 0)),
            velocityTolerance = definition.tolerance.velocityChange.default,
            enhancementAmountPercent = definition.tolerance.enhancementAmountPercent.clamp(definition.tolerance.enhancementAmountPercent.default + (modifier.enhancementAmountPercentDelta ?: 0)),
            instrumentAffinity = affinity,
            cohesionVocabulary = definition.cohesionVocabulary.sorted(),
            styleProcessing = definition.styleProcessing,
            licensePreference = definition.licensePreference,
            resolvedHash = ""
        )
        return unresolved.copy(resolvedHash = sha256(json.encodeToString(unresolved)))
    }

    private fun profileFor(ref: CompositionProfileRef): CompositionProfile = profiles[ref]
        ?: throw IllegalArgumentException("Unknown composition profile '${ref.id}' version ${ref.version}")

    private companion object {
        const val RESOURCE_VERSION = 1
        val json = Json { encodeDefaults = true }
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

object BundledCompositionProfileCatalog {
    private const val RESOURCE = "profiles/lofi-v1.json"
    private val json = Json { ignoreUnknownKeys = false }

    fun load(availableCapabilities: Set<ProfileCapability> = ProfileCapability.entries.toSet()): CompositionProfileCatalog {
        val contents = checkNotNull(BundledCompositionProfileCatalog::class.java.classLoader.getResourceAsStream(RESOURCE)) {
            "Bundled composition profile catalog is missing: $RESOURCE"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return ValidatedCompositionProfileCatalog(json.decodeFromString(contents), availableCapabilities)
    }
}

/** Ranking helper for a future resolver. It sees only already-admitted, hard-fit candidates. */
data class EligibleInstrumentCandidate(
    val id: String,
    val commerciallyAdmitted: Boolean,
    val hardMusicalCapabilityFit: Boolean,
    val attributionRequired: Boolean,
    val explicitlySelectedByUser: Boolean = false
) {
    init { require(Regex("[a-z][a-z0-9-]{0,47}").matches(id)) { "Instrument candidate ID is invalid" } }
}

fun rankEligibleCandidates(
    preference: LicensePreference,
    candidates: List<EligibleInstrumentCandidate>
): List<EligibleInstrumentCandidate> {
    require(candidates.map(EligibleInstrumentCandidate::id).distinct().size == candidates.size) { "Instrument candidates must be unique" }
    val eligible = candidates.filter { it.commerciallyAdmitted && it.hardMusicalCapabilityFit }
    return eligible.sortedWith(
        compareByDescending<EligibleInstrumentCandidate> { it.explicitlySelectedByUser }
            .thenBy { if (preference == LicensePreference.PREFER_NO_ATTRIBUTION) it.attributionRequired else false }
            .thenBy(EligibleInstrumentCandidate::id)
    )
}

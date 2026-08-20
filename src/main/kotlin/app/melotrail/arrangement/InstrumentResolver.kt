package app.melotrail.arrangement

import app.melotrail.profile.LicensePreference
import java.time.Instant

/** A path-free, replayable request to the local catalog. */
data class ResolveInstrumentRequest(
    val intent: InstrumentIntent,
    val actor: String = "system",
    val seed: Long? = null,
    val timestamp: Instant = Instant.EPOCH
) {
    init {
        intent.requireValid()
        require(actor.matches(Regex("[a-z][a-z0-9-]{0,47}"))) { "Instrument decision actor is invalid" }
    }
}

data class InstrumentCandidateDecision(
    val id: String,
    val score: Int?,
    val reasons: List<String>,
    val rejection: String? = null
)

/** Evidence persisted with an arrangement; it contains no library paths or engine filenames. */
data class InstrumentSelectionDecision(
    val normalizedRequest: InstrumentIntent,
    val registryVersion: Int,
    val registrySha256: String,
    val resolverVersion: Int,
    val candidates: List<InstrumentCandidateDecision>,
    val selectedId: String?,
    val actor: String,
    val timestamp: Instant,
    val seed: Long?
) {
    init {
        require(candidates == candidates.sortedBy(InstrumentCandidateDecision::id)) { "Instrument decision candidates must be stable-ID ordered" }
        require(selectedId == null || candidates.any { it.id == selectedId && it.rejection == null }) { "Instrument decision selection is not an eligible candidate" }
    }
}

fun interface ResolveInstrument {
    fun invoke(request: ResolveInstrumentRequest): InstrumentSelectionDecision
}

/**
 * Pure deterministic resolver. All filesystem work has already happened in
 * [InstrumentRegistryLoader]; this type only sees validated descriptors.
 */
class VersionedInstrumentResolver(private val registry: ValidatedInstrumentRegistry) : ResolveInstrument {
    override fun invoke(request: ResolveInstrumentRequest): InstrumentSelectionDecision {
        val intent = request.intent
        val candidates = registry.all().map { descriptor -> evaluate(descriptor, intent) }.sortedBy(InstrumentCandidateDecision::id)
        val eligible = candidates.filter { it.rejection == null }
        val selected = intent.pinnedInstrumentId?.let { pinned ->
            eligible.singleOrNull { it.id == pinned }
        } ?: choose(eligible, intent.licensePreference, request.seed)
        return InstrumentSelectionDecision(
            normalizedRequest = intent, registryVersion = registry.version, registrySha256 = registry.registrySha256,
            resolverVersion = VERSION, candidates = candidates, selectedId = selected?.id,
            actor = request.actor, timestamp = request.timestamp, seed = request.seed
        )
    }

    private fun evaluate(descriptor: ValidatedInstrumentDescriptor, intent: InstrumentIntent): InstrumentCandidateDecision {
        val rejection = when {
            descriptor.licenseAdmission.admission != LicenseAdmission.ADMITTED -> descriptor.licenseAdmission.reasons.joinToString("; ")
            intent.role !in descriptor.roles -> "does not support role '${intent.role.name.lowercase()}'"
            descriptor.selectionMode == InstrumentSelectionMode.MANUAL_ONLY && intent.pinnedInstrumentId != descriptor.id -> "manual-only catalog entry"
            !intent.requiredCapabilities.all { it in descriptor.verifiedCapabilities.performance } -> "missing verified required capability"
            else -> null
        }
        if (rejection != null) return InstrumentCandidateDecision(descriptor.id, null, emptyList(), rejection)
        val reasons = mutableListOf<String>()
        var score = 0
        fun affinity(label: String, value: Double?) {
            if (value != null) {
                val points = (value * 100).toInt()
                score += points
                reasons += "$label affinity ${if (points >= 0) "+" else ""}$points"
            }
        }
        affinity("profile", descriptor.profileAffinities[intent.profile.id])
        affinity("mood", descriptor.moodAffinities[intent.mood.id])
        intent.sectionPurpose?.let { affinity("section", descriptor.sectionAffinities[it.name.lowercase().replace('_', '-')]) }
        score += traitFit("attack", intent.attackTraits, descriptor.attackTraits, reasons)
        score += traitFit("tone", intent.toneTraits, descriptor.toneTraits, reasons)
        score += traitFit("articulation", intent.articulationTraits, descriptor.articulationTraits, reasons)
        if (intent.requiredCapabilities.isNotEmpty()) reasons += "verified capabilities satisfied"
        if (intent.pinnedInstrumentId == descriptor.id) reasons += "compatible user pin"
        return InstrumentCandidateDecision(descriptor.id, score, reasons)
    }

    private fun traitFit(label: String, requested: Set<SoundTrait>, available: Set<SoundTrait>, reasons: MutableList<String>): Int {
        val matched = requested.intersect(available)
        if (matched.isEmpty()) return 0
        val points = matched.size * TRAIT_POINTS
        reasons += "$label traits ${matched.map { it.name.lowercase() }.sorted().joinToString()} +$points"
        return points
    }

    private fun choose(eligible: List<InstrumentCandidateDecision>, preference: LicensePreference, seed: Long?): InstrumentCandidateDecision? {
        if (eligible.isEmpty()) return null
        val bestScore = eligible.maxOf { requireNotNull(it.score) }
        // Musical score is considered before attribution. A CC0 entry cannot displace a better musical fit.
        val musicalBest = eligible.filter { it.score == bestScore }
        val attributionBest = if (preference == LicensePreference.PREFER_NO_ATTRIBUTION) {
            val noAttribution = musicalBest.filter { candidate -> registry.resolve(candidate.id).license.attributionRequired.not() }
            noAttribution.ifEmpty { musicalBest }
        } else musicalBest
        if (seed == null || attributionBest.size == 1) return attributionBest.minBy { it.id }
        return attributionBest.sortedBy { it.id }[(seed and Long.MAX_VALUE).rem(attributionBest.size.toLong()).toInt()]
    }

    companion object {
        const val VERSION = 1
        private const val TRAIT_POINTS = 10
    }
}

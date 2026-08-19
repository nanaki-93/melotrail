package app.melotrail.arrangement

import kotlinx.serialization.Serializable

/** A resolved entry in the arranger timeline.  Canonical callers supply [instanceId]. */
@Serializable
data class SectionInstance(
    val index: Int,
    val partId: String,
    /** Persisted structure occurrence ID; planning rejects the blank parser-only form. */
    val instanceId: String = "",
    val label: String = instanceId,
    val variationOverrides: StructureVariationOverrides = StructureVariationOverrides()
)

fun StructureOccurrence.toSectionInstance(index: Int): SectionInstance =
    SectionInstance(index, partId, id, label, variationOverrides)

/**
 * Converts a small whitespace-separated structure string into an explicit
 * timeline. The only shorthand supported is `partId*count`. This legacy parser
 * intentionally has no occurrence-ID allocation; canonical Structure owns it.
 */
object StructureParser {
    fun parse(input: String, validPartIds: Collection<String>): List<SectionInstance> {
        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(tokens.isNotEmpty()) { "Structure must not be empty" }

        val knownPartIds = validPartIds.toSet()
        val sections = mutableListOf<SectionInstance>()
        tokens.forEach { token ->
            val (partId, count) = parseToken(token)
            require(partId in knownPartIds) { "Unknown part ID in structure: $partId" }

            repeat(count) {
                sections += SectionInstance(index = sections.size, partId = partId)
            }
        }
        return sections
    }

    fun parse(input: String, project: Project): List<SectionInstance> =
        parse(input, project.parts.map { it.id })

    private fun parseToken(token: String): Pair<String, Int> {
        if ('*' !in token) {
            return token to 1
        }

        val match = REPEATED_TOKEN.matchEntire(token)
            ?: throw IllegalArgumentException("Invalid repeated structure token: $token")
        val partId = match.groupValues[1]
        val count = match.groupValues[2].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid repetition count in structure token: $token")
        require(count > 0) { "Repetition count must be positive: $token" }
        return partId to count
    }

    private val REPEATED_TOKEN = Regex("([^*\\s]+)\\*(\\d+)")
}

package app.melotrail.arrangement

import kotlinx.serialization.Serializable

/**
 * Stable, extensible section identifier.  Only catalog entries acquire a
 * product label/profile; an unknown, normalized ID is retained as evidence
 * instead of being guessed into a built-in section.
 */
@Serializable
@JvmInline
value class SectionTypeId(val value: String) {
    init { require(NORMALIZED.matches(value)) { "Section type ID must be normalized lowercase kebab-case: $value" } }

    override fun toString(): String = value

    companion object {
        private val NORMALIZED = Regex("[a-z0-9]+(?:-[a-z0-9]+){0,63}")
        val INTRO = SectionTypeId("intro")
        val VERSE = SectionTypeId("verse")
        val CHORUS = SectionTypeId("chorus")
        val BRIDGE = SectionTypeId("bridge")
        val OUTRO = SectionTypeId("outro")
    }
}

data class SectionTypeProfile(val label: String, val supportsHarmonyContext: Boolean)

object SectionTypeCatalog {
    private val builtIns = mapOf(
        SectionTypeId.INTRO to SectionTypeProfile("Intro", true),
        SectionTypeId.VERSE to SectionTypeProfile("Verse", true),
        SectionTypeId.CHORUS to SectionTypeProfile("Chorus", true),
        SectionTypeId.BRIDGE to SectionTypeProfile("Bridge", true),
        SectionTypeId.OUTRO to SectionTypeProfile("Outro", true)
    )

    fun profile(id: SectionTypeId): SectionTypeProfile? = builtIns[id]
    fun label(id: SectionTypeId): String = profile(id)?.label ?: id.value
    fun isSupported(id: SectionTypeId): Boolean = id in builtIns

    /** Maps only known historical synonyms; all other input stays inspectable. */
    fun fromLegacyRole(role: String): SectionTypeId {
        val normalized = normalize(role).ifBlank { return SectionTypeId.VERSE }
        return when (normalized) {
            "intro", "introduction", "opening" -> SectionTypeId.INTRO
            "verse", "stanza" -> SectionTypeId.VERSE
            "chorus", "hook", "refrain" -> SectionTypeId.CHORUS
            "bridge", "middle-eight", "middle-8" -> SectionTypeId.BRIDGE
            "outro", "ending", "end" -> SectionTypeId.OUTRO
            else -> SectionTypeId(normalized)
        }
    }

    fun normalize(value: String): String = value.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(64)
}

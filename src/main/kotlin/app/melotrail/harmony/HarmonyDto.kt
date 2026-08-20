package app.melotrail.harmony

import app.melotrail.music.PitchClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Explicit persistence DTOs: only structured fields, never a display symbol. */
@Serializable
data class HarmonySettingsDto(
    val revision: Int = 1,
    val progressions: List<ChordProgressionDto> = emptyList()
) {
    fun toDomain(): HarmonySettings = HarmonySettings(revision, progressions.map(ChordProgressionDto::toDomain))

    companion object {
        fun fromDomain(value: HarmonySettings): HarmonySettingsDto = HarmonySettingsDto(
            value.revision, value.progressions.map(ChordProgressionDto::fromDomain)
        )
    }
}

@Serializable
data class ChordProgressionDto(
    val sectionType: SectionTypeId,
    val events: List<ChordEventDto> = emptyList(),
    val templateId: HarmonyTemplateId? = null
) {
    fun toDomain(): ChordProgression = ChordProgression(sectionType, events.map(ChordEventDto::toDomain), templateId)

    companion object {
        fun fromDomain(value: ChordProgression): ChordProgressionDto = ChordProgressionDto(
            value.sectionType, value.events.map(ChordEventDto::fromDomain), value.templateId
        )
    }
}

@Serializable
data class ChordEventDto(
    val id: ChordEventId,
    val root: PitchClass,
    val quality: ChordQuality,
    val order: Int,
    val durationMeasures: Int? = null,
    val bass: PitchClass? = null,
    val inversion: Int? = null,
    val extension: String? = null
) {
    fun toDomain(): ChordEvent = ChordEvent(id, root, quality, order, durationMeasures, bass, inversion, extension)

    companion object {
        fun fromDomain(value: ChordEvent): ChordEventDto = ChordEventDto(
            value.id, value.root, value.quality, value.order, value.durationMeasures,
            value.bass, value.inversion, value.extension
        )
    }
}

/** JSON is a persistence adapter; its stable settings produce reproducible text for the same DTO. */
object HarmonyJson {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encode(value: HarmonySettings): String = json.encodeToString(HarmonySettingsDto.fromDomain(value))
    fun decode(text: String): HarmonySettings = json.decodeFromString<HarmonySettingsDto>(text).toDomain()
}

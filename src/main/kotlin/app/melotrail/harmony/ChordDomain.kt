package app.melotrail.harmony

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Extensible identity for a song section. Known labels are conveniences only;
 * a persisted project may use another valid ID without losing it on read.
 */
@Serializable
@JvmInline
value class SectionTypeId(val value: String) {
    init { require(ID.matches(value)) { "Section type ID is invalid: $value" } }

    companion object {
        private val ID = Regex("[a-z][a-z0-9-]{0,63}")
        val VERSE = SectionTypeId("verse")
        val CHORUS = SectionTypeId("chorus")
        val BRIDGE = SectionTypeId("bridge")
    }
}

/** Stable event identity; presentation order is intentionally a separate field. */
@Serializable
@JvmInline
value class ChordEventId(val value: String) {
    init { require(ID.matches(value)) { "Chord event ID is invalid: $value" } }

    companion object {
        private val ID = Regex("[A-Za-z0-9_-]{1,80}")
    }
}

/** The MVP qualities that local processors can eventually execute. */
@Serializable
enum class ChordQuality(val intervals: List<Int>, val symbolSuffix: String, val displayName: String) {
    @SerialName("major") MAJOR(listOf(0, 4, 7), "", "Major"),
    @SerialName("minor") MINOR(listOf(0, 3, 7), "m", "Minor"),
    @SerialName("dominant7") DOMINANT_7(listOf(0, 4, 7, 10), "7", "Dominant 7"),
    @SerialName("major7") MAJOR_7(listOf(0, 4, 7, 11), "maj7", "Major 7"),
    @SerialName("minor7") MINOR_7(listOf(0, 3, 7, 10), "m7", "Minor 7"),
    @SerialName("major9") MAJOR_9(listOf(0, 4, 7, 11, 14), "maj9", "Major 9"),
    @SerialName("minor9") MINOR_9(listOf(0, 3, 7, 10, 14), "m9", "Minor 9"),
    @SerialName("add9") ADD_9(listOf(0, 4, 7, 14), "add9", "Add 9"),
    @SerialName("sus2") SUS_2(listOf(0, 2, 7), "sus2", "Sus 2"),
    @SerialName("sus4") SUS_4(listOf(0, 5, 7), "sus4", "Sus 4");
}

/**
 * One chord occurrence. Future notation fields are durable structured data,
 * but no current processor may execute them. A null duration is one measure.
 */
@Serializable
data class ChordEvent(
    val id: ChordEventId,
    val root: PitchClass,
    val quality: ChordQuality,
    val order: Int,
    val durationMeasures: Int? = null,
    val bass: PitchClass? = null,
    val inversion: Int? = null,
    val extension: String? = null
) {
    init {
        require(order >= 0) { "Chord event order must be non-negative" }
        require(durationMeasures == null || durationMeasures > 0) { "Chord duration must be positive when supplied" }
        require(inversion == null || inversion >= 0) { "Chord inversion must be non-negative when supplied" }
        require(extension == null || EXTENSION.matches(extension)) { "Chord extension is invalid" }
    }

    val effectiveDurationMeasures: Int get() = durationMeasures ?: 1

    /** A clear execution guard for the round-tripped but not-yet-supported fields. */
    fun unsupportedExecutionFields(): Set<String> = buildSet {
        if (durationMeasures != null && durationMeasures != 1) add("durationMeasures")
        if (bass != null) add("bass")
        if (inversion != null) add("inversion")
        if (extension != null) add("extension")
    }

    fun requireExecutable() {
        require(unsupportedExecutionFields().isEmpty()) {
            "Chord event '${id.value}' has future fields unsupported by execution: ${unsupportedExecutionFields().sorted().joinToString(", ")}"
        }
    }

    private companion object {
        val EXTENSION = Regex("[A-Za-z0-9+()#b-]{1,32}")
    }
}

/** Ordered immutable operations for the chords assigned to one section type. */
@Serializable
data class ChordProgression(
    val sectionType: SectionTypeId,
    val events: List<ChordEvent> = emptyList()
) {
    init { requireWellFormed() }

    fun add(event: ChordEvent, atIndex: Int = events.size): ChordProgression {
        require(atIndex in 0..events.size) { "Chord insertion index is outside this progression" }
        require(events.none { it.id == event.id }) { "Chord event IDs must be unique" }
        return copy(events = (events.take(atIndex) + event + events.drop(atIndex)).normalized())
    }

    /** The event ID remains stable; moving it is a separate explicit operation. */
    fun edit(event: ChordEvent): ChordProgression {
        val index = events.indexOfFirst { it.id == event.id }
        require(index >= 0) { "Chord event '${event.id.value}' does not exist" }
        return copy(events = events.mapIndexed { current, existing ->
            if (current == index) event.copy(order = existing.order) else existing
        })
    }

    fun remove(eventId: ChordEventId): ChordProgression {
        require(events.any { it.id == eventId }) { "Chord event '${eventId.value}' does not exist" }
        return copy(events = events.filterNot { it.id == eventId }.normalized())
    }

    fun move(eventId: ChordEventId, toIndex: Int): ChordProgression {
        val event = events.firstOrNull { it.id == eventId }
            ?: throw IllegalArgumentException("Chord event '${eventId.value}' does not exist")
        require(toIndex in events.indices) { "Chord move index is outside this progression" }
        return copy(events = (events.filterNot { it.id == eventId }.toMutableList().also { it.add(toIndex, event) }).normalized())
    }

    fun requireExecutable() = events.forEach(ChordEvent::requireExecutable)

    fun requireWellFormed() {
        require(events.map(ChordEvent::id).distinct().size == events.size) { "Chord event IDs must be unique" }
        require(events.map(ChordEvent::order) == events.indices.toList()) { "Chord event order must be contiguous and match list order" }
    }

    private fun List<ChordEvent>.normalized(): List<ChordEvent> = mapIndexed { index, event -> event.copy(order = index) }
}

/** V4 harmony aggregate. Chromatic roots are valid even when outside [projectKey]. */
@Serializable
data class HarmonySettings(
    val revision: Int = 1,
    val progressions: List<ChordProgression> = emptyList()
) {
    /**
     * Monotonically increasing aggregate revision used by the harmony command
     * boundary. The v4 JSON shape is fixed independently of this value.
     */
    init { require(revision >= 1) { "Harmony revision must be positive" } }

    fun requireWellFormed(projectKey: MusicalKey?) {
        require(progressions.map(ChordProgression::sectionType).distinct().size == progressions.size) {
            "Harmony section types must be unique"
        }
        if (progressions.isNotEmpty()) {
            requireNotNull(projectKey) { "Structured harmony requires a project key context" }
            require(projectKey.isExecutable) { "Harmony cannot execute unknown project key mode '${projectKey.modeId.value}'" }
        }
        progressions.forEach(ChordProgression::requireWellFormed)
    }
}

data class ChordRootOption(val value: PitchClass) { val label: String get() = value.toString() }
data class ChordQualityOption(val value: ChordQuality) { val label: String get() = value.displayName }

/** Ordered adapter-ready values for Task 009; this model does not select a chord. */
object HarmonyOptionModels {
    val roots: List<ChordRootOption> = (0..11).map { ChordRootOption(PitchClass.canonical(it)) }
    val qualities: List<ChordQualityOption> = ChordQuality.entries.map(::ChordQualityOption)
}

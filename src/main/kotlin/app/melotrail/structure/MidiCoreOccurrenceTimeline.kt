package app.melotrail.structure

import app.melotrail.midi.domain.MidiBeatPosition
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.music.core.ProjectMeter
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence

/** Explicit duration supplied when arranging a section occurrence; never inferred from source MIDI. */
data class MidiCoreOccurrencePlacement(
    val id: String,
    val definitionId: String,
    val label: String,
    val durationTicks: Long,
    val startTick: Long? = null,
) {
    init { require(durationTicks > 0) { "Occurrence duration must be positive" } }
    init { require(startTick == null || startTick >= 0) { "Occurrence start tick must not be negative" } }
}

/** Musician-facing placement expressed only in whole bars; exact ticks are derived from project authority. */
data class MidiCoreBarOccurrencePlacement(
    val id: String,
    val definitionId: String,
    val label: String,
    val barCount: Int,
) {
    init { require(barCount > 0) { "Occurrence bar count must be positive" } }
}

/** Tick-exact, gap-free occurrence authority for one fixed-PPQ project. */
class MidiCoreOccurrenceTimeline private constructor(
    val ppq: MidiPpq,
    val meter: ProjectMeter,
    val pickupTicks: Long,
    val occurrences: List<ProjectSectionOccurrence>,
) {
    init {
        require(pickupTicks in 0 until barTicks()) { "Pickup length must be shorter than one bar" }
        require(occurrences.isNotEmpty() || pickupTicks == 0L) { "A pickup requires a first section occurrence" }
        require(occurrences == occurrences.sortedBy(ProjectSectionOccurrence::startTick)) { "Occurrences must be tick ordered" }
        require(occurrences.isEmpty() || occurrences.first().startTick == 0L) { "Timeline must begin at tick zero" }
        occurrences.zipWithNext().forEach { (left, right) -> require(left.endTick == right.startTick) { "Timeline contains a gap or overlap" } }
    }

    val totalTicks: Long get() = occurrences.lastOrNull()?.endTick ?: 0L

    fun markerLabels(): List<String> = markers().map(MidiExportMarker::renderedLabel)

    /** Markers are derived only from ordered occurrence identity and exact start ticks. */
    fun markers(): List<MidiExportMarker> = occurrences.mapIndexed { index, occurrence ->
        MidiExportMarker(index + 1, occurrence.label, occurrence.startTick)
    }

    fun startPosition(occurrenceId: String): MidiBeatPosition =
        MidiBeatPosition.fromTicks(requireOccurrence(occurrenceId).startTick, ppq)

    fun durationPosition(occurrenceId: String): MidiBeatPosition {
        val occurrence = requireOccurrence(occurrenceId)
        return MidiBeatPosition.fromTicks(occurrence.endTick - occurrence.startTick, ppq)
    }

    private fun requireOccurrence(id: String): ProjectSectionOccurrence =
        occurrences.singleOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown occurrence '$id'")

    private fun barTicks(): Long {
        val denominator = meter.denominator
        require((ppq.value.toLong() * 4L) % denominator == 0L) { "Project PPQ cannot represent the confirmed meter exactly" }
        return Math.multiplyExact(meter.numerator.toLong(), (ppq.value.toLong() * 4L) / denominator)
    }

    companion object {
        fun buildFromBars(
            ppq: MidiPpq,
            meter: ProjectMeter,
            definitions: List<ProjectSectionDefinition>,
            placements: List<MidiCoreBarOccurrencePlacement>,
            expectedSongEndTick: Long,
        ): MidiCoreOccurrenceTimeline {
            val barTicks = ticksPerBar(ppq, meter)
            require(expectedSongEndTick >= 0) { "Expected song end tick must not be negative" }
            require(expectedSongEndTick % barTicks == 0L) {
                "The source ends at tick $expectedSongEndTick, which is not a whole-bar boundary in ${meter.numerator}/${meter.denominator}."
            }
            val expectedBars = expectedSongEndTick / barTicks
            val actualBars = placements.sumOf { it.barCount.toLong() }
            require(actualBars == expectedBars) {
                "Structure totals $actualBars bars, but the source melody requires exactly $expectedBars bars."
            }
            val tickPlacements = placements.map { placement ->
                MidiCoreOccurrencePlacement(
                    placement.id,
                    placement.definitionId,
                    placement.label,
                    Math.multiplyExact(barTicks, placement.barCount.toLong()),
                )
            }
            return build(ppq, meter, definitions, tickPlacements, pickupTicks = 0L, expectedSongEndTick = expectedSongEndTick)
        }

        fun build(
            ppq: MidiPpq,
            meter: ProjectMeter,
            definitions: List<ProjectSectionDefinition>,
            placements: List<MidiCoreOccurrencePlacement>,
            pickupTicks: Long = 0L,
            expectedSongEndTick: Long? = null,
        ): MidiCoreOccurrenceTimeline {
            require(expectedSongEndTick == null || expectedSongEndTick >= 0) { "Expected song end tick must not be negative" }
            require(definitions.map(ProjectSectionDefinition::id).distinct().size == definitions.size) { "Section definition IDs must be unique" }
            require(placements.map(MidiCoreOccurrencePlacement::id).distinct().size == placements.size) { "Occurrence IDs must be unique" }
            val definitionIds = definitions.map(ProjectSectionDefinition::id).toSet()
            require(placements.all { it.definitionId in definitionIds }) { "Occurrence references an unknown section definition" }
            var cursor = 0L
            val occurrences = placements.map { placement ->
                val start = placement.startTick ?: cursor
                require(start == cursor) { "Occurrence placements must form one contiguous timeline" }
                val end = Math.addExact(start, placement.durationTicks)
                ProjectSectionOccurrence(placement.id, placement.definitionId, placement.label, start, end).also { cursor = end }
            }
            val timeline = MidiCoreOccurrenceTimeline(ppq, meter, pickupTicks, occurrences)
            require(expectedSongEndTick == null || timeline.totalTicks == expectedSongEndTick) {
                "Occurrence timeline must cover the intended song range through tick $expectedSongEndTick"
            }
            return timeline
        }

        fun ticksPerBar(ppq: MidiPpq, meter: ProjectMeter): Long {
            val numerator = Math.multiplyExact(ppq.value.toLong(), 4L)
            require(numerator % meter.denominator == 0L) { "Project PPQ cannot represent the confirmed meter exactly" }
            return Math.multiplyExact(meter.numerator.toLong(), numerator / meter.denominator)
        }
    }
}

/** Pure occurrence mutations; application services persist their result atomically. */
class MidiCoreStructureEditor(private val ppq: MidiPpq) {
    fun replace(authority: ProjectAuthority, definitions: List<ProjectSectionDefinition>, placements: List<MidiCoreOccurrencePlacement>, pickupTicks: Long? = null): ProjectAuthority {
        val timeline = MidiCoreOccurrenceTimeline.build(ppq, authority.meter, definitions, placements, pickupTicks ?: authority.pickupTicks)
        return authority.copy(sectionDefinitions = definitions, occurrences = timeline.occurrences, pickupTicks = timeline.pickupTicks)
    }

    fun insert(authority: ProjectAuthority, index: Int, placement: MidiCoreOccurrencePlacement): ProjectAuthority {
        val placements = placements(authority).toMutableList().also { require(index in 0..it.size) { "Insertion index is outside the occurrence timeline" }; it.add(index, placement) }
        return replace(authority, authority.sectionDefinitions, placements, authority.pickupTicks)
    }

    fun duplicate(authority: ProjectAuthority, occurrenceId: String, duplicateId: String, duplicateLabel: String): ProjectAuthority {
        val placements = placements(authority).toMutableList()
        val index = placements.indexOfFirst { it.id == occurrenceId }
        require(index >= 0) { "Unknown occurrence '$occurrenceId'" }
        val original = placements[index]
        placements.add(index + 1, original.copy(id = duplicateId, label = duplicateLabel))
        return replace(authority, authority.sectionDefinitions, placements, authority.pickupTicks)
    }

    fun move(authority: ProjectAuthority, occurrenceId: String, destinationIndex: Int): ProjectAuthority {
        val placements = placements(authority).toMutableList()
        val sourceIndex = placements.indexOfFirst { it.id == occurrenceId }
        require(sourceIndex >= 0 && destinationIndex in placements.indices) { "Move location is outside the occurrence timeline" }
        val occurrence = placements.removeAt(sourceIndex)
        placements.add(destinationIndex, occurrence)
        return replace(authority, authority.sectionDefinitions, placements, authority.pickupTicks)
    }

    fun remove(authority: ProjectAuthority, occurrenceId: String): ProjectAuthority =
        replace(authority, authority.sectionDefinitions, placements(authority).filterNot { it.id == occurrenceId }.also {
            require(it.size < authority.occurrences.size) { "Unknown occurrence '$occurrenceId'" }
        }, authority.pickupTicks)

    private fun placements(authority: ProjectAuthority): List<MidiCoreOccurrencePlacement> = authority.occurrences.map {
        MidiCoreOccurrencePlacement(it.id, it.definitionId, it.label, it.endTick - it.startTick)
    }
}

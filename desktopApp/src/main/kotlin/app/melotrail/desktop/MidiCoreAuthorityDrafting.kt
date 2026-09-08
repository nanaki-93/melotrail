package app.melotrail.desktop

import app.melotrail.midi.domain.MidiPpq
import app.melotrail.music.core.ProjectMeter
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import app.melotrail.structure.MidiCoreOccurrenceTimeline
import java.math.BigInteger

/** Musician-facing section row. Persistence identifiers stay internal to the editor. */
internal data class MidiCoreSectionDraft(
    val occurrenceId: String,
    val definitionId: String,
    val name: String,
    val definitionName: String,
    val barsText: String,
)

/** One musician-facing harmonic row. Duration is a positive rational number of quarter-note beats. */
internal data class MidiCoreChordRowDraft(
    val id: String,
    val symbol: String,
    val durationBeats: String,
)

/** One readable, duration-explicit chord table for one saved section occurrence. */
internal data class MidiCoreProgressionDraft(
    val occurrenceId: String,
    val sectionName: String,
    val rows: List<MidiCoreChordRowDraft>,
    val originalRows: List<MidiCoreChordRowDraft> = rows,
    val originalEvents: List<AuthoritativeChordEvent> = emptyList(),
)

internal data class MidiCoreParsedStructure(
    val definitions: List<ProjectSectionDefinition>,
    val placements: List<MidiCoreBarOccurrencePlacement>,
    val occurrences: List<ProjectSectionOccurrence>,
)

/** Pure conversions between simple musical inputs and the exact persisted MIDI authority. */
internal object MidiCoreAuthorityDrafting {
    fun sectionDrafts(authority: ProjectAuthority?, ppq: Int?): List<MidiCoreSectionDraft> {
        if (authority == null) return emptyList()
        val definitions = authority.sectionDefinitions.associateBy(ProjectSectionDefinition::id)
        return authority.occurrences.map { occurrence ->
            val definitionName = definitions[occurrence.definitionId]?.name ?: occurrence.label
            MidiCoreSectionDraft(
                occurrenceId = occurrence.id,
                definitionId = occurrence.definitionId,
                name = occurrence.label,
                definitionName = definitionName,
                barsText = occurrenceBars(occurrence, ppq, authority.meter)?.toString().orEmpty(),
            )
        }
    }

    fun progressionDrafts(authority: ProjectAuthority?, ppq: Int?): List<MidiCoreProgressionDraft> {
        if (authority == null) return emptyList()
        val eventsByOccurrence = authority.chordEvents.groupBy(AuthoritativeChordEvent::occurrenceId)
        return authority.occurrences.map { occurrence ->
            val events = eventsByOccurrence[occurrence.id].orEmpty().sortedWith(
                compareBy<AuthoritativeChordEvent>(AuthoritativeChordEvent::startTick).thenBy(AuthoritativeChordEvent::id),
            )
            val rows = events.map { event ->
                MidiCoreChordRowDraft(event.id, event.symbol, formatBeatDuration(event.durationTicks, ppq))
            }
            MidiCoreProgressionDraft(occurrence.id, occurrence.label, rows, rows, events)
        }
    }

    fun parseStructure(
        drafts: List<MidiCoreSectionDraft>,
        ppq: Int?,
        meter: ProjectMeter,
        expectedSongEndTick: Long?,
    ): MidiCoreParsedStructure = runCatching {
        require(drafts.isNotEmpty()) { "Add at least one section." }
        requireNotNull(ppq) { "Import a source MIDI before defining sections." }
        requireNotNull(expectedSongEndTick) { "Import a source MIDI before defining sections." }
        require(drafts.map(MidiCoreSectionDraft::occurrenceId).distinct().size == drafts.size) {
            "Section identities must be unique."
        }
        val definitions = drafts.distinctBy(MidiCoreSectionDraft::definitionId).map { draft ->
            ProjectSectionDefinition(draft.definitionId, draft.definitionName.trim().ifBlank { draft.name.trim() })
        }
        val placements = drafts.map { draft ->
            val bars = draft.barsText.toIntOrNull() ?: error("${draft.name.ifBlank { "Section" }} needs a whole-number bar length.")
            MidiCoreBarOccurrencePlacement(
                id = draft.occurrenceId,
                definitionId = draft.definitionId,
                label = draft.name.trim(),
                barCount = bars,
            )
        }
        val timeline = MidiCoreOccurrenceTimeline.buildFromBars(
            MidiPpq(ppq),
            meter,
            definitions,
            placements,
            expectedSongEndTick,
        )
        MidiCoreParsedStructure(definitions, placements, timeline.occurrences)
    }.getOrElse { throw IllegalArgumentException(it.message ?: "The section structure is invalid.", it) }

    fun structureError(
        drafts: List<MidiCoreSectionDraft>,
        ppq: Int?,
        meter: ProjectMeter,
        expectedSongEndTick: Long?,
    ): String? = runCatching { parseStructure(drafts, ppq, meter, expectedSongEndTick) }.exceptionOrNull()?.message

    fun parseHarmony(
        drafts: List<MidiCoreProgressionDraft>,
        authority: ProjectAuthority,
        ppq: Int?,
    ): List<AuthoritativeChordEvent> {
        requireNotNull(ppq) { "Import a source MIDI before entering chord durations." }
        require(ppq > 0) { "Source PPQ must be positive." }
        val draftsByOccurrence = drafts.associateBy(MidiCoreProgressionDraft::occurrenceId)
        require(draftsByOccurrence.size == authority.occurrences.size && authority.occurrences.all { it.id in draftsByOccurrence }) {
            "Every saved section needs one chord progression."
        }
        return authority.occurrences.flatMap { occurrence ->
            val draft = requireNotNull(draftsByOccurrence[occurrence.id])
            if (draft.rows == draft.originalRows && exactOriginalCoverage(draft.originalEvents, occurrence)) {
                draft.originalEvents
            } else {
                require(draft.rows.isNotEmpty()) { "${occurrence.label} needs at least one chord row." }
                var cursor = occurrence.startTick
                draft.rows.mapIndexed { index, row ->
                    val duration = durationTicks(row.durationBeats, ppq)
                    val end = try { Math.addExact(cursor, duration) } catch (_: ArithmeticException) {
                        throw IllegalArgumentException("${occurrence.label} chord durations overflow the project timeline.")
                    }
                    require(end <= occurrence.endTick) { "${occurrence.label} chord rows exceed the section length." }
                    val event = AuthoritativeChordEvent(
                        id = row.id.ifBlank { "${occurrence.id}-chord-${index + 1}" },
                        occurrenceId = occurrence.id,
                        symbol = row.symbol.trim(),
                        startTick = cursor,
                        endTick = end,
                    )
                    cursor = end
                    event
                }
                    .also { require(cursor == occurrence.endTick) { "${occurrence.label} chord durations must total the section length exactly." } }
            }
        }
    }

    fun harmonyError(drafts: List<MidiCoreProgressionDraft>, authority: ProjectAuthority?, ppq: Int?): String? {
        if (authority == null || authority.occurrences.isEmpty()) return "Save the section structure before entering harmony."
        return runCatching { parseHarmony(drafts, authority, ppq) }.exceptionOrNull()?.message
    }

    fun sourceBarCount(expectedSongEndTick: Long?, ppq: Int?, meter: ProjectMeter): Int? {
        if (expectedSongEndTick == null || ppq == null) return null
        val barTicks = runCatching { MidiCoreOccurrenceTimeline.ticksPerBar(MidiPpq(ppq), meter) }.getOrNull() ?: return null
        if (expectedSongEndTick % barTicks != 0L) return null
        return (expectedSongEndTick / barTicks).takeIf { it in 0..Int.MAX_VALUE }?.toInt()
    }

    fun nextSection(
        drafts: List<MidiCoreSectionDraft>,
        expectedSongEndTick: Long?,
        ppq: Int?,
        meter: ProjectMeter,
    ): MidiCoreSectionDraft {
        val usedOccurrences = drafts.map(MidiCoreSectionDraft::occurrenceId).toSet()
        val usedDefinitions = drafts.map(MidiCoreSectionDraft::definitionId).toSet()
        val ordinal = drafts.size + 1
        val name = "Section $ordinal"
        val remaining = sourceBarCount(expectedSongEndTick, ppq, meter)
            ?.minus(drafts.sumOf { it.barsText.toIntOrNull()?.coerceAtLeast(0) ?: 0 })
            ?.takeIf { it > 0 }
            ?: 1
        return MidiCoreSectionDraft(
            occurrenceId = nextSafeId("section", usedOccurrences),
            definitionId = nextSafeId("part", usedDefinitions),
            name = name,
            definitionName = name,
            barsText = remaining.toString(),
        )
    }

    fun duplicateSection(drafts: List<MidiCoreSectionDraft>, index: Int): List<MidiCoreSectionDraft> {
        if (index !in drafts.indices) return drafts
        val original = drafts[index]
        val duplicate = original.copy(
            occurrenceId = nextSafeId("section", drafts.map(MidiCoreSectionDraft::occurrenceId).toSet()),
            name = original.name,
        )
        return drafts.toMutableList().also { it.add(index + 1, duplicate) }
    }

    /**
     * Splitting keeps the musical section family while assigning the new timeline
     * occurrence its own identity. The caller still validates the complete song
     * length before this draft can become authority.
     */
    fun splitSection(drafts: List<MidiCoreSectionDraft>, index: Int): List<MidiCoreSectionDraft> {
        if (index !in drafts.indices) return drafts
        val original = drafts[index]
        val bars = original.barsText.toIntOrNull() ?: return drafts
        if (bars < 2) return drafts
        val firstBars = bars / 2
        val second = original.copy(
            occurrenceId = nextSafeId("section", drafts.map(MidiCoreSectionDraft::occurrenceId).toSet()),
            barsText = (bars - firstBars).toString(),
        )
        return drafts.toMutableList().also {
            it[index] = original.copy(barsText = firstBars.toString())
            it.add(index + 1, second)
        }
    }

    /** Converts legacy shorthand only into editable explicit rows; it is never persisted as authority. */
    fun seedRowsFromShorthand(text: String, occurrence: ProjectSectionOccurrence, ppq: Int): List<MidiCoreChordRowDraft> {
        val symbols = text.trim().split(Regex("[|,\\s]+")).map(String::trim).filter(String::isNotEmpty)
        require(symbols.isNotEmpty()) { "${occurrence.label} needs at least one chord." }
        val duration = occurrence.endTick - occurrence.startTick
        require(symbols.size.toLong() <= duration) { "${occurrence.label} has too many chord changes for its MIDI length." }
        return symbols.indices.map { index ->
            val ticks = proportionalOffset(duration, index + 1L, symbols.size.toLong()) - proportionalOffset(duration, index.toLong(), symbols.size.toLong())
            MidiCoreChordRowDraft("${occurrence.id}-chord-${index + 1}", symbols[index], formatBeatDuration(ticks, ppq))
        }
    }

    private fun durationTicks(text: String, ppq: Int): Long {
        val match = BEAT_DURATION.matchEntire(text.trim())
            ?: throw IllegalArgumentException("Chord duration '$text' must be a positive number of beats, such as 3, 1/2, or 3/2.")
        val numerator = match.groupValues[1].toBigInteger()
        val denominator = match.groupValues[2].removePrefix("/").ifBlank { "1" }.toBigInteger()
        require(numerator > BigInteger.ZERO && denominator > BigInteger.ZERO) { "Chord duration must be positive." }
        val scaled = numerator * ppq.toBigInteger()
        require(scaled % denominator == BigInteger.ZERO) {
            "Chord duration '$text' cannot be represented exactly at PPQ $ppq; choose an exactly representable beat fraction."
        }
        return try { (scaled / denominator).longValueExact() } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Chord duration '$text' is too long for this project.")
        }
    }

    private fun formatBeatDuration(ticks: Long, ppq: Int?): String {
        if (ppq == null || ppq <= 0) return "$ticks ticks"
        val divisor = ticks.toBigInteger().gcd(ppq.toBigInteger())
        val numerator = ticks.toBigInteger() / divisor
        val denominator = ppq.toBigInteger() / divisor
        return if (denominator == BigInteger.ONE) numerator.toString() else "$numerator/$denominator"
    }

    /** Calculate floor(duration * index / slots) without overflowing Long multiplication. */
    private fun proportionalOffset(duration: Long, index: Long, slots: Long): Long =
        (duration / slots) * index + ((duration % slots) * index) / slots

    private fun exactOriginalCoverage(events: List<AuthoritativeChordEvent>, occurrence: ProjectSectionOccurrence): Boolean {
        if (events.isEmpty()) return false
        val ordered = events.sortedWith(compareBy<AuthoritativeChordEvent>(AuthoritativeChordEvent::startTick).thenBy(AuthoritativeChordEvent::id))
        if (ordered.first().startTick != occurrence.startTick || ordered.last().endTick != occurrence.endTick) return false
        return ordered.zipWithNext().all { (left, right) -> left.endTick == right.startTick }
    }

    private fun occurrenceBars(occurrence: ProjectSectionOccurrence, ppq: Int?, meter: ProjectMeter): Int? {
        val resolution = ppq ?: return null
        val barTicks = runCatching { MidiCoreOccurrenceTimeline.ticksPerBar(MidiPpq(resolution), meter) }.getOrNull() ?: return null
        val duration = occurrence.endTick - occurrence.startTick
        if (duration % barTicks != 0L) return null
        return (duration / barTicks).takeIf { it in 1..Int.MAX_VALUE }?.toInt()
    }

    private fun nextSafeId(prefix: String, used: Set<String>): String {
        var index = 1
        var candidate: String
        do {
            candidate = "$prefix-$index"
            index += 1
        } while (candidate in used)
        return candidate
    }

    private val BEAT_DURATION = Regex("([0-9]+)(/[0-9]+)?")
}

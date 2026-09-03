package app.melotrail.desktop

import app.melotrail.midi.domain.MidiPpq
import app.melotrail.music.core.ProjectMeter
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import app.melotrail.structure.MidiCoreOccurrenceTimeline

/** Musician-facing section row. Persistence identifiers stay internal to the editor. */
internal data class MidiCoreSectionDraft(
    val occurrenceId: String,
    val definitionId: String,
    val name: String,
    val definitionName: String,
    val barsText: String,
)

/** One readable chord progression for one saved section occurrence. */
internal data class MidiCoreProgressionDraft(
    val occurrenceId: String,
    val sectionName: String,
    val text: String,
    val originalText: String = "",
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

    fun progressionDrafts(authority: ProjectAuthority?): List<MidiCoreProgressionDraft> {
        if (authority == null) return emptyList()
        val eventsByOccurrence = authority.chordEvents.groupBy(AuthoritativeChordEvent::occurrenceId)
        return authority.occurrences.map { occurrence ->
            val events = eventsByOccurrence[occurrence.id].orEmpty().sortedWith(
                compareBy<AuthoritativeChordEvent>(AuthoritativeChordEvent::startTick).thenBy(AuthoritativeChordEvent::id),
            )
            val text = events.joinToString(" | ", transform = AuthoritativeChordEvent::symbol)
            MidiCoreProgressionDraft(occurrence.id, occurrence.label, text, text, events)
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
    ): List<AuthoritativeChordEvent> {
        val draftsByOccurrence = drafts.associateBy(MidiCoreProgressionDraft::occurrenceId)
        require(draftsByOccurrence.size == authority.occurrences.size && authority.occurrences.all { it.id in draftsByOccurrence }) {
            "Every saved section needs one chord progression."
        }
        return authority.occurrences.flatMap { occurrence ->
            val draft = requireNotNull(draftsByOccurrence[occurrence.id])
            if (draft.text == draft.originalText && exactOriginalCoverage(draft.originalEvents, occurrence)) {
                draft.originalEvents
            } else {
                val symbols = progressionSymbols(draft.text)
                require(symbols.isNotEmpty()) { "${occurrence.label} needs at least one chord." }
                val duration = occurrence.endTick - occurrence.startTick
                require(symbols.size.toLong() <= duration) { "${occurrence.label} has too many chord changes for its MIDI length." }
                symbols.mapIndexed { index, symbol ->
                    val start = occurrence.startTick + proportionalOffset(duration, index.toLong(), symbols.size.toLong())
                    val end = occurrence.startTick + proportionalOffset(duration, index + 1L, symbols.size.toLong())
                    AuthoritativeChordEvent(
                        id = "${occurrence.id}-chord-${index + 1}",
                        occurrenceId = occurrence.id,
                        symbol = symbol,
                        startTick = start,
                        endTick = end,
                    )
                }
            }
        }
    }

    fun harmonyError(drafts: List<MidiCoreProgressionDraft>, authority: ProjectAuthority?): String? {
        if (authority == null || authority.occurrences.isEmpty()) return "Save the section structure before entering harmony."
        return runCatching { parseHarmony(drafts, authority) }.exceptionOrNull()?.message
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

    private fun progressionSymbols(text: String): List<String> = text.trim()
        .split(Regex("[|,\\s]+"))
        .map(String::trim)
        .filter(String::isNotEmpty)

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
}

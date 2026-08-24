package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.MelodyPreparationArtifactReference
import app.melotrail.arrangement.MidiMonophonicMelodyPreparer
import app.melotrail.arrangement.MonophonicMelodyPreparationReport
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SourceSongArtifact
import app.melotrail.arrangement.SourceSongAssembler
import app.melotrail.arrangement.SourceSongAssemblyRequest
import app.melotrail.arrangement.SourceSongHarmonySpan
import app.melotrail.arrangement.SourceSongMidiInput
import app.melotrail.arrangement.SourceSongSection
import app.melotrail.arrangement.sha256Hex
import app.melotrail.arrangement.toSectionInstance
import java.nio.file.Path
import javax.sound.midi.MidiSystem

/**
 * Builds the milestone-three source-song artifact from the canonical authority
 * and prepared selected source MIDI. It is intentionally read-only with respect
 * to project.json: content-addressed candidates and reports are its evidence.
 */
class SourceSongApplicationService(
    private val musicalAuthorityBuilder: MusicalAuthorityBuilder = MusicalAuthorityBuilder(),
    private val selectedMidiArtifactResolver: SelectedMidiArtifactResolver = SelectedMidiArtifactResolver(),
    private val melodyPreparer: MidiMonophonicMelodyPreparer = MidiMonophonicMelodyPreparer(),
    private val sourceSongAssembler: SourceSongAssembler = SourceSongAssembler()
) {
    /** Assemble the current structured source song, or verify its existing immutable candidate. */
    fun assemble(projectRoot: Path): SourceSongArtifact {
        val root = projectRoot.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        project.requireValid(root)
        val authority = musicalAuthorityBuilder.build(root)
        val usedPartIds = project.envelope.structureOccurrences.map { it.partId }.toSet()
        val sourceMidi = project.parts.filter { it.id in usedPartIds }.associate { part ->
            val selected = selectedMidiArtifactResolver.resolve(root, project, part)
            val input = MelodyPreparationArtifactReference(selected.projectRelativePath, selected.sha256, selected.ppq, noteOnCount(selected.path))
            val prepared = melodyPreparer.prepare(root, part.id, input)
            part.id to SourceSongMidiInput(part.id, prepared.midi.path, prepared.midi.sha256, prepared.midi.ppq, "MONOPHONIC_PREPARED", prepared.report)
        }
        val preparationContext = sha256Hex(buildString {
            append(authority.contextSha256).append('|').append(MonophonicMelodyPreparationReport.PROCESSOR_VERSION).append('|')
            sourceMidi.toSortedMap().forEach { (partId, source) ->
                append(partId).append('=').append(source.sha256).append(':').append(requireNotNull(source.preparationReport).sha256).append(';')
            }
        })
        val counts = mutableMapOf<String, Int>()
        val occurrences = authority.occurrenceTimeline.associateBy { it.occurrenceId }
        val sections = project.envelope.structureOccurrences.mapIndexed { index, occurrence ->
            val timeline = requireNotNull(occurrences[occurrence.id]) { "Missing canonical source-song occurrence '${occurrence.id}'" }
            val number = (counts[occurrence.partId] ?: 0) + 1
            counts[occurrence.partId] = number
            SourceSongSection(
                instance = occurrence.toSectionInstance(index),
                sourcePartId = occurrence.partId,
                sectionRole = timeline.sectionType,
                occurrenceNumber = number,
                startBar = timeline.startBar,
                endBar = timeline.endBar,
                startTick = timeline.startTick,
                endTick = timeline.endTick,
                sourceMidi = sourceMidi.getValue(occurrence.partId),
                canonicalHarmony = authority.harmonicTimeline.forOccurrence(occurrence.id).map { chord ->
                    SourceSongHarmonySpan(chord.occurrenceId, chord.bar, chord.startTick, chord.endTick,
                        chord.chord.rootChromatic, chord.chord.rootSymbol, chord.chord.quality)
                }
            )
        }
        return sourceSongAssembler.assemble(SourceSongAssemblyRequest(
            root = root,
            contextSha256 = preparationContext,
            canonicalPpq = authority.harmonicTimeline.ppq,
            tempoBpm = authority.tempo.bpm,
            meterNumerator = authority.meter.numerator,
            meterDenominator = authority.meter.denominator,
            sections = sections
        ))
    }

    /** Count note-ons only to bind the selected input descriptor without changing that MIDI. */
    private fun noteOnCount(path: Path): Int = MidiSystem.getSequence(path.toFile()).tracks.sumOf { track ->
        (0 until track.size()).count { index ->
            val message = track[index].message as? javax.sound.midi.ShortMessage
            message?.command == javax.sound.midi.ShortMessage.NOTE_ON && message.data2 > 0
        }
    }
}

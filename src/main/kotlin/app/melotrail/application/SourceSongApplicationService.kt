package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SourceSongArtifact
import app.melotrail.arrangement.SourceSongAssembler
import app.melotrail.arrangement.SourceSongAssemblyRequest
import app.melotrail.arrangement.SourceSongHarmonySpan
import app.melotrail.arrangement.SourceSongMidiInput
import app.melotrail.arrangement.SourceSongSection
import app.melotrail.arrangement.toSectionInstance
import java.nio.file.Path

/**
 * Builds the milestone-three source-song artifact from the canonical authority
 * and selected enhanced/approved source MIDI. It is intentionally read-only
 * with respect to project.json: the content-addressed artifact is its evidence.
 */
class SourceSongApplicationService(
    private val musicalAuthorityBuilder: MusicalAuthorityBuilder = MusicalAuthorityBuilder(),
    private val selectedMidiArtifactResolver: SelectedMidiArtifactResolver = SelectedMidiArtifactResolver(),
    private val sourceSongAssembler: SourceSongAssembler = SourceSongAssembler()
) {
    /** Assemble the current structured source song, or verify its existing immutable candidate. */
    fun assemble(projectRoot: Path): SourceSongArtifact {
        val root = projectRoot.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        project.requireValid(root)
        val authority = musicalAuthorityBuilder.build(root)
        val sourceMidi = project.parts.associate { part ->
            val selected = selectedMidiArtifactResolver.resolve(root, project, part)
            part.id to SourceSongMidiInput(part.id, selected.projectRelativePath, selected.sha256, selected.ppq, selected.kind.name)
        }
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
            contextSha256 = authority.contextSha256,
            canonicalPpq = authority.harmonicTimeline.ppq,
            tempoBpm = authority.tempo.bpm,
            meterNumerator = authority.meter.numerator,
            meterDenominator = authority.meter.denominator,
            sections = sections
        ))
    }
}

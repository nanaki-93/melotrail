package app.melotrail.application

import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.MelodyPreparationArtifactReference
import app.melotrail.arrangement.MelodyHarmonyFitContext
import app.melotrail.arrangement.MelodyHarmonyFitReport
import app.melotrail.arrangement.MelodyHarmonyFitRequest
import app.melotrail.arrangement.MelodyHarmonyFitSpan
import app.melotrail.arrangement.MidiHarmonyFitter
import app.melotrail.arrangement.MidiMonophonicMelodyPreparer
import app.melotrail.arrangement.MonophonicMelodyPreparationReport
import app.melotrail.arrangement.SelectedMidiArtifactResolver
import app.melotrail.arrangement.SourceSongArtifact
import app.melotrail.arrangement.SourceSongAssembler
import app.melotrail.arrangement.SourceSongAssemblyRequest
import app.melotrail.arrangement.SourceSongGrooveEvidence
import app.melotrail.arrangement.SourceSongHarmonySpan
import app.melotrail.arrangement.SourceSongMidiInput
import app.melotrail.arrangement.SourceSongSection
import app.melotrail.arrangement.SourceSong
import app.melotrail.arrangement.SongPart
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.sha256Hex
import app.melotrail.arrangement.toSectionInstance
import app.melotrail.preparation.MidiTimeMappingStore
import app.melotrail.preparation.SourceGrooveTemplateStatus
import app.melotrail.preparation.SourceTimingEvidenceStore
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
    private val harmonyFitter: MidiHarmonyFitter = MidiHarmonyFitter(),
    private val sourceSongAssembler: SourceSongAssembler = SourceSongAssembler()
) {
    /** Assemble the current structured source song, or verify its existing immutable candidate. */
    fun assemble(projectRoot: Path): SourceSongArtifact {
        val root = projectRoot.toAbsolutePath().normalize()
        val project = ProjectStore.read(root)
        project.requireValid(root)
        val authority = musicalAuthorityBuilder.build(root)
        val usedPartIds = project.envelope.structureOccurrences.map { it.partId }.toSet()
        val grooveByPart = project.parts.filter { it.id in usedPartIds }.associate { part ->
            part.id to acceptedGrooveEvidence(root, part)
        }
        val preparedByPart = project.parts.filter { it.id in usedPartIds }.associate { part ->
            val selected = selectedMidiArtifactResolver.resolve(root, project, part)
            val input = MelodyPreparationArtifactReference(selected.projectRelativePath, selected.sha256, selected.ppq, noteOnCount(selected.path))
            part.id to melodyPreparer.prepare(root, part.id, input)
        }
        val occurrences = authority.occurrenceTimeline.associateBy { it.occurrenceId }
        val sourceMidiByOccurrence = project.envelope.structureOccurrences.associate { occurrence ->
            val timeline = requireNotNull(occurrences[occurrence.id]) { "Missing canonical source-song occurrence '${occurrence.id}'" }
            val prepared = preparedByPart.getValue(occurrence.partId)
            require(authority.harmonicTimeline.ppq % prepared.midi.ppq == 0) { "Harmony timeline cannot exactly represent prepared MIDI for '${occurrence.partId}'" }
            val scale = authority.harmonicTimeline.ppq / prepared.midi.ppq
            val harmony = authority.harmonicTimeline.forOccurrence(occurrence.id).map { span ->
                require((span.startTick - timeline.startTick) % scale == 0L && (span.endTick - timeline.startTick) % scale == 0L) {
                    "Harmony timeline cannot exactly map '${occurrence.id}' to prepared MIDI ticks"
                }
                MelodyHarmonyFitSpan(span.bar, (span.startTick - timeline.startTick) / scale, (span.endTick - timeline.startTick) / scale,
                    span.chord.rootChromatic, span.chord.rootSymbol, span.chord.quality)
            }
            val fit = harmonyFitter.fit(MelodyHarmonyFitRequest(
                root, prepared.midi, prepared.report,
                MelodyHarmonyFitContext(authority.contextSha256, occurrence.partId, occurrence.id, authority.projectKey, authority.tempo.bpm,
                    authority.meter.numerator, authority.meter.denominator, prepared.midi.ppq, harmony)
            ))
            occurrence.id to SourceSongMidiInput(occurrence.partId, fit.midi.path, fit.midi.sha256, fit.midi.ppq, "HARMONY_FITTED", prepared.report, fit.report)
        }
        val preparationContext = sha256Hex(buildString {
            append(authority.contextSha256).append('|').append(MonophonicMelodyPreparationReport.PROCESSOR_VERSION).append('|')
            append(MelodyHarmonyFitReport.PROCESSOR_VERSION).append('|').append(SourceSong.PROCESSOR_VERSION).append('|')
            sourceMidiByOccurrence.toSortedMap().forEach { (occurrenceId, source) ->
                append(occurrenceId).append('=').append(source.sha256).append(':').append(requireNotNull(source.preparationReport).sha256)
                    .append(':').append(requireNotNull(source.harmonyFitReport).sha256).append(';')
            }
            grooveByPart.toSortedMap().forEach { (partId, evidence) ->
                append(partId).append('=').append(evidence.status.name).append(':').append(evidence.sourceTimingReport?.sha256.orEmpty())
                    .append(':').append(evidence.template?.sourceSha256.orEmpty()).append(';')
            }
        })
        val counts = mutableMapOf<String, Int>()
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
                sourceMidi = sourceMidiByOccurrence.getValue(occurrence.id),
                canonicalHarmony = authority.harmonicTimeline.forOccurrence(occurrence.id).map { chord ->
                    SourceSongHarmonySpan(chord.occurrenceId, chord.bar, chord.startTick, chord.endTick,
                        chord.chord.rootChromatic, chord.chord.rootSymbol, chord.chord.quality)
                },
                groove = grooveByPart.getValue(occurrence.partId)
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

    /** Return only a QP-003-approved source template, otherwise persist an explicit neutral grid fallback. */
    private fun acceptedGrooveEvidence(root: Path, part: SongPart): SourceSongGrooveEvidence {
        val mappingReference = part.timingMappingEvidence ?: return SourceSongGrooveEvidence.gridFallback()
        val timingReference = requireNotNull(part.sourceTimingEvidence) {
            "Part '${part.id}' has timing mapping evidence without source timing evidence"
        }
        val mapping = MidiTimeMappingStore.readReport(root, mappingReference.report)
        require(mapping.partId == part.id && mapping.sourceTimingReport == timingReference.report && mapping.sourceSha256 == timingReference.sourceSha256) {
            "Part '${part.id}' timing mapping does not bind its source timing evidence"
        }
        if (!mapping.acceptedSourceGroove) return SourceSongGrooveEvidence.gridFallback()
        val timing = SourceTimingEvidenceStore.read(root, timingReference.report)
        require(timing.partId == part.id && timing.source.sha256 == timingReference.sourceSha256 && timing.groove.status == SourceGrooveTemplateStatus.MEASURED) {
            "Part '${part.id}' has no accepted measured source groove"
        }
        return SourceSongGrooveEvidence(
            status = app.melotrail.arrangement.SourceSongGrooveStatus.MEASURED,
            sourceTimingReport = WorkflowArtifactReference(timingReference.report.path, timingReference.report.sha256),
            template = timing.groove
        )
    }
}

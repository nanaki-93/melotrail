package app.melotrail.project

import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiTrackRoleHint
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectTempo
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Versioned JSON boundary for the target MIDI Core project. DTOs remain private to this file. */
object MidiCoreProjectSchema {
    const val SCHEMA = "melotrail-midi-core"
    const val VERSION = 4

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(project: MidiCoreProject): String = json.encodeToString(ProjectDocumentDto(SCHEMA, VERSION, project.toDto()))

    fun decode(text: String): MidiCoreProject = when (val result = inspect(text)) {
        is MidiCoreProjectDocument.Current -> result.project
        is MidiCoreProjectDocument.Unsupported -> throw UnsupportedMidiCoreProjectException(result.reason)
        is MidiCoreProjectDocument.Invalid -> throw IllegalArgumentException(result.reason)
    }

    /** Safely recognizes prior project documents without migrating or writing them. */
    fun inspect(text: String): MidiCoreProjectDocument {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (error: Exception) {
            return MidiCoreProjectDocument.Invalid("Project document is not valid JSON: ${error.message ?: error.javaClass.simpleName}")
        }
        val (schema, version) = try {
            root.string("schema") to root.string("version")?.toIntOrNull()
        } catch (error: Exception) {
            return MidiCoreProjectDocument.Invalid(
                "Project schema discriminator is invalid: ${error.message ?: error.javaClass.simpleName}",
            )
        }
        if (schema != SCHEMA) {
            return MidiCoreProjectDocument.Unsupported(schema, version, "Unsupported project schema '${schema ?: "missing"}'")
        }
        if (version != VERSION) {
            return MidiCoreProjectDocument.Unsupported(schema, version, "Unsupported MIDI Core project version '${version ?: "missing"}'")
        }
        return try {
            MidiCoreProjectDocument.Current(json.decodeFromString<ProjectDocumentDto>(text).project.toDomain())
        } catch (error: Exception) {
            MidiCoreProjectDocument.Invalid("Invalid MIDI Core project v$VERSION document: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}

sealed interface MidiCoreProjectDocument {
    data class Current(val project: MidiCoreProject) : MidiCoreProjectDocument
    data class Unsupported(val schema: String?, val version: Int?, val reason: String) : MidiCoreProjectDocument
    data class Invalid(val reason: String) : MidiCoreProjectDocument
}

class UnsupportedMidiCoreProjectException(message: String) : IllegalArgumentException(message)

@Serializable
private data class ProjectDocumentDto(val schema: String, val version: Int, val project: ProjectDto)

@Serializable
private data class ProjectDto(
    val id: String,
    val metadata: ProjectMetadataDto,
    val sourceMidi: SourceMidiDto? = null,
    val selectedMelody: SelectedMelodyDto? = null,
    val authority: AuthorityDto? = null,
    val arrangementPlan: ArrangementPlanDto? = null,
    val candidates: List<CandidateDto> = emptyList(),
    val arrangementDrafts: List<ArrangementDraftDto> = emptyList(),
    val acceptances: List<AcceptanceDto> = emptyList(),
    val acceptedPlannedRests: List<PlannedRestDto> = emptyList(),
    val acceptanceHistory: List<AcceptanceHistoryDto> = emptyList(),
    val arrangementDraftAcceptanceHistory: List<ArrangementDraftAcceptanceHistoryDto> = emptyList(),
    val exportSnapshots: List<ExportSnapshotDto> = emptyList(),
    val revision: Long = 0L,
)

@Serializable
private data class ProjectMetadataDto(val name: String, val createdAt: String, val applicationVersion: String? = null)

@Serializable
private data class ArtifactDto(val path: String, val sha256: String)

@Serializable
private data class SourceMidiDto(
    val originalFilename: String,
    val sha256: String,
    val format: Int,
    val ppq: Int,
    val original: ArtifactDto,
    val importReport: ArtifactDto,
    val trackSummaries: List<TrackSummaryDto>,
    val sourceEndTick: Long,
    val lastNoteEndTick: Long,
)

@Serializable
private data class TrackSummaryDto(
    val trackIndex: Int,
    val name: String? = null,
    val channels: List<ChannelSummaryDto>,
    val durationTicks: Long = 0L,
)

@Serializable
private data class ChannelSummaryDto(
    val channel: Int,
    val noteCount: Int,
    val minimumPitch: Int? = null,
    val maximumPitch: Int? = null,
    val controllerCount: Int,
    val likelyRoles: List<MidiTrackRoleHint>,
)

@Serializable
private data class SelectedMelodyDto(val trackIndex: Int, val channel: Int, val identitySha256: String)

@Serializable
private data class AuthorityDto(
    val key: KeyDto,
    val tempoMicrosecondsPerQuarter: Int,
    val meterNumerator: Int,
    val meterDenominatorExponent: Int,
    val sectionDefinitions: List<SectionDefinitionDto>,
    val occurrences: List<OccurrenceDto>,
    val chordEvents: List<ChordEventDto>,
    val pickupTicks: Long = 0L,
    val arrangementEndTick: Long,
)

@Serializable
private data class KeyDto(val tonic: Int, val modeId: String, val spelling: ProjectKeySpelling? = null)

@Serializable
private data class SectionDefinitionDto(val id: String, val name: String)

@Serializable
private data class OccurrenceDto(val id: String, val definitionId: String, val label: String, val startTick: Long, val endTick: Long)

@Serializable
private data class ChordEventDto(val id: String, val occurrenceId: String, val symbol: String, val startTick: Long, val endTick: Long)

@Serializable
private data class ArrangementPlanDto(
    val version: Int,
    val sharedGroove: SharedGrooveDto,
    val occurrences: List<OccurrenceArrangementPlanDto>,
)

@Serializable
private data class SharedGrooveDto(
    val feel: MidiCoreGrooveFeel,
    val subdivision: MidiCoreGrooveSubdivision,
    val drive: MidiCoreGrooveDrive,
)

@Serializable
private data class OccurrenceArrangementPlanDto(
    val occurrenceId: String,
    val purpose: MidiCoreArrangementPurpose,
    val phraseGroupId: String,
    val repeatFamilyId: String,
    val repeatOrdinal: Int,
    val energy: Int,
    val roleSettings: List<RolePlanSettingsDto>,
    val entryIntent: MidiCoreBoundaryIntent,
    val exitIntent: MidiCoreBoundaryIntent,
)

@Serializable
private data class RolePlanSettingsDto(
    val role: CandidateRole,
    val activity: MidiCoreRoleActivity,
    val density: Int,
    val registerPreference: MidiCoreRegisterPreference,
)

@Serializable
private data class CandidateDto(
    val id: String,
    val role: CandidateRole,
    val occurrenceId: String,
    val generatorVersion: String,
    val authorityHash: String,
    val seed: Long,
    val midi: ArtifactDto,
    val validationReport: ArtifactDto,
    val createdAt: String,
    val profileId: String = "default",
    val patternId: String = "unspecified",
    val status: MidiCoreCandidateStatus = MidiCoreCandidateStatus.CURRENT,
    val rejectionReason: String? = null,
    val draftDependencyIds: List<String> = emptyList(),
    val draftDependencyRests: List<PlannedRestDto> = emptyList(),
    val acceptedDependencyIds: List<String> = emptyList(),
    val boundarySummarySha256: String? = null,
)

@Serializable
private data class ArrangementDraftReferenceDto(
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val midiSha256: String,
    val validationReportSha256: String,
    val authorityHash: String,
)

@Serializable
private data class ArrangementDraftValidationDto(
    val scopeCount: Int,
    val noteCount: Int,
    val allPassed: Boolean,
    val reportDigestSha256: String,
)

@Serializable
private data class ArrangementDraftDto(
    val id: String,
    val styleId: String,
    val styleVersion: Int,
    val authorityHash: String,
    val rootSeed: Long,
    val candidateReferences: List<ArrangementDraftReferenceDto>,
    val validation: ArrangementDraftValidationDto,
    val createdAt: String,
    val plannedRests: List<PlannedRestDto> = emptyList(),
)

@Serializable
private data class AcceptanceDto(val occurrenceId: String, val role: CandidateRole, val candidateId: String, val locked: Boolean)
@Serializable
private data class PlannedRestDto(val occurrenceId: String, val role: CandidateRole, val authorityHash: String, val locked: Boolean = false)

@Serializable
private data class AcceptanceHistoryDto(
    val id: String,
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val action: MidiCoreAcceptanceAction,
    val recordedAt: String,
)

@Serializable
private data class ArrangementDraftAcceptanceHistoryDto(
    val id: String,
    val draftId: String,
    val previousAcceptances: List<AcceptanceDto>,
    val appliedAcceptances: List<AcceptanceDto>,
    val recordedAt: String,
    val previousPlannedRests: List<PlannedRestDto> = emptyList(),
    val appliedPlannedRests: List<PlannedRestDto> = emptyList(),
)

@Serializable
private data class ExportSnapshotDto(
    val id: String,
    val sourceSha256: String,
    val authorityHash: String,
    val files: List<ExportedSnapshotFileDto>,
    val createdAt: String,
    val acceptedCandidates: List<AcceptedCandidateReferenceDto> = emptyList(),
    val roleSettings: Map<String, String> = emptyMap(),
    val generatorVersions: Map<String, String> = emptyMap(),
    val enabledRoles: List<CandidateRole> = CandidateRole.entries,
)

@Serializable
private data class ExportedSnapshotFileDto(val kind: ExportedFileKind, val artifact: ArtifactDto)

@Serializable
private data class AcceptedCandidateReferenceDto(
    val occurrenceId: String,
    val role: CandidateRole,
    val candidateId: String,
    val midiSha256: String,
    val validationReportSha256: String,
    val authorityHash: String,
    val generatorVersion: String,
    val profileId: String,
    val patternId: String,
    val seed: Long,
)

private fun MidiCoreProject.toDto() = ProjectDto(
    id = id.value,
    metadata = metadata.toDto(),
    sourceMidi = sourceMidi?.toDto(),
    selectedMelody = selectedMelody?.toDto(),
    authority = authority?.toDto(),
    arrangementPlan = arrangementPlan?.toDto(),
    candidates = candidates.map(MidiCoreCandidate::toDto),
    arrangementDrafts = arrangementDrafts.map(MidiCoreArrangementDraft::toDto),
    acceptances = acceptances.map(CandidateAcceptance::toDto),
    acceptedPlannedRests = acceptedPlannedRests.map(MidiCorePlannedRest::toDto),
    acceptanceHistory = acceptanceHistory.map(CandidateAcceptanceHistory::toDto),
    arrangementDraftAcceptanceHistory = arrangementDraftAcceptanceHistory.map(MidiCoreArrangementDraftAcceptanceHistory::toDto),
    exportSnapshots = exportSnapshots.map(MidiCoreExportSnapshot::toDto),
    revision = revision,
)

private fun ProjectDto.toDomain() = MidiCoreProject(
    id = ProjectId(id),
    metadata = metadata.toDomain(),
    sourceMidi = sourceMidi?.toDomain(),
    selectedMelody = selectedMelody?.toDomain(),
    authority = authority?.toDomain(),
    arrangementPlan = arrangementPlan?.toDomain(),
    candidates = candidates.map(CandidateDto::toDomain),
    arrangementDrafts = arrangementDrafts.map(ArrangementDraftDto::toDomain),
    acceptances = acceptances.map(AcceptanceDto::toDomain),
    acceptedPlannedRests = acceptedPlannedRests.map(PlannedRestDto::toDomain),
    acceptanceHistory = acceptanceHistory.map(AcceptanceHistoryDto::toDomain),
    arrangementDraftAcceptanceHistory = arrangementDraftAcceptanceHistory.map(ArrangementDraftAcceptanceHistoryDto::toDomain),
    exportSnapshots = exportSnapshots.map(ExportSnapshotDto::toDomain),
    revision = revision,
)

private fun ProjectMetadata.toDto() = ProjectMetadataDto(name, createdAt, applicationVersion)
private fun ProjectMetadataDto.toDomain() = ProjectMetadata(name, createdAt, applicationVersion)
private fun ProjectArtifact.toDto() = ArtifactDto(path.value, sha256)
private fun ArtifactDto.toDomain() = ProjectArtifact(ProjectRelativePath(path), sha256)
private fun SourceMidiRecord.toDto() = SourceMidiDto(
    originalFilename = originalFilename,
    sha256 = sha256,
    format = format,
    ppq = ppq,
    original = original.toDto(),
    importReport = importReport.toDto(),
    trackSummaries = trackSummaries.map(MidiTrackSummary::toDto),
    sourceEndTick = sourceEndTick,
    lastNoteEndTick = lastNoteEndTick,
)

private fun SourceMidiDto.toDomain() = SourceMidiRecord(
    originalFilename = originalFilename,
    sha256 = sha256,
    format = format,
    ppq = ppq,
    original = original.toDomain(),
    importReport = importReport.toDomain(),
    trackSummaries = trackSummaries.map(TrackSummaryDto::toDomain),
    sourceEndTick = sourceEndTick,
    lastNoteEndTick = lastNoteEndTick,
)

private fun MidiTrackSummary.toDto() = TrackSummaryDto(trackIndex, name, channels.map(MidiChannelSummary::toDto), durationTicks)
private fun TrackSummaryDto.toDomain() = MidiTrackSummary(trackIndex, name, channels.map(ChannelSummaryDto::toDomain), durationTicks)
private fun MidiChannelSummary.toDto() = ChannelSummaryDto(channel, noteCount, minimumPitch, maximumPitch, controllerCount, likelyRoles)
private fun ChannelSummaryDto.toDomain() = MidiChannelSummary(channel, noteCount, minimumPitch, maximumPitch, controllerCount, likelyRoles)
private fun SelectedMelodyTrack.toDto() = SelectedMelodyDto(trackIndex, channel, identitySha256)
private fun SelectedMelodyDto.toDomain() = SelectedMelodyTrack(trackIndex, channel, identitySha256)

private fun ProjectAuthority.toDto() = AuthorityDto(
    key.toDto(), tempo.microsecondsPerQuarter, meter.numerator, meter.denominatorExponent,
    sectionDefinitions.map(ProjectSectionDefinition::toDto), occurrences.map(ProjectSectionOccurrence::toDto),
    chordEvents.map(AuthoritativeChordEvent::toDto), pickupTicks, arrangementEndTick,
)

private fun AuthorityDto.toDomain() = ProjectAuthority(
    key.toDomain(), ProjectTempo(tempoMicrosecondsPerQuarter), ProjectMeter(meterNumerator, meterDenominatorExponent),
    sectionDefinitions.map(SectionDefinitionDto::toDomain), occurrences.map(OccurrenceDto::toDomain),
    chordEvents.map(ChordEventDto::toDomain), pickupTicks, arrangementEndTick,
)

private fun ProjectKey.toDto() = KeyDto(tonic, modeId, spelling)
private fun KeyDto.toDomain() = ProjectKey(tonic, modeId, spelling ?: ProjectKeySpelling.canonical(tonic))
private fun ProjectSectionDefinition.toDto() = SectionDefinitionDto(id, name)
private fun SectionDefinitionDto.toDomain() = ProjectSectionDefinition(id, name)
private fun ProjectSectionOccurrence.toDto() = OccurrenceDto(id, definitionId, label, startTick, endTick)
private fun OccurrenceDto.toDomain() = ProjectSectionOccurrence(id, definitionId, label, startTick, endTick)
private fun AuthoritativeChordEvent.toDto() = ChordEventDto(id, occurrenceId, symbol, startTick, endTick)
private fun ChordEventDto.toDomain() = AuthoritativeChordEvent(id, occurrenceId, symbol, startTick, endTick)
private fun MidiCoreArrangementPlan.toDto() = ArrangementPlanDto(version, sharedGroove.toDto(), occurrences.map(MidiCoreOccurrenceArrangementPlan::toDto))
private fun ArrangementPlanDto.toDomain() = MidiCoreArrangementPlan(version, sharedGroove.toDomain(), occurrences.map(OccurrenceArrangementPlanDto::toDomain))
private fun MidiCoreSharedGrooveIntent.toDto() = SharedGrooveDto(feel, subdivision, drive)
private fun SharedGrooveDto.toDomain() = MidiCoreSharedGrooveIntent(feel, subdivision, drive)
private fun MidiCoreOccurrenceArrangementPlan.toDto() = OccurrenceArrangementPlanDto(
    occurrenceId, purpose, phraseGroupId, repeatFamilyId, repeatOrdinal, energy, roleSettings.map(MidiCoreRolePlanSettings::toDto), entryIntent, exitIntent,
)
private fun OccurrenceArrangementPlanDto.toDomain() = MidiCoreOccurrenceArrangementPlan(
    occurrenceId, purpose, phraseGroupId, repeatFamilyId, repeatOrdinal, energy, roleSettings.map(RolePlanSettingsDto::toDomain), entryIntent, exitIntent,
)
private fun MidiCoreRolePlanSettings.toDto() = RolePlanSettingsDto(role, activity, density, registerPreference)
private fun RolePlanSettingsDto.toDomain() = MidiCoreRolePlanSettings(role, activity, density, registerPreference)
private fun MidiCoreCandidate.toDto() = CandidateDto(
    id, role, occurrenceId, generatorVersion, authorityHash, seed, midi.toDto(), validationReport.toDto(), createdAt,
    profileId, patternId, status, rejectionReason, draftDependencyIds, draftDependencyRests.map(MidiCorePlannedRest::toDto), acceptedDependencyIds, boundarySummarySha256,
)
private fun CandidateDto.toDomain() = MidiCoreCandidate(
    id, role, occurrenceId, generatorVersion, authorityHash, seed, midi.toDomain(), validationReport.toDomain(), createdAt,
    profileId, patternId, status, rejectionReason, draftDependencyIds, acceptedDependencyIds, boundarySummarySha256, draftDependencyRests.map(PlannedRestDto::toDomain),
)
private fun MidiCoreArrangementDraft.toDto() = ArrangementDraftDto(
    id, styleId, styleVersion, authorityHash, rootSeed, candidateReferences.map(MidiCoreArrangementDraftCandidateReference::toDto), validation.toDto(), createdAt, plannedRests.map(MidiCorePlannedRest::toDto),
)
private fun ArrangementDraftDto.toDomain() = MidiCoreArrangementDraft(
    id, styleId, styleVersion, authorityHash, rootSeed, candidateReferences.map(ArrangementDraftReferenceDto::toDomain), validation.toDomain(), createdAt, plannedRests.map(PlannedRestDto::toDomain),
)
private fun MidiCoreArrangementDraftCandidateReference.toDto() = ArrangementDraftReferenceDto(
    occurrenceId, role, candidateId, midiSha256, validationReportSha256, authorityHash,
)
private fun ArrangementDraftReferenceDto.toDomain() = MidiCoreArrangementDraftCandidateReference(
    occurrenceId, role, candidateId, midiSha256, validationReportSha256, authorityHash,
)
private fun MidiCoreArrangementDraftValidationSummary.toDto() = ArrangementDraftValidationDto(
    scopeCount, noteCount, allPassed, reportDigestSha256,
)
private fun ArrangementDraftValidationDto.toDomain() = MidiCoreArrangementDraftValidationSummary(
    scopeCount, noteCount, allPassed, reportDigestSha256,
)
private fun CandidateAcceptance.toDto() = AcceptanceDto(occurrenceId, role, candidateId, locked)
private fun AcceptanceDto.toDomain() = CandidateAcceptance(occurrenceId, role, candidateId, locked)
private fun MidiCorePlannedRest.toDto() = PlannedRestDto(occurrenceId, role, authorityHash, locked)
private fun PlannedRestDto.toDomain() = MidiCorePlannedRest(occurrenceId, role, authorityHash, locked)
private fun CandidateAcceptanceHistory.toDto() = AcceptanceHistoryDto(id, occurrenceId, role, candidateId, action, recordedAt)
private fun AcceptanceHistoryDto.toDomain() = CandidateAcceptanceHistory(id, occurrenceId, role, candidateId, action, recordedAt)
private fun MidiCoreArrangementDraftAcceptanceHistory.toDto() = ArrangementDraftAcceptanceHistoryDto(
    id, draftId, previousAcceptances.map(CandidateAcceptance::toDto), appliedAcceptances.map(CandidateAcceptance::toDto), recordedAt, previousPlannedRests.map(MidiCorePlannedRest::toDto), appliedPlannedRests.map(MidiCorePlannedRest::toDto),
)
private fun ArrangementDraftAcceptanceHistoryDto.toDomain() = MidiCoreArrangementDraftAcceptanceHistory(
    id, draftId, previousAcceptances.map(AcceptanceDto::toDomain), appliedAcceptances.map(AcceptanceDto::toDomain), recordedAt, previousPlannedRests.map(PlannedRestDto::toDomain), appliedPlannedRests.map(PlannedRestDto::toDomain),
)
private fun MidiCoreExportSnapshot.toDto() = ExportSnapshotDto(
    id, sourceSha256, authorityHash, files.map(ExportedSnapshotFile::toDto), createdAt,
    acceptedCandidates.map(MidiCoreAcceptedCandidateReference::toDto), roleSettings.toSortedMap(), generatorVersions.toSortedMap(), enabledRoles,
)
private fun ExportSnapshotDto.toDomain() = MidiCoreExportSnapshot(
    id, sourceSha256, authorityHash, files.map(ExportedSnapshotFileDto::toDomain), createdAt,
    acceptedCandidates.map(AcceptedCandidateReferenceDto::toDomain), roleSettings, generatorVersions, enabledRoles,
)
private fun ExportedSnapshotFile.toDto() = ExportedSnapshotFileDto(kind, artifact.toDto())
private fun ExportedSnapshotFileDto.toDomain() = ExportedSnapshotFile(kind, artifact.toDomain())
private fun MidiCoreAcceptedCandidateReference.toDto() = AcceptedCandidateReferenceDto(
    occurrenceId, role, candidateId, midiSha256, validationReportSha256, authorityHash, generatorVersion, profileId, patternId, seed,
)
private fun AcceptedCandidateReferenceDto.toDomain() = MidiCoreAcceptedCandidateReference(
    occurrenceId, role, candidateId, midiSha256, validationReportSha256, authorityHash, generatorVersion, profileId, patternId, seed,
)

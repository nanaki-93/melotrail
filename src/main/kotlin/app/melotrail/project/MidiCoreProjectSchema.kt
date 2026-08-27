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
    const val VERSION = 1

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
    val candidates: List<CandidateDto> = emptyList(),
    val acceptances: List<AcceptanceDto> = emptyList(),
    val exportSnapshots: List<ExportSnapshotDto> = emptyList(),
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
)

@Serializable
private data class TrackSummaryDto(val trackIndex: Int, val name: String? = null, val channels: List<ChannelSummaryDto>)

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
)

@Serializable
private data class AcceptanceDto(val occurrenceId: String, val role: CandidateRole, val candidateId: String, val locked: Boolean)

@Serializable
private data class ExportSnapshotDto(
    val id: String,
    val sourceSha256: String,
    val authorityHash: String,
    val files: List<ExportedSnapshotFileDto>,
    val createdAt: String,
)

@Serializable
private data class ExportedSnapshotFileDto(val kind: ExportedFileKind, val artifact: ArtifactDto)

private fun MidiCoreProject.toDto() = ProjectDto(
    id = id.value,
    metadata = metadata.toDto(),
    sourceMidi = sourceMidi?.toDto(),
    selectedMelody = selectedMelody?.toDto(),
    authority = authority?.toDto(),
    candidates = candidates.map(MidiCoreCandidate::toDto),
    acceptances = acceptances.map(CandidateAcceptance::toDto),
    exportSnapshots = exportSnapshots.map(MidiCoreExportSnapshot::toDto),
)

private fun ProjectDto.toDomain() = MidiCoreProject(
    id = ProjectId(id),
    metadata = metadata.toDomain(),
    sourceMidi = sourceMidi?.toDomain(),
    selectedMelody = selectedMelody?.toDomain(),
    authority = authority?.toDomain(),
    candidates = candidates.map(CandidateDto::toDomain),
    acceptances = acceptances.map(AcceptanceDto::toDomain),
    exportSnapshots = exportSnapshots.map(ExportSnapshotDto::toDomain),
)

private fun ProjectMetadata.toDto() = ProjectMetadataDto(name, createdAt, applicationVersion)
private fun ProjectMetadataDto.toDomain() = ProjectMetadata(name, createdAt, applicationVersion)
private fun ProjectArtifact.toDto() = ArtifactDto(path.value, sha256)
private fun ArtifactDto.toDomain() = ProjectArtifact(ProjectRelativePath(path), sha256)
private fun SourceMidiRecord.toDto() = SourceMidiDto(
    originalFilename,
    sha256,
    format,
    ppq,
    original.toDto(),
    importReport.toDto(),
    trackSummaries.map(MidiTrackSummary::toDto),
    sourceEndTick,
)

private fun SourceMidiDto.toDomain() = SourceMidiRecord(
    originalFilename,
    sha256,
    format,
    ppq,
    original.toDomain(),
    importReport.toDomain(),
    trackSummaries.map(TrackSummaryDto::toDomain),
    sourceEndTick,
)

private fun MidiTrackSummary.toDto() = TrackSummaryDto(trackIndex, name, channels.map(MidiChannelSummary::toDto))
private fun TrackSummaryDto.toDomain() = MidiTrackSummary(trackIndex, name, channels.map(ChannelSummaryDto::toDomain))
private fun MidiChannelSummary.toDto() = ChannelSummaryDto(channel, noteCount, minimumPitch, maximumPitch, controllerCount, likelyRoles)
private fun ChannelSummaryDto.toDomain() = MidiChannelSummary(channel, noteCount, minimumPitch, maximumPitch, controllerCount, likelyRoles)
private fun SelectedMelodyTrack.toDto() = SelectedMelodyDto(trackIndex, channel, identitySha256)
private fun SelectedMelodyDto.toDomain() = SelectedMelodyTrack(trackIndex, channel, identitySha256)

private fun ProjectAuthority.toDto() = AuthorityDto(
    key.toDto(), tempo.microsecondsPerQuarter, meter.numerator, meter.denominatorExponent,
    sectionDefinitions.map(ProjectSectionDefinition::toDto), occurrences.map(ProjectSectionOccurrence::toDto),
    chordEvents.map(AuthoritativeChordEvent::toDto), pickupTicks,
)

private fun AuthorityDto.toDomain() = ProjectAuthority(
    key.toDomain(), ProjectTempo(tempoMicrosecondsPerQuarter), ProjectMeter(meterNumerator, meterDenominatorExponent),
    sectionDefinitions.map(SectionDefinitionDto::toDomain), occurrences.map(OccurrenceDto::toDomain),
    chordEvents.map(ChordEventDto::toDomain), pickupTicks,
)

private fun ProjectKey.toDto() = KeyDto(tonic, modeId, spelling)
private fun KeyDto.toDomain() = ProjectKey(tonic, modeId, spelling ?: ProjectKeySpelling.canonical(tonic))
private fun ProjectSectionDefinition.toDto() = SectionDefinitionDto(id, name)
private fun SectionDefinitionDto.toDomain() = ProjectSectionDefinition(id, name)
private fun ProjectSectionOccurrence.toDto() = OccurrenceDto(id, definitionId, label, startTick, endTick)
private fun OccurrenceDto.toDomain() = ProjectSectionOccurrence(id, definitionId, label, startTick, endTick)
private fun AuthoritativeChordEvent.toDto() = ChordEventDto(id, occurrenceId, symbol, startTick, endTick)
private fun ChordEventDto.toDomain() = AuthoritativeChordEvent(id, occurrenceId, symbol, startTick, endTick)
private fun MidiCoreCandidate.toDto() = CandidateDto(id, role, occurrenceId, generatorVersion, authorityHash, seed, midi.toDto(), validationReport.toDto(), createdAt)
private fun CandidateDto.toDomain() = MidiCoreCandidate(id, role, occurrenceId, generatorVersion, authorityHash, seed, midi.toDomain(), validationReport.toDomain(), createdAt)
private fun CandidateAcceptance.toDto() = AcceptanceDto(occurrenceId, role, candidateId, locked)
private fun AcceptanceDto.toDomain() = CandidateAcceptance(occurrenceId, role, candidateId, locked)
private fun MidiCoreExportSnapshot.toDto() = ExportSnapshotDto(id, sourceSha256, authorityHash, files.map(ExportedSnapshotFile::toDto), createdAt)
private fun ExportSnapshotDto.toDomain() = MidiCoreExportSnapshot(id, sourceSha256, authorityHash, files.map(ExportedSnapshotFileDto::toDomain), createdAt)
private fun ExportedSnapshotFile.toDto() = ExportedSnapshotFileDto(kind, artifact.toDto())
private fun ExportedSnapshotFileDto.toDomain() = ExportedSnapshotFile(kind, artifact.toDomain())

package app.melotrail.application

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.adapter.MidiReadException
import app.melotrail.midi.domain.MidiChannelSummary
import app.melotrail.midi.domain.MidiFinding
import app.melotrail.midi.domain.MidiImportDisposition
import app.melotrail.midi.domain.MidiImportValidationResult
import app.melotrail.midi.domain.MidiImportValidator
import app.melotrail.midi.domain.MidiInspectionResult
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.SourceMidiRecord
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Imports one source SMF into a target project without ever replacing an existing source. */
class MidiCoreSourceImport(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val validator: MidiImportValidator = MidiImportValidator(),
    private val inspectionObserver: SourceInspectionObserver = SourceInspectionObserver.NONE,
) {
    fun import(request: ImportMidiCoreSource): MidiCoreSourceImportResult {
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            return rejected(
                MidiCoreSourceImportProblemCode.INVALID_PROJECT,
                "The project cannot be verified before importing MIDI.",
                "Open a valid MIDI Core project and retry the import.",
            )
        }
        if (current != request.session.project) {
            return rejected(
                MidiCoreSourceImportProblemCode.STALE_PROJECT,
                "The project changed since this screen was opened.",
                "Reopen the project before importing MIDI so no current state is overwritten.",
            )
        }
        if (current.sourceMidi != null) {
            return rejected(
                MidiCoreSourceImportProblemCode.SOURCE_ALREADY_IMPORTED,
                "This project already has an immutable source MIDI file.",
                "Create a new project to import a different source MIDI file.",
            )
        }
        if (!hasMidiExtension(request.source)) {
            return rejected(
                MidiCoreSourceImportProblemCode.UNSUPPORTED_EXTENSION,
                "Choose a .mid or .midi Standard MIDI file.",
                "Export the source as a Standard MIDI file and retry.",
            )
        }
        if (hasUnboundCanonicalImportArtifacts(root)) {
            return rejected(
                MidiCoreSourceImportProblemCode.UNBOUND_IMPORT_ARTIFACTS,
                "This project contains unbound import artifacts from an earlier interrupted import.",
                "Inspect the project folder and recover or remove the unbound artifacts before retrying.",
            )
        }

        val inspection = try {
            reader.inspect(request.source)
        } catch (error: MidiReadException) {
            return rejected(MidiCoreSourceImportProblemCode.INVALID_MIDI, "The selected file is not a readable Standard MIDI file.", "Choose a supported SMF format 0 or 1 file.")
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreSourceImportProblemCode.INVALID_MIDI, error.message ?: "The selected file is not a supported Standard MIDI file.", "Choose a supported SMF format 0 or 1 file.")
        } catch (error: Exception) {
            return rejected(MidiCoreSourceImportProblemCode.IO_FAILURE, "The selected MIDI file could not be read.", "Check that the source file remains available and retry.")
        }
        val validation = validator.validate(inspection)
        if (validation.disposition == MidiImportDisposition.REJECTED) {
            return rejected(
                MidiCoreSourceImportProblemCode.IMPORT_REJECTED,
                "The MIDI source has blocking structural issues and was not imported.",
                "Resolve the blocking findings in the import report preview and retry.",
                validation,
            )
        }

        return publishAndBind(root, current, request.source, inspection, validation)
    }

    private fun publishAndBind(
        root: Path,
        current: MidiCoreProject,
        source: Path,
        inspection: MidiInspectionResult,
        validation: MidiImportValidationResult,
    ): MidiCoreSourceImportResult {
        val published = mutableListOf<ProjectArtifact>()
        try {
            inspectionObserver.afterInspection(source)
            val sourceArtifact = artifacts.publishSource(root, source)
            published += sourceArtifact
            if (sourceArtifact.sha256 != inspection.sequence.source.sha256) {
                discardUnbound(root, published)
                return rejected(
                    MidiCoreSourceImportProblemCode.SOURCE_CHANGED,
                    "The source MIDI changed while it was being imported.",
                    "Keep the source file unchanged and retry the import.",
                )
            }
            val reportArtifact = artifacts.publishImportReport(root, importReport(inspection, validation))
            published += reportArtifact
            val updated = current.copy(
                revision = current.revision + 1L,
                sourceMidi = SourceMidiRecord(
                    originalFilename = inspection.sequence.source.originalFilename,
                    sha256 = inspection.sequence.source.sha256,
                    format = inspection.sequence.source.format,
                    ppq = inspection.sequence.source.ppq.value,
                    original = sourceArtifact,
                    importReport = reportArtifact,
                    trackSummaries = inspection.trackSummaries,
                    sourceEndTick = inspection.sourceEndTick,
                ),
            )
            artifacts.saveProject(root, updated)
            return MidiCoreSourceImportResult.Imported(MidiCoreProjectSession(root, updated), validation)
        } catch (error: MidiCoreProjectSaveException) {
            discardUnbound(root, published)
            return rejected(
                MidiCoreSourceImportProblemCode.SAVE_FAILED,
                "The imported source could not be bound to the project safely.",
                "Retry the save; the last known-good project remains available.",
                validation,
            )
        } catch (error: Exception) {
            discardUnbound(root, published)
            return rejected(
                MidiCoreSourceImportProblemCode.IO_FAILURE,
                "The source MIDI could not be imported safely.",
                "Check the project folder and source file, then retry.",
                validation,
            )
        }
    }

    private fun discardUnbound(root: Path, published: List<ProjectArtifact>) {
        if (published.isEmpty()) return
        try {
            artifacts.discardUnboundImportArtifacts(root, published)
        } catch (_: Exception) {
            // The original error is more actionable; verified bound artifacts are never deleted here.
        }
    }

    private fun hasUnboundCanonicalImportArtifacts(root: Path): Boolean =
        Files.exists(root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value), LinkOption.NOFOLLOW_LINKS) ||
            Files.exists(root.resolve(MidiCoreArtifactStore.IMPORT_REPORT.value), LinkOption.NOFOLLOW_LINKS)

    private fun hasMidiExtension(source: Path): Boolean =
        source.fileName?.toString()?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase() in setOf("mid", "midi")

    private fun importReport(inspection: MidiInspectionResult, validation: MidiImportValidationResult): String =
        json.encodeToString(
            ImportReportDto(
                disposition = validation.disposition,
                source = ImportSourceDto(
                    inspection.sequence.source.originalFilename,
                    inspection.sequence.source.sha256,
                    inspection.sequence.source.format,
                    inspection.sequence.source.ppq.value,
                    inspection.sourceEndTick,
                ),
                trackSummaries = inspection.trackSummaries.map(MidiTrackSummary::toReportDto),
                findings = validation.findings.map(MidiFinding::toReportDto),
            ),
        )

    private fun rejected(
        code: MidiCoreSourceImportProblemCode,
        message: String,
        nextAction: String,
        validation: MidiImportValidationResult? = null,
    ): MidiCoreSourceImportResult.Rejected =
        MidiCoreSourceImportResult.Rejected(MidiCoreSourceImportProblem(code, message, nextAction), validation)

    private companion object {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

data class ImportMidiCoreSource(val session: MidiCoreProjectSession, val source: Path)

sealed interface MidiCoreSourceImportResult {
    data class Imported(val session: MidiCoreProjectSession, val validation: MidiImportValidationResult) : MidiCoreSourceImportResult
    data class Rejected(val problem: MidiCoreSourceImportProblem, val validation: MidiImportValidationResult? = null) : MidiCoreSourceImportResult
}

data class MidiCoreSourceImportProblem(
    val code: MidiCoreSourceImportProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreSourceImportProblemCode {
    INVALID_PROJECT,
    STALE_PROJECT,
    SOURCE_ALREADY_IMPORTED,
    UNSUPPORTED_EXTENSION,
    UNBOUND_IMPORT_ARTIFACTS,
    INVALID_MIDI,
    IMPORT_REJECTED,
    SOURCE_CHANGED,
    SAVE_FAILED,
    IO_FAILURE,
}

fun interface SourceInspectionObserver {
    fun afterInspection(source: Path)

    companion object {
        val NONE = SourceInspectionObserver { }
    }
}

@Serializable
private data class ImportReportDto(
    val schema: String = "melotrail-midi-import-report",
    val version: Int = 1,
    val disposition: MidiImportDisposition,
    val source: ImportSourceDto,
    val trackSummaries: List<ImportTrackSummaryDto>,
    val findings: List<ImportFindingDto>,
)

@Serializable
private data class ImportSourceDto(
    val originalFilename: String,
    val sha256: String,
    val format: Int,
    val ppq: Int,
    val sourceEndTick: Long,
)

@Serializable
private data class ImportTrackSummaryDto(
    val trackIndex: Int,
    val name: String? = null,
    val channels: List<ImportChannelSummaryDto>,
    val durationTicks: Long = 0L,
)

@Serializable
private data class ImportChannelSummaryDto(
    val channel: Int,
    val noteCount: Int,
    val minimumPitch: Int? = null,
    val maximumPitch: Int? = null,
    val controllerCount: Int,
    val likelyRoles: List<String>,
)

@Serializable
private data class ImportFindingDto(
    val code: String,
    val severity: String,
    val scope: String,
    val message: String,
    val action: String,
    val trackIndex: Int? = null,
    val channel: Int? = null,
    val tick: Long? = null,
)

private fun MidiTrackSummary.toReportDto() = ImportTrackSummaryDto(trackIndex, name, channels.map(MidiChannelSummary::toReportDto), durationTicks)
private fun MidiChannelSummary.toReportDto() = ImportChannelSummaryDto(channel, noteCount, minimumPitch, maximumPitch, controllerCount, likelyRoles.map(Enum<*>::name))
private fun MidiFinding.toReportDto() = ImportFindingDto(code.name, severity.name, scope.name, message, action, trackIndex, channel, tick)

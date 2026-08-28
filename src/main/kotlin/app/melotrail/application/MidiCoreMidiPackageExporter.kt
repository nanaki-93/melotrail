package app.melotrail.application

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.adapter.JdkMidiWriter
import app.melotrail.midi.domain.MidiChannelPressureEvent
import app.melotrail.midi.domain.MidiControlChangeEvent
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPitchBendEvent
import app.melotrail.midi.domain.SemanticMidiEvent
import app.melotrail.midi.domain.MidiTextEvent
import app.melotrail.midi.domain.MidiTrackNameEvent
import app.melotrail.midi.domain.MidiTempoEvent
import app.melotrail.midi.domain.MidiTimeSignatureEvent
import app.melotrail.midi.domain.MidiMarkerEvent
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.ExportedFileKind
import app.melotrail.project.ExportedSnapshotFile
import app.melotrail.project.MidiCoreAcceptedCandidateReference
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreExportSnapshot
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.adapter.MidiCoreArtifactCollisionException
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One request to publish a complete MIDI Core package for the current project state. */
data class ExportMidiCorePackage(
    val session: MidiCoreProjectSession,
    val enabledRoles: Set<CandidateRole> = CandidateRole.entries.toSet(),
    val snapshotId: String? = null,
    val expectedRevision: Long? = session.project.revision,
) {
    init {
        require(enabledRoles.all { it in CandidateRole.entries }) { "Export roles must be target MIDI Core roles" }
    }
}

/** Semantic facts recorded for one generated MIDI file after successful re-import. */
data class MidiCoreMidiFileValidation(
    val format: Int,
    val ppq: Int,
    val trackNames: List<String>,
    val songEndTick: Long,
    val noteCount: Int,
)

/** One portable MIDI file and its digest in a published package. */
data class MidiCoreExportedPackageFile(
    val kind: ExportedFileKind,
    val filename: String,
    val sha256: String,
    val validation: MidiCoreMidiFileValidation,
)

/** The immutable package returned after files and project snapshot state are both published. */
data class MidiCoreExportedPackage(
    val session: MidiCoreProjectSession,
    val snapshot: MidiCoreExportSnapshot,
    val directory: Path,
    val files: List<MidiCoreExportedPackageFile>,
    val manifestSha256: String,
)

/** Stable blocker categories exposed to the desktop export workflow. */
enum class MidiCorePackageExportProblemCode {
    INVALID_REQUEST,
    INVALID_PROJECT,
    REVISION_CONFLICT,
    STALE_PROJECT,
    SOURCE_REQUIRED,
    MELODY_REQUIRED,
    AUTHORITY_REQUIRED,
    MISSING_ACCEPTANCE,
    CANDIDATE_STALE,
    CANDIDATE_NOT_ACCEPTED,
    DIGEST_MISMATCH,
    VALIDATION_FAILED,
    CANDIDATE_FORMAT_MISMATCH,
    CANDIDATE_CHANNEL_MISMATCH,
    CANDIDATE_OVERFLOW,
    SONG_OVERFLOW,
    EXPORT_NOT_READY,
    SNAPSHOT_ID_COLLISION,
    DESTINATION_COLLISION,
    STAGING_FAILED,
    SEMANTIC_VALIDATION_FAILED,
    ARTIFACT_COLLISION,
    SAVE_FAILED,
    IO_FAILURE,
}

/** A recoverable export blocker with a user-facing next action. */
data class MidiCorePackageExportProblem(
    val code: MidiCorePackageExportProblemCode,
    val message: String,
    val nextAction: String,
    val occurrenceId: String? = null,
    val role: CandidateRole? = null,
    val candidateId: String? = null,
)

/** Result of a package export attempt; no failed attempt is reported as a current snapshot. */
sealed interface MidiCoreMidiPackageExportResult {
    data class Exported(val packageResult: MidiCoreExportedPackage) : MidiCoreMidiPackageExportResult
    data class Rejected(val problem: MidiCorePackageExportProblem) : MidiCoreMidiPackageExportResult
}

/**
 * Publishes the target MIDI package without involving the desktop UI, audio
 * rendering, or a legacy export service.
 *
 * MIDI files are first written into an isolated staging directory, re-imported
 * semantically, digest-checked, and then atomically renamed into one immutable
 * snapshot directory. Project JSON is updated only after the complete package
 * exists.
 */
class MidiCoreMidiPackageExporter(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val assembly: MidiCoreAcceptedSongAssembly = MidiCoreAcceptedSongAssembly(artifacts = artifacts),
    private val snapshotLifecycle: MidiCoreExportSnapshotLifecycle = MidiCoreExportSnapshotLifecycle(
        artifacts = artifacts,
    ),
    private val writer: JdkMidiWriter = JdkMidiWriter(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val clock: Clock = Clock.systemUTC(),
    private val snapshotIdFactory: () -> String = { "export-${UUID.randomUUID()}" },
    private val beforePublish: (Path) -> Unit = {},
) {
    /** Export the requested accepted roles and return the newly recorded immutable snapshot. */
    fun export(request: ExportMidiCorePackage): MidiCoreMidiPackageExportResult =
        MidiCoreProjectWriteCoordinator.withLock(request.session.root) { exportLocked(request) }

    private fun exportLocked(request: ExportMidiCorePackage): MidiCoreMidiPackageExportResult {
        if (request.expectedRevision != null && request.expectedRevision < 0L) {
            return rejected(
                MidiCorePackageExportProblemCode.REVISION_CONFLICT,
                "The expected project revision is invalid.",
                "Reload the project and retry the export.",
            )
        }
        val root = request.session.root.toAbsolutePath().normalize()
        val current = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            return rejected(
                if (error.message.orEmpty().contains("digest", ignoreCase = true)) {
                    MidiCorePackageExportProblemCode.DIGEST_MISMATCH
                } else {
                    MidiCorePackageExportProblemCode.INVALID_PROJECT
                },
                "The project cannot be verified before export.",
                "Open a valid MIDI Core project and repair or restore its referenced artifacts.",
            )
        }
        if (request.expectedRevision != null && current.revision != request.expectedRevision) {
            return rejected(
                MidiCorePackageExportProblemCode.REVISION_CONFLICT,
                "The project changed from revision ${request.expectedRevision} to ${current.revision}.",
                "Reload the project before exporting again.",
            )
        }
        if (current != request.session.project) {
            return rejected(
                MidiCorePackageExportProblemCode.STALE_PROJECT,
                "The project changed since this export request was opened.",
                "Reopen the project and retry the export.",
            )
        }

        val snapshotId = try {
            request.snapshotId ?: snapshotIdFactory()
        } catch (error: Exception) {
            return rejected(
                MidiCorePackageExportProblemCode.INVALID_REQUEST,
                "A stable export snapshot identifier could not be created.",
                "Retry with a valid export snapshot identifier.",
            )
        }
        if (!SAFE_ID.matches(snapshotId)) {
            return rejected(
                MidiCorePackageExportProblemCode.INVALID_REQUEST,
                "The export snapshot identifier is not safe.",
                "Use letters, numbers, hyphens, or underscores for the snapshot identifier.",
            )
        }
        if (current.exportSnapshots.any { it.id == snapshotId }) {
            return rejected(
                MidiCorePackageExportProblemCode.SNAPSHOT_ID_COLLISION,
                "An export snapshot with this identifier already exists.",
                "Choose a new snapshot identifier; existing export evidence was preserved.",
            )
        }

        val assembled = when (
            val result = assembly.assemble(
                AssembleMidiCoreSong(
                    session = MidiCoreProjectSession(root, current),
                    roles = request.enabledRoles,
                    expectedRevision = current.revision,
                ),
            )
        ) {
            is MidiCoreAcceptedSongAssemblyResult.Assembled -> result.review
            is MidiCoreAcceptedSongAssemblyResult.Rejected -> return rejected(result.problem)
        }
        val createdAt = try {
            Instant.now(clock).toString()
        } catch (error: Exception) {
            return rejected(
                MidiCorePackageExportProblemCode.INVALID_REQUEST,
                "A stable export timestamp could not be created.",
                "Retry the export with a functioning system clock.",
            )
        }
        val exportsRoot = try {
            prepareExportsRoot(root)
        } catch (error: Exception) {
            return rejected(
                MidiCorePackageExportProblemCode.IO_FAILURE,
                "The project export directory is unavailable.",
                "Choose a writable MIDI Core project folder and retry the export.",
            )
        }
        val destination = exportsRoot.resolve(snapshotId).normalize()
        if (!destination.startsWith(root) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            return rejected(
                MidiCorePackageExportProblemCode.DESTINATION_COLLISION,
                "The target export snapshot directory already exists.",
                "Choose a new snapshot identifier; existing export evidence was preserved.",
            )
        }

        val staged = try {
            Files.createTempDirectory(exportsRoot, ".${snapshotId}.staging-")
        } catch (error: Exception) {
            return rejected(
                MidiCorePackageExportProblemCode.STAGING_FAILED,
                "The export staging directory could not be created.",
                "Check project-folder permissions and retry the export.",
            )
        }
        val stagedFiles = midiFileSpecs(assembled.song, request.enabledRoles)
        val validations: List<MidiCoreExportedPackageFile>
        val manifestBytes: ByteArray
        try {
            stagedFiles.forEach { spec ->
                val path = staged.resolve(spec.filename)
                when (spec.kind) {
                    ExportedFileKind.COMPLETE_SONG -> writer.writeComplete(assembled.song, path)
                    ExportedFileKind.MELODY -> writer.writeRole(assembled.song, MidiExportRole.MELODY, path)
                    ExportedFileKind.CHORDS -> writer.writeRole(assembled.song, MidiExportRole.CHORDS, path)
                    ExportedFileKind.BASS -> writer.writeRole(assembled.song, MidiExportRole.BASS, path)
                    ExportedFileKind.DRUMS -> writer.writeRole(assembled.song, MidiExportRole.DRUMS, path)
                    ExportedFileKind.MANIFEST -> error("Manifest is written after MIDI validation")
                }
            }
            validations = stagedFiles.map { spec ->
                val path = staged.resolve(spec.filename)
                val validation = validateReimport(assembled.song, spec, path)
                MidiCoreExportedPackageFile(spec.kind, spec.filename, sha256(path), validation)
            }
            manifestBytes = manifest(
                project = current,
                review = assembled,
                snapshotId = snapshotId,
                createdAt = createdAt,
                enabledRoles = request.enabledRoles,
                files = validations,
            )
            validateManifest(manifestBytes, snapshotId, validations)
            Files.write(
                staged.resolve(MANIFEST_FILENAME),
                manifestBytes,
                StandardOpenOption.CREATE_NEW,
            )
            beforePublish(staged)
            validations.forEach { file ->
                require(sha256(staged.resolve(file.filename)) == file.sha256) {
                    "Generated MIDI digest changed before export publication"
                }
            }
            require(sha256(staged.resolve(MANIFEST_FILENAME)) == sha256Bytes(manifestBytes)) {
                "Generated manifest digest changed before export publication"
            }
            publishDirectory(staged, destination)
        } catch (error: SemanticExportValidationException) {
            deleteTree(staged)
            return rejected(
                MidiCorePackageExportProblemCode.SEMANTIC_VALIDATION_FAILED,
                error.message ?: "Generated MIDI failed semantic re-import.",
                "Regenerate or repair the accepted MIDI evidence before exporting.",
            )
        } catch (error: Exception) {
            deleteTree(staged)
            return rejected(
                MidiCorePackageExportProblemCode.STAGING_FAILED,
                error.message ?: "The MIDI package could not be staged safely.",
                "Retry the export; no incomplete package was published.",
            )
        }

        val exportedFiles = validations
        val manifestSha256 = try {
            sha256(destination.resolve(MANIFEST_FILENAME))
        } catch (error: Exception) {
            removeUnboundDestination(root, destination, snapshotId)
            return rejected(
                MidiCorePackageExportProblemCode.IO_FAILURE,
                "The published MIDI package could not be verified.",
                "Retry the export; an unbound incomplete package was removed when possible.",
            )
        }
        val snapshotFiles = (exportedFiles.map { file ->
            ExportedSnapshotFile(
                file.kind,
                ProjectArtifact(MidiCoreArtifactStore.exportFilePath(snapshotId, file.kind), file.sha256),
            )
        } + ExportedSnapshotFile(
            ExportedFileKind.MANIFEST,
            ProjectArtifact(MidiCoreArtifactStore.exportFilePath(snapshotId, ExportedFileKind.MANIFEST), manifestSha256),
        )).sortedBy(ExportedSnapshotFile::kind)
        val capture = try {
            snapshotLifecycle.capture(
                CaptureMidiCoreExportSnapshot(
                    session = MidiCoreProjectSession(root, current),
                    files = snapshotFiles,
                    snapshotId = snapshotId,
                    enabledRoles = request.enabledRoles,
                    createdAt = createdAt,
                ),
            )
        } catch (error: Exception) {
            removeUnboundDestination(root, destination, snapshotId)
            return rejected(
                MidiCorePackageExportProblemCode.SAVE_FAILED,
                "The export snapshot could not be recorded in project state.",
                "Retry the export; the prior project state remains authoritative.",
            )
        }
        return when (capture) {
            is MidiCoreExportSnapshotLifecycleResult.Captured -> MidiCoreMidiPackageExportResult.Exported(
                MidiCoreExportedPackage(capture.session, capture.snapshot, destination, exportedFiles, manifestSha256),
            )
            is MidiCoreExportSnapshotLifecycleResult.Rejected -> {
                removeUnboundDestination(root, destination, snapshotId)
                MidiCoreMidiPackageExportResult.Rejected(mapSnapshotProblem(capture.problem))
            }
        }
    }

    private fun midiFileSpecs(song: MidiExportSong, enabledRoles: Set<CandidateRole>): List<MidiFileSpec> = buildList {
        add(MidiFileSpec(ExportedFileKind.COMPLETE_SONG, "complete-song.mid", song.roles))
        add(MidiFileSpec(ExportedFileKind.MELODY, "melody.mid", listOf(song.role(MidiExportRole.MELODY))))
        enabledRoles.sortedBy(CandidateRole::ordinal).forEach { role ->
            val midiRole = exportRole(role)
            add(MidiFileSpec(ExportedFileKind.valueOf(role.name), "${role.name.lowercase()}.mid", listOf(song.role(midiRole))))
        }
        sortBy(MidiFileSpec::kind)
    }

    private fun validateReimport(song: MidiExportSong, spec: MidiFileSpec, path: Path): MidiCoreMidiFileValidation {
        val inspection = try {
            reader.inspect(path)
        } catch (error: Exception) {
            throw SemanticExportValidationException("Generated MIDI could not be semantically re-imported: ${spec.filename}", error)
        }
        val expectedNames = listOf("Conductor") + spec.roles.map { it.role.trackName }
        semanticRequire(inspection.sequence.source.format == 1, spec, "format is not SMF 1")
        semanticRequire(inspection.sequence.source.ppq.value == song.ppq.value, spec, "PPQ differs from the assembled song")
        semanticRequire(inspection.sourceEndTick == song.songEndTick, spec, "song end boundary differs from the assembled song")
        semanticRequire(inspection.sequence.tracks.size == expectedNames.size, spec, "track count differs")
        semanticRequire(inspection.trackSummaries.map { it.name } == expectedNames, spec, "track names or order differ")
        semanticRequire(
            portableFacts(inspection.sequence.tracks.first().events) == conductorFacts(song),
            spec,
            "conductor metadata or marker positions differ",
        )
        spec.roles.forEachIndexed { index, roleTrack ->
            val actual = portableFacts(inspection.sequence.tracks[index + 1].events)
            val expected = listOf("name:0:${roleTrack.role.trackName}") + roleTrack.events.map { event ->
                portableFact(event, roleTrack.role.channel)
            }
            semanticRequire(actual == expected, spec, "${roleTrack.role.trackName} semantic events differ")
        }
        return MidiCoreMidiFileValidation(
            format = inspection.sequence.source.format,
            ppq = inspection.sequence.source.ppq.value,
            trackNames = expectedNames,
            songEndTick = inspection.sourceEndTick,
            noteCount = inspection.sequence.tracks.drop(1).sumOf { track -> track.events.count { it is MidiNoteEvent } },
        )
    }

    private fun semanticRequire(condition: Boolean, spec: MidiFileSpec, message: String) {
        if (!condition) throw SemanticExportValidationException("${spec.filename}: $message")
    }

    private fun portableFacts(events: List<SemanticMidiEvent>): List<String> = events.map { event -> portableFact(event) }

    private fun portableFact(event: SemanticMidiEvent, channelOverride: Int? = null): String = when (event) {
        is MidiNoteEvent -> "note:${event.orderingKey.tick}:${event.endTick}:${channelOverride ?: event.channel}:${event.pitch}:${event.velocity}:${event.releaseVelocity ?: 0}"
        is MidiControlChangeEvent -> "cc:${event.orderingKey.tick}:${channelOverride ?: event.channel}:${event.controller}:${event.value}"
        is MidiPitchBendEvent -> "bend:${event.orderingKey.tick}:${channelOverride ?: event.channel}:${event.value}"
        is MidiChannelPressureEvent -> "pressure:${event.orderingKey.tick}:${channelOverride ?: event.channel}:${event.pressure}"
        is MidiTempoEvent -> "tempo:${event.orderingKey.tick}:${event.microsecondsPerQuarter}"
        is MidiTimeSignatureEvent -> "meter:${event.orderingKey.tick}:${event.numerator}:${event.denominatorExponent}:${event.clocksPerMetronome}:${event.thirtySecondNotesPerQuarter}"
        is MidiTrackNameEvent -> "name:${event.orderingKey.tick}:${event.name}"
        is MidiMarkerEvent -> "marker:${event.orderingKey.tick}:${event.marker}"
        is MidiTextEvent -> "text:${event.orderingKey.tick}:${event.textKind}:${event.text}"
        else -> "unsupported:${event.orderingKey.tick}:${event.kind}"
    }

    private fun conductorFacts(song: MidiExportSong): List<String> = buildList {
        add(ConductorFact(0, 10, "tempo:0:${song.tempoMicrosecondsPerQuarter}"))
        add(ConductorFact(0, 20, "meter:0:${song.meterNumerator}:${song.meterDenominatorExponent}:24:8"))
        add(ConductorFact(0, 30, "name:0:Conductor"))
        song.markers.forEach { marker -> add(ConductorFact(marker.tick, 40, "marker:${marker.tick}:${marker.renderedLabel()}")) }
        add(ConductorFact(0, 50, "text:0:SEQUENCE_NAME:${song.sequenceName}"))
    }.sortedWith(compareBy(ConductorFact::tick, ConductorFact::priority)).map(ConductorFact::fact)

    private fun manifest(
        project: MidiCoreProject,
        review: MidiCoreAcceptedSongReview,
        snapshotId: String,
        createdAt: String,
        enabledRoles: Set<CandidateRole>,
        files: List<MidiCoreExportedPackageFile>,
    ): ByteArray {
        val authority = requireNotNull(project.authority)
        val candidates = project.candidates.associateBy(MidiCoreCandidate::id)
        val accepted = review.acceptedCandidates.sortedWith(
            compareBy<MidiCoreAcceptedSongCandidate> { item ->
                authority.occurrences.single { it.id == item.occurrenceId }.startTick
            }.thenBy { it.role.ordinal },
        ).map { acceptedCandidate ->
            val candidate = requireNotNull(candidates[acceptedCandidate.candidateId])
            ManifestAcceptedCandidate(
                occurrenceId = acceptedCandidate.occurrenceId,
                role = acceptedCandidate.role.name.lowercase(),
                candidateId = candidate.id,
                midiSha256 = candidate.midi.sha256,
                validationReportSha256 = candidate.validationReport.sha256,
                generatorVersion = candidate.generatorVersion,
                profileId = candidate.profileId,
                patternId = candidate.patternId,
                seed = candidate.seed,
            )
        }
        val roleManifests = listOf(MidiExportRole.MELODY) + enabledRoles.sortedBy(CandidateRole::ordinal).map(::exportRole)
        val roles = MidiExportRole.entries.map { role ->
            val roleAccepted = accepted.filter { it.role.equals(role.name.lowercase(), ignoreCase = true) }
            ManifestRole(
                role = role.name.lowercase(),
                enabled = role in roleManifests,
                optional = role != MidiExportRole.MELODY,
                acceptedCandidateIds = roleAccepted.map(ManifestAcceptedCandidate::candidateId),
                acceptedCandidates = roleAccepted,
                performanceProfileIds = roleAccepted.map(ManifestAcceptedCandidate::profileId).distinct().sorted(),
                instrumentSuggestion = instrumentSuggestion(role),
            )
        }
        val manifest = MidiCoreExportManifest(
            schema = MANIFEST_SCHEMA,
            manifestSchemaVersion = 1,
            projectId = project.id.value,
            snapshotId = snapshotId,
            exportTimestamp = createdAt,
            applicationVersion = project.metadata.applicationVersion,
            buildIdentity = "melotrail-midi-core",
            source = ManifestSource(
                filename = portableSourceFilename(requireNotNull(project.sourceMidi).originalFilename),
                sha256 = review.sourceSha256,
                format = requireNotNull(project.sourceMidi).format,
            ),
            selectedMelody = ManifestSelectedMelody(
                trackIndex = requireNotNull(project.selectedMelody).trackIndex,
                channel = requireNotNull(project.selectedMelody).channel + 1,
                identitySha256 = review.selectedMelodyIdentitySha256,
            ),
            authority = ManifestAuthority(
                ppq = review.song.ppq.value,
                tempoMicrosecondsPerQuarter = authority.tempo.microsecondsPerQuarter,
                meterNumerator = authority.meter.numerator,
                meterDenominatorExponent = authority.meter.denominatorExponent,
                keyTonic = authority.key.tonic,
                mode = authority.key.modeId,
                keySpelling = authority.key.spelling.symbol,
                pickupTicks = authority.pickupTicks,
                sections = authority.occurrences.map { occurrence ->
                    val definition = authority.sectionDefinitions.single { it.id == occurrence.definitionId }
                    ManifestSection(occurrence.id, definition.id, definition.name, occurrence.label, occurrence.startTick, occurrence.endTick)
                },
                chordEvents = authority.chordEvents.map(::manifestChord),
            ),
            roles = roles,
            acceptedCandidates = accepted,
            generatedFiles = files.sortedBy(MidiCoreExportedPackageFile::kind).map { file ->
                ManifestGeneratedFile(
                    filename = file.filename,
                    kind = file.kind.name.lowercase(),
                    sha256 = file.sha256,
                    validation = ManifestFileValidation(
                        status = "passed",
                        format = file.validation.format,
                        ppq = file.validation.ppq,
                        trackNames = file.validation.trackNames,
                        songEndTick = file.validation.songEndTick,
                        noteCount = file.validation.noteCount,
                    ),
                )
            },
            validation = ManifestValidation(
                status = "passed",
                semanticReimportedMidiFiles = files.size,
                allMIDIFilesPassed = true,
            ),
        )
        return MANIFEST_JSON.encodeToString(manifest).toByteArray(StandardCharsets.UTF_8)
    }

    /** Re-read the manifest before publication so a malformed or non-portable package remains only staged. */
    private fun validateManifest(
        bytes: ByteArray,
        snapshotId: String,
        files: List<MidiCoreExportedPackageFile>,
    ) {
        val manifest = try {
            MANIFEST_JSON.decodeFromString<MidiCoreExportManifest>(bytes.toString(StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw SemanticExportValidationException("Generated export manifest is not valid JSON.", error)
        }
        val orderedFiles = files.sortedBy(MidiCoreExportedPackageFile::kind)
        semanticManifestRequire(manifest.schema == MANIFEST_SCHEMA, "schema identifier differs")
        semanticManifestRequire(manifest.manifestSchemaVersion == 1, "schema version differs")
        semanticManifestRequire(manifest.snapshotId == snapshotId, "snapshot identifier differs")
        semanticManifestRequire(isPortableFilename(manifest.source.filename), "source filename is not portable")
        semanticManifestRequire(
            manifest.generatedFiles.map(ManifestGeneratedFile::filename) == orderedFiles.map(MidiCoreExportedPackageFile::filename),
            "generated MIDI filenames differ",
        )
        semanticManifestRequire(
            manifest.generatedFiles.map(ManifestGeneratedFile::sha256) == orderedFiles.map(MidiCoreExportedPackageFile::sha256),
            "generated MIDI digests differ",
        )
        semanticManifestRequire(
            manifest.validation.semanticReimportedMidiFiles == orderedFiles.size && manifest.validation.allMIDIFilesPassed,
            "semantic validation summary differs",
        )
    }

    private fun semanticManifestRequire(condition: Boolean, message: String) {
        if (!condition) throw SemanticExportValidationException("manifest.json: $message")
    }

    private fun manifestChord(event: AuthoritativeChordEvent) = ManifestChordEvent(
        id = event.id,
        occurrenceId = event.occurrenceId,
        symbol = event.symbol,
        startTick = event.startTick,
        endTick = event.endTick,
    )

    private fun instrumentSuggestion(role: MidiExportRole): ManifestInstrumentSuggestion = when (role) {
        MidiExportRole.MELODY -> ManifestInstrumentSuggestion("Lead melody", "lead, flute, or vocal guide", "Preserve the source melody register")
        MidiExportRole.CHORDS -> ManifestInstrumentSuggestion("Keys or electric piano", "electric piano, keys, or soft pad", "Use a chord voicing that leaves the melody clear")
        MidiExportRole.BASS -> ManifestInstrumentSuggestion("Electric or acoustic bass", "bass guitar, sub bass, or upright bass", "Keep the low register controlled")
        MidiExportRole.DRUMS -> ManifestInstrumentSuggestion("GM drum kit", "dusty acoustic kit or electronic kit", "Use General MIDI drum mapping on channel 10")
    }

    private fun portableSourceFilename(filename: String): String = filename
        .replace('\\', '/')
        .substringAfterLast('/')
        .takeIf(::isPortableFilename)
        ?: "source.mid"

    private fun isPortableFilename(filename: String): Boolean =
        filename.isNotBlank() && filename != "." && filename != ".." && '/' !in filename && '\\' !in filename

    private fun prepareExportsRoot(root: Path): Path {
        val rootReal = root.toRealPath()
        val exportsRoot = root.resolve("exports").normalize()
        require(exportsRoot.startsWith(root)) { "Export directory escapes the project root" }
        if (Files.exists(exportsRoot, LinkOption.NOFOLLOW_LINKS)) {
            require(!Files.isSymbolicLink(exportsRoot) && Files.isDirectory(exportsRoot, LinkOption.NOFOLLOW_LINKS)) {
                "Project exports path is not a directory"
            }
        } else {
            Files.createDirectory(exportsRoot)
        }
        require(exportsRoot.toRealPath().startsWith(rootReal)) { "Project exports path escapes the project root" }
        return exportsRoot
    }

    private fun publishDirectory(staging: Path, destination: Path) {
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staging, destination)
        }
    }

    private fun removeUnboundDestination(root: Path, destination: Path, snapshotId: String) {
        val bound = runCatching { artifacts.openProject(root).exportSnapshots.any { it.id == snapshotId } }.getOrDefault(false)
        if (!bound && destination.startsWith(root) && Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            runCatching { deleteTree(destination) }
        }
    }

    private fun deleteTree(root: Path) {
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun sha256(path: Path): String = sha256Bytes(Files.readAllBytes(path))

    private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun rejected(
        code: MidiCorePackageExportProblemCode,
        message: String,
        nextAction: String,
        occurrenceId: String? = null,
        role: CandidateRole? = null,
        candidateId: String? = null,
    ) = MidiCoreMidiPackageExportResult.Rejected(MidiCorePackageExportProblem(code, message, nextAction, occurrenceId, role, candidateId))

    private fun rejected(problem: MidiCoreSongAssemblyProblem) = rejected(
        code = when (problem.code) {
            MidiCoreSongAssemblyProblemCode.REVISION_CONFLICT -> MidiCorePackageExportProblemCode.REVISION_CONFLICT
            MidiCoreSongAssemblyProblemCode.STALE_PROJECT -> MidiCorePackageExportProblemCode.STALE_PROJECT
            MidiCoreSongAssemblyProblemCode.SOURCE_REQUIRED -> MidiCorePackageExportProblemCode.SOURCE_REQUIRED
            MidiCoreSongAssemblyProblemCode.MELODY_REQUIRED -> MidiCorePackageExportProblemCode.MELODY_REQUIRED
            MidiCoreSongAssemblyProblemCode.AUTHORITY_REQUIRED -> MidiCorePackageExportProblemCode.AUTHORITY_REQUIRED
            MidiCoreSongAssemblyProblemCode.MISSING_ACCEPTANCE -> MidiCorePackageExportProblemCode.MISSING_ACCEPTANCE
            MidiCoreSongAssemblyProblemCode.CANDIDATE_STALE -> MidiCorePackageExportProblemCode.CANDIDATE_STALE
            MidiCoreSongAssemblyProblemCode.CANDIDATE_NOT_ACCEPTED -> MidiCorePackageExportProblemCode.CANDIDATE_NOT_ACCEPTED
            MidiCoreSongAssemblyProblemCode.DIGEST_MISMATCH,
            MidiCoreSongAssemblyProblemCode.SOURCE_DIGEST_MISMATCH,
            MidiCoreSongAssemblyProblemCode.MELODY_IDENTITY_MISMATCH,
            -> MidiCorePackageExportProblemCode.DIGEST_MISMATCH
            MidiCoreSongAssemblyProblemCode.VALIDATION_FAILED -> MidiCorePackageExportProblemCode.VALIDATION_FAILED
            MidiCoreSongAssemblyProblemCode.CANDIDATE_FORMAT_MISMATCH -> MidiCorePackageExportProblemCode.CANDIDATE_FORMAT_MISMATCH
            MidiCoreSongAssemblyProblemCode.CANDIDATE_CHANNEL_MISMATCH -> MidiCorePackageExportProblemCode.CANDIDATE_CHANNEL_MISMATCH
            MidiCoreSongAssemblyProblemCode.INVALID_ROLE_EVENT -> MidiCorePackageExportProblemCode.VALIDATION_FAILED
            MidiCoreSongAssemblyProblemCode.CANDIDATE_OVERFLOW,
            MidiCoreSongAssemblyProblemCode.MELODY_OVERFLOW,
            MidiCoreSongAssemblyProblemCode.SONG_OVERFLOW,
            -> MidiCorePackageExportProblemCode.CANDIDATE_OVERFLOW
            MidiCoreSongAssemblyProblemCode.CANDIDATE_SCOPE_MISMATCH,
            MidiCoreSongAssemblyProblemCode.DUPLICATE_ROLE_SCOPE,
            -> MidiCorePackageExportProblemCode.EXPORT_NOT_READY
            MidiCoreSongAssemblyProblemCode.INVALID_PROJECT -> MidiCorePackageExportProblemCode.INVALID_PROJECT
        },
        message = problem.message,
        nextAction = problem.nextAction,
        occurrenceId = problem.occurrenceId,
        role = problem.role,
        candidateId = problem.candidateId,
    )

    private fun mapSnapshotProblem(problem: MidiCoreExportSnapshotProblem) = MidiCorePackageExportProblem(
        code = when (problem.code) {
            MidiCoreExportSnapshotProblemCode.INVALID_PROJECT -> MidiCorePackageExportProblemCode.INVALID_PROJECT
            MidiCoreExportSnapshotProblemCode.STALE_PROJECT -> MidiCorePackageExportProblemCode.STALE_PROJECT
            MidiCoreExportSnapshotProblemCode.AUTHORITY_REQUIRED -> MidiCorePackageExportProblemCode.AUTHORITY_REQUIRED
            MidiCoreExportSnapshotProblemCode.INVALID_SNAPSHOT -> MidiCorePackageExportProblemCode.EXPORT_NOT_READY
            MidiCoreExportSnapshotProblemCode.EXPORT_NOT_READY,
            MidiCoreExportSnapshotProblemCode.STALE_SNAPSHOT,
            -> MidiCorePackageExportProblemCode.EXPORT_NOT_READY
            MidiCoreExportSnapshotProblemCode.SNAPSHOT_ID_COLLISION -> MidiCorePackageExportProblemCode.SNAPSHOT_ID_COLLISION
            MidiCoreExportSnapshotProblemCode.ARTIFACT_COLLISION -> MidiCorePackageExportProblemCode.ARTIFACT_COLLISION
            MidiCoreExportSnapshotProblemCode.SAVE_FAILED -> MidiCorePackageExportProblemCode.SAVE_FAILED
        },
        message = problem.message,
        nextAction = problem.nextAction,
    )

    private fun exportRole(role: CandidateRole): MidiExportRole = when (role) {
        CandidateRole.CHORDS -> MidiExportRole.CHORDS
        CandidateRole.BASS -> MidiExportRole.BASS
        CandidateRole.DRUMS -> MidiExportRole.DRUMS
    }

    private data class MidiFileSpec(
        val kind: ExportedFileKind,
        val filename: String,
        val roles: List<MidiExportRoleTrack>,
    )

    private data class ConductorFact(val tick: Long, val priority: Int, val fact: String)

    private class SemanticExportValidationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

    private companion object {
        const val MANIFEST_FILENAME = "manifest.json"
        const val MANIFEST_SCHEMA = "melotrail-midi-export"
        val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,119}")
        val MANIFEST_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}

@Serializable
private data class MidiCoreExportManifest(
    val schema: String,
    val manifestSchemaVersion: Int,
    val projectId: String,
    val snapshotId: String,
    val exportTimestamp: String,
    val applicationVersion: String? = null,
    val buildIdentity: String,
    val source: ManifestSource,
    val selectedMelody: ManifestSelectedMelody,
    val authority: ManifestAuthority,
    val roles: List<ManifestRole>,
    val acceptedCandidates: List<ManifestAcceptedCandidate>,
    val generatedFiles: List<ManifestGeneratedFile>,
    val validation: ManifestValidation,
)

@Serializable
private data class ManifestSource(val filename: String, val sha256: String, val format: Int)

@Serializable
private data class ManifestSelectedMelody(val trackIndex: Int, val channel: Int, val identitySha256: String)

@Serializable
private data class ManifestAuthority(
    val ppq: Int,
    val tempoMicrosecondsPerQuarter: Int,
    val meterNumerator: Int,
    val meterDenominatorExponent: Int,
    val keyTonic: Int,
    val mode: String,
    val keySpelling: String,
    val pickupTicks: Long,
    val sections: List<ManifestSection>,
    val chordEvents: List<ManifestChordEvent>,
)

@Serializable
private data class ManifestSection(
    val occurrenceId: String,
    val definitionId: String,
    val definitionName: String,
    val label: String,
    val startTick: Long,
    val endTick: Long,
)

@Serializable
private data class ManifestChordEvent(
    val id: String,
    val occurrenceId: String,
    val symbol: String,
    val startTick: Long,
    val endTick: Long,
)

@Serializable
private data class ManifestRole(
    val role: String,
    val enabled: Boolean,
    val optional: Boolean,
    val acceptedCandidateIds: List<String>,
    val acceptedCandidates: List<ManifestAcceptedCandidate>,
    val performanceProfileIds: List<String>,
    val instrumentSuggestion: ManifestInstrumentSuggestion,
)

@Serializable
private data class ManifestInstrumentSuggestion(
    val category: String,
    val dawSearchSuggestion: String,
    val registerNotes: String,
)

@Serializable
private data class ManifestAcceptedCandidate(
    val occurrenceId: String,
    val role: String,
    val candidateId: String,
    val midiSha256: String,
    val validationReportSha256: String,
    val generatorVersion: String,
    val profileId: String,
    val patternId: String,
    val seed: Long,
)

@Serializable
private data class ManifestGeneratedFile(
    val filename: String,
    val kind: String,
    val sha256: String,
    val validation: ManifestFileValidation,
)

@Serializable
private data class ManifestFileValidation(
    val status: String,
    val format: Int,
    val ppq: Int,
    val trackNames: List<String>,
    val songEndTick: Long,
    val noteCount: Int,
)

@Serializable
private data class ManifestValidation(
    val status: String,
    val semanticReimportedMidiFiles: Int,
    val allMIDIFilesPassed: Boolean,
)

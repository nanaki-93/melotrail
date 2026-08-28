package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreAcceptedDependencyContext
import app.melotrail.arrangement.core.MidiCoreBassGenerator
import app.melotrail.arrangement.core.MidiCoreCandidateEvent
import app.melotrail.arrangement.core.MidiCoreChordGenerator
import app.melotrail.arrangement.core.MidiCoreDrumGenerator
import app.melotrail.arrangement.core.MidiCoreGenerationContext
import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.arrangement.core.MidiCoreRoleValidationReportJson
import app.melotrail.arrangement.core.MidiCoreRoleValidationResult
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.adapter.JdkMidiWriter
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiMelodySelection
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.MidiProtectedMelodySelector
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptedDependency
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Cooperative cancellation independent of the coroutine Job, useful for UI cancel buttons and tests. */
fun interface MidiCoreGenerationCancellation {
    fun isCancelled(): Boolean

    companion object {
        val NONE = MidiCoreGenerationCancellation { false }
    }
}

/** Small testable lifecycle hooks; all generation hooks run away from the Compose event thread. */
data class MidiCoreGenerationHooks(
    val beforeContext: suspend () -> Unit = {},
    val afterCandidate: suspend () -> Unit = {},
    val afterMidiWritten: suspend () -> Unit = {},
    val afterArtifactsPublished: () -> Unit = {},
)

/** One scoped request for one deterministic role alternative. */
data class GenerateMidiCoreCandidate(
    val session: MidiCoreProjectSession,
    val role: CandidateRole,
    val occurrenceId: String,
    val performanceProfileId: String,
    val patternId: String,
    val generator: MidiCoreGeneratorInput,
    val sectionPolicy: app.melotrail.arrangement.core.MidiCoreSectionPolicy =
        app.melotrail.arrangement.core.MidiCoreSectionPolicy(),
    val candidateId: String? = null,
    val cancellation: MidiCoreGenerationCancellation = MidiCoreGenerationCancellation.NONE,
    val hooks: MidiCoreGenerationHooks = MidiCoreGenerationHooks(),
)

/** Result of one scoped generation/publication attempt. */
sealed interface MidiCoreCandidateGenerationResult {
    data class Published(
        val session: MidiCoreProjectSession,
        val candidate: MidiCoreCandidate,
        val context: MidiCoreGenerationContext,
        val validation: MidiCoreRoleValidationReport,
    ) : MidiCoreCandidateGenerationResult

    data class ValidationRejected(
        val context: MidiCoreGenerationContext,
        val validation: MidiCoreRoleValidationReport,
    ) : MidiCoreCandidateGenerationResult

    data class Cancelled(
        val context: MidiCoreGenerationContext?,
        val candidateId: String?,
        val publishedArtifacts: List<ProjectArtifact>,
    ) : MidiCoreCandidateGenerationResult

    data class Rejected(
        val problem: MidiCoreCandidateProblem,
        val context: MidiCoreGenerationContext? = null,
        val publishedCandidate: MidiCoreCandidate? = null,
    ) : MidiCoreCandidateGenerationResult
}

/**
 * Application use case for one deterministic role/occurrence alternative.
 * Pure generation is dispatched to [dispatcher]; only the final candidate
 * publication is serialized by [MidiCoreCandidateLifecycle].
 */
class MidiCoreCandidateGeneration(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val writer: JdkMidiWriter = JdkMidiWriter(),
    private val lifecycle: MidiCoreCandidateLifecycle = MidiCoreCandidateLifecycle(artifacts = artifacts),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val candidateIdFactory: () -> String = { "candidate-${UUID.randomUUID()}" },
    private val maximumPublicationAttempts: Int = 4,
) {
    init {
        require(maximumPublicationAttempts in 1..16) { "Candidate publication attempts must be bounded" }
    }

    /** Generate and publish one candidate without blocking the caller's UI dispatcher. */
    suspend fun generate(request: GenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult =
        withContext(dispatcher) { generateOnWorker(request) }

    private suspend fun generateOnWorker(request: GenerateMidiCoreCandidate): MidiCoreCandidateGenerationResult {
        request.hooks.beforeContext()
        cancellationCheckpoint(request, null)?.let { return it }

        val loaded = when (val result = loadContext(request)) {
            is ContextLoad.Ready -> result
            is ContextLoad.Rejected -> return MidiCoreCandidateGenerationResult.Rejected(result.problem)
        }
        val context = loaded.context
        cancellationCheckpoint(request, context)?.let { return it }

        val generated = try {
            generateRole(context)
        } catch (error: IllegalArgumentException) {
            return MidiCoreCandidateGenerationResult.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.INVALID_CANDIDATE,
                    error.message ?: "The requested role alternative is invalid.",
                    "Choose a curated profile and pattern for the selected role and retry.",
                ),
                context,
            )
        }
        val validation = generated.validation
        if (validation is MidiCoreRoleValidationResult.Rejected) {
            return MidiCoreCandidateGenerationResult.ValidationRejected(context, validation.report)
        }
        request.hooks.afterCandidate()
        cancellationCheckpoint(request, context)?.let { return it }

        val temporaryDirectory = try {
            Files.createTempDirectory(loaded.root, ".midi-core-generation-")
        } catch (error: Exception) {
            return MidiCoreCandidateGenerationResult.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.ARTIFACT_FAILURE,
                    "A temporary candidate workspace could not be created.",
                    "Check project permissions and retry candidate generation.",
                ),
                context,
            )
        }
        try {
            val temporaryMidi = temporaryDirectory.resolve("candidate.mid")
            try {
                writer.writeRole(exportSong(context, generated.candidate), exportRole(context.role), temporaryMidi)
                verifyCandidateMidi(context, generated.candidate, temporaryMidi)
            } catch (error: Exception) {
                return MidiCoreCandidateGenerationResult.Rejected(
                    problem(
                        MidiCoreCandidateProblemCode.ARTIFACT_FAILURE,
                        "The generated candidate MIDI could not be written or re-imported safely.",
                        "Retry generation; no candidate was added to project state.",
                    ),
                    context,
                )
            }
            request.hooks.afterMidiWritten()
            cancellationCheckpoint(request, context)?.let { return it }

            val reportJson = MidiCoreRoleValidationReportJson.encode(validation.report)
            var attempt = 0
            while (attempt < maximumPublicationAttempts) {
                cancellationCheckpoint(request, context)?.let { return it }
                val candidateId = request.candidateId ?: try {
                    candidateIdFactory()
                } catch (error: Exception) {
                    return MidiCoreCandidateGenerationResult.Rejected(
                        problem(
                            MidiCoreCandidateProblemCode.INVALID_CANDIDATE,
                            "A stable candidate identifier could not be created.",
                            "Retry candidate generation with a valid identifier factory.",
                        ),
                        context,
                    )
                }
                var publishedCandidate: MidiCoreCandidate? = null
                val job = currentCoroutineContext()[Job]
                val publication = lifecycle.publish(
                    PublishMidiCoreCandidate(
                        session = request.session,
                        role = request.role,
                        occurrenceId = request.occurrenceId,
                        generatorVersion = context.generatorVersion,
                        authorityHash = context.authorityHash,
                        seed = context.seed,
                        midi = temporaryMidi,
                        validationReportJson = reportJson,
                        candidateId = candidateId,
                        profileId = context.performanceProfile.id,
                        patternId = context.patternId,
                        acceptedDependencyIds = context.acceptedDependencies.map { it.dependency.candidateId },
                        beforeProjectSave = { candidate ->
                            publishedCandidate = candidate
                            if (job?.isActive == false || request.cancellation.isCancelled()) return@PublishMidiCoreCandidate false
                            request.hooks.afterArtifactsPublished()
                            job?.isActive != false && !request.cancellation.isCancelled()
                        },
                    ),
                )
                when (publication) {
                    is MidiCoreCandidateLifecycleResult.Published -> {
                        return MidiCoreCandidateGenerationResult.Published(
                            publication.session,
                            publication.candidate,
                            context,
                            validation.report,
                        )
                    }

                    is MidiCoreCandidateLifecycleResult.Rejected -> {
                        if (publication.problem.code == MidiCoreCandidateProblemCode.CANCELLED) {
                            val candidate = publishedCandidate
                            return MidiCoreCandidateGenerationResult.Cancelled(
                                context,
                                candidate?.id ?: candidateId,
                                listOfNotNull(candidate?.midi, candidate?.validationReport),
                            )
                        }
                        val retryableCollision = request.candidateId == null &&
                            publication.problem.code in setOf(
                                MidiCoreCandidateProblemCode.CANDIDATE_ID_COLLISION,
                                MidiCoreCandidateProblemCode.ARTIFACT_COLLISION,
                            )
                        if (retryableCollision && attempt + 1 < maximumPublicationAttempts) {
                            attempt++
                            continue
                        }
                        return MidiCoreCandidateGenerationResult.Rejected(
                            publication.problem,
                            context,
                            publishedCandidate,
                        )
                    }

                    is MidiCoreCandidateLifecycleResult.Updated -> {
                        return MidiCoreCandidateGenerationResult.Rejected(
                            problem(
                                MidiCoreCandidateProblemCode.INVALID_STATE,
                                "Candidate publication returned an invalid lifecycle state.",
                                "Reopen the project and retry candidate generation.",
                            ),
                            context,
                        )
                    }
                }
            }
            return MidiCoreCandidateGenerationResult.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.ARTIFACT_COLLISION,
                    "Candidate publication could not find a collision-free identifier.",
                    "Retry generation with a new candidate identifier.",
                ),
                context,
            )
        } finally {
            deleteTree(temporaryDirectory)
        }
    }

    private fun loadContext(request: GenerateMidiCoreCandidate): ContextLoad {
        val root = request.session.root.toAbsolutePath().normalize()
        val project = try {
            artifacts.openProject(root)
        } catch (error: Exception) {
            return ContextLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.INVALID_PROJECT,
                    "The project cannot be verified before candidate generation.",
                    "Open a valid MIDI Core project and retry.",
                ),
            )
        }
        if (project != request.session.project) {
            return ContextLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.STALE_PROJECT,
                    "The project changed while this generation request was pending.",
                    "Reopen the project and regenerate the selected role and occurrence.",
                ),
            )
        }
        val source = project.sourceMidi ?: return ContextLoad.Rejected(
            problem(
                MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED,
                "An imported source MIDI is required before generation.",
                "Import and protect one source melody before generating candidates.",
            ),
        )
        val selectedMelody = project.selectedMelody ?: return ContextLoad.Rejected(
            problem(
                MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED,
                "A protected melody is required before generation.",
                "Select exactly one source track and channel before generating candidates.",
            ),
        )
        if (project.authority == null) return ContextLoad.Rejected(
            problem(
                MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED,
                "Confirmed tempo, meter, key, structure, and harmony are required before generation.",
                "Complete Structure & Harmony authority for the selected occurrence.",
            ),
        )

        val sequence = try {
            reader.inspect(artifacts.verify(root, source.original)).sequence
        } catch (error: Exception) {
            return ContextLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.INVALID_PROJECT,
                    "The preserved source MIDI cannot be inspected safely.",
                    "Restore the immutable source artifact and reopen the project.",
                ),
            )
        }
        if (sequence.source.sha256 != source.sha256 || sequence.source.ppq.value != source.ppq || sequence.source.format != source.format) {
            return ContextLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.INVALID_PROJECT,
                    "The preserved source MIDI no longer matches project identity.",
                    "Restore the original source artifact before generating candidates.",
                ),
            )
        }
        val protectedMelody = try {
            MidiProtectedMelodySelector().select(sequence, MidiMelodySelection(selectedMelody.trackIndex, selectedMelody.channel))
        } catch (error: Exception) {
            return ContextLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.INVALID_PROJECT,
                    "The protected melody view cannot be re-derived from the preserved source.",
                    "Restore the source or reselect a safely pairable melody track and channel.",
                ),
            )
        }
        if (protectedMelody.identitySha256 != selectedMelody.identitySha256) return ContextLoad.Rejected(
            problem(
                MidiCoreCandidateProblemCode.INVALID_PROJECT,
                "The selected melody identity no longer matches the preserved source.",
                "Reopen the project and select the protected melody again.",
            ),
        )

        val dependencies = when (val result = acceptedDependencies(project, root, request)) {
            is DependencyLoad.Ready -> result.dependencies
            is DependencyLoad.Rejected -> return ContextLoad.Rejected(result.problem)
        }
        val context = try {
            val profile = app.melotrail.arrangement.core.MidiCorePerformanceProfileCatalog.requireForRole(
                request.role,
                request.performanceProfileId,
            )
            MidiCoreGenerationContext.from(
                project = project,
                role = request.role,
                occurrenceId = request.occurrenceId,
                performanceProfile = profile,
                patternId = request.patternId,
                generator = request.generator,
                protectedMelody = protectedMelody,
                acceptedDependencies = dependencies,
                sectionPolicy = request.sectionPolicy,
            )
        } catch (error: IllegalArgumentException) {
            val code = if (error.message.orEmpty().contains("Generation requires") ||
                error.message.orEmpty().contains("authority", ignoreCase = true)
            ) MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED else MidiCoreCandidateProblemCode.INVALID_CANDIDATE
            return ContextLoad.Rejected(
                problem(
                    code,
                    error.message ?: "The candidate generation request is invalid.",
                    "Choose a valid occurrence, profile, pattern, and generator input and retry.",
                ),
            )
        }
        return ContextLoad.Ready(root, context)
    }

    private fun acceptedDependencies(
        project: MidiCoreProject,
        root: Path,
        request: GenerateMidiCoreCandidate,
    ): DependencyLoad {
        val roles = CandidateRole.entries.filter { it != request.role }
        val candidates = project.candidates.associateBy(MidiCoreCandidate::id)
        val dependencies = mutableListOf<MidiCoreAcceptedDependencyContext>()
        roles.forEach { role ->
            val acceptance = project.acceptances.singleOrNull {
                it.role == role && it.occurrenceId == request.occurrenceId
            } ?: return@forEach
            val candidate = candidates[acceptance.candidateId] ?: return DependencyLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.INVALID_STATE,
                    "The accepted $role dependency is missing from the project.",
                    "Repair the acceptance state or regenerate the dependency first.",
                ),
            )
            if (candidate.status != MidiCoreCandidateStatus.ACCEPTED) return DependencyLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.CANDIDATE_STALE,
                    "The accepted $role dependency is stale for the current authority.",
                    "Regenerate and accept the dependency before generating this role.",
                ),
            )
            val expectedHash = try {
                app.melotrail.project.MidiCoreAuthorityHasher.from(project).scopeHash(request.occurrenceId, role)
            } catch (error: IllegalArgumentException) {
                return DependencyLoad.Rejected(
                    problem(
                        MidiCoreCandidateProblemCode.AUTHORITY_REQUIRED,
                        "The accepted dependency has no current authority scope.",
                        "Complete authority for the selected occurrence and regenerate the dependency.",
                    ),
                )
            }
            if (candidate.authorityHash != expectedHash) return DependencyLoad.Rejected(
                problem(
                    MidiCoreCandidateProblemCode.CANDIDATE_STALE,
                    "The accepted $role dependency no longer matches current authority.",
                    "Regenerate and accept the dependency before generating this role.",
                ),
            )
            val notes = try {
                val inspected = reader.inspect(artifacts.verify(root, candidate.midi))
                require(inspected.sequence.source.ppq.value == project.sourceMidi?.ppq && inspected.sequence.source.format == 1) {
                    "Accepted dependency MIDI has incompatible format or PPQ"
                }
                val expectedChannel = midiChannel(role)
                inspected.sequence.tracks.flatMap { it.events }
                    .filterIsInstance<MidiNoteEvent>()
                    .filter { it.channel == expectedChannel }
                    .map { note ->
                        app.melotrail.arrangement.core.MidiCoreGenerationNote(
                            note.orderingKey.tick,
                            note.endTick,
                            note.pitch,
                            note.velocity.coerceAtLeast(1),
                        )
                    }
                    .sortedWith(compareBy({ it.startTick }, { it.endTick }, { it.pitch }, { it.velocity }))
            } catch (error: Exception) {
                return DependencyLoad.Rejected(
                    problem(
                        MidiCoreCandidateProblemCode.INVALID_STATE,
                        "The accepted $role dependency MIDI cannot be read safely.",
                        "Regenerate and accept a valid dependency candidate.",
                    ),
                )
            }
            dependencies += MidiCoreAcceptedDependencyContext(
                MidiCoreAcceptedDependency(role, request.occurrenceId, candidate.id, candidate.authorityHash),
                notes,
            )
        }
        return DependencyLoad.Ready(dependencies)
    }

    private fun generateRole(context: MidiCoreGenerationContext): GeneratedRole = when (context.role) {
        CandidateRole.CHORDS -> MidiCoreChordGenerator.generate(context).let { GeneratedRole(it.candidate, it.validation) }
        CandidateRole.BASS -> MidiCoreBassGenerator.generate(context).let { GeneratedRole(it.candidate, it.validation) }
        CandidateRole.DRUMS -> MidiCoreDrumGenerator.generate(context).let { GeneratedRole(it.candidate, it.validation) }
    }

    private fun exportSong(
        context: MidiCoreGenerationContext,
        candidate: app.melotrail.arrangement.core.MidiCoreRoleCandidate,
    ): MidiExportSong {
        val events = candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .sortedWith(compareBy<MidiCoreCandidateEvent.Note> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.velocity })
            .mapIndexed { index, note ->
                MidiNoteEvent(
                    MidiEventOrderingKey(note.startTick, MidiSemanticEventKind.NOTE, generatedEventKey = index.toLong()),
                    note.endTick,
                    candidate.channel,
                    note.pitch,
                    note.velocity,
                )
            }
        return MidiExportSong(
            ppq = context.authority.ppq,
            sequenceName = "Melotrail ${context.role.name.lowercase()} candidate",
            tempoMicrosecondsPerQuarter = context.authority.tempo.microsecondsPerQuarter,
            meterNumerator = context.authority.meter.numerator,
            meterDenominatorExponent = context.authority.meter.denominatorExponent,
            markers = listOf(MidiExportMarker(1, context.occurrence.label, context.occurrence.startTick)),
            roles = listOf(MidiExportRoleTrack(exportRole(context.role), events)),
            songEndTick = context.occurrence.endTick,
        )
    }

    private fun verifyCandidateMidi(
        context: MidiCoreGenerationContext,
        candidate: app.melotrail.arrangement.core.MidiCoreRoleCandidate,
        output: Path,
    ) {
        require(Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) { "Candidate MIDI output is missing" }
        val inspected = reader.inspect(output)
        require(inspected.sequence.source.format == 1 && inspected.sequence.source.ppq == context.authority.ppq) {
            "Candidate MIDI format or PPQ changed during writing"
        }
        require(inspected.sourceEndTick == context.occurrence.endTick) { "Candidate MIDI end boundary changed during writing" }
        require(inspected.trackSummaries.map { it.name } == listOf("Conductor", exportRole(context.role).trackName)) {
            "Candidate MIDI track names are not deterministic"
        }
        val actualNotes = inspected.sequence.tracks.drop(1).flatMap { track ->
            track.events.filterIsInstance<MidiNoteEvent>()
        }.map { note -> NoteFact(note.orderingKey.tick, note.endTick, note.channel, note.pitch, note.velocity) }
        val expectedNotes = candidate.events.filterIsInstance<MidiCoreCandidateEvent.Note>()
            .sortedWith(compareBy<MidiCoreCandidateEvent.Note> { it.startTick }.thenBy { it.endTick }.thenBy { it.pitch }.thenBy { it.velocity })
            .map { note -> NoteFact(note.startTick, note.endTick, midiChannel(context.role), note.pitch, note.velocity) }
        require(actualNotes == expectedNotes) { "Candidate MIDI semantic notes changed during writing" }
        val conductor = inspected.sequence.tracks.first().events
        require(conductor.filterIsInstance<app.melotrail.midi.domain.MidiTempoEvent>().map { it.microsecondsPerQuarter } ==
            listOf(context.authority.tempo.microsecondsPerQuarter)) { "Candidate tempo metadata changed" }
        require(conductor.filterIsInstance<app.melotrail.midi.domain.MidiTimeSignatureEvent>().map {
            it.numerator to it.denominatorExponent
        } == listOf(context.authority.meter.numerator to context.authority.meter.denominatorExponent)) {
            "Candidate meter metadata changed"
        }
    }

    private suspend fun cancellationCheckpoint(
        request: GenerateMidiCoreCandidate,
        context: MidiCoreGenerationContext?,
    ): MidiCoreCandidateGenerationResult.Cancelled? {
        try {
            currentCoroutineContext().ensureActive()
        } catch (error: CancellationException) {
            throw error
        }
        return if (request.cancellation.isCancelled()) {
            MidiCoreCandidateGenerationResult.Cancelled(context, null, emptyList())
        } else {
            null
        }
    }

    private fun exportRole(role: CandidateRole): MidiExportRole = when (role) {
        CandidateRole.CHORDS -> MidiExportRole.CHORDS
        CandidateRole.BASS -> MidiExportRole.BASS
        CandidateRole.DRUMS -> MidiExportRole.DRUMS
    }

    private fun midiChannel(role: CandidateRole): Int = exportRole(role).channel

    private fun problem(code: MidiCoreCandidateProblemCode, message: String, nextAction: String) =
        MidiCoreCandidateProblem(code, message, nextAction)

    private fun deleteTree(root: Path) {
        if (Files.notExists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
        }
    }

    private data class NoteFact(val startTick: Long, val endTick: Long, val channel: Int, val pitch: Int, val velocity: Int)

    private data class GeneratedRole(
        val candidate: app.melotrail.arrangement.core.MidiCoreRoleCandidate,
        val validation: MidiCoreRoleValidationResult,
    )

    private sealed interface ContextLoad {
        data class Ready(val root: Path, val context: MidiCoreGenerationContext) : ContextLoad
        data class Rejected(val problem: MidiCoreCandidateProblem) : ContextLoad
    }

    private sealed interface DependencyLoad {
        data class Ready(val dependencies: List<MidiCoreAcceptedDependencyContext>) : DependencyLoad
        data class Rejected(val problem: MidiCoreCandidateProblem) : DependencyLoad
    }
}

package app.melotrail.application

import app.melotrail.arrangement.BassMidiGenerationAdapter
import app.melotrail.arrangement.ArrangementApprovalReferences
import app.melotrail.arrangement.ArrangementAssignmentReference
import app.melotrail.arrangement.ArrangementRoleSelection
import app.melotrail.arrangement.ArrangementSoundContext
import app.melotrail.arrangement.ArrangementHarmonyContext
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.DetailedArrangementInput
import app.melotrail.arrangement.DetailedArrangementPlanner
import app.melotrail.arrangement.DetailedArrangementStore
import app.melotrail.arrangement.DeterministicDetailedArrangementPlanner
import app.melotrail.arrangement.DeterministicGlobalSongPlanner
import app.melotrail.arrangement.DeterministicSectionVariationPlanner
import app.melotrail.arrangement.DrumMidiGenerationAdapter
import app.melotrail.arrangement.GlobalSongPlanner
import app.melotrail.arrangement.GeneratedMidiArtifactReference
import app.melotrail.arrangement.GeneratedMidiArtifactPaths
import app.melotrail.arrangement.GeneratedMidiWorkflowReferences
import app.melotrail.arrangement.GeneratedRoleValidationInput
import app.melotrail.arrangement.GeneratedRoleValidators
import app.melotrail.arrangement.GeneratedRoleValidator
import app.melotrail.arrangement.RoleValidationReport
import app.melotrail.arrangement.InstrumentMode
import app.melotrail.arrangement.LocalQwenDetailedArrangementPlanner
import app.melotrail.arrangement.LocalQwenGlobalSongPlanner
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.LegacyLogicalInstrumentRoles
import app.melotrail.arrangement.MidiAnalysis
import app.melotrail.arrangement.MidiChord
import app.melotrail.arrangement.MidiTransitionGenerationAdapter
import app.melotrail.arrangement.TransitionMidiWindow
import app.melotrail.arrangement.OccurrenceMidiArtifactResolver
import app.melotrail.arrangement.PadMidiGenerationAdapter
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.arrangement.SectionInstance
import app.melotrail.arrangement.SectionVariationStore
import app.melotrail.arrangement.SongPlan
import app.melotrail.arrangement.SongPlanStore
import app.melotrail.arrangement.SongPlanningInput
import app.melotrail.arrangement.StemRenderResult
import app.melotrail.arrangement.StemRenderingMixer
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.LibraryProvenanceSnapshot
import app.melotrail.arrangement.ResolveInstrumentRequest
import app.melotrail.arrangement.StringsMidiGenerationAdapter
import app.melotrail.arrangement.ValidatedInstrumentDescriptor
import app.melotrail.arrangement.VersionedInstrumentResolver
import app.melotrail.arrangement.toSectionInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Planner choices are intentionally logical; the UI never supplies model endpoints or paths. */
enum class ArrangementPlannerKind { DETERMINISTIC, QWEN }

data class GenerateArrangementRequest(
    val root: Path,
    val planner: ArrangementPlannerKind = ArrangementPlannerKind.DETERMINISTIC,
    /** Compatibility-only input for legacy callers and persisted v1 plans. */
    @Deprecated("Use roleSelections")
    val style: String? = null,
    @Deprecated("Use roleSelections")
    val instruments: List<String> = LogicalInstrument.entries.map { it.wireName },
    val roleSelections: List<ArrangementRoleSelection> = emptyList()
)

data class ArrangementSectionSnapshot(
    val index: Int,
    val instanceId: String,
    val partId: String,
    val purpose: String,
    val energy: Double,
    val instruments: List<ArrangementInstrumentSnapshot>,
    val transition: String,
    val durationSeconds: Double?
)

data class ArrangementInstrumentSnapshot(
    val name: String,
    val mode: String,
    val role: String?,
    val density: Double?
)

data class ArrangementSnapshot(
    val root: Path,
    val sections: List<ArrangementSectionSnapshot>,
    val approvalRequired: Boolean,
    val approved: Boolean,
    val stale: Boolean,
    val artifact: Path
)

data class GeneratedMidiArtifact(val instrument: String, val path: Path, val events: Int)

data class GeneratedMidiSnapshot(val artifacts: List<GeneratedMidiArtifact>)

enum class ApplicationErrorCategory { PREREQUISITE, VALIDATION, WORKER, MODEL, RENDERER, ARTIFACT, IO }

class ApplicationServiceException(
    val category: ApplicationErrorCategory,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/**
 * File-backed arrangement use cases shared by CLI and the future desktop adapter.
 * It owns validation and artifact writes; callers only provide typed choices and a progress sink.
 */
interface ArrangementApplicationService {
    suspend fun generate(request: GenerateArrangementRequest, progress: ProgressSink = ProgressSink.None): ArrangementSnapshot
    suspend fun generateRequiredMidi(root: Path, progress: ProgressSink = ProgressSink.None): GeneratedMidiSnapshot
    suspend fun renderApprovedStems(root: Path, renderer: InstrumentRenderer, progress: ProgressSink = ProgressSink.None): StemRenderResult
    fun load(root: Path): ArrangementSnapshot
    fun preview(root: Path): ArrangementSnapshot
    fun approve(root: Path): ArrangementSnapshot
}

class DefaultArrangementApplicationService(
    private val deterministicGlobalPlanner: GlobalSongPlanner = DeterministicGlobalSongPlanner(),
    private val qwenGlobalPlanner: GlobalSongPlanner = LocalQwenGlobalSongPlanner(),
    private val deterministicDetailedPlanner: DetailedArrangementPlanner = DeterministicDetailedArrangementPlanner(),
    private val qwenDetailedPlanner: DetailedArrangementPlanner = LocalQwenDetailedArrangementPlanner(),
    private val libraryRoot: Path,
    private val musicalAuthorityBuilder: MusicalAuthorityBuilder = MusicalAuthorityBuilder(),
    private val sourceSongApplicationService: SourceSongApplicationService = SourceSongApplicationService(musicalAuthorityBuilder),
    private val generatedRoleValidator: GeneratedRoleValidator = GeneratedRoleValidators
) : ArrangementApplicationService {
    override suspend fun generate(request: GenerateArrangementRequest, progress: ProgressSink): ArrangementSnapshot = mutate(request.root) { root ->
        progress.report(OperationProgress("arrange", 1, 3, "Validating MIDI analyses"))
        val project = readProject(root)
        val harmony = HarmonyApplicationService().query(project)
        require(harmony.ready) {
            val incomplete = buildList {
                harmony.completeness.missingSections.forEach { add("${it.value} (missing)") }
                harmony.completeness.emptySections.forEach { add("${it.value} (empty)") }
                harmony.validationErrors.forEach { add(it.message) }
                harmony.replacementRequiredSections.forEach { add("${it.value} (choose a compatible progression)") }
            }.distinct()
            "Arrangement requires complete canonical harmony. Update Harmony for: ${incomplete.joinToString()}."
        }
        val projection = musicalAuthorityBuilder.arrangementGeneration(root)
        sourceSongApplicationService.assemble(root)
        val structure = project.envelope.structureOccurrences.mapIndexed { index, occurrence -> occurrence.toSectionInstance(index) }
        require(structure.isNotEmpty()) { "Song structure must not be empty" }
        val context = request.roleSelections.takeIf { it.isNotEmpty() }?.let { structuredContext(project) }
        val intents = request.roleSelections.map { it.bind(requireNotNull(context)) }
        require(request.roleSelections.map(ArrangementRoleSelection::role).distinct().size == request.roleSelections.size) {
            "Arrangement role selections must not contain duplicates"
        }
        val requestedInstruments = if (intents.isEmpty()) request.instruments else intents
            .map { LegacyLogicalInstrumentRoles.logicalFor(it.role) }.distinct().sortedBy { if (it == "piano") 0 else 1 }
        val allowed = requestedInstruments.distinct()
        if (intents.isEmpty()) require(allowed == request.instruments) { "Arrangement instruments must not contain duplicates" }
        require("piano" in allowed && allowed.all { it in LogicalInstrument.entries.map(LogicalInstrument::wireName) }) {
            "Arrangement instruments must be selected from piano, bass, drums, pad, and strings and include piano"
        }
        val analyses = canonicalMidiAnalyses(projection)
        // Role selections activate the structured planning protocol, which has
        // no legacy style-string field. Ignore a stale compatibility value from
        // callers rather than constructing an invalid mixed-mode request.
        val input = SongPlanningInput(
            projectName = project.name,
            projectVersion = project.version,
            analyses = analyses,
            structure = structure,
            allowedInstruments = allowed,
            style = request.style?.takeIf { intents.isEmpty() },
            soundContext = context,
            requestedIntents = intents,
            canonicalProjection = projection
        )
        input.requireValid()
        coroutineContext.ensureActive()

        progress.report(OperationProgress("arrange", 2, 3, "Creating reviewed song plan"))
        val global = if (request.planner == ArrangementPlannerKind.QWEN) qwenGlobalPlanner else deterministicGlobalPlanner
        val plan = global.plan(input)
        SongPlanStore.write(root, input, plan)
        SectionVariationStore.write(root, input, plan, DeterministicSectionVariationPlanner.plan(input, plan))
        coroutineContext.ensureActive()

        progress.report(OperationProgress("arrange", 3, 3, "Creating detailed arrangement"))
        val detailedInput = detailedInput(root, project)
        val detailed = if (request.planner == ArrangementPlannerKind.QWEN) qwenDetailedPlanner else deterministicDetailedPlanner
        val arrangement = detailed.plan(detailedInput)
        val artifact = if (request.planner == ArrangementPlannerKind.QWEN) {
            DetailedArrangementStore.writeDraft(root, detailedInput, arrangement)
        } else {
            DetailedArrangementStore.writeApproved(root, detailedInput, arrangement)
        }
        val approvedProject = if (request.planner == ArrangementPlannerKind.QWEN) project else projectWithResolvedAssignments(project, input)
        if (approvedProject != project) ProjectStore.write(root, approvedProject)
        ProjectWorkflowStore.update(root) { workflow ->
            workflow.invalidate(app.melotrail.arrangement.WorkflowChange.ARRANGEMENT)
                .markCurrent(WorkflowArtifact.ARRANGEMENT)
                .copy(arrangement = if (request.planner == ArrangementPlannerKind.QWEN) workflow.arrangement else approvalReferences(root, approvedProject, input))
        }
        snapshot(root, approvedProject, arrangement, artifact, request.planner == ArrangementPlannerKind.QWEN)
    }

    override suspend fun generateRequiredMidi(root: Path, progress: ProgressSink): GeneratedMidiSnapshot = mutate(root) { normalized ->
        val project = readProject(normalized)
        val projection = musicalAuthorityBuilder.arrangementGeneration(normalized)
        val input = detailedInput(normalized, project)
        val arrangement = readApproved(normalized, input)
        val analyses = canonicalMidiAnalyses(projection)
        val active = arrangement.sections.flatMap { it.instruments }.filter { it.mode == InstrumentMode.GENERATED }.map { it.name }.toSet()
        val needsTransitionMidi = arrangement.sections.any { it.transitionOut.type.name != "NONE" }
        val total = active.size + if (needsTransitionMidi) 1 else 0
        var stage = 0
        val artifacts = mutableListOf<GeneratedMidiArtifact>()
        fun emit(name: String, path: Path, events: Int) {
            artifacts += GeneratedMidiArtifact(name, path, events)
        }
        fun generating(name: String, path: Path) {
            stage++
            progress.report(OperationProgress("generate-midi", stage, total, "Generating $name MIDI", path))
        }
        if ("bass" in active) {
            val path = normalized.resolve("midi/generated/bass.mid"); generating("bass", path)
            BassMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("bass", it.path, it.notes.size) }
        }
        coroutineContext.ensureActive()
        if ("drums" in active) {
            val path = normalized.resolve("midi/generated/drums.mid"); generating("drums", path)
            DrumMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("drums", it.path, it.hits.size) }
        }
        coroutineContext.ensureActive()
        if ("pad" in active) {
            val path = normalized.resolve("midi/generated/pad.mid"); generating("pad", path)
            PadMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("pad", it.path, it.notes.size) }
        }
        coroutineContext.ensureActive()
        if ("strings" in active) {
            val path = normalized.resolve("midi/generated/strings.mid"); generating("strings", path)
            StringsMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("strings", it.path, it.notes.size) }
        }
        var transitionWindows = emptyList<TransitionMidiWindow>()
        var transitionTimelineEndTick: Long? = null
        if (needsTransitionMidi) {
            coroutineContext.ensureActive()
            val path = normalized.resolve("midi/generated/transitions.mid"); generating("transitions", path)
            MidiTransitionGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let {
                transitionWindows = it.validationWindows
                transitionTimelineEndTick = it.validationTimelineEndTick
                emit("transitions", it.path, it.result.events.size)
            }
        }
        val approval = requireNotNull(ProjectStore.read(normalized).workflow.arrangement) {
            "Generated MIDI requires approved arrangement lineage"
        }
        require(approval.authoritySha256 == projection.contextSha256) {
            "Approved arrangement is stale for the current musical authority. Regenerate Arrangement first."
        }
        val registrySha256 = registrySha256()
        require(approval.registrySha256 == registrySha256) {
            "Approved arrangement is stale for the current instrument registry. Regenerate Arrangement first."
        }
        val registry = InstrumentRegistryLoader(libraryRoot).load()
        val references = artifacts.map { artifact ->
            val relative = normalized.relativize(artifact.path.toAbsolutePath().normalize()).toString().replace('\\', '/')
            val report = generatedRoleValidator.validate(GeneratedRoleValidationInput(
                role = artifact.instrument, midi = artifact.path, project = project, arrangement = arrangement,
                projection = projection, registry = registry, arrangementSha256 = approval.arrangement.sha256, registrySha256 = registrySha256,
                transitionWindows = if (artifact.instrument == "transitions") transitionWindows else emptyList(),
                transitionTimelineEndTick = transitionTimelineEndTick.takeIf { artifact.instrument == "transitions" }
            ))
            val reportPath = writeGeneratedMidiValidationReport(normalized, artifact.instrument, report)
            require(report.passed) {
                "Generated ${artifact.instrument} MIDI failed validation: ${report.violations.joinToString("; ")}"
            }
            GeneratedMidiArtifactReference(
                artifact.instrument, WorkflowArtifactReference(relative, sha256(artifact.path)),
                WorkflowArtifactReference(GeneratedMidiArtifactPaths.validationReport(artifact.instrument), sha256(reportPath))
            )
        }
        ProjectWorkflowStore.update(normalized) { workflow ->
            workflow.invalidate(WorkflowChange.GENERATED_MIDI)
                .markCurrent(WorkflowArtifact.GENERATED_MIDI)
                .copy(generatedMidi = GeneratedMidiWorkflowReferences(
                    approval.arrangement.sha256, projection.contextSha256, registrySha256, GENERATOR_VERSION, GENERATOR_SEED, references
                ))
        }
        GeneratedMidiSnapshot(artifacts)
    }

    /** Renders only a validated, approved detailed arrangement. DSP and mastering remain separate build stages. */
    override suspend fun renderApprovedStems(root: Path, renderer: InstrumentRenderer, progress: ProgressSink): StemRenderResult = mutate(root) { normalized ->
        progress.report(OperationProgress("render", 1, 2, "Validating approved arrangement"))
        val project = readProject(normalized)
        val input = detailedInput(normalized, project)
        val arrangement = readApproved(normalized, input)
        val analyses = midiAnalyses(normalized, project, project.parts.map { it.id }.toSet())
        progress.report(OperationProgress("render", 2, 2, "Rendering or reusing PCM-24 stems", normalized.resolve("mix/dry.wav")))
        StemRenderingMixer(renderer, libraryRoot).render(normalized, project, arrangement, analyses).also {
            ProjectWorkflowStore.update(normalized) { workflow -> workflow.markCurrent(WorkflowArtifact.STEMS, WorkflowArtifact.DRY_MIX) }
        }
    }

    override fun load(root: Path): ArrangementSnapshot {
        val normalized = root.normalizeRoot()
        val project = readProject(normalized)
        val approved = normalized.resolve(DetailedArrangementStore.APPROVED_FILE)
        val draft = normalized.resolve(DetailedArrangementStore.DRAFT_FILE)
        val artifact = when {
            Files.isRegularFile(approved) -> approved
            Files.isRegularFile(draft) -> draft
            else -> throw ApplicationServiceException(ApplicationErrorCategory.PREREQUISITE, "No detailed arrangement found. Generate an arrangement first.")
        }
        val approvalRequired = artifact == draft
        return runCatching {
            val input = detailedInput(normalized, project)
            val arrangement = if (approvalRequired) DetailedArrangementStore.readDraft(normalized, input) else readApproved(normalized, input)
            snapshot(normalized, project, arrangement, artifact, approvalRequired)
        }.getOrElse { error -> staleSnapshot(normalized, artifact, approvalRequired, error) }
    }

    override fun preview(root: Path): ArrangementSnapshot {
        val snapshot = load(root)
        require(snapshot.approvalRequired) { "Arrangement preview is only available for a draft; no approval is needed." }
        require(!snapshot.stale) { "Arrangement draft is stale: validate or regenerate it before approval." }
        return snapshot
    }

    override fun approve(root: Path): ArrangementSnapshot = mutateBlocking(root) { normalized ->
        val project = readProject(normalized)
        val input = detailedInput(normalized, project)
        val approved = DetailedArrangementStore.approve(normalized, input)
        val approvedProject = projectWithResolvedAssignments(project, input.planningInput)
        if (approvedProject != project) ProjectStore.write(normalized, approvedProject)
        ProjectWorkflowStore.update(normalized) { workflow ->
            workflow.invalidate(app.melotrail.arrangement.WorkflowChange.ARRANGEMENT)
                .markCurrent(WorkflowArtifact.ARRANGEMENT)
                .copy(arrangement = approvalReferences(normalized, approvedProject, input.planningInput))
        }
        snapshot(normalized, approvedProject, readApproved(normalized, input), approved, false)
    }

    private fun detailedInput(root: Path, project: Project): DetailedArrangementInput {
        val projection = musicalAuthorityBuilder.arrangementGeneration(root)
        val planPath = root.resolve(SongPlanStore.FILE_NAME)
        require(Files.isRegularFile(planPath)) { "Song plan not found: $planPath. Generate an arrangement first." }
        val rawPlan = json.decodeFromString(SongPlan.serializer(), Files.readString(planPath, StandardCharsets.UTF_8))
        val structure = rawPlan.sections.map { SectionInstance(it.index, it.partId, it.instanceId) }
        val analyses = canonicalMidiAnalyses(projection)
        // Persisted song-plan intents are already bound to individual section
        // purposes. Reconstruct the user request as purpose-neutral input so
        // validation can rebind each role to the section being checked.
        val requestedIntents = rawPlan.sections.flatMap { it.soundIntents }
            .distinctBy { it.role }
            .map { it.copy(sectionPurpose = null) }
        val planningInput = SongPlanningInput(
            projectName = project.name,
            projectVersion = project.version,
            analyses = analyses,
            structure = structure,
            allowedInstruments = rawPlan.sections.flatMap { it.instrumentProgression }.distinct(),
            style = rawPlan.style.takeIf { rawPlan.contextHash == null },
            soundContext = requestedIntents.takeIf { it.isNotEmpty() }?.let { structuredContext(project) },
            requestedIntents = requestedIntents,
            canonicalProjection = projection
        )
        val plan = SongPlanStore.read(root, planningInput)
        return DetailedArrangementInput(planningInput, plan, SectionVariationStore.read(root, planningInput, plan))
    }

    private fun midiAnalyses(root: Path, project: Project, ids: Set<String>): Map<String, MidiAnalysis> = ids.associateWith { id ->
        // Canonical projects persist a quality report; validating it here rejects a changed
        // clean MIDI before it can be used for arrangement.
        project.requireCleanMidi(root)
        val part = project.parts.find { it.id == id } ?: throw IllegalArgumentException("Structure references unknown part '$id'")
        val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '$id'. Run part analyze first." }
        require(reference.kind?.name == "MIDI") { "MIDI analysis is required for part '$id'. Run part analyze first." }
        val analysis = json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(reference.file), StandardCharsets.UTF_8))
        ArrangementHarmonyContext.apply(analysis, part.sectionType, project.envelope.harmony)
    }

    /**
     * Arrangement input never trusts inferred timing, key, or chords. Analysis
     * contributes only descriptive measurements; the authority projection
     * supplies the declared meter, tempo, key, and occurrence harmony.
     */
    private fun canonicalMidiAnalyses(projection: ArrangementGenerationProjection): Map<String, MidiAnalysis> {
        val facts = projection.analyzedFacts.associateBy { it.partId }
        return facts.mapValues { (partId, fact) ->
            val analysis = fact.analysis
            val occurrence = projection.occurrences.firstOrNull { it.partId == partId }
                ?: throw IllegalArgumentException("Canonical arrangement projection has no occurrence for '$partId'.")
            require(projection.harmonyPpq % analysis.ppq == 0) { "Canonical arrangement timing cannot be projected to '$partId'." }
            val scale = (projection.harmonyPpq / analysis.ppq).toLong()
            val chords = projection.harmony.filter { it.occurrenceId == occurrence.occurrenceId }.map { chord ->
                MidiChord(
                    startTick = (chord.startTick - occurrence.startTick) / scale,
                    endTick = (chord.endTick - occurrence.startTick) / scale,
                    symbol = chord.chord.symbol,
                    confidence = 1.0
                )
            }
            analysis.copy(
                tempoMap = listOf(app.melotrail.arrangement.MidiTempoChange(0, projection.tempo.bpm)),
                timeSignatures = listOf(app.melotrail.arrangement.MidiTimeSignature(0, projection.meter.numerator, projection.meter.denominator)),
                key = app.melotrail.arrangement.MidiKey(projection.projectKey.tonic.toString(), projection.projectKey.modeId.value, 1.0),
                chords = chords
            )
        }
    }

    private fun approvalReferences(root: Path, project: Project, input: SongPlanningInput): ArrangementApprovalReferences {
        val plan = root.resolve(SongPlanStore.FILE_NAME)
        val arrangement = root.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(plan) && Files.isRegularFile(arrangement)) { "Approved arrangement evidence is incomplete." }
        val structure = project.envelope.structureOccurrences.joinToString("|") { "${it.id}:${it.partId}:${it.revision}" }
        val occurrences = input.sectionsWithIdentity().joinToString("|") { "${it.index}:${it.instanceId}:${it.occurrenceHash}" }
        val context = input.contextHash() ?: sha256("${input.resolvedStyle}|${input.allowedInstruments.joinToString(",")}".toByteArray(StandardCharsets.UTF_8))
        val authoritySha256 = requireNotNull(input.canonicalProjection) {
            "Approved arrangement requires the canonical arrangement projection."
        }.contextSha256
        return ArrangementApprovalReferences(
            app.melotrail.arrangement.WorkflowArtifactReference(DetailedArrangementStore.APPROVED_FILE, sha256(arrangement)),
            sha256(structure.toByteArray(StandardCharsets.UTF_8)), sha256(occurrences.toByteArray(StandardCharsets.UTF_8)), context, sha256(plan),
            authoritySha256, registrySha256()
        )
    }

    /** Freezes a resolved stable ID for every canonical occurrence at approval time. */
    private fun projectWithResolvedAssignments(project: Project, input: SongPlanningInput): Project {
        if (input.requestedIntents.isEmpty()) return project
        val registry = InstrumentRegistryLoader(libraryRoot).load()
        if (registry.version == 1) return project
        val decisions = input.requestedIntents.sortedBy { it.role.name }.map { intent ->
            VersionedInstrumentResolver(registry).invoke(ResolveInstrumentRequest(intent, actor = "arranger"))
        }
        require(decisions.all { it.selectedId != null }) {
            "No approved local instrument matches: " + decisions.filter { it.selectedId == null }
                .joinToString { it.normalizedRequest.role.name.lowercase() }
        }
        val byLogicalStem = decisions.groupBy { LegacyLogicalInstrumentRoles.logicalFor(it.normalizedRequest.role) }
            .mapValues { (_, choices) -> choices.minBy { it.normalizedRequest.role.name } }
        val assignments = project.envelope.structureOccurrences.sortedBy { it.instanceId }.flatMap { occurrence ->
            byLogicalStem.toSortedMap().map { (logicalInstrument, decision) ->
                val descriptor = registry.resolve(requireNotNull(decision.selectedId))
                ArrangementAssignmentReference(
                    occurrenceId = occurrence.instanceId,
                    instrumentId = descriptor.id,
                    decisionSha256 = decisionFingerprint(decision),
                    libraryProvenance = libraryProvenance(descriptor),
                    logicalInstrument = logicalInstrument
                )
            }
        }
        return project.copy(envelope = project.envelope.copy(arrangementAssignments = assignments))
    }

    private fun decisionFingerprint(decision: app.melotrail.arrangement.InstrumentSelectionDecision): String = sha256(buildString {
        append(decision.registryVersion).append('|').append(decision.registrySha256).append('|').append(decision.resolverVersion).append('|')
        append(decision.normalizedRequest.role.name).append('|').append(decision.selectedId.orEmpty()).append('|').append(decision.actor).append('|').append(decision.timestamp)
        decision.candidates.forEach { candidate -> append('|').append(candidate.id).append(':').append(candidate.score).append(':').append(candidate.rejection.orEmpty()) }
    }.toByteArray(StandardCharsets.UTF_8))

    private fun libraryProvenance(descriptor: ValidatedInstrumentDescriptor): LibraryProvenanceSnapshot = LibraryProvenanceSnapshot(
        libraryId = descriptor.sourceLibrary.id,
        licenseSha256 = sha256(listOf(
            descriptor.license.displayName, descriptor.license.source, descriptor.license.provenance, descriptor.license.license,
            descriptor.license.commercialUse, descriptor.license.attributionRequired, descriptor.license.attributionText.orEmpty(), descriptor.license.redistribution
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8)),
        provenanceSha256 = sha256(listOf(
            descriptor.sourceLibrary.id, descriptor.sourceLibrary.name, descriptor.sourceLibrary.version, descriptor.sourceLibrary.source
        ).joinToString("|").toByteArray(StandardCharsets.UTF_8))
    )

    private fun snapshot(root: Path, project: Project, arrangement: DetailedArrangement, artifact: Path, approvalRequired: Boolean): ArrangementSnapshot {
        val analyses = midiAnalyses(root, project, arrangement.sections.map { it.partId }.toSet())
        return ArrangementSnapshot(
            root, arrangement.sections.map { section ->
                ArrangementSectionSnapshot(
                    section.index, section.instanceId, section.partId, section.role.name.lowercase(), section.energy,
                    section.instruments.map { instrument ->
                        val density = when (instrument) {
                            is app.melotrail.arrangement.BassInstrumentPlan -> instrument.density
                            is app.melotrail.arrangement.DrumsInstrumentPlan -> instrument.density
                            is app.melotrail.arrangement.PadInstrumentPlan -> instrument.density
                            is app.melotrail.arrangement.StringsInstrumentPlan -> instrument.density
                            else -> null
                        }
                        val role = instrument::class.simpleName?.removeSuffix("InstrumentPlan")?.removeSuffix("SourcePlan")?.lowercase()
                        ArrangementInstrumentSnapshot(instrument.name, instrument.mode.name.lowercase(), role, density)
                    },
                    section.transitionOut.type.name.lowercase(), analyses[section.partId]?.durationSeconds
                )
            }, approvalRequired, !approvalRequired, false, artifact
        )
    }

    private fun readApproved(root: Path, input: DetailedArrangementInput): DetailedArrangement {
        val path = root.resolve(DetailedArrangementStore.APPROVED_FILE)
        val arrangement = json.decodeFromString(DetailedArrangement.serializer(), Files.readString(path, StandardCharsets.UTF_8))
        arrangement.requireValid(input)
        return arrangement
    }

    private fun staleSnapshot(root: Path, artifact: Path, approvalRequired: Boolean, error: Throwable) = ArrangementSnapshot(
        root, emptyList(), approvalRequired, !approvalRequired, true, artifact
    )

    private suspend fun <T> mutate(root: Path, action: suspend (Path) -> T): T {
        val normalized = root.normalizeRoot()
        val lock = locks.computeIfAbsent(normalized) { Mutex() }
        if (!lock.tryLock()) throw ApplicationServiceException(ApplicationErrorCategory.PREREQUISITE, "Another project mutation is already running: $normalized")
        return try { withContext(Dispatchers.IO) { action(normalized) } } catch (error: ApplicationServiceException) {
            throw error
        } catch (error: Throwable) {
            throw ApplicationServiceException(categoryFor(error), error.message ?: "Arrangement operation failed", error)
        } finally { lock.unlock() }
    }

    private fun <T> mutateBlocking(root: Path, action: (Path) -> T): T {
        val normalized = root.normalizeRoot()
        val lock = locks.computeIfAbsent(normalized) { Mutex() }
        if (!lock.tryLock()) throw ApplicationServiceException(ApplicationErrorCategory.PREREQUISITE, "Another project mutation is already running: $normalized")
        return try { action(normalized) } catch (error: Throwable) {
            throw ApplicationServiceException(categoryFor(error), error.message ?: "Arrangement operation failed", error)
        } finally { lock.unlock() }
    }

    private fun readProject(root: Path): Project = ProjectStore.read(root).also { it.requireValid(root) }
    private fun structuredContext(project: Project): ArrangementSoundContext {
        val settings = requireNotNull(project.envelope.compositionSettings?.takeIf { it.complete }) {
            "Save complete project setup with profile, mood, key, and meter before arranging by role."
        }
        return ArrangementSoundContext(
            profile = requireNotNull(settings.profile), mood = requireNotNull(settings.mood),
            keyId = "${settings.key.tonic}-${settings.key.modeId.value}",
            meterNumerator = settings.timeSignature.numerator, meterDenominator = settings.timeSignature.denominator,
            resolvedProfileSha256 = settings.resolvedProfileSha256
        ).also(ArrangementSoundContext::requireValid)
    }
    private fun Path.normalizeRoot(): Path = toAbsolutePath().normalize()
    private fun registrySha256(): String {
        val registry = libraryRoot.toAbsolutePath().normalize().resolve("instruments.json")
        require(Files.isRegularFile(registry)) { "Validated instrument registry is unavailable. Choose a valid sound library in Settings." }
        return sha256(registry)
    }
    private fun writeGeneratedMidiValidationReport(
        root: Path,
        role: String,
        report: RoleValidationReport
    ): Path {
        val relative = GeneratedMidiArtifactPaths.validationReport(role)
        val output = root.resolve(relative)
        Files.createDirectories(checkNotNull(output.parent))
        val temporary = output.resolveSibling(".${output.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(report), StandardCharsets.UTF_8)
        try {
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return output
    }
    private fun sha256(path: Path): String = sha256(Files.readAllBytes(path))
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun categoryFor(error: Throwable) = when (error) {
        is java.io.IOException -> ApplicationErrorCategory.IO
        is IllegalArgumentException -> ApplicationErrorCategory.VALIDATION
        else -> ApplicationErrorCategory.ARTIFACT
    }

    private companion object {
        const val GENERATOR_VERSION = "arrangement-generators-v1"
        const val GENERATOR_SEED = 0L
        val locks = ConcurrentHashMap<Path, Mutex>()
        val json = Json { ignoreUnknownKeys = false }
    }
}

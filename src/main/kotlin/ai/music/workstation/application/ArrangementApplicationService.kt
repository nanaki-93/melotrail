package ai.music.workstation.application

import ai.music.workstation.arrangement.BassMidiGenerationAdapter
import ai.music.workstation.arrangement.DetailedArrangement
import ai.music.workstation.arrangement.DetailedArrangementInput
import ai.music.workstation.arrangement.DetailedArrangementPlanner
import ai.music.workstation.arrangement.DetailedArrangementStore
import ai.music.workstation.arrangement.DeterministicDetailedArrangementPlanner
import ai.music.workstation.arrangement.DeterministicGlobalSongPlanner
import ai.music.workstation.arrangement.DeterministicSectionVariationPlanner
import ai.music.workstation.arrangement.DrumMidiGenerationAdapter
import ai.music.workstation.arrangement.GlobalSongPlanner
import ai.music.workstation.arrangement.InstrumentMode
import ai.music.workstation.arrangement.LocalQwenDetailedArrangementPlanner
import ai.music.workstation.arrangement.LocalQwenGlobalSongPlanner
import ai.music.workstation.arrangement.LogicalInstrument
import ai.music.workstation.arrangement.MidiAnalysis
import ai.music.workstation.arrangement.MidiTransitionGenerationAdapter
import ai.music.workstation.arrangement.PadMidiGenerationAdapter
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.SectionInstance
import ai.music.workstation.arrangement.SectionVariationStore
import ai.music.workstation.arrangement.SongPlan
import ai.music.workstation.arrangement.SongPlanStore
import ai.music.workstation.arrangement.SongPlanningInput
import ai.music.workstation.arrangement.StemRenderResult
import ai.music.workstation.arrangement.StemRenderingMixer
import ai.music.workstation.arrangement.InstrumentRenderer
import ai.music.workstation.arrangement.StringsMidiGenerationAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** Planner choices are intentionally logical; the UI never supplies model endpoints or paths. */
enum class ArrangementPlannerKind { DETERMINISTIC, QWEN }

data class GenerateArrangementRequest(
    val root: Path,
    val planner: ArrangementPlannerKind = ArrangementPlannerKind.DETERMINISTIC,
    val style: String? = null,
    val instruments: List<String> = LogicalInstrument.entries.map { it.wireName },
    val structure: List<String>? = null
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
    private val libraryRoot: Path
) : ArrangementApplicationService {
    override suspend fun generate(request: GenerateArrangementRequest, progress: ProgressSink): ArrangementSnapshot = mutate(request.root) { root ->
        progress.report(OperationProgress("arrange", 1, 3, "Validating MIDI analyses"))
        val project = readProject(root)
        val structure = request.structure?.mapIndexed { index, id -> SectionInstance(index, id) }
            ?: project.structure.mapIndexed { index, id -> SectionInstance(index, id) }
        require(structure.isNotEmpty()) { "Song structure must not be empty" }
        val allowed = request.instruments.distinct()
        require(allowed == request.instruments) { "Arrangement instruments must not contain duplicates" }
        require("piano" in allowed && allowed.all { it in LogicalInstrument.entries.map(LogicalInstrument::wireName) }) {
            "Arrangement instruments must be selected from piano, bass, drums, pad, and strings and include piano"
        }
        val analyses = midiAnalyses(root, project, structure.map(SectionInstance::partId).toSet())
        val input = SongPlanningInput(project.name, project.version, analyses, structure, allowed, request.style)
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
        snapshot(root, project, arrangement, artifact, request.planner == ArrangementPlannerKind.QWEN)
    }

    override suspend fun generateRequiredMidi(root: Path, progress: ProgressSink): GeneratedMidiSnapshot = mutate(root) { normalized ->
        val project = readProject(normalized)
        val input = detailedInput(normalized, project)
        val arrangement = readApproved(normalized, input)
        val analyses = midiAnalyses(normalized, project, project.parts.map { it.id }.toSet())
        val active = arrangement.sections.flatMap { it.instruments }.filter { it.mode == InstrumentMode.GENERATED }.map { it.name }.toSet()
        val total = active.size + if (arrangement.sections.any { it.transitionOut.type.name != "NONE" }) 1 else 0
        var stage = 0
        val artifacts = mutableListOf<GeneratedMidiArtifact>()
        fun emit(name: String, path: Path, events: Int) {
            stage++
            progress.report(OperationProgress("generate-midi", stage, total, "Generated $name MIDI", path))
            artifacts += GeneratedMidiArtifact(name, path, events)
        }
        if ("bass" in active) BassMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("bass", it.path, it.notes.size) }
        coroutineContext.ensureActive()
        if ("drums" in active) DrumMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("drums", it.path, it.hits.size) }
        coroutineContext.ensureActive()
        if ("pad" in active) PadMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("pad", it.path, it.notes.size) }
        coroutineContext.ensureActive()
        if ("strings" in active) StringsMidiGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("strings", it.path, it.notes.size) }
        if (arrangement.sections.any { it.transitionOut.type.name != "NONE" }) {
            coroutineContext.ensureActive()
            MidiTransitionGenerationAdapter(libraryRoot = libraryRoot).generate(normalized, project, arrangement, analyses).let { emit("transitions", it.path, it.result.events.size) }
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
        StemRenderingMixer(renderer, libraryRoot).render(normalized, project, arrangement, analyses)
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
        snapshot(normalized, project, readApproved(normalized, input), approved, false)
    }

    private fun detailedInput(root: Path, project: Project): DetailedArrangementInput {
        val planPath = root.resolve(SongPlanStore.FILE_NAME)
        require(Files.isRegularFile(planPath)) { "Song plan not found: $planPath. Generate an arrangement first." }
        val rawPlan = json.decodeFromString(SongPlan.serializer(), Files.readString(planPath, StandardCharsets.UTF_8))
        val structure = rawPlan.sections.map { SectionInstance(it.index, it.partId) }
        val analyses = midiAnalyses(root, project, structure.map(SectionInstance::partId).toSet())
        val planningInput = SongPlanningInput(
            project.name, project.version, analyses, structure,
            rawPlan.sections.flatMap { it.instrumentProgression }.distinct(), rawPlan.style
        )
        val plan = SongPlanStore.read(root, planningInput)
        return DetailedArrangementInput(planningInput, plan, SectionVariationStore.read(root, planningInput, plan))
    }

    private fun midiAnalyses(root: Path, project: Project, ids: Set<String>): Map<String, MidiAnalysis> = ids.associateWith { id ->
        // New projects persist a quality report; validating it here rejects a changed clean MIDI
        // before it can be used for arrangement. Older projects remain legacy/unknown.
        project.requireCleanMidi(root)
        val part = project.parts.find { it.id == id } ?: throw IllegalArgumentException("Structure references unknown part '$id'")
        val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for part '$id'. Run part analyze first." }
        require(reference.kind?.name == "MIDI") { "MIDI analysis is required for part '$id'. Run part analyze first." }
        json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(reference.file), StandardCharsets.UTF_8))
    }

    private fun snapshot(root: Path, project: Project, arrangement: DetailedArrangement, artifact: Path, approvalRequired: Boolean): ArrangementSnapshot {
        val analyses = midiAnalyses(root, project, arrangement.sections.map { it.partId }.toSet())
        return ArrangementSnapshot(
            root, arrangement.sections.map { section ->
                ArrangementSectionSnapshot(
                    section.index, section.instanceId, section.partId, section.role.name.lowercase(), section.energy,
                    section.instruments.map { instrument ->
                        val density = when (instrument) {
                            is ai.music.workstation.arrangement.BassInstrumentPlan -> instrument.density
                            is ai.music.workstation.arrangement.DrumsInstrumentPlan -> instrument.density
                            is ai.music.workstation.arrangement.PadInstrumentPlan -> instrument.density
                            is ai.music.workstation.arrangement.StringsInstrumentPlan -> instrument.density
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
    private fun Path.normalizeRoot(): Path = toAbsolutePath().normalize()
    private fun categoryFor(error: Throwable) = when (error) {
        is java.io.IOException -> ApplicationErrorCategory.IO
        is IllegalArgumentException -> ApplicationErrorCategory.VALIDATION
        else -> ApplicationErrorCategory.ARTIFACT
    }

    private companion object {
        val locks = ConcurrentHashMap<Path, Mutex>()
        val json = Json { ignoreUnknownKeys = false }
    }
}

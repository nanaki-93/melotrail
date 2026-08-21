package app.melotrail.application

import app.melotrail.arrangement.LocalQwenMidiAiFixPlanner
import app.melotrail.arrangement.MidiAiFixDiff
import app.melotrail.arrangement.MidiAiFixInput
import app.melotrail.arrangement.MidiAiFixInputFactory
import app.melotrail.arrangement.MidiAiFixPlan
import app.melotrail.arrangement.MidiAiFixStore
import app.melotrail.arrangement.MidiAiFixTransformer
import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.WorkflowArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class CreateMidiAiFixRequest(val root: Path, val partId: String)

/** UI-safe review state. It exposes no local model configuration or filesystem paths. */
data class MidiAiFixSnapshot(
    val partId: String,
    val inputHash: String,
    val inputSha256: String,
    val outputSha256: String?,
    val edits: List<app.melotrail.arrangement.MidiAiFixEdit>,
    val approved: Boolean,
    val draftAvailable: Boolean,
    val approvedAvailable: Boolean,
    /** The local model could not produce a safe bounded change after retries. */
    val noSafeFix: Boolean = false,
    /** A concise, UI-safe explanation of why no draft was published. */
    val noSafeFixReason: String? = null
)

interface MidiAiFixApplicationService {
    suspend fun create(request: CreateMidiAiFixRequest, progress: ProgressSink = ProgressSink.None): MidiAiFixSnapshot
    fun load(root: Path, partId: String): MidiAiFixSnapshot
    fun approve(root: Path, partId: String): MidiAiFixSnapshot
    /** Rejecting or returning to cleaned MIDI never changes the cleaned source. */
    fun reject(root: Path, partId: String): MidiAiFixSnapshot? = returnToCleaned(root, partId)
    /** Records an explicit bypass even when no draft has been created. */
    fun skip(root: Path, partId: String): MidiAiFixSnapshot? = returnToCleaned(root, partId)
    fun returnToCleaned(root: Path, partId: String): MidiAiFixSnapshot?
    suspend fun regenerate(request: CreateMidiAiFixRequest, progress: ProgressSink = ProgressSink.None): MidiAiFixSnapshot = create(request, progress)
}

/**
 * Typed application boundary for Task 113. The optional model returns only a
 * strict plan; all identity checks, MIDI edits, output validation and project
 * selection happen here in deterministic code.
 */
class DefaultMidiAiFixApplicationService(
    private val planner: (MidiAiFixInput) -> MidiAiFixPlan = { LocalQwenMidiAiFixPlanner().plan(it) },
    private val transformer: MidiAiFixTransformer = MidiAiFixTransformer()
) : MidiAiFixApplicationService {
    override suspend fun create(request: CreateMidiAiFixRequest, progress: ProgressSink): MidiAiFixSnapshot = mutate(request.root) { root ->
        progress.report(OperationProgress("ai-fix", 1, 3, "Validating current cleaned MIDI"))
        val current = currentInput(root, request.partId)
        progress.report(OperationProgress("ai-fix", 2, 3, "Requesting a bounded local musical-fix plan"))
        val plan = planner(current.input)
        plan.requireValid(current.input)
        if (plan.edits.isEmpty()) {
            progress.report(OperationProgress("ai-fix", 3, 3, "No safe AI-fix change was available"))
            MidiAiFixStore.recordNoSafeFix(root, request.partId)
            return@mutate MidiAiFixSnapshot(
                partId = request.partId,
                inputHash = current.input.inputHash,
                inputSha256 = current.input.cleanedSha256,
                outputSha256 = null,
                edits = emptyList(),
                approved = false,
                draftAvailable = false,
                approvedAvailable = false,
                noSafeFix = true,
                noSafeFixReason = noSafeFixReason(current.input)
            )
        }
        progress.report(OperationProgress("ai-fix", 3, 3, "Applying and validating the AI-fix draft"))
        val draft = root.resolve(app.melotrail.arrangement.MidiAiFixArtifactPaths.draft(request.partId))
        val diff = transformer.apply(current.cleaned, draft, plan, current.input)
        MidiAiFixStore.writeDraft(root, current.input, plan, diff)
        snapshot(root, request.partId, current.input, diff, approved = false)
    }

    override fun load(root: Path, partId: String): MidiAiFixSnapshot = locked(root) { normalized ->
        val current = currentInput(normalized, partId)
        val project = ProjectStore.read(normalized); val midi = project.parts.single { it.id == partId }.midi!!
        val refs = requireNotNull(midi.aiFix) { "No AI-fix draft exists. Create one first." }
        require(refs.inputSha256 == current.input.cleanedSha256 && refs.draft != null) { "AI-fix draft is stale. Regenerate it." }
        val diff = MidiAiFixStore.readDiff(normalized, partId)
        require(diff.inputSha256 == current.input.cleanedSha256 && diff.outputSha256 == refs.draft.sha256) { "AI-fix diff is stale. Regenerate it." }
        snapshot(normalized, partId, current.input, diff, midi.aiFixSelection == app.melotrail.arrangement.MidiAiFixSelection.APPROVED)
    }

    override fun approve(root: Path, partId: String): MidiAiFixSnapshot = locked(root) { normalized ->
        val current = currentInput(normalized, partId)
        val refs = MidiAiFixStore.approve(normalized, partId, current.input)
        val diff = MidiAiFixStore.readDiff(normalized, partId)
        snapshot(normalized, partId, current.input, diff.copy(outputSha256 = requireNotNull(refs.approved).sha256), approved = true)
    }

    override fun returnToCleaned(root: Path, partId: String): MidiAiFixSnapshot? = locked(root) { normalized ->
        val current = currentInput(normalized, partId)
        val refs = MidiAiFixStore.selectCleaned(normalized, partId) ?: return@locked null
        val diff = runCatching { MidiAiFixStore.readDiff(normalized, partId) }.getOrNull() ?: return@locked null
        val draft = requireNotNull(refs.draft) { return@locked null }
        require(diff.inputSha256 == current.input.cleanedSha256 && diff.outputSha256 == draft.sha256) { "Retained AI-fix draft is stale. Regenerate it." }
        snapshot(normalized, partId, current.input, diff, approved = false)
    }

    override fun skip(root: Path, partId: String): MidiAiFixSnapshot? = locked(root) { normalized ->
        val current = currentInput(normalized, partId)
        val project = ProjectStore.read(normalized); val part = project.parts.single { it.id == partId }; val midi = requireNotNull(part.midi)
        val refs = MidiAiFixStore.selectCleaned(normalized, partId)
        if (refs == null) {
            ProjectStore.write(normalized, project.copy(parts = project.parts.map {
                if (it.id == partId) it.copy(analysis = null, midi = midi.copy(aiFixSelection = app.melotrail.arrangement.MidiAiFixSelection.SKIP,
                    analysisInput = app.melotrail.arrangement.MidiAnalysisInput.CURRENT, feel = null)) else it
            }))
            return@locked null
        }
        val diff = MidiAiFixStore.readDiff(normalized, partId)
        snapshot(normalized, partId, current.input, diff, approved = false)
    }

    private fun currentInput(root: Path, partId: String): CurrentInput {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        val part = project.parts.singleOrNull { it.id == partId } ?: throw IllegalArgumentException("Part not found: $partId")
        val midi = requireNotNull(part.midi) { "Part '$partId' has no cleaned MIDI." }
        val raw = requireNotNull(midi.raw) { "Part '$partId' predates current Clean MIDI evidence. Re-import it first." }
        val cleanRef = requireNotNull(midi.clean) { "Part '$partId' has no cleaned MIDI." }
        val cleanup = requireNotNull(midi.cleanup) { "Part '$partId' has no current Clean MIDI evidence." }
        val quality = requireNotNull(midi.quality) { "Part '$partId' has no current Clean MIDI evidence." }
        MidiQualityReportStore.requireCurrent(root, partId, raw, cleanRef, cleanup, quality)
        require(MidiQualityReportStore.isApproved(root, quality, midi.cleanApproval)) { "Approve Clean MIDI before creating an AI fix." }
        val correction = requireNotNull(midi.technicalCorrection) { "Part '$partId' has no technical correction. Run Technical Correction before AI Fix." }
        require(midi.technicalCorrectionSelection == app.melotrail.arrangement.TechnicalCorrectionSelection.CORRECTED) {
            "Select corrected MIDI before creating an AI fix."
        }
        val correctionInput = midi.transposed ?: midi.normalized ?: cleanRef
        val correctionInputPath = safeTechnicalCorrectionInput(root, correctionInput)
        require(correction.input.file == correctionInput && correction.input.sha256 == app.melotrail.arrangement.sha256(correctionInputPath)) {
            "Technical correction is stale. Run Technical Correction again."
        }
        val corrected = safeCorrected(root, correction.output)
        val input = MidiAiFixInputFactory.build(partId, corrected)
        return CurrentInput(corrected, input)
    }

    private fun safeCleaned(root: Path, reference: String): Path {
        val normalized = root.toAbsolutePath().normalize(); val relative = Path.of(reference)
        require(!relative.isAbsolute && reference.startsWith("midi/clean/") && relative.none { it.toString() == ".." }) { "Cleaned MIDI reference is unsafe" }
        val path = normalized.resolve(relative).normalize()
        require(path.startsWith(normalized) && Files.isRegularFile(path) && path.toRealPath().startsWith(normalized.toRealPath())) { "Cleaned MIDI is missing" }
        return path
    }

    /** Technical Correction may use the clean, normalized, or transposed baseline. */
    private fun safeTechnicalCorrectionInput(root: Path, reference: String): Path {
        val normalized = root.toAbsolutePath().normalize(); val relative = Path.of(reference)
        require(!relative.isAbsolute && relative.none { it.toString() == ".." } &&
            (reference.startsWith("midi/clean/") || reference.startsWith("midi/normalized/") || reference.startsWith("midi/transposed/"))) {
            "Technical-correction input reference is unsafe"
        }
        val path = normalized.resolve(relative).normalize()
        require(path.startsWith(normalized) && Files.isRegularFile(path) && path.toRealPath().startsWith(normalized.toRealPath())) {
            "Technical-correction input is missing"
        }
        return path
    }

    private fun safeCorrected(root: Path, reference: app.melotrail.arrangement.WorkflowArtifactReference): Path {
        val relative = Path.of(reference.file)
        require(!relative.isAbsolute && reference.file.startsWith("midi/corrected/") && relative.none { it.toString() == ".." }) { "Corrected MIDI reference is unsafe" }
        val path = root.toAbsolutePath().normalize().resolve(relative).normalize()
        require(path.startsWith(root.toAbsolutePath().normalize()) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())) { "Corrected MIDI is missing" }
        require(app.melotrail.arrangement.sha256(path) == reference.sha256) { "Corrected MIDI is stale" }
        return path
    }

    private fun snapshot(root: Path, partId: String, input: MidiAiFixInput, diff: MidiAiFixDiff, approved: Boolean): MidiAiFixSnapshot {
        val project = ProjectStore.read(root); val midi = project.parts.single { it.id == partId }.midi!!; val refs = midi.aiFix
        val draftAvailable = refs?.draft?.let { ref -> runCatching { Files.isRegularFile(root.resolve(ref.file)) && ref.sha256 == diff.outputSha256 }.getOrDefault(false) } == true
        val approvedAvailable = refs?.approved?.let { ref -> runCatching { Files.isRegularFile(root.resolve(ref.file)) && ref.sha256 == diff.outputSha256 }.getOrDefault(false) } == true
        return MidiAiFixSnapshot(partId, input.inputHash, input.cleanedSha256, diff.outputSha256, diff.edits, approved, draftAvailable, approvedAvailable)
    }

    private fun noSafeFixReason(input: MidiAiFixInput): String = if (input.problemRegions.any {
            it.kind == app.melotrail.arrangement.MidiAiFixProblemKind.COLLISION || it.kind == app.melotrail.arrangement.MidiAiFixProblemKind.DUPLICATE
        }) {
        "Existing same-pitch collisions in corrected MIDI could not be resolved safely."
    } else {
        "The model proposals would have created a same-pitch collision."
    }

    private suspend fun <T> mutate(root: Path, block: suspend (Path) -> T): T {
        val normalized = root.toAbsolutePath().normalize(); val lock = locks.computeIfAbsent(normalized) { Mutex() }
        check(lock.tryLock()) { "Another AI-fix operation is already running: $normalized" }
        return try { withContext(Dispatchers.IO) { block(normalized) } } finally { lock.unlock() }
    }
    private fun <T> locked(root: Path, block: (Path) -> T): T {
        val normalized = root.toAbsolutePath().normalize(); val lock = locks.computeIfAbsent(normalized) { Mutex() }
        check(lock.tryLock()) { "Another AI-fix operation is already running: $normalized" }
        return try { block(normalized) } finally { lock.unlock() }
    }
    private data class CurrentInput(val cleaned: Path, val input: MidiAiFixInput)
    private companion object { val locks = ConcurrentHashMap<Path, Mutex>() }
}

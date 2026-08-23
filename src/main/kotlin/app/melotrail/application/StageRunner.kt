package app.melotrail.application

import app.melotrail.arrangement.ArtifactRef
import app.melotrail.arrangement.ModelIdentity
import app.melotrail.arrangement.ProcessorIdentity
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStageRunManifestReference
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.SafeFailure
import app.melotrail.arrangement.SafeFailureCode
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunRecord
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageRunStore
import app.melotrail.arrangement.StageSubject
import app.melotrail.arrangement.artifactRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A processor is an application port: Compose, worker HTTP and renderers stay behind it. */
interface StageProcessor {
    val definition: StageDefinition
    suspend fun process(request: StageProcessingRequest): StageProcessorResult

    /**
     * Runs only after every processor artifact is atomically published, while
     * the project mutation lock is still held. It may update canonical
     * references, but must not publish another artifact.
     */
    fun onPublished(request: StageProcessingRequest, outputs: List<ArtifactRef>, reports: List<ArtifactRef>) = Unit

    /** Stage-specific processors override this for MIME/container validation. */
    fun validate(result: StageProcessorResult) {
        result.outputs.forEach { artifact ->
            require(Files.isRegularFile(artifact.temporaryPath) && Files.size(artifact.temporaryPath) > 0L) {
                "Processor returned an empty output"
            }
        }
        result.reports.forEach { artifact ->
            require(Files.isRegularFile(artifact.temporaryPath) && Files.size(artifact.temporaryPath) > 0L) {
                "Processor returned an empty report"
            }
        }
    }
}

data class StageDefinition(
    val stage: StageId,
    val subjectKind: StageSubjectKind,
    val dependencies: Set<StageId> = emptySet(),
    val automaticallyChainsTo: StageId? = null,
    val timeoutMillis: Long? = null
) {
    init {
        require(timeoutMillis == null || timeoutMillis > 0) { "Stage timeout must be positive" }
    }
}

enum class StageSubjectKind { PROJECT, PART, OCCURRENCE }

class StageProcessorRegistry(processors: Collection<StageProcessor>) {
    private val byStage = processors.associateBy { it.definition.stage }

    init {
        require(byStage.size == processors.size) { "A stage may have only one processor" }
        processors.forEach { processor ->
            require(if (processor.definition.stage.isPartStage) processor.definition.subjectKind == StageSubjectKind.PART
                else processor.definition.subjectKind != StageSubjectKind.PART) { "Stage processor subject kind is invalid" }
        }
    }

    fun require(stage: StageId): StageProcessor = requireNotNull(byStage[stage]) { "No eligible processor is registered for $stage" }
    fun get(stage: StageId): StageProcessor? = byStage[stage]
}

data class RunStage(
    val root: Path,
    val stage: StageId,
    val subject: StageSubject,
    val inputArtifacts: List<ArtifactRef> = emptyList(),
    val subjectDependencies: List<StageSubject> = emptyList(),
    val configurationSha256: String? = null,
    val contextSha256: String? = null,
    val model: ModelIdentity? = null,
    val seed: Long? = null
)

data class RetryStage(val root: Path, val runId: String)
data class GetStageRuns(val root: Path)
data class ObserveStageRuns(val root: Path)

data class StageRunSnapshot(
    val runId: String,
    val stage: StageId,
    val subject: StageSubject,
    val status: StageRunStatus,
    val retryable: Boolean,
    val progress: Int? = null,
    val failure: SafeFailureCode? = null,
    val cancellation: StageCancellation = StageCancellation.UNSUPPORTED,
    /** Safe, project-relative version references; UI clients never inspect the filesystem. */
    val outputs: List<StageArtifactSnapshot> = emptyList(),
    val reports: List<StageArtifactSnapshot> = emptyList()
)

/** A stable artifact version identity published by one immutable stage run. */
data class StageArtifactSnapshot(
    val id: String,
    val sha256: String
)

enum class StageCancellation { UNSUPPORTED, STOP_AFTER_CURRENT }
data class StageRunResult(val runId: String, val snapshot: StageRunSnapshot, val cacheHit: Boolean)

/** A processor uses this instead of exception text when a user decision is the next safe action. */
class InputRequiredException(message: String) : IllegalStateException(message)

data class StageProcessingRequest(
    val root: Path,
    val runId: String,
    val stage: StageId,
    val subject: StageSubject,
    val inputArtifacts: List<ArtifactRef>,
    val temporaryRoot: Path,
    val reportProgress: (Int) -> Unit
)

/** Only paths inside [StageProcessingRequest.temporaryRoot] may be returned. */
data class TemporaryStageArtifact(val temporaryPath: Path, val destination: String)
data class StageProcessorResult(
    val outputs: List<TemporaryStageArtifact>,
    val reports: List<TemporaryStageArtifact> = emptyList()
)

interface StageOutputPublisher {
    fun publish(root: Path, temporary: Path, destination: String)
}

object FileStageOutputPublisher : StageOutputPublisher {
    override fun publish(root: Path, temporary: Path, destination: String) {
        val target = root.resolve(destination).normalize()
        Files.createDirectories(requireNotNull(target.parent))
        require(!Files.exists(target)) { "Stage output destination already exists" }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (error: AtomicMoveNotSupportedException) {
            throw IllegalStateException("Atomic publication is unavailable for this project volume", error)
        }
    }
}

/**
 * Durable, local runner. Every indexed record is an immutable state revision;
 * project.json advances atomically to its new index only after that revision is
 * fully written. Output publication occurs before the Completed revision.
 */
class StageRunner(
    private val registry: StageProcessorRegistry,
    private val store: StageRunStore = StageRunStore(),
    private val outputPublisher: StageOutputPublisher = FileStageOutputPublisher,
    private val clock: Clock = Clock.systemUTC(),
    private val runIdFactory: () -> String = { "run-${UUID.randomUUID()}" }
) : ProjectOpenStageRunRecovery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<StageRunResult>>()
    private val observations = ConcurrentHashMap<Path, MutableStateFlow<List<StageRunSnapshot>>>()

    suspend fun run(command: RunStage): StageRunResult {
        val root = command.root.toAbsolutePath().normalize()
        val processor = registry.require(command.stage)
        require(subjectKind(command.subject) == processor.definition.subjectKind) { "Stage subject is not eligible" }
        val key = "$root:${cacheKey(command, processor)}"
        queryCached(root, command, processor)?.let { return it }
        val deferred = inFlight.computeIfAbsent(key) {
            scope.async { execute(command.copy(root = root), processor) }
        }
        return try {
            deferred.await().also { result -> scheduleContinuation(command.copy(root = root), processor, result) }
        } finally { if (deferred.isCompleted) inFlight.remove(key, deferred) }
    }

    suspend fun retry(command: RetryStage): StageRunResult {
        val root = command.root.toAbsolutePath().normalize()
        val record = records(root).firstOrNull { it.runId == command.runId }
            ?: throw IllegalArgumentException("Stage run was not found")
        require(record.status == StageRunStatus.FAILED) { "Only a failed stage run can be retried" }
        return run(RunStage(root, record.stage, record.subject, record.inputArtifacts, record.subjectDependencies,
            record.configurationSha256, record.contextSha256, record.model, record.seed))
    }

    fun get(command: GetStageRuns): List<StageRunSnapshot> = snapshots(command.root.toAbsolutePath().normalize())

    fun observe(command: ObserveStageRuns): StateFlow<List<StageRunSnapshot>> {
        val root = command.root.toAbsolutePath().normalize()
        return observations.computeIfAbsent(root) { MutableStateFlow(snapshots(root)) }
    }

    /** No dependency currently exposes verified cancellation; expose the truthful boundary. */
    fun requestStopAfterCurrent(root: Path, runId: String): StageRunSnapshot {
        val snapshot = get(GetStageRuns(root)).firstOrNull { it.runId == runId }
            ?: throw IllegalArgumentException("Stage run was not found")
        return snapshot.copy(cancellation = if (snapshot.status == StageRunStatus.PROCESSING) StageCancellation.STOP_AFTER_CURRENT else StageCancellation.UNSUPPORTED)
    }

    override fun recover(root: Path): Boolean {
        val normalized = root.toAbsolutePath().normalize()
        val lock = ProjectMutationCoordinator.lock(normalized)
        return if (lock.tryLock()) try {
            val project = ProjectStore.read(normalized)
            if (project.version != Project.CURRENT_VERSION || project.envelope.stageRuns.index == null) return false
            val interrupted = store.read(normalized, project.envelope.stageRuns).filter { it.status == StageRunStatus.PROCESSING }
            interrupted.forEach { record ->
                persist(normalized, record.copy(status = StageRunStatus.FAILED, finishedAt = now(),
                    failure = SafeFailure(SafeFailureCode.INTERRUPTED, "Retry the interrupted stage.")))
            }
            refresh(normalized)
            interrupted.isNotEmpty()
        } finally { lock.unlock() } else false
    }

    private suspend fun execute(command: RunStage, processor: StageProcessor): StageRunResult {
        val root = command.root
        val lock = ProjectMutationCoordinator.lock(root)
        return lock.withLock {
            queryCached(root, command, processor)?.let { return@withLock it }
            requireDependencies(root, command, processor.definition)
            val runId = runIdFactory().also { require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,79}").matches(it)) { "Stage run ID is invalid" } }
            val processing = record(command, processor, runId, StageRunStatus.PROCESSING, startedAt = now())
            persist(root, processing)
            refresh(root, progress = 0)
            val temporaryRoot = root.resolve("workflow-runs/work/$runId").normalize()
            Files.createDirectories(temporaryRoot)
            try {
                val request = StageProcessingRequest(root, runId, command.stage, command.subject, command.inputArtifacts, temporaryRoot) { progress ->
                    require(progress in 0..100) { "Stage progress must be 0 through 100" }
                    refresh(root, progress = progress)
                }
                val result = processor.definition.timeoutMillis?.let { timeout -> withTimeout(timeout) { processor.process(request) } }
                    ?: processor.process(request)
                validateTemporary(root, temporaryRoot, result)
                processor.validate(result)
                publish(root, result)
                val outputArtifacts = result.outputs.map { artifactRef(root, it.destination) }
                val reportArtifacts = result.reports.map { artifactRef(root, it.destination) }
                processor.onPublished(request, outputArtifacts, reportArtifacts)
                val completed = processing.copy(status = StageRunStatus.COMPLETED, finishedAt = now(),
                    outputArtifacts = outputArtifacts, reportArtifacts = reportArtifacts)
                persist(root, completed)
                refresh(root, progress = 100)
                StageRunResult(runId, snapshot(completed, 100), cacheHit = false)
            } catch (error: Exception) {
                val failed = processing.copy(status = StageRunStatus.FAILED, finishedAt = now(), failure = failureFor(error))
                persist(root, failed)
                refresh(root)
                StageRunResult(runId, snapshot(failed), cacheHit = false)
            }
        }
    }

    private fun persist(root: Path, record: StageRunRecord) {
        val project = ProjectStore.read(root).also { it.requireValid(root) }
        require(project.version == Project.CURRENT_VERSION) { "Stage runs require a v4 project" }
        val reference = store.transition(root, project.envelope.stageRuns, record)
        ProjectStore.write(root, project.copy(envelope = project.envelope.copy(stageRuns = reference)))
    }

    private fun requireDependencies(root: Path, command: RunStage, definition: StageDefinition) {
        if (definition.dependencies.isEmpty()) return
        val completed = records(root).filter { it.status == StageRunStatus.COMPLETED && it.subject == command.subject }.map { it.stage }.toSet()
        require(definition.dependencies.all { it in completed }) { "Stage dependencies are not complete" }
    }

    private fun validateTemporary(root: Path, temporaryRoot: Path, result: StageProcessorResult) {
        require(result.outputs.isNotEmpty()) { "A completed stage requires output" }
        val rootReal = root.toRealPath()
        val temporaryReal = temporaryRoot.toRealPath()
        val artifacts = result.outputs + result.reports
        require(artifacts.map { it.destination }.distinct().size == artifacts.size) { "Stage output destinations must be unique" }
        artifacts.forEach { artifact ->
            val temporary = artifact.temporaryPath.toAbsolutePath().normalize()
            require(temporary.startsWith(temporaryRoot) && Files.isRegularFile(temporary) && temporary.toRealPath().startsWith(temporaryReal)) {
                "Processor output must remain in the stage temporary directory"
            }
            val destination = Path.of(artifact.destination)
            require(artifact.destination.isNotBlank() && !destination.isAbsolute && destination.none { it.toString() == ".." } &&
                destination.normalize().toString().replace('\\', '/') == artifact.destination &&
                '\\' !in artifact.destination && ':' !in artifact.destination &&
                !artifact.destination.startsWith("source/") && !artifact.destination.startsWith("workflow-runs/")) {
                "Stage output destination is unsafe"
            }
            val target = root.resolve(destination).normalize()
            val existingParent = generateSequence(target.parent) { it.parent }.firstOrNull(Files::exists)
            require(target.startsWith(root) && target.parent.toAbsolutePath().normalize().startsWith(root) &&
                existingParent?.toRealPath()?.startsWith(rootReal) == true &&
                (!Files.exists(target) || target.toRealPath().startsWith(rootReal))) { "Stage output destination escapes the project" }
            require(!Files.exists(target)) { "Stage output destination already exists" }
        }
    }

    private fun publish(root: Path, result: StageProcessorResult) {
        (result.outputs + result.reports).forEach { outputPublisher.publish(root, it.temporaryPath, it.destination) }
    }

    private fun queryCached(root: Path, command: RunStage, processor: StageProcessor): StageRunResult? {
        val key = cacheKey(command, processor)
        return records(root).asReversed().firstOrNull { it.status == StageRunStatus.COMPLETED && it.cacheKey() == key }
            ?.let { StageRunResult(it.runId, snapshot(it), cacheHit = true) }
    }

    private fun cacheKey(command: RunStage, processor: StageProcessor): String = record(command, processor, "cache", StageRunStatus.PENDING).cacheKey()

    private fun record(command: RunStage, processor: StageProcessor, runId: String, status: StageRunStatus, startedAt: String? = null): StageRunRecord =
        StageRunRecord(runId = runId, stage = command.stage, subject = command.subject, status = status,
            inputArtifacts = command.inputArtifacts, subjectDependencies = command.subjectDependencies,
            configurationSha256 = command.configurationSha256, contextSha256 = command.contextSha256,
            processor = ProcessorIdentity("stage-${command.stage.name.lowercase()}", "1"),
            model = command.model, seed = command.seed, createdAt = startedAt ?: now(), startedAt = startedAt)

    private fun records(root: Path): List<StageRunRecord> = runCatching {
        val project = ProjectStore.read(root)
        if (project.envelope.stageRuns.index == null) emptyList() else store.read(root, project.envelope.stageRuns)
    }.getOrDefault(emptyList())

    private fun snapshots(root: Path): List<StageRunSnapshot> = records(root).map(::snapshot)

    private fun snapshot(record: StageRunRecord, progress: Int? = null) = StageRunSnapshot(
        record.runId, record.stage, record.subject, record.status,
        record.status == StageRunStatus.FAILED, progress, record.failure?.code,
        outputs = record.outputArtifacts.mapIndexed { index, artifact ->
            StageArtifactSnapshot("${record.runId}:output:$index", artifact.sha256)
        },
        reports = record.reportArtifacts.mapIndexed { index, artifact ->
            StageArtifactSnapshot("${record.runId}:report:$index", artifact.sha256)
        }
    )

    private fun refresh(root: Path, progress: Int? = null) {
        observations[root]?.value = snapshots(root).map { snapshot ->
            if (progress != null && snapshot.status == StageRunStatus.PROCESSING) snapshot.copy(progress = progress) else snapshot
        }
    }

    private fun failureFor(error: Throwable): SafeFailure = when (error) {
        is kotlinx.coroutines.TimeoutCancellationException -> SafeFailure(SafeFailureCode.DEPENDENCY_UNAVAILABLE, "Check the local dependency and retry.")
        is InputRequiredException -> SafeFailure(SafeFailureCode.INPUT_INVALID, "Provide the required input, then retry.")
        is IllegalArgumentException -> SafeFailure(SafeFailureCode.OUTPUT_INVALID, "Review the stage output and retry.")
        else -> SafeFailure(SafeFailureCode.PROCESSOR_REJECTED, "Review the stage input and retry.")
    }

    private fun now(): String = Instant.now(clock).toString()

    private fun scheduleContinuation(command: RunStage, processor: StageProcessor, result: StageRunResult) {
        val nextStage = processor.definition.automaticallyChainsTo ?: return
        if (result.snapshot.status != StageRunStatus.COMPLETED) return
        val next = registry.get(nextStage) ?: return
        if (next.definition.subjectKind != subjectKind(command.subject)) return
        val outputs = records(command.root).firstOrNull { it.runId == result.runId }?.outputArtifacts ?: return
        scope.async {
            run(command.copy(stage = nextStage, inputArtifacts = outputs))
        }
    }

    private fun subjectKind(subject: StageSubject) = when (subject) {
        StageSubject.Project -> StageSubjectKind.PROJECT
        is StageSubject.Part -> StageSubjectKind.PART
        is StageSubject.Occurrence -> StageSubjectKind.OCCURRENCE
    }
}

fun interface ProjectOpenStageRunRecovery { fun recover(root: Path): Boolean }

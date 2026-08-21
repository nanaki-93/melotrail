package app.melotrail.application

import app.melotrail.arrangement.ProcessorIdentity
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.SafeFailureCode
import app.melotrail.arrangement.StageId
import app.melotrail.arrangement.StageRunRecord
import app.melotrail.arrangement.StageRunStatus
import app.melotrail.arrangement.StageRunStore
import app.melotrail.arrangement.StageSubject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StageRunnerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `success is cached and a changed configuration creates a new atomic output`() = runBlocking {
        project()
        val processor = WritingProcessor(StageId.CLEANED)
        val runner = runner(processor)

        val first = runner.run(command(configuration = "a"))
        val cached = runner.run(command(configuration = "a"))
        val changed = runner.run(command(configuration = "b"))

        assertEquals(StageRunStatus.COMPLETED, first.snapshot.status)
        assertTrue(cached.cacheHit)
        assertFalse(changed.cacheHit)
        assertEquals(2, processor.calls)
        assertEquals(2, runner.get(GetStageRuns(root)).size)
        assertTrue(Files.isRegularFile(root.resolve("derived/output-1.txt")))
        assertTrue(Files.isRegularFile(root.resolve("derived/output-2.txt")))
    }

    @Test
    fun `failure is safe and retry creates a new attempt without rerunning an upstream stage`() = runBlocking {
        project()
        val processor = WritingProcessor(StageId.CLEANED, failFirst = true)
        val runner = runner(processor)

        val failed = runner.run(command())
        val retried = runner.retry(RetryStage(root, failed.runId))

        assertEquals(SafeFailureCode.PROCESSOR_REJECTED, failed.snapshot.failure)
        assertEquals(StageRunStatus.COMPLETED, retried.snapshot.status)
        assertEquals(2, processor.calls)
        assertEquals(listOf(StageRunStatus.FAILED, StageRunStatus.COMPLETED), runner.get(GetStageRuns(root)).map { it.status })
    }

    @Test
    fun `duplicate concurrent requests converge on one run`() = runBlocking {
        project()
        val processor = WritingProcessor(StageId.CLEANED, delayMillis = 100)
        val runner = runner(processor)

        val results = awaitAll(async { runner.run(command()) }, async { runner.run(command()) })

        assertEquals(1, processor.calls)
        assertEquals(results[0].runId, results[1].runId)
    }

    @Test
    fun `timeout invalid output and publication failure remain failed evidence`() = runBlocking {
        project()
        val timeout = runner(WritingProcessor(StageId.CLEANED, delayMillis = 100, timeoutMillis = 1))
        assertEquals(SafeFailureCode.DEPENDENCY_UNAVAILABLE, timeout.run(command()).snapshot.failure)

        project("invalid")
        val invalid = runner(WritingProcessor(StageId.CLEANED, emptyOutput = true))
        assertEquals(SafeFailureCode.OUTPUT_INVALID, invalid.run(command("invalid")).snapshot.failure)

        project("atomic")
        val atomic = StageRunner(StageProcessorRegistry(listOf(WritingProcessor(StageId.CLEANED))),
            outputPublisher = object : StageOutputPublisher {
                override fun publish(root: Path, temporary: Path, destination: String) = error("no atomic move")
            }, clock = CLOCK)
        assertEquals(SafeFailureCode.PROCESSOR_REJECTED, atomic.run(command("atomic")).snapshot.failure)
        assertFalse(Files.exists(root.resolve("atomic/derived/output-1.txt")))
    }

    @Test
    fun `open recovers processing as retryable interrupted failure`() {
        project()
        val store = StageRunStore()
        val processing = StageRunRecord(runId = "interrupted", stage = StageId.CLEANED, subject = StageSubject.Part("A"),
            status = StageRunStatus.PROCESSING, processor = ProcessorIdentity("stage-cleaned", "1"),
            createdAt = NOW, startedAt = NOW)
        val before = ProjectStore.read(root)
        val reference = store.transition(root, before.envelope.stageRuns, processing)
        ProjectStore.write(root, before.copy(envelope = before.envelope.copy(stageRuns = reference)))
        val runner = runner(WritingProcessor(StageId.CLEANED))
        val service = DefaultProjectApplicationService(
            midiPreparation = object : MidiPreparationService { override suspend fun transcribe(input: Path, output: Path) = Unit; override suspend fun clean(input: Path, output: Path) = Unit },
            stageRunRecovery = runner
        )

        val snapshot = service.open(root)

        assertEquals(StageRunStatus.FAILED, snapshot.readiness.stageRuns.single().status)
        assertEquals(SafeFailureCode.INTERRUPTED, snapshot.readiness.stageRuns.single().failure)
        assertTrue(snapshot.readiness.stageRuns.single().retryable)
        assertEquals(SafeFailureCode.INTERRUPTED,
            WorkflowReadModelDeriver.derive(snapshot)[WorkflowStage.CLEAN_MIDI].stageRun?.failure)
    }

    @Test
    fun `automatic chain stops at its boundary and restarts from cached upstream`() = runBlocking {
        project()
        val first = WritingProcessor(StageId.CLEANED, next = StageId.ANALYZED)
        val second = WritingProcessor(StageId.ANALYZED)
        val runner = runner(first, second)

        runner.run(command())
        repeat(30) {
            if (runner.get(GetStageRuns(root)).size == 2) return@repeat
            delay(10)
        }

        assertEquals(listOf(StageId.CLEANED, StageId.ANALYZED), runner.get(GetStageRuns(root)).map { it.stage })
        assertEquals(1, second.calls)
    }

    private fun project(name: String = "project") {
        val target = if (name == "project") root else root.resolve(name)
        Files.createDirectories(target)
        ProjectStore.create(target, name, RenderFormat())
    }

    private fun command(name: String = "project", configuration: String = "a") = RunStage(
        root = if (name == "project") root else root.resolve(name), stage = StageId.CLEANED,
        subject = StageSubject.Part("A"), configurationSha256 = configuration.repeat(64).take(64)
    )

    private fun runner(vararg processors: StageProcessor) = StageRunner(StageProcessorRegistry(processors.toList()), clock = CLOCK)

    private class WritingProcessor(
        stage: StageId,
        private val failFirst: Boolean = false,
        private val emptyOutput: Boolean = false,
        private val delayMillis: Long = 0,
        timeoutMillis: Long? = null,
        next: StageId? = null
    ) : StageProcessor {
        override val definition = StageDefinition(stage, StageSubjectKind.PART, automaticallyChainsTo = next, timeoutMillis = timeoutMillis)
        var calls = 0
        override suspend fun process(request: StageProcessingRequest): StageProcessorResult {
            calls++
            if (delayMillis > 0) delay(delayMillis)
            if (failFirst && calls == 1) error("worker failed")
            val temporary = request.temporaryRoot.resolve("output.txt")
            Files.writeString(temporary, if (emptyOutput) "" else "result-$calls")
            return StageProcessorResult(listOf(TemporaryStageArtifact(temporary, "derived/output-$calls.txt")))
        }
    }

    private companion object {
        const val NOW = "2026-08-19T00:00:00Z"
        val CLOCK: Clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC)
    }
}

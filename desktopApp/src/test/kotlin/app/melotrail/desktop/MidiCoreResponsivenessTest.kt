package app.melotrail.desktop

import app.melotrail.application.*
import app.melotrail.audition.MidiAuditionPlaybackState
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiNoteEvent
import java.awt.EventQueue
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Wall-clock measurements, never a virtual coroutine/test clock or an acoustic benchmark. */
class MidiCoreResponsivenessTest {
    @Test
    fun `evidence finds the repository with split planning documents`() {
        val repository = U07Evidence.repository()
        assertTrue(Files.isRegularFile(repository.resolve("settings.gradle.kts")))
        assertTrue(Files.isRegularFile(repository.resolve("PLAN-AUDIO.md")))
        assertTrue(Files.isRegularFile(repository.resolve("PLAN-VIDEO.md")))
    }

    @Test
    fun `nearest rank p95 retains the slow tail and rejects invalid measurements`() {
        assertEquals(19.0, U07Evidence.p95((1..20).map(Int::toDouble)))
        assertEquals(19.0, U07Evidence.p95((1..19).map(Int::toDouble) + 10_000.0))
        assertEquals(300.0, U07Evidence.p95(List(18) { 1.0 } + listOf(300.0, 1_000.0)))
        assertFailsWith<IllegalArgumentException> { U07Evidence.p95(emptyList()) }
        assertFailsWith<IllegalArgumentException> { U07Evidence.p95(listOf(Double.NaN)) }
        assertFailsWith<IllegalArgumentException> { U07Evidence.p95(listOf(-1.0)) }
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    fun `record twenty cold warm full song and cancellation samples through the workspace`() {
        val samples = mutableListOf<JsonObject>()
        val cold = mutableListOf<Double>()
        val warm = mutableListOf<Double>()
        val generation = mutableListOf<Double>()
        val uiCold = mutableListOf<Double>()
        val uiWarm = mutableListOf<Double>()
        val uiDraft = mutableListOf<Double>()
        val cancellation = mutableListOf<Double>()
        val heartbeat = EventThreadProbe()
        val output = U07Evidence.root.resolve("responsiveness.json")
        val identity = U07Evidence.identity()
        val machine = U07Evidence.machine()
        var finished = false
        try {
            repeat(20) { index ->
                val measured = MeasuredUseCases()
                MidiCoreResponsivenessFixture(decorate = measured::wrap).use { fixture ->
                    fixture.prepare()
                    val beforePreview = diskSnapshot(fixture.projectRoot)
                    val preview = MidiCoreWorkspaceIntent.PreviewArrangementStyle("steady-road", "section-1", 41L)
                    uiCold += fixture.apply(preview)
                    assertEquals(MidiCoreArrangementStylePreviewCacheStatus.COLD, measured.preview!!.cacheStatus)
                    cold += measured.previewMs
                    uiWarm += fixture.apply(preview)
                    assertEquals(MidiCoreArrangementStylePreviewCacheStatus.WARM, measured.preview!!.cacheStatus)
                    warm += measured.previewMs
                    assertEquals(beforePreview, diskSnapshot(fixture.projectRoot), "Both previews are write-free")
                    fixture.confirmPlan()
                    val beforeDraft = diskSnapshot(fixture.projectRoot)
                    val bytesBeforeDraft = beforeDraft.keys.sumOf { Files.size(fixture.projectRoot.resolve(it)) }
                    uiDraft += fixture.apply(MidiCoreWorkspaceIntent.CreateArrangementDraft("steady-road", 41L, "measured-$index"))
                    generation += measured.draftMs
                    val plan = assertNotNull(fixture.output.lastPlan)
                    assertEquals(MidiExportRole.entries, plan.view.song.roles.map { it.role })
                    assertTrue(plan.view.song.roles.all { role -> role.events.any { it is MidiNoteEvent } }, "All four roles must actually sound in this fixture")
                    assertEquals(64 * 1920L, plan.view.song.songEndTick)
                    assertEquals(MidiAuditionPlaybackState.PLAYING, fixture.workspace.state.value.audition.playback)

                    // One sample also freezes real accepted work and an export before cancellation.
                    if (index == 0) {
                        fixture.apply(MidiCoreWorkspaceIntent.UseArrangementDraft("measured-$index"))
                        fixture.apply(MidiCoreWorkspaceIntent.ExportPackage)
                    }
                    val projectBeforeCancel = assertNotNull(fixture.workspace.state.value.project)
                    val protected = diskSnapshot(fixture.projectRoot).filterKeys { it != "project.json" }
                    onEdt { fixture.workspace.accept(MidiCoreWorkspaceIntent.StopAudition) }
                    var cancelStarted = 0L
                    measured.progress = { progress ->
                        // Cancel after real publication, on entry to the next generation scope.
                        if (cancelStarted == 0L && progress.completedCount == 1 && progress.activeScope != null) {
                            cancelStarted = System.nanoTime()
                            onEdt { fixture.workspace.accept(MidiCoreWorkspaceIntent.CancelOperation) }
                        }
                    }
                    val id = fixture.start(MidiCoreWorkspaceIntent.CreateArrangementDraft("steady-road", 41L, "cancel-$index"))
                    val cancelled = fixture.await(id)
                    assertTrue(cancelStarted != 0L, "Cancellation must run during real generation")
                    cancellation += elapsedMs(cancelStarted)
                    assertEquals(MidiCoreWorkspaceOperationPhase.CANCELLED, cancelled.operation.phase)
                    val retained = assertNotNull(cancelled.project)
                    assertEquals(projectBeforeCancel.acceptances, retained.acceptances)
                    assertEquals(projectBeforeCancel.acceptedPlannedRests, retained.acceptedPlannedRests)
                    assertEquals(projectBeforeCancel.exportSnapshots, retained.exportSnapshots)
                    assertEquals(projectBeforeCancel.arrangementDrafts, retained.arrangementDrafts, "No incomplete draft publication")
                    protected.forEach { (path, digest) -> assertEquals(digest,
                        U07Evidence.sha256(Files.readAllBytes(fixture.projectRoot.resolve(path))), "Preserve $path") }
                    fixture.assertSourcePreserved()
                    assertEquals(1, fixture.output.maximumSessions)
                    samples += buildJsonObject {
                        put("sample", index + 1)
                        put("sourceSha256", U07Evidence.sha256(Files.readAllBytes(fixture.source)))
                        put("authorityHash", app.melotrail.project.MidiCoreAuthorityHasher.from(projectBeforeCancel).sha256)
                        put("sourceNotes", 256)
                        put("assembledEvents", plan.view.song.roles.sumOf { it.events.size })
                        put("assembledNotes", plan.view.song.roles.sumOf { role -> role.events.count { it is MidiNoteEvent } })
                        put("filesBeforeDraft", beforeDraft.size)
                        put("bytesBeforeDraft", bytesBeforeDraft)
                        put("filesAfterCancellation", diskSnapshot(fixture.projectRoot).size)
                        put("generatorVersions", JsonArray(projectBeforeCancel.candidates.map { it.generatorVersion }.distinct().map(::JsonPrimitive)))
                        put("coldPreparationMs", cold.last()); put("warmPreparationMs", warm.last())
                        put("generationMs", generation.last()); put("cancelToTerminalStateMs", cancellation.last())
                        put("coldUiCompletionMs", uiCold.last()); put("warmUiCompletionMs", uiWarm.last())
                        put("draftUiCompletionMs", uiDraft.last())
                    }
                }
            }
            finished = true
        } finally {
            heartbeat.close()
            U07Evidence.write(output, buildJsonObject {
                put("identity", identity); put("machine", machine)
                put("measurementStatus", if (finished) "MEASURED" else "INCOMPLETE")
                put("fixture", "64 bars, 8 occurrences of 8 bars, 480 PPQ, 120 BPM, 4/4, four roles, steady-road, seed 41")
                put("coldDefinition", "New service graph and empty preview cache per sample; process/JIT and OS disk cache are not cold")
                put("warmDefinition", "Exact same request immediately after cold; COLD/WARM result asserted")
                put("preparationBoundary", "Entry/return of production preview use case, including validation, dispatch and cache lookup; no MIDI output")
                put("generationBoundary", "Entry/return of production draft generation including candidate validation/publication; excludes audition preparation and UI completion")
                put("uiBoundary", "Before EDT intent dispatch to matching terminal workspace state; includes admission, IO, playback-plan preparation and state publication, excludes paint")
                put("cancellationBoundary", "Before EDT CancelOperation dispatch at second scope admission, after one real published scope, to terminal cancelled UI state")
                put("acousticOnset", "UNMEASURED: silent receiver; no physical output warm-up or sound capture")
                put("humanVisualDecision", "NOT_RECORDED")
                put("raw", JsonArray(samples))
                if (cold.isNotEmpty()) put("previewCold", U07Evidence.series(cold, 1_000.0))
                if (warm.isNotEmpty()) put("previewWarm", U07Evidence.series(warm, 300.0))
                if (generation.isNotEmpty()) put("fullDraft", U07Evidence.series(generation, 10_000.0))
                if (cancellation.isNotEmpty()) put("cancellation", U07Evidence.series(cancellation))
                if (uiCold.isNotEmpty()) put("uiCold", U07Evidence.series(uiCold))
                if (uiWarm.isNotEmpty()) put("uiWarm", U07Evidence.series(uiWarm))
                if (uiDraft.isNotEmpty()) put("uiDraft", U07Evidence.series(uiDraft))
                if (heartbeat.samples.isNotEmpty()) put("eventThreadQueueDelay", U07Evidence.series(heartbeat.samples.toList()))
            })
        }
        // Retain raw evidence before rejecting a missed performance target. No automatic budget waiver.
        assertTrue(U07Evidence.p95(cold) <= 1_000 && U07Evidence.p95(warm) <= 300 && U07Evidence.p95(generation) <= 10_000,
            "U07 performance target missed; profile and record a budget decision against $output")
    }

    @Test
    fun `three four and six eight prepare and generate through normal dispatchers`() {
        val results = mutableListOf<JsonObject>()
        listOf(3 to 2, 6 to 3).forEach { (numerator, exponent) ->
            val measured = MeasuredUseCases()
            MidiCoreResponsivenessFixture(bars = 8, numerator = numerator, denominatorExponent = exponent,
                notesPerBar = 6, sectionBars = 2, decorate = measured::wrap).use { fixture ->
                fixture.prepare()
                val ui = fixture.apply(MidiCoreWorkspaceIntent.PreviewArrangementStyle("steady-road", "section-1", 41L))
                fixture.confirmPlan()
                val draftUi = fixture.apply(MidiCoreWorkspaceIntent.CreateArrangementDraft("steady-road", 41L, "meter-smoke"))
                val song = assertNotNull(fixture.output.lastPlan).view.song
                assertEquals(numerator, song.meterNumerator)
                assertEquals(exponent, song.meterDenominatorExponent)
                assertEquals(8 * fixture.ticksPerBar, song.songEndTick)
                assertTrue(song.roles.all { role -> role.events.any { it is MidiNoteEvent } }, "Meter smoke must exercise all four roles")
                fixture.assertSourcePreserved()
                results += buildJsonObject {
                    put("meter", "$numerator/${1 shl exponent}"); put("bars", 8); put("occurrences", 4)
                    put("previewPreparationMs", measured.previewMs); put("draftGenerationMs", measured.draftMs)
                    put("previewUiCompletionMs", ui); put("draftUiCompletionMs", draftUi)
                    put("events", song.roles.sumOf { it.events.size })
                }
            }
        }
        U07Evidence.write(U07Evidence.root.resolve("meter-smoke.json"), buildJsonObject {
            put("identity", U07Evidence.identity()); put("machine", U07Evidence.machine())
            put("smokeOnlyNoPercentileClaim", true); put("results", JsonArray(results))
            put("acousticOnset", "UNMEASURED")
        })
    }

    @Test
    fun `rapid previews cancel the old worker and publish only the latest single session`() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CountDownLatch(1)
        MidiCoreResponsivenessFixture(bars = 8, sectionBars = 4, decorate = { real ->
            object : MidiCoreWorkspaceUseCases by real {
                override suspend fun previewArrangementStyle(request: PrepareMidiCoreArrangementStylePreview): MidiCoreArrangementStylePreviewResult {
                    check(!EventQueue.isDispatchThread())
                    if (request.seed == 1L) {
                        entered.complete(Unit)
                        try { awaitCancellation() } finally { cancelled.countDown() }
                    }
                    return real.previewArrangementStyle(request)
                }
            }
        }).use { fixture ->
            fixture.prepare()
            val before = diskSnapshot(fixture.projectRoot)
            fixture.start(MidiCoreWorkspaceIntent.PreviewArrangementStyle("steady-road", "section-1", 1L))
            runBlocking { withTimeout(10_000) { entered.await() } }
            fixture.apply(MidiCoreWorkspaceIntent.PreviewArrangementStyle("steady-road", "section-0", 41L))
            assertTrue(cancelled.await(10, TimeUnit.SECONDS), "Superseded worker must stop")
            assertEquals("section-0", fixture.workspace.state.value.stylePreview.key?.occurrenceId)
            assertEquals(41L, fixture.workspace.state.value.stylePreview.key?.seed)
            assertEquals(1, fixture.output.maximumSessions)
            assertEquals(before, diskSnapshot(fixture.projectRoot))
        }
    }
}

private class MeasuredUseCases {
    @Volatile var previewMs = 0.0
    @Volatile var draftMs = 0.0
    @Volatile var preview: MidiCoreArrangementStylePreviewResult.Ready? = null
    @Volatile var progress: (MidiCoreArrangementDraftProgress) -> Unit = {}
    fun wrap(real: MidiCoreWorkspaceUseCases): MidiCoreWorkspaceUseCases = object : MidiCoreWorkspaceUseCases by real {
        override suspend fun previewArrangementStyle(request: PrepareMidiCoreArrangementStylePreview): MidiCoreArrangementStylePreviewResult {
            check(!EventQueue.isDispatchThread()) { "Preview preparation ran on the UI event thread" }
            val start = System.nanoTime()
            return real.previewArrangementStyle(request).also {
                previewMs = elapsedMs(start)
                preview = it as? MidiCoreArrangementStylePreviewResult.Ready
            }
        }
        override suspend fun generateArrangementDraft(request: GenerateMidiCoreArrangementDraft): MidiCoreArrangementDraftGenerationResult {
            check(!EventQueue.isDispatchThread()) { "Draft generation ran on the UI event thread" }
            val start = System.nanoTime()
            return real.generateArrangementDraft(request.copy(onProgress = {
                request.onProgress(it)
                progress(it)
            })).also { draftMs = elapsedMs(start) }
        }
        override fun visualEvidence(request: ProjectMidiCoreVisualEvidence): MidiCoreVisualEvidenceProjection {
            check(!EventQueue.isDispatchThread()) { "MIDI projection reads ran on the UI event thread" }
            return real.visualEvidence(request)
        }
    }
}

private fun diskSnapshot(root: Path): Map<String, String> = Files.walk(root).use { paths ->
    paths.filter { Files.isRegularFile(it) }.toList().associate {
        root.relativize(it).toString() to U07Evidence.sha256(Files.readAllBytes(it))
    }
}

/** At most one outstanding heartbeat; it cannot flood or artificially serialize the worker. */
private class EventThreadProbe : AutoCloseable {
    val samples = ConcurrentLinkedQueue<Double>()
    private val pending = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    init {
        scheduler.scheduleAtFixedRate({
            if (pending.compareAndSet(false, true)) {
                val start = System.nanoTime()
                EventQueue.invokeLater { samples.add(elapsedMs(start)); pending.set(false) }
            }
        }, 0, 10, TimeUnit.MILLISECONDS)
    }
    override fun close() {
        scheduler.shutdownNow()
        check(scheduler.awaitTermination(5, TimeUnit.SECONDS))
        onEdt { Unit }
    }
}

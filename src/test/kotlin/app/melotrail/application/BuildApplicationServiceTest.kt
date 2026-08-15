package app.melotrail.application

import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.arrangement.StemRenderResult
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildApplicationServiceTest {
    @Test
    fun `worker readiness failure is an explicit build prerequisite`() = runTest {
        val root = Path.of("build/worker-down")
        val service = DefaultBuildApplicationService(ApprovedArrangement(root), DefaultMixApplicationService(), UnusedRenderer, object : BuildAudioWorker {
            override suspend fun healthCheck() = false
            override suspend fun repair(input: Path, output: Path) = Unit
            override suspend fun master(input: Path, output: Path) = Unit
            override suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int) = false
        })

        val error = assertFailsWith<ApplicationServiceException> { service.build(BuildSongRequest(root)) }
        assertEquals(ApplicationErrorCategory.WORKER, error.category)
        assertEquals("Python worker is not running. Start it with `make worker`.", error.message)
    }

    private class ApprovedArrangement(private val root: Path) : ArrangementApplicationService {
        override suspend fun generate(request: GenerateArrangementRequest, progress: ProgressSink) = error("Not used")
        override suspend fun generateRequiredMidi(root: Path, progress: ProgressSink) = error("Not used")
        override suspend fun renderApprovedStems(root: Path, renderer: InstrumentRenderer, progress: ProgressSink): StemRenderResult = error("Not used")
        override fun load(root: Path) = ArrangementSnapshot(root, emptyList(), approvalRequired = false, approved = true, stale = false, artifact = root.resolve("arrangement.json"))
        override fun preview(root: Path) = error("Not used")
        override fun approve(root: Path) = error("Not used")
    }

    private object UnusedRenderer : InstrumentRenderer {
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult = error("Not used")
    }
}

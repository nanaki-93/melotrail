package app.melotrail.worker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerJobServiceTest {
    @Test
    fun `completed worker response advances the submitted job lifecycle`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val service = WorkerJobService(FakeGateway(WorkerResponse(jobId = "protocol-job", status = WorkerStatus.COMPLETED)), WorkerJobQueue(), scope)

        val job = service.submit(InputInspectionCommand("/input.wav"))
        val completed = awaitTerminal(service, job.id)

        assertEquals(WorkerJobType.INSPECT_INPUT, completed.type)
        assertEquals(WorkerJobStatus.COMPLETED, completed.status)
        assertEquals(1.0, completed.progress)
        assertFalse(completed.result == null)
        scope.cancel()
    }

    @Test
    fun `typed worker failure is retained in the job progress`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val service = WorkerJobService(
            FakeGateway(WorkerResponse(jobId = "protocol-job", status = WorkerStatus.ERROR, error = WorkerError("DecodeError", "Invalid WAV"))),
            WorkerJobQueue(), scope
        )

        val job = service.submit(InputInspectionCommand("/input.wav"))
        val failed = awaitTerminal(service, job.id)

        assertEquals(WorkerJobStatus.FAILED, failed.status)
        assertEquals("Invalid WAV", service.progress(job.id)?.message)
        scope.cancel()
    }

    @Test
    fun `cancellation remains terminal when the in-flight worker call returns`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val gateway = object : WorkerGateway {
            override suspend fun execute(command: WorkerCommand): WorkerResponse {
                started.complete(Unit)
                gate.await()
                return WorkerResponse(jobId = "protocol-job", status = WorkerStatus.COMPLETED)
            }
            override suspend fun healthCheck() = true
        }
        val service = WorkerJobService(gateway, WorkerJobQueue(), scope)

        val job = service.submit(InputInspectionCommand("/input.wav"))
        withTimeout(1_000) { started.await() }
        assertEquals(WorkerJobStatus.CANCELLED, service.cancel(job.id)?.status)
        gate.complete(Unit)
        val cancelled = awaitTerminal(service, job.id)

        assertEquals(WorkerJobStatus.CANCELLED, cancelled.status)
        assertEquals(1.0, cancelled.progress)
        scope.cancel()
    }

    private suspend fun awaitTerminal(service: WorkerJobService, jobId: String): WorkerJob = withTimeout(1_000) {
        while (true) {
            service.get(jobId)?.let { job ->
                if (job.status in setOf(WorkerJobStatus.COMPLETED, WorkerJobStatus.FAILED, WorkerJobStatus.CANCELLED)) return@withTimeout job
            }
            kotlinx.coroutines.yield()
        }
        error("unreachable")
    }

    private class FakeGateway(private val response: WorkerResponse) : WorkerGateway {
        override suspend fun execute(command: WorkerCommand): WorkerResponse = response
        override suspend fun healthCheck(): Boolean = true
    }
}

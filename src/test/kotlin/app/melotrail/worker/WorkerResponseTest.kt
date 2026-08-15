package app.melotrail.worker

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class WorkerResponseTest {
    @Test
    fun `should create valid response`() {
        val output = mapOf("bpm" to JsonPrimitive("120.0"))
        val response = WorkerResponse(
            version = 1,
            jobId = "test-job",
            status = WorkerStatus.COMPLETED,
            output = output
        )

        assertEquals(1, response.version)
        assertEquals("test-job", response.jobId)
        assertEquals(WorkerStatus.COMPLETED, response.status)
        assertEquals(output, response.output)
    }

    @Test
    fun `should create error response`() {
        val error = WorkerError("TestError", "Test error message")
        val response = WorkerResponse(
            version = 1,
            jobId = "test-job",
            status = WorkerStatus.ERROR,
            error = error
        )

        assertEquals(WorkerStatus.ERROR, response.status)
        assertEquals("TestError", response.error?.type)
        assertEquals("Test error message", response.error?.message)
    }

    @Test
    fun `should create progress response`() {
        val response = WorkerResponse(
            version = 1,
            jobId = "test-job",
            status = WorkerStatus.IN_PROGRESS,
            progress = 0.5,
            message = "Processing..."
        )

        assertEquals(WorkerStatus.IN_PROGRESS, response.status)
        assertEquals(0.5, response.progress)
        assertEquals("Processing...", response.message)
    }
}

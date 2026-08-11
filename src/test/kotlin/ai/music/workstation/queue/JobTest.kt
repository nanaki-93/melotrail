package ai.music.workstation.queue

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class JobTest {
    @Test
    fun `should set status and result`() {
        val job = Job(
            id = "test-job",
            type = JobType.ANALYZE,
            request = mapOf("path" to "/test/audio.wav")
        )

        val result = mapOf("bpm" to "120.0", "key" to "A minor")
        val completed = job.withResult(result)

        assertEquals(JobStatus.COMPLETED, completed.status)
        assertEquals(result, completed.result)
    }

    @Test
    fun `should set error status`() {
        val job = Job(
            id = "test-job",
            type = JobType.ANALYZE
        )

        val failed = job.withError("Analysis failed")
        assertEquals(JobStatus.FAILED, failed.status)
        assertEquals("Analysis failed", failed.error)
    }

    @Test
    fun `should update progress`() {
        val job = Job(
            id = "test-job",
            type = JobType.ANALYZE
        )

        val updated = job.withProgress(0.5)
        assertEquals(0.5, updated.progress)
        assertEquals(0.0, job.progress) // Original unchanged
    }

    @Test
    fun `should allow retry when failed`() {
        val job = Job(
            id = "test-job",
            type = JobType.ANALYZE,
            status = JobStatus.FAILED,
            retryCount = 0,
            maxRetries = 3
        )

        assertTrue(job.canRetry())
    }

    @Test
    fun `should not allow retry when exhausted`() {
        val job = Job(
            id = "test-job",
            type = JobType.ANALYZE,
            status = JobStatus.FAILED,
            retryCount = 3,
            maxRetries = 3
        )

        assertFalse(job.canRetry())
    }

    @Test
    fun `should not allow retry when completed`() {
        val job = Job(
            id = "test-job",
            type = JobType.ANALYZE,
            status = JobStatus.COMPLETED
        )

        assertFalse(job.canRetry())
    }
}

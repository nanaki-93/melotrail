package app.melotrail.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerResponseMapperTest {
    @Test
    fun `successful worker response preserves its typed completed payload`() {
        val response = WorkerResponseMapper.fromHttp(
            "job-1",
            WorkerHttpResponse(200, """{"version":1,"jobId":"job-1","status":"completed","output":{"bpm":120}}""")
        )

        assertEquals(WorkerStatus.COMPLETED, response.status)
        assertEquals("120", response.output?.get("bpm")?.toString())
    }

    @Test
    fun `worker HTTP error preserves its typed error contract`() {
        val response = WorkerResponseMapper.fromHttp(
            "job-1",
            WorkerHttpResponse(422, """{"status":"error","error":{"type":"DecodeError","message":"Invalid WAV"}}""")
        )

        assertEquals(WorkerStatus.ERROR, response.status)
        assertEquals(WorkerError("DecodeError", "Invalid WAV"), response.error)
    }

    @Test
    fun `invalid successful response is a typed mapping error`() {
        val response = WorkerResponseMapper.fromHttp("job-1", WorkerHttpResponse(200, "not-json"))

        assertEquals(WorkerStatus.ERROR, response.status)
        assertEquals("ResponseMappingError", response.error?.type)
    }
}

package app.melotrail.worker

import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranscribeCommandTest {
    @Test
    fun `transcribe command maps only to its endpoint with exact request fields`() {
        val command = TranscribeCommand("/input.wav", "/output.mid", "piano")

        val request = WorkerProtocol.requestFor(command, "job-1")

        assertEquals("/transcribe", WorkerProtocol.endpointFor(command))
        assertEquals("job-1", request["jobId"]?.jsonPrimitive?.content)
        assertEquals("/input.wav", request["path"]?.jsonPrimitive?.content)
        assertEquals("/output.mid", request["outputPath"]?.jsonPrimitive?.content)
        assertEquals("piano", request["instrument"]?.jsonPrimitive?.content)
        assertEquals(setOf("jobId", "path", "outputPath", "instrument"), request.keys)
    }
}

package ai.music.workstation.worker

import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranscribeCommandTest {
    @Test
    fun `transcribe command maps only to its endpoint with exact request fields`() {
        val logger = DefaultLogger()
        val client = WorkerClient(logger = logger, errorReporter = ErrorReporter(logger))
        val command = TranscribeCommand("/input.wav", "/output.mid", "piano")

        val request = client.buildRequest(command, "job-1")

        assertEquals("/transcribe", client.endpointFor(command))
        assertEquals("job-1", request["jobId"]?.jsonPrimitive?.content)
        assertEquals("/input.wav", request["path"]?.jsonPrimitive?.content)
        assertEquals("/output.mid", request["outputPath"]?.jsonPrimitive?.content)
        assertEquals("piano", request["instrument"]?.jsonPrimitive?.content)
        assertEquals(setOf("jobId", "path", "outputPath", "instrument"), request.keys)
    }
}

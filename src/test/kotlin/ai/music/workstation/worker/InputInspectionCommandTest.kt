package ai.music.workstation.worker

import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputInspectionCommandTest {
    @Test
    fun `input inspection maps only its path to its endpoint`() {
        val logger = DefaultLogger()
        val client = WorkerClient(logger = logger, errorReporter = ErrorReporter(logger))
        val request = client.buildRequest(InputInspectionCommand("/project/source/intro.wav"), "job-inspect")

        assertEquals("/inspect-input", client.endpointFor(InputInspectionCommand("x.wav")))
        assertEquals("job-inspect", request["jobId"]?.jsonPrimitive?.content)
        assertEquals("/project/source/intro.wav", request["path"]?.jsonPrimitive?.content)
        assertEquals(2, request.size)
    }
}

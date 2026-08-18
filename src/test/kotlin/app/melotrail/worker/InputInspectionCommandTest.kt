package app.melotrail.worker

import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputInspectionCommandTest {
    @Test
    fun `input inspection maps only its path to its endpoint`() {
        val request = WorkerProtocol.requestFor(InputInspectionCommand("/project/source/intro.wav"), "job-inspect")

        assertEquals("/inspect-input", WorkerProtocol.endpointFor(InputInspectionCommand("x.wav")))
        assertEquals("job-inspect", request["jobId"]?.jsonPrimitive?.content)
        assertEquals("/project/source/intro.wav", request["path"]?.jsonPrimitive?.content)
        assertEquals(2, request.size)
    }
}

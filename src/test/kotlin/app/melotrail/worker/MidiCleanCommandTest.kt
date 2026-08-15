package app.melotrail.worker

import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.DefaultLogger
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MidiCleanCommandTest {
    @Test
    fun `midi cleanup command maps only to its endpoint with exact request fields`() {
        val logger = DefaultLogger()
        val client = WorkerClient(logger = logger, errorReporter = ErrorReporter(logger))
        val command = MidiCleanCommand(
            path = "/raw.mid",
            outputPath = "/clean.mid",
            quantize = "1/16",
            strength = 0.4,
            minNoteMs = 50,
            minVelocity = 8,
            normalizeVelocity = true,
            cleanSustain = true
        )

        val request = client.buildRequest(command, "job-2")

        assertEquals("/midi-clean", client.endpointFor(command))
        assertEquals("job-2", request["jobId"]?.jsonPrimitive?.content)
        assertEquals("/raw.mid", request["path"]?.jsonPrimitive?.content)
        assertEquals("/clean.mid", request["outputPath"]?.jsonPrimitive?.content)
        assertEquals("1/16", request["quantize"]?.jsonPrimitive?.content)
        assertEquals("0.4", request["strength"]?.jsonPrimitive?.content)
        assertEquals("50", request["minNoteMs"]?.jsonPrimitive?.content)
        assertEquals("8", request["minVelocity"]?.jsonPrimitive?.content)
        assertEquals("true", request["normalizeVelocity"]?.jsonPrimitive?.content)
        assertEquals("true", request["cleanSustain"]?.jsonPrimitive?.content)
    }
}

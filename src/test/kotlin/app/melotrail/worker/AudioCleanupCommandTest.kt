package app.melotrail.worker

import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.DefaultLogger
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AudioCleanupCommandTest {
    @Test
    fun `cleanup command has a strict typed endpoint mapping`() {
        val logger = DefaultLogger()
        val client = WorkerClient(logger = logger, errorReporter = ErrorReporter(logger))
        val command = AudioCleanupCommand(
            path = "/project/source/intro.wav",
            outputPath = "/project/prepared/intro/clean.wav",
            operations = listOf(AudioCleanupOperation.DcRemoval, AudioCleanupOperation.HumRemoval(50), AudioCleanupOperation.NoiseReduction(0.35))
        )

        val request = client.buildRequest(command, "job-cleanup")

        assertEquals("/cleanup", client.endpointFor(command))
        assertEquals("job-cleanup", request["jobId"]?.jsonPrimitive?.content)
        assertEquals("/project/source/intro.wav", request["path"]?.jsonPrimitive?.content)
        assertEquals("/project/prepared/intro/clean.wav", request["outputPath"]?.jsonPrimitive?.content)
        val operations = request["operations"]?.jsonArray.orEmpty()
        assertEquals(listOf("dc_removal", "hum_removal", "noise_reduction"), operations.map { it.jsonObject["type"]?.jsonPrimitive?.content })
        assertEquals("50", operations[1].jsonObject["params"]?.jsonObject?.get("frequencyHz")?.jsonPrimitive?.content)
        assertEquals("0.35", operations[2].jsonObject["params"]?.jsonObject?.get("strength")?.jsonPrimitive?.content)
    }

    @Test
    fun `cleanup operation bounds are enforced before the worker boundary`() {
        assertThrows(IllegalArgumentException::class.java) { AudioCleanupOperation.ClipRepair(0.9) }
        assertThrows(IllegalArgumentException::class.java) { AudioCleanupOperation.HumRemoval(55) }
        assertThrows(IllegalArgumentException::class.java) { AudioCleanupOperation.NoiseReduction(0.8) }
    }
}

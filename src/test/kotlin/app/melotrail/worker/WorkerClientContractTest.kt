package app.melotrail.worker

import app.melotrail.errors.ErrorReporter
import app.melotrail.logging.DefaultLogger
import app.melotrail.model.DSPSettings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration

class WorkerClientContractTest {
    @Test
    fun `every supported command receives the same typed completed response contract`() = runBlocking {
        val commands = listOf<WorkerCommand>(
            AnalyzeCommand("/input.wav"),
            ApplyDSPCommand("/input.wav", DSPSettings()),
            RepairCommand("/input.wav", listOf(RepairSpec("declick"))),
            MasterCommand("/input.wav", emptyMap()),
            MP3ConvertCommand("/input.mp3", "/output.wav"),
            MP3ExportCommand("/master.wav", "/song.mp3"),
            TranscribeCommand("/input.wav", "/raw.mid", "piano"),
            MidiCleanCommand("/raw.mid", "/clean.mid"),
            InputInspectionCommand("/input.wav"),
            AudioCleanupCommand("/input.wav", "/clean.wav", listOf(AudioCleanupOperation.DcRemoval))
        )

        commands.forEach { command ->
            val transport = CompletedTransport()
            val logger = DefaultLogger()
            val client = WorkerClient(logger = logger, errorReporter = ErrorReporter(logger), transport = transport)

            val response = client.execute(command)

            assertEquals(WorkerProtocol.endpointFor(command), transport.path)
            assertEquals(WorkerStatus.COMPLETED, response.status)
            assertEquals("true", response.output?.get("accepted")?.toString())
        }
    }

    @Test
    fun `health and readiness use bounded health metadata only`() = runBlocking {
        val transport = HealthTransport()
        val logger = DefaultLogger()
        val client = WorkerClient(
            baseUrl = "http://127.0.0.1:8081",
            logger = logger,
            errorReporter = ErrorReporter(logger),
            transport = transport
        )

        assertTrue(client.healthCheck())
        assertEquals(WorkerRuntimeStatus(true, true, "1.0.0", true), client.runtimeStatus())
        assertEquals(listOf("GET" to "/health", "GET" to "/health"), transport.requests)
        assertFalse(transport.requestBodies.any { it?.contains("path") == true })
        assertEquals("Python worker is not running at http://127.0.0.1:8081", client.unavailableMessage)
    }

    private class CompletedTransport : WorkerHttpTransport {
        var path: String? = null

        override fun request(method: String, path: String, body: String?, timeout: Duration): WorkerHttpResponse {
            this.path = path
            return WorkerHttpResponse(200, """{"version":1,"jobId":"job-1","status":"completed","output":{"accepted":true}}""")
        }
    }

    private class HealthTransport : WorkerHttpTransport {
        val requests = mutableListOf<Pair<String, String>>()
        val requestBodies = mutableListOf<String?>()

        override fun request(method: String, path: String, body: String?, timeout: Duration): WorkerHttpResponse {
            requests += method to path
            requestBodies += body
            return WorkerHttpResponse(200, """{"status":"ok","version":"1.0.0","transcriptionRuntime":true,"mp3ExportRuntime":true}""")
        }
    }
}

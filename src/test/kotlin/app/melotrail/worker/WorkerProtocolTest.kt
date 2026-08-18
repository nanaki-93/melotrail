package app.melotrail.worker

import app.melotrail.model.DSPSettings
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkerProtocolTest {
    @Test
    fun `every supported Python endpoint has one exact command mapping`() {
        val commands = listOf(
            AnalyzeCommand("/input.wav") to "/analyze",
            ApplyDSPCommand("/input.wav", DSPSettings()) to "/apply_dsp",
            RepairCommand("/input.wav", listOf(RepairSpec("declick")), "/output.wav") to "/repair",
            MasterCommand("/input.wav", mapOf("targetLufs" to -14), "/output.wav") to "/master",
            MP3ConvertCommand("/input.mp3", "/output.wav") to "/mp3_convert",
            MP3ExportCommand("/master.wav", "/song.mp3", 320) to "/mp3_export",
            TranscribeCommand("/input.wav", "/raw.mid", "piano") to "/transcribe",
            MidiCleanCommand("/raw.mid", "/clean.mid") to "/midi-clean",
            InputInspectionCommand("/input.wav") to "/inspect-input",
            AudioCleanupCommand("/input.wav", "/clean.wav", listOf(AudioCleanupOperation.DcRemoval)) to "/cleanup"
        )

        commands.forEach { (command, endpoint) ->
            assertEquals(endpoint, WorkerProtocol.endpointFor(command))
            assertEquals("job-1", WorkerProtocol.requestFor(command, "job-1")["jobId"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `protocol preserves every command payload field name`() {
        val analyze = WorkerProtocol.requestFor(AnalyzeCommand("/input.wav"), "job-1")
        assertEquals(setOf("jobId", "path", "options"), analyze.keys)
        assertEquals("true", analyze["options"]!!.jsonObject["detectBPM"]!!.jsonPrimitive.content)

        val dsp = WorkerProtocol.requestFor(ApplyDSPCommand("/input.wav", DSPSettings(), "wav"), "job-1")
        assertEquals(setOf("jobId", "path", "settings", "outputFormat"), dsp.keys)

        val repair = WorkerProtocol.requestFor(RepairCommand("/input.wav", listOf(RepairSpec("declick")), "/output.wav"), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath", "repairs"), repair.keys)

        val master = WorkerProtocol.requestFor(MasterCommand("/input.wav", mapOf("targetLufs" to -14), "/output.wav"), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath", "settings"), master.keys)

        val convert = WorkerProtocol.requestFor(MP3ConvertCommand("/input.mp3", "/output.wav"), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath"), convert.keys)

        val export = WorkerProtocol.requestFor(MP3ExportCommand("/master.wav", "/song.mp3", 192), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath", "bitrateKbps"), export.keys)

        val transcribe = WorkerProtocol.requestFor(TranscribeCommand("/input.wav", "/raw.mid", "piano"), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath", "instrument"), transcribe.keys)

        val clean = WorkerProtocol.requestFor(MidiCleanCommand("/raw.mid", "/clean.mid", quantize = "1/16", strength = 0.4), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath", "version", "profile", "quantize", "strength", "minNoteMs", "minVelocity", "normalizeVelocity", "cleanSustain"), clean.keys)

        val inspect = WorkerProtocol.requestFor(InputInspectionCommand("/input.wav"), "job-1")
        assertEquals(setOf("jobId", "path"), inspect.keys)

        val cleanup = WorkerProtocol.requestFor(AudioCleanupCommand("/input.wav", "/clean.wav", listOf(AudioCleanupOperation.HumRemoval(50))), "job-1")
        assertEquals(setOf("jobId", "path", "outputPath", "operations"), cleanup.keys)
        val operation = cleanup["operations"]!!.jsonArray.single().jsonObject
        assertEquals("hum_removal", operation["type"]!!.jsonPrimitive.content)
        assertEquals("50", operation["params"]!!.jsonObject["frequencyHz"]!!.jsonPrimitive.content)
    }
}

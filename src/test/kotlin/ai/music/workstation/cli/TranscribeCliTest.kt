package ai.music.workstation.cli

import ai.music.workstation.worker.WorkerError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TranscribeCliTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `transcribe parser accepts valid arguments`() {
        val input = tempDir.resolve("verse.WAV").also { Files.writeString(it, "fixture") }
        val output = tempDir.resolve("midi/raw/A.mid")

        val options = ArrangementProjectCommands.parseTranscribeOptions(
            listOf("--input", input.toString(), "--output", output.toString(), "--instrument", "piano")
        )

        assertEquals(input.toAbsolutePath().normalize(), options.input)
        assertEquals(output.toAbsolutePath().normalize(), options.output)
        assertEquals("piano", options.instrument)
    }

    @Test
    fun `transcribe parser rejects duplicate or missing values`() {
        val input = tempDir.resolve("verse.wav").also { Files.writeString(it, "fixture") }

        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseTranscribeOptions(
                listOf("--input", input.toString(), "--input", input.toString(), "--output", "result.mid", "--instrument", "piano")
            )
        }
        val missing = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseTranscribeOptions(listOf("--input"))
        }

        assertEquals("Duplicate option: --input", duplicate.message)
        assertEquals("Missing value for --input", missing.message)
    }

    @Test
    fun `worker error becomes stage-specific transcription failure`() {
        assertEquals(
            "Transcription failed during model inference: Basic Pitch is unavailable",
            ArrangementProjectCommands.transcriptionFailureMessage(
                WorkerError("ModelError", "Basic Pitch is unavailable")
            )
        )
    }
}

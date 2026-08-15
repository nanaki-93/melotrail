package app.melotrail.cli

import app.melotrail.worker.WorkerError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MidiCleanCliTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `midi cleanup parser uses conservative defaults and accepts named profiles`() {
        val input = tempDir.resolve("raw.MID").also { Files.writeString(it, "fixture") }
        val output = tempDir.resolve("midi/clean/A.mid")

        val defaults = ArrangementProjectCommands.parseMidiCleanOptions(
            listOf("--input", input.toString(), "--output", output.toString())
        )
        val configured = ArrangementProjectCommands.parseMidiCleanOptions(
            listOf("--input", input.toString(), "--output", output.toString(), "--profile", "tighten-timing", "--quantize", "1/16", "--strength", "0.4", "--min-note-ms", "60", "--min-velocity", "9", "--normalize-velocity", "--clean-sustain")
        )

        assertEquals("conservative", defaults.profile)
        assertEquals(null, defaults.quantize)
        assertEquals(0.0, defaults.strength)
        assertEquals(50, defaults.minNoteMs)
        assertEquals(8, defaults.minVelocity)
        assertEquals("tighten-timing", configured.profile)
        assertEquals("1/16", configured.quantize)
        assertEquals(0.4, configured.strength)
        assertEquals(60, configured.minNoteMs)
        assertEquals(9, configured.minVelocity)
        assertEquals(true, configured.normalizeVelocity)
        assertEquals(true, configured.cleanSustain)
    }

    @Test
    fun `midi cleanup parser rejects ambiguous profile quantization and duplicate flags`() {
        val input = tempDir.resolve("raw.mid").also { Files.writeString(it, "fixture") }
        val output = tempDir.resolve("clean.mid")
        val invalidGrid = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseMidiCleanOptions(listOf("--input", input.toString(), "--output", output.toString(), "--quantize", "1/12"))
        }
        val detachedStrength = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseMidiCleanOptions(listOf("--input", input.toString(), "--output", output.toString(), "--strength", "0.4"))
        }
        val conservativeGrid = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseMidiCleanOptions(listOf("--input", input.toString(), "--output", output.toString(), "--quantize", "1/16"))
        }
        val missingStrength = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseMidiCleanOptions(listOf("--input", input.toString(), "--output", output.toString(), "--profile", "tighten-timing", "--quantize", "1/16"))
        }
        val conservativeSustain = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseMidiCleanOptions(listOf("--input", input.toString(), "--output", output.toString(), "--clean-sustain"))
        }
        val duplicateFlag = assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.parseMidiCleanOptions(listOf("--input", input.toString(), "--output", output.toString(), "--clean-sustain", "--clean-sustain"))
        }

        assertEquals("Quantize must be one of: 1/4, 1/8, 1/16, 1/32", invalidGrid.message)
        assertEquals("--strength requires --quantize", detachedStrength.message)
        assertEquals("--quantize requires --profile tighten-timing", conservativeGrid.message)
        assertEquals("--profile tighten-timing requires --strength from 0.0 to 1.0 (exclusive of 0.0)", missingStrength.message)
        assertEquals("--normalize-velocity and --clean-sustain require --profile transcription-safe or tighten-timing", conservativeSustain.message)
        assertEquals("Duplicate option: --clean-sustain", duplicateFlag.message)
    }

    @Test
    fun `midi cleanup worker error becomes stage specific failure`() {
        assertEquals(
            "MIDI cleanup failed during validation: Invalid grid",
            ArrangementProjectCommands.midiCleanupFailureMessage(
                WorkerError("MidiCleanupValidationError", "Invalid grid")
            )
        )
    }
}

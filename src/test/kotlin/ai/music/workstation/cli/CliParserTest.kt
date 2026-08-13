package ai.music.workstation.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CliParserTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `rejects MP3 output because the processing pipeline writes WAV`() {
        val input = inputFile()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            CliParser.parse(arrayOf("--input", input.toString(), "--output", "master.mp3"))
        }

        assertEquals("Output must be a .wav file. MP3 export is a separate final step.", exception.message)
    }

    @Test
    fun `accepts a WAV master output path`() {
        val input = inputFile()

        val args = CliParser.parse(arrayOf("--input", input.toString(), "--output", "master.wav"))

        assertEquals("master.wav", args.outputPath)
    }

    @Test
    fun `help text includes the lossless pipeline contract`() {
        val usage = CliParser.usage()

        org.junit.jupiter.api.Assertions.assertTrue(usage.contains("Output WAV file"))
        org.junit.jupiter.api.Assertions.assertTrue(ArrangementProjectCommands.usage().contains("generate bass|drums|pad|strings|transitions"))
    }

    private fun inputFile(): Path = tempDir.resolve("mix.wav").also { Files.writeString(it, "fixture") }
}

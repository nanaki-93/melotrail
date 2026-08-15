package ai.music.workstation.cli

import ai.music.workstation.arrangement.MidiReferences
import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.application.MidiPreparationService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class UnifiedInputAdapterTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `v2 creation stores explicit format and round trips through project store`() {
        val root = tempDir.resolve("song")
        ArrangementProjectCommands.execute(arrayOf("project", "create", root.toString(), "--sample-rate", "48000", "--channels", "1"))

        val project = ProjectStore.read(root)

        assertEquals(2, project.version)
        assertEquals(48_000, project.renderFormat?.sampleRate)
        assertEquals(1, project.renderFormat?.channels)
        assertEquals(24, project.renderFormat?.bitDepth)
        assertTrue(Files.isDirectory(root.resolve("midi/generated")))
    }

    @Test
    fun `direct MIDI copies source and registers a clean MIDI artifact`() {
        val root = createProject("midi")
        val input = midiFile("verse.mid")

        ArrangementProjectCommands.executePartAddForTest(
            arrayOf("part", "add", root.toString(), "--id", "A", "--file", input.toString(), "--role", "verse"),
            CopyingPreparationWorker()
        )

        val project = ProjectStore.read(root)
        val part = project.parts.single()
        assertEquals("source/A.mid", part.file)
        assertEquals(null, part.midi?.raw)
        assertEquals("midi/clean/A.mid", part.midi?.clean)
        assertTrue(Files.readAllBytes(input).contentEquals(Files.readAllBytes(root.resolve(part.file))))
        assertMidi(root.resolve(requireNotNull(part.midi).clean))
        assertEquals(listOf(root.resolve("midi/clean/A.mid")), project.requireCleanMidi(root))
    }

    @Test
    fun `audio WAV and MP3 imports use transcription and cleanup without changing originals`() {
        listOf("wav", "mp3").forEachIndexed { index, extension ->
            val root = createProject("audio-$extension")
            val input = tempDir.resolve("input-$extension.$extension").also { Files.write(it, "original-$extension".encodeToByteArray()) }
            val before = Files.readAllBytes(input)

            ArrangementProjectCommands.executePartAddForTest(
                arrayOf("part", "add", root.toString(), "--id", "P$index", "--file", input.toString(), "--transcribe"),
                CopyingPreparationWorker()
            )

            val part = ProjectStore.read(root).parts.single()
            assertEquals("midi/raw/P$index.mid", part.midi?.raw)
            assertMidi(root.resolve(requireNotNull(part.midi).clean))
            assertTrue(before.contentEquals(Files.readAllBytes(input)))
            assertTrue(before.contentEquals(Files.readAllBytes(root.resolve(part.file))))
        }
    }

    @Test
    fun `failure leaves copied source and unregistered artifacts but does not update metadata`() {
        val root = createProject("failure")
        val input = tempDir.resolve("verse.wav").also { Files.writeString(it, "source") }

        val error = assertThrows(IllegalStateException::class.java) {
            ArrangementProjectCommands.executePartAddForTest(
                arrayOf("part", "add", root.toString(), "--id", "A", "--file", input.toString(), "--transcribe"),
                object : MidiPreparationService {
                    override suspend fun transcribe(input: Path, output: Path) = writeMidi(output)
                    override suspend fun clean(input: Path, output: Path) = error("cleanup unavailable")
                }
            )
        }

        assertTrue(error.message.orEmpty().contains("was not registered"))
        assertTrue(Files.isRegularFile(root.resolve("source/A.wav")))
        assertTrue(Files.isRegularFile(root.resolve("midi/raw/A.mid")))
        assertFalse(Files.exists(root.resolve("midi/clean/A.mid")))
        assertTrue(ProjectStore.read(root).parts.isEmpty())
    }

    @Test
    fun `v1 metadata upgrades non-destructively only when clean MIDI is available`() {
        val root = tempDir.resolve("legacy")
        Files.createDirectories(root.resolve("parts"))
        Files.writeString(root.resolve("parts/A.wav"), "legacy source")
        writeMidi(root.resolve("midi/clean/A.mid"))
        val legacy = Project(name = "legacy", parts = listOf(Part("A", "parts/A.wav")))
        ProjectStore.write(root, legacy)
        val sourceBefore = Files.readAllBytes(root.resolve("parts/A.wav"))

        val upgraded = ProjectStore.upgrade(root, ProjectStore.read(root), listOf(
            Part("A", "parts/A.wav", midi = MidiReferences(clean = "midi/clean/A.mid"))
        ))

        assertEquals(2, upgraded.version)
        assertEquals("parts/A.wav", upgraded.parts.single().file)
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve("parts/A.wav"))))
        assertTrue("sourceFile" in Json.parseToJsonElement(Files.readString(root.resolve("project.json"))).jsonObject["parts"]!!.jsonArray.first().jsonObject)
    }

    @Test
    fun `v2 input validation rejects bad flags formats and render options before registration`() {
        val root = createProject("validation")
        val audio = tempDir.resolve("verse.wav").also { Files.writeString(it, "audio") }
        val midi = midiFile("verse.mid")

        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executePartAddForTest(arrayOf("part", "add", root.toString(), "--id", "A", "--file", audio.toString()), CopyingPreparationWorker())
        }.message.orEmpty().contains("requires --transcribe"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executePartAddForTest(arrayOf("part", "add", root.toString(), "--id", "A", "--file", midi.toString(), "--transcribe"), CopyingPreparationWorker())
        }.message.orEmpty().contains("only valid for audio"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.execute(arrayOf("project", "create", tempDir.resolve("bad").toString(), "--channels", "0"))
        }.message.orEmpty().contains("--channels"))
        ArrangementProjectCommands.executePartAddForTest(
            arrayOf("part", "add", root.toString(), "--id", "A", "--file", midi.toString()), CopyingPreparationWorker()
        )
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executePartAddForTest(
                arrayOf("part", "add", root.toString(), "--id", "A", "--file", midi.toString()), CopyingPreparationWorker()
            )
        }.message.orEmpty().contains("Part ID already exists"))
        val overwriteRoot = createProject("overwrite")
        val existingSource = overwriteRoot.resolve("source/A.mid").also(::writeMidi)
        val before = Files.readAllBytes(existingSource)
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            ArrangementProjectCommands.executePartAddForTest(
                arrayOf("part", "add", overwriteRoot.toString(), "--id", "A", "--file", existingSource.toString()), CopyingPreparationWorker()
            )
        }.message.orEmpty().contains("destination paths must differ"))
        assertTrue(before.contentEquals(Files.readAllBytes(existingSource)))
        assertEquals(1, ProjectStore.read(root).parts.size)
    }

    private fun createProject(name: String): Path = tempDir.resolve(name).also {
        ArrangementProjectCommands.execute(arrayOf("project", "create", it.toString()))
    }

    private fun midiFile(name: String): Path = tempDir.resolve(name).also(::writeMidi)

    private class CopyingPreparationWorker : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path) = writeMidi(output)
        override suspend fun clean(input: Path, output: Path) { Files.copy(input, output) }
    }

    private fun assertMidi(path: Path) = assertEquals("MThd", Files.readAllBytes(path).copyOfRange(0, 4).decodeToString())

    private companion object {
        fun writeMidi(path: Path) {
            Files.createDirectories(checkNotNull(path.parent))
            val sequence = Sequence(Sequence.PPQ, 480)
            val track = sequence.createTrack()
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 96), 0))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
            require(MidiSystem.write(sequence, 1, path.toFile()) > 0) { "Could not write test MIDI" }
        }
    }
}

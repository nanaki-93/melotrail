package app.melotrail.documentation

import app.melotrail.arrangement.MidiQualityReportStore
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InputInspectionPaths
import app.melotrail.preparation.TranscriptionFailureStage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MidiImportProcessDocumentationTest {
    private val guidePath = Path.of("docs/MIDI_IMPORT_PROCESS.md")
    private val guide = Files.readString(guidePath)

    @Test
    fun `guide links the current README workflow worker and import help`() {
        assertTrue(Files.isRegularFile(guidePath))
        assertTrue(Files.readString(Path.of("README.md")).contains("[MIDI import process](docs/MIDI_IMPORT_PROCESS.md)"))
        assertTrue(Files.readString(Path.of("docs/TRACK_PROCESS_WORKFLOW.md")).contains("[MIDI import process](MIDI_IMPORT_PROCESS.md)"))
        assertTrue(Files.readString(Path.of("worker/README.md")).contains("POST /inspect-input"))
        assertTrue(Files.readString(Path.of("desktopApp/src/main/kotlin/app/melotrail/desktop/WorkspacePageRouter.kt")).contains("docs/MIDI_IMPORT_PROCESS.md"))
        assertTrue(guide.contains("[`worker/README.md`](../worker/README.md)"))
    }

    @Test
    fun `guide agrees with inspection and transcription contracts`() {
        assertEquals(setOf("mid", "midi"), InputContainer.MIDI.extensions)
        assertEquals(setOf("wav", "wave"), InputContainer.RIFF_WAVE.extensions)
        assertEquals(setOf("mp3"), InputContainer.MPEG_AUDIO.extensions)
        TranscriptionFailureStage.entries.forEach { stage -> assertTrue(TranscriptionFailureStage.entries.contains(stage)) }

        val root = Files.createTempDirectory("midi-import-docs")
        assertEquals("prepared/A/report.json", relative(root, InputInspectionPaths.report(root, "A")))
        assertEquals("prepared/A/decoded.wav", relative(root, InputInspectionPaths.decodedWav(root, "A")))
        assertEquals("prepared/A/clean.wav", relative(root, InputInspectionPaths.cleanWav(root, "A")))
        assertEquals("midi/raw/A.mid", relative(root, InputInspectionPaths.rawMidi(root, "A")))
        assertEquals("midi/quality/A.json", relative(root, MidiQualityReportStore.path(root, "A")))
        listOf(
            "`.mid` and `.midi`", "`prepared/<part>/report.json`", "`prepared/<part>/decoded.wav`",
            "`prepared/<part>/clean.wav`", "`midi/raw/<part>.mid`", "`midi/clean/<part>.mid`",
            "`midi/quality/<part>.json`", "`midi/derived/<part>/lofi-80-swing-v1.mid`",
            "`midi/feel/<part>/lofi-80-swing-v1.json`"
        ).forEach { expected -> assertTrue(guide.contains(expected), "Guide must contain $expected") }
    }

    private fun relative(root: Path, path: Path): String = root.relativize(path).toString().replace('\\', '/')
}

package ai.music.workstation.cli

import ai.music.workstation.arrangement.Arrangement
import ai.music.workstation.arrangement.ArrangementSection
import ai.music.workstation.arrangement.ArrangementStore
import ai.music.workstation.arrangement.InstrumentMode
import ai.music.workstation.arrangement.InstrumentPlan
import ai.music.workstation.arrangement.MidiReferences
import ai.music.workstation.arrangement.Part
import ai.music.workstation.arrangement.Project
import ai.music.workstation.arrangement.ProjectStore
import ai.music.workstation.arrangement.RenderFormat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class MidiAndLicenseCommandsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `v2 part analyze uses clean MIDI locally and license report exposes only logical metadata`() {
        val clean = root.resolve("midi/clean/A.mid")
        Files.createDirectories(clean.parent)
        val sequence = Sequence(Sequence.PPQ, 480)
        val track = sequence.createTrack()
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0))
        track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
        MidiSystem.write(sequence, 1, clean.toFile())
        val source = root.resolve("source/A.mid"); Files.createDirectories(source.parent); Files.copy(clean, source)
        val project = Project(Project.CURRENT_VERSION, "demo", listOf(Part("A", "source/A.mid", midi = MidiReferences(clean = "midi/clean/A.mid"))), renderFormat = RenderFormat())
        ProjectStore.write(root, project)

        val analysis = ArrangementProjectCommands.execute(arrayOf("part", "analyze", root.toString(), "--id", "A"))
        assertTrue(analysis.contains("Analyzed MIDI part"))
        ArrangementStore.write(root, ProjectStore.read(root), Arrangement(sections = listOf(
            ArrangementSection(0, "A", listOf(InstrumentPlan("source", InstrumentMode.SOURCE), InstrumentPlan("bass", InstrumentMode.GENERATED, density = 0.3)))
        )))
        val licenses = ArrangementProjectCommands.execute(arrayOf("licenses", root.toString(), "--commercial"))
        assertTrue(licenses.contains("bass:"))
        assertTrue(licenses.contains("commercialUse=true"))
        assertTrue(!licenses.contains(".sfz") && !licenses.contains("samples"))
    }
}

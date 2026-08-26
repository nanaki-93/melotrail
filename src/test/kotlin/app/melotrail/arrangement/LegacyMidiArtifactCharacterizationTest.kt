package app.melotrail.arrangement

import app.melotrail.midi.OwnedMidiFixtures
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Characterizes only legacy behavior that the MIDI Core may extract later.
 * It deliberately does not make the audio-era schema or stage graph a target contract.
 */
class LegacyMidiArtifactCharacterizationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `legacy analyzer pairs velocity-zero notes and keeps tempo meter and track-name facts`() {
        OwnedMidiFixtures.writeAll(root)

        val analyzer = MidiPartAnalyzer()
        val formatZero = analyzer.analyze(root.resolve("smf0-melody.mid"), "format-zero")
        val velocityZero = analyzer.analyze(root.resolve("velocity-zero-note-off.mid"), "velocity-zero")
        val formatOne = analyzer.analyze(root.resolve("smf1-reference-tracks.mid"), "format-one")

        assertEquals(480, formatZero.ppq)
        assertEquals(listOf(MidiTempoChange(0, 120.0)), formatZero.tempoMap)
        assertEquals(listOf(MidiTimeSignature(0, 4, 4)), formatZero.timeSignatures)
        assertEquals(1, velocityZero.noteCount)
        assertEquals(480, velocityZero.durationTicks)
        assertEquals(2, formatOne.noteCount)

        val names = MidiSystem.getSequence(root.resolve("smf1-reference-tracks.mid").toFile()).tracks.mapNotNull { track ->
            (0 until track.size()).map(track::get).firstNotNullOfOrNull { event ->
                (event.message as? MetaMessage)?.takeIf { it.type == 0x03 }?.data?.toString(StandardCharsets.UTF_8)
            }
        }
        assertEquals(listOf("Conductor", "Melody", "Reference"), names)
    }

    @Test
    fun `legacy hash confinement and immutable publication reject tampering and replacement`() {
        val source = OwnedMidiFixtures.writeAll(root).first { it.fileName.toString() == "smf0-melody.mid" }
        val candidate = root.resolve("candidates/chords/fixture.mid")
        Files.createDirectories(requireNotNull(candidate.parent))
        Files.copy(source, candidate)
        val artifact = ArtifactRef("candidates/chords/fixture.mid", sha256(candidate))

        StageRunValidator.requireArtifact(root, artifact, "characterization candidate")
        assertFailsWith<IllegalArgumentException> { WorkflowArtifactReference("../outside.mid", artifact.sha256) }
        assertFailsWith<IllegalArgumentException> { WorkflowArtifactReference("/outside.mid", artifact.sha256) }

        val store = StageRunStore()
        val record = StageRunRecord(
            runId = "candidate-fixture",
            stage = StageId.GENERATED,
            subject = StageSubject.Occurrence("fixture-occurrence"),
            status = StageRunStatus.COMPLETED,
            createdAt = "2026-08-26T00:00:00Z",
            finishedAt = "2026-08-26T00:00:00Z",
            outputArtifacts = listOf(artifact),
        )
        val manifest = store.append(root, record)

        assertEquals(listOf(record), store.read(root, manifest))
        assertFailsWith<IllegalArgumentException> { store.append(root, record) }
        assertTrue(Files.readAllBytes(candidate).contentEquals(Files.readAllBytes(source)))

        Files.writeString(candidate, "tampered")
        assertFailsWith<IllegalArgumentException> { store.read(root, manifest) }
    }
}

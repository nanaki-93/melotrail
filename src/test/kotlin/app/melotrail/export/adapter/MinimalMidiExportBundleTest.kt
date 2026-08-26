package app.melotrail.export.adapter

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.SemanticMidiSequence
import app.melotrail.midi.domain.SemanticMidiTrack
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MinimalMidiExportBundleTest {
    @TempDir lateinit var root: Path

    @Test
    fun `publishes a complete semantically re-imported core role bundle with a portable manifest`() {
        val destination = root.resolve("exports/intro")
        val result = MinimalMidiExportBundle().export(snapshot(), song(), destination)

        assertEquals(destination, result.directory)
        assertEquals(setOf("complete-song.mid", "melody.mid", "chords.mid", "bass.mid", "drums.mid"), result.files.keys)
        assertTrue(result.files.all { (filename, digest) -> sha256(destination.resolve(filename)) == digest })
        val manifest = Files.readString(destination.resolve("manifest.json"))
        assertTrue(manifest.contains("\"snapshotId\": \"spike-001\""))
        assertTrue(manifest.contains("\"complete-song.mid\""))
        assertFalse(manifest.contains(root.toString()))
        assertFalse(Files.list(destination.parent).use { paths -> paths.anyMatch { it.name.contains(".staging-") } })

        val complete = JdkMidiReader().inspect(destination.resolve("complete-song.mid"))
        assertEquals(listOf("Conductor", "Melody", "Chords", "Bass", "Drums"), complete.trackSummaries.map { it.name })
        assertEquals(480, complete.sourceEndTick)
    }

    @Test
    fun `refuses an existing destination without changing it`() {
        val destination = root.resolve("existing")
        Files.createDirectories(destination)
        val sentinel = destination.resolve("keep.txt")
        Files.writeString(sentinel, "preserve")

        assertFailsWith<IllegalArgumentException> { MinimalMidiExportBundle().export(snapshot(), song(), destination) }

        assertEquals("preserve", Files.readString(sentinel))
        assertFalse(Files.exists(destination.resolve("complete-song.mid")))
    }

    @Test
    fun `cleans interrupted staging and never publishes a partial bundle`() {
        val destination = root.resolve("interrupted")
        val exporter = MinimalMidiExportBundle(beforePublish = { error("simulated interruption") })

        assertFailsWith<IllegalStateException> { exporter.export(snapshot(), song(), destination) }

        assertFalse(Files.exists(destination))
        assertFalse(Files.list(root).use { paths -> paths.anyMatch { it.name.contains(".staging-") } })
    }

    @Test
    fun `rejects a generated file whose digest changes after validation`() {
        val destination = root.resolve("tampered")
        val exporter = MinimalMidiExportBundle(beforePublish = { staging ->
            Files.write(staging.resolve("bass.mid"), byteArrayOf(0x00))
        })

        assertFailsWith<IllegalArgumentException> { exporter.export(snapshot(), song(), destination) }

        assertFalse(Files.exists(destination))
        assertFalse(Files.list(root).use { paths -> paths.anyMatch { it.name.contains(".staging-") } })
    }

    @Test
    fun `semantic comparison ignores source identity but reports musical event changes`() {
        val inspected = run {
            MinimalMidiExportBundle().export(snapshot(), song(), root.resolve("comparison"))
            JdkMidiReader().inspect(root.resolve("comparison/complete-song.mid")).sequence
        }
        val melody = inspected.tracks[1]
        val changed = melody.events.map { event ->
            if (event is MidiNoteEvent) event.copy(pitch = event.pitch + 1) else event
        }
        val different = SemanticMidiSequence(inspected.source, inspected.tracks.map { track ->
            if (track.index == 1) SemanticMidiTrack(track.index, changed) else track
        })

        assertTrue(MidiSemanticComparator.differences(inspected, inspected).isEmpty())
        assertEquals(listOf("track 1 events differ"), MidiSemanticComparator.differences(inspected, different))
    }

    private fun snapshot() = MinimalExportSnapshot("spike-001", "a".repeat(64), "b".repeat(64))

    private fun song() = MidiExportSong(
        ppq = MidiPpq(480),
        sequenceName = "MC-008 Spike",
        tempoMicrosecondsPerQuarter = 500_000,
        meterNumerator = 4,
        meterDenominatorExponent = 2,
        markers = listOf(MidiExportMarker(1, "Intro", 0), MidiExportMarker(2, "Verse", 240)),
        roles = listOf(
            role(MidiExportRole.MELODY, note(0, 240, 1, 72)),
            role(MidiExportRole.CHORDS, note(0, 480, 2, 60)),
            role(MidiExportRole.BASS, note(0, 480, 3, 40)),
            role(MidiExportRole.DRUMS, note(0, 120, 4, 36)),
        ),
        songEndTick = 480,
    )

    private fun role(role: MidiExportRole, vararg events: MidiNoteEvent) = MidiExportRoleTrack(role, events.toList())

    private fun note(start: Long, end: Long, id: Long, pitch: Int) = MidiNoteEvent(
        MidiEventOrderingKey(start, MidiSemanticEventKind.NOTE, generatedEventKey = id), end, 0, pitch, 96, 32,
    )

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

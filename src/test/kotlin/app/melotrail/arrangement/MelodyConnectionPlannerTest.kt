package app.melotrail.arrangement

import app.melotrail.harmony.ChordQuality
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MelodyConnectionPlannerTest {
    @TempDir lateinit var root: Path

    @Test
    fun `planner persists a bounded hold-last candidate and preserves source plus anchors`() {
        val song = sourceSong()
        val source = root.resolve(song.assembledMidi.file)
        val original = Files.readAllBytes(source)

        val artifact = MelodyConnectionPlanner().connect(root, song)
        val output = root.resolve(artifact.connection.outputMidi.file)
        val persisted = Json.decodeFromString(MelodyConnection.serializer(), Files.readString(artifact.metadataPath))

        assertEquals(artifact.connection, persisted)
        assertEquals(original.toList(), Files.readAllBytes(source).toList())
        assertEquals(MelodyConnectionStrategy.HOLD_LAST_NOTE, artifact.connection.boundaries.single().decision.strategy)
        assertEquals(MidiMutationStage.MELODY_CONNECTION, artifact.connection.boundaries.single().report.stage)
        assertEquals(1, artifact.connection.boundaries.single().report.mutations.size)
        assertEquals(1_920L, noteEnds(output).filter { it.first < 1_920L }.maxOf { it.second })
        assertTrue(output.startsWith(root.resolve("source-song")))
    }

    @Test
    fun `allow-listed strategies remain boundary-local and never repitch or delete anchors`() {
        val song = sourceSong(notesPerSection = 12)
        val source = root.resolve(song.assembledMidi.file)
        val original = Files.readAllBytes(source)
        val strategies = listOf(
            MelodyConnectionStrategy.NONE,
            MelodyConnectionStrategy.HOLD_LAST_NOTE, MelodyConnectionStrategy.EXTEND_CHORD,
            MelodyConnectionStrategy.INSERT_REST, MelodyConnectionStrategy.PICKUP,
            MelodyConnectionStrategy.STEPWISE_PICKUP, MelodyConnectionStrategy.VELOCITY_RAMP,
            MelodyConnectionStrategy.SIMPLIFY_ENDING
        )

        strategies.forEach { strategy ->
            val artifact = MelodyConnectionPlanner(MelodyConnectionStrategySelector { strategy }).connect(root, song)
            val boundary = artifact.connection.boundaries.single()
            assertEquals(strategy, boundary.decision.strategy, "strategy=$strategy")
            assertTrue(boundary.report.mutations.all { mutation -> mutation.operation != MidiMutationOperation.REMOVE || mutation.noteId !in anchors(song, "out") })
            assertTrue(boundary.report.mutations.all { mutation -> mutation.operation != MidiMutationOperation.PITCH })
            assertTrue(boundary.report.mutations.all { mutation ->
                mutation.after?.let { value -> value.startTick >= 0 && value.endTick <= 1_920L } ?: true
            })
        }
        assertEquals(original.toList(), Files.readAllBytes(source).toList())
    }

    private fun sourceSong(notesPerSection: Int = 4): SourceSong {
        val out = root.resolve("source/out.mid"); val incoming = root.resolve("source/in.mid")
        writeMidi(out, 60, notesPerSection); writeMidi(incoming, 62, notesPerSection)
        val sections = listOf(section("out", "out-one", out, 0), section("in", "in-one", incoming, 1_920))
        return SourceSongAssembler().assemble(SourceSongAssemblyRequest(
            root = root, contextSha256 = "a".repeat(64), canonicalPpq = 480, tempoBpm = 90.0,
            meterNumerator = 4, meterDenominator = 4, sections = sections
        )).song
    }

    private fun section(part: String, instance: String, midi: Path, start: Long): SourceSongSection = SourceSongSection(
        instance = SectionInstance(if (start == 0L) 0 else 1, part, instance), sourcePartId = part,
        sectionRole = if (part == "out") SectionTypeId.VERSE else SectionTypeId.CHORUS,
        occurrenceNumber = 1, startBar = start / 1_920L, endBar = start / 1_920L + 1, startTick = start, endTick = start + 1_920,
        sourceMidi = SourceSongMidiInput(part, root.relativize(midi).toString(), sha256(midi), 480, "CLEANED"),
        canonicalHarmony = listOf(SourceSongHarmonySpan(instance, start / 1_920L, start, start + 1_920, 0, "C", ChordQuality.MAJOR))
    )

    private fun writeMidi(path: Path, pitch: Int, notes: Int) {
        Files.createDirectories(path.parent)
        val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
        repeat(notes) { index ->
            val start = index * (1_920L / notes)
            val end = start + (1_920L / notes) - 60
            val notePitch = if (index == notes - 1) pitch + 2 else pitch + index % 3 * 2
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, notePitch, 80), start))
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, notePitch, 0), end))
        }
        track.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), 1_920))
        MidiSystem.write(sequence, 1, path.toFile())
    }

    private fun anchors(song: SourceSong, part: String): Set<MelodyNoteId> = MelodyIdentityBuilder.build(root.resolve(song.sections.single { it.sourcePartId == part }.sourceMidi.projectRelativePath), 480).anchorIds.toSet()

    private fun noteEnds(path: Path): List<Pair<Long, Long>> {
        val active = mutableMapOf<Int, ArrayDeque<Long>>(); val result = mutableListOf<Pair<Long, Long>>()
        MidiSystem.getSequence(path.toFile()).tracks.forEach { track ->
            (0 until track.size()).forEach { index ->
                val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
                if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) active.getOrPut(message.data1) { ArrayDeque() }.addLast(event.tick)
                if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                    active[message.data1]?.removeFirstOrNull()?.let { result += it to event.tick }
                }
            }
        }
        return result
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

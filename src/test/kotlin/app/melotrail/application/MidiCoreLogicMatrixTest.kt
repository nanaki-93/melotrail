package app.melotrail.application

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.*
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreLogicMatrixTest {
    @TempDir lateinit var temporary: Path
    private val root: Path get() = temporary.toRealPath()
    private val testBuild = buildJsonObject {
        put("kind", "TEST_ONLY")
        put("inputTreeSha256", "1".repeat(64))
    }

    @Test
    fun `prepares current complete matrix through the command and reproduces packages`() {
        // Exercise the documented entry point. Retain an immutable packet after successful checks.
        val repository = Path.of("").toAbsolutePath().normalize()
        val evidenceParent = repository.resolve("build/test-results/q02-logic-matrix")
        Files.createDirectories(evidenceParent)
        val run = Files.createTempDirectory(evidenceParent, "run-").toRealPath()
        val first = run.resolve("packet")
        MidiCoreLogicMatrix.main(arrayOf(repository.toString(), first.toString()))
        val matrix = read(first.resolve("matrix.json"))
        val second = MidiCoreLogicMatrix.prepare(root.resolve("repeat"), matrix.getValue("build").jsonObject)
        assertContentEquals(Files.readAllBytes(first.resolve("matrix.json")), Files.readAllBytes(second.resolve("matrix.json")))
        assertContentEquals(Files.readAllBytes(first.resolve("review.md")), Files.readAllBytes(second.resolve("review.md")))
        val cases = matrix.getValue("cases").jsonArray.map { it.jsonObject }.associateBy { it.getValue("id").jsonPrimitive.content }
        assertEquals(MidiCoreLogicMatrixFixtures.cases.map { it.id }.toSet(), cases.keys)
        assertEquals(21, cases.size)
        assertEquals("PENDING_USER", matrix.getValue("logicImportPlaybackReopen").jsonPrimitive.content)
        assertEquals(JsonNull, matrix.getValue("logicVersion"))
        cases.forEach { (id, record) ->
            assertContentEquals(Files.readAllBytes(first.resolve("$id/source.mid")), Files.readAllBytes(second.resolve("$id/source.mid")))
            if (record.getValue("result").jsonPrimitive.content == "EXPECTED_IMPORT_REJECTION") {
                assertFalse(Files.exists(first.resolve("$id/package")))
                assertEquals("SINGLE_MELODY_TRACK_REQUIRED", record.getValue("reason").jsonPrimitive.content)
            } else {
                assertEquals(LogicMatrixBuild.treeHashes(first.resolve("$id/package")), LogicMatrixBuild.treeHashes(second.resolve("$id/package")), id)
            }
        }
        val padded = cases.getValue("padded-arrangement-end")
        assertEquals(listOf(1800L, 1700L, 1920L), listOf("sourceEndTick", "lastNoteEndTick", "arrangementEndTick").map { padded.getValue(it).jsonPrimitive.long })
        val odd = cases.getValue("odd-ppq")
        assertEquals(481, odd.getValue("authority").jsonObject.getValue("ppq").jsonPrimitive.int)
        assertEquals(1924, odd.getValue("arrangementEndTick").jsonPrimitive.int)
        assertEquals(setOf("complete-song.mid", "melody.mid", "manifest.json"), LogicMatrixBuild.treeHashes(first.resolve("odd-ppq/package")).keys)
        val unequal = cases.getValue("unequal-harmony").getValue("authority").jsonObject.getValue("chordEvents").jsonArray
        assertEquals(listOf(1440L, 480L, 960L, 960L), unequal.map { it.jsonObject.getValue("endTick").jsonPrimitive.long - it.jsonObject.getValue("startTick").jsonPrimitive.long })
        assertFalse(Files.exists(first.resolve("all-song-bass-rest/package/bass.mid")))
        val bass = JdkMidiReader().inspect(first.resolve("intro-bass-rest/package/bass.mid")).sequence.tracks[1].events.filterIsInstance<MidiNoteEvent>()
        assertEquals(1920L, bass.minOf { it.orderingKey.tick })
        assertTrue(bass.all { it.endTick <= 3840 })
        val ending = JdkMidiReader().inspect(first.resolve("complete-arrangement-boundary/package/drums.mid"))
            .sequence.tracks[1].events.filterIsInstance<MidiNoteEvent>()
        assertEquals(listOf(1440L, 1560L, 1680L, 1800L), ending.filter { it.pitch == 38 && it.orderingKey.tick >= 1440L }.map { it.orderingKey.tick })
        assertTrue(ending.all { it.endTick <= 1920L })
        val expression = JdkMidiReader().inspect(first.resolve("expression-channel-remap/package/melody.mid")).sequence.tracks[1].events
        assertTrue(expression.filterIsInstance<MidiChannelPressureEvent>().any { it.channel == 0 && it.pressure == 45 })
        assertEquals(33, expression.filterIsInstance<MidiNoteEvent>().single().releaseVelocity)
        assertTrue(expression.filterIsInstance<MidiControlChangeEvent>().none { it.controller in setOf(0, 32) })
        assertTrue(Files.readString(first.resolve("review.md")).contains("Save, close, reopen in Logic and replay"))
        // Every retained project/candidate/source/output byte is covered, even though project timestamps are observational.
        val sums = Files.readAllLines(first.resolve("SHA256SUMS")).associate { line -> line.substring(66) to line.substring(0, 64) }
        assertEquals(sums, LogicMatrixBuild.treeHashes(first, exclude = setOf("SHA256SUMS")))
        println("Q02a prepared packet (Logic result pending): $first")
    }

    @Test
    fun `brief sub-bar source uses sustained candidates without changing its exact harmony`() {
        val case = MidiCoreLogicMatrixFixtures.cases.single { it.id == "sub-bar-harmony" }
        val packet = MidiCoreLogicMatrix.prepare(root.resolve("brief-harmony"), testBuild, listOf(case))
        val caseRoot = packet.resolve(case.id)
        val record = read(caseRoot.resolve("case.json"))
        assertEquals("explicit-role-patterns", record.getValue("style").jsonPrimitive.content)
        assertEquals(JsonNull, record.getValue("explicitSectionPolicy").jsonObject.getValue("drumFillPatternId"))
        assertTrue(record.getValue("plannedRests").jsonArray.isEmpty())
        val harmony = record.getValue("authority").jsonObject.getValue("chordEvents").jsonArray.map { it.jsonObject }
        assertEquals(listOf("C", "G7"), harmony.map { it.getValue("symbol").jsonPrimitive.content })
        assertEquals(listOf(0L to 240L, 240L to 1920L), harmony.map {
            it.getValue("startTick").jsonPrimitive.long to it.getValue("endTick").jsonPrimitive.long
        })
        val candidates = record.getValue("acceptedCandidates").jsonArray.map { it.jsonObject }
        assertEquals("chords.rhythm.sustained", candidates.single { it.getValue("role").jsonPrimitive.content == "chords" }
            .getValue("patternId").jsonPrimitive.content)
        val notes = JdkMidiReader().inspect(caseRoot.resolve("package/chords.mid")).sequence.tracks[1].events.filterIsInstance<MidiNoteEvent>()
        assertTrue(notes.any { it.orderingKey.tick == 0L })
        assertTrue(notes.any { it.orderingKey.tick == 240L })
        assertTrue(notes.all { if (it.orderingKey.tick < 240L) it.endTick <= 240L else it.endTick <= 1920L })
        assertContentEquals(case.sourceBytes, Files.readAllBytes(caseRoot.resolve("source.mid")))
        assertContentEquals(case.sourceBytes, Files.readAllBytes(caseRoot.resolve("project/source/original.mid")))
    }

    @Test
    fun `export omits bank hints while preserving the protected source and expression`() {
        val case = MidiCoreLogicMatrixFixtures.cases.single { it.id == "expression-channel-remap" }
        val packet = MidiCoreLogicMatrix.prepare(root.resolve("expression"), testBuild, listOf(case))
        val caseRoot = packet.resolve(case.id)
        val opened = assertIs<MidiCoreProjectLifecycleResult.Opened>(MidiCoreProjectLifecycle().open(caseRoot.resolve("project"))).session
        val source = JdkMidiReader().inspect(caseRoot.resolve("source.mid"))
        val protected = MidiProtectedMelodySelector().select(source.sequence,
            MidiMelodySelection(opened.project.selectedMelody!!.trackIndex, opened.project.selectedMelody!!.channel))
        assertEquals(setOf(0, 32), protected.events.filterIsInstance<MidiControlChangeEvent>()
            .filter { it.controller in setOf(0, 32) }.map { it.controller }.toSet())
        for (filename in listOf("complete-song.mid", "melody.mid")) {
            val events = JdkMidiReader().inspect(caseRoot.resolve("package/$filename")).sequence.tracks[1].events
            val controllers = events.filterIsInstance<MidiControlChangeEvent>()
            assertEquals(setOf(64, 11), controllers.map { it.controller }.toSet())
            assertTrue(controllers.all { it.channel == 0 })
            assertEquals(listOf(1024, 0), events.filterIsInstance<MidiPitchBendEvent>().map { it.value })
            assertEquals(33, events.filterIsInstance<MidiNoteEvent>().single().releaseVelocity)
        }
        assertContentEquals(case.sourceBytes, Files.readAllBytes(caseRoot.resolve("source.mid")))
        assertContentEquals(case.sourceBytes, Files.readAllBytes(opened.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
    }

    @Test
    fun `semantic verification rejects shifted notes channels controllers and end boundaries`() {
        val case = MidiCoreLogicMatrixFixtures.cases.single { it.id == "expression-channel-remap" }
        val packet = MidiCoreLogicMatrix.prepare(root.resolve("original"), testBuild, listOf(case))
        val caseRoot = packet.resolve(case.id)
        val opened = assertIs<MidiCoreProjectLifecycleResult.Opened>(MidiCoreProjectLifecycle().open(caseRoot.resolve("project"))).session
        val song = assertIs<MidiCoreAcceptedSongAssemblyResult.Assembled>(MidiCoreAcceptedSongAssembly().assemble(AssembleMidiCoreSong(opened))).review.song
        val source = caseRoot.resolve("package/melody.mid")
        val originals = LogicMatrixBuild.treeHashes(packet)
        listOf("note", "channel", "controller", "end").forEach { corruption ->
            val path = root.resolve("$corruption.mid")
            val midi = MidiSystem.getSequence(source.toFile())
            val track = midi.tracks[1]
            when (corruption) {
                "end" -> track.get(track.size() - 1).tick = song.songEndTick + 1
                else -> {
                    val event = (0 until track.size()).map { track.get(it) }.first { event ->
                        val message = event.message as? ShortMessage
                        message?.command == if (corruption == "controller") ShortMessage.CONTROL_CHANGE else ShortMessage.NOTE_ON
                    }
                    val message = event.message as ShortMessage
                    when (corruption) {
                        "note" -> event.tick += 1
                        "channel" -> message.setMessage(message.command, 2, message.data1, message.data2)
                        "controller" -> message.setMessage(message.command, message.channel, message.data1, (message.data2 + 1) % 128)
                    }
                }
            }
            MidiSystem.write(midi, 1, path.toFile())
            assertFailsWith<IllegalStateException>(corruption) { LogicMatrixSemantics.verifyFile(song, listOf(MidiExportRole.MELODY), path) }
        }
        assertEquals(originals, LogicMatrixBuild.treeHashes(packet))
    }

    @Test
    fun `refuses existing or linked output and leaves failed staging unpublished`() {
        val case = MidiCoreLogicMatrixFixtures.cases.single { it.id == "one-bar" }
        val existing = Files.createDirectory(root.resolve("existing"))
        Files.writeString(existing.resolve("keep"), "unchanged")
        assertFailsWith<IllegalArgumentException> { MidiCoreLogicMatrix.prepare(existing, testBuild, listOf(case)) }
        assertEquals("unchanged", Files.readString(existing.resolve("keep")))
        val link = Files.createSymbolicLink(root.resolve("link"), existing)
        assertFailsWith<IllegalArgumentException> { MidiCoreLogicMatrix.prepare(link.resolve("packet"), testBuild, listOf(case)) }
        assertFalse(Files.exists(existing.resolve("packet")))
        val failed = root.resolve("failed")
        assertFailsWith<IllegalStateException> {
            MidiCoreLogicMatrix.prepare(failed, testBuild, listOf(case), beforePublish = { error("Build inputs changed") })
        }
        assertFalse(Files.exists(failed))
        assertEquals("unchanged", Files.readString(existing.resolve("keep")))
        val raced = root.resolve("raced")
        assertFailsWith<IllegalArgumentException> {
            MidiCoreLogicMatrix.prepare(raced, testBuild, listOf(case), beforePublish = {
                Files.createDirectory(raced)
                Files.writeString(raced.resolve("previous"), "preserve publication")
            })
        }
        assertEquals("preserve publication", Files.readString(raced.resolve("previous")))
    }

    @Test
    fun `failed source import leaves a partial run unpublished and prior output untouched`() {
        val valid = MidiCoreLogicMatrixFixtures.cases.single { it.id == "one-bar" }
        val previous = MidiCoreLogicMatrix.prepare(root.resolve("previous"), testBuild, listOf(valid))
        val previousHashes = LogicMatrixBuild.treeHashes(previous)
        val failed = root.resolve("new-packet")
        val malformed = valid.copy(id = "malformed", sourceBytes = byteArrayOf(0x4d, 0x54, 0x68, 0x64))
        assertFailsWith<IllegalStateException> { MidiCoreLogicMatrix.prepare(failed, testBuild, listOf(valid, malformed)) }
        assertFalse(Files.exists(failed))
        assertEquals(previousHashes, LogicMatrixBuild.treeHashes(previous))
    }

    @Test
    fun `manifest and package inventory corruption cannot be represented as verified evidence`() {
        val case = MidiCoreLogicMatrixFixtures.cases.single { it.id == "one-bar" }
        val packet = MidiCoreLogicMatrix.prepare(root.resolve("source"), testBuild, listOf(case))
        val caseRoot = packet.resolve(case.id)
        val store = MidiCoreArtifactStore()
        val opened = assertIs<MidiCoreProjectLifecycleResult.Opened>(MidiCoreProjectLifecycle(store).open(caseRoot.resolve("project"))).session
        val song = assertIs<MidiCoreAcceptedSongAssemblyResult.Assembled>(MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(opened))).review.song
        val exported = assertIs<MidiCoreMidiPackageExportResult.Exported>(MidiCoreMidiPackageExporter(artifacts = store).export(
            ExportMidiCorePackage(opened, snapshotId = "tamper-test"))).packageResult
        val copy = Files.createDirectory(root.resolve("tampered"))
        Files.list(exported.directory).use { paths -> paths.forEach { Files.copy(it, copy.resolve(it.fileName)) } }
        val manifest = copy.resolve("manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace("\"source\"", "\"wrongSource\""))
        assertFailsWith<IllegalStateException> { LogicMatrixSemantics.verify(song, exported, copy, caseRoot.resolve("source.mid")) }
        Files.copy(exported.directory.resolve("manifest.json"), manifest, StandardCopyOption.REPLACE_EXISTING)
        Files.copy(copy.resolve("melody.mid"), copy.resolve("unexpected.mid"))
        assertFailsWith<IllegalStateException> { LogicMatrixSemantics.verify(song, exported, copy, caseRoot.resolve("source.mid")) }
    }

    private fun read(path: Path) = Json.parseToJsonElement(Files.readString(path)).jsonObject
}

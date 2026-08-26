package app.melotrail.export.adapter

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.adapter.JdkMidiWriter
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.midi.domain.MidiExportRoleTrack
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.SemanticMidiEvent
import app.melotrail.midi.domain.SemanticMidiSequence
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class MinimalExportSnapshot(val id: String, val sourceSha256: String, val authorityHash: String) {
    init {
        require(ID.matches(id)) { "Export snapshot ID must be a safe stable identifier" }
        require(SHA.matches(sourceSha256) && SHA.matches(authorityHash)) { "Export snapshot hashes must be SHA-256 values" }
    }
    private companion object { val ID = Regex("[A-Za-z0-9_-]+"); val SHA = Regex("[0-9a-f]{64}") }
}

data class MinimalMidiExportResult(val snapshot: MinimalExportSnapshot, val directory: Path, val files: Map<String, String>)

/** Semantic comparison intentionally ignores source identity and compares portable musical facts only. */
object MidiSemanticComparator {
    fun differences(expected: SemanticMidiSequence, actual: SemanticMidiSequence): List<String> {
        if (expected.source.format != actual.source.format) return listOf("format differs")
        if (expected.source.ppq != actual.source.ppq) return listOf("PPQ differs")
        if (expected.tracks.size != actual.tracks.size) return listOf("track count differs")
        return expected.tracks.zip(actual.tracks).flatMap { (left, right) ->
            val expectedEvents = left.events.map(::eventFact)
            val actualEvents = right.events.map(::eventFact)
            if (expectedEvents == actualEvents) emptyList() else listOf("track ${left.index} events differ")
        }
    }

    private fun eventFact(event: SemanticMidiEvent): String = when (event) {
        is app.melotrail.midi.domain.MidiNoteEvent -> "note:${event.orderingKey.tick}:${event.endTick}:${event.channel}:${event.pitch}:${event.velocity}:${event.releaseVelocity}"
        is app.melotrail.midi.domain.MidiControlChangeEvent -> "cc:${event.orderingKey.tick}:${event.channel}:${event.controller}:${event.value}"
        is app.melotrail.midi.domain.MidiPitchBendEvent -> "bend:${event.orderingKey.tick}:${event.channel}:${event.value}"
        is app.melotrail.midi.domain.MidiChannelPressureEvent -> "pressure:${event.orderingKey.tick}:${event.channel}:${event.pressure}"
        is app.melotrail.midi.domain.MidiTempoEvent -> "tempo:${event.orderingKey.tick}:${event.microsecondsPerQuarter}"
        is app.melotrail.midi.domain.MidiTimeSignatureEvent -> "meter:${event.orderingKey.tick}:${event.numerator}:${event.denominatorExponent}:${event.clocksPerMetronome}:${event.thirtySecondNotesPerQuarter}"
        is app.melotrail.midi.domain.MidiTrackNameEvent -> "name:${event.orderingKey.tick}:${event.name}"
        is app.melotrail.midi.domain.MidiMarkerEvent -> "marker:${event.orderingKey.tick}:${event.marker}"
        is app.melotrail.midi.domain.MidiTextEvent -> "text:${event.orderingKey.tick}:${event.textKind}:${event.text}"
        is app.melotrail.midi.domain.MidiUnsupportedEvent -> "unsupported:${event.orderingKey.tick}:${event.messageType}:${event.detail}"
    }

    internal fun portableEventFact(event: SemanticMidiEvent): String = eventFact(event)
}

/** Small MC-008 export spike. Project snapshots and full manifests are expanded in later tasks. */
class MinimalMidiExportBundle(
    private val writer: JdkMidiWriter = JdkMidiWriter(),
    private val reader: JdkMidiReader = JdkMidiReader(),
    private val beforePublish: (Path) -> Unit = {},
) {
    fun export(snapshot: MinimalExportSnapshot, song: MidiExportSong, destination: Path): MinimalMidiExportResult {
        require(!Files.exists(destination)) { "Export destination already exists: $destination" }
        val parent = requireNotNull(destination.parent) { "Export destination must have a parent directory" }
        Files.createDirectories(parent)
        val staging = Files.createTempDirectory(parent, ".${destination.fileName}.staging-")
        try {
            val output = linkedMapOf<String, ExportedFile>()
            output[COMPLETE] = ExportedFile(staging.resolve(COMPLETE), song.roles)
            writer.writeComplete(song, output.getValue(COMPLETE).path)
            MidiExportRole.entries.forEach { role ->
                if (song.roles.any { it.role == role }) {
                    val filename = "${role.name.lowercase()}.mid"
                    output[filename] = ExportedFile(staging.resolve(filename), listOf(song.role(role)))
                    writer.writeRole(song, role, output.getValue(filename).path)
                }
            }
            output.values.forEach { exported -> validateReimport(song, exported) }
            val hashes = output.mapValues { (_, exported) -> sha256(exported.path) }
            Files.writeString(staging.resolve(MANIFEST), manifest(snapshot, hashes))
            beforePublish(staging)
            require(output.all { (filename, exported) -> hashes.getValue(filename) == sha256(exported.path) }) {
                "Generated MIDI digest changed before export publication"
            }
            require(!Files.exists(destination)) { "Export destination already exists: $destination" }
            publish(staging, destination)
            return MinimalMidiExportResult(snapshot, destination, hashes)
        } catch (failure: Throwable) {
            deleteTree(staging)
            throw failure
        }
    }

    private fun validateReimport(song: MidiExportSong, exported: ExportedFile) {
        val inspected = reader.inspect(exported.path)
        require(inspected.sequence.source.format == 1 && inspected.sequence.source.ppq == song.ppq) {
            "Generated MIDI failed semantic re-import: ${exported.path}"
        }
        require(inspected.sourceEndTick == song.songEndTick) {
            "Generated MIDI end boundary differs after re-import: ${exported.path}"
        }
        val expectedNames = listOf("Conductor") + exported.roles.map { it.role.trackName }
        require(inspected.trackSummaries.map { it.name } == expectedNames) {
            "Generated MIDI track names differ after re-import: ${exported.path}"
        }
        val actualConductor = inspected.sequence.tracks.first().events.map(MidiSemanticComparator::portableEventFact)
        require(actualConductor == conductorFacts(song)) {
            "Generated MIDI conductor differs after re-import: ${exported.path}"
        }
        exported.roles.forEachIndexed { index, roleTrack ->
            val actual = inspected.sequence.tracks[index + 1].events.map(MidiSemanticComparator::portableEventFact)
            val expected = listOf("name:0:${roleTrack.role.trackName}") + roleTrack.events.map { event ->
                MidiSemanticComparator.portableEventFact(remapped(event, roleTrack.role.channel))
            }
            require(actual == expected) {
                "Generated ${roleTrack.role.trackName} track differs after re-import: ${exported.path}"
            }
        }
    }

    private fun conductorFacts(song: MidiExportSong): List<String> = buildList {
        add(ConductorFact(0, 10, "tempo:0:${song.tempoMicrosecondsPerQuarter}"))
        add(ConductorFact(0, 20, "meter:0:${song.meterNumerator}:${song.meterDenominatorExponent}:24:8"))
        add(ConductorFact(0, 30, "name:0:Conductor"))
        song.markers.forEach { marker -> add(ConductorFact(marker.tick, 40, "marker:${marker.tick}:${marker.renderedLabel()}")) }
        add(ConductorFact(0, 50, "text:0:SEQUENCE_NAME:${song.sequenceName}"))
    }.sortedWith(compareBy(ConductorFact::tick, ConductorFact::priority)).map(ConductorFact::fact)

    private fun remapped(event: SemanticMidiEvent, channel: Int): SemanticMidiEvent = when (event) {
        is app.melotrail.midi.domain.MidiNoteEvent -> event.copy(channel = channel)
        is app.melotrail.midi.domain.MidiControlChangeEvent -> event.copy(channel = channel)
        is app.melotrail.midi.domain.MidiPitchBendEvent -> event.copy(channel = channel)
        is app.melotrail.midi.domain.MidiChannelPressureEvent -> event.copy(channel = channel)
        else -> error("${event.kind} events are not allowed in exported role tracks")
    }

    private fun manifest(snapshot: MinimalExportSnapshot, hashes: Map<String, String>): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"snapshotId\": \"${snapshot.id}\",")
        appendLine("  \"sourceSha256\": \"${snapshot.sourceSha256}\",")
        appendLine("  \"authorityHash\": \"${snapshot.authorityHash}\",")
        appendLine("  \"files\": {")
        hashes.entries.forEachIndexed { index, (file, hash) -> append("    \"$file\": \"$hash\"").appendLine(if (index == hashes.size - 1) "" else ",") }
        appendLine("  }")
        append('}')
    }

    private fun publish(staging: Path, destination: Path) =
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE)

    private fun sha256(path: Path): String = Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        generateSequence { input.read(buffer).takeIf { it >= 0 } }.forEach { count -> digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deleteTree(root: Path) {
        if (Files.notExists(root)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private data class ExportedFile(val path: Path, val roles: List<MidiExportRoleTrack>)
    private data class ConductorFact(val tick: Long, val priority: Int, val fact: String)

    private companion object { const val COMPLETE = "complete-song.mid"; const val MANIFEST = "manifest.json" }
}

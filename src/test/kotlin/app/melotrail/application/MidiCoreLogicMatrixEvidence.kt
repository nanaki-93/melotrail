package app.melotrail.application

import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiSystem
import kotlinx.serialization.json.*

internal fun logicSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Content identity includes uncommitted/untracked source; HEAD alone cannot identify a candidate build. */
internal object LogicMatrixBuild {
    fun capture(repository: Path): JsonObject {
        fun git(vararg args: String): ByteArray {
            val process = ProcessBuilder(listOf("git", "-C", repository.toString()) + args).start()
            val output = process.inputStream.readAllBytes()
            check(process.waitFor() == 0) { "Cannot identify build inputs with git ${args.firstOrNull()}" }
            return output
        }
        val commit = git("rev-parse", "HEAD").decodeToString().trim()
        check(commit.matches(Regex("[0-9a-f]{40}")))
        val diff = git("diff", "--no-ext-diff", "--no-textconv", "--binary", commit, "--")
        val status = git("status", "--porcelain=v1", "-z")
        val names = git("ls-files", "-z", "--cached", "--others", "--exclude-standard").decodeToString()
            .split('\u0000').filter { it.isNotEmpty() }.distinct().sorted()
        val inputs = names.associateWith { relative ->
            val path = repository.resolve(relative)
            when {
                Files.isSymbolicLink(path) -> logicSha256(("symlink:" + Files.readSymbolicLink(path)).encodeToByteArray())
                Files.isRegularFile(path, NOFOLLOW_LINKS) -> logicSha256(Files.readAllBytes(path))
                Files.notExists(path, NOFOLLOW_LINKS) -> "DELETED"
                else -> error("Unsupported build input: $relative")
            }
        }
        val compiled = listOf(MidiCoreMidiPackageExporter::class.java, MidiCoreLogicMatrix::class.java).mapIndexed { index, type ->
            val path = Path.of(type.protectionDomain.codeSource.location.toURI())
            "classes-$index" to if (Files.isDirectory(path)) hashList(treeHashes(path)) else logicSha256(Files.readAllBytes(path))
        }.toMap()
        val dependencies = System.getProperty("java.class.path").split(java.io.File.pathSeparator)
            .map { Path.of(it) }.filter { Files.isRegularFile(it) }
            .map { it.fileName.toString() to logicSha256(Files.readAllBytes(it)) }.sortedBy { it.first }
        return buildJsonObject {
            put("gitCommit", commit)
            put("candidate", if (status.isEmpty()) "COMMITTED" else "UNCOMMITTED")
            put("gitDiffSha256", logicSha256(diff))
            put("gitStatusSha256", logicSha256(status))
            put("inputTreeSha256", hashList(inputs))
            put("compiledClassesSha256", hashList(compiled))
            put("runtimeJarsSha256", logicSha256(dependencies.joinToString("\n") { "${it.first}:${it.second}" }.encodeToByteArray()))
            put("javaRuntimeVersion", System.getProperty("java.runtime.version"))
            put("javaVendor", System.getProperty("java.vendor"))
            put("inputs", buildJsonObject { inputs.forEach { (name, hash) -> put(name, hash) } })
        }
    }

    fun treeHashes(root: Path, exclude: Set<String> = emptySet()): Map<String, String> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it, NOFOLLOW_LINKS) }.toList()
            .associate { path -> root.relativize(path).toString().replace('\\', '/') to logicSha256(Files.readAllBytes(path)) }
            .filterKeys { it !in exclude }.toSortedMap()
    }

    private fun hashList(files: Map<String, String>) = logicSha256(
        files.toSortedMap().entries.joinToString("\n") { "${it.key.length}:${it.key}:${it.value}" }.encodeToByteArray(),
    )
}

/** Independent comparisons against the actual accepted assembly and immutable imported melody. */
internal object LogicMatrixSemantics {
    fun verify(song: MidiExportSong, exported: MidiCoreExportedPackage, directory: Path, source: Path): Map<String, String> {
        val manifestBytes = Files.readAllBytes(directory.resolve("manifest.json"))
        check(logicSha256(manifestBytes) == exported.manifestSha256) { "Export manifest changed" }
        val manifest = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
        val manifestFiles = manifest.getValue("generatedFiles").jsonArray.associate { element ->
            element.jsonObject.getValue("filename").jsonPrimitive.content to element.jsonObject.getValue("sha256").jsonPrimitive.content
        }
        check(manifestFiles == exported.files.associate { it.filename to it.sha256 }) { "Manifest inventory differs" }
        val actualFiles = Files.list(directory).use { it.map { path -> path.fileName.toString() }.toList().toSet() }
        check(actualFiles == manifestFiles.keys + "manifest.json") { "Unlisted or missing package files" }
        val presentRoles = listOf(MidiExportRole.MELODY) + exported.snapshot.enabledRoles.map { role -> MidiExportRole.valueOf(role.name) }
        val semantic = exported.files.associate { file ->
            val path = directory.resolve(file.filename)
            check(logicSha256(Files.readAllBytes(path)) == file.sha256) { "MIDI digest differs: ${file.filename}" }
            val roles = if (file.filename == "complete-song.mid") presentRoles else listOf(
                MidiExportRole.valueOf(file.filename.removeSuffix(".mid").uppercase()))
            file.filename to verifyFile(song, roles, path)
        }
        val sourceInspection = JdkMidiReader().inspect(source)
        val sourceNotesAndExpression = sourceInspection.sequence.tracks.flatMap { it.events }.filter(::isPreserved)
            .sortedBy { it.orderingKey }.map { fact(it, 0) }
        val melody = song.role(MidiExportRole.MELODY).events.filter(::isPreserved).map { fact(it, 0) }
        check(sourceNotesAndExpression == melody) { "Protected source notes/expression differ from assembled Melody" }
        return semantic
    }

    fun verifyFile(song: MidiExportSong, roles: List<MidiExportRole>, path: Path): String {
        val inspection = JdkMidiReader().inspect(path)
        check(inspection.sequence.source.format == 1 && inspection.sequence.source.ppq == song.ppq) { "Format/PPQ differs" }
        check(inspection.sourceEndTick == song.songEndTick) { "Song end differs" }
        check(MidiSystem.getSequence(path.toFile()).tracks.all { it.ticks() == song.songEndTick }) { "Individual track EOT differs" }
        check(inspection.trackSummaries.map { it.name } == listOf("Conductor") + roles.map { it.trackName }) { "Role names/order differs" }
        check(inspection.findings.isEmpty()) { "Re-import has MIDI parser findings: ${inspection.findings}" }
        val conductor = inspection.sequence.tracks.first().events
        val tempos = conductor.filterIsInstance<MidiTempoEvent>()
        check(tempos.map { it.orderingKey.tick to it.microsecondsPerQuarter } == listOf(0L to song.tempoMicrosecondsPerQuarter))
        val meters = conductor.filterIsInstance<MidiTimeSignatureEvent>()
        check(meters.map { listOf(it.orderingKey.tick, it.numerator.toLong(), it.denominatorExponent.toLong(), it.clocksPerMetronome.toLong(), it.thirtySecondNotesPerQuarter.toLong()) } ==
            listOf(listOf(0L, song.meterNumerator.toLong(), song.meterDenominatorExponent.toLong(), 24L, 8L)))
        check(conductor.filterIsInstance<MidiMarkerEvent>().map { it.orderingKey.tick to it.marker } == song.markers.map { it.tick to it.renderedLabel() })
        check(conductor.all { it is MidiTempoEvent || it is MidiTimeSignatureEvent || it is MidiTrackNameEvent || it is MidiMarkerEvent || it is MidiTextEvent })
        roles.forEachIndexed { index, role ->
            val actual = inspection.sequence.tracks[index + 1].events.filterNot { it is MidiTrackNameEvent }
            check(actual.map { fact(it) } == song.role(role).events.filter(::isPreserved).map { fact(it, role.channel) }) { "${role.trackName} semantic events differ" }
        }
        // Track positions, every supported semantic field and per-file boundary participate.
        return logicSha256(buildString {
            appendLine("format=1;ppq=${song.ppq.value};end=${song.songEndTick}")
            inspection.sequence.tracks.forEach { track ->
                appendLine("track=${track.index}")
                track.events.forEach { appendLine(fact(it)) }
            }
        }.encodeToByteArray())
    }

    private fun isPreserved(event: SemanticMidiEvent): Boolean = when (event) {
        is MidiNoteEvent, is MidiPitchBendEvent, is MidiChannelPressureEvent -> true
        is MidiControlChangeEvent -> event.controller !in setOf(0, 32)
        else -> false
    }

    private fun fact(event: SemanticMidiEvent, channel: Int? = null): String = when (event) {
        is MidiNoteEvent -> "note:${event.orderingKey.tick}:${event.endTick}:${channel ?: event.channel}:${event.pitch}:${event.velocity}:${event.releaseVelocity ?: 0}"
        is MidiControlChangeEvent -> "cc:${event.orderingKey.tick}:${channel ?: event.channel}:${event.controller}:${event.value}"
        is MidiPitchBendEvent -> "bend:${event.orderingKey.tick}:${channel ?: event.channel}:${event.value}"
        is MidiChannelPressureEvent -> "pressure:${event.orderingKey.tick}:${channel ?: event.channel}:${event.pressure}"
        is MidiTempoEvent -> "tempo:${event.orderingKey.tick}:${event.microsecondsPerQuarter}"
        is MidiTimeSignatureEvent -> "meter:${event.orderingKey.tick}:${event.numerator}:${event.denominatorExponent}:${event.clocksPerMetronome}:${event.thirtySecondNotesPerQuarter}"
        is MidiTrackNameEvent -> "name:${event.orderingKey.tick}:${event.name}"
        is MidiMarkerEvent -> "marker:${event.orderingKey.tick}:${event.marker}"
        is MidiTextEvent -> "text:${event.orderingKey.tick}:${event.textKind}:${event.text}"
        else -> error("Unexpected export event ${event.kind}")
    }
}

package app.melotrail.arrangement

import app.melotrail.application.ApprovedSourceSongMelody
import app.melotrail.application.DefaultSourceSongCriticApplicationService
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

/** Immutable occurrence-local view of the one approved connected full melody. */
data class OccurrenceMidiArtifact(
    val occurrenceId: String,
    val partId: String,
    val path: Path,
    val projectRelativePath: String,
    val sha256: String,
    /** Exact hash of the global connected source approved by Source Song Critic. */
    val canonicalFullMelodySha256: String,
    val canonicalFullMelodyPath: String,
    /** Half-open global sidecar window that this artifact was clipped from. */
    val startTick: Long,
    val endTick: Long,
    val ppq: Int,
    val timing: MidiTimingSummary,
    val source: OccurrenceMidiSource
) {
    init {
        require(startTick >= 0 && endTick > startTick && sha256.matches(Regex("[0-9a-f]{64}")) && canonicalFullMelodySha256.matches(Regex("[0-9a-f]{64}"))) {
            "Occurrence MIDI identity is invalid"
        }
    }
}

enum class OccurrenceMidiSource { APPROVED_FULL_MELODY, COHESION }

/** Content-addressed occurrence-view paths for one approved connected full melody. */
object ApprovedFullMelodyOccurrencePaths {
    fun midi(contextSha256: String, connectedSha256: String, occurrenceId: String): String {
        require(contextSha256.matches(HASH) && connectedSha256.matches(HASH) && SAFE_ID.matches(occurrenceId)) {
            "Approved full-melody occurrence identity is invalid"
        }
        return "source-song/v2/$contextSha256/occurrence-views/$connectedSha256/$occurrenceId.mid"
    }

    private val HASH = Regex("[0-9a-f]{64}")
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,80}")
}

/**
 * Provides read-only, occurrence-local views clipped from the exact approved
 * connected full melody. No selected-part MIDI is reachable from this current
 * piano path; stale or absent approval is a recoverable prerequisite failure.
 */
class OccurrenceMidiArtifactResolver(
    private val approvedSource: (Path) -> ApprovedSourceSongMelody = { root ->
        DefaultSourceSongCriticApplicationService().requireApprovedMelody(root)
    }
) {
    fun resolve(root: Path, project: Project, occurrences: List<SectionInstance>): List<OccurrenceMidiArtifact> =
        resolve(root, project, occurrences, approvedSource(root))

    /** Resolve views from an already validated approval so one operation uses one exact source identity. */
    fun resolve(root: Path, project: Project, occurrences: List<SectionInstance>, approved: ApprovedSourceSongMelody): List<OccurrenceMidiArtifact> {
        val normalized = root.toAbsolutePath().normalize()
        require(project.version == Project.CURRENT_VERSION) { "Occurrence MIDI requires a MIDI-first v4 project." }
        require(approved.sourceSong.contextSha256 == approved.approval.sourceSongContextSha256 &&
            approved.connectedMidi.sha256 == approved.approval.connectedMidiSha256 &&
            approved.connection.outputMidi == approved.connectedMidi) {
            "Approved full melody lineage is incomplete or stale. Re-run Source Song Critic and approve the current melody."
        }
        val source = verified(normalized, approved.connectedMidi, "Approved connected full melody")
        val windows = approved.sourceSong.fullMelody.occurrences.associateBy(FullMelodyOccurrenceWindow::occurrenceId)
        val expectedOrder = approved.sourceSong.sections.map { it.instance.instanceId }
        require(occurrences.map(SectionInstance::instanceId) == expectedOrder.filter { it in occurrences.map(SectionInstance::instanceId) }) {
            "Occurrence MIDI must retain canonical source-song order."
        }
        return occurrences.map { occurrence ->
            val window = requireNotNull(windows[occurrence.instanceId]) {
                "Approved full melody has no sidecar window for occurrence '${occurrence.instanceId}'."
            }
            require(window.sourcePartId == occurrence.partId) { "Approved full-melody occurrence '${occurrence.instanceId}' has a different source part." }
            val clipped = clip(normalized, approved, source, window)
            val cohesive = project.workflow.cohesion?.takeIf { it.approved && WorkflowArtifact.COHESION !in project.workflow.stale }
                ?.occurrences?.singleOrNull { it.instanceId == occurrence.instanceId }
            val resolved = cohesive?.let { reference ->
                require(reference.approved && reference.sourceSha256 == approved.connectedMidi.sha256 &&
                    reference.cohesionInputSha256 == project.workflow.cohesion?.inputSha256) {
                    "Approved Cohesion occurrence '${occurrence.instanceId}' is not bound to the approved full melody. Regenerate Cohesion."
                }
                val path = verified(normalized, reference.result, "Approved Cohesion occurrence '${occurrence.instanceId}'")
                require(digest(path) == clipped.sha256) {
                    "Approved Cohesion modified piano occurrence '${occurrence.instanceId}' without publishing a new full melody candidate. Regenerate from the approved full melody."
                }
                Triple(path, reference.result.file, reference.result.sha256)
            }
            OccurrenceMidiArtifact(
                occurrenceId = occurrence.instanceId,
                partId = occurrence.partId,
                path = resolved?.first ?: clipped.path,
                projectRelativePath = resolved?.second ?: clipped.projectRelativePath,
                sha256 = resolved?.third ?: clipped.sha256,
                canonicalFullMelodySha256 = approved.connectedMidi.sha256,
                canonicalFullMelodyPath = approved.connectedMidi.file,
                startTick = window.startTick,
                endTick = window.endTick,
                ppq = approved.sourceSong.canonicalPpq,
                timing = clipped.timing,
                source = if (resolved == null) OccurrenceMidiSource.APPROVED_FULL_MELODY else OccurrenceMidiSource.COHESION
            )
        }
    }

    private fun clip(root: Path, approved: ApprovedSourceSongMelody, source: Path, window: FullMelodyOccurrenceWindow): ClippedOccurrence {
        val relative = ApprovedFullMelodyOccurrencePaths.midi(approved.sourceSong.contextSha256, approved.connectedMidi.sha256, window.occurrenceId)
        val target = root.resolve(relative).normalize()
        require(target.startsWith(root)) { "Approved full-melody occurrence view escapes project root" }
        val input = MidiSystem.getSequence(source.toFile())
        require(input.divisionType == Sequence.PPQ && input.resolution == approved.sourceSong.canonicalPpq) {
            "Approved connected full melody does not match its canonical PPQ"
        }
        val melodyTracks = input.tracks.filter { trackName(it) == approved.sourceSong.fullMelody.melodyTrackName }
        require(melodyTracks.size == 1) { "Approved connected full melody has no unique canonical melody track" }
        val output = Sequence(Sequence.PPQ, input.resolution)
        val conductor = output.createTrack()
        input.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
            .filter { event -> (event.message as? MetaMessage)?.type in setOf(TEMPO, TIME_SIGNATURE) }
            .filter { it.tick == 0L }
            .sortedBy { it.tick }
            .forEach { conductor.add(MidiEvent(it.message.copy(), 0)) }
        val melody = output.createTrack()
        melody.add(MidiEvent(trackName("full-melody:${window.occurrenceId}"), 0))
        copyWindow(melodyTracks.single(), melody, window)
        val duration = window.endTick - window.startTick
        conductor.add(MidiEvent(MetaMessage(END_OF_TRACK, byteArrayOf(), 0), duration))
        melody.add(MidiEvent(MetaMessage(END_OF_TRACK, byteArrayOf(), 0), duration))
        Files.createDirectories(requireNotNull(target.parent))
        val temporary = Files.createTempFile(target.parent, ".${window.occurrenceId}.", ".mid")
        try {
            require(MidiSystem.write(output, 1, temporary.toFile()) > 0) { "Could not write approved full-melody occurrence view" }
            val hash = digest(temporary)
            if (Files.exists(target)) {
                require(digest(target) == hash) { "Existing approved full-melody occurrence view differs; preserving it for inspection" }
            } else {
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) }
            }
            val sequence = MidiSystem.getSequence(target.toFile())
            require(sequence.tickLength == duration && sequence.tracks.size == 2) { "Approved full-melody occurrence view has invalid bounds" }
            return ClippedOccurrence(target, relative, digest(target), timing(sequence))
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Copy a closed note pair only when it is wholly inside its sidecar occurrence window. */
    private fun copyWindow(source: javax.sound.midi.Track, target: javax.sound.midi.Track, window: FullMelodyOccurrenceWindow) {
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<MidiEvent>>()
        (0 until source.size()).map(source::get).sortedWith(compareBy<MidiEvent> { it.tick }.thenBy { event ->
            val message = event.message as? ShortMessage
            if (message?.command == ShortMessage.NOTE_OFF || message?.command == ShortMessage.NOTE_ON && message.data2 == 0) 0 else 1
        }).forEach { event ->
            val message = event.message as? ShortMessage ?: return@forEach
            val key = message.channel to message.data1
            when {
                message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> {
                    active.getOrPut(key) { ArrayDeque() }.addLast(event)
                }
                message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                    val start = active[key]?.removeFirstOrNull() ?: error("Approved full melody has an unmatched note-off")
                    val touchesWindow = start.tick in window.startTick until window.endTick || event.tick in (window.startTick + 1) until window.endTick
                    if (!touchesWindow) return@forEach
                    require(start.tick >= window.startTick && event.tick > start.tick && event.tick <= window.endTick) {
                        "Approved full melody crosses occurrence boundary '${window.occurrenceId}' (${start.tick}..${event.tick} outside ${window.startTick}..${window.endTick})"
                    }
                    target.add(MidiEvent(start.message.copy(), start.tick - window.startTick))
                    target.add(MidiEvent(event.message.copy(), event.tick - window.startTick))
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "Approved full melody has an unmatched note-on" }
    }

    private fun verified(root: Path, reference: WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && digest(path) == reference.sha256) { "$label is missing or stale. Recover by rerunning Source Song Critic and approving the current full melody." }
        return path
    }

    private fun timing(sequence: Sequence): MidiTimingSummary = MidiTimingSummary(
        sequence.events().mapNotNull { event ->
            val message = event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != TEMPO || message.data.size != 3) null else {
                val micros = ((message.data[0].toInt() and 0xff) shl 16) or ((message.data[1].toInt() and 0xff) shl 8) or (message.data[2].toInt() and 0xff)
                require(micros > 0) { "Approved full melody has invalid tempo metadata" }
                MidiTempoChange(event.tick, 60_000_000.0 / micros)
            }
        }.toList().sortedBy(MidiTempoChange::tick).distinctBy(MidiTempoChange::tick).ifEmpty { listOf(MidiTempoChange(0, 120.0, true)) },
        sequence.events().mapNotNull { event ->
            val message = event.message as? MetaMessage ?: return@mapNotNull null
            if (message.type != TIME_SIGNATURE || message.data.size < 2) null else {
                val numerator = message.data[0].toInt() and 0xff; val exponent = message.data[1].toInt() and 0xff
                require(numerator > 0 && exponent in 0..5) { "Approved full melody has invalid time-signature metadata" }
                MidiTimeSignature(event.tick, numerator, 1 shl exponent)
            }
        }.toList().sortedBy(MidiTimeSignature::tick).distinctBy(MidiTimeSignature::tick).ifEmpty { listOf(MidiTimeSignature(0, 4, 4, true)) }
    )

    private fun Sequence.events(): kotlin.sequences.Sequence<MidiEvent> = tracks.asSequence().flatMap { track ->
        (0 until track.size()).asSequence().map(track::get)
    }

    private fun trackName(track: javax.sound.midi.Track): String? = (0 until track.size()).map(track::get).firstNotNullOfOrNull { event ->
        (event.message as? MetaMessage)?.takeIf { it.type == TRACK_NAME }?.data?.toString(Charsets.UTF_8)
    }

    private fun trackName(value: String): MetaMessage = MetaMessage().also { message ->
        val bytes = value.toByteArray(Charsets.UTF_8); message.setMessage(TRACK_NAME, bytes, bytes.size)
    }

    private fun MidiMessage.copy(): MidiMessage = clone() as MidiMessage
    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private data class ClippedOccurrence(val path: Path, val projectRelativePath: String, val sha256: String, val timing: MidiTimingSummary)

    private companion object {
        val HASH = Regex("[0-9a-f]{64}")
        const val TEMPO = 0x51
        const val TIME_SIGNATURE = 0x58
        const val TRACK_NAME = 0x03
        const val END_OF_TRACK = 0x2F
    }
}

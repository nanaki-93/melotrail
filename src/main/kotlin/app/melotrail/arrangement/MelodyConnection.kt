package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** The small, allow-listed vocabulary for connecting two already-authored sections. */
@Serializable
enum class MelodyConnectionStrategy {
    NONE, HOLD_LAST_NOTE, EXTEND_CHORD, INSERT_REST, PICKUP, STEPWISE_PICKUP, VELOCITY_RAMP, SIMPLIFY_ENDING
}

/** One reviewable strategy decision for an adjacent pair of source-song occurrences. */
@Serializable
data class MelodyConnectionDecision(
    val boundaryId: String,
    val outgoingInstanceId: String,
    val incomingInstanceId: String,
    val strategy: MelodyConnectionStrategy
) {
    init {
        require(IDENTIFIER.matches(boundaryId) && IDENTIFIER.matches(outgoingInstanceId) && IDENTIFIER.matches(incomingInstanceId)) {
            "Melody connection decision identity is invalid"
        }
    }
}

/** Per-boundary decision plus its deterministic, note-level before/after evidence. */
@Serializable
data class MelodyConnectionBoundaryReport(
    val decision: MelodyConnectionDecision,
    val report: MidiMutationReport
) {
    init {
        require(report.stage == MidiMutationStage.MELODY_CONNECTION && report.target == decision.boundaryId) {
            "Melody connection report does not match its boundary decision"
        }
    }
}

/** The inspectable record for a connected source-song candidate. */
@Serializable
data class MelodyConnection(
    val version: Int = VERSION,
    val sourceSongContextSha256: String,
    val inputMidiSha256: String,
    val outputMidi: WorkflowArtifactReference,
    val boundaries: List<MelodyConnectionBoundaryReport>
) {
    init {
        require(version == VERSION && SHA_256.matches(sourceSongContextSha256) && SHA_256.matches(inputMidiSha256)) {
            "Melody connection identity is invalid"
        }
        require(boundaries.map { it.decision.boundaryId }.distinct().size == boundaries.size) {
            "Melody connection boundary IDs must be unique"
        }
    }

    companion object { const val VERSION = 1 }
}

/** A persisted connected MIDI candidate and its sidecar. Neither replaces the assembled source song. */
data class MelodyConnectionArtifact(val connection: MelodyConnection, val metadataPath: Path)

/** Stable, content-addressed locations for one connected source-song candidate. */
object MelodyConnectionArtifactPaths {
    /** MIDI artifact path for a validated decision fingerprint. */
    fun midi(contextSha256: String, decisionSha256: String): String = base(contextSha256, decisionSha256) + "/connected.mid"

    /** JSON decision/report sidecar path for a validated decision fingerprint. */
    fun metadata(contextSha256: String, decisionSha256: String): String = base(contextSha256, decisionSha256) + "/connection.json"

    private fun base(contextSha256: String, decisionSha256: String): String {
        require(SHA_256.matches(contextSha256) && SHA_256.matches(decisionSha256)) { "Melody connection path hash is invalid" }
        return "source-song/$contextSha256/connections/$decisionSha256"
    }
}

/** Boundary context supplied to deterministic or Qwen-backed strategy selection. */
data class MelodyConnectionBoundary(
    val boundaryId: String,
    val outgoing: SourceSongSection,
    val incoming: SourceSongSection,
    val outgoingNotes: List<MelodyConnectionNote>,
    val incomingNotes: List<MelodyConnectionNote>,
    val canonicalBeatTicks: Long
)

/** A selected source note with its immutable identity and writable assembled-MIDI events. */
class MelodyConnectionNote internal constructor(
    val identity: MelodyIdentityNote,
    val startEvent: MidiEvent,
    val endEvent: MidiEvent,
    val track: javax.sound.midi.Track,
    val globalStartTick: Long,
    val globalEndTick: Long
)

/** Strategy-selection boundary: Qwen may choose only this enum, never raw MIDI edits. */
fun interface MelodyConnectionStrategySelector {
    /** Choose one strategy for this boundary; the executor validates and performs every MIDI edit. */
    fun choose(boundary: MelodyConnectionBoundary): MelodyConnectionStrategy
}

/** Default conservative selector: fill a genuine trailing gap, otherwise leave the authored boundary untouched. */
object DeterministicMelodyConnectionStrategySelector : MelodyConnectionStrategySelector {
    override fun choose(boundary: MelodyConnectionBoundary): MelodyConnectionStrategy {
        val last = boundary.outgoingNotes.maxByOrNull(MelodyConnectionNote::globalEndTick) ?: return MelodyConnectionStrategy.NONE
        val windowStart = boundary.outgoing.endTick - 2 * boundary.canonicalBeatTicks
        return if (last.globalStartTick >= windowStart && last.globalEndTick < boundary.outgoing.endTick) MelodyConnectionStrategy.HOLD_LAST_NOTE else MelodyConnectionStrategy.NONE
    }
}

/** Adapter point for a Qwen client that returns one already-parsed allow-listed strategy. */
class QwenMelodyConnectionStrategySelector(
    private val delegate: MelodyConnectionStrategySelector
) : MelodyConnectionStrategySelector {
    override fun choose(boundary: MelodyConnectionBoundary): MelodyConnectionStrategy = delegate.choose(boundary)
}

/**
 * Creates one bounded connected-MIDI candidate from an immutable [SourceSong].
 * It may touch only the final two bars of the outgoing occurrence or first two
 * bars of the incoming occurrence; it never alters project harmony or source MIDI.
 */
class MelodyConnectionPlanner(
    private val strategySelector: MelodyConnectionStrategySelector = DeterministicMelodyConnectionStrategySelector
) {
    /** Plan, validate, and atomically publish an inspectable candidate for every adjacent occurrence boundary. */
    fun connect(root: Path, sourceSong: SourceSong): MelodyConnectionArtifact {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val input = normalizedRoot.resolve(sourceSong.assembledMidi.file).normalize()
        require(input.startsWith(normalizedRoot) && Files.isRegularFile(input) && melodyConnectionSha256(input) == sourceSong.assembledMidi.sha256) {
            "Source-song MIDI is missing or changed"
        }
        val sequence = try { MidiSystem.getSequence(input.toFile()) } catch (error: Exception) {
            throw IllegalArgumentException("Source-song MIDI is malformed", error)
        }
        require(sequence.divisionType == Sequence.PPQ && sequence.resolution == sourceSong.canonicalPpq) {
            "Source-song MIDI timing is incompatible with its sidecar"
        }
        require(sequence.tracks.count { trackName(it) == sourceSong.fullMelody.melodyTrackName } == 1) {
            "Source-song MIDI must contain exactly one canonical full melody track"
        }
        val fullIdentity = MelodyIdentityBuilder.build(input,
            canonicalBeatTicks = canonicalBeatTicks(sourceSong),
            occurrenceWindows = sourceSong.fullMelody.occurrences.map { window -> MelodyOccurrenceWindow(window.occurrenceId, window.startTick, window.endTick) })
        val anchorIdentity = fullIdentity.copy(anchorIds = (fullIdentity.anchorIds + sourceSong.fullMelody.protectedAnchorIdentityIds(fullIdentity)).distinct().sortedBy(MelodyNoteId::value))
        val notes = collectNotes(sequence, sourceSong, fullIdentity)
        val reports = sourceSong.sections.zipWithNext().mapIndexed { index, (outgoing, incoming) ->
            val beat = canonicalBeatTicks(sourceSong)
            val boundary = MelodyConnectionBoundary(
                boundaryId = "boundary-${index.toString().padStart(5, '0')}", outgoing = outgoing, incoming = incoming,
                outgoingNotes = notes.getValue(outgoing.instance.instanceId), incomingNotes = notes.getValue(incoming.instance.instanceId),
                canonicalBeatTicks = beat
            )
            apply(boundary, anchorIdentity, sourceSong.assembledMidi.sha256, sourceSong.contextSha256)
        }
        val decisionSha256 = digest(reports.joinToString("|") { report ->
            val mutations = report.report.mutations.joinToString(",") { mutation -> mutation.noteId.value + mutation.operation.name }
            "${report.decision.boundaryId}:${report.decision.strategy.name}:$mutations"
        })
        val relativeMidi = MelodyConnectionArtifactPaths.midi(sourceSong.contextSha256, decisionSha256)
        val relativeMetadata = MelodyConnectionArtifactPaths.metadata(sourceSong.contextSha256, decisionSha256)
        val midiPath = normalizedRoot.resolve(relativeMidi)
        val metadataPath = normalizedRoot.resolve(relativeMetadata)
        Files.createDirectories(requireNotNull(midiPath.parent))
        val temporary = Files.createTempFile(midiPath.parent, ".melody-connection-", ".mid")
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write melody connection MIDI" }
            val outputSha256 = melodyConnectionSha256(temporary)
            val finalizedReports = reports.map { boundary ->
                boundary.copy(report = boundary.report.copy(outputSha256 = outputSha256).also(MidiMutationReport::requireValid))
            }
            validateOutput(input, temporary, sourceSong, finalizedReports)
            val connection = MelodyConnection(sourceSongContextSha256 = sourceSong.contextSha256, inputMidiSha256 = sourceSong.assembledMidi.sha256,
                outputMidi = WorkflowArtifactReference(relativeMidi, outputSha256), boundaries = finalizedReports)
            publishOrVerify(temporary, midiPath, outputSha256)
            publishOrVerifyText(JSON.encodeToString(connection), metadataPath, connection)
            return MelodyConnectionArtifact(connection, metadataPath)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun apply(
        boundary: MelodyConnectionBoundary,
        outgoingIdentity: MelodyIdentity,
        inputSha256: String,
        contextSha256: String
    ): MelodyConnectionBoundaryReport {
        val requested = strategySelector.choose(boundary)
        val mutations = when (requested) {
            MelodyConnectionStrategy.NONE -> emptyList()
            MelodyConnectionStrategy.HOLD_LAST_NOTE -> holdLast(boundary)
            MelodyConnectionStrategy.EXTEND_CHORD -> extendChord(boundary)
            MelodyConnectionStrategy.INSERT_REST -> insertRest(boundary)
            MelodyConnectionStrategy.PICKUP -> pickup(boundary, stepwise = false)
            MelodyConnectionStrategy.STEPWISE_PICKUP -> pickup(boundary, stepwise = true)
            MelodyConnectionStrategy.VELOCITY_RAMP -> velocityRamp(boundary)
            MelodyConnectionStrategy.SIMPLIFY_ENDING -> simplifyEnding(boundary, outgoingIdentity)
        }
        val strategy = if (mutations.isEmpty()) MelodyConnectionStrategy.NONE else requested
        val sorted = mutations.sortedWith(compareBy<MidiMutation> { it.noteId.value }.thenBy { it.operation.ordinal })
        MidiMutationInvariants.requireAnchorPreservation(outgoingIdentity, sorted.filter { it.operation != MidiMutationOperation.ADD })
        val budget = MidiMutationBudget(
            originalNoteCount = boundary.outgoingNotes.size + boundary.incomingNotes.size,
            changedNotes = sorted.count { it.operation !in setOf(MidiMutationOperation.ADD, MidiMutationOperation.REMOVE) },
            additions = sorted.count { it.operation == MidiMutationOperation.ADD }, deletions = sorted.count { it.operation == MidiMutationOperation.REMOVE },
            maximumChanges = (((boundary.outgoingNotes.size + boundary.incomingNotes.size) * 10) / 100).coerceAtLeast(1),
            maximumAdditions = 1, maximumDeletions = 1
        )
        MidiMutationInvariants.requireBudget(budget)
        val decision = MelodyConnectionDecision(boundary.boundaryId, boundary.outgoing.instance.instanceId, boundary.incoming.instance.instanceId, strategy)
        return MelodyConnectionBoundaryReport(decision, MidiMutationReport(
            inputSha256 = inputSha256, outputSha256 = null, contextSha256 = contextSha256, target = boundary.boundaryId,
            stage = MidiMutationStage.MELODY_CONNECTION, mutations = sorted, budget = budget
        ).also(MidiMutationReport::requireValid))
    }

    private fun holdLast(boundary: MelodyConnectionBoundary): List<MidiMutation> {
        val note = boundary.outgoingNotes.maxByOrNull(MelodyConnectionNote::globalEndTick) ?: return emptyList()
        if (note.globalStartTick < boundary.outgoing.endTick - 2 * boundary.canonicalBeatTicks || note.globalEndTick >= boundary.outgoing.endTick) return emptyList()
        val before = values(note)
        note.endEvent.tick = boundary.outgoing.endTick
        return listOf(MidiMutation(MidiMutationOperation.DURATION, note.identity.id, before, before.copy(endTick = localEnd(note, boundary.outgoing.endTick)), MidiMutationReasonCode.TRANSITION_SMOOTHING, "hold-last-note"))
    }

    private fun extendChord(boundary: MelodyConnectionBoundary): List<MidiMutation> {
        val candidates = boundary.outgoingNotes.filter { it.globalEndTick < boundary.outgoing.endTick && it.globalStartTick >= boundary.outgoing.endTick - 2 * boundary.canonicalBeatTicks }
            .sortedByDescending(MelodyConnectionNote::globalEndTick).take(1)
        if (candidates.isEmpty()) return emptyList()
        return candidates.map { note ->
            val before = values(note); note.endEvent.tick = boundary.outgoing.endTick
            MidiMutation(MidiMutationOperation.DURATION, note.identity.id, before, before.copy(endTick = localEnd(note, boundary.outgoing.endTick)), MidiMutationReasonCode.TRANSITION_SMOOTHING, "extend-chord")
        }
    }

    private fun insertRest(boundary: MelodyConnectionBoundary): List<MidiMutation> {
        val note = boundary.outgoingNotes.maxByOrNull(MelodyConnectionNote::globalEndTick) ?: return emptyList()
        val rest = boundary.canonicalBeatTicks.coerceAtMost((note.globalEndTick - note.globalStartTick) / 2)
        val end = note.globalEndTick - rest
        if (rest <= 0 || end <= note.globalStartTick) return emptyList()
        val before = values(note); note.endEvent.tick = end
        return listOf(MidiMutation(MidiMutationOperation.DURATION, note.identity.id, before, before.copy(endTick = localEnd(note, end)), MidiMutationReasonCode.PHRASE_SHAPING, "insert-rest"))
    }

    private fun pickup(boundary: MelodyConnectionBoundary, stepwise: Boolean): List<MidiMutation> {
        val last = boundary.outgoingNotes.maxByOrNull(MelodyConnectionNote::globalEndTick) ?: return emptyList()
        val start = maxOf(boundary.outgoing.endTick - boundary.canonicalBeatTicks / 2, last.globalEndTick, boundary.outgoing.startTick)
        if (start >= boundary.outgoing.endTick) return emptyList()
        val harmony = boundary.outgoing.canonicalHarmony.lastOrNull { start in it.startTick until it.endTick } ?: return emptyList()
        val allowed = harmony.quality.intervals.map { (harmony.rootChromatic + it).mod(12) }.toSet()
        val pitch = candidatePickupPitch(last.identity.pitch, allowed, stepwise) ?: return emptyList()
        val duration = (boundary.outgoing.endTick - start).coerceAtLeast(1)
        val id = MelodyNoteId.derive(digest("${boundary.boundaryId}|pickup|$pitch|$start"), 0, last.identity.channel, 0, pitch, 0, duration)
        val values = MidiMutationValues(last.identity.channel, pitch, (last.identity.velocity - 8).coerceIn(1, 127), start, start + duration)
        last.track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, values.channel, values.pitch, values.velocity), start))
        last.track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, values.channel, values.pitch, 0), start + duration))
        return listOf(MidiMutation(MidiMutationOperation.ADD, id, null, values, MidiMutationReasonCode.TRANSITION_SMOOTHING, if (stepwise) "stepwise-pickup" else "pickup"))
    }

    private fun velocityRamp(boundary: MelodyConnectionBoundary): List<MidiMutation> {
        val candidates = boundary.outgoingNotes.filter { it.globalStartTick >= boundary.outgoing.endTick - 2 * boundary.canonicalBeatTicks }.sortedBy(MelodyConnectionNote::globalStartTick).takeLast(2)
        if (candidates.isEmpty()) return emptyList()
        return candidates.mapIndexed { index, note ->
            val before = values(note); val velocity = (note.identity.velocity + 6 + index * 6).coerceAtMost(127)
            (note.startEvent.message as ShortMessage).setMessage(ShortMessage.NOTE_ON, note.identity.channel, note.identity.pitch, velocity)
            MidiMutation(MidiMutationOperation.VELOCITY, note.identity.id, before, before.copy(velocity = velocity), MidiMutationReasonCode.PHRASE_SHAPING, "velocity-ramp")
        }
    }

    private fun simplifyEnding(boundary: MelodyConnectionBoundary, identity: MelodyIdentity): List<MidiMutation> {
        val note = boundary.outgoingNotes.filter { it.identity.id !in identity.anchorIds && it.globalStartTick >= boundary.outgoing.endTick - 2 * boundary.canonicalBeatTicks }
            .minByOrNull(MelodyConnectionNote::globalStartTick) ?: return emptyList()
        note.track.remove(note.startEvent); note.track.remove(note.endEvent)
        return listOf(MidiMutation(MidiMutationOperation.REMOVE, note.identity.id, values(note), null, MidiMutationReasonCode.DENSITY_REDUCTION, "simplify-ending"))
    }

    private fun validateOutput(input: Path, output: Path, song: SourceSong, reports: List<MelodyConnectionBoundaryReport>) {
        val before = MidiSystem.getSequence(input.toFile())
        val after = MidiSystem.getSequence(output.toFile())
        MidiMutationInvariants.requireTempoMeterPreserved(before, after)
        require(after.divisionType == Sequence.PPQ && after.resolution == song.canonicalPpq && after.tickLength == before.tickLength) {
            "Melody connection changed canonical song timing"
        }
        val melodyTracks = after.tracks.filter { trackName(it) == song.fullMelody.melodyTrackName }
        require(melodyTracks.size == 1 && after.tracks.size == 2 && (0 until melodyTracks.single().size()).none { index ->
            (melodyTracks.single()[index].message as? ShortMessage)?.command == ShortMessage.CONTROL_CHANGE
        }) { "Melody connection broke the canonical full-melody track contract" }
        val outputIdentity = MelodyIdentityBuilder.build(output, canonicalBeatTicks(song), song.fullMelody.occurrences.map { window ->
            MelodyOccurrenceWindow(window.occurrenceId, window.startTick, window.endTick)
        })
        require(outputIdentity.notes.zipWithNext().all { (left, right) -> left.originalEndTick <= right.originalStartTick }) {
            "Melody connection created a cross-boundary or polyphonic melody overlap"
        }
        reports.forEach { report -> report.report.requireValid() }
    }

    private fun collectNotes(sequence: Sequence, song: SourceSong, identity: MelodyIdentity): Map<String, List<MelodyConnectionNote>> {
        val sections = song.sections.associateBy { it.instance.instanceId }
        val result = sections.keys.associateWith { mutableListOf<MelodyConnectionNote>() }.toMutableMap()
        sequence.tracks.forEachIndexed { trackIndex, track ->
            if (trackName(track) != song.fullMelody.melodyTrackName) return@forEachIndexed
            val identityByOrdinal = identity.notes.filter { it.track == trackIndex }.associateBy { it.channel to it.noteOnOrdinal }
            val ordinals = mutableMapOf<Int, Int>(); val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<MelodyIdentityNote, MidiEvent>>>()
            (0 until track.size()).forEach { index ->
                val event = track[index]; val message = event.message as? ShortMessage ?: return@forEach
                val key = message.channel to message.data1
                if (message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                    val ordinal = ordinals.getOrDefault(message.channel, 0); ordinals[message.channel] = ordinal + 1
                    val noteIdentity = requireNotNull(identityByOrdinal[message.channel to ordinal]) { "Source-song full-melody note identity is missing" }
                    active.getOrPut(key) { ArrayDeque() }.addLast(noteIdentity to event)
                } else if (message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0) {
                    val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Source-song MIDI has unmatched note-off")
                    val section = requireNotNull(sections.values.singleOrNull { start.second.tick >= it.startTick && event.tick <= it.endTick }) {
                        "Source-song full-melody note crosses an occurrence boundary"
                    }
                    result.getValue(section.instance.instanceId) += MelodyConnectionNote(start.first, start.second, event, track, start.second.tick, event.tick)
                }
            }
            require(active.values.all { it.isEmpty() }) { "Source-song MIDI has an unclosed note" }
        }
        return result.mapValues { (_, notes) -> notes.sortedBy(MelodyConnectionNote::globalStartTick) }
    }

    private fun canonicalBeatTicks(song: SourceSong): Long = song.canonicalPpq * 4L / song.meterDenominator
    private fun localEnd(note: MelodyConnectionNote, globalEnd: Long): Long = globalEnd
    private fun values(note: MelodyConnectionNote): MidiMutationValues = MidiMutationValues(note.identity.channel, note.identity.pitch, note.identity.velocity, note.identity.originalStartTick, note.identity.originalEndTick)
    private fun candidatePickupPitch(lastPitch: Int, allowed: Set<Int>, stepwise: Boolean): Int? = (if (stepwise) (1..2).flatMap { listOf(lastPitch - it, lastPitch + it) } else (0..12).flatMap { listOf(lastPitch + it, lastPitch - it) })
        .firstOrNull { it in 0..127 && it.mod(12) in allowed && (!stepwise || abs(it - lastPitch) in 1..2) }
    private fun trackName(track: javax.sound.midi.Track): String? = (0 until track.size()).map(track::get).firstNotNullOfOrNull { event ->
        val message = event.message as? javax.sound.midi.MetaMessage
        message?.takeIf { it.type == 0x03 }?.data?.toString(Charsets.UTF_8)
    }
    private fun publishOrVerify(temporary: Path, target: Path, hash: String) {
        if (Files.exists(target)) { require(melodyConnectionSha256(target) == hash) { "Existing melody connection MIDI differs; preserving known-good candidate" }; return }
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) }
    }
    private fun publishOrVerifyText(serialized: String, target: Path, expected: MelodyConnection) {
        if (Files.exists(target)) { require(JSON.decodeFromString<MelodyConnection>(Files.readString(target)) == expected) { "Existing melody connection report differs; preserving known-good candidate" }; return }
        val temporary = Files.createTempFile(requireNotNull(target.parent), ".melody-connection-", ".json")
        try { Files.writeString(temporary, serialized); try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE) } catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target) } } finally { Files.deleteIfExists(temporary) }
    }
}

private val JSON = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false; prettyPrint = true }
private val SHA_256 = Regex("[0-9a-f]{64}")
private val IDENTIFIER = Regex("[A-Za-z0-9_-]{1,80}")
private fun melodyConnectionSha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

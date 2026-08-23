package app.melotrail.arrangement

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** Deterministic severities used by the pre-arrangement source-song gate. */
@Serializable
enum class SourceSongIssueSeverity { WARNING, BLOCKING }

/** Objective checks applied to the connected source melody before arrangement. */
@Serializable
enum class SourceSongIssueCategory {
    BOUNDARY_TIMING,
    UNEXPECTED_GAP,
    UNEXPECTED_OVERLAP,
    EXTREME_JUMP,
    PHRASE_LENGTH,
    CHORD_COMPATIBILITY,
    IDENTITY_PRESERVATION
}

/** A tick- and bar-addressable source-song issue location. */
@Serializable
data class SourceSongIssueLocation(
    val boundaryId: String,
    val bar: Long,
    val startTick: Long,
    val endTick: Long
) {
    init {
        require(IDENTIFIER.matches(boundaryId) && bar >= 0 && startTick >= 0 && endTick > startTick) {
            "Source-song issue location is invalid"
        }
    }
}

/** Structured evidence for one deterministic source-song check. */
@Serializable
data class SourceSongIssue(
    val id: String,
    val category: SourceSongIssueCategory,
    val severity: SourceSongIssueSeverity,
    val location: SourceSongIssueLocation,
    val message: String,
    val observed: Double,
    val threshold: Double
) {
    init {
        require(IDENTIFIER.matches(id) && message.length in 1..180 && message.none { it.isISOControl() } &&
            observed.isFinite() && threshold.isFinite()) { "Source-song issue is invalid" }
    }
}

/** Immutable report for one assembled source-song and connected-MIDI candidate. */
@Serializable
data class SourceSongCriticReport(
    val version: Int = VERSION,
    val sourceSongContextSha256: String,
    val sourceMidiSha256: String,
    val connectedMidi: WorkflowArtifactReference,
    val issues: List<SourceSongIssue>
) {
    init {
        require(version == VERSION && HASH.matches(sourceSongContextSha256) && HASH.matches(sourceMidiSha256) &&
            issues.map(SourceSongIssue::id).distinct().size == issues.size && issues == issues.sortedWith(ISSUE_ORDER)) {
            "Source-song critic report is invalid"
        }
    }

    /** True when arrangement needs an explicit user override rather than ordinary approval. */
    val hasBlockingIssues: Boolean get() = issues.any { it.severity == SourceSongIssueSeverity.BLOCKING }

    companion object {
        const val VERSION = 1
        internal val ISSUE_ORDER = compareBy<SourceSongIssue> { it.location.startTick }
            .thenBy { it.category.ordinal }.thenBy(SourceSongIssue::id)
    }
}

/** A persisted user decision granting the source melody access to arrangement. */
@Serializable
data class SourceSongApproval(
    val version: Int = VERSION,
    val sourceSongContextSha256: String,
    val sourceMidiSha256: String,
    val connectedMidiSha256: String,
    val criticReport: WorkflowArtifactReference,
    val overriddenBlockingIssueIds: List<String> = emptyList(),
    val overrideReason: String? = null
) {
    init {
        require(version == VERSION && HASH.matches(sourceSongContextSha256) && HASH.matches(sourceMidiSha256) &&
            HASH.matches(connectedMidiSha256) && overriddenBlockingIssueIds.distinct().size == overriddenBlockingIssueIds.size &&
            overriddenBlockingIssueIds.all(IDENTIFIER::matches) &&
            ((overriddenBlockingIssueIds.isEmpty() && overrideReason == null) ||
                (overriddenBlockingIssueIds.isNotEmpty() && overrideReason != null && overrideReason.length in 1..180 && overrideReason.none { it.isISOControl() }))) {
            "Source-song approval is invalid"
        }
    }

    companion object { const val VERSION = 1 }
}

/** Stable source-song critic evidence locations, all rooted below the project directory. */
object SourceSongCriticArtifactPaths {
    /** Return the deterministic report path for one connected source candidate. */
    fun report(contextSha256: String, connectedMidiSha256: String): String = base(contextSha256, connectedMidiSha256) + "/report.json"

    /** Return the persisted explicit approval location for one reviewed candidate. */
    fun approval(contextSha256: String, connectedMidiSha256: String): String = base(contextSha256, connectedMidiSha256) + "/approval.json"

    /** Validate and combine the two content fingerprints that scope critic evidence. */
    private fun base(contextSha256: String, connectedMidiSha256: String): String {
        require(HASH.matches(contextSha256) && HASH.matches(connectedMidiSha256)) { "Source-song critic artifact path is invalid" }
        return "source-song/$contextSha256/critic/$connectedMidiSha256"
    }
}

/** Fully resolved input for the deterministic source-song critic. */
data class SourceSongCriticInput(
    val root: Path,
    val sourceSong: SourceSong,
    val connection: MelodyConnection,
    val projectScalePitchClasses: Set<Int>
) {
    /** Reject non-canonical inputs before objective musical checks run. */
    fun requireValid() {
        require(projectScalePitchClasses.isNotEmpty() && projectScalePitchClasses.all { it in 0..11 }) {
            "Source-song critic requires project-scale pitch classes"
        }
        require(connection.sourceSongContextSha256 == sourceSong.contextSha256 &&
            connection.inputMidiSha256 == sourceSong.assembledMidi.sha256) { "Melody connection does not match source song" }
    }
}

/** Performs deterministic source-only quality checks without modifying any MIDI. */
class SourceSongCritic {
    /** Return a sorted, reproducible report for the exact connected source candidate. */
    fun criticize(input: SourceSongCriticInput): SourceSongCriticReport {
        input.requireValid()
        val root = input.root.toAbsolutePath().normalize()
        val source = verified(root, input.sourceSong.assembledMidi, "Assembled source-song MIDI")
        val connected = verified(root, input.connection.outputMidi, "Connected source-song MIDI")
        val sourceSequence = parse(source, "Assembled source-song MIDI")
        val connectedSequence = parse(connected, "Connected source-song MIDI")
        require(sourceSequence.divisionType == Sequence.PPQ && connectedSequence.divisionType == Sequence.PPQ &&
            sourceSequence.resolution == input.sourceSong.canonicalPpq && connectedSequence.resolution == input.sourceSong.canonicalPpq) {
            "Source-song critic requires matching PPQ MIDI"
        }
        val notes = notes(connectedSequence)
        val beat = canonicalBeatTicks(input.sourceSong)
        val bar = beat * meterNumerator(sourceSequence)
        val issues = buildList {
            addAll(boundaries(input.sourceSong, connectedSequence, notes, beat, bar))
            addAll(phrases(input.sourceSong, notes, beat, bar))
            addAll(chords(input.sourceSong, notes, input.projectScalePitchClasses, bar))
            addAll(identity(input.sourceSong, input.connection, root, bar))
        }.sortedWith(SourceSongCriticReport.ISSUE_ORDER)
        return SourceSongCriticReport(
            sourceSongContextSha256 = input.sourceSong.contextSha256,
            sourceMidiSha256 = input.sourceSong.assembledMidi.sha256,
            connectedMidi = input.connection.outputMidi,
            issues = issues
        )
    }

    /** Confirm the referenced artifact is confined, present, and fingerprint-matched. */
    private fun verified(root: Path, reference: WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && sourceSongCriticSha256(path) == reference.sha256) { "$label is missing or stale" }
        return path
    }

    /** Parse one standard MIDI file into a sequence or surface a bounded validation error. */
    private fun parse(path: Path, label: String): Sequence = try {
        MidiSystem.getSequence(path.toFile())
    } catch (error: Exception) {
        throw IllegalArgumentException("$label is malformed", error)
    }

    /** Check each adjacent occurrence for invalid timing, long silence, overlap, and large melodic jumps. */
    private fun boundaries(song: SourceSong, sequence: Sequence, notes: List<Note>, beat: Long, bar: Long): List<SourceSongIssue> = song.sections.zipWithNext().flatMapIndexed { index, (outgoing, incoming) ->
        val id = boundaryId(index)
        val boundary = incoming.startTick
        val outgoingNotes = notes.filter { it.startTick >= outgoing.startTick && it.startTick < outgoing.endTick }
        val incomingNotes = notes.filter { it.startTick >= incoming.startTick && it.startTick < incoming.endTick }
        buildList {
            if (outgoing.endTick != incoming.startTick || boundary <= 0 || sequence.tickLength != song.sections.last().endTick) {
                add(issue(SourceSongIssueCategory.BOUNDARY_TIMING, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, boundary, boundary + 1, "boundary timing does not match canonical source-song structure", sequence.tickLength.toDouble(), song.sections.last().endTick.toDouble()))
            }
            val crossing = outgoingNotes.filter { it.endTick > boundary }
            if (crossing.isNotEmpty()) {
                add(issue(SourceSongIssueCategory.UNEXPECTED_OVERLAP, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, boundary, crossing.maxOf(Note::endTick), "source notes overlap the next section boundary", (crossing.maxOf(Note::endTick) - boundary).toDouble(), 0.0))
            }
            val last = outgoingNotes.maxWithOrNull(compareBy<Note> { it.endTick }.thenBy(Note::pitch))
            val first = incomingNotes.minWithOrNull(compareBy<Note> { it.startTick }.thenByDescending(Note::pitch))
            if (last == null || first == null) {
                add(issue(SourceSongIssueCategory.BOUNDARY_TIMING, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, boundary, boundary + beat, "a source section has no playable melody near this boundary", 0.0, 1.0))
            } else {
                val gap = first.startTick - last.endTick
                if (gap > bar) add(issue(SourceSongIssueCategory.UNEXPECTED_GAP, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, last.endTick, first.startTick, "unexpected silence exceeds one bar at a source boundary", gap.toDouble(), bar.toDouble()))
                else if (gap > beat) add(issue(SourceSongIssueCategory.UNEXPECTED_GAP, SourceSongIssueSeverity.WARNING, id, incoming.startBar, last.endTick, first.startTick, "silence exceeds one beat at a source boundary", gap.toDouble(), beat.toDouble()))
                val leap = abs(first.pitch - last.pitch)
                if (leap > 24) add(issue(SourceSongIssueCategory.EXTREME_JUMP, SourceSongIssueSeverity.BLOCKING, id, incoming.startBar, maxOf(last.startTick, boundary - beat), minOf(first.endTick, boundary + beat), "melodic boundary jump exceeds two octaves", leap.toDouble(), 24.0))
                else if (leap > 19) add(issue(SourceSongIssueCategory.EXTREME_JUMP, SourceSongIssueSeverity.WARNING, id, incoming.startBar, maxOf(last.startTick, boundary - beat), minOf(first.endTick, boundary + beat), "melodic boundary jump exceeds a twelfth", leap.toDouble(), 19.0))
            }
        }
    }

    /** Flag empty, implausibly short, or unusually long phrase groups within each canonical occurrence. */
    private fun phrases(song: SourceSong, notes: List<Note>, beat: Long, bar: Long): List<SourceSongIssue> = song.sections.flatMapIndexed { sectionIndex, section ->
        val sectionNotes = notes.filter { it.startTick in section.startTick until section.endTick }.sortedBy(Note::startTick)
        if (sectionNotes.isEmpty()) return@flatMapIndexed listOf(issue(SourceSongIssueCategory.PHRASE_LENGTH, SourceSongIssueSeverity.BLOCKING, boundaryId(sectionIndex), section.startBar, section.startTick, section.endTick, "source section contains no playable notes", 0.0, 1.0))
        val groups = mutableListOf<MutableList<Note>>()
        sectionNotes.forEach { note ->
            val previousEnd = groups.lastOrNull()?.maxOfOrNull(Note::endTick)
            if (previousEnd == null || note.startTick - previousEnd >= beat) groups.add(mutableListOf())
            groups.last() += note
        }
        groups.mapNotNull { group ->
            val start = group.minOf(Note::startTick); val end = group.maxOf(Note::endTick); val duration = end - start
            when {
                duration < beat / 4 -> issue(SourceSongIssueCategory.PHRASE_LENGTH, SourceSongIssueSeverity.WARNING, boundaryId(sectionIndex), start / bar, start, end, "source phrase is shorter than a sixteenth-note beat fraction", duration.toDouble(), (beat / 4).toDouble())
                duration > bar * 16 -> issue(SourceSongIssueCategory.PHRASE_LENGTH, SourceSongIssueSeverity.BLOCKING, boundaryId(sectionIndex), start / bar, start, end, "source phrase exceeds sixteen bars without a phrase break", duration.toDouble(), (bar * 16).toDouble())
                else -> null
            }
        }
    }

    /** Check that sustained non-scale notes also fit the authoritative active chord. */
    private fun chords(song: SourceSong, notes: List<Note>, scale: Set<Int>, bar: Long): List<SourceSongIssue> = notes.mapNotNull { note ->
        val sectionIndex = song.sections.indexOfLast { note.startTick >= it.startTick && note.startTick < it.endTick }
        if (sectionIndex < 0) return@mapNotNull issue(SourceSongIssueCategory.BOUNDARY_TIMING, SourceSongIssueSeverity.BLOCKING, boundaryId(0), 0, note.startTick, note.endTick, "source note is outside canonical section timing", note.startTick.toDouble(), 0.0)
        val section = song.sections[sectionIndex]
        val chord = section.canonicalHarmony.singleOrNull { note.startTick in it.startTick until it.endTick }
            ?: return@mapNotNull issue(SourceSongIssueCategory.CHORD_COMPATIBILITY, SourceSongIssueSeverity.BLOCKING, boundaryId(sectionIndex), note.startTick / bar, note.startTick, note.endTick, "source note has no authoritative harmony span", note.pitch.toDouble(), 0.0)
        val pitchClass = note.pitch % 12
        val chordTones = chord.quality.intervals.map { (chord.rootChromatic + it) % 12 }.toSet()
        if (pitchClass in chordTones || pitchClass in scale) null
        else issue(SourceSongIssueCategory.CHORD_COMPATIBILITY, if (note.endTick - note.startTick >= bar) SourceSongIssueSeverity.BLOCKING else SourceSongIssueSeverity.WARNING, boundaryId(sectionIndex), note.startTick / bar, note.startTick, note.endTick, "source note is outside the project scale and active canonical chord", pitchClass.toDouble(), chord.rootChromatic.toDouble())
    }

    /** Verify persisted connection evidence still preserves every protected source melody anchor. */
    private fun identity(song: SourceSong, connection: MelodyConnection, root: Path, bar: Long): List<SourceSongIssue> {
        val expected = song.sections.zipWithNext().mapIndexed { index, (outgoing, incoming) -> boundaryId(index) to (outgoing to incoming) }.toMap()
        val reports = connection.boundaries.associateBy { it.decision.boundaryId }
        val issues = mutableListOf<SourceSongIssue>()
        if (reports.keys != expected.keys) {
            issues += issue(SourceSongIssueCategory.IDENTITY_PRESERVATION, SourceSongIssueSeverity.BLOCKING, boundaryId(0), 0, 0, 1, "connection evidence does not cover every canonical source boundary", reports.size.toDouble(), expected.size.toDouble())
        }
        expected.forEach { (id, pair) ->
            val report = reports[id] ?: return@forEach
            val outgoing = pair.first
            val identity = MelodyIdentityBuilder.build(root.resolve(outgoing.sourceMidi.projectRelativePath), outgoing.sourceMidi.ppq.toLong())
            val invalid = report.report.mutations.filter { mutation ->
                mutation.operation != MidiMutationOperation.ADD && (mutation.noteId !in identity.notes.map(MelodyIdentityNote::id) ||
                    (identity.isAnchor(mutation.noteId) && (mutation.operation == MidiMutationOperation.REMOVE || mutation.after?.pitch != identity.note(mutation.noteId).pitch)))
            }
            if (invalid.isNotEmpty() || report.report.inputSha256 != song.assembledMidi.sha256 || report.report.outputSha256 != connection.outputMidi.sha256) {
                issues += issue(SourceSongIssueCategory.IDENTITY_PRESERVATION, SourceSongIssueSeverity.BLOCKING, id, outgoing.endTick / bar, outgoing.endTick - 1, outgoing.endTick, "connection evidence does not preserve protected source melody identity", invalid.size.toDouble(), 0.0)
            }
        }
        return issues
    }

    /** Pair note-on and note-off events without inferring or modifying source timing. */
    private fun notes(sequence: Sequence): List<Note> {
        val active = mutableMapOf<Triple<Int, Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        val result = mutableListOf<Note>()
        sequence.tracks.forEachIndexed { trackIndex, track ->
            (0 until track.size()).forEach { eventIndex ->
                val event = track[eventIndex]; val message = event.message as? ShortMessage ?: return@forEach
                val key = Triple(trackIndex, message.channel, message.data1)
                when {
                    message.command == ShortMessage.NOTE_ON && message.data2 > 0 -> active.getOrPut(key) { ArrayDeque() }.addLast(event.tick to message.data2)
                    message.command == ShortMessage.NOTE_OFF || message.command == ShortMessage.NOTE_ON && message.data2 == 0 -> {
                        val start = active[key]?.removeFirstOrNull() ?: throw IllegalArgumentException("Connected source-song MIDI has an unmatched note-off")
                        require(event.tick > start.first) { "Connected source-song MIDI has a non-positive note" }
                        result += Note(message.data1, start.first, event.tick)
                    }
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "Connected source-song MIDI has an unclosed note" }
        return result.sortedWith(compareBy<Note> { it.startTick }.thenBy(Note::pitch).thenBy(Note::endTick))
    }

    /** Build a stable issue identifier and preserve one full tick of issue evidence. */
    private fun issue(category: SourceSongIssueCategory, severity: SourceSongIssueSeverity, boundaryId: String, bar: Long, start: Long, end: Long, message: String, observed: Double, threshold: Double): SourceSongIssue {
        val safeStart = maxOf(0, start); val safeEnd = maxOf(safeStart + 1, end)
        val id = "issue-" + digest("$category|$severity|$boundaryId|$safeStart|$safeEnd|$message").take(32)
        return SourceSongIssue(id, category, severity, SourceSongIssueLocation(boundaryId, maxOf(0, bar), safeStart, safeEnd), message, observed, threshold)
    }

    /** Convert the canonical meter denominator to the fixed PPQ beat unit. */
    private fun canonicalBeatTicks(song: SourceSong): Long = song.canonicalPpq.toLong()

    /** Read the source song's conductor meter numerator, which assembly always publishes at tick zero. */
    private fun meterNumerator(sequence: Sequence): Long = sequence.tracks.asSequence().flatMap { track -> (0 until track.size()).asSequence().map(track::get) }
        .mapNotNull { event -> (event.message as? javax.sound.midi.MetaMessage)?.takeIf { it.type == 0x58 }?.data?.firstOrNull()?.toInt()?.and(0xff)?.toLong() }
        .firstOrNull() ?: throw IllegalArgumentException("Source-song MIDI is missing its canonical meter")

    /** Build the canonical stable identifier for an adjacent-boundary position. */
    private fun boundaryId(index: Int): String = "boundary-${index.toString().padStart(5, '0')}"

    /** Compact parsed note representation used only by deterministic checks. */
    private data class Note(val pitch: Int, val startTick: Long, val endTick: Long)
}

/** Hash one local file without exposing its path in persisted evidence. */
private fun sourceSongCriticSha256(path: Path): String = Files.newInputStream(path).use { input ->
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    generateSequence { input.read(buffer).takeIf { it > 0 } }.forEach { digest.update(buffer, 0, it) }
    digest.digest().joinToString("") { "%02x".format(it) }
}

/** Hash one deterministic issue identity input. */
private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private val HASH = Regex("[0-9a-f]{64}")
private val IDENTIFIER = Regex("[A-Za-z0-9_-]{1,80}")

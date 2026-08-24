package app.melotrail.arrangement

import app.melotrail.music.MusicalKey
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlin.math.abs

/** The only range policy supported by the deterministic project-key processor. */
@Serializable
enum class MidiTransposeRangePolicy { OCTAVE_FOLD }

@Serializable
data class MidiTranspositionArtifact(val sha256: String, val ppq: Int, val eventCount: Int, val noteCount: Int) {
    fun requireValid(label: String) {
        require(SHA256.matches(sha256) && ppq > 0 && eventCount >= 0 && noteCount >= 0) { "MIDI transposition $label artifact is invalid" }
    }
}

/** One paired note movement. This is evidence, never a request to quantize a pitch to a scale. */
@Serializable
data class MidiPitchMovement(
    val channel: Int,
    val startTick: Long,
    val endTick: Long,
    val sourcePitch: Int,
    val outputPitch: Int,
    val octaveFolded: Boolean,
    val mappingKind: MidiPitchMappingKind,
    val sourceScaleDegree: Int? = null
) {
    init {
        require(channel in 0..15 && startTick >= 0 && endTick > startTick && sourcePitch in 0..127 && outputPitch in 0..127) {
            "MIDI pitch movement is invalid"
        }
    }
}

/** Explains whether a note used tonic transposition, a mode-aware degree, or an unresolved chromatic fallback. */
@Serializable
enum class MidiPitchMappingKind { TONIC_CHROMATIC, MODE_AWARE_SCALE_DEGREE, UNRESOLVED_CHROMATIC }

@Serializable
data class MidiScaleFitEvidence(val notes: Int, val fittingNotes: Int) {
    init { require(notes >= 0 && fittingNotes in 0..notes) { "MIDI scale-fit evidence is invalid" } }
}

@Serializable
data class MidiChordFitEvidence(val detectedChordWindows: Int, val noteOnsets: Int) {
    init { require(detectedChordWindows >= 0 && noteOnsets >= 0) { "MIDI chord-fit evidence is invalid" } }
}

/** Hash-bound, replayable evidence for one published transposed MIDI artifact. */
@Serializable
data class MidiTranspositionReport(
    val version: Int = CURRENT_VERSION,
    val processorVersion: String = PROCESSOR_VERSION,
    val partId: String,
    val sourceKey: MusicalKey,
    val projectKey: MusicalKey,
    /** Signed chromatic interval in the compact -6..+6 representation. */
    val intervalSemitones: Int,
    val rangePolicy: MidiTransposeRangePolicy,
    val input: MidiTranspositionArtifact,
    val output: MidiTranspositionArtifact,
    val sourceScaleFit: MidiScaleFitEvidence,
    val projectScaleFit: MidiScaleFitEvidence,
    val chordFit: MidiChordFitEvidence,
    val movements: List<MidiPitchMovement>,
    val modeAdjustedMovements: Int,
    val unresolvedChromaticSourceNotes: List<MidiPitchMovement> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun requireValid() {
        require(version == CURRENT_VERSION && processorVersion == PROCESSOR_VERSION && PART_ID.matches(partId)) { "MIDI transposition report is invalid" }
        require(intervalSemitones in -6..6) { "MIDI transposition interval is invalid" }
        require(sourceKey.isExecutable && projectKey.isExecutable) { "MIDI transposition keys are not executable" }
        input.requireValid("input"); output.requireValid("output")
        sourceScaleFit.let { require(it.notes == movements.size) { "MIDI source scale evidence does not match movements" } }
        projectScaleFit.let { require(it.notes == movements.size) { "MIDI project scale evidence does not match movements" } }
        chordFit.let { require(it.noteOnsets == movements.size) { "MIDI chord evidence does not match movements" } }
        require(input.noteCount == output.noteCount) { "MIDI transposition changed note count" }
        require(modeAdjustedMovements == movements.count { it.mappingKind == MidiPitchMappingKind.MODE_AWARE_SCALE_DEGREE }) {
            "MIDI mode-adjusted movement count is invalid"
        }
        require(unresolvedChromaticSourceNotes == movements.filter { it.mappingKind == MidiPitchMappingKind.UNRESOLVED_CHROMATIC }) {
            "MIDI unresolved chromatic evidence is invalid"
        }
        require(warnings == warnings.distinct() && warnings.all { it in WARNING_CODES }) { "MIDI transposition warnings are invalid" }
    }

    companion object {
        const val CURRENT_VERSION = 2
        const val PROCESSOR_VERSION = "2"
        private val PART_ID = Regex("[A-Za-z0-9_-]{1,80}")
        private val WARNING_CODES = setOf("OCTAVE_FOLD_APPLIED", "PERCUSSION_CHANNEL_PRESERVED", "MODE_AWARE_SCALE_DEGREES", "UNRESOLVED_CHROMATIC_SOURCE_NOTES")
    }
}

/**
 * Deterministically transposes melodic note events only. It deliberately copies
 * timing, duration, velocity, controllers, tempo and meter unchanged.
 */
class MidiProjectKeyTransposer {
    fun transpose(
        partId: String,
        input: Path,
        output: Path,
        sourceKey: MusicalKey,
        projectKey: MusicalKey,
        rangePolicy: MidiTransposeRangePolicy = MidiTransposeRangePolicy.OCTAVE_FOLD
    ): MidiTranspositionReport {
        require(PART_ID.matches(partId)) { "Invalid MIDI transposition part ID" }
        require(sourceKey.isExecutable && projectKey.isExecutable) { "MIDI transposition requires executable keys" }
        val source = input.toAbsolutePath().normalize()
        val target = output.toAbsolutePath().normalize()
        require(Files.isRegularFile(source) && source != target) { "MIDI transposition input/output is invalid" }
        val inputHash = sha256(source)
        val before = read(source)
        val interval = signedInterval(sourceKey.tonic.chromatic, projectKey.tonic.chromatic)
        val movements = movements(before, sourceKey, projectKey, interval, rangePolicy)
        val sequence = Sequence(Sequence.PPQ, before.resolution)
        before.tracks.forEach { sourceTrack ->
            val targetTrack = sequence.createTrack()
            (0 until sourceTrack.size()).map(sourceTrack::get)
                .filterNot { (it.message as? MetaMessage)?.type == END_OF_TRACK }
                .forEach { event -> targetTrack.add(MidiEvent(transform(event.message, sourceKey, projectKey, interval, rangePolicy), event.tick)) }
        }
        Files.createDirectories(requireNotNull(target.parent))
        MidiSystem.write(sequence, 1, target.toFile())
        require(sha256(source) == inputHash) { "MIDI transposition changed its input" }
        val after = read(target)
        val outputEvents = after.tracks.sumOf { track -> (0 until track.size()).count { (track[it].message as? MetaMessage)?.type != END_OF_TRACK } }
        val sourceFit = MidiScaleFitEvidence(movements.size, movements.count { sourceKey.contains(app.melotrail.music.PitchClass.canonical(it.sourcePitch)) })
        val projectFit = MidiScaleFitEvidence(movements.size, movements.count { projectKey.contains(app.melotrail.music.PitchClass.canonical(it.outputPitch)) })
        val chordWindows = MidiPartAnalyzer().analyze(source, partId).chords.count { it.symbol != null }
        val warnings = buildList {
            if (movements.any(MidiPitchMovement::octaveFolded)) add("OCTAVE_FOLD_APPLIED")
            if (before.tracks.any { track -> (0 until track.size()).any { (track[it].message as? ShortMessage)?.channel == DRUM_CHANNEL } }) add("PERCUSSION_CHANNEL_PRESERVED")
            if (movements.any { it.mappingKind == MidiPitchMappingKind.MODE_AWARE_SCALE_DEGREE }) add("MODE_AWARE_SCALE_DEGREES")
            if (movements.any { it.mappingKind == MidiPitchMappingKind.UNRESOLVED_CHROMATIC }) add("UNRESOLVED_CHROMATIC_SOURCE_NOTES")
        }
        return MidiTranspositionReport(
            partId = partId, sourceKey = sourceKey, projectKey = projectKey, intervalSemitones = interval, rangePolicy = rangePolicy,
            input = MidiTranspositionArtifact(inputHash, before.resolution, eventCount(before), noteCount(before)),
            output = MidiTranspositionArtifact(sha256(target), after.resolution, outputEvents, noteCount(after)),
            sourceScaleFit = sourceFit, projectScaleFit = projectFit,
            chordFit = MidiChordFitEvidence(chordWindows, movements.size), movements = movements,
            modeAdjustedMovements = movements.count { it.mappingKind == MidiPitchMappingKind.MODE_AWARE_SCALE_DEGREE },
            unresolvedChromaticSourceNotes = movements.filter { it.mappingKind == MidiPitchMappingKind.UNRESOLVED_CHROMATIC },
            warnings = warnings
        ).also(MidiTranspositionReport::requireValid)
    }

    private fun movements(sequence: Sequence, sourceKey: MusicalKey, projectKey: MusicalKey, interval: Int, policy: MidiTransposeRangePolicy): List<MidiPitchMovement> {
        data class Start(val tick: Long, val mapping: PitchMapping)
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Start>>()
        val result = mutableListOf<MidiPitchMovement>()
        sequence.tracks.forEach { track ->
            (0 until track.size()).map(track::get).sortedBy(MidiEvent::getTick).forEach { event ->
                val message = event.message as? ShortMessage ?: return@forEach
                if (message.channel == DRUM_CHANNEL) return@forEach
                val noteOn = message.command == ShortMessage.NOTE_ON && message.data2 > 0
                val noteOff = message.command == ShortMessage.NOTE_OFF || (message.command == ShortMessage.NOTE_ON && message.data2 == 0)
                if (!noteOn && !noteOff) return@forEach
                val key = message.channel to message.data1
                if (noteOn) {
                    val mapped = mapPitch(message.data1, sourceKey, projectKey, interval, policy)
                    active.getOrPut(key) { ArrayDeque() }.addLast(Start(event.tick, mapped))
                } else {
                    val start = active[key]?.removeFirstOrNull()
                        ?: throw IllegalArgumentException("MIDI transposition input has an unmatched note-off")
                    require(event.tick > start.tick) { "MIDI transposition input has a non-positive note duration" }
                    result += MidiPitchMovement(message.channel, start.tick, event.tick, message.data1, start.mapping.pitch, start.mapping.folded,
                        start.mapping.kind, start.mapping.sourceScaleDegree)
                }
            }
        }
        require(active.values.all { it.isEmpty() }) { "MIDI transposition input has unclosed notes" }
        return result.sortedWith(compareBy<MidiPitchMovement> { it.startTick }.thenBy { it.channel }.thenBy { it.sourcePitch })
    }

    private fun transform(message: MidiMessage, sourceKey: MusicalKey, projectKey: MusicalKey, interval: Int, policy: MidiTransposeRangePolicy): MidiMessage {
        val copy = message.clone() as MidiMessage
        val short = copy as? ShortMessage ?: return copy
        val isNote = short.command == ShortMessage.NOTE_ON || short.command == ShortMessage.NOTE_OFF
        if (!isNote || short.channel == DRUM_CHANNEL) return copy
        val mapped = mapPitch(short.data1, sourceKey, projectKey, interval, policy)
        short.setMessage(short.command, short.channel, mapped.pitch, short.data2)
        return short
    }

    private fun mapPitch(pitch: Int, sourceKey: MusicalKey, projectKey: MusicalKey, interval: Int, policy: MidiTransposeRangePolicy): PitchMapping {
        val sourceDegree = sourceKey.scalePitchClasses().indexOfFirst { it.chromatic == Math.floorMod(pitch, 12) }.takeIf { it >= 0 }
        val kind = when {
            sourceKey.modeId == projectKey.modeId -> MidiPitchMappingKind.TONIC_CHROMATIC
            sourceDegree != null -> MidiPitchMappingKind.MODE_AWARE_SCALE_DEGREE
            else -> MidiPitchMappingKind.UNRESOLVED_CHROMATIC
        }
        var mapped = if (kind == MidiPitchMappingKind.MODE_AWARE_SCALE_DEGREE) {
            nearestRegister(projectKey.scalePitchClasses()[requireNotNull(sourceDegree)].chromatic, pitch + interval)
        } else pitch + interval
        var folded = false
        when (policy) {
            MidiTransposeRangePolicy.OCTAVE_FOLD -> while (mapped !in 0..127) {
                mapped += if (mapped < 0) 12 else -12
                folded = true
            }
        }
        return PitchMapping(mapped, folded, kind, sourceDegree)
    }

    private fun nearestRegister(targetPitchClass: Int, referencePitch: Int): Int {
        val lower = referencePitch - Math.floorMod(referencePitch - targetPitchClass, 12)
        val upper = lower + 12
        return if (abs(referencePitch - lower) <= abs(upper - referencePitch)) lower else upper
    }

    private fun signedInterval(source: Int, target: Int): Int {
        val ascending = Math.floorMod(target - source, 12)
        return if (ascending > 6) ascending - 12 else ascending
    }

    private fun read(path: Path): Sequence = try {
        MidiSystem.getSequence(path.toFile()).also { require(it.divisionType == Sequence.PPQ && it.resolution > 0) { "MIDI transposition requires positive PPQ MIDI" } }
    } catch (error: Exception) {
        throw IllegalArgumentException("MIDI transposition input is malformed", error)
    }

    private fun eventCount(sequence: Sequence): Int = sequence.tracks.sumOf { track ->
        (0 until track.size()).count { (track[it].message as? MetaMessage)?.type != END_OF_TRACK }
    }

    private fun noteCount(sequence: Sequence): Int = sequence.tracks.sumOf { track ->
        (0 until track.size()).count {
            val message = track[it].message as? ShortMessage
            message?.command == ShortMessage.NOTE_ON && message.data2 > 0
        }
    }

    private data class PitchMapping(val pitch: Int, val folded: Boolean, val kind: MidiPitchMappingKind, val sourceScaleDegree: Int?)

    private companion object {
        val PART_ID = Regex("[A-Za-z0-9_-]{1,80}")
        const val DRUM_CHANNEL = 9
        const val END_OF_TRACK = 0x2f
    }
}

/** Project-local report validation for selected transposed MIDI. */
object MidiTranspositionReportStore {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = false }

    fun read(projectRoot: Path, reference: String): MidiTranspositionReport = try {
        val root = projectRoot.toAbsolutePath().normalize()
        val path = root.resolve(reference).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && path.toRealPath().startsWith(root.toRealPath())) { "MIDI transposition report is missing" }
        json.decodeFromString<MidiTranspositionReport>(Files.readString(path)).also(MidiTranspositionReport::requireValid)
    } catch (error: Exception) {
        throw IllegalArgumentException("MIDI transposition report is malformed", error)
    }

    fun write(path: Path, report: MidiTranspositionReport): Path {
        report.requireValid()
        Files.writeString(path, json.encodeToString(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return path
    }

    fun isCurrent(projectRoot: Path, partId: String, input: Path, output: Path, sourceKey: MusicalKey, projectKey: MusicalKey, reference: String): Boolean = runCatching {
        val report = read(projectRoot, reference)
        val temporary = Files.createTempFile(output.parent, ".transpose-check-", ".mid")
        try {
            val expected = MidiProjectKeyTransposer().transpose(partId, input, temporary, sourceKey, projectKey)
            report == expected && report.output.sha256 == sha256(output)
        } finally { Files.deleteIfExists(temporary) }
    }.getOrDefault(false)
}

private val SHA256 = Regex("[0-9a-f]{64}")

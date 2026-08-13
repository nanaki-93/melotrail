package ai.music.workstation.arrangement

import ai.music.workstation.audio.AudioBuffer
import ai.music.workstation.audio.WAVDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * The narrow Task-017 rendering boundary. MIDI is assembled into a final,
 * transition-aware timeline, rendered one logical instrument at a time, then
 * mixed as an unprocessed PCM-24 reference. It does not call DSP or mastering.
 */
class StemRenderingMixer(
    private val renderer: InstrumentRenderer,
    private val mixer: DeterministicStemMixer = DeterministicStemMixer()
) {
    suspend fun render(
        projectRoot: Path,
        project: Project,
        arrangement: DetailedArrangement,
        analyses: Map<String, MidiAnalysis>
    ): StemRenderResult {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireCleanMidi(root)
        val format = requireNotNull(project.renderFormat)
        require(arrangement.sections.isNotEmpty()) { "Detailed arrangement has no sections to render" }
        val timeline = Timeline.create(arrangement, analyses)
        val activeNames = arrangement.sections.flatMap { it.instruments }.map { LogicalInstrument.parse(it.name) }.toSet()
        val active = LogicalInstrument.entries.filter { it in activeNames }
        require(active.isNotEmpty()) { "Detailed arrangement has no active instruments" }
        require(LogicalInstrument.PIANO in active) { "Detailed arrangement must retain the source piano" }

        val requiredInputs = active.filter { it != LogicalInstrument.PIANO }.associateWith { root.resolve("midi/generated/${it.wireName}.mid") }
        requiredInputs.forEach { (instrument, path) ->
            require(Files.isRegularFile(path)) { "Missing generated ${instrument.wireName} MIDI: $path" }
        }
        val needsTransitions = timeline.segments.any { it.insertedTicksAfter > 0L }
        val transitions = root.resolve("midi/generated/transitions.mid")
        if (needsTransitions) require(Files.isRegularFile(transitions)) {
            "Transition insertions are planned but transition MIDI is missing: $transitions"
        }

        val fingerprint = fingerprint(root, project, arrangement, analyses, requiredInputs.values.toList(), transitions.takeIf(Files::isRegularFile))
        val reportPath = root.resolve(REPORT_FILE)
        readReport(reportPath)?.takeIf { it.inputFingerprint == fingerprint && it.timelineFrames == timeline.frames(format.sampleRate) }
            ?.takeIf { report -> report.stems.all { stem ->
                val path = root.resolve(stem.path)
                validWav(path, format, timeline.frames(format.sampleRate)) && digest(Files.readAllBytes(path)) == stem.fingerprint
            } && root.resolve(report.dryMix).let { dry -> validWav(dry, format, timeline.frames(format.sampleRate)) && digest(Files.readAllBytes(dry)) == report.dryMixFingerprint } }
            ?.let { return StemRenderResult(it, reused = true) }

        val sourceHashes = project.parts.associate { it.id to digest(Files.readAllBytes(root.resolve(it.file))) }
        val expectedFrames = timeline.frames(format.sampleRate)
        val stems = mutableListOf<StemArtifact>()
        active.forEach { instrument ->
            val assembled = assembleMidi(root, project, instrument, timeline, requiredInputs[instrument], transitions.takeIf(Files::isRegularFile))
            val target = root.resolve("stems/${instrument.wireName}.wav")
            val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.rendering.wav")
            try {
                renderer.render(assembled, instrument, temporary, format, expectedFrames)
                val audio = requireCompatibleStem(temporary, format, expectedFrames, "${instrument.wireName} render")
                atomicReplace(temporary, target)
                stems += StemArtifact(instrument.wireName, "stems/${instrument.wireName}.wav", audio.length.toLong(), digest(Files.readAllBytes(target)))
            } finally {
                Files.deleteIfExists(assembled)
                Files.deleteIfExists(temporary)
            }
        }

        val tracks = stems.map { stem ->
            MixTrack(stem.name, requireCompatibleStem(root.resolve(stem.path), format, expectedFrames, stem.name), gainDb = DEFAULT_GAINS_DB.getValue(stem.name), generated = stem.name != LogicalInstrument.PIANO.wireName)
        }
        val mixed = mixer.mix(tracks, MixSettings(requiredFormat = format, peakCeiling = DRY_PEAK_CEILING))
        require(mixed.buffer.length.toLong() == expectedFrames) { "Dry mix does not cover the complete timeline" }
        val dryMix = root.resolve("mix/dry.wav")
        mixer.writeWav(mixed, dryMix)
        requireCompatibleStem(dryMix, format, expectedFrames, "dry mix")
        require(project.parts.all { part -> digest(Files.readAllBytes(root.resolve(part.file))) == sourceHashes.getValue(part.id) }) { "A source file changed while rendering stems" }

        val report = StemRenderReport(
            inputFingerprint = fingerprint,
            timelineFrames = expectedFrames,
            sampleRate = format.sampleRate,
            channels = format.channels,
            stems = stems,
            dryMix = "mix/dry.wav",
            dryMixFingerprint = digest(Files.readAllBytes(dryMix)),
            predictedPeak = mixed.predictedPeak,
            appliedGain = mixed.appliedGain,
            appliedGainDb = mixed.appliedGainDb,
            sourceHashes = sourceHashes
        )
        writeReport(reportPath, report)
        return StemRenderResult(report, reused = false)
    }

    private fun assembleMidi(root: Path, project: Project, instrument: LogicalInstrument, timeline: Timeline, generated: Path?, transitions: Path?): Path {
        val output = root.resolve("midi/render-input/.${instrument.wireName}-${UUID.randomUUID()}.mid")
        Files.createDirectories(requireNotNull(output.parent))
        val sequence = Sequence(Sequence.PPQ, timeline.ppq)
        val meta = sequence.createTrack()
        timeline.segments.forEach { segment ->
            val analysis = segment.analysis
            analysis.tempoMap.forEach { tempo -> meta.add(MidiEvent(tempoMessage(tempo.bpm), segment.timelineStartTick + tempo.tick)) }
            analysis.timeSignatures.forEach { signature -> meta.add(MidiEvent(signatureMessage(signature), segment.timelineStartTick + signature.tick)) }
        }
        if (instrument == LogicalInstrument.PIANO) {
            timeline.segments.forEach { segment ->
                val part = project.parts.first { it.id == segment.partId }
                val source = MidiSystem.getSequence(root.resolve(requireNotNull(part.midi).clean).toFile())
                require(source.divisionType == Sequence.PPQ && source.resolution == timeline.ppq) { "Clean MIDI for '${part.id}' does not match project PPQ" }
                copySectionEvents(source, sequence, segment)
            }
        } else {
            copyGeneratedEvents(MidiSystem.getSequence(checkNotNull(generated).toFile()), sequence, timeline)
            transitions?.let { copyTransitionEvents(MidiSystem.getSequence(it.toFile()), sequence, instrument) }
        }
        meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), timeline.endTick))
        require(MidiSystem.write(sequence, 1, output.toFile()) > 0) { "Could not assemble ${instrument.wireName} timeline MIDI" }
        return output
    }

    private fun copySectionEvents(source: Sequence, destination: Sequence, segment: TimelineSegment) {
        source.tracks.forEach { sourceTrack ->
            val target = destination.createTrack()
            (0 until sourceTrack.size()).map(sourceTrack::get)
                .filterNot { (it.message as? MetaMessage)?.type == 0x2F }
                .filter { it.tick <= segment.analysis.durationTicks }
                .forEach { event -> target.add(MidiEvent(event.message.copy(), segment.timelineStartTick + event.tick)) }
        }
    }

    private fun copyGeneratedEvents(source: Sequence, destination: Sequence, timeline: Timeline) {
        require(source.divisionType == Sequence.PPQ && source.resolution == timeline.ppq) { "Generated MIDI does not match project PPQ" }
        source.tracks.forEach { sourceTrack ->
            val target = destination.createTrack()
            (0 until sourceTrack.size()).map(sourceTrack::get)
                .filterNot { (it.message as? MetaMessage)?.type == 0x2F }
                .filter { it.tick <= timeline.originalEndTick }
                .forEach { event -> target.add(MidiEvent(event.message.copy(), timeline.map(event.tick, isNoteOff(event.message)))) }
        }
    }

    private fun copyTransitionEvents(source: Sequence, destination: Sequence, instrument: LogicalInstrument) {
        val descriptor = InstrumentRegistryLoader().load().resolve(instrument.wireName)
        source.tracks.drop(1).filter { track -> belongsTo(track, instrument, descriptor) }.forEach { sourceTrack ->
            val target = destination.createTrack()
            (0 until sourceTrack.size()).map(sourceTrack::get)
                .filterNot { (it.message as? MetaMessage)?.type == 0x2F }
                .forEach { event -> target.add(MidiEvent(event.message.copy(), event.tick)) }
        }
    }

    private fun belongsTo(track: javax.sound.midi.Track, instrument: LogicalInstrument, descriptor: ValidatedInstrumentDescriptor): Boolean {
        val messages = (0 until track.size()).map(track::get).mapNotNull { it.message as? ShortMessage }
        return when {
            descriptor.midiProgram != null -> messages.any { it.command == ShortMessage.PROGRAM_CHANGE && it.data1 == descriptor.midiProgram }
            descriptor.midiChannelZeroBased != null -> messages.any { it.channel == descriptor.midiChannelZeroBased && it.command in setOf(ShortMessage.NOTE_ON, ShortMessage.NOTE_OFF) }
            else -> false
        }
    }

    private fun requireCompatibleStem(path: Path, format: RenderFormat, frames: Long, label: String): AudioBuffer {
        val audio = WAVDecoder(NoOpErrorReporter).decode(path)
        require(audio.format.sampleRate == format.sampleRate && audio.format.channels == format.channels && audio.format.bitDepth == 24) {
            "$label has wrong WAV format; expected ${format.sampleRate} Hz, ${format.channels} channels, PCM-24"
        }
        require(audio.length.toLong() == frames) { "$label has ${audio.length} frames; expected $frames" }
        require(audio.samples.all { it.isFinite() }) { "$label contains non-finite samples" }
        return audio
    }

    private fun validWav(path: Path, format: RenderFormat, frames: Long): Boolean = runCatching { requireCompatibleStem(path, format, frames, path.fileName.toString()) }.isSuccess

    private fun fingerprint(root: Path, project: Project, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>, generated: List<Path>, transitions: Path?): String = digest(buildString {
        append(project.version).append('|').append(project.renderFormat).append('|').append(arrangement).append('|')
        project.parts.sortedBy { it.id }.forEach { part -> append(part.id).append(':').append(digest(Files.readAllBytes(root.resolve(part.file)))).append(':').append(digest(Files.readAllBytes(root.resolve(requireNotNull(part.midi).clean)))).append('|') }
        analyses.toSortedMap().forEach { (id, analysis) -> append(id).append(':').append(analysis.durationTicks).append(':').append(analysis.durationSeconds).append(':').append(analysis.tempoMap).append('|') }
        generated.sorted().forEach { append(it.fileName).append(':').append(digest(Files.readAllBytes(it))).append('|') }
        transitions?.let { append("transitions:").append(digest(Files.readAllBytes(it))).append('|') }
        val registry = InstrumentRegistryLoader().libraryRoot.resolve("instruments.json")
        append("registry:").append(digest(Files.readAllBytes(registry))).append('|')
        append("renderer:").append(renderer.javaClass.name).append(':').append(System.getenv("SFZ_RENDERER_PATH").orEmpty()).append(':').append(System.getenv("SFZ_RENDERER_VERSION").orEmpty())
    }.toByteArray(StandardCharsets.UTF_8))

    private fun readReport(path: Path): StemRenderReport? = runCatching { json.decodeFromString(StemRenderReport.serializer(), Files.readString(path)) }.getOrNull()
    private fun writeReport(path: Path, report: StemRenderReport) {
        Files.createDirectories(requireNotNull(path.parent)); val temporary = path.resolveSibling(".${path.fileName}.${UUID.randomUUID()}.tmp")
        try { Files.writeString(temporary, json.encodeToString(report), StandardCharsets.UTF_8); atomicReplace(temporary, path) } finally { Files.deleteIfExists(temporary) }
    }
    private fun atomicReplace(source: Path, target: Path) {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) }
    }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun isNoteOff(message: MidiMessage): Boolean = (message as? ShortMessage)?.let { it.command == ShortMessage.NOTE_OFF || (it.command == ShortMessage.NOTE_ON && it.data2 == 0) } == true
    private fun MidiMessage.copy(): MidiMessage = clone() as MidiMessage
    private fun tempoMessage(bpm: Double): MetaMessage { val micros = (60_000_000.0 / bpm).roundToLong().toInt(); return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }
    private fun signatureMessage(signature: MidiTimeSignature) = MetaMessage(0x58, byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8), 4)

    private companion object {
        const val REPORT_FILE = "stem-render.json"
        const val DRY_PEAK_CEILING = 0.95
        val DEFAULT_GAINS_DB = mapOf("piano" to 0.0, "bass" to -6.0, "drums" to -8.0, "pad" to -10.0, "strings" to -10.0)
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        object NoOpErrorReporter : ai.music.workstation.model.ErrorReporter { override fun report(message: String) = Unit; override fun report(message: String, cause: Throwable) = Unit }
    }
}

private data class TimelineSegment(val partId: String, val analysis: MidiAnalysis, val originalStartTick: Long, val timelineStartTick: Long, val insertedTicksAfter: Long) {
    val originalEndTick get() = originalStartTick + analysis.durationTicks
    val timelineEndTick get() = timelineStartTick + analysis.durationTicks
}

private data class Timeline(val ppq: Int, val segments: List<TimelineSegment>) {
    val originalEndTick get() = segments.last().originalEndTick
    val endTick get() = segments.last().timelineEndTick
    fun map(tick: Long, isNoteOff: Boolean): Long {
        val segment = segments.firstOrNull { tick < it.originalEndTick } ?: segments.last()
        if (tick == segment.originalStartTick && segment != segments.first() && !isNoteOff) return segment.timelineStartTick
        if (tick == segment.originalEndTick && isNoteOff) return segment.timelineEndTick
        return segment.timelineStartTick + (tick - segment.originalStartTick).coerceIn(0, segment.analysis.durationTicks)
    }
    fun frames(sampleRate: Int): Long = segments.sumOf { segment ->
        (segment.analysis.durationSeconds * sampleRate).roundToLong() + if (segment.insertedTicksAfter == 0L) 0L else {
            val incoming = segments.getOrNull(segment.index + 1)?.analysis ?: return@sumOf 0L
            val bpm = incoming.tempoMap.first().bpm
            (segment.insertedTicksAfter.toDouble() / ppq * 60.0 / bpm * sampleRate).roundToLong()
        }
    }
    private val TimelineSegment.index get() = segments.indexOf(this)
    companion object {
        fun create(arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>): Timeline {
            val ppq = analyses.getValue(arrangement.sections.first().partId).ppq
            var original = 0L; var shifted = 0L
            return Timeline(ppq, arrangement.sections.mapIndexed { index, section ->
                val analysis = analyses[section.partId] ?: throw IllegalArgumentException("Missing MIDI analysis for arranged part '${section.partId}'")
                require(analysis.ppq == ppq && analysis.durationTicks > 0 && analysis.durationSeconds > 0.0) { "MIDI analysis for '${section.partId}' has incompatible timing" }
                val inserted = if (section.transitionOut.type == TransitionType.BRIDGE && index < arrangement.sections.lastIndex) {
                    val incoming = analyses.getValue(arrangement.sections[index + 1].partId)
                    val signature = incoming.timeSignatures.first()
                    section.transitionOut.bars.toLong() * (ppq * 4L / signature.denominator) * signature.numerator
                } else 0L
                TimelineSegment(section.partId, analysis, original, shifted, inserted).also { original += analysis.durationTicks; shifted += analysis.durationTicks + inserted }
            })
        }
    }
}

@Serializable data class StemArtifact(val name: String, val path: String, val frames: Long, val fingerprint: String)
@Serializable data class StemRenderReport(
    val version: Int = 1, val inputFingerprint: String, val timelineFrames: Long, val sampleRate: Int, val channels: Int,
    val stems: List<StemArtifact>, val dryMix: String, val dryMixFingerprint: String, val predictedPeak: Float, val appliedGain: Float, val appliedGainDb: Double, val sourceHashes: Map<String, String>
)
data class StemRenderResult(val report: StemRenderReport, val reused: Boolean)

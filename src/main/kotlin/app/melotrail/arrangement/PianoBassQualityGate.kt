package app.melotrail.arrangement

import app.melotrail.audio.WAVDecoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import kotlin.math.roundToLong

/**
 * The deliberately narrow Task 012 path. It consumes only reviewed MIDI-first
 * planning data, renders source piano plus generated bass, and stops at a dry
 * PCM-24 WAV. It never invokes transitions or the post-production pipeline.
 */
class PianoBassQualityGate(
    private val renderer: InstrumentRenderer,
    private val analyzer: MidiPartAnalyzer = MidiPartAnalyzer(),
    private val bassGenerator: BassMidiGenerationAdapter,
    private val mixer: DeterministicStemMixer = DeterministicStemMixer()
) {
    suspend fun run(projectRoot: Path): PianoBassQualityGateResult {
        val root = projectRoot.toAbsolutePath().normalize()
        val progress = mutableListOf<String>()
        fun stage(number: Int, text: String) { progress += "[$number/8] $text" }

        var project = ProjectStore.read(root)
        project.requireValid(root)
        require(project.version >= Project.MIDI_FIRST_VERSION) { "Piano+bass quality gate requires a MIDI-first project" }
        val structure = project.structure.mapIndexed { index, partId -> SectionInstance(index, partId) }
        require(structure.size == 5 && structure[0].partId == structure[1].partId && structure[0].partId == structure[4].partId && structure[2].partId == structure[3].partId && structure[0].partId != structure[2].partId) {
            "Piano+bass quality gate requires the audible repeated-section structure A A B B A using two distinct project parts"
        }
        stage(1, "Validated MIDI-first project and ${structure.size} section instances")

        val sourceHashes = sourceHashes(root, project)
        var selectedMidi = project.requireSelectedMidi(root).associateBy(SelectedMidiArtifact::partId)
        selectedMidi.values.forEach { validateMidi(it.path, "Selected MIDI") }
        stage(2, "Prepared selected MIDI for ${selectedMidi.size} part(s)")

        project.parts.forEach { part ->
            val selected = selectedMidi.getValue(part.id).path
            val analysisPath = part.analysis?.takeIf { it.kind == AnalysisKind.MIDI }?.let { root.resolve(it.file) }
            if (analysisPath == null || !Files.isRegularFile(analysisPath) || Files.getLastModifiedTime(analysisPath) < Files.getLastModifiedTime(selected)) {
                MidiAnalysisStore.write(root, project, part.id, analyzer.analyze(selected, part.id))
                project = ProjectStore.read(root)
                selectedMidi = project.requireSelectedMidi(root).associateBy(SelectedMidiArtifact::partId)
            }
        }
        val analyses = loadMidiAnalyses(root, project)
        require(analyses.values.map { it.ppq }.distinct().size == 1) { "Piano+bass quality gate requires all clean MIDI parts to use the same PPQ" }
        stage(3, "Analyzed or reused current MIDI analyses")

        val planningInput = SongPlanningInput(
            projectName = project.name,
            projectVersion = project.version,
            analyses = structure.map { it.partId }.distinct().associateWith(analyses::getValue),
            structure = structure,
            allowedInstruments = listOf("piano", "bass"),
            style = "piano and supporting bass quality gate"
        )
        val songPlanPath = root.resolve(SongPlanStore.FILE_NAME)
        val songPlan = readOrCreateSongPlan(root, planningInput, songPlanPath, selectedMidi.values.map(SelectedMidiArtifact::path))
        val variationsPath = root.resolve(SectionVariationStore.FILE_NAME)
        val variations = readOrCreateVariations(root, planningInput, songPlan, variationsPath, songPlanPath)
        stage(4, "Created or reused piano+bass song plan and repeated-section variations")

        val detailedInput = DetailedArrangementInput(planningInput, songPlan, variations)
        val arrangementPath = root.resolve(DetailedArrangementStore.APPROVED_FILE)
        val arrangement = readOrCreateArrangement(root, detailedInput, arrangementPath, variationsPath)
        requirePianoAndBassOnly(arrangement)
        stage(5, "Created or reused approved piano+bass arrangement")

        val inputFingerprint = fingerprint(project, selectedMidi, analyses, arrangementPath)
        val reportPath = root.resolve(REPORT_FILE)
        val existing = readReport(reportPath)
        val timelineFrames = arrangement.sections.sumOf { section ->
            (analyses.getValue(section.partId).durationSeconds * project.renderFormat!!.sampleRate).roundToLong()
        }
        require(timelineFrames > 0) { "Piano+bass arrangement timeline has no frames" }

        val expectedArtifacts = mapOf(
            "pianoMidi" to "midi/generated/piano.mid",
            "bassMidi" to "midi/generated/bass.mid",
            "pianoStem" to "stems/piano.wav",
            "bassStem" to "stems/bass.wav",
            "dryMix" to "mix/dry.wav"
        )
        val reusable = existing?.takeIf { it.inputFingerprint == inputFingerprint && it.timelineFrames == timelineFrames }?.let {
            artifactsValid(root, expectedArtifacts, project.renderFormat!!, timelineFrames)
        } == true

        val bass: GeneratedBassMidi
        val pianoMidi: Path
        val pianoResult: RenderResult
        val bassResult: RenderResult
        if (reusable) {
            pianoMidi = root.resolve(expectedArtifacts.getValue("pianoMidi"))
            bass = GeneratedBassMidi(root.resolve(expectedArtifacts.getValue("bassMidi")), analyses.values.first().ppq, emptyList(), emptyList())
            pianoResult = renderedFrom(root.resolve(expectedArtifacts.getValue("pianoStem")), "reused")
            bassResult = renderedFrom(root.resolve(expectedArtifacts.getValue("bassStem")), "reused")
            stage(6, "Reused current full-timeline piano and bass MIDI")
            stage(7, "Reused validated piano and bass PCM-24 stems")
            stage(8, "Reused validated dry mix")
        } else {
            pianoMidi = writePianoTimeline(root, project, selectedMidi, arrangement, analyses)
            bass = bassGenerator.generate(root, project, legacyBassArrangement(arrangement), analyses)
            validateMidi(pianoMidi, "Timeline piano MIDI")
            validateMidi(bass.path, "Generated bass MIDI")
            stage(6, "Generated full-timeline piano and bass MIDI")

            val format = project.renderFormat!!
            pianoResult = renderer.render(pianoMidi, LogicalInstrument.PIANO, root.resolve(expectedArtifacts.getValue("pianoStem")), format, timelineFrames)
            bassResult = renderer.render(bass.path, LogicalInstrument.BASS, root.resolve(expectedArtifacts.getValue("bassStem")), format, timelineFrames)
            requireRenderedStem(pianoResult.output, format, timelineFrames, "Piano render")
            requireRenderedStem(bassResult.output, format, timelineFrames, "Bass render")
            stage(7, "Rendered timeline-aligned piano and bass PCM-24 stems")

            val piano = WAVDecoder(NoOpErrorReporter).decode(pianoResult.output)
            val bassAudio = WAVDecoder(NoOpErrorReporter).decode(bassResult.output)
            val mix = mixer.mix(
                listOf(MixTrack("piano", piano), MixTrack("bass", bassAudio, gainDb = -6.0, generated = true)),
                MixSettings(targetSampleRate = format.sampleRate, peakCeiling = MIX_PEAK_CEILING.toDouble())
            )
            require(mix.buffer.length.toLong() == timelineFrames) { "Dry mix frame count does not match arrangement timeline" }
            mixer.writeWav(mix, root.resolve(expectedArtifacts.getValue("dryMix")))
            stage(8, "Created dry lossless mix with ${MIX_PEAK_CEILING} peak ceiling")
        }

        val dryMix = root.resolve(expectedArtifacts.getValue("dryMix"))
        val mixBuffer = WAVDecoder(NoOpErrorReporter).decode(dryMix)
        require(mixBuffer.length.toLong() == timelineFrames) { "Dry mix frame count does not match arrangement timeline" }
        require(mixBuffer.samples.all { it.isFinite() }) { "Dry mix contains non-finite samples" }
        val mixPeak = mixBuffer.samples.maxOf { kotlin.math.abs(it) }
        require(mixPeak <= MIX_PEAK_CEILING + 0.0001f) { "Dry mix peak $mixPeak exceeds $MIX_PEAK_CEILING ceiling" }
        verifySourceHashes(root, project, sourceHashes)

        val report = PianoBassQualityGateReport(
            inputFingerprint = inputFingerprint,
            sourceHashes = sourceHashes,
            timelineFrames = timelineFrames,
            sampleRate = project.renderFormat!!.sampleRate,
            channels = project.renderFormat.channels,
            peakCeiling = MIX_PEAK_CEILING,
            dryMixPeak = mixPeak,
            bassEventCount = if (reusable) existing?.bassEventCount ?: 0 else bass.notes.size,
            artifacts = expectedArtifacts,
            pianoRenderer = pianoResult.rendererIdentity,
            bassRenderer = bassResult.rendererIdentity
        )
        writeReport(reportPath, report)
        return PianoBassQualityGateResult(progress, report, reusable)
    }

    private fun readOrCreateSongPlan(root: Path, input: SongPlanningInput, path: Path, cleanMidi: List<Path>): SongPlan =
        runCatching { SongPlanStore.read(root, input) }.getOrNull()
            ?.takeIf { Files.isRegularFile(path) && cleanMidi.all { Files.getLastModifiedTime(path) >= Files.getLastModifiedTime(it) } }
            ?: DeterministicGlobalSongPlanner().plan(input).also { SongPlanStore.write(root, input, it) }

    private fun readOrCreateVariations(root: Path, input: SongPlanningInput, plan: SongPlan, path: Path, planPath: Path): SectionVariationPlan =
        runCatching { SectionVariationStore.read(root, input, plan) }.getOrNull()
            ?.takeIf { Files.isRegularFile(path) && Files.getLastModifiedTime(path) >= Files.getLastModifiedTime(planPath) }
            ?: DeterministicSectionVariationPlanner.plan(input, plan).also { SectionVariationStore.write(root, input, plan, it) }

    private fun readOrCreateArrangement(root: Path, input: DetailedArrangementInput, path: Path, variationsPath: Path): DetailedArrangement =
        runCatching {
            json.decodeFromString(DetailedArrangement.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also { it.requireValid(input) }
        }.getOrNull()
            ?.takeIf { Files.getLastModifiedTime(path) >= Files.getLastModifiedTime(variationsPath) }
            ?: DeterministicDetailedArrangementPlanner().plan(input).also { DetailedArrangementStore.writeApproved(root, input, it) }

    private fun requirePianoAndBassOnly(arrangement: DetailedArrangement) {
        arrangement.sections.forEach { section ->
            require(section.instruments.filter { it.mode == InstrumentMode.GENERATED }.all { it is BassInstrumentPlan }) {
                "Quality-gate arrangement section ${section.index + 1} contains a generated instrument other than bass"
            }
            require(section.instruments.filterIsInstance<PianoSourcePlan>().size == 1) {
                "Quality-gate arrangement section ${section.index + 1} must retain exactly one source piano"
            }
        }
        require(arrangement.sections.any { it.instruments.any { instrument -> instrument is BassInstrumentPlan } }) {
            "Quality-gate arrangement must include generated bass in at least one section"
        }
    }

    private fun legacyBassArrangement(arrangement: DetailedArrangement): Arrangement = Arrangement(
        version = Arrangement.LATEST_VERSION,
        sections = arrangement.sections.map { section ->
            ArrangementSection(section.index, section.partId, section.instruments.mapNotNull { instrument ->
                when (instrument) {
                    is PianoSourcePlan -> InstrumentPlan("piano", InstrumentMode.SOURCE)
                    is BassInstrumentPlan -> InstrumentPlan("bass", InstrumentMode.GENERATED, instrument.role.name.lowercase(), instrument.density)
                    else -> null
                }
            })
        }
    )

    private fun writePianoTimeline(root: Path, project: Project, selectedMidi: Map<String, SelectedMidiArtifact>, arrangement: DetailedArrangement, analyses: Map<String, MidiAnalysis>): Path {
        val output = root.resolve("midi/generated/piano.mid")
        val temporary = output.resolveSibling(".${output.fileName}.tmp")
        Files.createDirectories(requireNotNull(output.parent))
        val ppq = analyses.values.first().ppq
        val sequence = Sequence(Sequence.PPQ, ppq)
        val meta = sequence.createTrack()
        var startTick = 0L
        arrangement.sections.forEach { section ->
            val part = project.parts.first { it.id == section.partId }
            val source = MidiSystem.getSequence(selectedMidi.getValue(part.id).path.toFile())
            require(source.divisionType == Sequence.PPQ && source.resolution == ppq) { "Selected MIDI for '${part.id}' does not match project PPQ" }
            val track = sequence.createTrack()
            source.tracks.forEach { sourceTrack ->
                (0 until sourceTrack.size()).map { sourceTrack[it] }.filterNot { it.message is MetaMessage && (it.message as MetaMessage).type == 0x2F }
                    .forEach { event -> track.add(MidiEvent(event.message.copy(), startTick + event.tick)) }
            }
            val analysis = analyses.getValue(section.partId)
            analysis.tempoMap.forEach { meta.add(MidiEvent(tempoMessage(it.bpm), startTick + it.tick)) }
            analysis.timeSignatures.forEach { meta.add(MidiEvent(signatureMessage(it), startTick + it.tick)) }
            startTick = Math.addExact(startTick, analysis.durationTicks)
        }
        meta.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), startTick))
        try {
            require(MidiSystem.write(sequence, 1, temporary.toFile()) > 0) { "Could not write timeline piano MIDI" }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
        return output
    }

    private fun loadMidiAnalyses(root: Path, project: Project): Map<String, MidiAnalysis> = project.parts.associate { part ->
        val reference = requireNotNull(part.analysis) { "Missing MIDI analysis for '${part.id}'" }
        require(reference.kind == AnalysisKind.MIDI) { "Quality gate requires MIDI analysis for '${part.id}'" }
        part.id to json.decodeFromString(MidiAnalysis.serializer(), Files.readString(root.resolve(reference.file), StandardCharsets.UTF_8))
    }

    private fun artifactsValid(root: Path, artifacts: Map<String, String>, format: RenderFormat, frames: Long): Boolean = runCatching {
        validateMidi(root.resolve(artifacts.getValue("pianoMidi")), "Timeline piano MIDI")
        validateMidi(root.resolve(artifacts.getValue("bassMidi")), "Generated bass MIDI")
        listOf("pianoStem", "bassStem", "dryMix").forEach { key ->
            val audio = WAVDecoder(NoOpErrorReporter).decode(root.resolve(artifacts.getValue(key)))
            require(audio.format.sampleRate == format.sampleRate && audio.format.channels == format.channels && audio.format.bitDepth == 24 && audio.length.toLong() == frames)
            require(audio.samples.all { it.isFinite() })
            if (key == "dryMix") require(audio.samples.maxOf { kotlin.math.abs(it) } <= MIX_PEAK_CEILING + 0.0001f)
        }
    }.isSuccess

    private fun requireRenderedStem(path: Path, format: RenderFormat, frames: Long, stage: String) {
        val audio = WAVDecoder(NoOpErrorReporter).decode(path)
        require(audio.format.sampleRate == format.sampleRate && audio.format.channels == format.channels && audio.format.bitDepth == 24) {
            "$stage wrote the wrong WAV format; expected ${format.sampleRate} Hz, ${format.channels} channels, PCM-24"
        }
        require(audio.length.toLong() == frames) { "$stage frame count does not match arrangement timeline" }
        require(audio.samples.all { it.isFinite() }) { "$stage contains non-finite samples" }
    }

    private fun renderedFrom(path: Path, identity: String): RenderResult {
        val audio = WAVDecoder(NoOpErrorReporter).decode(path)
        return RenderResult(path, audio.format.sampleRate, audio.format.channels, audio.format.bitDepth, audio.length.toLong(), audio.length.toDouble() / audio.format.sampleRate, audio.samples.maxOf { kotlin.math.abs(it).toDouble() }, identity, "cached", "", "")
    }

    private fun fingerprint(project: Project, selectedMidi: Map<String, SelectedMidiArtifact>, analyses: Map<String, MidiAnalysis>, arrangement: Path): String = digest(
        buildString {
            append(project.version).append('|').append(project.renderFormat).append('|')
            project.parts.sortedBy { it.id }.forEach { part ->
                val selected = selectedMidi.getValue(part.id)
                append(part.id).append(':').append(selected.kind).append(':').append(selected.sha256).append('|')
            }
            append(digest(Files.readAllBytes(arrangement))).append('|')
            analyses.toSortedMap().forEach { (id, analysis) -> append(id).append(':').append(analysis.durationTicks).append(':').append(analysis.durationSeconds).append('|') }
        }.toByteArray(StandardCharsets.UTF_8)
    )

    private fun sourceHashes(root: Path, project: Project): Map<String, String> = project.parts.associate { it.id to digest(Files.readAllBytes(root.resolve(it.file))) }
    private fun verifySourceHashes(root: Path, project: Project, before: Map<String, String>) = require(sourceHashes(root, project) == before) { "Source hash changed during quality gate" }
    private fun validateMidi(path: Path, stage: String) { require(Files.isRegularFile(path) && Files.size(path) >= 14 && MidiSystem.getSequence(path.toFile()).divisionType == Sequence.PPQ) { "$stage is missing or invalid: $path" } }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun readReport(path: Path): PianoBassQualityGateReport? = runCatching { json.decodeFromString(PianoBassQualityGateReport.serializer(), Files.readString(path)) }.getOrNull()
    private fun writeReport(path: Path, report: PianoBassQualityGateReport) { Files.writeString(path, json.encodeToString(report), StandardCharsets.UTF_8) }
    private fun tempoMessage(bpm: Double): MetaMessage { val micros = (60_000_000.0 / bpm).roundToLong().toInt(); return MetaMessage(0x51, byteArrayOf((micros shr 16).toByte(), (micros shr 8).toByte(), micros.toByte()), 3) }
    private fun signatureMessage(signature: MidiTimeSignature) = MetaMessage(0x58, byteArrayOf(signature.numerator.toByte(), Integer.numberOfTrailingZeros(signature.denominator).toByte(), 24, 8), 4)
    private fun MidiMessage.copy(): MidiMessage = clone() as MidiMessage

    private companion object {
        const val REPORT_FILE = "quality-gate.json"
        const val MIX_PEAK_CEILING = 0.95f
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        object NoOpErrorReporter : app.melotrail.model.ErrorReporter {
            override fun report(message: String) = Unit
            override fun report(message: String, cause: Throwable) = Unit
        }
    }
}

@Serializable
data class PianoBassQualityGateReport(
    val version: Int = 1,
    val inputFingerprint: String,
    val sourceHashes: Map<String, String>,
    val timelineFrames: Long,
    val sampleRate: Int,
    val channels: Int,
    val peakCeiling: Float,
    val dryMixPeak: Float,
    val bassEventCount: Int,
    val artifacts: Map<String, String>,
    val pianoRenderer: String,
    val bassRenderer: String
)

data class PianoBassQualityGateResult(
    val progress: List<String>,
    val report: PianoBassQualityGateReport,
    val reusedFinalArtifacts: Boolean
)

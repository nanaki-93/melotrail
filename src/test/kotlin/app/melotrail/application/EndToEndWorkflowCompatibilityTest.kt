package app.melotrail.application

import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MixedStem
import app.melotrail.arrangement.Part
import app.melotrail.arrangement.Project
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.RenderFormat
import app.melotrail.arrangement.RenderResult
import app.melotrail.preparation.AudioCleanupBoundary
import app.melotrail.preparation.AudioCleanupRequest
import app.melotrail.preparation.AudioCleanupResult
import app.melotrail.preparation.AudioInspectionMeasurements
import app.melotrail.preparation.CleanupMetrics
import app.melotrail.preparation.DetectedAudioFormat
import app.melotrail.preparation.DetectedInput
import app.melotrail.preparation.EvidenceLevel
import app.melotrail.preparation.InputCleanupApplicationService
import app.melotrail.preparation.InputCleanupMode
import app.melotrail.preparation.InputContainer
import app.melotrail.preparation.InputInspectionBoundary
import app.melotrail.preparation.InputInspectionReport
import app.melotrail.preparation.InputInspectionResult
import app.melotrail.preparation.SignalIndicator
import app.melotrail.preparation.SilenceEvidence
import app.melotrail.preparation.TranscriptionBoundary
import app.melotrail.preparation.TranscriptionBoundaryResult
import app.melotrail.preparation.TranscriptionEngineMetadata
import app.melotrail.preparation.TranscriptionQualityGateService
import app.melotrail.preparation.TranscriptionInputArtifact
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Task 054's offline compatibility proof: canonical artifacts, not UI strings, are the oracle. */
class EndToEndWorkflowCompatibilityTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `direct MIDI clean WAV noisy WAV and MP3 reach validated releases without source mutation`() = runBlocking {
        val cases = listOf(
            Fixture("direct-midi", "mid", noisy = false),
            Fixture("clean-wav", "wav", noisy = false),
            Fixture("noisy-clipped-wav", "wav", noisy = true),
            Fixture("mp3", "mp3", noisy = false)
        )

        cases.forEach { fixture ->
            val source = fixtureSource(fixture)
            val sourceHash = hash(source)
            val root = tempDir.resolve("project/my-song-${fixture.name}")
            val services = services()
            services.projects.create(CreateProjectRequest(root, name = "my-song"))
            services.projects.importPart(ImportPartRequest(root, "A", source, transcribe = fixture.extension != "mid"))
            val inspected = services.projects.inspectPart(InspectPartRequest(root, "A"))
            assertTrue(inspected.parts.single().preparation.inspected)

            if (fixture.noisy) {
                services.preparation.applyCleanup(root, "A", InputCleanupMode.SAFE_CLEANUP, confirmedSafeCleanup = true)
                assertTrue(Files.isRegularFile(root.resolve("prepared/A/clean.wav")))
                services.preparation.transcribe(root, "A", TranscriptionInputArtifact.CLEAN_WAV)
                services.projects.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
            } else if (fixture.extension != "mid") {
                services.preparation.transcribe(root, "A", TranscriptionInputArtifact.SOURCE)
                services.projects.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
            } else {
                services.projects.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions()))
            }

            services.projects.analyzePart(AnalyzePartRequest(root, "A"))
            services.projects.saveStructure(SaveStructureRequest(root, listOf("A", "A")))
            DefaultCohesionApplicationService().generate(GenerateCohesionRequest(root))
            val arrangement = services.arrangements.generate(GenerateArrangementRequest(root, instruments = listOf("piano")))
            assertTrue(arrangement.approved)
            val build = services.build.build(BuildSongRequest(root))
            val preview = services.preview.resolve(PreviewRequest(root, "A"))

            assertTrue(Files.isRegularFile(build.master))
            assertTrue(Files.isRegularFile(root.resolve("output/release.json")))
            assertTrue(Files.readString(root.resolve("output/release.json")).contains("\"pcmBitDepth\": 24"))
            assertTrue(preview is PreviewResult.Failed, "fixture monitor artifacts are deliberately malformed and must not report preview success: $preview")
            assertEquals(sourceHash, hash(source), "fixture source must remain immutable")
            assertEquals(sourceHash, hash(root.resolve("source/A.${fixture.extension}")))
            assertFalse(Files.readString(root.resolve("prepared/A/report.json")).contains(source.toAbsolutePath().toString()))
        }
    }

    @Test
    fun `stale reports and failed preparation are recoverable and never claim success`() = runBlocking {
        val source = fixtureSource(Fixture("stale", "wav", noisy = false))
        val root = tempDir.resolve("project/my-song-stale")
        val services = services(failCleanup = true)
        services.projects.create(CreateProjectRequest(root))

        services.projects.importPart(ImportPartRequest(root, "A", source, transcribe = true))
        val failure = runCatching { services.projects.retryMidiCleanup(RetryMidiCleanupRequest(root, "A", app.melotrail.arrangement.MidiCleanupOptions())) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("cleanup failure"))
        assertTrue(Files.isRegularFile(root.resolve("source/A.wav")))
        assertFalse(Files.exists(root.resolve("midi/clean/A.mid")))
        assertEquals(listOf("A"), services.projects.open(root).parts.map { it.id })

        val current = services(failCleanup = false)
        val currentRoot = tempDir.resolve("project/my-song-stale-report")
        current.projects.create(CreateProjectRequest(currentRoot))
        current.projects.importPart(ImportPartRequest(currentRoot, "A", source, transcribe = true))
        current.projects.inspectPart(InspectPartRequest(currentRoot, "A"))
        Files.writeString(currentRoot.resolve("source/A.wav"), "changed after inspection")
        assertEquals(AudioPreparationAvailability.STALE, current.preparation.load(currentRoot, "A").availability)
    }

    @Test
    fun `legacy v1 and pre-provenance v2 projects remain readable beside approved v3 arrangements`() = runBlocking {
        val v1 = tempDir.resolve("compat/v1")
        writeMidi(v1.resolve("parts/A.mid"))
        ProjectStore.write(v1, Project(version = 1, name = "v1", parts = listOf(Part("A", "parts/A.mid")), structure = listOf("A")))
        val services = services()
        assertEquals(1, services.projects.open(v1).version)

        val v2 = tempDir.resolve("compat/v2")
        writeMidi(v2.resolve("source/A.mid")); writeMidi(v2.resolve("midi/clean/A.mid"))
        ProjectStore.write(v2, Project(version = 2, name = "v2", parts = listOf(Part("A", "source/A.mid", midi = app.melotrail.arrangement.MidiReferences(clean = "midi/clean/A.mid"))), structure = listOf("A"), renderFormat = RenderFormat()))
        val snapshot = services.projects.open(v2)
        assertEquals(2, snapshot.version)
        assertEquals(MidiQualityStatus.LEGACY_UNKNOWN, snapshot.parts.single().preparation.midiQuality.status)

        val current = tempDir.resolve("compat/current")
        val source = fixtureSource(Fixture("approved-v3", "mid", false))
        services.projects.create(CreateProjectRequest(current)); services.projects.importPart(ImportPartRequest(current, "A", source)); services.projects.retryMidiCleanup(RetryMidiCleanupRequest(current, "A", app.melotrail.arrangement.MidiCleanupOptions())); services.projects.analyzePart(AnalyzePartRequest(current, "A")); services.projects.saveStructure(SaveStructureRequest(current, listOf("A")))
        DefaultCohesionApplicationService().generate(GenerateCohesionRequest(current))
        assertTrue(services.arrangements.generate(GenerateArrangementRequest(current, instruments = listOf("piano"))).approved)
        assertTrue(Files.readString(current.resolve("arrangement.json")).contains("\"version\": 3"))
    }

    private fun services(failCleanup: Boolean = false): Services {
        val projects = DefaultProjectApplicationService(
            midiPreparation = FakeMidiPreparation(failCleanup),
            legacyPartAnalysis = LegacyPartAnalysisService { error("legacy analysis is not used by this v2 fixture") },
            inputInspection = InputInspectionBoundary { request -> InputInspectionResult.Inspected(report(request)) }
        )
        val cleanup = InputCleanupApplicationService(FakeAudioCleanup())
        val preparation = DefaultAudioPreparationApplicationService(
            projects, cleanup, TranscriptionQualityGateService(FakeGateTranscriber())
        )
        val library = fixtureLibrary()
        val arrangements = DefaultArrangementApplicationService(libraryRoot = library)
        val renderer = FakeRenderer()
        return Services(
            projects, preparation, arrangements,
            DefaultBuildApplicationService(arrangements, DefaultMixApplicationService(), renderer, FakeBuildWorker()),
            DefaultPartPreviewApplicationService(renderer, FakePreviewMp3Decoder())
        )
    }

    private fun report(request: app.melotrail.preparation.InputInspectionRequest): InputInspectionReport {
        val extension = request.source.relativePath.substringAfterLast('.')
        if (extension == "mid") return InputInspectionReport(
            partId = request.partId, source = request.source, detectedInput = DetectedInput(InputContainer.MIDI, "SMF_1", "mid"), durationSeconds = 1.0
        )
        val noisy = request.source.sha256 in noisySourceHashes
        return InputInspectionReport(
            partId = request.partId, source = request.source,
            detectedInput = DetectedInput(if (extension == "mp3") InputContainer.MPEG_AUDIO else InputContainer.RIFF_WAVE, if (extension == "mp3") "MPEG" else "PCM", extension),
            durationSeconds = 1.0, audioFormat = DetectedAudioFormat(1_000, 1, 24),
            measurements = AudioInspectionMeasurements(
                peak = if (noisy) 1.0 else 0.2, rms = 0.1, dcOffset = if (noisy) 0.02 else 0.0,
                clippedRunCount = if (noisy) 2 else 0, clippedFrameCount = if (noisy) 4 else 0,
                silence = SilenceEvidence(0, 0), hum = SignalIndicator(EvidenceLevel.NONE, 0.0), noise = SignalIndicator(if (noisy) EvidenceLevel.HIGH else EvidenceLevel.NONE, if (noisy) 0.9 else 0.0)
            )
        )
    }

    private fun fixtureSource(fixture: Fixture): Path = tempDir.resolve("fixtures/${fixture.name}.${fixture.extension}").also { path ->
        when (fixture.extension) {
            "mid" -> writeMidi(path)
            "wav" -> writeWav(path)
            "mp3" -> { Files.createDirectories(path.parent); Files.write(path, byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0, 0, 0, 0, 0)) }
        }
        if (fixture.noisy) noisySourceHashes += hash(path)
    }

    private class FakeMidiPreparation(private val failCleanup: Boolean) : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path) = writeMidi(output)
        override suspend fun clean(input: Path, output: Path) { if (failCleanup) error("fake cleanup failure"); Files.copy(input, output) }
    }
    private class FakeGateTranscriber : TranscriptionBoundary {
        override suspend fun transcribe(input: Path, output: Path): TranscriptionBoundaryResult {
            writeMidi(output); return TranscriptionBoundaryResult.Completed(TranscriptionEngineMetadata("fake", "1"))
        }
    }
    private class FakeAudioCleanup : AudioCleanupBoundary {
        override suspend fun cleanup(request: AudioCleanupRequest): AudioCleanupResult {
            writeWav(request.output)
            val metrics = CleanupMetrics(0.2, 0.1, 0.0, 0, 0, 0.0, 0.0, 0.0)
            return AudioCleanupResult(1_000, 1, 1_000, metrics, metrics, request.operations.map { it.type }, emptyList(), emptyList(), mapOf("fake" to "1"))
        }
    }
    private class FakeRenderer : InstrumentRenderer {
        override suspend fun render(midi: Path, instrument: LogicalInstrument, output: Path, format: RenderFormat, expectedFrames: Long): RenderResult {
            val audio = app.melotrail.audio.AudioBuffer(FloatArray((expectedFrames * format.channels).toInt()) { 0.2f }, app.melotrail.audio.AudioFormat(format.sampleRate, format.channels, 24, false, false, "WAV"), expectedFrames.toDouble() / format.sampleRate)
            DeterministicStemMixer().writeWav(MixedStem(audio, listOf(instrument.wireName)), output)
            return RenderResult(output, format.sampleRate, format.channels, 24, expectedFrames, audio.duration, 0.2, "fake", "1", "", "")
        }
    }
    private class FakeBuildWorker : BuildAudioWorker {
        override suspend fun healthCheck() = true
        override suspend fun repair(input: Path, output: Path) { Files.copy(input, output) }
        override suspend fun master(input: Path, output: Path) { Files.copy(input, output) }
        override suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int) = false
    }
    private class FakePreviewMp3Decoder : PreviewMp3Decoder {
        override val configurationFingerprint = "fake-v1"
        override suspend fun decode(source: Path, output: Path) = writeWav(output)
    }
    private data class Fixture(val name: String, val extension: String, val noisy: Boolean)
    private data class Services(val projects: ProjectApplicationService, val preparation: AudioPreparationApplicationService, val arrangements: ArrangementApplicationService, val build: BuildApplicationService, val preview: PartPreviewApplicationService)
    private val noisySourceHashes = mutableSetOf<String>()

    private fun fixtureLibrary(): Path {
        val root = tempDir.resolve("fixture-library")
        if (Files.exists(root.resolve("instruments.json"))) return root
        val instruments = listOf("piano" to 60, "bass" to 48, "pad" to 60, "strings" to 60)
        instruments.forEach { (name, key) ->
            writeSample(root.resolve("$name/samples/$name.wav"))
            Files.createDirectories(root.resolve(name))
            Files.writeString(root.resolve("$name/$name.sfz"), "<region> sample=samples/$name.wav key=$key")
        }
        listOf("kick" to 36, "snare" to 38, "clap" to 39, "hat_closed" to 42, "hat_open" to 46).forEach { (name, _) -> writeSample(root.resolve("drums/samples/$name.wav")) }
        Files.createDirectories(root.resolve("drums"))
        Files.writeString(root.resolve("drums/drums.sfz"), listOf("kick" to 36, "snare" to 38, "clap" to 39, "hat_closed" to 42, "hat_open" to 46).joinToString("\n") { (name, key) -> "<region> sample=samples/$name.wav key=$key" })
        Files.writeString(root.resolve("LICENSES.json"), """{"version":1,"libraries":{"fixture":{"displayName":"Fixture","source":"local","provenance":"generated-original","license":"fixture","commercialUse":true,"attributionRequired":false,"redistribution":"allowed"}}}""")
        Files.writeString(root.resolve("instruments.json"), """{"version":1,"workingSampleRate":44100,"midiChannelConvention":"one-based","instruments":{"piano":{"engine":"sfz","path":"piano/piano.sfz","licenseId":"fixture","midiProgram":0},"bass":{"engine":"sfz","path":"bass/bass.sfz","licenseId":"fixture","midiProgram":32},"drums":{"engine":"sfz","path":"drums/drums.sfz","licenseId":"fixture","midiChannel":10,"noteMap":{"kick":36,"snare":38,"clap":39,"closedHat":42,"openHat":46}},"pad":{"engine":"sfz","path":"pad/pad.sfz","licenseId":"fixture","midiProgram":89},"strings":{"engine":"sfz","path":"strings/strings.sfz","licenseId":"fixture","midiProgram":48}}}""")
        return root
    }

    private companion object {
        fun writeMidi(path: Path) {
            Files.createDirectories(path.parent)
            val sequence = Sequence(Sequence.PPQ, 480); val track = sequence.createTrack()
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0)); track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 480))
            MidiSystem.write(sequence, 1, path.toFile())
        }
        fun writeWav(path: Path) {
            Files.createDirectories(path.parent); val frames = 1_000; val data = ByteArray(frames * 3)
            val bytes = ByteBuffer.allocate(46 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()); bytes.putInt(40 + data.size); bytes.put("WAVEfmt ".toByteArray()); bytes.putInt(18); bytes.putShort(1); bytes.putShort(1); bytes.putInt(1_000); bytes.putInt(3_000); bytes.putShort(3); bytes.putShort(24); bytes.putShort(0); bytes.put("data".toByteArray()); bytes.putInt(data.size); bytes.put(data)
            Files.write(path, bytes.array())
        }
        fun writeSample(path: Path) {
            Files.createDirectories(path.parent); val data = byteArrayOf(0, 0)
            val bytes = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()); bytes.putInt(36 + data.size); bytes.put("WAVEfmt ".toByteArray()); bytes.putInt(16); bytes.putShort(1); bytes.putShort(1); bytes.putInt(44_100); bytes.putInt(88_200); bytes.putShort(2); bytes.putShort(16); bytes.put("data".toByteArray()); bytes.putInt(data.size); bytes.put(data)
            Files.write(path, bytes.array())
        }
        fun hash(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    }
}

package app.melotrail.application

import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.MixedStem
import app.melotrail.audio.WAVDecoder
import app.melotrail.dsp.DSPChain
import app.melotrail.dsp.LOFIPresets
import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

data class BuildSongRequest(
    val root: Path,
    val enableLoFi: Boolean = false,
    val enableMp3: Boolean = false,
    val mp3BitrateKbps: Int = 320
)

data class BuildResult(
    val root: Path,
    val dryMix: Path,
    val loFiMix: Path?,
    val master: Path,
    val mp3: Path?,
    val reusedStems: Boolean
)

/** Worker-only boundary. The build service owns all project and artifact orchestration. */
interface BuildAudioWorker {
    suspend fun healthCheck(): Boolean
    suspend fun repair(input: Path, output: Path)
    suspend fun master(input: Path, output: Path)
    suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int): Boolean
}

interface BuildApplicationService {
    suspend fun build(request: BuildSongRequest, progress: ProgressSink = ProgressSink.None): BuildResult
}

/**
 * The desktop/CLI-neutral v3 build pipeline. It deliberately has no knowledge of
 * Compose, command-line arguments, or HTTP; adapters provide only the renderer
 * and worker boundary.
 */
class DefaultBuildApplicationService(
    private val arrangementService: ArrangementApplicationService,
    private val mixService: MixApplicationService,
    private val renderer: InstrumentRenderer,
    private val worker: BuildAudioWorker
) : BuildApplicationService {
    override suspend fun build(request: BuildSongRequest, progress: ProgressSink): BuildResult {
        require(request.mp3BitrateKbps in MP3_BITRATES) { "MP3 bitrate must be one of ${MP3_BITRATES.sorted().joinToString()} kbps" }
        val root = request.root.toAbsolutePath().normalize()
        val lock = locks.computeIfAbsent(root) { Mutex() }
        if (!lock.tryLock()) throw ApplicationServiceException(ApplicationErrorCategory.PREREQUISITE, "Another project mutation is already running: $root")
        return try {
            withContext(Dispatchers.IO) {
                stage(progress, 1, "Validating project readiness") {
                    val arrangement = arrangementService.load(root)
                    require(arrangement.approved && !arrangement.approvalRequired && !arrangement.stale) {
                        "Build Song requires a current approved arrangement. Review and approve the Qwen draft or regenerate it."
                    }
                    if (!worker.healthCheck()) throw ApplicationServiceException(ApplicationErrorCategory.WORKER, "Python worker is not running. Start it with `make worker`.")
                }
                coroutineContext.ensureActive()
                stage(progress, 2, "Generating required MIDI") { arrangementService.generateRequiredMidi(root, progress) }
                coroutineContext.ensureActive()
                val render = stage(progress, 3, "Rendering or reusing stems") { arrangementService.renderApprovedStems(root, renderer, progress) }
                coroutineContext.ensureActive()
                val mixed = stage(progress, 4, "Applying persisted mix settings") {
                    mixService.apply(ApplyMixRequest(root, mixService.load(root).settings), progress)
                }
                val dry = requireNotNull(mixed.dryMix) { "Mixing did not create mix/dry.wav" }
                val dryAudio = validate(dry, "Dry mix")
                coroutineContext.ensureActive()
                val repaired = root.resolve("mix/repaired.wav")
                stage(progress, 5, "Repairing dry mix", repaired) {
                    publishWav(repaired, "repair") { temporary -> worker.repair(dry, temporary) }
                    requireCompatible(dryAudio, validate(repaired, "Repair"), "Repair")
                }
                coroutineContext.ensureActive()
                val masteringInput = if (request.enableLoFi) {
                    val lofi = root.resolve("mix/lofi.wav")
                    stage(progress, 6, "Applying Lo-fi audio texture", lofi) {
                        publishWav(lofi, "lofi") { temporary -> applyLoFi(repaired, temporary) }
                        requireCompatible(validate(repaired, "Repair"), validate(lofi, "Lo-fi audio texture"), "Lo-fi audio texture")
                    }
                    lofi
                } else {
                    progress.report(OperationProgress("build", 6, STAGE_COUNT, "Skipping Lo-fi audio texture; mastering repaired dry mix", repaired))
                    repaired
                }
                coroutineContext.ensureActive()
                val master = root.resolve("output/master.wav")
                stage(progress, 7, "Mastering lossless WAV", master) {
                    publishWav(master, "master") { temporary -> worker.master(masteringInput, temporary) }
                    val masterAudio = validate(master, "Master")
                    requireCompatible(validate(masteringInput, "Master input"), masterAudio, "Master")
                    require(masterAudio.peak <= 0.891251 + PCM_24_TOLERANCE) { "Master exceeds the -1 dB peak ceiling" }
                }
                coroutineContext.ensureActive()
                val mp3 = if (request.enableMp3) {
                    val target = root.resolve("output/song.mp3")
                    val available = stage(progress, 8, "Exporting optional MP3", target) {
                        worker.exportMp3(master, target, request.mp3BitrateKbps)
                    }
                    target.takeIf { available && Files.isRegularFile(it) && Files.size(it) > 0L }
                } else {
                    progress.report(OperationProgress("build", 8, STAGE_COUNT, "MP3 export not requested"))
                    null
                }
                stage(progress, 9, "Writing release metadata", master) { writeRelease(root, masteringInput, master, mp3, request) }
                progress.report(OperationProgress("build", STAGE_COUNT, STAGE_COUNT, "Build complete", master))
                BuildResult(root, dry, root.resolve("mix/lofi.wav").takeIf { request.enableLoFi }, master, mp3, render.reused)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApplicationServiceException) {
            throw error
        } catch (error: Throwable) {
            throw ApplicationServiceException(category(error), error.message ?: "Build Song failed", error)
        } finally {
            lock.unlock()
        }
    }

    private suspend fun <T> stage(progress: ProgressSink, index: Int, message: String, artifact: Path? = null, action: suspend () -> T): T {
        progress.report(OperationProgress("build", index, STAGE_COUNT, message, artifact))
        return action()
    }

    private fun applyLoFi(input: Path, output: Path) {
        val audio = WAVDecoder(noOpReporter).decode(input)
        val preset = checkNotNull(LOFIPresets.getByName("Bedroom LoFi"))
        val processed = DSPChain.createDefaultChain(preset.settings, audio.format.sampleRate, audio.format.channels).process(audio)
        DeterministicStemMixer().writeWav(MixedStem(processed, listOf("lofi")), output)
    }

    private suspend fun publishWav(target: Path, label: String, action: suspend (Path) -> Unit) {
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.$label-${UUID.randomUUID()}.wav")
        try {
            action(temporary)
            validate(temporary, label)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun validate(path: Path, label: String): AudioDescriptor {
        require(Files.isRegularFile(path)) { "$label did not create an audio artifact: $path" }
        val audio = WAVDecoder(noOpReporter).decode(path)
        require(audio.format.bitDepth == 24 && audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() }) {
            "$label did not create a valid PCM-24 WAV artifact: $path"
        }
        return AudioDescriptor(audio.format.sampleRate, audio.format.channels, audio.length.toLong(), audio.samples.maxOf { abs(it).toDouble() })
    }

    private fun requireCompatible(input: AudioDescriptor, output: AudioDescriptor, label: String) {
        require(input.sampleRate == output.sampleRate && input.channels == output.channels) { "$label changed sample rate or channel count" }
        require(abs(input.frames - output.frames).toDouble() / input.sampleRate <= 0.05) { "$label changed duration by more than 50 ms" }
    }

    private fun writeRelease(root: Path, input: Path, master: Path, mp3: Path?, request: BuildSongRequest) {
        val inputAudio = validate(input, "Master input")
        val audio = validate(master, "Master")
        val release = DesktopReleaseMetadata(
            master = "master.wav", masterFingerprint = digest(master), inputArtifact = root.relativize(input).toString(),
            inputFingerprint = digest(input), inputSampleRate = inputAudio.sampleRate, inputChannels = inputAudio.channels,
            inputPcmBitDepth = 24, sampleRate = audio.sampleRate, channels = audio.channels, pcmBitDepth = 24,
            frameCount = audio.frames, durationSeconds = audio.frames.toDouble() / audio.sampleRate, peak = audio.peak,
            peakDb = if (audio.peak == 0.0) Double.NEGATIVE_INFINITY else 20.0 * kotlin.math.log10(audio.peak),
            repairEnabled = true, loFiAudioTextureEnabled = request.enableLoFi,
            mp3 = mp3?.let { DesktopMp3Metadata("song.mp3", digest(it), request.mp3BitrateKbps) }
        )
        val target = root.resolve("output/release.json")
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(release), StandardCharsets.UTF_8)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun category(error: Throwable) = when (error) {
        is java.io.IOException -> ApplicationErrorCategory.IO
        is IllegalArgumentException -> ApplicationErrorCategory.VALIDATION
        else -> ApplicationErrorCategory.ARTIFACT
    }

    private data class AudioDescriptor(val sampleRate: Int, val channels: Int, val frames: Long, val peak: Double)

    @Serializable private data class DesktopReleaseMetadata(
        val version: Int = 1, val master: String, val masterFingerprint: String, val inputArtifact: String,
        val inputFingerprint: String, val inputSampleRate: Int, val inputChannels: Int, val inputPcmBitDepth: Int,
        val sampleRate: Int, val channels: Int, val pcmBitDepth: Int, val frameCount: Long, val durationSeconds: Double,
        val peak: Double, val peakDb: Double, val targetLufs: Double = -14.0, val truePeakCeilingDb: Double = -1.0,
        val repairEnabled: Boolean, val loFiAudioTextureEnabled: Boolean, val mp3: DesktopMp3Metadata? = null
    )
    @Serializable private data class DesktopMp3Metadata(val name: String, val fingerprint: String, val bitrateKbps: Int, val format: String = "MP3")

    private companion object {
        const val STAGE_COUNT = 9
        const val PCM_24_TOLERANCE = 1.0 / 8_388_608.0
        val MP3_BITRATES = setOf(128, 160, 192, 256, 320)
        val locks = ConcurrentHashMap<Path, Mutex>()
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val noOpReporter = object : ErrorReporter { override fun report(message: String) = Unit; override fun report(message: String, cause: Throwable) = Unit }
    }
}

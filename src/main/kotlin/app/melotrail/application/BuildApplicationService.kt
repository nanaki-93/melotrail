package app.melotrail.application

import app.melotrail.arrangement.InstrumentRenderer
import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.MixedStem
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.DetailedArrangement
import app.melotrail.arrangement.DetailedArrangementStore
import app.melotrail.arrangement.ReleaseFingerprint
import app.melotrail.arrangement.ReleaseSimilarityCritic
import app.melotrail.arrangement.ReleaseSimilarityReport
import app.melotrail.arrangement.StemRenderReport
import app.melotrail.arrangement.WorkflowArtifactReference
import app.melotrail.audio.AudioBuffer
import app.melotrail.audio.WAVDecoder
import app.melotrail.dsp.DSPChain
import app.melotrail.dsp.LOFIPresets
import app.melotrail.model.ErrorReporter
import app.melotrail.model.MasteringMeasurement
import app.melotrail.model.MasteringProfile
import app.melotrail.model.MasteringProfiles
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
import kotlin.math.sqrt

enum class LoFiPresetId { SUBTLE, MEDIUM, PRONOUNCED }

data class BuildSongRequest(
    val root: Path,
    val enableLoFi: Boolean = false,
    val enableMp3: Boolean = false,
    val mp3BitrateKbps: Int = 320,
    val loFiPreset: LoFiPresetId = LoFiPresetId.MEDIUM,
    val loFiStrength: Double = 1.0,
    val masteringProfile: MasteringProfile = MasteringProfiles.LOFI,
    /** Explicit completed-release references only; no unreviewed global catalog is consulted. */
    val similarityReferences: List<ReleaseFingerprint> = emptyList()
)

data class BuildResult(
    val root: Path,
    val dryMix: Path,
    val loFiMix: Path?,
    val master: Path,
    val mp3: Path?,
    val reusedStems: Boolean
)

/** Local encode/decode evidence is a regression proxy, never a prediction of a platform transcode. */
@Serializable
enum class DeliveryCodec { AAC, MP3 }

@Serializable
enum class CodecPreviewStatus { VERIFIED, UNVERIFIED, BLOCKED }

/** Hash-bound local codec-preview measurement for the exact selected lossless master. */
@Serializable
data class CodecPreviewEvidence(
    val codec: DeliveryCodec,
    val status: CodecPreviewStatus,
    val masterSha256: String,
    val encoded: WorkflowArtifactReference? = null,
    val decoded: WorkflowArtifactReference? = null,
    val truePeakDbtp: Double? = null,
    val clippingSampleCount: Int? = null,
    val detail: String
) {
    init {
        require(masterSha256.matches(Regex("[0-9a-f]{64}")) && detail.isNotBlank() &&
            (status == CodecPreviewStatus.VERIFIED || status == CodecPreviewStatus.BLOCKED) ==
                (encoded != null && decoded != null && truePeakDbtp?.isFinite() == true && clippingSampleCount != null && clippingSampleCount >= 0)) {
            "Codec-preview evidence is invalid"
        }
    }
}

/** Worker-only boundary. The build service owns all project and artifact orchestration. */
interface BuildAudioWorker {
    suspend fun healthCheck(): Boolean
    suspend fun repair(input: Path, output: Path)
    suspend fun master(input: Path, output: Path, profile: MasteringProfile): MasteringMeasurement
    suspend fun exportMp3(input: Path, output: Path, bitrateKbps: Int): Boolean
    /** Optional local codec execution. Unavailable codecs remain visible as unverified evidence. */
    suspend fun codecPreviews(input: Path, outputDirectory: Path, profile: MasteringProfile): List<CodecPreviewEvidence> =
        DeliveryCodec.entries.map { codec -> CodecPreviewEvidence(codec, CodecPreviewStatus.UNVERIFIED,
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)).joinToString("") { "%02x".format(it) },
            detail = "Local $codec codec preview is unavailable; no platform claim is implied.") }
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
    private val worker: BuildAudioWorker,
    private val cohesionService: EnsembleCohesionApplicationService = DefaultEnsembleCohesionApplicationService(),
    private val humanizationService: HumanizationApplicationService = DefaultHumanizationApplicationService()
) : BuildApplicationService {
    override suspend fun build(request: BuildSongRequest, progress: ProgressSink): BuildResult {
        require(request.mp3BitrateKbps in MP3_BITRATES) { "MP3 bitrate must be one of ${MP3_BITRATES.sorted().joinToString()} kbps" }
        require(request.loFiStrength.isFinite() && request.loFiStrength in 0.0..1.0) { "Lo-fi strength must be from 0.0 to 1.0" }
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
                    val cohesion = cohesionService.load(root)
                    require(cohesion.approved && !cohesion.approvalRequired && !cohesion.stale) {
                        "Build Song requires current approved Cohesion. Regenerate, compare, and approve it."
                    }
                    if (!worker.healthCheck()) throw ApplicationServiceException(ApplicationErrorCategory.WORKER, "Python worker is not running. Start it with `make worker`.")
                    requireCurrentFullSongEnhancement(root)
                }
                coroutineContext.ensureActive()
                stage(progress, 2, "Validating approved ensemble MIDI") { requireCurrentGeneratedMidi(root) }
                coroutineContext.ensureActive()
                stage(progress, 3, "Validating selected humanization") { humanizationService.load(root) }
                coroutineContext.ensureActive()
                val render = stage(progress, 4, "Rendering or reusing stems") { arrangementService.renderApprovedStems(root, renderer, progress) }
                coroutineContext.ensureActive()
                val mixed = stage(progress, 5, "Applying persisted mix settings") {
                    mixService.apply(ApplyMixRequest(root, mixService.load(root).settings), progress)
                }
                val dry = requireNotNull(mixed.dryMix) { "Mixing did not create mix/dry.wav" }
                val dryAudio = validate(dry, "Dry mix")
                coroutineContext.ensureActive()
                val repaired = root.resolve("mix/repaired.wav")
                stage(progress, 6, "Repairing dry mix", repaired) {
                    publishWav(repaired, "repair") { temporary -> worker.repair(dry, temporary) }
                    requireCompatible(dryAudio, validate(repaired, "Repair"), "Repair")
                }
                coroutineContext.ensureActive()
                val masteringInput = if (request.enableLoFi) {
                    val lofi = root.resolve("mix/lofi.wav")
                    stage(progress, 7, "Applying Lo-fi audio texture", lofi) {
                        publishWav(lofi, "lofi") { temporary -> applyLoFi(repaired, temporary, request.loFiPreset, request.loFiStrength) }
                        requireCompatible(validate(repaired, "Repair"), validate(lofi, "Lo-fi audio texture"), "Lo-fi audio texture")
                    }
                    lofi
                } else {
                    progress.report(OperationProgress("build", 7, STAGE_COUNT, "Skipping Lo-fi audio texture; mastering repaired dry mix", repaired))
                    repaired
                }
                coroutineContext.ensureActive()
                val master = root.resolve("output/master.wav")
                val masteringMeasurement = stage(progress, 8, "Mastering lossless WAV", master) {
                    var measurement: MasteringMeasurement? = null
                    publishWav(master, "master") { temporary -> measurement = worker.master(masteringInput, temporary, request.masteringProfile) }
                    val masterAudio = validate(master, "Master")
                    requireCompatible(validate(masteringInput, "Master input"), masterAudio, "Master")
                    val completedMeasurement = requireNotNull(measurement) { "Mastering worker returned no measurement evidence" }
                    requireMasteringProfile(completedMeasurement, request.masteringProfile)
                    completedMeasurement
                }
                coroutineContext.ensureActive()
                val codecPreviews = stage(progress, 9, "Measuring local codec previews", master) {
                    val evidence = worker.codecPreviews(master, root.resolve("output/codec-previews"), request.masteringProfile)
                    require(evidence.map(CodecPreviewEvidence::codec).toSet() == DeliveryCodec.entries.toSet()) { "Codec preview evidence is incomplete." }
                    require(evidence.all { it.masterSha256 == digest(master) }) { "Codec preview evidence belongs to another selected master." }
                    require(evidence.none { it.status == CodecPreviewStatus.BLOCKED }) {
                        "A local codec preview exceeds the delivery policy. Lower the versioned pre-encode ceiling or repair the master before review."
                    }
                    evidence.sortedBy { it.codec.name }
                }
                val mp3 = if (request.enableMp3) {
                    val target = root.resolve("output/song.mp3")
                    val available = stage(progress, 10, "Exporting optional MP3", target) {
                        worker.exportMp3(master, target, request.mp3BitrateKbps)
                    }
                    target.takeIf { available && Files.isRegularFile(it) && Files.size(it) > 0L }
                } else {
                    progress.report(OperationProgress("build", 10, STAGE_COUNT, "MP3 export not requested"))
                    null
                }
                stage(progress, 11, "Writing release metadata", master) { writeRelease(root, masteringInput, master, mp3, codecPreviews, request, masteringMeasurement) }
                ProjectWorkflowStore.update(root) { workflow ->
                    workflow.markCurrent(WorkflowArtifact.MASTER, WorkflowArtifact.RELEASE).let {
                        if (request.enableLoFi) it.markCurrent(WorkflowArtifact.AUDIO_TEXTURE) else it
                    }
                }
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

    private fun requireCurrentGeneratedMidi(root: Path) {
        val project = ProjectStore.read(root)
        require(WorkflowArtifact.GENERATED_MIDI !in project.workflow.stale) {
            "Build Song requires current ensemble MIDI created before Cohesion. Regenerate Cohesion."
        }
        val arrangement = requireNotNull(project.workflow.arrangement)
        val generated = requireNotNull(project.workflow.generatedMidi) {
            "Build Song requires fingerprinted ensemble MIDI. Regenerate Cohesion."
        }
        require(generated.arrangementSha256 == arrangement.arrangement.sha256) { "Generated ensemble MIDI belongs to another arrangement." }
        generated.artifacts.forEach { reference ->
            val path = root.resolve(reference.artifact.file).normalize()
            require(path.startsWith(root) && Files.isRegularFile(path) && digest(path) == reference.artifact.sha256) {
                "Generated ensemble MIDI '${reference.id}' is missing or changed. Regenerate Cohesion."
            }
        }
    }

    private fun requireCurrentFullSongEnhancement(root: Path) {
        val project = ProjectStore.read(root)
        val critic = requireNotNull(project.workflow.critic) {
            "Build Song requires a current Critic report. Run Critic after Cohesion."
        }
        val report = root.resolve(critic.report.file).normalize()
        require(WorkflowArtifact.CRITIC !in project.workflow.stale && report.startsWith(root) && Files.isRegularFile(report) && digest(report) == critic.report.sha256) {
            "Build Song requires a current Critic report. Rerun Critic after Cohesion."
        }
        require(project.workflow.fullSongEnhancementSelection != app.melotrail.arrangement.FullSongEnhancementSelection.UNRESOLVED) {
            "Build Song requires an approved Full-Song Enhance candidate, recorded no-op, or explicit bypass."
        }
        if (project.workflow.fullSongEnhancementSelection == app.melotrail.arrangement.FullSongEnhancementSelection.APPROVED) {
            require(WorkflowArtifact.FULL_SONG_ENHANCEMENT !in project.workflow.stale && project.workflow.fullSongEnhancement != null) {
                "Build Song requires a current approved Full-Song Enhance candidate."
            }
        }
    }

    private fun applyLoFi(input: Path, output: Path, presetId: LoFiPresetId, strength: Double) {
        val audio = WAVDecoder(ErrorReporter.NoOp).decode(input)
        val name = when (presetId) {
            LoFiPresetId.SUBTLE -> "Warm Cassette"
            LoFiPresetId.MEDIUM -> "Bedroom LoFi"
            LoFiPresetId.PRONOUNCED -> "Old Sampler"
        }
        val preset = checkNotNull(LOFIPresets.getByName(name))
        val settings = preset.settings.copy(amount = (preset.settings.amount * strength).coerceIn(0.0, 1.0))
        val processed = DSPChain.createDefaultChain(settings, audio.format.sampleRate, audio.format.channels).process(audio)
        DeterministicStemMixer().writeWav(MixedStem(loudnessMatch(audio, processed), listOf("lofi", presetId.name.lowercase())), output)
    }

    /** Whole-file RMS matching keeps A/B judgments about character rather than gain. */
    private fun loudnessMatch(reference: AudioBuffer, processed: AudioBuffer): AudioBuffer {
        fun rms(samples: FloatArray) = sqrt(samples.sumOf { value -> value.toDouble() * value } / samples.size.coerceAtLeast(1))
        val target = rms(reference.samples); val current = rms(processed.samples)
        if (target <= 1e-9 || current <= 1e-9) return processed
        val gain = (target / current).coerceIn(0.5, 2.0)
        val peak = processed.samples.maxOfOrNull { abs(it) }?.toDouble()?.times(gain) ?: 0.0
        val safeGain = if (peak > 0.98) gain * 0.98 / peak else gain
        return processed.copy(samples = FloatArray(processed.samples.size) { index -> (processed.samples[index] * safeGain).toFloat() })
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
        val audio = WAVDecoder(ErrorReporter.NoOp).decode(path)
        require(audio.format.bitDepth == 24 && audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() }) {
            "$label did not create a valid PCM-24 WAV artifact: $path"
        }
        return AudioDescriptor(audio.format.sampleRate, audio.format.channels, audio.length.toLong(), audio.samples.maxOf { abs(it).toDouble() })
    }

    private fun requireCompatible(input: AudioDescriptor, output: AudioDescriptor, label: String) {
        require(input.sampleRate == output.sampleRate && input.channels == output.channels) { "$label changed sample rate or channel count" }
        require(abs(input.frames - output.frames).toDouble() / input.sampleRate <= 0.05) { "$label changed duration by more than 50 ms" }
    }

    /** Enforces the profile's true-peak and dynamics boundaries without treating -14 LUFS as an exact law. */
    private fun requireMasteringProfile(measurement: MasteringMeasurement, profile: MasteringProfile) {
        require(measurement.truePeakDbtp <= profile.maximumTruePeakDbtp + 0.05) {
            "Master exceeds the ${profile.maximumTruePeakDbtp} dBTP true-peak ceiling"
        }
        require(measurement.integratedLufs <= profile.nominalIntegratedLufs + profile.loudnessToleranceLu) {
            "Master exceeds the ${profile.nominalIntegratedLufs} LUFS delivery reference tolerance"
        }
        require(measurement.dynamicsPreserved && measurement.lraLu >= profile.minimumLraLu && measurement.crestDb >= profile.minimumCrestDb &&
            measurement.limiterMaxGainReductionDb <= profile.maximumLimiterGainReductionDb) {
            "Master dynamics quality failed: ${measurement.qualityIssues.joinToString().ifBlank { "profile limits exceeded" }}"
        }
    }

    private fun writeRelease(root: Path, input: Path, master: Path, mp3: Path?, codecPreviews: List<CodecPreviewEvidence>, request: BuildSongRequest, mastering: MasteringMeasurement) {
        val inputAudio = validate(input, "Master input")
        val audio = validate(master, "Master")
        val similarityReview = releaseSimilarityReview(root, request.similarityReferences)
        val canonicalFullMelody = releaseMelodyLineage(root)
        val release = DesktopReleaseMetadata(
            master = "master.wav", masterFingerprint = digest(master), inputArtifact = root.relativize(input).toString(),
            inputFingerprint = digest(input), inputSampleRate = inputAudio.sampleRate, inputChannels = inputAudio.channels,
            inputPcmBitDepth = 24, sampleRate = audio.sampleRate, channels = audio.channels, pcmBitDepth = 24,
            frameCount = audio.frames, durationSeconds = audio.frames.toDouble() / audio.sampleRate, peak = audio.peak,
            peakDb = if (audio.peak == 0.0) Double.NEGATIVE_INFINITY else 20.0 * kotlin.math.log10(audio.peak),
            masteringProfile = request.masteringProfile.id, integratedLufs = mastering.integratedLufs, truePeakDbtp = mastering.truePeakDbtp,
            masteringPolicy = request.masteringProfile,
            loudnessRangeLu = mastering.lraLu, crestDb = mastering.crestDb,
            limiterMaxGainReductionDb = mastering.limiterMaxGainReductionDb, limiterMeanGainReductionDb = mastering.limiterMeanGainReductionDb,
            loudnessReference = mastering.loudnessReference, dynamicsPreserved = mastering.dynamicsPreserved, masteringQualityIssues = mastering.qualityIssues,
            repairEnabled = true, loFiAudioTextureEnabled = request.enableLoFi,
            loFiPreset = request.loFiPreset.takeIf { request.enableLoFi }?.name?.lowercase(),
            loFiStrength = request.loFiStrength.takeIf { request.enableLoFi },
            loFiMeanAbsoluteDelta = if (request.enableLoFi) audioDelta(root.resolve("mix/repaired.wav"), root.resolve("mix/lofi.wav")) else null,
            mp3 = mp3?.let { DesktopMp3Metadata("song.mp3", digest(it), request.mp3BitrateKbps) },
            codecPreviews = codecPreviews,
            canonicalFullMelody = canonicalFullMelody,
            similarityReview = similarityReview
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

    /** Bind release evidence to the same approved connected melody that supplied every rendered piano section. */
    private fun releaseMelodyLineage(root: Path): WorkflowArtifactReference {
        val sourceApproval = DefaultSourceSongCriticApplicationService()
        sourceApproval.requireQualityCertifiedApproved(root)
        val approved = sourceApproval.requireApprovedMelody(root)
        val reportPath = root.resolve("stem-render.json").normalize()
        require(reportPath.startsWith(root) && Files.isRegularFile(reportPath)) { "Release requires a current stem-render report with canonical melody lineage." }
        val report = json.decodeFromString(StemRenderReport.serializer(), Files.readString(reportPath, StandardCharsets.UTF_8))
        require(report.canonicalFullMelody == approved.connectedMidi) {
            "Stem-render melody lineage is stale. Rerender stems from the approved connected full melody before release."
        }
        return approved.connectedMidi
    }

    private fun releaseSimilarityReview(root: Path, references: List<ReleaseFingerprint>): ReleaseSimilarityReport {
        val project = ProjectStore.read(root)
        val settings = requireNotNull(project.envelope.compositionSettings?.takeIf { it.complete }) {
            "Release similarity review requires complete canonical composition settings."
        }
        val arrangementPath = root.resolve(DetailedArrangementStore.APPROVED_FILE)
        require(Files.isRegularFile(arrangementPath)) { "Release similarity review requires the approved detailed arrangement." }
        val arrangement = json.decodeFromString(DetailedArrangement.serializer(), Files.readString(arrangementPath, StandardCharsets.UTF_8))
        val fingerprint = ReleaseSimilarityCritic().fingerprint(arrangement, settings.tempo, settings.timeSignature)
        return ReleaseSimilarityCritic().review(fingerprint, references)
    }

    private fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun audioDelta(left: Path, right: Path): Double {
        val a = WAVDecoder(ErrorReporter.NoOp).decode(left).samples; val b = WAVDecoder(ErrorReporter.NoOp).decode(right).samples
        require(a.size == b.size) { "Lo-fi comparison length mismatch" }
        return a.indices.sumOf { abs(a[it] - b[it]).toDouble() } / a.size.coerceAtLeast(1)
    }
    private fun category(error: Throwable) = when (error) {
        is java.io.IOException -> ApplicationErrorCategory.IO
        is IllegalArgumentException -> ApplicationErrorCategory.VALIDATION
        else -> ApplicationErrorCategory.ARTIFACT
    }

    private data class AudioDescriptor(val sampleRate: Int, val channels: Int, val frames: Long, val peak: Double)

    @Serializable private data class DesktopReleaseMetadata(
        val version: Int = 3, val master: String, val masterFingerprint: String, val inputArtifact: String,
        val inputFingerprint: String, val inputSampleRate: Int, val inputChannels: Int, val inputPcmBitDepth: Int,
        val sampleRate: Int, val channels: Int, val pcmBitDepth: Int, val frameCount: Long, val durationSeconds: Double,
        val peak: Double, val peakDb: Double, val masteringProfile: String, val masteringPolicy: MasteringProfile,
        val integratedLufs: Double, val truePeakDbtp: Double,
        val loudnessRangeLu: Double, val crestDb: Double, val limiterMaxGainReductionDb: Double, val limiterMeanGainReductionDb: Double,
        val loudnessReference: String, val dynamicsPreserved: Boolean, val masteringQualityIssues: List<String>,
        val repairEnabled: Boolean, val loFiAudioTextureEnabled: Boolean,
        val loFiPreset: String? = null, val loFiStrength: Double? = null, val loFiMeanAbsoluteDelta: Double? = null,
        val mp3: DesktopMp3Metadata? = null,
        val codecPreviews: List<CodecPreviewEvidence> = emptyList(),
        val canonicalFullMelody: WorkflowArtifactReference,
        val similarityReview: ReleaseSimilarityReport
    )
    @Serializable private data class DesktopMp3Metadata(val name: String, val fingerprint: String, val bitrateKbps: Int, val format: String = "MP3")

    private companion object {
        const val STAGE_COUNT = 11
        val MP3_BITRATES = setOf(128, 160, 192, 256, 320)
        val locks = ConcurrentHashMap<Path, Mutex>()
        val json = Json { prettyPrint = true; encodeDefaults = true }
    }
}

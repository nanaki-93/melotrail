package app.melotrail.application

import app.melotrail.arrangement.AudioMixCritic
import app.melotrail.arrangement.AudioMixCriticReport
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MixPlan
import app.melotrail.arrangement.MixPlanInputStem
import app.melotrail.arrangement.MixTrack
import app.melotrail.arrangement.MixTrackPlan
import app.melotrail.arrangement.LowEndBandAnalyzer
import app.melotrail.arrangement.LowEndInteractionPlan
import app.melotrail.arrangement.LowEndInteractionPlanner
import app.melotrail.arrangement.LowEndInteractionStatus
import app.melotrail.arrangement.ProductionStemMixer
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.RoleValidationReport
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import kotlin.math.roundToInt

/** Existing desktop naming is retained as a source alias; persisted data is [MixPlan]. */
typealias LogicalMixSetting = MixTrackPlan
typealias PersistedMixSettings = MixPlan

data class MixSnapshot(
    val root: Path,
    val settings: MixPlan,
    val availableStems: List<String>,
    val dryMix: Path?,
    val report: AudioMixCriticReport? = null,
    val stale: Boolean,
    /** Present only when the exact persisted plan and dry-mix artifact were explicitly approved. */
    val approval: MixApproval? = null
)

/** Durable user approval for one exact production-mix revision. */
@Serializable
data class MixApproval(
    val version: Int = VERSION,
    val planSha256: String,
    val mixSha256: String
) {
    init {
        require(version == VERSION) { "Unsupported mix approval" }
        require(SHA_256.matches(planSha256) && SHA_256.matches(mixSha256)) { "Mix approval fingerprints are invalid" }
    }

    companion object {
        const val VERSION = 1
        private val SHA_256 = Regex("[0-9a-f]{64}")
    }
}

data class ApplyMixRequest(val root: Path, val settings: MixPlan)

interface MixApplicationService {
    fun load(root: Path): MixSnapshot
    suspend fun apply(request: ApplyMixRequest, progress: ProgressSink = ProgressSink.None): MixSnapshot
    /** Approves the exact plan and dry mix currently inspected by the musician. */
    fun approve(root: Path): MixSnapshot
}

/** Re-mixes validated stems only; it never triggers MIDI generation, rendering, DSP, or mastering. */
class DefaultMixApplicationService(private val mixer: ProductionStemMixer = ProductionStemMixer()) : MixApplicationService {
    override fun load(root: Path): MixSnapshot {
        val normalized = root.normalizeRoot()
        val settings = plan(normalized)
        val stems = availableStems(normalized)
        val dry = normalized.resolve(DRY_MIX).takeIf(Files::isRegularFile)
        val report = normalized.resolve(REPORT_FILE).takeIf(Files::isRegularFile)?.let { path ->
            runCatching { json.decodeFromString(AudioMixCriticReport.serializer(), Files.readString(path, StandardCharsets.UTF_8)) }.getOrNull()
        }
        val approval = report?.let { currentApproval(normalized, it) }
        return MixSnapshot(normalized, settings, stems, dry, report, stale = stems.isEmpty() || report?.lowEndInteraction == null, approval = approval)
    }

    override fun approve(root: Path): MixSnapshot {
        val normalized = root.normalizeRoot()
        val snapshot = load(normalized)
        val report = requireNotNull(snapshot.report) { "Build a current dry mix and audio critic report before approving the mix." }
        val dry = requireNotNull(snapshot.dryMix) { "Build a current dry mix before approving the mix." }
        val planPath = normalized.resolve(PLAN_FILE)
        require(Files.isRegularFile(planPath)) { "Current mix settings are missing. Build the dry mix again." }
        val planHash = sha256(Files.readAllBytes(planPath))
        val mixHash = digest(dry)
        require(report.planSha256 == planHash && report.mixSha256 == mixHash) {
            "Mix evidence no longer matches the current plan or dry mix. Build the dry mix again."
        }
        require(report.commercialReady && report.lowEndInteraction?.severeUnresolvedOverlap != true && report.lowEndInteraction?.pumpingDetected != true) {
            "Current audio critic report has blocking low-end or production findings. Repair and rebuild the mix before approval."
        }
        writeAtomically(normalized.resolve(APPROVAL_FILE), json.encodeToString(MixApproval(planSha256 = planHash, mixSha256 = mixHash)))
        return load(normalized)
    }

    override suspend fun apply(request: ApplyMixRequest, progress: ProgressSink): MixSnapshot {
        val root = request.root.normalizeRoot()
        val lock = locks.computeIfAbsent(root) { Mutex() }
        if (!lock.tryLock()) throw ApplicationServiceException(ApplicationErrorCategory.PREREQUISITE, "Another project mutation is already running: $root")
        return try {
            withContext(Dispatchers.IO) {
                request.settings.requireValid()
                val project = ProjectStore.read(root).also { it.requireValid(root) }
                val format = requireNotNull(project.renderFormat) { "Stem-only mixing requires a MIDI-first project render format" }
                val stemNames = availableStems(root)
                require(stemNames.isNotEmpty()) { "No rendered stems found. Render the approved arrangement first." }
                progress.report(OperationProgress("mix", 1, 3, "Validating rendered stems"))
                val tracks = stemNames.map { name -> MixTrack(name, WAVDecoder(noOpErrorReporter).decode(root.resolve("stems/$name.wav"))) }
                val inputs = stemNames.map { name -> MixPlanInputStem(name, digest(root.resolve("stems/$name.wav"))) }
                val basePlan = request.settings.withInputs(inputs)
                val plan = basePlan.copy(lowEndInteraction = deriveLowEndPlan(root, project, tracks, inputs)).also(MixPlan::requireValid)
                progress.report(OperationProgress("mix", 2, 3, "Rendering deterministic production mix", root.resolve(DRY_MIX)))
                val mixed = mixer.mix(tracks, plan, format)
                val output = root.resolve(DRY_MIX)
                app.melotrail.arrangement.DeterministicStemMixer().writeWav(mixed, output)
                val planText = json.encodeToString(plan)
                writeAtomically(root.resolve(PLAN_FILE), planText)
                val report = AudioMixCritic.analyze(mixed, tracks, sha256(planText.toByteArray(StandardCharsets.UTF_8)), digest(output), plan.lowEndInteraction)
                writeAtomically(root.resolve(REPORT_FILE), json.encodeToString(report))
                progress.report(OperationProgress("mix", 3, 3, "Published production mix and critic report", root.resolve(REPORT_FILE)))
                ProjectWorkflowStore.update(root) { it.invalidate(WorkflowChange.MIX_ONLY).markCurrent(WorkflowArtifact.DRY_MIX, WorkflowArtifact.MIX_REPORT) }
                load(root)
            }
        } catch (error: ApplicationServiceException) {
            throw error
        } catch (error: Throwable) {
            throw ApplicationServiceException(if (error is java.io.IOException) ApplicationErrorCategory.IO else ApplicationErrorCategory.ARTIFACT, error.message ?: "Mix failed", error)
        } finally { lock.unlock() }
    }

    private fun plan(root: Path): MixPlan {
        val path = root.resolve(PLAN_FILE)
        if (!Files.isRegularFile(path)) return MixPlan()
        return json.decodeFromString(MixPlan.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also(MixPlan::requireValid)
    }

    private fun currentApproval(root: Path, report: AudioMixCriticReport): MixApproval? {
        val path = root.resolve(APPROVAL_FILE)
        if (!Files.isRegularFile(path)) return null
        val approval = runCatching { json.decodeFromString(MixApproval.serializer(), Files.readString(path, StandardCharsets.UTF_8)) }.getOrNull() ?: return null
        val plan = root.resolve(PLAN_FILE)
        val dry = root.resolve(DRY_MIX)
        if (!Files.isRegularFile(plan) || !Files.isRegularFile(dry)) return null
        val planHash = sha256(Files.readAllBytes(plan))
        val mixHash = digest(dry)
        return approval.takeIf {
            it.planSha256 == report.planSha256 && it.mixSha256 == report.mixSha256 &&
                it.planSha256 == planHash && it.mixSha256 == mixHash && report.lowEndInteraction != null && report.commercialReady
        }
    }

    /** Derive a plan only from current approved drum-MIDI/report evidence and exact rendered-stem fingerprints. */
    private fun deriveLowEndPlan(
        root: Path,
        project: app.melotrail.arrangement.Project,
        tracks: List<MixTrack>,
        inputs: List<MixPlanInputStem>
    ): LowEndInteractionPlan {
        val drums = tracks.singleOrNull { it.name == LogicalInstrument.DRUMS.wireName }?.buffer
        val bass = tracks.singleOrNull { it.name == LogicalInstrument.BASS.wireName }?.buffer
        val drumHash = inputs.singleOrNull { it.name == LogicalInstrument.DRUMS.wireName }?.sha256
        val bassHash = inputs.singleOrNull { it.name == LogicalInstrument.BASS.wireName }?.sha256
        val inputHash = MixPlan.inputFingerprint(inputs)
        if (drums == null || bass == null) return LowEndInteractionPlanner.derive(
            drums, bass, drumHash, bassHash, null, null, inputHash, null, null, emptyList()
        )
        return runCatching {
            val generated = requireNotNull(project.workflow.generatedMidi) { "Current approved generated MIDI is required for low-end interaction." }
            val drumsReference = requireNotNull(generated.artifacts.singleOrNull { it.id == LogicalInstrument.DRUMS.wireName }) {
                "Current approved drum MIDI is required for low-end interaction."
            }
            val midi = verified(root, drumsReference.artifact, "Approved drum MIDI")
            val reportPath = verified(root, drumsReference.validationReport, "Approved drum validation report")
            val report = json.decodeFromString(RoleValidationReport.serializer(), Files.readString(reportPath, StandardCharsets.UTF_8))
            require(report.passed && report.role == LogicalInstrument.DRUMS.wireName && report.outputSha256 == drumsReference.artifact.sha256) {
                "Approved drum validation evidence is stale or failing. Regenerate drums before mixing."
            }
            val kick = requireNotNull(report.kickTimingEvidence) { "Approved drum validation report has no kick timing evidence." }
            val map = requireNotNull(report.instrumentMapEvidence) { "Approved drum validation report has no instrument-note map." }
            require(map.role == LogicalInstrument.DRUMS.wireName && map.midiChannel == kick.midiChannel && map.notes.any { it.name == "kick" && it.pitch == kick.note }) {
                "Approved drum kick timing and instrument-note map disagree."
            }
            val ticks = kickTicks(midi, kick.midiChannel, kick.note)
            require(ticks == kick.attackTicks) { "Approved drum MIDI kick attacks do not match its validation evidence." }
            val triggers = ticks.map { tick -> app.melotrail.arrangement.LowEndKickTrigger(tick, tickToFrame(midi, tick, drums.format.sampleRate, drums.length)) }
                .filter { it.frame in 0 until drums.length }
            LowEndInteractionPlanner.derive(drums, bass, drumHash, bassHash, drumsReference.artifact.sha256, drumsReference.validationReport.sha256,
                inputHash, kick.midiChannel, kick.note, triggers)
        }.getOrElse {
            LowEndInteractionPlan(
                status = LowEndInteractionStatus.BLOCKED, drumStemSha256 = drumHash, bassStemSha256 = bassHash,
                mixInputsSha256 = inputHash,
                blockers = listOf(it.message ?: "Approved drum kick-map evidence is unavailable.").map(String::trim).distinct().sorted(),
                before = LowEndBandAnalyzer.measure(drums, bass)
            )
        }
    }

    /** Verify a confined fingerprinted workflow artifact before using it as mix-control evidence. */
    private fun verified(root: Path, reference: app.melotrail.arrangement.WorkflowArtifactReference, label: String): Path {
        val path = root.resolve(reference.file).normalize()
        require(path.startsWith(root) && Files.isRegularFile(path) && !Files.isSymbolicLink(path) && digest(path) == reference.sha256) {
            "$label is missing or stale."
        }
        return path
    }

    /** Return sorted note-on ticks only for the exact approved kick channel/note. */
    private fun kickTicks(path: Path, channel: Int, note: Int): List<Long> = MidiSystem.getSequence(path.toFile()).tracks.flatMap { track ->
        (0 until track.size()).map(track::get).mapNotNull { event ->
            val message = event.message as? ShortMessage
            event.tick.takeIf { message?.command == ShortMessage.NOTE_ON && message.channel == channel && message.data1 == note && message.data2 > 0 }
        }
    }.sorted()

    /** Map PPQ ticks with the MIDI's explicit tempo events, preserving every approved kick attack phase. */
    private fun tickToFrame(path: Path, tick: Long, sampleRate: Int, maximumFrame: Int): Int {
        val sequence = MidiSystem.getSequence(path.toFile())
        val tempos = sequence.tracks.flatMap { track -> (0 until track.size()).map(track::get) }
            .mapNotNull { event -> (event.message as? MetaMessage)?.takeIf { it.type == 0x51 && it.data.size == 3 }?.let { tempo ->
                event.tick to (((tempo.data[0].toInt() and 0xFF) shl 16) or ((tempo.data[1].toInt() and 0xFF) shl 8) or (tempo.data[2].toInt() and 0xFF))
            } }
            .sortedBy { it.first }
        var previousTick = 0L; var micros = 0.0; var tempo = 500_000
        tempos.takeWhile { it.first <= tick }.forEach { (changeTick, nextTempo) ->
            micros += (changeTick - previousTick).toDouble() * tempo / sequence.resolution
            previousTick = changeTick; tempo = nextTempo
        }
        micros += (tick - previousTick).toDouble() * tempo / sequence.resolution
        return (micros / 1_000_000.0 * sampleRate).roundToInt().coerceIn(0, maximumFrame)
    }

    private fun writeAtomically(target: Path, contents: String) {
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun availableStems(root: Path): List<String> = LogicalInstrument.entries.map { it.wireName }.filter { Files.isRegularFile(root.resolve("stems/$it.wav")) }
    private fun digest(path: Path): String = sha256(Files.readAllBytes(path))
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun Path.normalizeRoot(): Path = toAbsolutePath().normalize()

    companion object {
        const val PLAN_FILE = "mix/plan.json"
        const val REPORT_FILE = "mix/report.json"
        const val APPROVAL_FILE = "mix/approval.json"
        const val DRY_MIX = "mix/dry.wav"
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        val locks = ConcurrentHashMap<Path, Mutex>()
        val noOpErrorReporter = ErrorReporter.NoOp
    }
}

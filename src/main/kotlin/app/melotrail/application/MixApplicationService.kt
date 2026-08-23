package app.melotrail.application

import app.melotrail.arrangement.AudioMixCritic
import app.melotrail.arrangement.AudioMixCriticReport
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MixPlan
import app.melotrail.arrangement.MixPlanInputStem
import app.melotrail.arrangement.MixTrack
import app.melotrail.arrangement.MixTrackPlan
import app.melotrail.arrangement.ProductionStemMixer
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.ProjectWorkflowStore
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.arrangement.WorkflowChange
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
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

/** Existing desktop naming is retained as a source alias; persisted data is [MixPlan]. */
typealias LogicalMixSetting = MixTrackPlan
typealias PersistedMixSettings = MixPlan

data class MixSnapshot(
    val root: Path,
    val settings: MixPlan,
    val availableStems: List<String>,
    val dryMix: Path?,
    val report: AudioMixCriticReport? = null,
    val stale: Boolean
)

data class ApplyMixRequest(val root: Path, val settings: MixPlan)

interface MixApplicationService {
    fun load(root: Path): MixSnapshot
    suspend fun apply(request: ApplyMixRequest, progress: ProgressSink = ProgressSink.None): MixSnapshot
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
        return MixSnapshot(normalized, settings, stems, dry, report, stale = stems.isEmpty())
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
                val plan = request.settings.withInputs(inputs).also(MixPlan::requireValid)
                progress.report(OperationProgress("mix", 2, 3, "Rendering deterministic production mix", root.resolve(DRY_MIX)))
                val mixed = mixer.mix(tracks, plan, format)
                val output = root.resolve(DRY_MIX)
                app.melotrail.arrangement.DeterministicStemMixer().writeWav(mixed, output)
                val planText = json.encodeToString(plan)
                writeAtomically(root.resolve(PLAN_FILE), planText)
                val report = AudioMixCritic.analyze(mixed, tracks, sha256(planText.toByteArray(StandardCharsets.UTF_8)), digest(output))
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
        const val DRY_MIX = "mix/dry.wav"
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        val locks = ConcurrentHashMap<Path, Mutex>()
        val noOpErrorReporter = ErrorReporter.NoOp
    }
}

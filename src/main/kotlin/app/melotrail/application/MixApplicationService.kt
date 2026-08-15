package app.melotrail.application

import app.melotrail.arrangement.DeterministicStemMixer
import app.melotrail.arrangement.LogicalInstrument
import app.melotrail.arrangement.MixSettings
import app.melotrail.arrangement.MixTrack
import app.melotrail.arrangement.ProjectStore
import app.melotrail.audio.WAVDecoder
import app.melotrail.model.ErrorReporter
import kotlinx.coroutines.Dispatchers
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class LogicalMixSetting(val gainDb: Double = 0.0, val pan: Double = 0.0, val muted: Boolean = false, val solo: Boolean = false) {
    fun requireValid(name: String) {
        require(gainDb.isFinite()) { "Mix setting '$name' gain must be finite" }
        require(pan.isFinite() && pan in -1.0..1.0) { "Mix setting '$name' pan must be between -1 and 1" }
    }
}

@Serializable
data class PersistedMixSettings(val version: Int = VERSION, val tracks: Map<String, LogicalMixSetting> = defaults()) {
    fun requireValid() {
        require(version == VERSION) { "Unsupported mix settings version: $version" }
        require(tracks.keys.all { it in LOGICAL_NAMES }) { "Mix settings contain unsupported logical instruments" }
        tracks.forEach { (name, setting) -> setting.requireValid(name) }
    }

    companion object {
        const val VERSION = 1
        val LOGICAL_NAMES = LogicalInstrument.entries.map { it.wireName }.toSet()
        fun defaults(): Map<String, LogicalMixSetting> = LOGICAL_NAMES.associateWith { LogicalMixSetting() }
    }
}

data class MixSnapshot(
    val root: Path,
    val settings: PersistedMixSettings,
    val availableStems: List<String>,
    val dryMix: Path?,
    val stale: Boolean
)

data class ApplyMixRequest(val root: Path, val settings: PersistedMixSettings)

interface MixApplicationService {
    fun load(root: Path): MixSnapshot
    suspend fun apply(request: ApplyMixRequest, progress: ProgressSink = ProgressSink.None): MixSnapshot
}

/** Re-mixes validated stems only. It never triggers MIDI generation, rendering, DSP, or mastering. */
class DefaultMixApplicationService(private val mixer: DeterministicStemMixer = DeterministicStemMixer()) : MixApplicationService {
    override fun load(root: Path): MixSnapshot {
        val normalized = root.normalizeRoot()
        val settings = settings(normalized)
        val stems = availableStems(normalized)
        val dry = normalized.resolve("mix/dry.wav").takeIf(Files::isRegularFile)
        return MixSnapshot(normalized, settings, stems, dry, stale = stems.isEmpty())
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
                val stems = availableStems(root)
                require(stems.isNotEmpty()) { "No rendered stems found. Render the approved arrangement first." }
                progress.report(OperationProgress("mix", 1, 2, "Validating rendered stems"))
                val hasSolo = stems.any { request.settings.tracks[it]?.solo == true }
                val tracks = stems.map { name ->
                    val setting = request.settings.tracks[name] ?: LogicalMixSetting()
                    MixTrack(
                        name = name,
                        buffer = WAVDecoder(noOpErrorReporter).decode(root.resolve("stems/$name.wav")),
                        gainDb = setting.gainDb,
                        pan = setting.pan,
                        muted = setting.muted || (hasSolo && !setting.solo),
                        generated = name != "piano"
                    )
                }
                progress.report(OperationProgress("mix", 2, 2, "Writing lossless dry mix", root.resolve("mix/dry.wav")))
                val mixed = mixer.mix(tracks, MixSettings(requiredFormat = format, peakCeiling = 0.95))
                val output = root.resolve("mix/dry.wav")
                mixer.writeWav(mixed, output)
                writeSettings(root, request.settings)
                load(root)
            }
        } catch (error: ApplicationServiceException) {
            throw error
        } catch (error: Throwable) {
            throw ApplicationServiceException(if (error is java.io.IOException) ApplicationErrorCategory.IO else ApplicationErrorCategory.ARTIFACT, error.message ?: "Mix failed", error)
        } finally { lock.unlock() }
    }

    private fun settings(root: Path): PersistedMixSettings {
        val path = root.resolve(SETTINGS_FILE)
        if (!Files.isRegularFile(path)) return PersistedMixSettings()
        return json.decodeFromString(PersistedMixSettings.serializer(), Files.readString(path, StandardCharsets.UTF_8)).also { it.requireValid() }
    }

    private fun writeSettings(root: Path, settings: PersistedMixSettings) {
        val target = root.resolve(SETTINGS_FILE)
        Files.createDirectories(requireNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, json.encodeToString(settings), StandardCharsets.UTF_8)
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun availableStems(root: Path): List<String> = LogicalInstrument.entries.map { it.wireName }.filter { Files.isRegularFile(root.resolve("stems/$it.wav")) }
    private fun Path.normalizeRoot(): Path = toAbsolutePath().normalize()

    private companion object {
        const val SETTINGS_FILE = "mix/settings.json"
        val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
        val locks = ConcurrentHashMap<Path, Mutex>()
        val noOpErrorReporter = object : ErrorReporter {
            override fun report(message: String) = Unit
            override fun report(message: String, cause: Throwable) = Unit
        }
    }
}

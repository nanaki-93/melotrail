package app.melotrail.application

import app.melotrail.audio.WAVDecoder
import app.melotrail.arrangement.ProjectStore
import app.melotrail.arrangement.WorkflowArtifact
import app.melotrail.model.ErrorReporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

/** The only final-user formats currently supported by the desktop release flow. */
enum class ReleaseExportFormat(val extension: String) { WAV("wav"), MP3("mp3") }

data class ReleaseExportSummary(
    val master: Path,
    val durationSeconds: Double,
    val sampleRate: Int,
    val channels: Int,
    val pcmBitDepth: Int,
    val trackCount: Int
)

data class ReleaseExportInspection(
    val summary: ReleaseExportSummary?,
    val supportedFormats: Set<ReleaseExportFormat>,
    val blockedReason: String? = null
) {
    val ready: Boolean get() = summary != null && blockedReason == null
}

data class ReleaseExportRequest(
    val root: Path,
    val format: ReleaseExportFormat,
    val filename: String,
    /** A user-facing destination is constrained to the canonical project output directory. */
    val destination: Path,
    val mp3BitrateKbps: Int = 320
)

data class ReleaseExportResult(val output: Path, val format: ReleaseExportFormat)

/** Adapter boundary for the optional local encoder. It must never receive an unchecked path. */
interface ReleaseMp3Exporter {
    suspend fun available(): Boolean
    suspend fun export(input: Path, output: Path, bitrateKbps: Int): Boolean
}

/** Filesystem boundary kept deliberately small so export coordination stays deterministic and testable. */
interface ReleaseExportFilesystem {
    fun loadValidatedRelease(root: Path): ReleaseExportSummary
    fun createDirectories(path: Path)
    fun exists(path: Path): Boolean
    fun temporarySibling(target: Path): Path
    fun copy(source: Path, target: Path)
    fun moveAtomically(source: Path, target: Path)
    fun deleteIfExists(path: Path)
    fun validateOutput(path: Path, format: ReleaseExportFormat)
    fun digest(path: Path): String
}

interface ReleaseExportApplicationService {
    suspend fun inspect(root: Path): ReleaseExportInspection
    suspend fun export(request: ReleaseExportRequest): ReleaseExportResult
}

/**
 * Coordinates a final copy/conversion only after the canonical release is measured and validated.
 * It neither re-masters nor changes project release metadata.
 */
class DefaultReleaseExportApplicationService(
    private val filesystem: ReleaseExportFilesystem = NioReleaseExportFilesystem(),
    private val mp3Exporter: ReleaseMp3Exporter? = null
) : ReleaseExportApplicationService {
    override suspend fun inspect(root: Path): ReleaseExportInspection = try {
        val summary = filesystem.loadValidatedRelease(normalizeRoot(root))
        val formats = buildSet {
            add(ReleaseExportFormat.WAV)
            if (mp3Exporter?.available() == true) add(ReleaseExportFormat.MP3)
        }
        ReleaseExportInspection(summary, formats)
    } catch (failure: Throwable) {
        ReleaseExportInspection(null, emptySet(), failure.message ?: "Build a current master and release metadata first.")
    }

    override suspend fun export(request: ReleaseExportRequest): ReleaseExportResult {
        val root = normalizeRoot(request.root)
        val summary = filesystem.loadValidatedRelease(root)
        val target = validatedTarget(root, request.destination, request.filename, request.format)
        require(request.format == ReleaseExportFormat.WAV || request.mp3BitrateKbps in MP3_BITRATES) {
            "MP3 bitrate must be one of ${MP3_BITRATES.sorted().joinToString()} kbps"
        }
        require(target != summary.master) { "Export must not overwrite the authoritative master.wav" }
        require(!filesystem.exists(target)) { "Export target already exists; choose a new filename." }
        val masterDigest = filesystem.digest(summary.master)
        val temporary = filesystem.temporarySibling(target)
        try {
            filesystem.createDirectories(target.parent)
            when (request.format) {
                ReleaseExportFormat.WAV -> filesystem.copy(summary.master, temporary)
                ReleaseExportFormat.MP3 -> {
                    val exporter = requireNotNull(mp3Exporter) { "MP3 export is unavailable. Start the local worker with lameenc installed." }
                    require(exporter.available()) { "MP3 export is unavailable. Start the local worker with lameenc installed." }
                    require(exporter.export(summary.master, temporary, request.mp3BitrateKbps)) { "MP3 export is unavailable. Install lameenc in the local worker environment." }
                }
            }
            filesystem.validateOutput(temporary, request.format)
            require(filesystem.digest(summary.master) == masterDigest) { "Export changed the authoritative master; the result was discarded." }
            filesystem.moveAtomically(temporary, target)
            filesystem.validateOutput(target, request.format)
            require(filesystem.digest(summary.master) == masterDigest) { "Export changed the authoritative master; the result was discarded." }
            return ReleaseExportResult(target, request.format)
        } finally {
            filesystem.deleteIfExists(temporary)
        }
    }

    private fun normalizeRoot(root: Path): Path = root.toAbsolutePath().normalize()

    private fun validatedTarget(root: Path, destination: Path, filename: String, format: ReleaseExportFormat): Path {
        require(filename.isNotBlank() && filename == filename.trim()) { "Export filename is required." }
        require(!filename.contains('/') && !filename.contains('\\') && filename != "." && filename != "..") { "Export filename must not contain a path." }
        require(filename.substringAfterLast('.', "").equals(format.extension, ignoreCase = true)) { "Export filename must end in .${format.extension}." }
        val outputRoot = root.resolve("output").normalize()
        val normalizedDestination = destination.toAbsolutePath().normalize()
        require(normalizedDestination == outputRoot) { "Export destination must be the project output folder." }
        val target = outputRoot.resolve(filename).normalize()
        require(target.parent == outputRoot && target.startsWith(root)) { "Export target escapes the project output folder." }
        require(target.fileName.toString().lowercase() !in PROTECTED_NAMES) { "Export must not overwrite a protected project artifact." }
        return target
    }

    private companion object {
        val MP3_BITRATES = setOf(128, 160, 192, 256, 320)
        val PROTECTED_NAMES = setOf("master.wav", "release.json", "project.json", "arrangement.json")
    }
}

/** NIO implementation validates metadata, fingerprints, and actual containers before exposing a release. */
class NioReleaseExportFilesystem : ReleaseExportFilesystem {
    override fun loadValidatedRelease(root: Path): ReleaseExportSummary {
        val project = root.resolve("project.json")
        val master = root.resolve("output/master.wav").normalize()
        val release = root.resolve("output/release.json").normalize()
        require(Files.isRegularFile(project)) { "Project metadata is unavailable." }
        val canonicalProject = ProjectStore.read(root)
        require(WorkflowArtifact.MASTER !in canonicalProject.workflow.stale && WorkflowArtifact.RELEASE !in canonicalProject.workflow.stale) {
            "Master artifacts are stale. Build Song again."
        }
        require(Files.isRegularFile(master) && Files.isRegularFile(release)) { "Build a current master and release metadata first." }
        val metadata = Json.parseToJsonElement(Files.readString(release)).jsonObject
        fun string(name: String) = metadata[name]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Release metadata is missing $name.")
        fun int(name: String) = metadata[name]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("Release metadata is missing $name.")
        fun long(name: String) = metadata[name]?.jsonPrimitive?.longOrNull ?: throw IllegalArgumentException("Release metadata is missing $name.")
        fun double(name: String) = metadata[name]?.jsonPrimitive?.doubleOrNull ?: throw IllegalArgumentException("Release metadata is missing $name.")
        require(string("master").equals("master.wav", ignoreCase = true)) { "Release metadata does not identify the canonical master.wav." }
        require(string("masterFingerprint") == digest(master)) { "Master WAV does not match current release metadata. Build Song again." }
        val audio = WAVDecoder(NoOpErrorReporter).decode(master)
        require(audio.format.bitDepth == 24 && audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() }) { "Master WAV is not a valid PCM-24 release artifact." }
        val sampleRate = int("sampleRate")
        val channels = int("channels")
        val bitDepth = int("pcmBitDepth")
        val frames = long("frameCount")
        val duration = double("durationSeconds")
        require(sampleRate == audio.format.sampleRate && channels == audio.format.channels && bitDepth == audio.format.bitDepth && frames == audio.length.toLong()) {
            "Master WAV does not match measured release metadata. Build Song again."
        }
        require(abs(duration - audio.duration) <= 0.001) { "Master WAV duration does not match release metadata. Build Song again." }
        val tracks = canonicalProject.parts.size
        return ReleaseExportSummary(master, duration, sampleRate, channels, bitDepth, tracks)
    }

    override fun createDirectories(path: Path) = Files.createDirectories(path).let { Unit }
    override fun exists(path: Path): Boolean = Files.exists(path)
    override fun temporarySibling(target: Path): Path = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.${target.fileName.toString().substringAfterLast('.', "tmp")}")
    override fun copy(source: Path, target: Path) = Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING).let { Unit }
    override fun moveAtomically(source: Path, target: Path) {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING) }
    }
    override fun deleteIfExists(path: Path) = Files.deleteIfExists(path).let { Unit }
    override fun validateOutput(path: Path, format: ReleaseExportFormat) {
        require(Files.isRegularFile(path) && Files.size(path) > 0) { "Export did not produce an output file." }
        when (format) {
            ReleaseExportFormat.WAV -> {
                val audio = WAVDecoder(NoOpErrorReporter).decode(path)
                require(audio.format.bitDepth == 24 && audio.samples.isNotEmpty() && audio.samples.all { it.isFinite() }) { "Export did not produce a valid PCM-24 WAV." }
            }
            ReleaseExportFormat.MP3 -> {
                val header = Files.newInputStream(path).use { it.readNBytes(3) }
                require(header.decodeToString() == "ID3" || (header.size == 3 && header[0].toInt() and 0xFF == 0xFF && header[1].toInt() and 0xE0 == 0xE0)) {
                    "Export did not produce a valid MP3 container."
                }
            }
        }
    }
    override fun digest(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    private object NoOpErrorReporter : ErrorReporter { override fun report(message: String) = Unit; override fun report(message: String, cause: Throwable) = Unit }
}

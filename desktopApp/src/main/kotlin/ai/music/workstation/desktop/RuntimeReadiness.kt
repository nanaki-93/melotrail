package ai.music.workstation.desktop

import ai.music.workstation.arrangement.InstrumentRegistryLoader
import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.WorkerClient
import ai.music.workstation.worker.WorkerRuntimeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

enum class DependencyStatus { CHECKING, READY, UNAVAILABLE, FAILED }
enum class RecoveryAction { START_WORKER, INSTALL_BASIC_PITCH, CHOOSE_SOUND_LIBRARY, INSTALL_SAMPLES, CONFIGURE_RENDERER, CHECK_AUDIO_OUTPUT }
enum class RuntimeDependency { WORKER, TRANSCRIPTION, SOUND_LIBRARY, SAMPLES, RENDERER, AUDIO_OUTPUT }
enum class RuntimeCapability { AUDIO_IMPORT, SOURCE_PREVIEW, MIDI_PREVIEW, ARRANGEMENT_RENDER, BUILD_SONG }

data class DependencyReadiness(
    val status: DependencyStatus,
    val detail: String,
    val recoveryAction: RecoveryAction? = null,
    /** Safe identifiers/versions only; never source paths or project content. */
    val diagnostics: Map<String, String> = emptyMap()
) {
    val available: Boolean get() = status == DependencyStatus.READY
}

data class CapabilityReadiness(val available: Boolean, val reason: String? = null)

data class RuntimeReadiness(private val values: Map<RuntimeDependency, DependencyReadiness>) {
    val worker get() = values.getValue(RuntimeDependency.WORKER)
    val transcription get() = values.getValue(RuntimeDependency.TRANSCRIPTION)
    val soundLibrary get() = values.getValue(RuntimeDependency.SOUND_LIBRARY)
    val samples get() = values.getValue(RuntimeDependency.SAMPLES)
    val renderer get() = values.getValue(RuntimeDependency.RENDERER)
    val audioOutput get() = values.getValue(RuntimeDependency.AUDIO_OUTPUT)
    fun dependency(dependency: RuntimeDependency): DependencyReadiness = values.getValue(dependency)

    fun capability(capability: RuntimeCapability): CapabilityReadiness {
        val required = when (capability) {
            RuntimeCapability.AUDIO_IMPORT -> listOf(RuntimeDependency.WORKER, RuntimeDependency.TRANSCRIPTION)
            RuntimeCapability.SOURCE_PREVIEW -> listOf(RuntimeDependency.AUDIO_OUTPUT)
            RuntimeCapability.MIDI_PREVIEW -> listOf(RuntimeDependency.SOUND_LIBRARY, RuntimeDependency.SAMPLES, RuntimeDependency.RENDERER, RuntimeDependency.AUDIO_OUTPUT)
            RuntimeCapability.ARRANGEMENT_RENDER -> listOf(RuntimeDependency.SOUND_LIBRARY, RuntimeDependency.SAMPLES, RuntimeDependency.RENDERER)
            RuntimeCapability.BUILD_SONG -> listOf(RuntimeDependency.WORKER, RuntimeDependency.SOUND_LIBRARY, RuntimeDependency.SAMPLES, RuntimeDependency.RENDERER)
        }
        val missing = required.firstOrNull { !dependency(it).available }
        return if (missing == null) CapabilityReadiness(true) else CapabilityReadiness(false, dependency(missing).detail)
    }

    companion object {
        fun checking(): RuntimeReadiness = RuntimeReadiness(RuntimeDependency.entries.associateWith {
            DependencyReadiness(DependencyStatus.CHECKING, "Checking ${it.name.lowercase().replace('_', ' ')}…")
        })
        fun of(vararg entries: Pair<RuntimeDependency, DependencyReadiness>): RuntimeReadiness = RuntimeReadiness(entries.toMap())
    }
}

fun interface RuntimeReadinessService { suspend fun check(): RuntimeReadiness }

class LocalRuntimeReadinessService(
    private val workerProbe: suspend () -> WorkerRuntimeStatus,
    private val libraryRoot: () -> Path?,
    private val environment: Map<String, String> = System.getenv(),
    private val audioOutputProbe: () -> Boolean = ::audioOutputAvailable
) : RuntimeReadinessService {
    override suspend fun check(): RuntimeReadiness = withContext(Dispatchers.IO) {
        val worker = runCatching { workerProbe() }.getOrElse { WorkerRuntimeStatus(false, false) }
        val workerReadiness = if (worker.reachable) DependencyReadiness(DependencyStatus.READY, "Worker ready", diagnostics = worker.version?.let { mapOf("version" to it) } ?: emptyMap())
        else DependencyReadiness(DependencyStatus.UNAVAILABLE, "Start the Python worker with make worker.", RecoveryAction.START_WORKER)
        val transcription = if (!worker.reachable) DependencyReadiness(DependencyStatus.UNAVAILABLE, "Transcription needs the running Python worker.", RecoveryAction.START_WORKER)
        else if (worker.transcriptionAvailable) DependencyReadiness(DependencyStatus.READY, "Basic Pitch runtime ready")
        else DependencyReadiness(DependencyStatus.UNAVAILABLE, "Install Basic Pitch with worker/requirements-transcription.txt in Python 3.11.", RecoveryAction.INSTALL_BASIC_PITCH)
        val (library, samples) = libraryReadiness(libraryRoot())
        val renderer = rendererReadiness()
        val audioOutput = if (runCatching(audioOutputProbe).getOrDefault(false)) DependencyReadiness(DependencyStatus.READY, "Audio output ready")
        else DependencyReadiness(DependencyStatus.UNAVAILABLE, "No audio output device is available. Check the selected output device and retry.", RecoveryAction.CHECK_AUDIO_OUTPUT)
        RuntimeReadiness.of(
            RuntimeDependency.WORKER to workerReadiness, RuntimeDependency.TRANSCRIPTION to transcription,
            RuntimeDependency.SOUND_LIBRARY to library, RuntimeDependency.SAMPLES to samples,
            RuntimeDependency.RENDERER to renderer, RuntimeDependency.AUDIO_OUTPUT to audioOutput
        )
    }

    private fun libraryReadiness(root: Path?): Pair<DependencyReadiness, DependencyReadiness> {
        if (root == null || !Files.isDirectory(root)) return DependencyReadiness(DependencyStatus.UNAVAILABLE, "Choose a validated sound-library folder.", RecoveryAction.CHOOSE_SOUND_LIBRARY) to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Samples require a valid sound library.", RecoveryAction.CHOOSE_SOUND_LIBRARY)
        val result = runCatching { InstrumentRegistryLoader(root).load() }
        if (result.isSuccess) return DependencyReadiness(DependencyStatus.READY, "Sound library registry ready") to DependencyReadiness(DependencyStatus.READY, "All registry samples are present")
        val message = result.exceptionOrNull()?.message.orEmpty()
        return if (message.contains("sample", ignoreCase = true) || message.contains("WAV", ignoreCase = true)) {
            DependencyReadiness(DependencyStatus.READY, "Sound library registry ready") to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Copy the approved local starter samples into the selected sound library.", RecoveryAction.INSTALL_SAMPLES)
        } else DependencyReadiness(DependencyStatus.FAILED, "Sound-library registry is invalid. Choose a validated folder.", RecoveryAction.CHOOSE_SOUND_LIBRARY) to DependencyReadiness(DependencyStatus.UNAVAILABLE, "Samples cannot be checked until the registry is valid.", RecoveryAction.CHOOSE_SOUND_LIBRARY)
    }

    private fun rendererReadiness(): DependencyReadiness {
        val executable = environment["SFZ_RENDERER_PATH"]?.takeIf(String::isNotBlank)?.let { runCatching { Path.of(it) }.getOrNull() }
            ?: environment["PATH"].orEmpty().split(File.pathSeparator).asSequence().mapNotNull { runCatching { Path.of(it).resolve("sfizz_render") }.getOrNull() }.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
        if (executable == null || !Files.isRegularFile(executable) || !Files.isExecutable(executable)) return DependencyReadiness(DependencyStatus.UNAVAILABLE, "Set SFZ_RENDERER_PATH to an executable sfizz_render.", RecoveryAction.CONFIGURE_RENDERER)
        val version = runCatching {
            val process = ProcessBuilder(executable.toString(), "--version").redirectErrorStream(true).start()
            require(process.waitFor(2, TimeUnit.SECONDS)) { "Renderer version check timed out" }
            require(process.exitValue() == 0) { "Renderer version check failed" }
            process.inputStream.bufferedReader().use { it.readLine()?.take(80) ?: "unknown" }
        }.getOrElse { return DependencyReadiness(DependencyStatus.FAILED, "Renderer is executable but its version could not be verified.", RecoveryAction.CONFIGURE_RENDERER) }
        return DependencyReadiness(DependencyStatus.READY, "SFZ renderer ready", diagnostics = mapOf("version" to version))
    }
}

private fun audioOutputAvailable(): Boolean = runCatching {
    AudioSystem.getSourceDataLine(AudioFormat(44_100f, 16, 2, true, false)) != null
}.getOrDefault(false)

fun defaultRuntimeReadinessService(libraryRoot: () -> Path? = { null }): RuntimeReadinessService {
    val logger = DefaultLogger()
    val client = WorkerClient(System.getenv("WORKER_BASE_URL")?.takeIf(String::isNotBlank) ?: "http://127.0.0.1:8081", logger = logger, errorReporter = ErrorReporter(logger))
    return LocalRuntimeReadinessService(workerProbe = { client.runtimeStatus() }, libraryRoot = libraryRoot)
}

package ai.music.workstation.desktop

import ai.music.workstation.errors.ErrorReporter
import ai.music.workstation.logging.DefaultLogger
import ai.music.workstation.worker.WorkerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/** Typed local dependency check kept outside the view model and composables. */
interface RuntimeReadinessService {
    suspend fun check(): RuntimeReadiness
}

data class RuntimeReadiness(
    val worker: DependencyReadiness,
    val renderer: DependencyReadiness
)

data class DependencyReadiness(val available: Boolean, val detail: String)

class LocalRuntimeReadinessService(
    private val workerHealthCheck: suspend () -> Boolean,
    private val environment: Map<String, String> = System.getenv()
) : RuntimeReadinessService {
    override suspend fun check(): RuntimeReadiness = withContext(Dispatchers.IO) {
        RuntimeReadiness(
            worker = if (workerHealthCheck()) {
                DependencyReadiness(true, "Worker ready")
            } else {
                DependencyReadiness(false, "Start the Python worker with make worker for audio import.")
            },
            renderer = rendererReadiness()
        )
    }

    private fun rendererReadiness(): DependencyReadiness {
        val configured = environment["SFZ_RENDERER_PATH"]?.takeIf(String::isNotBlank)
        if (configured != null) {
            val path = runCatching { Path.of(configured) }.getOrNull()
            return if (path != null && Files.isRegularFile(path) && Files.isExecutable(path)) {
                DependencyReadiness(true, "SFZ renderer ready")
            } else {
                DependencyReadiness(false, "SFZ_RENDERER_PATH does not point to an executable renderer.")
            }
        }
        val foundOnPath = environment["PATH"].orEmpty().split(java.io.File.pathSeparator)
            .mapNotNull { runCatching { Path.of(it).resolve("sfizz_render") }.getOrNull() }
            .any { Files.isRegularFile(it) && Files.isExecutable(it) }
        return if (foundOnPath) DependencyReadiness(true, "sfizz_render ready")
        else DependencyReadiness(false, "Set SFZ_RENDERER_PATH to sfizz_render before previewing or rendering.")
    }
}

fun defaultRuntimeReadinessService(): RuntimeReadinessService {
    val logger = DefaultLogger()
    val client = WorkerClient(
        baseUrl = System.getenv("WORKER_BASE_URL")?.takeIf { it.isNotBlank() } ?: "http://127.0.0.1:8081",
        logger = logger,
        errorReporter = ErrorReporter(logger)
    )
    return LocalRuntimeReadinessService(client::healthCheck)
}

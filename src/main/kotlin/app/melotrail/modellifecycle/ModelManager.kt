package app.melotrail.modellifecycle

import app.melotrail.logging.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.nio.file.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files

@Serializable
data class ModelRegistry(
    @SerialName("version")
    val version: Int = 1,
    @SerialName("models")
    val models: List<ModelManifest> = emptyList()
)

interface WorkerClient {
    suspend fun loadModel(name: String, version: String): Result<Unit>
    suspend fun unloadModel(name: String, version: String): Result<Unit>
    suspend fun getModelInfo(name: String, version: String): Result<ModelManifest>
}

class ModelManager(
    private val modelDir: Path,
    private val workerClient: WorkerClient,
    private val logger: Logger
) {
    private val modelHandles: MutableMap<String, ModelHandle> = mutableMapOf()
    private val modelManifests: MutableMap<String, ModelManifest> = mutableMapOf()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        discoverModels()
    }

    private fun discoverModels() {
        if (!Files.exists(modelDir)) return

        Files.list(modelDir).use { streams ->
            streams.filter { Files.isDirectory(it) }.forEach { modelFolder ->
                val modelName = modelFolder.fileName.toString()
                Files.list(modelFolder).use { versionStreams ->
                    versionStreams.filter { Files.isDirectory(it) }.forEach { versionFolder ->
                        val versionName = versionFolder.fileName.toString()
                        val manifest = scanModelFolder(modelName, versionName, versionFolder)
                        modelManifests["$modelName/$versionName"] = manifest
                    }
                }
            }
        }
    }

    private fun scanModelFolder(
        name: String,
        version: String,
        folder: Path
    ): ModelManifest {
        val modelFile = folder.resolve("model.safetensors")
        val fileSize = if (Files.exists(modelFile)) Files.size(modelFile) else 0L
        val estimatedMemoryGB = fileSize.toDouble() / (1024.0 * 1024.0 * 1024.0) * 3.5

        return ModelManifest(
            name = name,
            version = version,
            fileSize = fileSize,
            estimatedMemoryGB = estimatedMemoryGB,
            installed = Files.exists(modelFile),
            installedAt = if (Files.exists(modelFile)) Clock.System.now() else null
        )
    }

    suspend fun loadModel(name: String, version: String): ModelHandle {
        val id = "$name/$version"
        val manifest = modelManifests[id] ?: run {
            logger.error("ModelManager", "Model not found: $id")
            return ModelHandle(name, version, 0, Clock.System.now(), ModelState.ERROR)
        }

        if (!manifest.installed) {
            logger.warning("ModelManager", "Model not installed: $id")
            return ModelHandle(name, version, 0, Clock.System.now(), ModelState.ERROR)
        }

        val existing = modelHandles[id]
        if (existing != null && existing.state == ModelState.LOADED) {
            existing.incrementReference()
            logger.info("ModelManager", "Model already loaded, ref count: ${existing.referenceCount}")
            return existing
        }

        // Loading
        val handle = ModelHandle(name, version, 0, Clock.System.now(), ModelState.LOADING)
        modelHandles[id] = handle

        logger.info("ModelManager", "Loading model: $id")
        val result = workerClient.loadModel(name, version)

        return if (result.isSuccess) {
            handle.state = ModelState.LOADED
            handle.incrementReference()
            logger.info("ModelManager", "Model loaded: $id")
            handle
        } else {
            handle.state = ModelState.ERROR
            logger.error("ModelManager", "Failed to load model: $id - ${result.exceptionOrNull()?.message}")
            handle
        }
    }

    suspend fun unloadModel(handle: ModelHandle) {
        val id = handle.id
        handle.decrementReference()

        if (handle.referenceCount > 0) {
            logger.info("ModelManager", "Model still in use, ref count: ${handle.referenceCount}")
            return
        }

        logger.info("ModelManager", "Unloading model: $id")
        handle.state = ModelState.UNLOADING

        val result = workerClient.unloadModel(handle.name, handle.version)

        if (result.isSuccess) {
            handle.state = ModelState.UNLOADED
            modelHandles.remove(id)
            logger.info("ModelManager", "Model unloaded: $id")
        } else {
            handle.state = ModelState.ERROR
            logger.error("ModelManager", "Failed to unload model: $id")
        }
    }

    fun getModelState(name: String, version: String): ModelState {
        val handle = modelHandles["$name/$version"]
        return handle?.state ?: ModelState.IDLE
    }

    fun getMemoryUsage(): Double {
        return modelHandles.values
            .filter { it.state == ModelState.LOADED }
            .sumOf { manifest ->
                modelManifests[manifest.id]?.estimatedMemoryGB ?: 0.0
            }
    }

    fun getAvailableModels(): List<ModelManifest> = modelManifests.values.toList()

    suspend fun warmupModel(name: String, version: String) {
        val handle = loadModel(name, version)
        if (handle.state == ModelState.LOADED) {
            unloadModel(handle)
        }
    }

    fun getModelManifest(name: String, version: String): ModelManifest? {
        return modelManifests["$name/$version"]
    }

    fun listInstalledModels(): List<ModelManifest> {
        return modelManifests.values.filter { it.installed }
    }
}

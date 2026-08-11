package ai.music.workstation.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class AppConfigSection(
    val name: String = "AI Music Workstation",
    val version: String = "1.0.0",
    val defaultProjectPath: String = "projects",
    val autoSaveInterval: Int = 60
)

@Serializable
data class ModelConfig(
    val modelDir: String = "models",
    val maxMemoryGB: Int = 16,
    val autoLoadOnStartup: Boolean = false,
    val allowedModels: List<String> = emptyList()
)

@Serializable
data class ExportConfig(
    val defaultFormat: String = "WAV",
    val defaultSampleRate: Int = 48000,
    val defaultBitDepth: Int = 24,
    val defaultFloat: Boolean = false
)

class ConfigManager(private val configPath: Path) {
    private val json = Json { ignoreUnknownKeys = true }
    private var config: AppConfig = AppConfig()

    init {
        load()
    }

    fun load(): AppConfig {
        if (Files.exists(configPath)) {
            val content = Files.readString(configPath)
            config = json.decodeFromString(AppConfig.serializer(), content)
        }
        return config
    }

    fun save() {
        Files.createDirectories(configPath.parent)
        Files.writeString(configPath, json.encodeToString(AppConfig.serializer(), config))
    }

    fun getModelDir(): Path = Path.of(config.models.defaultPath)
    fun getCacheDir(): Path = Path.of("projects", "cache")
    fun getExportDir(): Path = Path.of("projects", "exports")
    fun getConfigPath(): Path = configPath
}

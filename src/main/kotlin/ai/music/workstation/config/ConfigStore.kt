package ai.music.workstation.config

import ai.music.workstation.errors.AppError
import ai.music.workstation.errors.ErrorCategory
import ai.music.workstation.model.ErrorReporter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class ConfigStore(
    private val configPath: Path,
    private val errorReporter: ErrorReporter
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var _config: AppConfig = loadOrCreateDefault()

    fun getConfig(): AppConfig = _config

    fun loadOrCreateDefault(): AppConfig {
        return try {
            if (Files.exists(configPath)) {
                val jsonStr = Files.readString(configPath)
                val config = json.decodeFromString<AppConfig>(jsonStr)
                migrate(config)
            } else {
                val defaultConfig = AppConfig()
                save(defaultConfig)
                defaultConfig
            }
        } catch (e: Exception) {
            errorReporter.report("Failed to load config: ${e.message}", e)
            AppConfig()
        }
    }

    fun save(config: AppConfig) {
        try {
            Files.createDirectories(configPath.parent)
            val jsonStr = json.encodeToString(config)
            Files.writeString(configPath, jsonStr)
            _config = config
        } catch (e: Exception) {
            errorReporter.report("Failed to save config: ${e.message}", e)
        }
    }

    fun updateAudioConfig(update: (AudioConfig) -> AudioConfig) {
        val newAudio = update(_config.audio)
        _config = _config.copy(audio = newAudio, lastUpdated = kotlinx.datetime.Clock.System.now())
        save(_config)
    }

    fun updateWorkerConfig(update: (WorkerConfig) -> WorkerConfig) {
        val newWorker = update(_config.worker)
        _config = _config.copy(worker = newWorker, lastUpdated = kotlinx.datetime.Clock.System.now())
        save(_config)
    }

    fun updateUIConfig(update: (UIConfig) -> UIConfig) {
        val newUI = update(_config.ui)
        _config = _config.copy(ui = newUI, lastUpdated = kotlinx.datetime.Clock.System.now())
        save(_config)
    }

    private fun migrate(config: AppConfig): AppConfig {
        return when (config.configVersion) {
            1 -> config
            else -> {
                errorReporter.report("Unknown config version: ${config.configVersion}")
                AppConfig()
            }
        }
    }

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (_config.audio.sampleRate !in listOf(22050, 44100, 48000, 96000)) {
            errors.add("Invalid sample rate: ${_config.audio.sampleRate}")
        }
        if (_config.audio.bitDepth !in listOf(16, 24, 32)) {
            errors.add("Invalid bit depth: ${_config.audio.bitDepth}")
        }
        if (_config.audio.bufferSize !in listOf(64, 128, 256, 512, 1024, 2048, 4096)) {
            errors.add("Invalid buffer size: ${_config.audio.bufferSize}")
        }
        return errors
    }
}

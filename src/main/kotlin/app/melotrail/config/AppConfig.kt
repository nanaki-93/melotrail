package app.melotrail.config

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val configVersion: Int = 1,
    val audio: AudioConfig = AudioConfig(),
    val worker: WorkerConfig = WorkerConfig(),
    val cache: CacheConfig = CacheConfig(),
    val ui: UIConfig = UIConfig(),
    val models: ModelsConfig = ModelsConfig(),
    val lastUpdated: Instant = kotlinx.datetime.Clock.System.now()
)

@Serializable
data class AudioConfig(
    val sampleRate: Int = 48000,
    val bitDepth: Int = 24,
    val bufferSize: Int = 256,
    val outputDevice: String? = null
)

@Serializable
data class WorkerConfig(
    val workerUrl: String = "http://localhost:8081",
    val timeoutSeconds: Int = 300,
    val maxRetries: Int = 3,
    val logLevel: String = "INFO"
)

@Serializable
data class CacheConfig(
    val maxSizeGB: Int = 10,
    val cleanupIntervalDays: Int = 7
)

@Serializable
data class UIConfig(
    val theme: String = "dark",
    val language: String = "en"
)

@Serializable
data class ModelsConfig(
    val defaultPath: String = ""
)

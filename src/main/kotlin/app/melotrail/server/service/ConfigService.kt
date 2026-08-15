package app.melotrail.server.service

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class ServerConfigDTO(
    val port: Int,
    val host: String,
    val workerBaseUrl: String,
    val projectStoragePath: String,
    val audioStoragePath: String,
    val updatedAt: String
) {
    @Serializable
    data class Update(
        val port: Int? = null,
        val host: String? = null,
        val workerBaseUrl: String? = null,
        val projectStoragePath: String? = null,
        val audioStoragePath: String? = null
    )
}

@Service
class ConfigService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val configPath: Path = Paths.get("data/config/server-config.json")

    init { Files.createDirectories(configPath.parent) }

    @Synchronized
    fun getConfig(): ServerConfigDTO {
        if (Files.exists(configPath)) {
            try { return json.decodeFromString<ServerConfigDTO>(Files.readString(configPath)) }
            catch (_: Exception) {}
        }
        return defaults()
    }

    @Synchronized
    fun updateConfig(update: ServerConfigDTO.Update): ServerConfigDTO {
        val current = getConfig()
        val updated = current.copy(
            port = update.port ?: current.port,
            host = update.host ?: current.host,
            workerBaseUrl = update.workerBaseUrl ?: current.workerBaseUrl,
            projectStoragePath = update.projectStoragePath ?: current.projectStoragePath,
            audioStoragePath = update.audioStoragePath ?: current.audioStoragePath,
            updatedAt = Clock.System.now().toString()
        )
        validateConfig(updated)
        persist(updated)
        return updated
    }

    private fun validateConfig(config: ServerConfigDTO) {
        require(config.port in 1..65535) { "Port must be between 1 and 65535" }
        require(config.host.isNotBlank()) { "Host cannot be blank" }
        require(config.workerBaseUrl.isNotBlank()) { "Worker base URL cannot be blank" }
        require(config.projectStoragePath.isNotBlank()) { "Project storage path cannot be blank" }
        require(config.audioStoragePath.isNotBlank()) { "Audio storage path cannot be blank" }
    }

    private fun persist(config: ServerConfigDTO) {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, json.encodeToString(config))
        } catch (e: Exception) { throw RuntimeException("Failed to persist configuration: ${e.message}", e) }
    }

    private fun defaults() = ServerConfigDTO(
        8080, "localhost", "http://localhost:8081",
        "data/projects", "data/audio", Clock.System.now().toString()
    )
}

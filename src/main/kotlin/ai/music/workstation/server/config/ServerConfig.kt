package ai.music.workstation.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.nio.file.Paths

@ConfigurationProperties(prefix = "server")
data class ServerConfig(
    val port: Int = 8080,
    val host: String = "localhost",
    val workerBaseUrl: String = "http://localhost:8081",
    val projectStoragePath: Path = Paths.get("data/projects"),
    val audioStoragePath: Path = Paths.get("data/audio")
)

package ai.music.workstation.server.service

import ai.music.workstation.server.config.ServerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AudioServiceConfig {
    @Bean
    fun audioService(config: ServerConfig) = AudioService(config.audioStoragePath)
}

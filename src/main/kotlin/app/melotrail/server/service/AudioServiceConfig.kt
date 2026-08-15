package app.melotrail.server.service

import app.melotrail.server.config.ServerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AudioServiceConfig {
    @Bean
    fun audioService(config: ServerConfig) = AudioService(config.audioStoragePath)
}

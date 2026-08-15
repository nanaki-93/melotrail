package app.melotrail.server.service

import app.melotrail.server.config.ServerConfig
import app.melotrail.model.ProjectService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProjectServiceConfig {
    @Bean
    fun projectService(config: ServerConfig): ProjectService =
        ProjectServiceAdapter(config.projectStoragePath)
}

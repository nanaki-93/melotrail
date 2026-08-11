package ai.music.workstation.server.service

import ai.music.workstation.server.config.ServerConfig
import ai.music.workstation.model.ProjectService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProjectServiceConfig {
    @Bean
    fun projectService(config: ServerConfig): ProjectService =
        ProjectServiceAdapter(config.projectStoragePath)
}

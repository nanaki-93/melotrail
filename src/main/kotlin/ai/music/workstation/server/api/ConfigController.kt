package ai.music.workstation.server.api

import ai.music.workstation.server.service.ConfigService
import ai.music.workstation.server.service.ServerConfigDTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/config")
class ConfigController(private val service: ConfigService) {
    @GetMapping
    fun get() = service.getConfig()

    @PutMapping
    fun update(@RequestBody request: ServerConfigDTO.Update) = service.updateConfig(request)
}

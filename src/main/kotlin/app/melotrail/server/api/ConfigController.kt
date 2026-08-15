package app.melotrail.server.api

import app.melotrail.server.service.ConfigService
import app.melotrail.server.service.ServerConfigDTO
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/config")
class ConfigController(private val service: ConfigService) {
    @GetMapping
    fun get() = service.getConfig()

    @PutMapping
    fun update(@RequestBody request: ServerConfigDTO.Update) = service.updateConfig(request)
}

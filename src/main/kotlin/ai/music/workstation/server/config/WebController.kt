package ai.music.workstation.server.config

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SPA fallback for client-side routes. Static assets and / are served by
 * Spring Boot's default resource handler from classpath:/static/.
 */
@RestController
class WebController {
    @GetMapping("/project/{id}")
    fun project(): ResponseEntity<Resource> =
        ResponseEntity.ok(ClassPathResource("static/index.html"))
}

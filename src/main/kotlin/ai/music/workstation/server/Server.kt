package ai.music.workstation.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MusicWorkstationApplication

fun main(args: Array<String>) {
    runApplication<MusicWorkstationApplication>(*args)
}

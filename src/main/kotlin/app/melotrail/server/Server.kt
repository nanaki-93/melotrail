package app.melotrail.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MelotrailApplication

fun main(args: Array<String>) {
    runApplication<MelotrailApplication>(*args)
}

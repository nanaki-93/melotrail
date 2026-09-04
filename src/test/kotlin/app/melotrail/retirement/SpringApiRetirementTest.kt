package app.melotrail.retirement

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class SpringApiRetirementTest {
    private val repository = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()

    @Test
    fun `retirement removes the separate server authority and its build wiring`() {
        val serverSources = repository.resolve("src/main/kotlin/app/melotrail/server")
        assertFalse(
            Files.exists(serverSources) && Files.walk(serverSources).use { paths ->
                paths.anyMatch { path -> path.toString().endsWith(".kt") }
            },
            "Retired server sources remain"
        )

        listOf(
            "src/main/kotlin/app/melotrail/model/Project.kt",
            "src/main/kotlin/app/melotrail/model/ProjectTrack.kt",
            "src/main/kotlin/app/melotrail/model/ProjectServiceImpl.kt",
            "src/main/kotlin/app/melotrail/worker/WorkerJobService.kt",
            "src/main/resources/application.properties"
        ).forEach { path ->
            assertFalse(Files.exists(repository.resolve(path)), "Retired artifact remains: $path")
        }

        listOf("build.gradle.kts", "desktopApp/build.gradle.kts").forEach { path ->
            assertFalse(
                Files.readString(repository.resolve(path)).contains("spring", ignoreCase = true),
                "Spring build wiring remains in $path"
            )
        }
    }

}

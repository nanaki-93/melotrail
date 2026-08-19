package app.melotrail.retirement

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `legacy store disposition remains explicit and non destructive`() {
        val record = Files.readString(repository.resolve("docs/SPRING_API_RETIREMENT.md"))
        assertTrue(record.contains("data/projects/"))
        assertTrue(record.contains("data/audio/"))
        assertTrue(record.contains("data/config/server-config.json"))
        assertTrue(record.contains("never auto-imported"))
        assertTrue(record.contains("does not delete or write"))
    }
}

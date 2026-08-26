package app.melotrail.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/** Dependency policy for the target packages introduced by the MIDI Core migration. */
class TargetArchitectureRulesTest {
    @Test
    fun `target source tree obeys the MIDI Core dependency policy`() {
        val violations = TargetArchitectureRules.violations(TargetArchitectureRules.readProductionSources())

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `domain desktop and MIDI adapter violations are rejected`() {
        val violations = TargetArchitectureRules.violations(
            listOf(
                SourceFile("src/main/kotlin/app/melotrail/midi/domain/Sequence.kt", "import javax.sound.midi.Sequence"),
                SourceFile("src/main/kotlin/app/melotrail/project/ProjectStore.kt", "import java.nio.file.Path"),
                SourceFile("src/main/kotlin/app/melotrail/review/ReviewState.kt", "import androidx.compose.runtime.State"),
                SourceFile("src/main/kotlin/app/melotrail/structure/Planner.kt", "import java.net.URI"),
                SourceFile("desktopApp/src/main/kotlin/app/melotrail/desktop/target/MidiPage.kt", "import javax.sound.midi.MidiSystem"),
                SourceFile("src/main/kotlin/app/melotrail/midi/adapter/JdkMidiReader.kt", "import javax.sound.midi.MidiSystem"),
            ),
        )

        assertEquals(
            listOf(
                "src/main/kotlin/app/melotrail/midi/domain/Sequence.kt: domain code may not import javax.sound.midi",
                "src/main/kotlin/app/melotrail/project/ProjectStore.kt: domain code may not import java.nio.file",
                "src/main/kotlin/app/melotrail/review/ReviewState.kt: domain code may not import androidx.compose",
                "src/main/kotlin/app/melotrail/structure/Planner.kt: domain code may not import java.net",
                "desktopApp/src/main/kotlin/app/melotrail/desktop/target/MidiPage.kt: desktop code may not parse raw MIDI",
            ),
            violations,
        )
    }
}

private data class SourceFile(val path: String, val contents: String)

private object TargetArchitectureRules {
    private val domainRoots = listOf(
        "src/main/kotlin/app/melotrail/project/",
        "src/main/kotlin/app/melotrail/midi/domain/",
        "src/main/kotlin/app/melotrail/music/core/",
        "src/main/kotlin/app/melotrail/structure/",
        "src/main/kotlin/app/melotrail/arrangement/core/",
        "src/main/kotlin/app/melotrail/review/",
        "src/main/kotlin/app/melotrail/export/domain/",
    )
    private const val desktopTargetRoot = "desktopApp/src/main/kotlin/app/melotrail/desktop/target/"
    private val forbiddenDomainImports = listOf("androidx.compose", "java.io", "java.net", "java.nio.file", "okhttp", "javax.sound.midi")

    fun readProductionSources(): List<SourceFile> = listOf(Path.of("src/main/kotlin"), Path.of("desktopApp/src/main/kotlin"))
        .filter(Files::isDirectory)
        .flatMap { root -> Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.extension == "kt" }.map { path ->
            SourceFile(path.toString().replace('\\', '/'), Files.readString(path))
        }.toList() } }

    fun violations(sources: List<SourceFile>): List<String> = sources.flatMap { source ->
        val imports = source.contents.lineSequence().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }.toList()
        buildList {
            if (domainRoots.any(source.path::startsWith)) {
                forbiddenDomainImports.firstOrNull { forbidden -> imports.any { it.startsWith(forbidden) } }?.let { forbidden ->
                    add("${source.path}: domain code may not import $forbidden")
                }
            }
            if (source.path.startsWith(desktopTargetRoot) && imports.any { it.startsWith("javax.sound.midi") }) {
                add("${source.path}: desktop code may not parse raw MIDI")
            }
            if (!domainRoots.any(source.path::startsWith) && source.path.startsWith("src/main/kotlin/app/melotrail/midi/") &&
                !source.path.startsWith("src/main/kotlin/app/melotrail/midi/adapter/") &&
                imports.any { it.startsWith("javax.sound.midi") }
            ) {
                add("${source.path}: javax.sound.midi is confined to MIDI/audition adapters")
            }
            if (source.path.startsWith("src/main/kotlin/app/melotrail/audition/") &&
                !source.path.startsWith("src/main/kotlin/app/melotrail/audition/adapter/") &&
                imports.any { it.startsWith("javax.sound.midi") }
            ) {
                add("${source.path}: javax.sound.midi is confined to MIDI/audition adapters")
            }
        }
    }
}

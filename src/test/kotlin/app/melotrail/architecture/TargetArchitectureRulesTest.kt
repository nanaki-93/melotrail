package app.melotrail.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

/** Dependency policy for the target packages introduced by the MIDI Core migration. */
class TargetArchitectureRulesTest {
    @Test
    fun `target source tree obeys the MIDI Core dependency policy`() {
        val violations = TargetArchitectureRules.violations(TargetArchitectureRules.readProductionSources())

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `MIDI Core entrypoints do not depend on rejected application workflows`() {
        val targetSources = TargetArchitectureRules.readProductionSources().filter { source ->
            source.path.startsWith("desktopApp/src/main/kotlin/app/melotrail/desktop/") ||
                source.path.startsWith("src/main/kotlin/app/melotrail/application/MidiCore")
        }
        val legacyImports = targetSources.flatMap { source ->
            source.contents.lineSequence()
                .filter { it.startsWith("import app.melotrail.application.") }
                .map { it.removePrefix("import app.melotrail.application.").trim() }
                .filterNot { it.contains("MidiCore") }
                .map { "${source.path}: app.melotrail.application.$it" }
                .toList()
        }

        assertEquals(emptyList(), legacyImports)
    }

    @Test
    fun `only MIDI Core musical generation and no model adapters remain`() {
        val sources = TargetArchitectureRules.readProductionSources()
        val retiredSourcePaths = sources.map(SourceFile::path).filter { path ->
            (path.startsWith("src/main/kotlin/app/melotrail/arrangement/") &&
                !path.startsWith("src/main/kotlin/app/melotrail/arrangement/core/")) ||
                listOf(
                    "src/main/kotlin/app/melotrail/analysis/",
                    "src/main/kotlin/app/melotrail/commercial/",
                    "src/main/kotlin/app/melotrail/harmony/",
                    "src/main/kotlin/app/melotrail/licensing/",
                    "src/main/kotlin/app/melotrail/preparation/",
                    "src/main/kotlin/app/melotrail/profile/",
                ).any { prefix -> path.startsWith(prefix) } ||
                path == "src/main/kotlin/app/melotrail/music/MusicalPrimitives.kt"
        }
        val modelReferences = sources.flatMap { source ->
            listOf("LocalQwen", "LmStudioQwen", "okhttp3").mapNotNull { reference ->
                reference.takeIf(source.contents::contains)?.let { "${source.path}: $it" }
            }
        }

        assertEquals(emptyList(), retiredSourcePaths)
        assertEquals(emptyList(), modelReferences)
    }

    @Test
    fun `exclusive Qwen and schema V4 fixtures are removed with their owners`() {
        val retiredFixtures = listOf(
            Path.of("src/test/resources/fixtures/qwen"),
            Path.of("src/test/resources/fixtures/project/v4-pending-run.json"),
        )

        assertEquals(emptyList(), retiredFixtures.filter(Files::exists).map(Path::toString))
    }

    @Test
    fun `MIDI application has no audio production or worker runtime`() {
        val retiredOwners = listOf(
            "src/main/kotlin/app/melotrail/audio",
            "src/main/kotlin/app/melotrail/dsp",
            "src/main/kotlin/app/melotrail/errors",
            "src/main/kotlin/app/melotrail/logging",
            "src/main/kotlin/app/melotrail/model",
            "src/main/kotlin/app/melotrail/worker",
            "src/test/kotlin/app/melotrail/audio",
            "src/test/kotlin/app/melotrail/dsp",
            "src/test/kotlin/app/melotrail/errors",
            "src/test/kotlin/app/melotrail/logging",
            "src/test/kotlin/app/melotrail/model",
            "src/test/kotlin/app/melotrail/quality",
            "src/test/kotlin/app/melotrail/worker",
            "worker",
            "tools/curate_sound_library.py",
        )

        assertEquals(emptyList(), retiredOwners.filter { Files.exists(Path.of(it)) })
        val pythonSources = listOf("src", "desktopApp", "tools", "worker").flatMap { root ->
            Path.of(root).takeIf { Files.isDirectory(it) }?.let { directory ->
                Files.walk(directory).use { paths ->
                    paths.filter { Files.isRegularFile(it) && it.extension == "py" }
                        .map(Path::toString)
                        .toList()
                }
            }.orEmpty()
        }
        assertEquals(emptyList(), pythonSources)
        val makefile = Files.readString(Path.of("Makefile"))
        listOf("worker-test", "python-install", "live-e2e", ".venv-worker", "sfizz_render").forEach { retiredTarget ->
            assertFalse(makefile.contains(retiredTarget), "Retired Make wiring remains: $retiredTarget")
        }
        val rootBuild = Files.readString(Path.of("build.gradle.kts"))
        listOf("kotlinx-coroutines-swing", "kotlinx-datetime").forEach { retiredDependency ->
            assertFalse(rootBuild.contains(retiredDependency), "Unused root dependency remains: $retiredDependency")
        }
    }

    @Test
    fun `legacy data payloads and their application consumers remain removed`() {
        val retiredData = listOf(
            ".venv-worker",
            "data/audio",
            "sounds",
            "Piano Song n.17.mp4",
        )

        // Restored, untracked user data can be present in a developer checkout. The
        // repository must not own or package these retired payloads.
        val trackedData = ProcessBuilder(listOf("git", "ls-files", "--cached", "--") + retiredData)
            .redirectErrorStream(true).start().let { process ->
                val output = process.inputStream.bufferedReader().readLines()
                check(process.waitFor() == 0) { "Could not inspect tracked legacy data: $output" }
                output
            }
        assertEquals(emptyList(), trackedData)
        val targetSources = TargetArchitectureRules.readProductionSources()
        val staleConsumers = targetSources.flatMap { source ->
            listOf(
                "MUSIC_SOUNDS_ROOT",
                "Piano Song n.17.mp4",
                "sounds/instruments.json",
                "sfizz_render",
                "javax.imageio.ImageIO",
            ).mapNotNull { reference ->
                reference.takeIf(source.contents::contains)
                    ?.takeUnless { it == "javax.imageio.ImageIO" && source.path == TargetArchitectureRules.videoImageAdapter }
                    ?.let { "${source.path}: $it" }
            }
        }

        assertEquals(emptyList(), staleConsumers)
        val ignores = Files.readString(Path.of(".gitignore"))
        listOf(".venv-worker", "sounds/", "/data/", "*.wav", "renders/").forEach { retiredIgnore ->
            assertFalse(ignores.contains(retiredIgnore), "Stale ignore entry remains: $retiredIgnore")
        }
    }

    @Test
    fun `MIDI export and video launcher do not dispatch to a Swift soundtrack application`() {
        val sources = TargetArchitectureRules.readProductionSources()
        val activeFiles = listOf("Makefile", "tools/companion-check.mjs")
        assertFalse(Files.exists(Path.of(activeFiles[1])))
        assertFalse(Files.exists(Path.of("tools/companion-check.test.mjs")))
        assertFalse(Files.exists(Path.of("companion")), "Swift application directory remains")
        assertEquals(emptyList(), sources.filter { source ->
            source.contents.contains("MidiCoreCompanionLauncher") ||
                source.contents.contains("MidiCoreExportHandoffReference") ||
                source.contents.contains("MELOTRAIL_TABI_EXECUTABLE")
        }.map(SourceFile::path))
        val makefile = Files.readString(Path.of(activeFiles[0]))
        assertFalse(makefile.contains("swift run"))
        assertFalse(makefile.contains("VIDEO_REQUEST"))
        assertEquals(true, makefile.contains(":desktopApp:run --args='--video'"))
    }

    @Test
    fun `domain desktop and MIDI adapter violations are rejected`() {
        val violations = TargetArchitectureRules.violations(
            listOf(
                SourceFile("src/main/kotlin/app/melotrail/midi/domain/Sequence.kt", "import javax.sound.midi.Sequence"),
                SourceFile("src/main/kotlin/app/melotrail/project/ProjectStore.kt", "import java.nio.file.Path"),
                SourceFile("src/main/kotlin/app/melotrail/review/ReviewState.kt", "import androidx.compose.runtime.State"),
                SourceFile("src/main/kotlin/app/melotrail/structure/Planner.kt", "import java.net.URI"),
                SourceFile("src/main/kotlin/app/melotrail/project/adapter/ProjectStore.kt", "import java.nio.file.Path"),
                SourceFile("src/main/kotlin/app/melotrail/project/adapter/BadStore.kt", "import java.net.URI"),
                SourceFile("desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreMidiPage.kt", "import javax.sound.midi.MidiSystem"),
                SourceFile("src/main/kotlin/app/melotrail/midi/adapter/JdkMidiReader.kt", "import javax.sound.midi.MidiSystem"),
            ),
        )

        assertEquals(
            listOf(
                "src/main/kotlin/app/melotrail/midi/domain/Sequence.kt: domain code may not import javax.sound.midi",
                "src/main/kotlin/app/melotrail/project/ProjectStore.kt: domain code may not import java.nio.file",
                "src/main/kotlin/app/melotrail/review/ReviewState.kt: domain code may not import androidx.compose",
                "src/main/kotlin/app/melotrail/structure/Planner.kt: domain code may not import java.net",
                "src/main/kotlin/app/melotrail/project/adapter/BadStore.kt: project adapter may not import java.net",
                "desktopApp/src/main/kotlin/app/melotrail/desktop/MidiCoreMidiPage.kt: desktop code may not parse raw MIDI",
            ),
            violations,
        )
    }

    @Test
    fun `video project boundaries reject domain IO and cross-workspace dependencies`() {
        val violations = TargetArchitectureRules.violations(
            listOf(
                SourceFile("src/main/kotlin/app/melotrail/video/domain/VideoProject.kt", "import java.nio.file.Path"),
                SourceFile(
                    "src/main/kotlin/app/melotrail/video/adapter/VideoStore.kt",
                    """import app.melotrail.project.adapter.MidiCoreArtifactStore
                        |fun overwriteMidi() = MidiCoreArtifactStore().saveProject(projectRoot, project)
                    """.trimMargin(),
                ),
                SourceFile(
                    "src/main/kotlin/app/melotrail/application/MidiCoreProjectLifecycle.kt",
                    "import app.melotrail.video.application.VideoProjectLifecycle",
                ),
            ),
        )

        assertEquals(
            listOf(
                "src/main/kotlin/app/melotrail/video/domain/VideoProject.kt: domain code may not import java.nio.file",
                "src/main/kotlin/app/melotrail/video/adapter/VideoStore.kt: video code may not import or write MIDI project storage owners",
                "src/main/kotlin/app/melotrail/application/MidiCoreProjectLifecycle.kt: MIDI code may not import video owners",
            ),
            violations,
        )
    }

    @Test
    fun `ImageIO is confined to the video image filesystem adapter`() {
        val violations = TargetArchitectureRules.violations(
            listOf(
                SourceFile(
                    "src/main/kotlin/app/melotrail/video/adapter/VideoImageFiles.kt",
                    "import javax.imageio.ImageIO",
                ),
                SourceFile(
                    "src/main/kotlin/app/melotrail/video/domain/VideoAsset.kt",
                    "import javax.imageio.ImageIO",
                ),
                SourceFile(
                    "src/main/kotlin/app/melotrail/video/application/VideoAssetImport.kt",
                    "import javax.imageio.ImageIO",
                ),
                SourceFile(
                    "src/main/kotlin/app/melotrail/midi/adapter/JdkMidiReader.kt",
                    "import javax.imageio.ImageIO",
                ),
            ),
        )

        assertEquals(
            listOf(
                "src/main/kotlin/app/melotrail/video/domain/VideoAsset.kt: javax.imageio.ImageIO is confined to the video image filesystem adapter",
                "src/main/kotlin/app/melotrail/video/application/VideoAssetImport.kt: javax.imageio.ImageIO is confined to the video image filesystem adapter",
                "src/main/kotlin/app/melotrail/midi/adapter/JdkMidiReader.kt: javax.imageio.ImageIO is confined to the video image filesystem adapter",
            ),
            violations,
        )
    }
}

private data class SourceFile(val path: String, val contents: String)

private object TargetArchitectureRules {
    const val videoImageAdapter = "src/main/kotlin/app/melotrail/video/adapter/VideoImageFiles.kt"
    private val domainRoots = listOf(
        "src/main/kotlin/app/melotrail/project/",
        "src/main/kotlin/app/melotrail/midi/domain/",
        "src/main/kotlin/app/melotrail/music/core/",
        "src/main/kotlin/app/melotrail/structure/",
        "src/main/kotlin/app/melotrail/arrangement/core/",
        "src/main/kotlin/app/melotrail/review/",
        "src/main/kotlin/app/melotrail/export/domain/",
        "src/main/kotlin/app/melotrail/video/domain/",
    )
    // Target pages live directly in the desktop package; the retired target/ subtree never existed.
    private const val desktopRoot = "desktopApp/src/main/kotlin/app/melotrail/desktop/"
    private const val projectAdapterRoot = "src/main/kotlin/app/melotrail/project/adapter/"
    private const val videoRoot = "src/main/kotlin/app/melotrail/video/"
    private val midiOwnerRoots = listOf(
        "src/main/kotlin/app/melotrail/project/",
        "src/main/kotlin/app/melotrail/midi/",
        "src/main/kotlin/app/melotrail/music/",
        "src/main/kotlin/app/melotrail/structure/",
        "src/main/kotlin/app/melotrail/arrangement/",
        "src/main/kotlin/app/melotrail/review/",
        "src/main/kotlin/app/melotrail/export/",
        "src/main/kotlin/app/melotrail/audition/",
    )
    private val forbiddenDomainImports = listOf(
        "androidx.compose",
        "java.io",
        "java.net",
        "java.nio.channels",
        "java.nio.file",
        "kotlin.io",
        "okhttp",
        "javax.sound.midi",
    )
    private val forbiddenProjectAdapterImports = listOf("androidx.compose", "java.net", "okhttp", "javax.sound.midi")

    fun readProductionSources(): List<SourceFile> = listOf(Path.of("src/main/kotlin"), Path.of("desktopApp/src/main/kotlin"))
        .filter(Files::isDirectory)
        .flatMap { root -> Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.extension == "kt" }.map { path ->
            SourceFile(path.toString().replace('\\', '/'), Files.readString(path))
        }.toList() } }

    fun violations(sources: List<SourceFile>): List<String> = sources.flatMap { source ->
        val imports = source.contents.lineSequence().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }.toList()
        buildList {
            if (isDomainSource(source.path)) {
                forbiddenDomainImports.firstOrNull { forbidden -> imports.any { it.startsWith(forbidden) } }?.let { forbidden ->
                    add("${source.path}: domain code may not import $forbidden")
                }
            }
            if (source.path.startsWith(projectAdapterRoot)) {
                forbiddenProjectAdapterImports.firstOrNull { forbidden -> imports.any { it.startsWith(forbidden) } }?.let { forbidden ->
                    add("${source.path}: project adapter may not import $forbidden")
                }
            }
            if (source.path.startsWith(videoRoot) &&
                (imports.any { imported ->
                    imported.startsWith("app.melotrail.project") ||
                        imported.startsWith("app.melotrail.midi") ||
                        imported.startsWith("app.melotrail.application.MidiCore")
                } || listOf("MidiCoreArtifactStore", "MidiCoreProjectSchema").any(source.contents::contains))
            ) {
                add("${source.path}: video code may not import or write MIDI project storage owners")
            }
            if ((midiOwnerRoots.any(source.path::startsWith) ||
                    source.path.startsWith("src/main/kotlin/app/melotrail/application/MidiCore")) &&
                imports.any { it.startsWith("app.melotrail.video") }
            ) {
                add("${source.path}: MIDI code may not import video owners")
            }
            if (source.path.startsWith(desktopRoot) && imports.any { it.startsWith("javax.sound.midi") }) {
                add("${source.path}: desktop code may not parse raw MIDI")
            }
            if (source.path != videoImageAdapter && imports.any { it.startsWith("javax.imageio.ImageIO") }) {
                add("${source.path}: javax.imageio.ImageIO is confined to the video image filesystem adapter")
            }
            if (!isDomainSource(source.path) && source.path.startsWith("src/main/kotlin/app/melotrail/midi/") &&
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

    private fun isDomainSource(path: String): Boolean =
        domainRoots.any(path::startsWith) && !path.startsWith(projectAdapterRoot)
}

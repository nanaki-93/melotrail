package ai.music.workstation.desktop

import java.nio.file.Path
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.prefs.Preferences

/**
 * Stores only optional desktop convenience state. Project files remain the
 * source of truth for every song and artifact.
 */
interface DesktopPreferences {
    fun lastOpenedProject(): Path?
    fun saveLastOpenedProject(root: Path)
    fun clearLastOpenedProject()
}

class JvmDesktopPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(JvmDesktopPreferences::class.java)
) : DesktopPreferences {
    override fun lastOpenedProject(): Path? = runCatching {
        preferences.get(LAST_PROJECT_KEY, null)?.takeIf(String::isNotBlank)?.let(Path::of)
    }.getOrNull()

    override fun saveLastOpenedProject(root: Path) {
        runCatching { preferences.put(LAST_PROJECT_KEY, root.toAbsolutePath().normalize().toString()) }
    }

    override fun clearLastOpenedProject() {
        runCatching { preferences.remove(LAST_PROJECT_KEY) }
    }

    private companion object {
        const val LAST_PROJECT_KEY = "last-successfully-opened-project"
    }
}

/** Logs bounded diagnostic metadata without recording model output or source content. */
interface DesktopOperationLogger {
    fun event(operation: String, stage: String, artifact: Path? = null, failure: Throwable? = null)
}

class LocalDesktopOperationLogger : DesktopOperationLogger {
    private val logger = Logger.getLogger("ai.music.workstation.desktop.operations").apply {
        useParentHandlers = true
        level = Level.INFO
        runCatching {
            val directory = Path.of(System.getProperty("user.home"), ".personal-ai-music-arranger", "logs")
            java.nio.file.Files.createDirectories(directory)
            addHandler(FileHandler(directory.resolve("desktop-%g.log").toString(), 512 * 1024, 3, true).apply {
                formatter = SimpleFormatter()
            })
        }
    }

    override fun event(operation: String, stage: String, artifact: Path?, failure: Throwable?) {
        val artifactValue = artifact?.toAbsolutePath()?.normalize()?.toString()?.replace('"', '\'') ?: ""
        val failureType = failure?.javaClass?.simpleName ?: ""
        logger.info("operation=${safe(operation)} stage=${safe(stage)} artifact=\"$artifactValue\" failure=$failureType")
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}

object NoOpDesktopPreferences : DesktopPreferences {
    override fun lastOpenedProject(): Path? = null
    override fun saveLastOpenedProject(root: Path) = Unit
    override fun clearLastOpenedProject() = Unit
}

object NoOpDesktopOperationLogger : DesktopOperationLogger {
    override fun event(operation: String, stage: String, artifact: Path?, failure: Throwable?) = Unit
}

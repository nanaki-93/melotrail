package app.melotrail.desktop

import java.nio.file.Path
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/** Logs bounded diagnostic metadata without recording project paths or MIDI content. */
interface DesktopOperationLogger {
    fun event(operation: String, stage: String, artifact: Path? = null, failure: Throwable? = null)
}

class LocalDesktopOperationLogger(
    private val logger: Logger = Logger.getLogger("app.melotrail.desktop.operations").apply {
        useParentHandlers = true
        level = Level.INFO
        runCatching {
            val directory = Path.of(System.getProperty("user.home"), ".melotrail", "logs")
            java.nio.file.Files.createDirectories(directory)
            addHandler(FileHandler(directory.resolve("desktop-%g.log").toString(), 512 * 1024, 3, true).apply {
                formatter = SimpleFormatter()
            })
        }
    },
) : DesktopOperationLogger {
    override fun event(operation: String, stage: String, artifact: Path?, failure: Throwable?) {
        val artifactValue = artifact?.let(::artifactClass).orEmpty()
        val failureType = failure?.javaClass?.simpleName ?: ""
        logger.info("operation=${safe(operation)} phase_or_stage=${safe(stage)} artifact=\"$artifactValue\" failure=$failureType")
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    /** Keep diagnostics useful without recording user paths or source file names. */
    private fun artifactClass(path: Path): String = when (path.fileName?.toString().orEmpty().substringAfterLast('.', "").lowercase()) {
        "mid", "midi" -> "midi"
        "json" -> "json"
        "" -> "directory_or_extensionless"
        else -> "other"
    }
}

object NoOpDesktopOperationLogger : DesktopOperationLogger {
    override fun event(operation: String, stage: String, artifact: Path?, failure: Throwable?) = Unit
}

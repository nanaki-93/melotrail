package app.melotrail.desktop

import java.nio.file.Path
import java.nio.file.Files
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
    fun soundLibraryRoot(): Path?
    fun saveSoundLibraryRoot(root: Path)
    fun clearSoundLibraryRoot()
}

class JvmDesktopPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(JvmDesktopPreferences::class.java),
    private val legacyPreferences: Preferences = Preferences.userRoot().node(LEGACY_PREFERENCES_NODE)
) : DesktopPreferences {
    override fun lastOpenedProject(): Path? = readOrMigrate(LAST_PROJECT_KEY) { raw ->
        Path.of(raw).toAbsolutePath().normalize()
    }

    override fun saveLastOpenedProject(root: Path) {
        runCatching { preferences.put(LAST_PROJECT_KEY, root.toAbsolutePath().normalize().toString()) }
    }

    override fun clearLastOpenedProject() {
        runCatching { preferences.remove(LAST_PROJECT_KEY) }
    }

    override fun soundLibraryRoot(): Path? = readOrMigrate(SOUND_LIBRARY_ROOT_KEY) { raw ->
        Path.of(raw).toAbsolutePath().normalize().takeIf { Files.isDirectory(it) }
    }

    override fun saveSoundLibraryRoot(root: Path) {
        val normalized = root.toAbsolutePath().normalize()
        require(normalized.isAbsolute) { "Sound-library root must be absolute" }
        runCatching { preferences.put(SOUND_LIBRARY_ROOT_KEY, normalized.toString()) }
    }

    override fun clearSoundLibraryRoot() {
        runCatching { preferences.remove(SOUND_LIBRARY_ROOT_KEY) }
    }

    /**
     * The old node is read only when this product's value is absent.  A valid
     * legacy value is copied forward; the legacy preference is intentionally
     * retained so an older installed app remains unaffected.
     */
    private fun readOrMigrate(key: String, parse: (String) -> Path?): Path? {
        val current = preferences.get(key, null)?.takeIf(String::isNotBlank)
        if (current != null) return runCatching { parse(current) }.getOrNull()

        val legacy = legacyPreferences.get(key, null)?.takeIf(String::isNotBlank) ?: return null
        val migrated = runCatching { parse(legacy) }.getOrNull() ?: return null
        runCatching { preferences.put(key, migrated.toString()) }
        return migrated
    }

    private companion object {
        const val LAST_PROJECT_KEY = "last-successfully-opened-project"
        const val SOUND_LIBRARY_ROOT_KEY = "sound-library-root"
        // Compatibility boundary for preferences written before the Melotrail rename.
        const val LEGACY_PREFERENCES_NODE = "ai/music/workstation/desktop"
    }
}

/** Logs bounded diagnostic metadata without recording model output or source content. */
interface DesktopOperationLogger {
    fun event(operation: String, stage: String, artifact: Path? = null, failure: Throwable? = null)

    fun operationEvent(
        sessionId: String,
        kind: OperationKind?,
        phase: OperationPhase,
        artifact: Path? = null,
        failure: Throwable? = null
    ) = event(kind?.name?.lowercase() ?: "workspace", "$sessionId-${phase.name.lowercase()}", artifact, failure)
}

class LocalDesktopOperationLogger : DesktopOperationLogger {
    private val logger = Logger.getLogger("app.melotrail.desktop.operations").apply {
        useParentHandlers = true
        level = Level.INFO
        runCatching {
            val directory = Path.of(System.getProperty("user.home"), ".melotrail", "logs")
            java.nio.file.Files.createDirectories(directory)
            addHandler(FileHandler(directory.resolve("desktop-%g.log").toString(), 512 * 1024, 3, true).apply {
                formatter = SimpleFormatter()
            })
        }
    }

    override fun event(operation: String, stage: String, artifact: Path?, failure: Throwable?) {
        val artifactValue = artifact?.toAbsolutePath()?.normalize()?.toString()?.replace('"', '\'') ?: ""
        val failureType = failure?.javaClass?.simpleName ?: ""
        logger.info("operation=${safe(operation)} phase_or_stage=${safe(stage)} artifact=\"$artifactValue\" failure=$failureType")
    }

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}

object NoOpDesktopPreferences : DesktopPreferences {
    override fun lastOpenedProject(): Path? = null
    override fun saveLastOpenedProject(root: Path) = Unit
    override fun clearLastOpenedProject() = Unit
    override fun soundLibraryRoot(): Path? = null
    override fun saveSoundLibraryRoot(root: Path) = Unit
    override fun clearSoundLibraryRoot() = Unit
}

object NoOpDesktopOperationLogger : DesktopOperationLogger {
    override fun event(operation: String, stage: String, artifact: Path?, failure: Throwable?) = Unit
}

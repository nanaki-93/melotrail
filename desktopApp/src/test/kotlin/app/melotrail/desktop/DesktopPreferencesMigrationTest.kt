package app.melotrail.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopPreferencesMigrationTest {
    @Test
    fun `migrates absent preferences without deleting the legacy values`() = withNodes { current, legacy ->
        val project = Path.of("build", "legacy-project").toAbsolutePath().normalize()
        val library = Files.createTempDirectory("melotrail-legacy-library")
        legacy.put("last-successfully-opened-project", project.toString())
        legacy.put("sound-library-root", library.toString())

        val preferences = JvmDesktopPreferences(current, legacy)

        assertEquals(project, preferences.lastOpenedProject())
        assertEquals(library, preferences.soundLibraryRoot())
        assertEquals(project.toString(), current.get("last-successfully-opened-project", null))
        assertEquals(library.toString(), current.get("sound-library-root", null))
        assertEquals(project.toString(), legacy.get("last-successfully-opened-project", null))
        assertEquals(library.toString(), legacy.get("sound-library-root", null))
    }

    @Test
    fun `new preferences win over legacy preferences`() = withNodes { current, legacy ->
        val currentProject = Path.of("build", "current-project").toAbsolutePath().normalize()
        val legacyProject = Path.of("build", "legacy-project").toAbsolutePath().normalize()
        val currentLibrary = Files.createTempDirectory("melotrail-current-library")
        val legacyLibrary = Files.createTempDirectory("melotrail-old-library")
        current.put("last-successfully-opened-project", currentProject.toString())
        current.put("sound-library-root", currentLibrary.toString())
        legacy.put("last-successfully-opened-project", legacyProject.toString())
        legacy.put("sound-library-root", legacyLibrary.toString())

        val preferences = JvmDesktopPreferences(current, legacy)

        assertEquals(currentProject, preferences.lastOpenedProject())
        assertEquals(currentLibrary, preferences.soundLibraryRoot())
    }

    @Test
    fun `malformed legacy values are ignored and retained`() = withNodes { current, legacy ->
        legacy.put("last-successfully-opened-project", "   ")
        legacy.put("sound-library-root", Path.of("build", "missing-library").toAbsolutePath().toString())

        val preferences = JvmDesktopPreferences(current, legacy)

        assertNull(preferences.lastOpenedProject())
        assertNull(preferences.soundLibraryRoot())
        assertNull(current.get("last-successfully-opened-project", null))
        assertNull(current.get("sound-library-root", null))
        assertEquals("   ", legacy.get("last-successfully-opened-project", null))
        assertEquals(Path.of("build", "missing-library").toAbsolutePath().toString(), legacy.get("sound-library-root", null))
    }

    private fun withNodes(block: (Preferences, Preferences) -> Unit) {
        val base = Preferences.userRoot().node("melotrail-test-${UUID.randomUUID()}")
        val current = base.node("current")
        val legacy = base.node("legacy")
        try {
            block(current, legacy)
        } finally {
            base.removeNode()
        }
    }
}

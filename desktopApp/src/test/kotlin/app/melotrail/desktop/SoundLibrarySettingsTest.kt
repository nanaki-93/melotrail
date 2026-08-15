package app.melotrail.desktop

import app.melotrail.arrangement.SoundLibraryLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundLibrarySettingsTest {
    @Test
    fun `valid selection persists and invalid selection preserves last known good root`() {
        val good = Files.createTempDirectory("sound-library-good")
        val bad = Files.createTempDirectory("sound-library-bad")
        val preferences = MemoryPreferences()
        val validator = SoundLibraryValidator { root -> if (root == good) Result.success(Unit) else Result.failure(IllegalArgumentException("instruments.json is invalid")) }
        val service = SoundLibrarySettingsService(preferences, SoundLibraryLocator(emptyMap()), validator, environment = emptyMap())

        assertEquals(good, service.select(good).resolvedRoot)
        val invalid = service.select(bad)

        assertEquals(good, preferences.soundLibraryRoot())
        assertEquals(good, invalid.resolvedRoot)
        assertEquals("instruments.json is invalid", invalid.validationError)
    }

    @Test
    fun `environment root is authoritative and disables selection`() {
        val root = Files.createTempDirectory("sound-library-env")
        val environment = mapOf("MUSIC_SOUNDS_ROOT" to root.toString())
        val service = SoundLibrarySettingsService(
            MemoryPreferences(), SoundLibraryLocator(environment), SoundLibraryValidator { Result.success(Unit) }, environment = environment
        )

        val state = service.refresh()

        assertEquals(root, state.resolvedRoot)
        assertEquals("MUSIC_SOUNDS_ROOT", state.source)
        assertTrue(state.selectionDisabledReason!!.contains("MUSIC_SOUNDS_ROOT"))
    }

    @Test
    fun `clear removes configured root and corrupt or missing preference is ignored`() {
        val root = Files.createTempDirectory("sound-library-clear")
        val preferences = MemoryPreferences(root)
        val service = SoundLibrarySettingsService(preferences, SoundLibraryLocator(emptyMap()), SoundLibraryValidator { Result.success(Unit) }, environment = emptyMap())

        service.clear()

        assertNull(preferences.soundLibraryRoot())
        assertFalse(service.refresh().restartRequired)
    }

    private class MemoryPreferences(private var library: Path? = null) : DesktopPreferences {
        override fun lastOpenedProject(): Path? = null
        override fun saveLastOpenedProject(root: Path) = Unit
        override fun clearLastOpenedProject() = Unit
        override fun soundLibraryRoot(): Path? = library
        override fun saveSoundLibraryRoot(root: Path) { library = root }
        override fun clearSoundLibraryRoot() { library = null }
    }
}

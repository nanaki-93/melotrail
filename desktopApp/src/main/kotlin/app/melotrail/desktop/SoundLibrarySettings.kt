package app.melotrail.desktop

import app.melotrail.arrangement.InstrumentRegistryLoader
import app.melotrail.arrangement.SoundLibraryLocation
import app.melotrail.arrangement.SoundLibraryLocator
import java.nio.file.Path

data class SoundLibrarySettingsState(
    val resolvedRoot: Path? = null,
    val source: String? = null,
    val validationError: String? = null,
    val selectionDisabledReason: String? = null,
    val restartRequired: Boolean = false
)

/** Validates the complete registry before a desktop preference is retained. */
fun interface SoundLibraryValidator {
    fun validate(root: Path): Result<Unit>
}

object RegistrySoundLibraryValidator : SoundLibraryValidator {
    override fun validate(root: Path): Result<Unit> = runCatching {
        InstrumentRegistryLoader(root).load()
        Unit
    }
}

/**
 * Desktop-only preference boundary for the sound-library location.  It never
 * stores projects or audio, and an environment override is intentionally
 * authoritative.
 */
class SoundLibrarySettingsService(
    private val preferences: DesktopPreferences,
    private val locator: SoundLibraryLocator = SoundLibraryLocator(configuredDevelopmentCandidates = emptyList()),
    private val validator: SoundLibraryValidator = RegistrySoundLibraryValidator,
    private val activeRoot: Path? = null,
    private val environment: Map<String, String> = System.getenv()
) {
    fun refresh(): SoundLibrarySettingsState = resolve()

    fun select(root: Path): SoundLibrarySettingsState {
        environment["MUSIC_SOUNDS_ROOT"]?.takeIf(String::isNotBlank)?.let {
            return resolve()
        }
        val normalized = root.toAbsolutePath().normalize()
        val validation = validator.validate(normalized)
        if (validation.isFailure) {
            return resolve(validation.exceptionOrNull()?.message ?: "The selected folder is not a valid sound library.")
        }
        preferences.saveSoundLibraryRoot(normalized)
        return resolve()
    }

    fun clear(): SoundLibrarySettingsState {
        if (environment["MUSIC_SOUNDS_ROOT"].orEmpty().isNotBlank()) return resolve()
        preferences.clearSoundLibraryRoot()
        return resolve()
    }

    private fun resolve(selectionError: String? = null): SoundLibrarySettingsState {
        val environmentConfigured = environment["MUSIC_SOUNDS_ROOT"].orEmpty().isNotBlank()
        val location = locator.locate(preferences.soundLibraryRoot())
        return when (location) {
            is SoundLibraryLocation.Success -> {
                val validation = validator.validate(location.root)
                if (validation.isSuccess) SoundLibrarySettingsState(
                    resolvedRoot = location.root,
                    source = location.source,
                    validationError = selectionError,
                    selectionDisabledReason = if (environmentConfigured) "MUSIC_SOUNDS_ROOT is set for this launch; folder selection is disabled." else null,
                    restartRequired = activeRoot != null && activeRoot != location.root
                ) else SoundLibrarySettingsState(
                    resolvedRoot = location.root,
                    source = location.source,
                    validationError = selectionError ?: validation.exceptionOrNull()?.message ?: "The sound-library registry is invalid.",
                    selectionDisabledReason = if (environmentConfigured) "MUSIC_SOUNDS_ROOT is set for this launch; folder selection is disabled." else null
                )
            }
            is SoundLibraryLocation.Failure -> SoundLibrarySettingsState(
                validationError = selectionError ?: location.candidates.joinToString(" ") { it.reason }
                    .takeIf(String::isNotBlank) ?: "Choose the folder containing a complete sound-library registry.",
                selectionDisabledReason = if (environmentConfigured) "MUSIC_SOUNDS_ROOT is set for this launch; correct it before selecting another folder." else null
            )
        }
    }
}

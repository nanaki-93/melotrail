package app.melotrail.arrangement

import java.nio.file.Files
import java.nio.file.Path

/**
 * Result of a sound-library discovery attempt.
 *
 * A successful result carries the normalized absolute root and the name of the
 * discovery source so that the UI can display it.  A failure lists every
 * checked candidate without exposing secrets or leaking internal paths.
 */
sealed class SoundLibraryLocation {

    /** Discovery succeeded; [root] is a validated absolute directory. */
    data class Success(
        val root: Path,
        val source: String
    ) : SoundLibraryLocation() {
        init {
            require(root.isAbsolute) { "SoundLibraryLocation.Success root must be absolute" }
        }
    }

    /** Discovery failed; [candidates] contains the checked paths (safe to log). */
    data class Failure(
        val candidates: List<Candidate>
    ) : SoundLibraryLocation() {

        data class Candidate(
            val path: Path,
            val reason: String
        )
    }
}

/**
 * Resolves a validated absolute sound-library root through a strict precedence.
 *
 * Resolution order:
 * 1. Nonblank `MUSIC_SOUNDS_ROOT` environment variable (must be a valid directory).
 * 2. An explicitly injected configured path (must be a valid directory).
 * 3. Explicit development/bundled candidates (current directory, then
 *    resource-classpath-relative paths).
 *
 * An invalid environment override fails immediately — it never silently falls
 * back to another candidate.
 */
class SoundLibraryLocator(
    private val environment: Map<String, String> = System.getenv(),
    private val configuredDevelopmentCandidates: List<Path> = defaultDevelopmentCandidates()
) {

    /**
     * Resolve the sound-library root.
     *
     * @param configuredRoot optional pre-resolved configured path (e.g. from
     *   desktop preferences).  When present and non-blank, it overrides all
     *   discovery candidates.
     */
    fun locate(configuredRoot: Path?): SoundLibraryLocation {
        val checked = mutableListOf<SoundLibraryLocation.Failure.Candidate>()

        // 1. Environment variable — strict, no fallback on failure.
        val envValue = environment["MUSIC_SOUNDS_ROOT"].orEmpty()
        if (envValue.isNotBlank()) {
            val envPath = try {
                Path.of(envValue)
            } catch (_: Exception) {
                return SoundLibraryLocation.Failure(
                    listOf(SoundLibraryLocation.Failure.Candidate(
                        Path.of(envValue),
                        "Environment variable MUSIC_SOUNDS_ROOT is not a resolvable path"
                    ))
                )
            }
            val absEnv = envPath.toAbsolutePath().normalize()
            if (Files.isDirectory(absEnv)) {
                return SoundLibraryLocation.Success(absEnv, "MUSIC_SOUNDS_ROOT")
            }
            checked += SoundLibraryLocation.Failure.Candidate(
                absEnv,
                "MUSIC_SOUNDS_ROOT points to a non-directory: $absEnv"
            )
            // Invalid env override fails clearly — no fallback.
            return SoundLibraryLocation.Failure(checked)
        }

        // 2. Injected configured path.
        if (configuredRoot != null) {
            val absConfig = configuredRoot.toAbsolutePath().normalize()
            if (Files.isDirectory(absConfig)) {
                return SoundLibraryLocation.Success(absConfig, "configured")
            }
            checked += SoundLibraryLocation.Failure.Candidate(
                absConfig,
                "Configured path is not a directory: $absConfig"
            )
        }

        // 3. Development / bundled candidates.
        for (candidate in configuredDevelopmentCandidates) {
            if (Files.isDirectory(candidate)) {
                return SoundLibraryLocation.Success(candidate, "development")
            }
            checked += SoundLibraryLocation.Failure.Candidate(
                candidate,
                "Development candidate is not a directory"
            )
        }

        return SoundLibraryLocation.Failure(checked)
    }

    /**
     * Development-only candidates: CWD-relative "sounds", then a few common
     * bundled locations.  These are only reached when no env or config path
     * is set.
     */
    companion object {
        private fun defaultDevelopmentCandidates(): List<Path> = listOf(
            Path.of("sounds").toAbsolutePath().normalize(),
            Path.of("resources", "sounds").toAbsolutePath().normalize(),
            Path.of("assets", "sounds").toAbsolutePath().normalize()
        )
    }
}

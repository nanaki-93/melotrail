package app.melotrail.desktop

import app.melotrail.application.MidiCoreExportHandoff
import app.melotrail.application.MidiCoreExportHandoffReference
import app.melotrail.application.MidiCoreProjectSession
import app.melotrail.project.MidiCoreExportSnapshot
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal interface MidiCoreOptionalCompanion {
    fun isAvailable(): Boolean
    fun launch(session: MidiCoreProjectSession, snapshot: MidiCoreExportSnapshot)
}

/** Small OS launch adapter only: no companion library, media runtime, project writes or PATH search. */
internal class MidiCoreCompanionLauncher(
    private val executable: Path? = installedExecutable(),
    private val handoff: MidiCoreExportHandoff = MidiCoreExportHandoff(),
    private val probeTimeoutMillis: Long = 2_000,
) : MidiCoreOptionalCompanion {
    override fun isAvailable(): Boolean = runCatching {
        val path = executable ?: return false
        if (!path.isAbsolute || !Files.isRegularFile(path) || !Files.isExecutable(path)) return false
        val process = ProcessBuilder(path.toString(), "--capabilities")
            .directory(Path.of(System.getProperty("java.io.tmpdir")).toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.outputStream.close()
        val response = ByteArrayOutputStream()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(probeTimeoutMillis)
        fun drainAvailable() {
            val count = minOf(process.inputStream.available(), 4_097 - response.size())
            if (count > 0) response.write(process.inputStream.readNBytes(count))
        }
        try {
            while (process.isAlive && response.size() <= 4_096 && System.nanoTime() < deadline) {
                drainAvailable()
                process.waitFor(10, TimeUnit.MILLISECONDS)
            }
            drainAvailable()
            !process.isAlive && process.exitValue() == 0 && response.size() <= 4_096 &&
                response.toString(Charsets.UTF_8).trim() == CAPABILITY
        } finally {
            process.descendants().forEach { it.destroyForcibly() }
            if (process.isAlive) process.destroyForcibly()
            process.inputStream.close()
        }
    }.getOrDefault(false)

    override fun launch(session: MidiCoreProjectSession, snapshot: MidiCoreExportSnapshot) {
        check(isAvailable()) { "TABI is unavailable or incompatible. Install the current companion and reopen Export." }
        handoff.launch(session, snapshot, ::launchProcess)
    }

    internal fun launchProcess(reference: MidiCoreExportHandoffReference) {
        val process = ProcessBuilder(
            requireNotNull(executable).toString(), "--midi-export",
            reference.manifest.toString(), reference.sha256, reference.snapshotId,
        ).directory(Path.of(System.getProperty("java.io.tmpdir")).toFile())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.outputStream.close()
        check(!process.waitFor(150, TimeUnit.MILLISECONDS) || process.exitValue() == 0) {
            "TABI could not start. Open the companion directly, then retry."
        }
    }

    companion object {
        // Protocol v1 includes manifest schema v2, digest/snapshot pinning and separate soundtrack intake.
        const val CAPABILITY = "melotrail-tabi-export-handoff-v1-manifest-v2"

        private fun installedExecutable(): Path? = runCatching {
            if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return null
            System.getenv("MELOTRAIL_TABI_EXECUTABLE")?.let(Path::of)
                ?: Path.of(System.getProperty("user.home"), "Applications", "Melotrail TABI", "melotrail-tabi-editor")
        }.getOrNull()
    }
}

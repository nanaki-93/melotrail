package app.melotrail.desktop

import app.melotrail.application.MidiCoreExportHandoffReference
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class MidiCoreCompanionLauncherTest {
    @TempDir lateinit var root: Path

    @Test
    fun `only an installed responsive protocol-compatible companion is available`() {
        assertFalse(MidiCoreCompanionLauncher(null).isAvailable())
        assertFalse(MidiCoreCompanionLauncher(root.resolve("absent")).isAvailable())
        assertFalse(MidiCoreCompanionLauncher(Path.of("relative-editor")).isAvailable())
        assertFalse(MidiCoreCompanionLauncher(script("old", "echo old-version")).isAvailable())
        assertFalse(MidiCoreCompanionLauncher(script("failure", "echo ${MidiCoreCompanionLauncher.CAPABILITY}\nexit 1")).isAvailable())
        val installed = script("TABI 日本語 editor", "test \"\$1\" = --capabilities\necho ${MidiCoreCompanionLauncher.CAPABILITY}")
        val launcher = MidiCoreCompanionLauncher(installed)
        assertTrue(launcher.isAvailable())
        Files.delete(installed)
        assertFalse(launcher.isAvailable())
    }

    @Test
    fun `capability probing bounds both time and output`() {
        val slow = script("slow", "exec sleep 5")
        val start = System.nanoTime()
        assertFalse(MidiCoreCompanionLauncher(slow, probeTimeoutMillis = 100).isAvailable())
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2_000)
        val noisy = script("noisy", "while :; do echo too-much-output; done")
        assertFalse(MidiCoreCompanionLauncher(noisy, probeTimeoutMillis = 100).isAvailable())
    }

    @Test
    fun `launch passes literal Unicode and spaces as argv without a project working directory`() {
        val arguments = root.resolve("arguments.txt")
        val executable = script("installed editor", "printf '%s\\n' \"\$@\" > '${arguments}'\npwd >> '${arguments}'")
        val manifest = root.resolve("日本語 with spaces \$(touch injected) manifest.json")
        val reference = MidiCoreExportHandoffReference(manifest, "a".repeat(64), "snapshot-1")
        MidiCoreCompanionLauncher(executable).launchProcess(reference)
        // The adapter permits a persistent GUI process; this owned fixture exits immediately.
        val deadline = System.nanoTime() + 2_000_000_000
        while ((!Files.exists(arguments) || Files.readAllLines(arguments).size < 5) && System.nanoTime() < deadline) Thread.sleep(10)
        val lines = Files.readAllLines(arguments)
        assertEquals(listOf("--midi-export", manifest.toString(), reference.sha256, "snapshot-1"), lines.take(4))
        assertEquals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), Path.of(lines.last()).toRealPath())
        assertFalse(Files.exists(root.resolve("injected")))
        assertFailsWith<IllegalStateException> {
            MidiCoreCompanionLauncher(script("crash", "exit 7")).launchProcess(reference)
        }
    }

    private fun script(name: String, body: String): Path = root.resolve(name).also {
        Files.writeString(it, "#!/bin/sh\n$body\n")
        assertTrue(it.toFile().setExecutable(true))
    }
}

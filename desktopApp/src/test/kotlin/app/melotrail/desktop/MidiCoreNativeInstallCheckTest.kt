package app.melotrail.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MidiCoreNativeInstallCheckTest {
    @Test
    fun `nonzero launch and stalled launch fail with retained logs and no surviving process`(@TempDir root: Path) {
        val failedLog = root.resolve("failure.log")
        val failure = assertFailsWith<IllegalStateException> {
            MidiCoreNativeInstallCheck.runProcess(listOf("/bin/sh", "-c", "printf startup-failed; exit 7"), root, failedLog, 5)
        }
        assertTrue(failure.message!!.contains("Exit 7"))
        assertTrue(failure.message!!.contains("startup-failed"))
        assertEquals("startup-failed", Files.readString(failedLog))
        val stalledLog = root.resolve("timeout.log")
        val timeout = assertFailsWith<IllegalStateException> {
            MidiCoreNativeInstallCheck.runProcess(listOf("/bin/sh", "-c", "echo $$; exec /bin/sleep 30"), root, stalledLog, 1)
        }
        assertTrue(timeout.message!!.contains("Timed out"))
        val pid = Files.readString(stalledLog).trim().toLong()
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false))
    }

    @Test
    fun `reduction includes new uncommitted source and excludes removed source`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("desktopApp/src/main/kotlin"))
        Files.writeString(root.resolve("desktopApp/src/main/kotlin/New.kt"), "package fixture\nclass New\n")
        val report = MidiCoreNativeInstallCheck.reduction(root,
            listOf("desktopApp/src/main/kotlin/Deleted.kt"),
            listOf("desktopApp/src/main/kotlin/Deleted.kt", "desktopApp/src/main/kotlin/New.kt"))
        assertEquals(1, report.getValue("productionFiles").jsonPrimitive.int)
        assertEquals(2, report.getValue("productionLines").jsonPrimitive.int)
        assertEquals(0, report.getValue("trackedPayloadBytes").jsonPrimitive.int)
    }

    @Test
    fun `inventory detects changed install bytes and rejects external symlinks`(@TempDir root: Path) {
        val bundle = Files.createDirectory(root.resolve("Melotrail.app"))
        val payload = Files.writeString(bundle.resolve("payload"), "packaged")
        Files.createSymbolicLink(bundle.resolve("internal"), Path.of("payload"))
        val original = MidiCoreNativeInstallCheck.inventory(bundle)
        assertEquals("symlink:payload", original["internal"])
        Files.writeString(payload, "changed after installation")
        assertNotEquals(original, MidiCoreNativeInstallCheck.inventory(bundle))
        Files.writeString(root.resolve("external"), "protected external file")
        Files.createSymbolicLink(bundle.resolve("escape"), Path.of("../external"))
        assertFailsWith<IllegalStateException> { MidiCoreNativeInstallCheck.inventory(bundle) }
        assertEquals("protected external file", Files.readString(root.resolve("external")))
    }

    @Test
    fun `installed payload requires launcher config and excludes retired runtimes and test libraries`() {
        val required = setOf("Contents/MacOS/Melotrail", "Contents/app/Melotrail.cfg")
        MidiCoreNativeInstallCheck.validatePayload(required + "Contents/app/kotlin-stdlib-2.2.21.jar")
        assertFailsWith<IllegalStateException> { MidiCoreNativeInstallCheck.validatePayload(required - "Contents/app/Melotrail.cfg") }
        listOf("worker/start", "piano.sf2", "qwen.gguf", "junit-jupiter-api.jar", "ui-test-desktop.jar", "companion/provider").forEach {
            assertFailsWith<IllegalStateException>(it) { MidiCoreNativeInstallCheck.validatePayload(required + "Contents/app/$it") }
        }
    }

    @Test
    fun `success requires the installed runtime and application classes as well as a realized window`(@TempDir root: Path) {
        val installed = Files.createDirectory(root.resolve("Melotrail.app"))
        val runtime = Files.createDirectories(installed.resolve("Contents/runtime/Contents/Home"))
        val app = Files.createDirectories(installed.resolve("Contents/app"))
        val jar = Files.writeString(app.resolve("desktop.jar"), "owned test payload")
        val external = Files.createDirectory(root.resolve("external-jdk"))
        val report = Properties().apply {
            setProperty("status", "READY")
            setProperty("entrypoint", "app.melotrail.desktop.DesktopMainKt")
            setProperty("preferences", "ISOLATED_NO_OP")
            setProperty("window.showing", "true")
            setProperty("audition.closed", "true")
            setProperty("window.width", "1280")
            setProperty("window.height", "900")
            setProperty("destinations", midiCoreWorkspaceDestinations.joinToString(",") { it.route })
            setProperty("java.home", runtime.toString())
            setProperty("codeSource", jar.toString())
        }
        MidiCoreNativeInstallCheck.validateStartup(report, installed)
        listOf("status" to "STARTING", "window.showing" to "false", "audition.closed" to "false",
            "destinations" to "project", "java.home" to external.toString(), "codeSource" to external.toString()).forEach { (key, value) ->
            val before = report.setProperty(key, value)
            assertFailsWith<IllegalStateException>(key) { MidiCoreNativeInstallCheck.validateStartup(report, installed) }
            report[key] = before
        }
    }
}

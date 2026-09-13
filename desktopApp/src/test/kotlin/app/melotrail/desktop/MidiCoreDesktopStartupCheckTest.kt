package app.melotrail.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MidiCoreDesktopStartupCheckTest {
    @Test
    fun `ordinary launcher takes no check path and packaged main accepts native arguments`() {
        assertNull(MidiCoreDesktopStartupCheck.fromArguments(emptyArray()))
        assertNotNull(Class.forName("app.melotrail.desktop.DesktopMainKt").getMethod("main", Array<String>::class.java))
        assertFailsWith<IllegalArgumentException> { MidiCoreDesktopStartupCheck.fromArguments(arrayOf("--startup-check")) }
    }

    @Test
    fun `startup output never reuses an existing directory or changes its evidence`(@TempDir root: Path) {
        val evidence = Files.createDirectory(root.resolve("previous"))
        val report = Files.writeString(evidence.resolve("startup.properties"), "previous evidence")
        val home = System.getProperty("user.home")
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            MidiCoreDesktopStartupCheck.fromArguments(arrayOf("--startup-check", evidence.toString()))
        }
        assertEquals("previous evidence", Files.readString(report))
        assertEquals(home, System.getProperty("user.home"))
    }

    @Test
    fun `unrealized window and unclosed session cannot become successful startup evidence`(@TempDir root: Path) {
        val home = System.getProperty("user.home")
        try {
            val directory = root.resolve("startup space Ω")
            val probe = requireNotNull(MidiCoreDesktopStartupCheck.fromArguments(arrayOf("--startup-check", directory.toString())))
            assertEquals(directory.resolve("home").toString(), System.getProperty("user.home"))
            assertFailsWith<IllegalStateException> { probe.complete(720, 620, false, true) }
            assertFailsWith<IllegalStateException> { probe.complete(719, 620, true, true) }
            assertFailsWith<IllegalStateException> { probe.complete(720, 620, true, false) }
            assertFalse(Files.exists(directory.resolve("startup.properties")))
            probe.complete(720, 620, true, true)
            val bytes = Files.readAllBytes(directory.resolve("startup.properties"))
            val report = Properties().apply { bytes.inputStream().use(::load) }
            assertEquals("READY", report.getProperty("status"))
            assertEquals("NOT_MEASURED", report.getProperty("onscreenCompositorCapture"))
            assertEquals(6, report.getProperty("destinations").split(',').size)
            assertFailsWith<java.nio.file.FileAlreadyExistsException> { probe.complete(720, 620, true, true) }
            kotlin.test.assertContentEquals(bytes, Files.readAllBytes(directory.resolve("startup.properties")))
        } finally {
            System.setProperty("user.home", home)
        }
    }
}

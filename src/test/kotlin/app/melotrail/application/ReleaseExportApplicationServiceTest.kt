package app.melotrail.application

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReleaseExportApplicationServiceTest {
    private val root = Path.of("/tmp/melotrail-export-test").toAbsolutePath()
    private val master = root.resolve("output/master.wav")

    @Test fun `inspection offers WAV and optional MP3 only when encoder is available`() = runTest {
        val unavailable = service().inspect(root)
        assertEquals(setOf(ReleaseExportFormat.WAV), unavailable.supportedFormats)
        val available = service(mp3Available = true).inspect(root)
        assertEquals(setOf(ReleaseExportFormat.WAV, ReleaseExportFormat.MP3), available.supportedFormats)
    }

    @Test fun `stale master blocks inspection and export before publishing`() = runTest {
        val files = FakeFilesystem(stale = true)
        val service = DefaultReleaseExportApplicationService(files)
        assertFalse(service.inspect(root).ready)
        assertFailsWith<IllegalStateException> { service.export(wavRequest()) }
        assertEquals(0, files.moves)
    }

    @Test fun `filename extension escaped paths and master overwrite are rejected`() = runTest {
        val files = FakeFilesystem()
        val service = DefaultReleaseExportApplicationService(files)
        assertFailsWith<IllegalArgumentException> { service.export(wavRequest(filename = "song.mp3")) }
        assertFailsWith<IllegalArgumentException> { service.export(wavRequest(filename = "../song.wav")) }
        assertFailsWith<IllegalArgumentException> { service.export(wavRequest(filename = "master.wav")) }
        assertFailsWith<IllegalArgumentException> { service.export(wavRequest(destination = root.resolve("../outside"))) }
        assertEquals(0, files.moves)
    }

    @Test fun `an existing export target is rejected without overwrite`() = runTest {
        val files = FakeFilesystem(targetExists = true)
        assertFailsWith<IllegalArgumentException> { DefaultReleaseExportApplicationService(files).export(wavRequest()) }
        assertEquals(0, files.moves)
    }

    @Test fun `disguised output and atomic failure never report success`() = runTest {
        val disguised = FakeFilesystem(disguisedOutput = true)
        assertFailsWith<IllegalArgumentException> { DefaultReleaseExportApplicationService(disguised).export(wavRequest()) }
        assertEquals(0, disguised.moves)

        val failingMove = FakeFilesystem(failMove = true)
        assertFailsWith<IllegalStateException> { DefaultReleaseExportApplicationService(failingMove).export(wavRequest()) }
        assertEquals(0, failingMove.moves)
    }

    @Test fun `validated WAV success preserves master and publishes only after validation`() = runTest {
        val files = FakeFilesystem()
        val result = DefaultReleaseExportApplicationService(files).export(wavRequest())
        assertEquals(root.resolve("output/Midnight Train.wav"), result.output)
        assertEquals(ReleaseExportFormat.WAV, result.format)
        assertTrue(files.validatedBeforeMove)
        assertEquals("master-before", files.masterDigest)
        assertEquals(1, files.moves)
    }

    @Test fun `optional MP3 uses fake exporter and rejects an exporter that mutates master`() = runTest {
        val files = FakeFilesystem()
        val exporter = FakeMp3Exporter(available = true)
        val result = DefaultReleaseExportApplicationService(files, exporter).export(
            wavRequest(format = ReleaseExportFormat.MP3, filename = "Midnight Train.mp3")
        )
        assertEquals(ReleaseExportFormat.MP3, result.format)
        assertEquals(1, exporter.calls)

        val altered = FakeFilesystem(modifyMasterDuringValidation = true)
        assertFailsWith<IllegalArgumentException> {
            DefaultReleaseExportApplicationService(altered).export(wavRequest())
        }
        assertEquals(0, altered.moves)
    }

    private fun service(mp3Available: Boolean = false) = DefaultReleaseExportApplicationService(FakeFilesystem(), FakeMp3Exporter(mp3Available))
    private fun wavRequest(
        format: ReleaseExportFormat = ReleaseExportFormat.WAV,
        filename: String = "Midnight Train.wav",
        destination: Path = root.resolve("output")
    ) = ReleaseExportRequest(root, format, filename, destination)

    private class FakeMp3Exporter(private val available: Boolean) : ReleaseMp3Exporter {
        var calls = 0
        override suspend fun available(): Boolean = available
        override suspend fun export(input: Path, output: Path, bitrateKbps: Int): Boolean { calls++; return available }
    }

    private class FakeFilesystem(
        private val stale: Boolean = false,
        private val disguisedOutput: Boolean = false,
        private val failMove: Boolean = false,
        private val modifyMasterDuringValidation: Boolean = false,
        private val targetExists: Boolean = false
    ) : ReleaseExportFilesystem {
        var masterDigest = "master-before"
        var moves = 0
        var validatedBeforeMove = false
        private var temporary: Path? = null
        override fun loadValidatedRelease(root: Path): ReleaseExportSummary {
            if (stale) throw IllegalStateException("Master artifacts are stale. Build Song again.")
            return ReleaseExportSummary(root.resolve("output/master.wav"), 252.0, 44_100, 2, 24, 6)
        }
        override fun createDirectories(path: Path) = Unit
        override fun exists(path: Path): Boolean = targetExists
        override fun temporarySibling(target: Path): Path = target.resolveSibling(".${target.fileName}.tmp").also { temporary = it }
        override fun copy(source: Path, target: Path) = Unit
        override fun moveAtomically(source: Path, target: Path) {
            if (failMove) throw IllegalStateException("atomic move failed")
            check(source == temporary)
            moves++
        }
        override fun deleteIfExists(path: Path) = Unit
        override fun validateOutput(path: Path, format: ReleaseExportFormat) {
            if (disguisedOutput) throw IllegalArgumentException("Export did not produce a valid ${format.name} container.")
            validatedBeforeMove = true
            if (modifyMasterDuringValidation) masterDigest = "master-after"
        }
        override fun digest(path: Path): String = masterDigest
    }
}

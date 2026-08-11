package ai.music.workstation.provenance

import ai.music.workstation.model.ErrorReporter
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.nio.file.Files
import java.nio.file.Path

class ProvenanceLogTest {

    private val errorReporter = object : ErrorReporter {
        override fun report(message: String) {}
        override fun report(message: String, cause: Throwable) {}
    }

    @Test
    fun `create empty provenance log`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            val record = log.getRecord()
            assertEquals(0, record.entries.size)
            assertEquals("0.1.0", record.applicationVersion)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `append import entry`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendImportEntry(
                filename = "test.wav",
                relativePath = "source/test.wav",
                format = "WAV",
                sampleRate = 44100,
                channels = 2,
                duration = 10.0,
                hash = "abc123"
            )

            val record = log.getRecord()
            assertEquals(1, record.entries.size)
            val entry = record.entries[0]
            assertTrue(entry is ProvenanceEntry.ImportEntry)
            val importEntry = entry as ProvenanceEntry.ImportEntry
            assertEquals("test.wav", importEntry.filename)
            assertEquals("WAV", importEntry.format)
            assertEquals(44100, importEntry.sampleRate)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `append export entry`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendExportEntry(
                format = "WAV",
                sampleRate = 44100,
                bitDepth = 16,
                filename = "export.wav",
                relativePath = "exports/export.wav",
                inputHash = "input_hash",
                outputHash = "output_hash"
            )

            val record = log.getRecord()
            assertEquals(1, record.entries.size)
            val entry = record.entries[0]
            assertTrue(entry is ProvenanceEntry.ExportEntry)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `append DSP entry`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendDSPEntry(
                presetName = "warm_vinyl",
                dspType = "LOFI",
                settings = mapOf("amount" to "0.5", "tape" to "0.3"),
                targetTrack = "track_1",
                inputHash = "input_hash",
                outputPath = "tracks/track_1_dsp.wav",
                outputHash = "output_hash"
            )

            val record = log.getRecord()
            assertEquals(1, record.entries.size)
            val entry = record.entries[0]
            assertTrue(entry is ProvenanceEntry.DSPEntry)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `append multiple entries of different types`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendImportEntry("test.wav", "source/test.wav", "WAV", 44100, 2, 10.0, "hash1")
            log.appendExportEntry("WAV", 44100, 16, "out.wav", "exports/out.wav", "h1", "h2")
            log.appendDSPEntry(null, "LOFI", emptyMap(), "t1", "h1", "out.wav", "h2")

            val record = log.getRecord()
            assertEquals(3, record.entries.size)
            assertTrue(record.entries[0] is ProvenanceEntry.ImportEntry)
            assertTrue(record.entries[1] is ProvenanceEntry.ExportEntry)
            assertTrue(record.entries[2] is ProvenanceEntry.DSPEntry)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `query entries by type`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendImportEntry("test.wav", "source/test.wav", "WAV", 44100, 2, 10.0, "hash1")
            log.appendExportEntry("WAV", 44100, 16, "out.wav", "exports/out.wav", "h1", "h2")
            log.appendImportEntry("test2.wav", "source/test2.wav", "WAV", 44100, 1, 5.0, "hash2")

            val importEntries = log.getEntriesByType("IMPORT")
            assertEquals(2, importEntries.size)
            assertTrue(importEntries.all { it is ProvenanceEntry.ImportEntry })
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `get latest entry`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendImportEntry("test.wav", "source/test.wav", "WAV", 44100, 2, 10.0, "hash1")
            log.appendExportEntry("WAV", 44100, 16, "out.wav", "exports/out.wav", "h1", "h2")

            val latest = log.getLatestEntry()
            assertTrue(latest is ProvenanceEntry.ExportEntry)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `get entry count`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            assertEquals(0, log.getEntryCount())
            log.appendImportEntry("test.wav", "source/test.wav", "WAV", 44100, 2, 10.0, "hash1")
            assertEquals(1, log.getEntryCount())
            log.appendExportEntry("WAV", 44100, 16, "out.wav", "exports/out.wav", "h1", "h2")
            assertEquals(2, log.getEntryCount())
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `serialize and deserialize provenance`() {
        val tempFile = Files.createTempFile("provenance", ".json")
        try {
            val log = ProvenanceLog(tempFile, errorReporter)
            log.appendImportEntry("test.wav", "source/test.wav", "WAV", 44100, 2, 10.0, "hash1")
            log.appendExportEntry("WAV", 44100, 16, "out.wav", "exports/out.wav", "h1", "h2")

            // Reload from file
            val reloaded = ProvenanceLog(tempFile, errorReporter)
            val record = reloaded.getRecord()
            assertEquals(2, record.entries.size)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `compute SHA-256 hash`() {
        val tempFile = Files.createTempFile("test", ".txt")
        try {
            Files.writeString(tempFile, "Hello, World!")
            val hash = computeSHA256File(tempFile)
            assertEquals(64, hash.length) // SHA-256 produces 64 hex characters
            assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash)
        } finally {
            Files.delete(tempFile)
        }
    }

    @Test
    fun `compute SHA-256 hash of byte array`() {
        val bytes = "Hello, World!".encodeToByteArray()
        val hash = computeSHA256(bytes)
        assertEquals(64, hash.length)
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash)
    }

    @Test
    fun `provenance record filter by type`() {
        val now = Clock.System.now()
        val record = ProvenanceRecord(
            entries = listOf(
                ProvenanceEntry.ImportEntry(now, "IMPORT", null, "test.wav", "source/test.wav", "WAV", 44100, 2, 10.0, "hash1"),
                ProvenanceEntry.ExportEntry(now, "EXPORT", null, "WAV", 44100, 16, "out.wav", "exports/out.wav", "h1", "h2"),
                ProvenanceEntry.ImportEntry(now, "IMPORT", null, "test2.wav", "source/test2.wav", "WAV", 44100, 1, 5.0, "hash2")
            )
        )
        val importEntries = record.filterByType("IMPORT")
        assertEquals(2, importEntries.size)
    }
}

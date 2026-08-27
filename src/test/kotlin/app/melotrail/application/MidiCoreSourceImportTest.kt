package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiImportDisposition
import app.melotrail.project.adapter.AtomicWriteObserver
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreSourceImportTest {
    @TempDir lateinit var root: Path

    @Test
    fun `imports supported SMF sources as immutable bytes with inspection report and track summaries`() {
        listOf("smf0-melody.mid", "smf1-reference-tracks.mid").forEach { filename ->
            val projectRoot = root.resolve(filename.removeSuffix(".mid"))
            val source = fixture(filename, root.resolve("inputs-$filename"))
            val store = MidiCoreArtifactStore()
            val session = create(projectRoot, store)

            val result = assertIs<MidiCoreSourceImportResult.Imported>(
                MidiCoreSourceImport(store).import(ImportMidiCoreSource(session, source)),
            )

            val record = requireNotNull(result.session.project.sourceMidi)
            assertEquals(MidiImportDisposition.AWAITING_AUTHORITY, result.validation.disposition)
            assertEquals(filename, record.originalFilename)
            assertEquals(record.sha256, record.original.sha256)
            assertEquals(record.trackSummaries.indices.toList(), record.trackSummaries.map { it.trackIndex })
            assertContentEquals(Files.readAllBytes(source), Files.readAllBytes(projectRoot.resolve(record.original.path.value)))
            assertEquals(record.sha256, sha256(Files.readAllBytes(projectRoot.resolve(record.original.path.value))))
            val reportBytes = Files.readAllBytes(projectRoot.resolve(record.importReport.path.value))
            assertEquals(record.importReport.sha256, sha256(reportBytes))
            val report = Json.parseToJsonElement(reportBytes.decodeToString()).jsonObject
            assertEquals("melotrail-midi-import-report", report.getValue("schema").jsonPrimitive.content)
            assertEquals(record.sha256, report.getValue("source").jsonObject.getValue("sha256").jsonPrimitive.content)
            assertEquals(result.session.project, store.openProject(projectRoot))
        }
    }

    @Test
    fun `renamed non MIDI input leaves project and artifact tree unchanged`() {
        val store = MidiCoreArtifactStore()
        val session = create(root.resolve("project"), store)
        val source = root.resolve("input/not-midi.mid")
        Files.createDirectories(requireNotNull(source.parent))
        Files.writeString(source, "this is not MIDI")
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val beforeHash = sha256(before)

        val result = assertIs<MidiCoreSourceImportResult.Rejected>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(session, source)),
        )

        assertEquals(MidiCoreSourceImportProblemCode.INVALID_MIDI, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(beforeHash, sha256(Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))))
        assertFalse(Files.exists(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        assertFalse(Files.exists(session.root.resolve(MidiCoreArtifactStore.IMPORT_REPORT.value)))
    }

    @Test
    fun `source mutation between inspection and copy is rejected and removes unbound bytes`() {
        val store = MidiCoreArtifactStore()
        val session = create(root.resolve("project"), store)
        val source = fixture("smf0-melody.mid", root.resolve("input"))
        val replacement = fixture("smf1-reference-tracks.mid", root.resolve("replacement"))
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val beforeHash = sha256(before)
        val importer = MidiCoreSourceImport(
            artifacts = store,
            inspectionObserver = SourceInspectionObserver { inspected ->
                Files.copy(replacement, inspected, StandardCopyOption.REPLACE_EXISTING)
            },
        )

        val result = assertIs<MidiCoreSourceImportResult.Rejected>(importer.import(ImportMidiCoreSource(session, source)))

        assertEquals(MidiCoreSourceImportProblemCode.SOURCE_CHANGED, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(beforeHash, sha256(Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))))
        assertFalse(Files.exists(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        assertFalse(Files.exists(session.root.resolve(MidiCoreArtifactStore.IMPORT_REPORT.value)))
    }

    @Test
    fun `duplicate import preserves the first immutable source and project document`() {
        val store = MidiCoreArtifactStore()
        val session = create(root.resolve("project"), store)
        val first = fixture("smf0-melody.mid", root.resolve("input"))
        val second = fixture("smf1-reference-tracks.mid", root.resolve("replacement"))
        val importer = MidiCoreSourceImport(store)
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(importer.import(ImportMidiCoreSource(session, first)))
        val firstBytes = Files.readAllBytes(imported.session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val before = Files.readAllBytes(imported.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val beforeHash = sha256(before)

        val duplicate = assertIs<MidiCoreSourceImportResult.Rejected>(importer.import(ImportMidiCoreSource(imported.session, second)))

        assertEquals(MidiCoreSourceImportProblemCode.SOURCE_ALREADY_IMPORTED, duplicate.problem.code)
        assertContentEquals(firstBytes, Files.readAllBytes(imported.session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        assertContentEquals(before, Files.readAllBytes(imported.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(beforeHash, sha256(Files.readAllBytes(imported.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))))
    }

    @Test
    fun `failed final project save cleans new import artifacts and preserves prior project`() {
        var failSave = false
        val store = MidiCoreArtifactStore(AtomicWriteObserver { temporary, target ->
            if (failSave && target.fileName.toString() == MidiCoreArtifactStore.PROJECT_FILE) {
                Files.writeString(temporary, "partial")
                throw IOException("simulated final save failure")
            }
        })
        val session = create(root.resolve("project"), store)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val beforeHash = sha256(before)
        failSave = true

        val result = assertIs<MidiCoreSourceImportResult.Rejected>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(session, fixture("smf0-melody.mid", root.resolve("input")))),
        )

        assertEquals(MidiCoreSourceImportProblemCode.SAVE_FAILED, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(beforeHash, sha256(Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))))
        assertFalse(Files.exists(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        assertFalse(Files.exists(session.root.resolve(MidiCoreArtifactStore.IMPORT_REPORT.value)))
        assertEquals(session.project, store.openProject(session.root))
        assertTrue(Files.list(session.root).use { it.anyMatch { path -> path.fileName.toString().contains("recovery") } })
    }

    private fun create(projectRoot: Path, store: MidiCoreArtifactStore): MidiCoreProjectSession =
        assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle(store).create(CreateMidiCoreProject(projectRoot, "Import Test", "project-1"))).session

    private fun fixture(filename: String, directory: Path): Path =
        OwnedMidiFixtures.writeAll(directory).first { it.fileName.toString() == filename }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun lifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "generated-project" },
    )
}

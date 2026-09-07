package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class MidiCoreVisualEvidenceProjectionTest {
    @TempDir lateinit var root: Path

    @Test
    fun `projects protected source notes as read-only verified lane facts`() {
        val session = imported()
        val projectBytes = Files.readAllBytes(session.root.resolve("project.json"))

        val projection = MidiCoreVisualEvidenceProvider().project(ProjectMidiCoreVisualEvidence(session))

        val source = assertIs<MidiCoreVisualEvidence.Available>(projection.source).value
        assertEquals(MidiCoreVisualEvidenceScope.PROTECTED_SOURCE, source.scope)
        assertEquals(listOf(MidiExportRole.MELODY), source.lanes.map { it.role })
        assertTrue(source.lanes.single().events.isNotEmpty())
        assertContentEquals(projectBytes, Files.readAllBytes(session.root.resolve("project.json")))
    }

    @Test
    fun `digest mismatch is unavailable rather than drawn or repaired`() {
        val session = imported()
        val source = requireNotNull(session.project.sourceMidi)
        val projectBytes = Files.readAllBytes(session.root.resolve("project.json"))
        Files.write(session.root.resolve(source.original.path.value), byteArrayOf(0x00, 0x01))

        val projection = MidiCoreVisualEvidenceProvider().project(ProjectMidiCoreVisualEvidence(session))

        val unavailable = assertIs<MidiCoreVisualEvidence.Unavailable>(projection.source).value
        assertEquals("SOURCE_DIGEST_MISMATCH", unavailable.code)
        assertContentEquals(projectBytes, Files.readAllBytes(session.root.resolve("project.json")))
    }

    @Test
    fun `missing source artifact is unavailable without changing project authority`() {
        val session = imported()
        val source = requireNotNull(session.project.sourceMidi)
        val projectBytes = Files.readAllBytes(session.root.resolve("project.json"))
        Files.delete(session.root.resolve(source.original.path.value))

        val projection = MidiCoreVisualEvidenceProvider().project(ProjectMidiCoreVisualEvidence(session))

        val unavailable = assertIs<MidiCoreVisualEvidence.Unavailable>(projection.source).value
        assertEquals("SOURCE_DIGEST_MISMATCH", unavailable.code)
        assertContentEquals(projectBytes, Files.readAllBytes(session.root.resolve("project.json")))
    }

    @Test
    fun `stale project evidence is unavailable rather than drawn`() {
        val store = MidiCoreArtifactStore()
        val session = imported(store)
        store.saveProject(session.root, session.project.copy(revision = session.project.revision + 1L))

        val projection = MidiCoreVisualEvidenceProvider().project(ProjectMidiCoreVisualEvidence(session))

        val unavailable = assertIs<MidiCoreVisualEvidence.Unavailable>(projection.source).value
        assertEquals("STALE_PROJECT", unavailable.code)
    }

    private fun imported(store: MidiCoreArtifactStore = MidiCoreArtifactStore()): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(store).create(CreateMidiCoreProject(root.resolve("project"), "Visual evidence", "visual-evidence-project")),
        ).session
        val fixture = OwnedMidiFixtures.writeAll(root.resolve("fixtures"))
            .first { it.fileName.toString() == "smf0-melody.mid" }
        return assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, fixture)),
        ).session
    }
}

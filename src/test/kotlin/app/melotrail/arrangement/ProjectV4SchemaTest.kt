package app.melotrail.arrangement

import app.melotrail.music.MusicalKey
import app.melotrail.music.PitchClass
import app.melotrail.music.PitchSpelling
import app.melotrail.music.ScaleModeId
import app.melotrail.music.Tempo
import app.melotrail.music.TimeSignature
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectV4SchemaTest {
    @TempDir lateinit var root: Path

    @Test
    fun `v4 fixture reads as a portable empty stage-run envelope`() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/fixtures/project/v4-pending-run.json")) { "Missing v4 fixture" }
        Files.copy(fixture, root.resolve(ProjectStore.FILE_NAME))

        val project = ProjectStore.read(root)

        assertEquals(Project.CURRENT_VERSION, project.version)
        assertEquals(null, project.envelope.stageRuns.index)
        assertTrue(project.validate(root).isValid)
    }

    @Test
    fun `v4 project without the required signature motif field is rejected without rewriting`() {
        val text = """{"version":4,"name":"missing-motif","parts":[],"workflow":{"fullSongEnhancementSelection":"UNRESOLVED"},"envelope":{}}"""
        Files.writeString(root.resolve(ProjectStore.FILE_NAME), text)

        assertFailsWith<IllegalArgumentException> { ProjectStore.read(root) }
        assertEquals(text, Files.readString(root.resolve(ProjectStore.FILE_NAME)))
    }

    @Test
    fun `v4 composition context round trips typed musical primitives including an unknown future mode`() {
        val settings = CompositionSettings(
            key = MusicalKey(PitchClass.of(PitchSpelling.E_FLAT), ScaleModeId("future-mode-v2")),
            tempo = Tempo(117.5),
            timeSignature = TimeSignature(7, 8)
        )
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "musical-context",
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(compositionSettings = settings)
        )

        ProjectStore.write(root, project)

        val text = Files.readString(root.resolve(ProjectStore.FILE_NAME))
        val restored = requireNotNull(ProjectStore.read(root).envelope.compositionSettings)
        assertEquals(settings, restored)
        assertTrue(text.contains("\"chromatic\": 3"))
        assertTrue(text.contains("\"spelling\": \"Eb\""))
        assertTrue(text.contains("\"modeId\": \"future-mode-v2\""))
        assertTrue(text.contains("\"bpm\": 117.5"))
        assertTrue(text.contains("\"numerator\": 7"))
        assertFalse(restored.key.isExecutable)
    }

    @Test
    fun `canonical song parts persist names sections and evidence without legacy roles`() {
        write("source/A.mid", "source")
        write("midi/raw/A.mid", "raw")
        val sourceHash = sha256(root.resolve("source/A.mid"))
        val rawHash = sha256(root.resolve("midi/raw/A.mid"))
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "song-parts",
            renderFormat = RenderFormat(),
            parts = listOf(SongPart(
                id = "A",
                file = "source/A.mid",
                name = "Main chorus",
                sectionType = SectionTypeId.CHORUS,
                midi = MidiReferences(raw = "midi/raw/A.mid"),
                importEvidence = ImportEvidence(sourceHash, rawHash),
                stageManifestRef = "import-a"
            ))
        )

        ProjectStore.write(root, project)

        val text = Files.readString(root.resolve(ProjectStore.FILE_NAME))
        val restored = ProjectStore.read(root).parts.single()
        assertFalse(text.contains("\"role\""))
        assertEquals("Main chorus", restored.name)
        assertEquals(SectionTypeId.CHORUS, restored.sectionType)
        assertEquals(sourceHash, restored.importEvidence?.sourceSha256)
        assertEquals("import-a", restored.stageManifestRef)
    }

    @Test
    fun `future canonical section identifiers remain explicit`() {
        val unknown = SectionTypeId("pre-chorus")
        assertFalse(SectionTypeCatalog.isSupported(unknown))
        assertEquals("Unsupported section type 'pre-chorus'.",
            SongPart("A", "source/A.mid", sectionType = unknown).unsupportedSectionWarning)
    }

    @Test
    fun `clean-only MIDI from superseded projects cannot be written`() {
        write("source/A.mid", "source")
        write("midi/clean/A.mid", "clean")
        val project = Project(
            name = "clean-only",
            renderFormat = RenderFormat(),
            parts = listOf(SongPart(
                id = "A",
                file = "source/A.mid",
                name = "Verse",
                sectionType = SectionTypeId.VERSE,
                midi = MidiReferences(clean = "midi/clean/A.mid")
            ))
        )

        val error = assertFailsWith<IllegalArgumentException> { ProjectStore.write(root, project) }
        assertTrue(error.message.orEmpty().contains("requires raw MIDI provenance"))
        assertFalse(Files.exists(root.resolve(ProjectStore.FILE_NAME)))
    }

    @Test
    fun `project schemas v1 through v3 and missing versions are rejected without rewriting`() {
        listOf(null, 1, 2, 3).forEach { version ->
            val versionField = version?.let { "\"version\":$it," }.orEmpty()
            val text = "{$versionField\"name\":\"unsupported\",\"parts\":[],\"structure\":[]}"
            Files.writeString(root.resolve(ProjectStore.FILE_NAME), text)

            assertFailsWith<IllegalArgumentException> { ProjectStore.read(root) }
            assertEquals(text, Files.readString(root.resolve(ProjectStore.FILE_NAME)))
        }
    }

    @Test
    fun `superseded and unknown v4 fields are rejected`() {
        ProjectStore.write(root, Project(name = "strict-v4", renderFormat = RenderFormat()))
        val canonical = Files.readString(root.resolve(ProjectStore.FILE_NAME))
        listOf(
            canonical.replace("\"evolvedParts\": [],", "\"evolvedParts\": [], \"structureOccurrences\": [],"),
            canonical.replace("\"stageRuns\": {", "\"manifests\": {\"runs\": []}, \"stageRuns\": {"),
            canonical.replace("\"fullSongEnhancementSelection\": \"UNRESOLVED\",", ""),
            canonical.replace("\"version\": 4", "\"version\": 4, \"machinePath\": \"/tmp/not-portable\"")
        ).forEach { unsupported ->
            Files.writeString(root.resolve(ProjectStore.FILE_NAME), unsupported)
            assertFalse(runCatching { ProjectStore.read(root) }.isSuccess)
            assertEquals(unsupported, Files.readString(root.resolve(ProjectStore.FILE_NAME)))
        }
    }

    @Test
    fun `stage-run index is hash validated by the project manifest`() {
        write("workflow-runs/index.json", "{}")
        val project = Project(
            version = Project.CURRENT_VERSION,
            name = "runs",
            renderFormat = RenderFormat(),
            envelope = ProjectV4Envelope(stageRuns = ProjectStageRunManifestReference(
                ArtifactRef(StageRunStore.INDEX_FILE, sha256(root.resolve("workflow-runs/index.json")))
            ))
        )
        assertTrue(project.validate(root).isValid)
        write("workflow-runs/index.json", "tampered")
        assertFalse(project.validate(root).isValid)
    }

    @Test
    fun `one resolved preset may serve different logical instruments in one occurrence`() {
        val fingerprint = "a".repeat(64)
        write("source/A.mid", "source")
        val assignment = { logical: String -> ArrangementAssignmentReference(
            occurrenceId = "occ-1", instrumentId = "shared-preset", decisionSha256 = fingerprint,
            libraryProvenance = LibraryProvenanceSnapshot("library", fingerprint, fingerprint), logicalInstrument = logical
        ) }
        val project = Project(
            version = Project.CURRENT_VERSION, name = "shared-preset", renderFormat = RenderFormat(),
            parts = listOf(SongPart("A", "source/A.mid", importPending = true)),
            envelope = ProjectV4Envelope(
                structureOccurrences = listOf(StructureOccurrence("occ-1", "A")),
                arrangementAssignments = listOf(assignment("piano"), assignment("pad"))
            )
        )

        assertTrue(project.validate(root).isValid)
    }

    private fun write(relative: String, content: String) {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val bytes = input.readBytes()
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

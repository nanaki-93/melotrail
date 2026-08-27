package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.name
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlinx.coroutines.runBlocking

class MidiCoreMidiPackageExporterTest {
    @TempDir lateinit var root: Path

    @Test
    fun `publishes a complete package records snapshot and reopens it`() {
        val store = MidiCoreArtifactStore()
        val initial = readySession(store, root.resolve("complete-project"))
        val accepted = acceptAll(store, initial)
        val sourceBefore = Files.readAllBytes(accepted.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val candidateBefore = Files.readAllBytes(accepted.root.resolve(accepted.project.candidates.first().midi.path.value))

        val result = exporter(store, "export-1").export(ExportMidiCorePackage(accepted))
        val exported = assertIs<MidiCoreMidiPackageExportResult.Exported>(result).packageResult

        assertEquals(
            listOf("complete-song.mid", "melody.mid", "chords.mid", "bass.mid", "drums.mid"),
            exported.files.map { it.filename },
        )
        assertEquals(
            listOf("complete-song.mid", "melody.mid", "chords.mid", "bass.mid", "drums.mid", "manifest.json"),
            Files.list(exported.directory).use { paths -> paths.map(Path::getFileName).map(Path::toString).toList().sortedWith(fileOrder()) },
        )
        assertEquals(6, exported.snapshot.files.size)
        assertEquals(3, exported.snapshot.acceptedCandidates.size)
        assertEquals(CandidateRole.entries, exported.snapshot.enabledRoles)
        assertEquals(exported.snapshot, store.openProject(accepted.root).exportSnapshots.single())
        assertEquals(accepted.project.revision + 1L, exported.session.project.revision)
        assertTrue(exported.files.all { file -> Files.isRegularFile(exported.directory.resolve(file.filename)) })
        assertTrue(Files.isRegularFile(exported.directory.resolve("manifest.json")))

        val manifest = Files.readString(exported.directory.resolve("manifest.json"))
        assertTrue(manifest.contains("\"manifestSchemaVersion\": 1"))
        assertTrue(manifest.contains("\"projectId\": \"${accepted.project.id.value}\""))
        assertTrue(manifest.contains("\"snapshotId\": \"export-1\""))
        assertTrue(manifest.contains("\"source\""))
        assertTrue(manifest.contains("\"selectedMelody\""))
        assertTrue(manifest.contains("\"chordEvents\""))
        assertTrue(manifest.contains("\"instrumentSuggestion\""))
        assertTrue(manifest.contains("\"semanticReimportedMidiFiles\": 5"))
        assertFalse(manifest.contains(accepted.root.toString()))
        assertFalse(Files.list(exported.directory.parent).use { paths -> paths.anyMatch { it.name.contains(".staging-") } })
        assertContentEquals(sourceBefore, Files.readAllBytes(accepted.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        assertContentEquals(candidateBefore, Files.readAllBytes(accepted.root.resolve(accepted.project.candidates.first().midi.path.value)))

        exported.files.forEach { file ->
            val reopened = JdkMidiReader().inspect(exported.directory.resolve(file.filename))
            assertEquals(file.validation.trackNames, reopened.trackSummaries.map { it.name })
            assertEquals(file.validation.songEndTick, reopened.sourceEndTick)
        }
    }

    @Test
    fun `omits disabled generated roles and records portable role enablement`() {
        val store = MidiCoreArtifactStore()
        val accepted = acceptAll(store, readySession(store, root.resolve("optional-project")))

        val result = exporter(store, "export-optional").export(
            ExportMidiCorePackage(accepted, enabledRoles = setOf(CandidateRole.CHORDS)),
        )
        val exported = assertIs<MidiCoreMidiPackageExportResult.Exported>(result).packageResult

        assertEquals(listOf("complete-song.mid", "melody.mid", "chords.mid"), exported.files.map { it.filename })
        assertEquals(listOf(CandidateRole.CHORDS), exported.snapshot.enabledRoles)
        val complete = JdkMidiReader().inspect(exported.directory.resolve("complete-song.mid"))
        assertEquals(listOf("Conductor", "Melody", "Chords"), complete.trackSummaries.map { it.name })
        val manifest = Files.readString(exported.directory.resolve("manifest.json"))
        assertTrue(manifest.contains("\"role\": \"chords\",\n            \"enabled\": true"))
        assertTrue(manifest.contains("\"role\": \"bass\",\n            \"enabled\": false"))
        assertTrue(manifest.contains("\"role\": \"drums\",\n            \"enabled\": false"))
    }

    @Test
    fun `refuses missing and unaccepted role evidence without changing project state`() {
        val store = MidiCoreArtifactStore()
        val missing = readySession(store, root.resolve("missing-project"))
        val missingResult = exporter(store, "export-missing").export(ExportMidiCorePackage(missing))
        assertEquals(
            MidiCorePackageExportProblemCode.MISSING_ACCEPTANCE,
            assertIs<MidiCoreMidiPackageExportResult.Rejected>(missingResult).problem.code,
        )
        assertEquals(missing.project, store.openProject(missing.root))
        assertFalse(Files.exists(missing.root.resolve("exports/export-missing")))

        val unacceptedStore = MidiCoreArtifactStore()
        val unaccepted = publishOne(unacceptedStore, readySession(unacceptedStore, root.resolve("unaccepted-project")))
        val unacceptedResult = exporter(unacceptedStore, "export-unaccepted").export(ExportMidiCorePackage(unaccepted))
        val problem = assertIs<MidiCoreMidiPackageExportResult.Rejected>(unacceptedResult).problem
        assertEquals(MidiCorePackageExportProblemCode.CANDIDATE_NOT_ACCEPTED, problem.code)
        assertEquals(CandidateRole.CHORDS, problem.role)
        assertEquals(unaccepted.project, unacceptedStore.openProject(unaccepted.root))
        assertFalse(Files.exists(unaccepted.root.resolve("exports/export-unaccepted")))
    }

    @Test
    fun `refuses stale accepted evidence`() {
        val store = MidiCoreArtifactStore()
        val accepted = acceptAll(store, readySession(store, root.resolve("stale-project")))
        val changed = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    accepted,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "Db", 0, 480)),
                ),
            ),
        ).session

        val result = exporter(store, "export-stale").export(ExportMidiCorePackage(changed))
        assertEquals(
            MidiCorePackageExportProblemCode.CANDIDATE_STALE,
            assertIs<MidiCoreMidiPackageExportResult.Rejected>(result).problem.code,
        )
        assertFalse(Files.exists(changed.root.resolve("exports/export-stale")))
    }

    @Test
    fun `cleans tampered staging and refuses silent overwrite`() {
        val store = MidiCoreArtifactStore()
        val accepted = acceptAll(store, readySession(store, root.resolve("staging-project")))
        val failing = MidiCoreMidiPackageExporter(
            artifacts = store,
            snapshotLifecycle = snapshotLifecycle(store),
            clock = fixedClock(),
            snapshotIdFactory = { "export-failed" },
            beforePublish = { staging -> Files.write(staging.resolve("bass.mid"), byteArrayOf(0x00)) },
        )
        val failed = failing.export(ExportMidiCorePackage(accepted))
        assertEquals(
            MidiCorePackageExportProblemCode.STAGING_FAILED,
            assertIs<MidiCoreMidiPackageExportResult.Rejected>(failed).problem.code,
        )
        assertFalse(Files.exists(accepted.root.resolve("exports/export-failed")))
        assertFalse(Files.list(accepted.root.resolve("exports")).use { paths -> paths.anyMatch { it.name.contains(".staging-") } })
        assertEquals(accepted.project, store.openProject(accepted.root))

        val published = assertIs<MidiCoreMidiPackageExportResult.Exported>(
            exporter(store, "export-collision").export(ExportMidiCorePackage(accepted)),
        ).packageResult
        val before = Files.readAllBytes(published.directory.resolve("complete-song.mid"))
        val collision = exporter(store, "export-collision").export(ExportMidiCorePackage(published.session))
        assertEquals(
            MidiCorePackageExportProblemCode.SNAPSHOT_ID_COLLISION,
            assertIs<MidiCoreMidiPackageExportResult.Rejected>(collision).problem.code,
        )
        assertContentEquals(before, Files.readAllBytes(published.directory.resolve("complete-song.mid")))
    }

    @Test
    fun `uses deterministic MIDI hashes for equal inputs`() {
        val firstStore = MidiCoreArtifactStore()
        val first = acceptAll(firstStore, readySession(firstStore, root.resolve("deterministic-one")))
        val secondStore = MidiCoreArtifactStore()
        val second = acceptAll(secondStore, readySession(secondStore, root.resolve("deterministic-two")))

        val firstExport = assertIs<MidiCoreMidiPackageExportResult.Exported>(
            exporter(firstStore, "export-deterministic").export(ExportMidiCorePackage(first)),
        ).packageResult
        val secondExport = assertIs<MidiCoreMidiPackageExportResult.Exported>(
            exporter(secondStore, "export-deterministic").export(ExportMidiCorePackage(second)),
        ).packageResult

        assertEquals(firstExport.files.map { it.sha256 }, secondExport.files.map { it.sha256 })
        assertEquals(firstExport.files.map { it.filename }, secondExport.files.map { it.filename })
    }

    private fun exporter(store: MidiCoreArtifactStore, snapshotId: String) = MidiCoreMidiPackageExporter(
        artifacts = store,
        snapshotLifecycle = snapshotLifecycle(store),
        clock = fixedClock(),
        snapshotIdFactory = { snapshotId },
    )

    private fun snapshotLifecycle(store: MidiCoreArtifactStore) = MidiCoreExportSnapshotLifecycle(
        artifacts = store,
        clock = fixedClock(),
        idFactory = { "unused" },
    )

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)

    private fun acceptAll(store: MidiCoreArtifactStore, initial: MidiCoreProjectSession): MidiCoreProjectSession {
        var session = initial
        CandidateRole.entries.forEachIndexed { index, role ->
            val candidate = publishCandidate(store, session, role, "candidate-${role.name.lowercase()}", index)
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                MidiCoreCandidateReview(artifacts = store).accept(
                    AcceptMidiCoreCandidate(candidate.session, candidate.candidate.id),
                ),
            ).session
        }
        return session
    }

    private fun publishOne(store: MidiCoreArtifactStore, initial: MidiCoreProjectSession): MidiCoreProjectSession {
        val published = publishCandidate(store, initial, CandidateRole.CHORDS, "candidate-chords", 0)
        val project = published.session.project.copy(
            acceptances = listOf(CandidateAcceptance("verse-1", CandidateRole.CHORDS, published.candidate.id, locked = false)),
            revision = published.session.project.revision + 1L,
        )
        store.saveProject(published.session.root, project)
        return MidiCoreProjectSession(published.session.root, project)
    }

    private fun publishCandidate(
        store: MidiCoreArtifactStore,
        session: MidiCoreProjectSession,
        role: CandidateRole,
        candidateId: String,
        index: Int,
    ) = runBlocking {
        val patternId = when (role) {
            CandidateRole.CHORDS -> "chords.rhythm.sustained"
            CandidateRole.BASS -> "bass.sustained-root"
            CandidateRole.DRUMS -> "drums.dusty-straight"
        }
        val result = MidiCoreCandidateGeneration(artifacts = store).generate(
            GenerateMidiCoreCandidate(
                session = session,
                role = role,
                occurrenceId = "verse-1",
                performanceProfileId = when (role) {
                    CandidateRole.CHORDS -> "chords.sustained"
                    CandidateRole.BASS -> "bass.sustained-sub-like"
                    CandidateRole.DRUMS -> "drums.dusty"
                },
                patternId = when (role) {
                    CandidateRole.CHORDS -> "chords.rhythm.sustained"
                    CandidateRole.BASS -> "bass.sustained-root"
                    CandidateRole.DRUMS -> "drums.dusty-straight"
                },
                generator = app.melotrail.project.MidiCoreGeneratorInput(
                    "exporter-test",
                    "exporter-test-v1",
                    patternId,
                    index.toLong(),
                ),
                sectionPolicy = app.melotrail.arrangement.core.MidiCoreSectionPolicy(density = 1.0),
                candidateId = candidateId,
            ),
        )
        assertIs<MidiCoreCandidateGenerationResult.Published>(result, result.toString())
    }

    private fun readySession(store: MidiCoreArtifactStore, projectRoot: Path): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(artifacts = store).create(
                CreateMidiCoreProject(projectRoot, "Exporter Test", "exporter-${projectRoot.fileName}"),
            ),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixture-${projectRoot.fileName}"))
            .single { it.fileName.toString() == "smf0-melody.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        val selected = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(imported, 0, 0)),
        ).session
        val authority = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    selected,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
                ),
            ),
        ).session
        val structured = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    authority,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(app.melotrail.structure.MidiCoreOccurrencePlacement("verse-1", "verse", "Verse", 480)),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(structured, listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 480))),
            ),
        ).session
    }

    private fun fileOrder() = compareBy<String> { filename ->
        listOf("complete-song.mid", "melody.mid", "chords.mid", "bass.mid", "drums.mid", "manifest.json").indexOf(filename)
    }
}

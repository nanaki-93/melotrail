package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreDerivedWorkKind
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.project.CandidateRole
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreAuthoritativeHarmonyTest {
    @TempDir lateinit var root: Path

    @Test
    fun `binds explicit sub-bar chromatic harmony and reopens it unchanged`() {
        val store = MidiCoreArtifactStore()
        val session = structured(store)
        val result = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    session,
                    listOf(
                        AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 240),
                        AuthoritativeChordEvent("chord-2", "verse-1", "Dbmaj9/F", 240, 480),
                    ),
                ),
            ),
        )

        assertEquals(listOf("C", "Dbmaj9/F"), result.timeline.forOccurrence("verse-1").map { it.event.symbol })
        assertTrue(result.validation.findings.any { it.code.name == "CHROMATIC_CHORD" })
        assertEquals(result.session.project, store.openProject(result.session.root))
    }

    @Test
    fun `blocking harmony findings leave project bytes unchanged`() {
        val store = MidiCoreArtifactStore()
        val session = structured(store)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = assertIs<MidiCoreAuthoritativeHarmonyResult.Rejected>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    session,
                    listOf(AuthoritativeChordEvent("bad", "verse-1", "C", 0, 239)),
                ),
            ),
        )

        assertEquals(MidiCoreAuthoritativeHarmonyProblemCode.INVALID_HARMONY, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    @Test
    fun `save failure preserves the prior empty harmony authority`() {
        val store = MidiCoreArtifactStore()
        val session = structured(store)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        val failingStore = MidiCoreArtifactStore { _, _ -> throw java.io.IOException("simulated save failure") }
        val result = assertIs<MidiCoreAuthoritativeHarmonyResult.Rejected>(
            MidiCoreAuthoritativeHarmony(failingStore).replace(
                ReplaceMidiCoreHarmony(
                    session,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 480)),
                ),
            ),
        )

        assertEquals(MidiCoreAuthoritativeHarmonyProblemCode.SAVE_FAILED, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    @Test
    fun `authority edit persists while previewing the affected candidate`() {
        val store = MidiCoreArtifactStore()
        val session = structured(store)
        val source = OwnedMidiFixtures.writeAll(root.resolve("candidate-fixtures-${store.hashCode()}"))
            .single { it.fileName.toString() == "smf0-melody.mid" }
        val candidateMidi = store.publishCandidateMidi(session.root, CandidateRole.CHORDS, "verse-1", "candidate-1", source)
        val candidateReport = store.publishCandidateReport(session.root, "candidate-1", "{}")
        val authorityHash = MidiCoreAuthorityHasher.from(session.project)
            .scopeHash("verse-1", CandidateRole.CHORDS)
        val candidate = MidiCoreCandidate(
            "candidate-1",
            CandidateRole.CHORDS,
            "verse-1",
            "chords-v1",
            authorityHash,
            42,
            candidateMidi,
            candidateReport,
            "2026-08-27T00:00:00Z",
        )
        val withCandidate = session.project.copy(candidates = listOf(candidate))
        store.saveProject(session.root, withCandidate)
        val current = MidiCoreProjectSession(session.root, withCandidate)

        val result = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    current,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "Db", 0, 480)),
                ),
            ),
        )

        assertEquals(listOf("candidate-1"), result.invalidation.staleCandidateIds)
        assertEquals(listOf(MidiCoreDerivedWorkKind.CANDIDATE), result.invalidation.staleTargets.map { it.kind })
        assertEquals("Db", requireNotNull(store.openProject(session.root).authority).chordEvents.single().symbol)
    }

    private fun structured(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle(store).create(CreateMidiCoreProject(root.resolve("project-${store.hashCode()}"), "Harmony Test", "project-1")),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures-${store.hashCode()}"))
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
                    app.melotrail.project.ProjectKey(app.melotrail.music.core.ProjectKeySpelling.C, app.melotrail.music.core.ProjectScaleMode.MAJOR),
                    app.melotrail.music.core.ProjectTempo(500_000),
                    app.melotrail.music.core.ProjectMeter(4, 2),
                ),
            ),
        ).session
        return assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    authority,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Verse", 480)),
                ),
            ),
        ).session
    }

    private fun lifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "generated-project" },
    )
}

package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.AtomicWriteObserver
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreOccurrencePlacement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreStructureTimelineTest {
    @TempDir lateinit var root: Path

    @Test
    fun `persists an exact repeated timeline and deterministic sanitized markers`() {
        val store = MidiCoreArtifactStore()
        val session = authoritative(store)
        val result = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    session,
                    listOf(ProjectSectionDefinition("verse", "Verse"), ProjectSectionDefinition("chorus", "Chorus")),
                    listOf(
                        MidiCoreOccurrencePlacement("verse-1", "verse", "Verse   one", 240),
                        MidiCoreOccurrencePlacement("chorus-1", "chorus", "Chorus", 240),
                        MidiCoreOccurrencePlacement("verse-2", "verse", "Verse repeat", 480, 480),
                    ),
                    pickupTicks = 120,
                    expectedSongEndTick = 960,
                ),
            ),
        )

        val authority = requireNotNull(result.session.project.authority)
        assertEquals(listOf(0L to 240L, 240L to 480L, 480L to 960L), authority.occurrences.map { it.startTick to it.endTick })
        assertEquals(120L, authority.pickupTicks)
        assertEquals(listOf("1:Verse one", "2:Chorus", "3:Verse repeat"), result.markerLabels)
        assertEquals(result.session.project, store.openProject(result.session.root))

        val persisted = Json.parseToJsonElement(Files.readString(result.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))).jsonObject
            .getValue("project").jsonObject.getValue("authority").jsonObject
        assertEquals("120", persisted.getValue("pickupTicks").jsonPrimitive.content)
    }

    @Test
    fun `rejects a timeline that does not cover the imported song and preserves project bytes`() {
        val store = MidiCoreArtifactStore()
        val session = authoritative(store)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))

        val result = assertIs<MidiCoreStructureTimelineResult.Rejected>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    session,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Verse", 479)),
                ),
            ),
        )

        assertEquals(MidiCoreStructureTimelineProblemCode.INVALID_STRUCTURE, result.problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    @Test
    fun `does not discard authoritative harmony or immutable work during a structure edit`() {
        val store = MidiCoreArtifactStore()
        val session = authoritative(store)
        val structured = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    session,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Verse", 480)),
                ),
            ),
        ).session
        val withHarmony = structured.project.copy(
            authority = requireNotNull(structured.project.authority).copy(
                chordEvents = listOf(app.melotrail.project.AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 480)),
            ),
        )
        store.saveProject(session.root, withHarmony)
        val current = MidiCoreProjectSession(structured.root, withHarmony)

        val result = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    current,
                    withHarmony.authority!!.sectionDefinitions,
                    listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Renamed", 480)),
                ),
            ),
        )

        assertEquals("C", requireNotNull(result.session.project.authority).chordEvents.single().symbol)
    }

    @Test
    fun `save failure leaves the previous timeline readable`() {
        var fail = false
        val store = MidiCoreArtifactStore(AtomicWriteObserver { temporary, target ->
            if (fail && target.fileName.toString() == MidiCoreArtifactStore.PROJECT_FILE) {
                Files.writeString(temporary, "partial")
                throw IOException("simulated structure save failure")
            }
        })
        val session = authoritative(store)
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        fail = true

        val result = MidiCoreStructureTimeline(store).replace(
            ReplaceMidiCoreStructure(
                session,
                listOf(ProjectSectionDefinition("verse", "Verse")),
                listOf(MidiCoreOccurrencePlacement("verse-1", "verse", "Verse", 480)),
            ),
        )

        assertEquals(MidiCoreStructureTimelineProblemCode.SAVE_FAILED, assertIs<MidiCoreStructureTimelineResult.Rejected>(result).problem.code)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertTrue(Files.exists(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
    }

    private fun authoritative(store: MidiCoreArtifactStore): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle(store).create(CreateMidiCoreProject(root.resolve("project-${store.hashCode()}"), "Structure Test", "project-1")),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures-${store.hashCode()}"))
            .single { it.fileName.toString() == "smf0-melody.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        val selected = assertIs<MidiCoreMelodySelectionResult.Selected>(
            MidiCoreMelodySelection(store).select(SelectMidiCoreMelody(imported, 0, 0)),
        ).session
        return assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    selected,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
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

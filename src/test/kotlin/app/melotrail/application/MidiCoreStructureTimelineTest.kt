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
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
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
                        MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse   one", 1),
                        MidiCoreBarOccurrencePlacement("chorus-1", "chorus", "Chorus", 1),
                        MidiCoreBarOccurrencePlacement("verse-2", "verse", "Verse repeat", 1),
                    ),
                ),
            ),
        )

        val authority = requireNotNull(result.session.project.authority)
        assertEquals(listOf(0L to 1920L, 1920L to 3840L, 3840L to 5760L), authority.occurrences.map { it.startTick to it.endTick })
        assertEquals(0L, authority.pickupTicks)
        assertEquals(listOf("1:Verse one", "2:Chorus", "3:Verse repeat"), result.markerLabels)
        assertEquals(result.session.project, store.openProject(result.session.root))

        val persisted = Json.parseToJsonElement(Files.readString(result.session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))).jsonObject
            .getValue("project").jsonObject.getValue("authority").jsonObject
        assertEquals("0", persisted.getValue("pickupTicks").jsonPrimitive.content)
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
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 2)),
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
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 3)),
                ),
            ),
        ).session
        val withHarmony = structured.project.copy(
            authority = requireNotNull(structured.project.authority).copy(
                chordEvents = listOf(app.melotrail.project.AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 5760)),
            ),
        )
        store.saveProject(session.root, withHarmony)
        val current = MidiCoreProjectSession(structured.root, withHarmony)

        val result = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    current,
                    withHarmony.authority!!.sectionDefinitions,
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Renamed", 3)),
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
                listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 3)),
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
            .single { it.fileName.toString() == "whole-song-three-bars.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        return assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    imported,
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

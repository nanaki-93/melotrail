package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreSectionPolicy
import app.melotrail.audition.MidiAuditionController
import app.melotrail.audition.MidiAuditionOutput
import app.melotrail.audition.MidiAuditionOutputListener
import app.melotrail.audition.MidiAuditionOutputSession
import app.melotrail.audition.MidiAuditionPlaybackPlan
import app.melotrail.audition.MidiAuditionResult
import app.melotrail.audition.MidiAuditionScope
import app.melotrail.audition.MidiAuditionView
import app.melotrail.audition.MidiAuditionLoop
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.domain.MidiExportRole
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** JVM-only proof that the target MIDI Core kernel composes without legacy services. */
class MidiCoreVerticalSliceTest {
    @TempDir lateinit var root: Path

    @Test
    fun `completes the target project to auditioned and semantically reimported MIDI package`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val projectRoot = root.resolve("vertical-project")
        val lifecycle = projectLifecycle(store)
        var session = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            lifecycle.create(CreateMidiCoreProject(projectRoot, "Vertical Slice", "vertical-project", "mc-030")),
        ).session

        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures")).single { it.fileName.toString() == "whole-song-one-bar.mid" }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(session, source)),
        )
        session = imported.session
        assertEquals(1, session.project.sourceMidi?.format)
        assertEquals(480, session.project.sourceMidi?.ppq)

        assertEquals(1, session.project.selectedMelody?.trackIndex)
        assertEquals(0, session.project.selectedMelody?.channel)

        session = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    session,
                    ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR),
                    ProjectTempo(500_000),
                    ProjectMeter(4, 2),
                ),
            ),
        ).session
        assertEquals(ProjectKeySpelling.C, session.project.authority?.key?.spelling)
        assertEquals(ProjectTempo(500_000), session.project.authority?.tempo)
        assertEquals(ProjectMeter(4, 2), session.project.authority?.meter)

        session = assertIs<MidiCoreStructureTimelineResult.Updated>(
            MidiCoreStructureTimeline(store).replace(
                ReplaceMidiCoreStructure(
                    session,
                    listOf(ProjectSectionDefinition("verse", "Verse")),
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1)),
                ),
            ),
        ).session
        assertEquals(listOf(0L to 1920L), session.project.authority?.occurrences?.map { it.startTick to it.endTick })

        session = assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    session,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 1920)),
                ),
            ),
        ).session
        assertEquals("C", session.project.authority?.chordEvents?.single()?.symbol)

        val missingAcceptance = export(store, "vertical-missing").export(ExportMidiCorePackage(session))
        assertEquals(
            MidiCorePackageExportProblemCode.MISSING_ACCEPTANCE,
            assertIs<MidiCoreMidiPackageExportResult.Rejected>(missingAcceptance).problem.code,
        )
        assertFalse(Files.exists(projectRoot.resolve("exports/vertical-missing")))
        assertEquals(session.project, store.openProject(projectRoot))

        val review = MidiCoreCandidateReview(artifacts = store)
        val generation = MidiCoreCandidateGeneration(artifacts = store)
        val candidateBytesDuringRegeneration = linkedMapOf<String, ByteArray>()
        CandidateRole.entries.forEach { role ->
            val alternatives = alternatives(role)
            val first = assertIs<MidiCoreCandidateGenerationResult.Published>(
                generation.generate(candidateRequest(session, role, "${role.name.lowercase()}-first", alternatives.first(), 101L + role.ordinal)),
            )
            session = first.session
            val firstBytes = Files.readAllBytes(session.root.resolve(first.candidate.midi.path.value))
            candidateBytesDuringRegeneration[first.candidate.id] = firstBytes

            val second = assertIs<MidiCoreCandidateGenerationResult.Published>(
                review.regenerate(
                    RegenerateMidiCoreCandidate(
                        candidateRequest(session, role, "${role.name.lowercase()}-second", alternatives[1], 201L + role.ordinal),
                        expectedRevision = session.project.revision,
                    ),
                ),
            )
            session = second.session
            assertContentEquals(firstBytes, Files.readAllBytes(session.root.resolve(first.candidate.midi.path.value)))
            val compared = assertIs<MidiCoreCandidateReviewResult.Compared>(
                review.compare(
                    CompareMidiCoreCandidates(
                        session,
                        role,
                        "verse-1",
                        first.candidate.id,
                        second.candidate.id,
                    ),
                ),
            )
            assertTrue(compared.differences.isNotEmpty(), "Expected distinct ${role.name.lowercase()} alternatives")

            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                review.reject(RejectMidiCoreCandidate(session, second.candidate.id, "Alternative not selected for the vertical slice")),
            ).session
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                review.accept(AcceptMidiCoreCandidate(session, first.candidate.id)),
            ).session
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                review.lock(LockMidiCoreCandidate(session, first.candidate.id)),
            ).session
        }

        assertEquals(6, session.project.candidates.size)
        assertEquals(3, session.project.candidates.count { it.status == MidiCoreCandidateStatus.ACCEPTED })
        assertEquals(3, session.project.candidates.count { it.status == MidiCoreCandidateStatus.REJECTED })
        assertEquals(CandidateRole.entries, session.project.acceptances.map { it.role })
        assertTrue(session.project.acceptances.all { it.locked })
        candidateBytesDuringRegeneration.forEach { (candidateId, bytes) ->
            val candidate = session.project.candidates.single { it.id == candidateId }
            assertContentEquals(bytes, Files.readAllBytes(session.root.resolve(candidate.midi.path.value)))
        }

        session = assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle.open(projectRoot)).session
        assertEquals(6, session.project.candidates.size)
        val sourceBeforeAuditionAndExport = Files.readAllBytes(projectRoot.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val candidateBytesBeforeExport = session.project.candidates.associate { candidate ->
            candidate.id to Files.readAllBytes(projectRoot.resolve(candidate.midi.path.value))
        }

        val assembled = assertIs<MidiCoreAcceptedSongAssemblyResult.Assembled>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(session)),
        ).review
        assertEquals(listOf("Melody", "Chords", "Bass", "Drums"), assembled.song.roles.map { it.role.trackName })
        assertEquals(1920L, assembled.song.songEndTick)
        assertEquals(3, assembled.acceptedCandidates.size)

        val fakeOutput = RecordingMidiOutput()
        val audition = MidiAuditionController(fakeOutput)
        val auditionPlan = MidiAuditionPlaybackPlan(MidiAuditionView.accepted(assembled.song))
        assertIs<MidiAuditionResult.Applied>(audition.play(auditionPlan))
        assertEquals(MidiAuditionScope.AcceptedArrangement, audition.state.scope)
        assertIs<MidiAuditionResult.Applied>(audition.setMutedRole(MidiExportRole.BASS, true))
        assertIs<MidiAuditionResult.Applied>(audition.setSoloRole(MidiExportRole.MELODY, true))
        assertIs<MidiAuditionResult.Applied>(audition.setLoop(MidiAuditionLoop(120, 360)))
        assertIs<MidiAuditionResult.Applied>(audition.seek(120))
        assertIs<MidiAuditionResult.Applied>(audition.stop())
        assertTrue(fakeOutput.session.closed)
        assertTrue(fakeOutput.session.stopCalled)
        audition.close()
        assertTrue(fakeOutput.closed)
        assertContentEquals(sourceBeforeAuditionAndExport, Files.readAllBytes(projectRoot.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))

        val exported = assertIs<MidiCoreMidiPackageExportResult.Exported>(
            export(store, "vertical-export").export(ExportMidiCorePackage(session)),
        ).packageResult
        assertEquals(
            listOf("complete-song.mid", "melody.mid", "chords.mid", "bass.mid", "drums.mid"),
            exported.files.map { it.filename },
        )
        assertEquals(
            listOf("bass.mid", "chords.mid", "complete-song.mid", "drums.mid", "manifest.json", "melody.mid"),
            Files.list(exported.directory).use { paths -> paths.map { it.fileName.toString() }.sorted().toList() },
        )
        exported.files.forEach { file ->
            val path = exported.directory.resolve(file.filename)
            val reimported = JdkMidiReader().inspect(path)
            assertEquals(file.validation.trackNames, reimported.trackSummaries.map { it.name })
            assertEquals(file.validation.songEndTick, reimported.sourceEndTick)
            assertEquals(1, reimported.sequence.source.format)
            assertEquals(480, reimported.sequence.source.ppq.value)
            assertEquals(file.sha256, sha256(Files.readAllBytes(path)))
        }
        exported.snapshot.files.forEach { file ->
            assertEquals(file.artifact.sha256, sha256(Files.readAllBytes(projectRoot.resolve(file.artifact.path.value))))
        }
        val manifestPath = exported.directory.resolve("manifest.json")
        val manifest = Files.readString(manifestPath)
        assertTrue(manifest.contains("\"projectId\": \"vertical-project\""))
        assertTrue(manifest.contains("\"semanticReimportedMidiFiles\": 5"))
        assertFalse(manifest.contains(projectRoot.toString()))
        assertEquals(exported.manifestSha256, sha256(Files.readAllBytes(manifestPath)))
        assertContentEquals(sourceBeforeAuditionAndExport, Files.readAllBytes(projectRoot.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
        candidateBytesBeforeExport.forEach { (candidateId, bytes) ->
            val candidate = session.project.candidates.single { it.id == candidateId }
            assertContentEquals(bytes, Files.readAllBytes(projectRoot.resolve(candidate.midi.path.value)))
        }

        val reopened = assertIs<MidiCoreProjectLifecycleResult.Opened>(lifecycle.open(projectRoot)).session
        assertEquals(exported.session.project, reopened.project)
        assertEquals(exported.snapshot, reopened.project.exportSnapshots.single())
    }

    private fun candidateRequest(
        session: MidiCoreProjectSession,
        role: CandidateRole,
        candidateId: String,
        alternative: Alternative,
        seed: Long,
    ) = GenerateMidiCoreCandidate(
        session = session,
        role = role,
        occurrenceId = "verse-1",
        performanceProfileId = alternative.profileId,
        patternId = alternative.patternId,
        generator = MidiCoreGeneratorInput("vertical-slice", "vertical-slice-v1", alternative.patternId, seed),
        sectionPolicy = MidiCoreSectionPolicy(density = 1.0),
        candidateId = candidateId,
    )

    private fun alternatives(role: CandidateRole): List<Alternative> = when (role) {
        CandidateRole.CHORDS -> listOf(
            Alternative("chords.sustained", "chords.rhythm.sustained"),
            Alternative("chords.pulsed", "chords.rhythm.laid-back-quarters"),
        )
        CandidateRole.BASS -> listOf(
            Alternative("bass.sustained-sub-like", "bass.sustained-root"),
            Alternative("bass.muted-plucked", "bass.root-fifth"),
        )
        CandidateRole.DRUMS -> listOf(
            Alternative("drums.dusty", "drums.dusty-straight"),
            Alternative("drums.lifted", "drums.lazy-swing"),
        )
    }

    private fun export(store: MidiCoreArtifactStore, snapshotId: String) = MidiCoreMidiPackageExporter(
        artifacts = store,
        snapshotLifecycle = MidiCoreExportSnapshotLifecycle(
            artifacts = store,
            clock = fixedClock(),
            idFactory = { "unused-snapshot" },
        ),
        clock = fixedClock(),
        snapshotIdFactory = { snapshotId },
    )

    private fun projectLifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        clock = fixedClock(),
        idFactory = { "vertical-project" },
    )

    private fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class Alternative(val profileId: String, val patternId: String)
}

private class RecordingMidiOutput : MidiAuditionOutput {
    lateinit var session: RecordingMidiSession
    var closed = false

    override fun open(plan: MidiAuditionPlaybackPlan, listener: MidiAuditionOutputListener): MidiAuditionOutputSession =
        RecordingMidiSession(plan).also { session = it }

    override fun close() {
        closed = true
    }
}

private class RecordingMidiSession(val plan: MidiAuditionPlaybackPlan) : MidiAuditionOutputSession {
    var closed = false
    var stopCalled = false

    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() {
        stopCalled = true
    }
    override fun seek(tick: Long) = Unit
    override fun setLoop(loop: MidiAuditionLoop?) = Unit
    override fun setMutedRoles(roles: Set<MidiExportRole>) = Unit
    override fun setSoloRoles(roles: Set<MidiExportRole>) = Unit

    override fun close() {
        closed = true
    }
}

package app.melotrail.application

import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.midi.adapter.JdkMidiWriter
import app.melotrail.midi.domain.MidiEventOrderingKey
import app.melotrail.midi.domain.MidiExportMarker
import app.melotrail.midi.domain.MidiExportSong
import app.melotrail.midi.domain.MidiMarkerEvent
import app.melotrail.midi.domain.MidiNoteEvent
import app.melotrail.midi.domain.MidiSemanticEventKind
import app.melotrail.midi.domain.MidiPpq
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptanceHistory
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreAcceptanceAction
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreAcceptedSongAssemblyTest {
    @TempDir lateinit var root: Path

    @Test
    fun `assembles repeated bar-defined occurrences with sub-bar harmony and source identity`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(
            store,
            root.resolve("repeated-project"),
            sourceFixture = "whole-song-two-bars.mid",
            placements = listOf(
                MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse one", 1),
                MidiCoreBarOccurrencePlacement("verse-2", "verse", "Verse repeat", 1),
            ),
            harmony = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 960),
                AuthoritativeChordEvent("chord-2", "verse-1", "G", 960, 1920),
                AuthoritativeChordEvent("chord-3", "verse-2", "C", 1920, 2880),
                AuthoritativeChordEvent("chord-4", "verse-2", "G", 2880, 3840),
            ),
        )
        val sourceBefore = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value))
        val accepted = acceptAll(store, session, listOf("verse-1", "verse-2"))

        val assemblyResult = MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(accepted))
        assertTrue(assemblyResult is MidiCoreAcceptedSongAssemblyResult.Assembled, assemblyResult.toString())
        val assembled = (assemblyResult as MidiCoreAcceptedSongAssemblyResult.Assembled).review
        val song = assembled.song

        assertEquals(3840L, song.songEndTick)
        assertEquals(MidiPpq(480), song.ppq)
        assertEquals(listOf(0L, 1920L), song.markers.map(MidiExportMarker::tick))
        assertEquals(listOf("Melody", "Chords", "Bass", "Drums"), song.roles.map { it.role.trackName })
        assertEquals(listOf(0L, 960L, 1920L, 2880L), song.roles.first().events.filterIsInstance<MidiNoteEvent>().map { it.orderingKey.tick })
        assertEquals(setOf(0L, 960L, 1920L, 2880L), song.roles[1].events.map { it.orderingKey.tick }.toSet())
        assertEquals(setOf(0, 1, 2, 9), song.roles.map { it.role.channel }.toSet())
        assertEquals(6, assembled.acceptedCandidates.size)
        assertEquals(accepted.project.sourceMidi!!.sha256, assembled.sourceSha256)
        assertEquals(accepted.project.selectedMelody!!.identitySha256, assembled.selectedMelodyIdentitySha256)

        val output = root.resolve("assembled.mid")
        JdkMidiWriter().writeComplete(song, output)
        val inspected = JdkMidiReader().inspect(output)
        assertEquals(listOf("Conductor", "Melody", "Chords", "Bass", "Drums"), inspected.trackSummaries.map { it.name })
        assertEquals(listOf(0L, 1920L), inspected.sequence.tracks.first().events.filterIsInstance<MidiMarkerEvent>().map { it.orderingKey.tick })
        assertContentEquals(sourceBefore, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)))
    }

    @Test
    fun `accepts intentional role silence but rejects a missing accepted scope`() {
        val store = MidiCoreArtifactStore()
        val initial = readySession(store, root.resolve("silence-project"))
        val accepted = acceptAll(store, initial, listOf("verse-1"), density = 0.0)

        val assembled = assertIs<MidiCoreAcceptedSongAssemblyResult.Assembled>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assemble(AssembleMidiCoreSong(accepted)),
        ).review.song
        assertTrue(assembled.roles.drop(1).all { it.events.isEmpty() })

        val missing = accepted.project.copy(
            acceptances = accepted.project.acceptances.filterNot { it.role == CandidateRole.BASS },
            revision = accepted.project.revision + 1L,
        )
        store.saveProject(accepted.root, missing)
        val rejected = assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = store).assemble(
                AssembleMidiCoreSong(MidiCoreProjectSession(accepted.root, missing)),
            ),
        )
        assertEquals(MidiCoreSongAssemblyProblemCode.MISSING_ACCEPTANCE, rejected.problem.code)
        assertEquals(CandidateRole.BASS, rejected.problem.role)
    }

    @Test
    fun `rejects stale and digest-invalid accepted evidence`() {
        val staleStore = MidiCoreArtifactStore()
        val staleInitial = readySession(staleStore, root.resolve("stale-project"))
        val staleAccepted = acceptAll(staleStore, staleInitial, listOf("verse-1"))
        val staleProject = staleAccepted.project.copy(
            authority = staleAccepted.project.authority!!.copy(
                key = ProjectKey(ProjectKeySpelling.D, ProjectScaleMode.MAJOR),
            ),
            candidates = staleAccepted.project.candidates.map { it.copy(status = MidiCoreCandidateStatus.STALE) },
            revision = staleAccepted.project.revision + 1L,
        )
        staleStore.saveProject(staleAccepted.root, staleProject)
        val stale = assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = staleStore).assemble(
                AssembleMidiCoreSong(MidiCoreProjectSession(staleAccepted.root, staleProject)),
            ),
        )
        assertEquals(MidiCoreSongAssemblyProblemCode.CANDIDATE_STALE, stale.problem.code)

        val digestStore = MidiCoreArtifactStore()
        val digestInitial = readySession(digestStore, root.resolve("digest-project"))
        val digestAccepted = acceptAll(digestStore, digestInitial, listOf("verse-1"))
        val sourcePath = digestAccepted.root.resolve(MidiCoreArtifactStore.SOURCE_MIDI.value)
        Files.write(sourcePath, Files.readAllBytes(sourcePath) + byteArrayOf(0x01))
        val digest = assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = digestStore).assemble(AssembleMidiCoreSong(digestAccepted)),
        )
        assertEquals(MidiCoreSongAssemblyProblemCode.DIGEST_MISMATCH, digest.problem.code)
    }

    @Test
    fun `rejects a candidate that crosses its occurrence or violates its role channel`() {
        val overflowStore = MidiCoreArtifactStore()
        val overflowInitial = readySession(
            overflowStore,
            root.resolve("overflow-project"),
            sourceFixture = "whole-song-two-bars.mid",
            placements = listOf(
                MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1),
                MidiCoreBarOccurrencePlacement("verse-2", "verse", "Verse repeat", 1),
            ),
            harmony = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 1920),
                AuthoritativeChordEvent("chord-2", "verse-2", "C", 1920, 3840),
            ),
        )
        val overflowAccepted = acceptAll(overflowStore, overflowInitial, listOf("verse-1", "verse-2"))
        val overflow = replaceAcceptedCandidate(
            overflowStore,
            overflowAccepted,
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-2",
            candidateId = "overflow-candidate",
            midi = MidiExportSongForTest.roleSong(
                role = app.melotrail.midi.domain.MidiExportRole.CHORDS,
                noteStart = 0,
                noteEnd = 120,
                songEnd = 3840,
            ),
        )
        val overflowResult = assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = overflowStore).assemble(AssembleMidiCoreSong(overflow)),
        )
        assertEquals(MidiCoreSongAssemblyProblemCode.CANDIDATE_OVERFLOW, overflowResult.problem.code)

        val channelStore = MidiCoreArtifactStore()
        val channelInitial = readySession(channelStore, root.resolve("channel-project"))
        val channelAccepted = acceptAll(channelStore, channelInitial, listOf("verse-1"))
        val wrongChannel = replaceAcceptedCandidate(
            channelStore,
            channelAccepted,
            role = CandidateRole.CHORDS,
            occurrenceId = "verse-1",
            candidateId = "wrong-channel",
            midi = MidiExportSongForTest.wrongChannelSong(),
        )
        val channelResult = assertIs<MidiCoreAcceptedSongAssemblyResult.Rejected>(
            MidiCoreAcceptedSongAssembly(artifacts = channelStore).assemble(AssembleMidiCoreSong(wrongChannel)),
        )
        assertEquals(MidiCoreSongAssemblyProblemCode.CANDIDATE_CHANNEL_MISMATCH, channelResult.problem.code)
    }

    @Test
    fun `summarizes semantic additions removals and changes deterministically`() {
        val first = listOf(
            MidiCoreReviewNote(0, 120, 1, 60, 90),
            MidiCoreReviewNote(240, 360, 1, 64, 90),
        )
        val second = listOf(
            MidiCoreReviewNote(0, 120, 1, 61, 90),
            MidiCoreReviewNote(120, 240, 1, 67, 90),
        )

        val differences = MidiCoreCandidateDiff.differences(first, second)
        val summary = MidiCoreCandidateDiff.summary(differences)

        assertEquals(listOf(MidiCoreCandidateDifferenceKind.CHANGED, MidiCoreCandidateDifferenceKind.ADDED, MidiCoreCandidateDifferenceKind.REMOVED), differences.map { it.kind })
        assertEquals(MidiCoreCandidateDifferenceSummary(1, 1, 1), summary)
        assertEquals(3, summary.total)
    }

    private fun acceptAll(
        store: MidiCoreArtifactStore,
        initial: MidiCoreProjectSession,
        occurrenceIds: List<String>,
        density: Double = 1.0,
    ): MidiCoreProjectSession {
        val review = MidiCoreCandidateReview(artifacts = store)
        var session = initial
        occurrenceIds.forEachIndexed { occurrenceIndex, occurrenceId ->
            val chords = generate(store, request(session, CandidateRole.CHORDS, occurrenceId, "chords-$occurrenceIndex", density))
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                review.accept(AcceptMidiCoreCandidate(chords.session, chords.candidate.id)),
            ).session
            val bass = generate(store, request(session, CandidateRole.BASS, occurrenceId, "bass-$occurrenceIndex", density))
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                review.accept(AcceptMidiCoreCandidate(bass.session, bass.candidate.id)),
            ).session
            val drums = generate(store, request(session, CandidateRole.DRUMS, occurrenceId, "drums-$occurrenceIndex", density))
            session = assertIs<MidiCoreCandidateLifecycleResult.Updated>(
                review.accept(AcceptMidiCoreCandidate(drums.session, drums.candidate.id)),
            ).session
        }
        return session
    }

    private fun generate(store: MidiCoreArtifactStore, request: GenerateMidiCoreCandidate) = assertIs<MidiCoreCandidateGenerationResult.Published>(
        runBlocking { MidiCoreCandidateGeneration(artifacts = store).generate(request) },
    )

    private fun request(
        session: MidiCoreProjectSession,
        role: CandidateRole,
        occurrenceId: String,
        candidateId: String,
        density: Double,
    ) = GenerateMidiCoreCandidate(
        session = session,
        role = role,
        occurrenceId = occurrenceId,
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
        generator = MidiCoreGeneratorInput("assembly-test", "assembly-test-v1", when (role) {
            CandidateRole.CHORDS -> "chords.rhythm.sustained"
            CandidateRole.BASS -> "bass.sustained-root"
            CandidateRole.DRUMS -> "drums.dusty-straight"
        }, candidateId.hashCode().toLong()),
        sectionPolicy = app.melotrail.arrangement.core.MidiCoreSectionPolicy(density = density),
        candidateId = candidateId,
    )

    private fun readySession(
        store: MidiCoreArtifactStore,
        projectRoot: Path,
        sourceFixture: String = "whole-song-one-bar.mid",
        placements: List<MidiCoreBarOccurrencePlacement> = listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1)),
        harmony: List<AuthoritativeChordEvent> = listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 1920)),
    ): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            MidiCoreProjectLifecycle(artifacts = store).create(CreateMidiCoreProject(projectRoot, "Assembly Test", "assembly-project-${projectRoot.fileName}")),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures-${projectRoot.fileName}"))
            .single { it.fileName.toString() == sourceFixture }
        val imported = assertIs<MidiCoreSourceImportResult.Imported>(
            MidiCoreSourceImport(store).import(ImportMidiCoreSource(created, source)),
        ).session
        val authority = assertIs<MidiCoreAuthorityResult.Confirmed>(
            MidiCoreMusicalAuthority(store).confirm(
                ConfirmMidiCoreAuthority(
                    imported,
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
                    placements,
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(ReplaceMidiCoreHarmony(structured, harmony)),
        ).session
    }

    private fun replaceAcceptedCandidate(
        store: MidiCoreArtifactStore,
        session: MidiCoreProjectSession,
        role: CandidateRole,
        occurrenceId: String,
        candidateId: String,
        midi: MidiExportSong,
    ): MidiCoreProjectSession {
        val temporary = Files.createTempFile(root, "candidate-", ".mid")
        JdkMidiWriter().writeComplete(midi, temporary)
        if (candidateId == "wrong-channel") MidiExportSongForTest.writeWrongChannel(temporary)
        val midiArtifact = store.publishCandidateMidi(session.root, role, occurrenceId, candidateId, temporary)
        val report = app.melotrail.arrangement.core.MidiCoreRoleValidationReportJson.encode(
            app.melotrail.arrangement.core.MidiCoreRoleValidationReport(
                contextSha256 = "a".repeat(64),
                candidateSha256 = "b".repeat(64),
                role = role,
                occurrenceId = occurrenceId,
                noteCount = midi.roles.single { it.role == app.melotrail.midi.domain.MidiExportRole.entries[role.ordinal + 1] }.events.count { it is MidiNoteEvent },
                findings = emptyList(),
            ),
        )
        val reportArtifact = store.publishCandidateReport(session.root, candidateId, report)
        val current = session.project
        val authorityHash = MidiCoreAuthorityHasher.from(current).scopeHash(occurrenceId, role)
        val candidate = MidiCoreCandidate(
            id = candidateId,
            role = role,
            occurrenceId = occurrenceId,
            generatorVersion = "manual-assembly-v1",
            authorityHash = authorityHash,
            seed = 1,
            midi = midiArtifact,
            validationReport = reportArtifact,
            createdAt = Instant.parse("2026-08-27T00:00:01Z").toString(),
            profileId = when (role) {
                CandidateRole.CHORDS -> "chords.sustained"
                CandidateRole.BASS -> "bass.sustained-sub-like"
                CandidateRole.DRUMS -> "drums.dusty"
            },
            patternId = when (role) {
                CandidateRole.CHORDS -> "chords.rhythm.sustained"
                CandidateRole.BASS -> "bass.sustained-root"
                CandidateRole.DRUMS -> "drums.dusty-straight"
            },
            status = MidiCoreCandidateStatus.ACCEPTED,
        )
        val previous = current.acceptances.single { it.role == role && it.occurrenceId == occurrenceId }
        val updated = current.copy(
            candidates = current.candidates.map { item ->
                when {
                    item.id == previous.candidateId -> item.copy(status = MidiCoreCandidateStatus.CURRENT)
                    else -> item
                }
            } + candidate,
            acceptances = current.acceptances.map { acceptance ->
                if (acceptance == previous) acceptance.copy(candidateId = candidateId) else acceptance
            },
            acceptanceHistory = current.acceptanceHistory + CandidateAcceptanceHistory(
                id = "history-$candidateId",
                occurrenceId = occurrenceId,
                role = role,
                candidateId = candidateId,
                action = MidiCoreAcceptanceAction.REPLACED,
                recordedAt = Instant.now().plusSeconds(1).toString(),
            ),
            revision = current.revision + 1L,
        )
        store.saveProject(session.root, updated)
        Files.deleteIfExists(temporary)
        return MidiCoreProjectSession(session.root, updated)
    }
}

private object MidiExportSongForTest {
    fun roleSong(
        role: app.melotrail.midi.domain.MidiExportRole,
        noteStart: Long,
        noteEnd: Long,
        songEnd: Long,
    ) = app.melotrail.midi.domain.MidiExportSong(
        ppq = MidiPpq(480),
        sequenceName = "Manual candidate",
        tempoMicrosecondsPerQuarter = 500_000,
        meterNumerator = 4,
        meterDenominatorExponent = 2,
        markers = listOf(app.melotrail.midi.domain.MidiExportMarker(1, "Verse", 0)),
        roles = listOf(
            app.melotrail.midi.domain.MidiExportRoleTrack(
                role,
                listOf(MidiNoteEvent(MidiEventOrderingKey(noteStart, MidiSemanticEventKind.NOTE, generatedEventKey = 1), noteEnd, role.channel, 60, 90)),
            ),
        ),
        songEndTick = songEnd,
    )

    fun wrongChannelSong() = app.melotrail.midi.domain.MidiExportSong(
        ppq = MidiPpq(480),
        sequenceName = "Wrong channel candidate",
        tempoMicrosecondsPerQuarter = 500_000,
        meterNumerator = 4,
        meterDenominatorExponent = 2,
        markers = listOf(app.melotrail.midi.domain.MidiExportMarker(1, "Verse", 0)),
        roles = listOf(
            app.melotrail.midi.domain.MidiExportRoleTrack(
                app.melotrail.midi.domain.MidiExportRole.CHORDS,
                listOf(MidiNoteEvent(MidiEventOrderingKey(0, MidiSemanticEventKind.NOTE, generatedEventKey = 1), 120, 0, 60, 90)),
            ),
        ),
        songEndTick = 1920,
    )

    fun writeWrongChannel(path: Path) {
        val sequence = Sequence(Sequence.PPQ, 480)
        val conductor = sequence.createTrack()
        val conductorName = "Conductor".encodeToByteArray()
        conductor.add(MidiEvent(MetaMessage(0x03, conductorName, conductorName.size), 0))
        conductor.add(MidiEvent(MetaMessage(0x51, byteArrayOf(0x07, 0xA1.toByte(), 0x20), 3), 0))
        conductor.add(MidiEvent(MetaMessage(0x58, byteArrayOf(4, 2, 24, 8), 4), 0))
        conductor.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), 1920))
        val role = sequence.createTrack()
        val roleName = "Chords".encodeToByteArray()
        role.add(MidiEvent(MetaMessage(0x03, roleName, roleName.size), 0))
        role.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, 60, 90), 0))
        role.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 120))
        role.add(MidiEvent(MetaMessage(0x2F, byteArrayOf(), 0), 1920))
        require(MidiSystem.write(sequence, 1, path.toFile()) > 0)
    }
}

package app.melotrail.application

import app.melotrail.arrangement.core.MidiCoreRoleFindingCode
import app.melotrail.arrangement.core.MidiCoreSectionPolicy
import app.melotrail.arrangement.core.MidiCoreRoleValidationReportJson
import app.melotrail.midi.OwnedMidiFixtures
import app.melotrail.midi.adapter.JdkMidiReader
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreGeneratorInput
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.adapter.AtomicWriteObserver
import app.melotrail.project.adapter.MidiCoreArtifactCollisionException
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.structure.MidiCoreBarOccurrencePlacement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MidiCoreCandidateGenerationTest {
    @TempDir lateinit var root: Path

    @Test
    fun `publishes one deterministic immutable candidate for every core role`() {
        listOf(CandidateRole.CHORDS, CandidateRole.BASS, CandidateRole.DRUMS).forEach { role ->
            val store = MidiCoreArtifactStore()
            val session = readySession(store, root.resolve("project-${role.name.lowercase()}"))
            val result = runBlocking {
                MidiCoreCandidateGeneration(artifacts = store, dispatcher = Dispatchers.Default).generate(
                    request(session, role, "${role.name.lowercase()}-candidate"),
                )
            }

            val published = assertIs<MidiCoreCandidateGenerationResult.Published>(result)
            assertEquals(role, published.candidate.role)
            assertEquals("verse-1", published.candidate.occurrenceId)
            assertEquals(MidiCoreCandidateStatus.CURRENT, published.candidate.status)
            assertEquals(published.validation, MidiCoreRoleValidationReportJson.decode(
                Files.readString(session.root.resolve(published.candidate.validationReport.path.value)),
            ))
            assertTrue(published.validation.passed)
            assertEquals(published.candidate.validationReport.sha256, digest(
                Files.readAllBytes(session.root.resolve(published.candidate.validationReport.path.value)),
            ))
            store.verify(session.root, published.candidate.midi)
            assertEquals(published.session.project, store.openProject(session.root))

            val replacement = session.root.resolve("replacement.mid")
            Files.write(replacement, byteArrayOf(0x01, 0x02, 0x03))
            assertFailsWith<MidiCoreArtifactCollisionException> {
                store.publishCandidateMidi(
                    session.root,
                    role,
                    published.candidate.occurrenceId,
                    published.candidate.id,
                    replacement,
                )
            }
            assertEquals(1, store.openProject(session.root).candidates.size)
        }
    }

    @Test
    fun `retries a generated candidate after a collision without replacing existing evidence`() {
        val store = MidiCoreArtifactStore()
        val firstSession = readySession(store, root.resolve("collision-project"))
        val first = assertIs<MidiCoreCandidateGenerationResult.Published>(runBlocking {
            MidiCoreCandidateGeneration(artifacts = store).generate(
                request(firstSession, CandidateRole.CHORDS, "existing-candidate"),
            )
        })
        val originalBytes = Files.readAllBytes(first.session.root.resolve(first.candidate.midi.path.value))
        val ids = listOf("existing-candidate", "retry-candidate")
        val next = AtomicInteger(0)
        val retry = assertIs<MidiCoreCandidateGenerationResult.Published>(runBlocking {
            MidiCoreCandidateGeneration(
                artifacts = store,
                candidateIdFactory = { ids[next.getAndIncrement()] },
            ).generate(request(first.session, CandidateRole.CHORDS))
        })

        assertEquals("retry-candidate", retry.candidate.id)
        assertEquals(2, store.openProject(first.session.root).candidates.size)
        assertContentEquals(originalBytes, Files.readAllBytes(first.session.root.resolve(first.candidate.midi.path.value)))
    }

    @Test
    fun `cancellation before generation leaves no project or candidate artifacts`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("cancel-before-project"))
        val cancelled = AtomicBoolean(true)

        val result = runBlocking {
            MidiCoreCandidateGeneration(artifacts = store).generate(
                request(
                    session,
                    CandidateRole.CHORDS,
                    cancellation = MidiCoreGenerationCancellation { cancelled.get() },
                ),
            )
        }

        val typed = assertIs<MidiCoreCandidateGenerationResult.Cancelled>(result)
        assertEquals(null, typed.context)
        assertTrue(typed.publishedArtifacts.isEmpty())
        assertEquals(session.project, store.openProject(session.root))
        assertFalse(Files.exists(session.root.resolve("candidates")))
        assertFalse(hasGenerationTemporaryDirectory(session.root))
    }

    @Test
    fun `cancellation after immutable publication keeps inspectable evidence but does not bind it`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("cancel-after-project"))
        val cancelled = AtomicBoolean(false)
        val result = runBlocking {
            MidiCoreCandidateGeneration(artifacts = store).generate(
                request(
                    session,
                    CandidateRole.BASS,
                    "cancelled-candidate",
                    cancellation = MidiCoreGenerationCancellation { cancelled.get() },
                    hooks = MidiCoreGenerationHooks(afterArtifactsPublished = { cancelled.set(true) }),
                ),
            )
        }

        val typed = assertIs<MidiCoreCandidateGenerationResult.Cancelled>(result)
        assertEquals("cancelled-candidate", typed.candidateId)
        assertEquals(2, typed.publishedArtifacts.size)
        typed.publishedArtifacts.forEach { store.verify(session.root, it) }
        assertEquals(session.project, store.openProject(session.root))
        assertFalse(hasGenerationTemporaryDirectory(session.root))
    }

    @Test
    fun `validation rejection never publishes a malformed-grid candidate`() {
        val store = MidiCoreArtifactStore()
        val valid = readySession(store, root.resolve("validation-project"))
        val authority = requireNotNull(valid.project.authority)
        val malformed = valid.project.copy(
            authority = authority.copy(
                chordEvents = listOf(
                    AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 1),
                    AuthoritativeChordEvent("chord-2", "verse-1", "C", 1, 1920),
                ),
            ),
        )
        store.saveProject(valid.root, malformed)
        val session = MidiCoreProjectSession(valid.root, malformed)

        val result = runBlocking {
            MidiCoreCandidateGeneration(artifacts = store).generate(
                request(session, CandidateRole.CHORDS, "malformed-candidate"),
            )
        }

        val rejected = assertIs<MidiCoreCandidateGenerationResult.ValidationRejected>(result)
        assertTrue(rejected.validation.findings.any { it.code == MidiCoreRoleFindingCode.UNREPRESENTABLE_TICK })
        assertEquals(malformed, store.openProject(session.root))
        assertFalse(Files.exists(session.root.resolve("candidates")))
    }

    @Test
    fun `stale completion is rejected after generation and before publication`() {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("stale-project"))
        val changed = session.project.copy(
            authority = requireNotNull(session.project.authority).copy(
                chordEvents = listOf(AuthoritativeChordEvent("chord-1", "verse-1", "G", 0, 1920)),
            ),
        )

        val result = runBlocking {
            MidiCoreCandidateGeneration(artifacts = store).generate(
                request(
                    session,
                    CandidateRole.CHORDS,
                    "stale-candidate",
                    hooks = MidiCoreGenerationHooks(afterCandidate = { store.saveProject(session.root, changed) }),
                ),
            )
        }

        val rejected = assertIs<MidiCoreCandidateGenerationResult.Rejected>(result)
        assertEquals(MidiCoreCandidateProblemCode.STALE_PROJECT, rejected.problem.code)
        assertEquals(changed, store.openProject(session.root))
        assertFalse(Files.exists(session.root.resolve("candidates")))
        assertFalse(hasGenerationTemporaryDirectory(session.root))
    }

    @Test
    fun `failed project save preserves last good state and published candidate evidence`() {
        val failSave = AtomicBoolean(false)
        val store = MidiCoreArtifactStore(AtomicWriteObserver { temporary, target ->
            if (failSave.get() && target.fileName.toString() == MidiCoreArtifactStore.PROJECT_FILE) {
                Files.writeString(temporary, "partial")
                throw IOException("simulated candidate save failure")
            }
        })
        val session = readySession(store, root.resolve("save-failure-project"))
        val before = Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE))
        failSave.set(true)

        val result = runBlocking {
            MidiCoreCandidateGeneration(artifacts = store).generate(
                request(session, CandidateRole.DRUMS, "unsaved-candidate"),
            )
        }

        val rejected = assertIs<MidiCoreCandidateGenerationResult.Rejected>(result)
        assertEquals(MidiCoreCandidateProblemCode.SAVE_FAILED, rejected.problem.code)
        val published = assertNotNull(rejected.publishedCandidate)
        store.verify(session.root, published.midi)
        store.verify(session.root, published.validationReport)
        assertContentEquals(before, Files.readAllBytes(session.root.resolve(MidiCoreArtifactStore.PROJECT_FILE)))
        assertEquals(session.project, store.openProject(session.root))
        assertTrue(Files.list(session.root).use { paths -> paths.anyMatch { it.fileName.toString().contains("recovery") } })
    }

    @Test
    fun `concurrent requests bind at most one candidate and reject the stale completion`() = runBlocking {
        val store = MidiCoreArtifactStore()
        val session = readySession(store, root.resolve("concurrent-project"))
        val generation = MidiCoreCandidateGeneration(artifacts = store, dispatcher = Dispatchers.Default)

        val results = listOf(
            async { generation.generate(request(session, CandidateRole.CHORDS, "parallel-a")) },
            async { generation.generate(request(session, CandidateRole.CHORDS, "parallel-b")) },
        ).awaitAll()

        assertEquals(1, results.count { it is MidiCoreCandidateGenerationResult.Published })
        assertEquals(1, results.count {
            it is MidiCoreCandidateGenerationResult.Rejected && it.problem.code == MidiCoreCandidateProblemCode.STALE_PROJECT
        })
        assertEquals(1, store.openProject(session.root).candidates.size)
    }

    @Test
    fun `accepted role evidence enriches only a regenerated role occurrence without rewriting dependencies`() {
        val store = MidiCoreArtifactStore()
        var session = readySession(store, root.resolve("interaction-project"))
        val generation = MidiCoreCandidateGeneration(artifacts = store)

        val chords = assertIs<MidiCoreCandidateGenerationResult.Published>(runBlocking {
            generation.generate(request(session, CandidateRole.CHORDS, "chords-accepted"))
        })
        session = accept(store, chords.session, chords.candidate.id)
        val bass = assertIs<MidiCoreCandidateGenerationResult.Published>(runBlocking {
            generation.generate(request(session, CandidateRole.BASS, "bass-accepted"))
        })
        session = accept(store, bass.session, bass.candidate.id)
        val drums = assertIs<MidiCoreCandidateGenerationResult.Published>(runBlocking {
            generation.generate(request(session, CandidateRole.DRUMS, "drums-accepted"))
        })
        session = accept(store, drums.session, drums.candidate.id)
        val dependencyBytes = listOf(chords.candidate, bass.candidate, drums.candidate).associate { candidate ->
            candidate.id to Files.readAllBytes(session.root.resolve(candidate.midi.path.value))
        }

        val regenerated = assertIs<MidiCoreCandidateGenerationResult.Published>(runBlocking {
            generation.generate(request(session, CandidateRole.CHORDS, "chords-interaction-repair"))
        })

        assertEquals(listOf("bass-accepted", "drums-accepted"), regenerated.candidate.acceptedDependencyIds)
        assertEquals(CandidateRole.CHORDS, regenerated.candidate.role)
        assertEquals("verse-1", regenerated.candidate.occurrenceId)
        dependencyBytes.forEach { (candidateId, bytes) ->
            val candidate = session.project.candidates.single { it.id == candidateId }
            assertContentEquals(bytes, Files.readAllBytes(session.root.resolve(candidate.midi.path.value)))
        }
        assertEquals(4, store.openProject(session.root).candidates.size)
    }

    private fun readySession(store: MidiCoreArtifactStore, projectRoot: Path): MidiCoreProjectSession {
        val created = assertIs<MidiCoreProjectLifecycleResult.Opened>(
            projectLifecycle(store).create(CreateMidiCoreProject(projectRoot, "Generation Test", "generation-project")),
        ).session
        val source = OwnedMidiFixtures.writeAll(root.resolve("fixtures-${projectRoot.fileName}"))
            .single { it.fileName.toString() == "whole-song-one-bar.mid" }
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
                    listOf(MidiCoreBarOccurrencePlacement("verse-1", "verse", "Verse", 1)),
                ),
            ),
        ).session
        return assertIs<MidiCoreAuthoritativeHarmonyResult.Updated>(
            MidiCoreAuthoritativeHarmony(store).replace(
                ReplaceMidiCoreHarmony(
                    structured,
                    listOf(AuthoritativeChordEvent("chord-1", "verse-1", "C", 0, 1920)),
                ),
            ),
        ).session
    }

    private fun request(
        session: MidiCoreProjectSession,
        role: CandidateRole,
        candidateId: String? = null,
        cancellation: MidiCoreGenerationCancellation = MidiCoreGenerationCancellation.NONE,
        hooks: MidiCoreGenerationHooks = MidiCoreGenerationHooks(),
    ): GenerateMidiCoreCandidate {
        val (profile, pattern) = when (role) {
            CandidateRole.CHORDS -> "chords.sustained" to "chords.rhythm.sustained"
            CandidateRole.BASS -> "bass.sustained-sub-like" to "bass.sustained-root"
            CandidateRole.DRUMS -> "drums.dusty" to "drums.dusty-straight"
        }
        return GenerateMidiCoreCandidate(
            session = session,
            role = role,
            occurrenceId = "verse-1",
            performanceProfileId = profile,
            patternId = pattern,
            generator = MidiCoreGeneratorInput("test-generator", "test-generator-v1", pattern, 42L),
            sectionPolicy = MidiCoreSectionPolicy(density = 1.0),
            candidateId = candidateId,
            cancellation = cancellation,
            hooks = hooks,
        )
    }

    private fun projectLifecycle(store: MidiCoreArtifactStore) = MidiCoreProjectLifecycle(
        artifacts = store,
        idFactory = { "generation-project" },
    )

    private fun accept(store: MidiCoreArtifactStore, session: MidiCoreProjectSession, candidateId: String): MidiCoreProjectSession =
        assertIs<MidiCoreCandidateLifecycleResult.Updated>(
            MidiCoreCandidateLifecycle(store).accept(AcceptMidiCoreCandidate(session, candidateId)),
        ).session

    private fun hasGenerationTemporaryDirectory(projectRoot: Path): Boolean = Files.list(projectRoot).use { paths ->
        paths.anyMatch { it.fileName.toString().startsWith(".midi-core-generation-") }
    }

    private fun digest(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

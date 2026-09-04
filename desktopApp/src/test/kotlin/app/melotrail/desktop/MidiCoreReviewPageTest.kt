package app.melotrail.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import app.melotrail.application.MidiCoreCandidateReviewItem
import app.melotrail.arrangement.core.MidiCoreRoleValidationReport
import app.melotrail.project.AuthoritativeChordEvent
import app.melotrail.project.CandidateAcceptance
import app.melotrail.project.CandidateRole
import app.melotrail.project.MidiCoreArrangementDraft
import app.melotrail.project.MidiCoreArrangementDraftAcceptanceHistory
import app.melotrail.project.MidiCoreArrangementDraftCandidateReference
import app.melotrail.project.MidiCoreArrangementDraftValidationSummary
import app.melotrail.project.MidiCoreAuthorityHasher
import app.melotrail.project.MidiCoreCandidate
import app.melotrail.project.MidiCoreCandidateStatus
import app.melotrail.project.MidiCoreProject
import app.melotrail.project.ProjectArtifact
import app.melotrail.project.ProjectAuthority
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectKey
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.ProjectRelativePath
import app.melotrail.project.ProjectSectionDefinition
import app.melotrail.project.ProjectSectionOccurrence
import app.melotrail.project.SelectedMelodyTrack
import app.melotrail.project.SourceMidiRecord
import app.melotrail.midi.domain.MidiTrackSummary
import app.melotrail.music.core.ProjectKeySpelling
import app.melotrail.music.core.ProjectMeter
import app.melotrail.music.core.ProjectScaleMode
import app.melotrail.music.core.ProjectTempo
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MidiCoreReviewPageTest {
    @Test
    fun `Review presents the shared map and one complete draft listen-use decision`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(), intents::add, {}) } }

        onNodeWithTag(MidiCoreReviewPageTags.ROOT).assertExists()
        onNodeWithTag(MidiCoreSongMapTags.ROOT).assertExists()
        onNodeWithTag(MidiCoreReviewPageTags.DRAFT).assertExists()
        onNodeWithTag(MidiCoreReviewPageTags.PLAY_DRAFT).performScrollTo().assertIsEnabled().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.USE_DRAFT).performScrollTo().performClick()
        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.PlayArrangementDraft("review-draft"),
                MidiCoreWorkspaceIntent.UseArrangementDraft("review-draft"),
            ),
            intents,
        )
    }

    @Test
    fun `Review exposes undo only for the latest batch and Export only after strict acceptance`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val navigation = mutableListOf<MidiCoreWorkspaceDestination>()
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(accepted = true), intents::add, navigation::add) } }

        onNodeWithTag(MidiCoreReviewPageTags.UNDO_DRAFT).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.EXPORT).performScrollTo().performClick()
        assertEquals(listOf<MidiCoreWorkspaceIntent>(MidiCoreWorkspaceIntent.UndoArrangementDraftAcceptance("batch-review-draft")), intents)
        assertEquals(listOf(MidiCoreWorkspaceDestination.EXPORT), navigation)

        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(), {}, {}) } }
        onNodeWithTag(MidiCoreReviewPageTags.UNDO_DRAFT).assertDoesNotExist()
        onNodeWithTag(MidiCoreReviewPageTags.EXPORT).assertDoesNotExist()
    }

    @Test
    fun `Review keeps candidate comparison and lifecycle operations behind selected-section disclosure`() = runComposeUiTest {
        val intents = mutableListOf<MidiCoreWorkspaceIntent>()
        val state = reviewState().copy(review = reviewItems())
        setContent { MelotrailTheme { MidiCoreReviewPage(state, intents::add, {}) } }

        onNodeWithTag(MidiCoreReviewPageTags.CANDIDATE_MENU).assertDoesNotExist()
        onNodeWithTag(MidiCoreReviewPageTags.OPEN_EXCEPTIONS).performScrollTo().performClick()
        waitForIdle()
        intents.clear()
        onNodeWithTag(MidiCoreReviewPageTags.PLAY_CANDIDATE).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.compare("review-alt")).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.MORE_ACTIONS).performScrollTo().performClick()
        onNodeWithTag(MidiCoreReviewPageTags.REJECT_CANDIDATE).performScrollTo().performClick()
        assertEquals(
            listOf(
                MidiCoreWorkspaceIntent.PlayCandidate("review-main", CandidateRole.CHORDS, "verse-1"),
                MidiCoreWorkspaceIntent.CompareCandidates("review-main", "review-alt"),
                MidiCoreWorkspaceIntent.RejectCandidate("review-main", "Not selected in Review."),
            ),
            intents,
        )
    }

    @Test
    fun `Review repair uses the already selected song-map context`() = runComposeUiTest {
        val navigation = mutableListOf<MidiCoreWorkspaceDestination>()
        setContent { MelotrailTheme { MidiCoreReviewPage(reviewState(), {}, navigation::add) } }

        onNodeWithTag(MidiCoreReviewPageTags.REPAIR).performScrollTo().performClick()
        assertEquals(listOf(MidiCoreWorkspaceDestination.ARRANGE), navigation)
    }

    @Test
    fun `Review source contains no retired role first acceptance ladder`() {
        val source = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreReviewPage.kt")).lowercase()
        listOf("1. choose a part", "listen and decide", "continue to next", "play accepted arrangement").forEach { retired ->
            assertFalse(source.contains(retired), "Review page must not contain $retired")
        }
    }

    @Test
    fun `wide Review keeps its selected-section inspector alongside factual contextual evidence`() = runSkikoComposeUiTest(size = Size(1280f, 900f)) {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = reviewState(),
                    initialDestination = MidiCoreWorkspaceDestination.REVIEW,
                )
            }
        }
        onNodeWithTag(MidiCoreSongMapTags.TRACK).assertExists()
        onNodeWithTag(MidiCoreReviewPageTags.INSPECTOR).assertExists()
        onNodeWithContentDescription("Review contextual inspector").assertExists()
        writeReviewFixture("wide-review-draft.png", onRoot().captureToImage().toAwtImage())
    }

    @Test
    fun `compact Review retains map and persistent player after scrolling`() = runSkikoComposeUiTest(size = Size(720f, 900f)) {
        setContent {
            MelotrailTheme {
                MidiCoreWorkspaceShell(
                    state = reviewState(),
                    initialDestination = MidiCoreWorkspaceDestination.REVIEW,
                )
            }
        }
        onNodeWithTag(MidiCoreSongMapTags.TRACK).assertExists()
        onNodeWithTag(MidiCoreReviewPageTags.EXCEPTIONS).performScrollTo().assertExists()
        onNodeWithTag(MidiCoreWorkspaceShellTags.PLAYER).assertExists()
        writeReviewFixture("compact-review-draft-scrolled.png", onRoot().captureToImage().toAwtImage())
    }

    private fun reviewState(accepted: Boolean = false): MidiCoreWorkspaceState {
        val authority = ProjectAuthority(
            key = ProjectKey(ProjectKeySpelling.C, ProjectScaleMode.MAJOR), tempo = ProjectTempo(500_000), meter = ProjectMeter(4, 2),
            sectionDefinitions = listOf(ProjectSectionDefinition("verse", "Verse")),
            occurrences = listOf(
                ProjectSectionOccurrence("verse-1", "verse", "Verse", 0L, 1920L),
                ProjectSectionOccurrence("verse-2", "verse", "Verse", 1920L, 3840L),
            ),
            chordEvents = listOf(
                AuthoritativeChordEvent("chord-1", "verse-1", "C", 0L, 1920L),
                AuthoritativeChordEvent("chord-2", "verse-2", "G", 1920L, 3840L),
            ),
        )
        val base = MidiCoreProject(
            ProjectId("review-project"), ProjectMetadata("Review project", "2026-09-04T00:00:00Z"),
            SourceMidiRecord("source.mid", "a".repeat(64), 1, 480, ProjectArtifact(ProjectRelativePath("source/original.mid"), "a".repeat(64)), ProjectArtifact(ProjectRelativePath("reports/import.json"), "b".repeat(64)), listOf(MidiTrackSummary(0, "Lead", emptyList(), 3840L)), 3840L),
            SelectedMelodyTrack(0, 0, "c".repeat(64)), authority,
        )
        val hasher = MidiCoreAuthorityHasher.from(base)
        val candidates = authority.occurrences.flatMap { occurrence -> CandidateRole.entries.map { role ->
            MidiCoreCandidate(
                id = "draft-${occurrence.id}-${role.name.lowercase()}", role = role, occurrenceId = occurrence.id, generatorVersion = "midi-core-v1",
                authorityHash = hasher.scopeHash(occurrence.id, role), seed = role.ordinal.toLong() + 1L,
                midi = ProjectArtifact(ProjectRelativePath("candidates/${role.name.lowercase()}/${occurrence.id}/draft.mid"), "d".repeat(64)),
                validationReport = ProjectArtifact(ProjectRelativePath("reports/candidates/${occurrence.id}-${role.name.lowercase()}.json"), "e".repeat(64)),
                createdAt = "2026-09-04T00:00:00Z", profileId = "${role.name.lowercase()}.profile", patternId = "${role.name.lowercase()}.pattern",
                status = if (accepted) MidiCoreCandidateStatus.ACCEPTED else MidiCoreCandidateStatus.CURRENT,
            )
        } }
        val draft = MidiCoreArrangementDraft(
            id = "review-draft", styleId = "open-sky", styleVersion = 1, authorityHash = hasher.sha256, rootSeed = 1L,
            candidateReferences = candidates.map { candidate -> MidiCoreArrangementDraftCandidateReference(candidate.occurrenceId, candidate.role, candidate.id, candidate.midi.sha256, candidate.validationReport.sha256, candidate.authorityHash) },
            validation = MidiCoreArrangementDraftValidationSummary(candidates.size, 24, true, "f".repeat(64)), createdAt = "2026-09-04T00:00:00Z",
        )
        val acceptances = if (accepted) candidates.map { CandidateAcceptance(it.occurrenceId, it.role, it.id, false) } else emptyList()
        val batches = if (accepted) listOf(MidiCoreArrangementDraftAcceptanceHistory("batch-review-draft", draft.id, emptyList(), acceptances, "2026-09-04T00:01:00Z")) else emptyList()
        return MidiCoreWorkspaceState(
            project = base.copy(candidates = candidates, acceptances = acceptances, arrangementDrafts = listOf(draft), arrangementDraftAcceptanceHistory = batches, revision = 6L),
            arrangement = MidiCoreArrangementUiState(selectedOccurrenceId = "verse-1"),
        )
    }

    private fun reviewItems(): MidiCoreCandidateReviewUiState {
        val state = reviewState()
        val main = state.project!!.candidates.first().copy(id = "review-main")
        val alternative = main.copy(id = "review-alt", seed = 99L)
        fun item(candidate: MidiCoreCandidate) = MidiCoreCandidateReviewItem(
            candidate, MidiCoreRoleValidationReport("1".repeat(64), "2".repeat(64), CandidateRole.CHORDS, "verse-1", 4, emptyList()), emptyList(), true, false, false,
        )
        return MidiCoreCandidateReviewUiState(CandidateRole.CHORDS, "verse-1", listOf(item(main), item(alternative)), selectedCandidateId = "review-main")
    }

    private fun sourceFile(relativePath: String): Path = sequenceOf(Path.of(relativePath), Path.of("desktopApp").resolve(relativePath)).first { Files.isRegularFile(it) }

    private fun writeReviewFixture(name: String, image: BufferedImage) {
        assertEquals(if (name.startsWith("wide")) 1280 else 720, image.width)
        assertEquals(900, image.height)
        val target = Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("build/test-results/midi-core-review-draft").resolve(name)
        Files.createDirectories(target.parent)
        assertTrue(ImageIO.write(image, "png", target.toFile()))
    }
}

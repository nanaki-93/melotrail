package app.melotrail.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @Test
    fun `workspace layout breakpoints retain wide medium and narrow access paths`() {
        assertEquals(WorkspaceLayout.WIDE, workspaceLayoutForWidth(1440.dp))
        assertEquals(WorkspaceLayout.WIDE, workspaceLayoutForWidth(1180.dp))
        assertEquals(WorkspaceLayout.MEDIUM, workspaceLayoutForWidth(1100.dp))
        assertEquals(WorkspaceLayout.MEDIUM, workspaceLayoutForWidth(760.dp))
        assertEquals(WorkspaceLayout.NARROW, workspaceLayoutForWidth(759.dp))
    }

    @Test
    fun `workspace shell exposes its core regions`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(WorkspaceUiState(), onIntent = {})
            }
        }

        listOf(
            WorkspaceTags.PROJECT_HEADER,
            WorkspaceTags.WORKSPACE_NAV,
            WorkspaceTags.PARTS_PANEL,
            WorkspaceTags.STRUCTURE_PANEL,
            WorkspaceTags.ARRANGEMENT_PANEL,
            WorkspaceTags.TIMELINE_PANEL,
            WorkspaceTags.COMPACT_TRANSPORT,
            WorkspaceTags.OPERATION_STATUS
        ).forEach { onNodeWithTag(it).assertExists() }
        onNodeWithText("Melotrail").assertIsDisplayed()
    }

    @Test
    fun `top navigation has explicit destinations and dispatches section selection`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MelotrailTheme { WorkspaceScreen(projectState(), intents::add) } }

        WorkspaceSection.entries.forEach { section ->
            onAllNodesWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + section.name.lowercase()).assertCountEquals(1)
        }
        onAllNodesWithText("Project · Complete").assertCountEquals(0)
        onAllNodesWithText("Prepare · Current").assertCountEquals(0)
        onNodeWithTag(WorkspaceTags.WORKSPACE_SECTION_PREFIX + WorkspaceSection.ARRANGE.name.lowercase()).performClick()
        assertEquals(WorkspaceIntent.SelectWorkspaceSection(WorkspaceSection.ARRANGE), intents.last())
    }

    @Test
    fun `library destination exposes configuration and readiness actions`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(workspaceSection = WorkspaceSection.LIBRARY), onIntent = {})
            }
        }

        onNodeWithTag(WorkspaceTags.LIBRARY_PANEL).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.SOUND_LIBRARY_SETTINGS).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.READINESS_RECOVERY).assertIsDisplayed()
    }

    @Test
    fun `stable status feedback renders typed error dismissal and retry actions`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val feedback = OperationFeedback(
            sessionId = "operation-7", kind = OperationKind.IMPORT, phase = OperationPhase.FAILED,
            message = "Worker unavailable", outcomeSeverity = OperationSeverity.ERROR,
            retryAction = OperationRetryAction.RETRY_SAFE_OPERATION
        )
        setContent {
            MelotrailTheme {
                OperationStatusSurface(projectState().copy(notification = "Worker unavailable", operationFeedback = feedback, retry = WorkspaceRetry.Analyze(java.nio.file.Path.of("build/test-project"), "A")), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.OPERATION_FEEDBACK).assertExists()
        onNodeWithText("✕  Error").assertExists()
        onNodeWithText("Worker unavailable").assertExists()
        onAllNodesWithTag(WorkspaceTags.GLOBAL_FEEDBACK_RETRY).assertCountEquals(1)
        onNodeWithTag(WorkspaceTags.GLOBAL_FEEDBACK_RETRY).performClick()
        assertEquals(WorkspaceIntent.Retry, intents.last())
        onNodeWithTag(WorkspaceTags.GLOBAL_FEEDBACK_DISMISS).performClick()
        assertEquals(WorkspaceIntent.DismissNotification, intents.last())
    }

    @Test
    fun `status surface keeps text and icons for all severities and loading modes`() = runComposeUiTest {
        fun status(phase: OperationPhase, severity: OperationSeverity? = null, work: OperationWork? = null) = OperationFeedback(
            sessionId = "operation-${phase.name}", kind = OperationKind.MIXING, phase = phase,
            message = "Backend phase is explicit", work = work, outcomeSeverity = severity
        )

        setContent { MelotrailTheme { WorkspaceScreen(projectState().copy(operationFeedback = status(OperationPhase.WAITING_FOR_WORKER)), onIntent = {}) } }
        onNodeWithText("↻  Loading · waiting for worker").assertExists()
        onNodeWithTag(WorkspaceTags.IMPORT_PROGRESS).assertExists()

        setContent { MelotrailTheme { WorkspaceScreen(projectState().copy(operationFeedback = status(OperationPhase.LOCAL, work = OperationWork(2, 5))), onIntent = {}) } }
        onNodeWithText("2/5 steps").assertExists()

        listOf(
            OperationSeverity.INFORMATION to "ℹ  Information",
            OperationSeverity.WARNING to "⚠  Warning",
            OperationSeverity.SUCCESS to "✓  Complete",
            OperationSeverity.ERROR to "✕  Error"
        ).forEach { (severity, label) ->
            setContent { MelotrailTheme { WorkspaceScreen(projectState().copy(operationFeedback = status(if (severity == OperationSeverity.ERROR) OperationPhase.FAILED else OperationPhase.COMPLETE, severity)), onIntent = {}) } }
            onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun `empty project workflow exposes one guided import dialog`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState(), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.ADD_MIDI).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.ADD_MIDI).performClick()
        assertEquals(WorkspaceIntent.ShowImportPart(audio = false), intents.last())

        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ImportPart(audio = true, source = java.nio.file.Path.of("build/solo.wav"), detectedType = ImportSourceKind.WAV, sourceSizeBytes = 1_536)), onIntent = {})
            }
        }
        onNodeWithText("Import part").assertIsDisplayed()
        onNodeWithText("Filename: solo.wav · Type: WAV · Size: 1 KiB").assertIsDisplayed()
        onNodeWithText("WAV/MP3 transcription currently supports solo piano only—not full mixes, vocals, or arbitrary polyphonic sources.").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.IMPORT_SOURCE).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.IMPORT_CONFIRM).assertIsEnabled()
    }

    @Test
    fun `role editor and keyboard structure movement controls are visible`() = runComposeUiTest {
        val project = projectState().project!!.copy(
            parts = listOf(app.melotrail.application.PartSummary("A", "verse", "source/A.mid", "A.mid", app.melotrail.application.PartSourceType.MIDI, null)),
            structure = listOf(app.melotrail.application.StructureSectionSummary(0, "A", 1, "A1", 12.0))
        )
        setContent {
            MelotrailTheme {
                WorkspaceScreen(
                    WorkspaceUiState(project = project, structureDraft = listOf("A"), dialog = WorkspaceDialog.EditRole("A", "verse")),
                    onIntent = {}
                )
            }
        }

        onNodeWithText("Edit A role").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.STRUCTURE_MOVE_RIGHT + "0").assertIsDisplayed()
    }

    @Test
    fun `parts and structure occurrences are selectable and use validated duration overview`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val project = projectState().project!!.copy(
            parts = listOf(app.melotrail.application.PartSummary("A", "verse", "source/A.mid", "A.mid", app.melotrail.application.PartSourceType.MIDI, null)),
            structure = listOf(app.melotrail.application.StructureSectionSummary(0, "A", 1, "A1", 12.0))
        )
        setContent {
            MelotrailTheme {
                WorkspaceScreen(WorkspaceUiState(project = project, structureDraft = listOf("A")), intents::add)
            }
        }
        onNodeWithTag(WorkspaceTags.PART_ROW_PREFIX + "A").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.STRUCTURE_OVERVIEW).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.STRUCTURE_OCCURRENCE_PREFIX + "0").performClick()
        assertEquals(WorkspaceIntent.SelectArrangementSection(0), intents.last())
    }

    @Test
    fun `arrangement controls and validated proportional timeline are accessible`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        val root = java.nio.file.Path.of("build/test-project")
        val arrangement = app.melotrail.application.ArrangementSnapshot(
            root, listOf(
                app.melotrail.application.ArrangementSectionSnapshot(0, "A1", "A", "introduction", 0.3, listOf(
                    app.melotrail.application.ArrangementInstrumentSnapshot("piano", "source", null, null)
                ), "build", 2.0),
                app.melotrail.application.ArrangementSectionSnapshot(1, "A2", "A", "climax", 0.8, listOf(
                    app.melotrail.application.ArrangementInstrumentSnapshot("piano", "source", null, null),
                    app.melotrail.application.ArrangementInstrumentSnapshot("bass", "generated", "bass", 0.7)
                ), "none", 6.0)
            ), approvalRequired = true, approved = false, stale = false, artifact = root.resolve("arrangement.draft.json")
        )
        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(arrangement = arrangement, selectedArrangementSection = 0), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.ARRANGEMENT_GENERATE).assertExists()
        onNodeWithTag(WorkspaceTags.ARRANGEMENT_PREVIEW).assertExists()
        onNodeWithTag(WorkspaceTags.ARRANGEMENT_APPROVE).assertExists()
        onNodeWithTag(WorkspaceTags.TIMELINE_LANE_PREFIX + "piano").assertExists()
        onNodeWithTag(WorkspaceTags.TIMELINE_LANE_PREFIX + "bass").assertExists()
        onNodeWithText("Transition out: build").assertExists()
    }

    @Test
    fun `mix and transport expose available artifact controls`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val project = projectState().project!!.copy(
            readiness = app.melotrail.application.ProjectReadiness(true, true, true, true, true, true, true, true, true, true)
        )
        val mix = app.melotrail.application.MixSnapshot(
            root, app.melotrail.application.PersistedMixSettings(), listOf("piano"), root.resolve("mix/dry.wav"), stale = false
        )
        val arrangement = app.melotrail.application.ArrangementSnapshot(root, emptyList(), false, true, false, root.resolve("arrangement.json"))
        setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(project = project, arrangement = arrangement, mix = mix, workspaceSection = WorkspaceSection.MIX_MASTER), onIntent = {}) } }

        onNodeWithTag(WorkspaceTags.BUILD_SONG).assertExists()
        onNodeWithTag(WorkspaceTags.MIX_RESET).assertExists()
        onNodeWithTag(WorkspaceTags.MIX_TRACK_PREFIX + "piano").assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_DRY).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_LOFI).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_MASTER).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_SEEK).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_VOLUME).assertExists()
    }

    @Test
    fun `arrangement and build lifecycle expose gates nine-stage progress reuse and safe cancellation`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/build-lifecycle-project")
        val project = projectState().project!!.copy(
            readiness = app.melotrail.application.ProjectReadiness(true, true, true, true, true, true, true, true, true, true)
        )
        val approved = app.melotrail.application.ArrangementSnapshot(root, emptyList(), false, true, false, root.resolve("arrangement.json"))
        setContent {
            MelotrailTheme {
                WorkspaceScreen(
                    WorkspaceUiState(project = project.copy(root = root), arrangement = approved, runtimeReadiness = runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")), workspaceSection = WorkspaceSection.MIX_MASTER),
                    onIntent = {}
                )
            }
        }
        onNodeWithTag(WorkspaceTags.BUILD_LIFECYCLE).assertExists()
        onNodeWithTag(WorkspaceTags.BUILD_START).assertIsEnabled()
        onNodeWithText("Stems are reused only when their canonical fingerprints are current", substring = true).assertExists()
        onNodeWithText("Available: dry", substring = true).assertExists()

        setContent {
            MelotrailTheme {
                WorkspaceScreen(
                    WorkspaceUiState(
                        project = project.copy(root = root),
                        arrangement = approved,
                        runtimeReadiness = runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")),
                        workspaceSection = WorkspaceSection.MIX_MASTER,
                        operation = WorkspaceOperation.BuildingSong(
                            app.melotrail.application.OperationProgress("build", 3, 9, "Rendering or reusing stems", root.resolve("stems/piano.wav"))
                        )
                    ),
                    onIntent = {}
                )
            }
        }
        onNodeWithText("Stage 3 of 9: Rendering or reusing stems").assertExists()
        onNodeWithText("Current artifact: piano.wav").assertExists()
        onNodeWithTag(WorkspaceTags.BUILD_CANCEL).assertIsEnabled()
    }

    @Test
    fun `persistent footer is the one transport for part previews and recovery`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val source = PreviewSourceIdentity(root, "A", root.resolve("previews/piano-A.wav"))

        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(playbackSession = previewSession(source, PlaybackSessionPhase.PREPARING, elapsedSeconds = 2.0, durationSeconds = 12.0)), onIntent = {})
            }
        }
        onAllNodesWithTag(WorkspaceTags.COMPACT_TRANSPORT).assertCountEquals(1)
        onNodeWithText("Part A preview").assertExists()
        onNodeWithText("Preparing monitor audio…").assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_TOGGLE).assertIsNotEnabled()
        onNodeWithTag(WorkspaceTags.PLAYBACK_SEEK).assertIsNotEnabled()

        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(playbackSession = previewSession(source, PlaybackSessionPhase.PLAYING, elapsedSeconds = 2.0, durationSeconds = 12.0)), onIntent = {})
            }
        }
        onNodeWithText("Playing").assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_TOGGLE).assertIsEnabled()
        onNodeWithTag(WorkspaceTags.PLAYBACK_SEEK).assertIsEnabled()

        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(playbackSession = previewSession(source, PlaybackSessionPhase.FAILED, message = "Analyze A before previewing it.")), onIntent = {})
            }
        }
        onNodeWithText("Preview unavailable").assertExists()
        onNodeWithText("Analyze A before previewing it.").assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_RETRY).assertIsEnabled()
    }

    @Test
    fun `selected audio preparation shows no issue guidance and bounded A B transcription controls`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val audioPart = app.melotrail.application.PartSummary("A", "verse", "source/A.wav", "A.wav", app.melotrail.application.PartSourceType.AUDIO, null)
        val project = projectState().project!!.copy(parts = listOf(audioPart))
        val snapshot = preparationSnapshot("A", recommendation = false, clean = true)
        setContent {
            MelotrailTheme {
                WorkspaceScreen(WorkspaceUiState(project = project, selectedPartId = "A", audioPreparation = AudioPreparationUiState("A", snapshot)), onIntent = {})
            }
        }
        onNodeWithTag(WorkspaceTags.PREPARATION_PANEL).assertIsDisplayed()
        onNodeWithText("No measured safe cleanup is recommended. Inspect-only keeps original audio selected for transcription.").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.PREPARATION_ORIGINAL).assertIsEnabled()
        onNodeWithTag(WorkspaceTags.PREPARATION_CLEAN).assertIsEnabled()
        onNodeWithTag(WorkspaceTags.PREPARATION_TRANSCRIBE).assertIsEnabled()
    }

    @Test
    fun `safe cleanup confirmation explains that original audio remains unchanged`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmSafeCleanup("A")), onIntent = {})
            }
        }
        onNodeWithText("Apply safe cleanup to A?").assertIsDisplayed()
        onNodeWithText("The original source remains available and unchanged.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `MIDI quality inspector shows metrics warnings profiles and downstream next action`() = runComposeUiTest {
        val project = projectState().project!!.copy(parts = listOf(qualityPart(app.melotrail.application.MidiQualityStatus.CURRENT)))
        setContent {
            MelotrailTheme {
                WorkspaceScreen(WorkspaceUiState(project = project, selectedPartId = "A"), onIntent = {})
            }
        }

        onNodeWithTag(WorkspaceTags.MIDI_QUALITY_PANEL).assertIsDisplayed()
        onNodeWithText("Raw → clean").assertExists()
        onNodeWithText("Notes 12 → 10", substring = true).assertExists()
        onNodeWithText("Timing: 2 starts, 1 ends changed", substring = true).assertExists()
        onNodeWithText("Warning: Cleanup shifted note timing", substring = true).assertExists()
        onNodeWithText("Next: analyze A.").assertExists()
        onNodeWithTag(WorkspaceTags.MIDI_QUALITY_PROFILE_PREFIX + "conservative").assertExists()
        onNodeWithTag(WorkspaceTags.MIDI_QUALITY_PROFILE_PREFIX + "transcription_safe").assertExists()
        onNodeWithTag(WorkspaceTags.MIDI_QUALITY_RETRY).assertExists()
    }

    @Test
    fun `MIDI quality inspector explains legacy stale failed and timing confirmation states`() = runComposeUiTest {
        listOf(
            app.melotrail.application.MidiQualityStatus.LEGACY_UNKNOWN to "Legacy MIDI has no raw-to-clean quality record.",
            app.melotrail.application.MidiQualityStatus.STALE_OR_INVALID to "Raw MIDI is ready but repaired MIDI evidence is missing, stale, or invalid."
        ).forEach { (status, message) ->
            val project = projectState().project!!.copy(parts = listOf(qualityPart(status)))
            setContent { MelotrailTheme { WorkspaceScreen(WorkspaceUiState(project = project, selectedPartId = "A"), onIntent = {}) } }
            onNodeWithText(message).assertIsDisplayed()
        }
        val project = projectState().project!!.copy(parts = listOf(qualityPart(app.melotrail.application.MidiQualityStatus.CURRENT)))
        setContent {
            MelotrailTheme {
                WorkspaceScreen(
                    WorkspaceUiState(
                        project = project,
                        selectedPartId = "A",
                        midiQualityReview = MidiQualityReviewDraft(app.melotrail.arrangement.MidiCleanupProfile.TIGHTEN_TIMING),
                        operation = WorkspaceOperation.Failed("MIDI cleanup", "worker unavailable"),
                        dialog = WorkspaceDialog.ConfirmTightenTiming("A")
                    ), onIntent = {}
                )
            }
        }
        onNodeWithText("Timing warning: tighten timing uses a fixed 1/16 grid", substring = true).assertExists()
        onNodeWithText("Retry failed: worker unavailable").assertExists()
        onNodeWithText("Tighten timing for A?").assertIsDisplayed()
        onNodeWithText("Retry with timing changes").assertIsDisplayed()
    }

    @Test
    fun `transport shortcuts retain play seek and stop behavior`() {
        val playback = PlaybackSnapshot(positionSeconds = 10.0, durationSeconds = 12.0)

        assertEquals(WorkspaceIntent.PlayPause, transportShortcutIntent(Key.Spacebar, shortcutPressed = true, keyDown = true, playback))
        assertEquals(WorkspaceIntent.SeekPlayback(5.0), transportShortcutIntent(Key.DirectionLeft, shortcutPressed = true, keyDown = true, playback))
        assertEquals(WorkspaceIntent.SeekPlayback(12.0), transportShortcutIntent(Key.DirectionRight, shortcutPressed = true, keyDown = true, playback))
        assertEquals(WorkspaceIntent.StopPlayback, transportShortcutIntent(Key.K, shortcutPressed = true, keyDown = true, playback))
        assertEquals(null, transportShortcutIntent(Key.Spacebar, shortcutPressed = false, keyDown = true, playback))
    }

    @Test
    fun `every preview lifecycle phase has a user-facing status`() {
        assertEquals(
            setOf(
                "Checking preview prerequisites…", "Preparing monitor audio…", "Preview ready; starting audio output…",
                "Starting audio output…", "Playing", "Paused", "Stopped", "Preview unavailable"
            ),
            PreviewPhase.entries.map(::previewStatusLabel).toSet()
        )
    }

    @Test
    fun `discard and close confirmations are keyboard reachable dialogs`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmDiscardDraft(root = java.nio.file.Path.of("build/other"))), onIntent = {})
            }
        }
        onNodeWithText("Discard arrangement draft?").assertIsDisplayed()
        onNodeWithText("Discard and continue").assertIsDisplayed()

        setContent {
            MelotrailTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmClose), onIntent = {})
            }
        }
        onNodeWithText("Close Melotrail?").assertIsDisplayed()
        onNodeWithText("Keep working").assertIsDisplayed()
    }

    @Test
    fun `sound library dialog exposes choose clear and validation feedback`() = runComposeUiTest {
        setContent {
            MelotrailTheme {
                WorkspaceScreen(WorkspaceUiState(dialog = WorkspaceDialog.SoundLibrarySettings, soundLibrary = SoundLibrarySettingsState(validationError = "Registry is invalid")), onIntent = {})
            }
        }
        onNodeWithTag(WorkspaceTags.SOUND_LIBRARY_CHOOSE).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.SOUND_LIBRARY_CLEAR).assertIsDisplayed()
        onNodeWithText("Registry is invalid").assertIsDisplayed()
    }

    @Test
    fun `readiness header renders checking ready partial and failed states`() = runComposeUiTest {
        val states = listOf(
            RuntimeReadiness.checking(),
            runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")),
            runtimeReadiness(DependencyReadiness(DependencyStatus.UNAVAILABLE, "Basic Pitch missing")),
            runtimeReadiness(DependencyReadiness(DependencyStatus.FAILED, "Registry invalid"))
        )
        states.forEach { readiness ->
            setContent { MelotrailTheme { WorkspaceScreen(projectState().copy(runtimeReadiness = readiness), onIntent = {}) } }
            onNodeWithTag(WorkspaceTags.PROJECT_HEADER).assertIsDisplayed()
        }
    }

    @Test
    fun `wide medium and narrow compositions keep one footer transport and one readiness recovery`() = runComposeUiTest {
        assertEquals(WorkspaceLayout.WIDE, workspaceLayoutForWidth(1440.dp))
        assertEquals(WorkspaceLayout.MEDIUM, workspaceLayoutForWidth(900.dp))
        assertEquals(WorkspaceLayout.NARROW, workspaceLayoutForWidth(600.dp))
        setContent { MelotrailTheme { WorkspaceScreen(projectState(), onIntent = {}) } }
        onAllNodesWithTag(WorkspaceTags.COMPACT_TRANSPORT).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.PLAYBACK_VOLUME).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.READINESS_RECOVERY).assertCountEquals(1)
        onAllNodesWithTag(WorkspaceTags.GLOBAL_FEEDBACK_RETRY).assertCountEquals(0)
    }

    private fun runtimeReadiness(worker: DependencyReadiness): RuntimeReadiness = RuntimeReadiness.of(
        RuntimeDependency.WORKER to worker,
        RuntimeDependency.TRANSCRIPTION to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.SOUND_LIBRARY to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.SAMPLES to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.RENDERER to DependencyReadiness(DependencyStatus.READY, "ready"),
        RuntimeDependency.AUDIO_OUTPUT to DependencyReadiness(DependencyStatus.READY, "ready")
    )

    private fun projectState(): WorkspaceUiState {
        val root = java.nio.file.Path.of("build/test-project")
        return WorkspaceUiState(project = app.melotrail.application.ProjectSnapshot(
            root = root,
            version = 2,
            name = "test-project",
            renderFormat = app.melotrail.arrangement.RenderFormat(),
            parts = emptyList(),
            structure = emptyList(),
            readiness = app.melotrail.application.ProjectReadiness(false, false, false, false, false, false, false, false, false, false)
        ), runtimeReadiness = runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")))
    }

    private fun previewSession(
        source: PreviewSourceIdentity,
        phase: PlaybackSessionPhase,
        elapsedSeconds: Double = 0.0,
        durationSeconds: Double = 0.0,
        message: String? = null
    ) = PlaybackSession(
        id = 1,
        request = PlaybackRequest.Part(source.projectRoot, source.partId, source.audioSource),
        sourceKind = PlaybackSourceKind.MIDI,
        artifact = source.artifact?.let { PlaybackArtifactIdentity(source.projectRoot, it, source.partId, source.audioSource) },
        phase = phase,
        positionSeconds = elapsedSeconds,
        durationSeconds = durationSeconds,
        failureStage = if (phase == PlaybackSessionPhase.FAILED) PlaybackFailureStage.RESOLUTION else null,
        failureMessage = message,
        retryAction = if (phase == PlaybackSessionPhase.FAILED) PlaybackRetryAction.RETRY_SAME_SELECTION else null
    )

    private fun qualityPart(status: app.melotrail.application.MidiQualityStatus): app.melotrail.application.PartSummary {
        val cleanup = app.melotrail.arrangement.MidiCleanupOptions()
        val report = if (status == app.melotrail.application.MidiQualityStatus.CURRENT) {
            val raw = app.melotrail.arrangement.MidiQualityArtifact("0".repeat(64), 12, 3.0, null, 2, 480, 1.0, null, listOf(app.melotrail.arrangement.MidiTempoChange(0, 120.0)), listOf(app.melotrail.arrangement.MidiTimeSignature(0, 4, 4)))
            val clean = raw.copy(sha256 = "1".repeat(64), noteCount = 10, notesPerSecond = 2.5)
            app.melotrail.arrangement.MidiQualityReport(
                partId = "A", raw = raw, clean = clean, cleanup = cleanup,
                timing = app.melotrail.arrangement.MidiTimingChangeSummary(10, 2, 0, 2, 1, 48, 24),
                tempoAndTimeSignaturesPreserved = true,
                warnings = listOf(app.melotrail.arrangement.MidiQualityWarning(app.melotrail.arrangement.MidiQualityWarningCode.LARGE_TIMING_SHIFT, "Cleanup shifted note timing by more than 240 ticks."))
            )
        } else null
        val quality = app.melotrail.application.MidiQualitySummary(
            status,
            cleanup = if (status == app.melotrail.application.MidiQualityStatus.CURRENT) cleanup else null,
            warnings = report?.warnings.orEmpty(),
            report = report
        )
        return app.melotrail.application.PartSummary(
            "A", "verse", "source/A.mid", "A.mid", app.melotrail.application.PartSourceType.MIDI, null,
            app.melotrail.application.PartPreparationSummary(false, false, false, true, true, false, false, emptyList(), quality)
        )
    }

    private fun preparationSnapshot(partId: String, recommendation: Boolean, clean: Boolean): app.melotrail.application.AudioPreparationSnapshot {
        val source = app.melotrail.preparation.InspectionSourceIdentity("source/$partId.wav", "0".repeat(64))
        val measurements = app.melotrail.preparation.AudioInspectionMeasurements(
            0.5, 0.2, 0.0, 0, 0,
            app.melotrail.preparation.SilenceEvidence(0, 0),
            app.melotrail.preparation.SignalIndicator(app.melotrail.preparation.EvidenceLevel.NONE, 0.0),
            app.melotrail.preparation.SignalIndicator(app.melotrail.preparation.EvidenceLevel.NONE, 0.0)
        )
        val report = app.melotrail.preparation.InputInspectionReport(
            partId = partId, source = source,
            detectedInput = app.melotrail.preparation.DetectedInput(app.melotrail.preparation.InputContainer.RIFF_WAVE, "pcm", "wav"),
            durationSeconds = 1.0, audioFormat = app.melotrail.preparation.DetectedAudioFormat(44100, 1, 24), measurements = measurements
        )
        val plan = if (!recommendation) null else app.melotrail.preparation.InputCleanupPlan(
            partId = partId, source = source, mode = app.melotrail.preparation.InputCleanupMode.SAFE_CLEANUP,
            operations = listOf(app.melotrail.preparation.CleanupPlanOperation(app.melotrail.preparation.CleanupOperationType.DC_REMOVAL)),
            evidence = measurements, confidence = 0.5, transcriptionInput = app.melotrail.preparation.TranscriptionInputArtifact.CLEAN_WAV
        )
        return app.melotrail.application.AudioPreparationSnapshot(partId, app.melotrail.application.AudioPreparationAvailability.AVAILABLE, report, plan, cleanWavAvailable = clean)
    }
}

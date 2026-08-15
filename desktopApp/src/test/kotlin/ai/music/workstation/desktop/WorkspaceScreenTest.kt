package ai.music.workstation.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WorkspaceScreenTest {
    @Test
    fun `workspace shell exposes its core regions`() = runComposeUiTest {
        setContent {
            MusicWorkstationTheme {
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
            WorkspaceTags.MIX_PANEL,
            WorkspaceTags.OPERATION_STATUS
        ).forEach { onNodeWithTag(it).assertExists() }
    }

    @Test
    fun `empty project workflow exposes one guided import dialog`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState(), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.ADD_MIDI).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.ADD_MIDI).performClick()
        assertEquals(WorkspaceIntent.ShowImportPart(audio = false), intents.last())

        setContent {
            MusicWorkstationTheme {
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
            parts = listOf(ai.music.workstation.application.PartSummary("A", "verse", "source/A.mid", "A.mid", ai.music.workstation.application.PartSourceType.MIDI, null)),
            structure = listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 12.0))
        )
        setContent {
            MusicWorkstationTheme {
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
            parts = listOf(ai.music.workstation.application.PartSummary("A", "verse", "source/A.mid", "A.mid", ai.music.workstation.application.PartSourceType.MIDI, null)),
            structure = listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 12.0))
        )
        setContent {
            MusicWorkstationTheme {
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
        val arrangement = ai.music.workstation.application.ArrangementSnapshot(
            root, listOf(
                ai.music.workstation.application.ArrangementSectionSnapshot(0, "A1", "A", "introduction", 0.3, listOf(
                    ai.music.workstation.application.ArrangementInstrumentSnapshot("piano", "source", null, null)
                ), "build", 2.0),
                ai.music.workstation.application.ArrangementSectionSnapshot(1, "A2", "A", "climax", 0.8, listOf(
                    ai.music.workstation.application.ArrangementInstrumentSnapshot("piano", "source", null, null),
                    ai.music.workstation.application.ArrangementInstrumentSnapshot("bass", "generated", "bass", 0.7)
                ), "none", 6.0)
            ), approvalRequired = true, approved = false, stale = false, artifact = root.resolve("arrangement.draft.json")
        )
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(arrangement = arrangement, selectedArrangementSection = 0), intents::add)
            }
        }

        onNodeWithTag(WorkspaceTags.ARRANGEMENT_GENERATE).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.ARRANGEMENT_PREVIEW).assertExists()
        onNodeWithTag(WorkspaceTags.ARRANGEMENT_APPROVE).assertExists()
        onNodeWithText("Transition out: build").assertExists()
    }

    @Test
    fun `mix and transport expose available artifact controls`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val project = projectState().project!!.copy(
            readiness = ai.music.workstation.application.ProjectReadiness(true, true, true, true, true, true, true, true, true, true)
        )
        val mix = ai.music.workstation.application.MixSnapshot(
            root, ai.music.workstation.application.PersistedMixSettings(), listOf("piano"), root.resolve("mix/dry.wav"), stale = false
        )
        val arrangement = ai.music.workstation.application.ArrangementSnapshot(root, emptyList(), false, true, false, root.resolve("arrangement.json"))
        setContent { MusicWorkstationTheme { WorkspaceScreen(WorkspaceUiState(project = project, arrangement = arrangement, mix = mix), onIntent = {}) } }

        onNodeWithTag(WorkspaceTags.BUILD_SONG).assertExists()
        onNodeWithTag(WorkspaceTags.MIX_RESET).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_DRY).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_LOFI).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_MASTER).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_SEEK).assertExists()
        onNodeWithTag(WorkspaceTags.PLAYBACK_VOLUME).assertExists()
    }

    @Test
    fun `selected preview shows lifecycle states labels controls and recovery`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val source = PreviewSourceIdentity(root, "A", root.resolve("previews/piano-A.wav"))

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(preview = PreviewUiState(source, PreviewPhase.PREPARING, elapsedSeconds = 2.0, durationSeconds = 12.0)), onIntent = {})
            }
        }
        onNodeWithTag(WorkspaceTags.PREVIEW_TRANSPORT).assertIsDisplayed()
        onNodeWithText("Part A").assertIsDisplayed()
        onNodeWithText("Preparing monitor audio…").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.PREVIEW_TOGGLE).assertIsNotEnabled()
        onNodeWithTag(WorkspaceTags.PREVIEW_STOP).assertIsEnabled()
        onNodeWithTag(WorkspaceTags.PREVIEW_SEEK).assertIsNotEnabled()

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(preview = PreviewUiState(source, PreviewPhase.PLAYING, elapsedSeconds = 2.0, durationSeconds = 12.0)), onIntent = {})
            }
        }
        onNodeWithText("Playing").assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.PREVIEW_TOGGLE).assertIsEnabled()
        onNodeWithTag(WorkspaceTags.PREVIEW_SEEK).assertIsEnabled()

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(preview = PreviewUiState(source, PreviewPhase.FAILED, "Analyze A before previewing it.")), onIntent = {})
            }
        }
        onNodeWithText("Preview unavailable").assertIsDisplayed()
        onNodeWithText("Analyze A before previewing it.").assertExists()
        onNodeWithTag(WorkspaceTags.PREVIEW_RETRY).assertIsEnabled()
    }

    @Test
    fun `selected audio preparation shows no issue guidance and bounded A B transcription controls`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/test-project")
        val audioPart = ai.music.workstation.application.PartSummary("A", "verse", "source/A.wav", "A.wav", ai.music.workstation.application.PartSourceType.AUDIO, null)
        val project = projectState().project!!.copy(parts = listOf(audioPart))
        val snapshot = preparationSnapshot("A", recommendation = false, clean = true)
        setContent {
            MusicWorkstationTheme {
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
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmSafeCleanup("A")), onIntent = {})
            }
        }
        onNodeWithText("Apply safe cleanup to A?").assertIsDisplayed()
        onNodeWithText("The original source remains available and unchanged.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `MIDI quality inspector shows metrics warnings profiles and downstream next action`() = runComposeUiTest {
        val project = projectState().project!!.copy(parts = listOf(qualityPart(ai.music.workstation.application.MidiQualityStatus.CURRENT)))
        setContent {
            MusicWorkstationTheme {
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
            ai.music.workstation.application.MidiQualityStatus.LEGACY_UNKNOWN to "Legacy MIDI has no raw-to-clean quality record.",
            ai.music.workstation.application.MidiQualityStatus.STALE_OR_INVALID to "The raw-to-clean quality report or clean MIDI is stale or invalid."
        ).forEach { (status, message) ->
            val project = projectState().project!!.copy(parts = listOf(qualityPart(status)))
            setContent { MusicWorkstationTheme { WorkspaceScreen(WorkspaceUiState(project = project, selectedPartId = "A"), onIntent = {}) } }
            onNodeWithText(message).assertIsDisplayed()
        }
        val project = projectState().project!!.copy(parts = listOf(qualityPart(ai.music.workstation.application.MidiQualityStatus.CURRENT)))
        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(
                    WorkspaceUiState(
                        project = project,
                        selectedPartId = "A",
                        midiQualityReview = MidiQualityReviewDraft(ai.music.workstation.arrangement.MidiCleanupProfile.TIGHTEN_TIMING),
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
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmDiscardDraft(root = java.nio.file.Path.of("build/other"))), onIntent = {})
            }
        }
        onNodeWithText("Discard arrangement draft?").assertIsDisplayed()
        onNodeWithText("Discard and continue").assertIsDisplayed()

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(dialog = WorkspaceDialog.ConfirmClose), onIntent = {})
            }
        }
        onNodeWithText("Close Personal AI Music Arranger?").assertIsDisplayed()
        onNodeWithText("Keep working").assertIsDisplayed()
    }

    @Test
    fun `sound library dialog exposes choose clear and validation feedback`() = runComposeUiTest {
        setContent {
            MusicWorkstationTheme {
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
            setContent { MusicWorkstationTheme { WorkspaceScreen(projectState().copy(runtimeReadiness = readiness), onIntent = {}) } }
            onNodeWithTag(WorkspaceTags.PROJECT_HEADER).assertIsDisplayed()
        }
    }

    @Test
    fun `creation header exposes all stages dispatches its next action and pauses during work`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MusicWorkstationTheme { WorkspaceScreen(projectState(), intents::add) } }

        CreationStage.entries.forEach { stage ->
            onNodeWithTag(WorkspaceTags.CREATION_STAGE_PREFIX + stage.name.lowercase()).assertExists()
        }
        onNodeWithTag(WorkspaceTags.CREATION_DEPENDENCY).assertIsDisplayed()
        onNodeWithTag(WorkspaceTags.CREATION_CHECKLIST).assertExists()
        onNodeWithText("Next safe action").assertExists()
        onNodeWithTag(WorkspaceTags.CREATION_STAGE_PREFIX + "project").performClick()
        assertEquals(WorkspaceIntent.ShowImportPart(audio = false), intents.last())

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(projectState().copy(operation = WorkspaceOperation.ImportingPart("A")), onIntent = {})
            }
        }
        onNodeWithTag(WorkspaceTags.CREATION_NEXT_ACTION).assertIsNotEnabled()
        onNodeWithText("Operation in progress").assertExists()
    }

    @Test
    fun `creation stepper gives blocked stages an accessible reason and recovery action`() = runComposeUiTest {
        val intents = mutableListOf<WorkspaceIntent>()
        setContent { MusicWorkstationTheme { WorkspaceScreen(projectState(), intents::add) } }

        val stage = onNodeWithTag(WorkspaceTags.CREATION_STAGE_PREFIX + "structure")
        assertEquals(
            listOf("Structure: Blocked. Every part must be prepared before the structure can be arranged. Recovery: Import a MIDI, WAV, or MP3 source."),
            stage.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )
        stage.performClick()

        assertEquals(WorkspaceIntent.ShowImportPart(audio = false), intents.last())
    }

    @Test
    fun `creation header explains Qwen approval and completed release states`() = runComposeUiTest {
        val root = java.nio.file.Path.of("build/creation-header")
        val part = ai.music.workstation.application.PartSummary(
            "A", "verse", "source/A.mid", "A.mid", ai.music.workstation.application.PartSourceType.MIDI,
            ai.music.workstation.application.PartAnalysisSummary(ai.music.workstation.application.PartAnalysisStatus.MIDI, "analysis/A.json", 4, 4.0, "C major"),
            ai.music.workstation.application.PartPreparationSummary(
                sourcePreserved = true, inspected = true, preparedAudio = false, rawMidi = true,
                cleanMidi = true, analyzed = true, ready = true, warnings = emptyList(),
                midiQuality = ai.music.workstation.application.MidiQualitySummary(ai.music.workstation.application.MidiQualityStatus.CURRENT)
            )
        )
        val project = ai.music.workstation.application.ProjectSnapshot(
            root, 2, "creation-header", ai.music.workstation.arrangement.RenderFormat(), listOf(part),
            listOf(ai.music.workstation.application.StructureSectionSummary(0, "A", 1, "A1", 4.0)),
            ai.music.workstation.application.ProjectReadiness(true, true, true, false, false, false, false, false, false, false)
        )
        val draft = ai.music.workstation.application.ArrangementSnapshot(
            root, listOf(ai.music.workstation.application.ArrangementSectionSnapshot(0, "A1", "A", "verse", 0.5, emptyList(), "none", 4.0)),
            approvalRequired = true, approved = false, stale = false, artifact = root.resolve("arrangement.draft.json")
        )
        assertEquals(CreationIntent.APPROVE_ARRANGEMENT, CreationProgressDeriver.derive(CreationProgressInput(project, draft)).nextAction.intent)
        setContent { MusicWorkstationTheme { WorkspaceScreen(WorkspaceUiState(project = project, arrangement = draft), onIntent = {}) } }
        onNodeWithText("Start approve arrangement").assertExists()
        onNodeWithTag(WorkspaceTags.CREATION_NEXT_ACTION).assertIsEnabled()

        setContent {
            MusicWorkstationTheme {
                WorkspaceScreen(
                    WorkspaceUiState(
                        project = project.copy(readiness = project.readiness.copy(masterAvailable = true, releaseAvailable = true)),
                        arrangement = draft.copy(approvalRequired = false, approved = true, artifact = root.resolve("arrangement.json"))
                    ),
                    onIntent = {}
                )
            }
        }
        onNodeWithText("Mix & Master · Complete").assertExists()
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
        return WorkspaceUiState(project = ai.music.workstation.application.ProjectSnapshot(
            root = root,
            version = 2,
            name = "test-project",
            renderFormat = ai.music.workstation.arrangement.RenderFormat(),
            parts = emptyList(),
            structure = emptyList(),
            readiness = ai.music.workstation.application.ProjectReadiness(false, false, false, false, false, false, false, false, false, false)
        ), runtimeReadiness = runtimeReadiness(DependencyReadiness(DependencyStatus.READY, "ready")))
    }

    private fun qualityPart(status: ai.music.workstation.application.MidiQualityStatus): ai.music.workstation.application.PartSummary {
        val cleanup = ai.music.workstation.arrangement.MidiCleanupOptions()
        val report = if (status == ai.music.workstation.application.MidiQualityStatus.CURRENT) {
            val raw = ai.music.workstation.arrangement.MidiQualityArtifact("0".repeat(64), 12, 3.0, null, 2, 480, 1.0, null, listOf(ai.music.workstation.arrangement.MidiTempoChange(0, 120.0)), listOf(ai.music.workstation.arrangement.MidiTimeSignature(0, 4, 4)))
            val clean = raw.copy(sha256 = "1".repeat(64), noteCount = 10, notesPerSecond = 2.5)
            ai.music.workstation.arrangement.MidiQualityReport(
                partId = "A", raw = raw, clean = clean, cleanup = cleanup,
                timing = ai.music.workstation.arrangement.MidiTimingChangeSummary(10, 2, 0, 2, 1, 48, 24),
                tempoAndTimeSignaturesPreserved = true,
                warnings = listOf(ai.music.workstation.arrangement.MidiQualityWarning(ai.music.workstation.arrangement.MidiQualityWarningCode.LARGE_TIMING_SHIFT, "Cleanup shifted note timing by more than 240 ticks."))
            )
        } else null
        val quality = ai.music.workstation.application.MidiQualitySummary(
            status,
            cleanup = if (status == ai.music.workstation.application.MidiQualityStatus.CURRENT) cleanup else null,
            warnings = report?.warnings.orEmpty(),
            report = report
        )
        return ai.music.workstation.application.PartSummary(
            "A", "verse", "source/A.mid", "A.mid", ai.music.workstation.application.PartSourceType.MIDI, null,
            ai.music.workstation.application.PartPreparationSummary(false, false, false, true, true, false, false, emptyList(), quality)
        )
    }

    private fun preparationSnapshot(partId: String, recommendation: Boolean, clean: Boolean): ai.music.workstation.application.AudioPreparationSnapshot {
        val source = ai.music.workstation.preparation.InspectionSourceIdentity("source/$partId.wav", "0".repeat(64))
        val measurements = ai.music.workstation.preparation.AudioInspectionMeasurements(
            0.5, 0.2, 0.0, 0, 0,
            ai.music.workstation.preparation.SilenceEvidence(0, 0),
            ai.music.workstation.preparation.SignalIndicator(ai.music.workstation.preparation.EvidenceLevel.NONE, 0.0),
            ai.music.workstation.preparation.SignalIndicator(ai.music.workstation.preparation.EvidenceLevel.NONE, 0.0)
        )
        val report = ai.music.workstation.preparation.InputInspectionReport(
            partId = partId, source = source,
            detectedInput = ai.music.workstation.preparation.DetectedInput(ai.music.workstation.preparation.InputContainer.RIFF_WAVE, "pcm", "wav"),
            durationSeconds = 1.0, audioFormat = ai.music.workstation.preparation.DetectedAudioFormat(44100, 1, 24), measurements = measurements
        )
        val plan = if (!recommendation) null else ai.music.workstation.preparation.InputCleanupPlan(
            partId = partId, source = source, mode = ai.music.workstation.preparation.InputCleanupMode.SAFE_CLEANUP,
            operations = listOf(ai.music.workstation.preparation.CleanupPlanOperation(ai.music.workstation.preparation.CleanupOperationType.DC_REMOVAL)),
            evidence = measurements, confidence = 0.5, transcriptionInput = ai.music.workstation.preparation.TranscriptionInputArtifact.CLEAN_WAV
        )
        return ai.music.workstation.application.AudioPreparationSnapshot(partId, ai.music.workstation.application.AudioPreparationAvailability.AVAILABLE, report, plan, cleanWavAvailable = clean)
    }
}

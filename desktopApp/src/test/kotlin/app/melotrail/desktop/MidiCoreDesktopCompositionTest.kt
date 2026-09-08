package app.melotrail.desktop

import app.melotrail.application.MidiCoreAcceptedSongAssembly
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreArrangementPlanProposalUseCase
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.Test

class MidiCoreDesktopCompositionTest {
    @Test
    fun `target composition wires every MIDI Core use case without starting external services`() {
        val services = MidiCoreDesktopComposition.create(
            artifacts = MidiCoreArtifactStore(),
            dialogs = NullMidiCoreDesktopFileDialogs,
            preferences = NoOpMidiCoreDesktopPreferences,
            logger = NoOpDesktopOperationLogger,
        )

        assertIs<MidiCoreProjectLifecycle>(services.project)
        assertIs<MidiCoreSourceImport>(services.sourceImport)
        assertIs<MidiCoreMusicalAuthority>(services.authority)
        assertIs<MidiCoreStructureTimeline>(services.structure)
        assertIs<MidiCoreAuthoritativeHarmony>(services.harmony)
        assertIs<MidiCoreArrangementPlanProposalUseCase>(services.arrangementPlan)
        assertIs<MidiCoreCandidateGeneration>(services.generation)
        assertIs<MidiCoreCandidateReview>(services.review)
        assertIs<MidiCoreAcceptedSongAssembly>(services.assembly)
        assertIs<MidiCoreMidiPackageExporter>(services.export)
        assertIs<DefaultMidiCoreWorkspaceUseCases>(services.workspace)
        assertEquals(NoOpMidiCoreDesktopPreferences, services.preferences)
        assertTrue(services.audition.state.sessionId == null)
        assertFalse(services.audition.state.isClosed)
        services.audition.close()
        assertTrue(services.audition.state.isClosed)
    }

    @Test
    fun `default main delegates to target entrypoint and target graph has no worker construction`() {
        val mainSource = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/DesktopMain.kt"))
        val defaultMain = mainSource.substringAfter("fun main()").substringBefore("/**")
        assertTrue(defaultMain.contains("MidiCoreDesktopEntrypoint.run()"))
        assertFalse(defaultMain.contains("WorkerClient"))
        assertFalse(defaultMain.contains("WorkspaceViewModel"))
        assertFalse(defaultMain.contains("DefaultArrangementApplicationService"))

        val targetSource = Files.readString(sourceFile("src/main/kotlin/app/melotrail/desktop/MidiCoreDesktopComposition.kt"))
        assertFalse(targetSource.contains("WorkerClient"))
        assertFalse(targetSource.contains("DefaultBuildApplicationService"))
        assertFalse(targetSource.contains("DefaultMixApplicationService"))
        assertFalse(targetSource.contains("LocalQwen"))
    }

    @Test
    fun `desktop production tree retains only the MIDI Core routes and no legacy composition`() {
        val desktopRoot = sourceFile("src/main/kotlin/app/melotrail/desktop/DesktopMain.kt").parent
        val productionFiles = Files.walk(desktopRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                .toList()
        }
        val names = productionFiles.map { it.fileName.toString() }.toSet()
        setOf(
            "WorkspaceApp.kt", "WorkspaceViewModel.kt", "WorkspacePageRouter.kt", "WorkspaceShellFrame.kt",
            "CreationProgress.kt", "DesktopFileDialogs.kt", "HarmonyEditor.kt", "JvmAudioPlayer.kt",
            "MelodyPartsPresentation.kt", "OperationFeedback.kt", "ProjectSetup.kt", "RuntimeReadiness.kt",
            "SoundLibrarySettings.kt", "WorkflowPresentation.kt",
        ).forEach { legacyFile -> assertFalse(legacyFile in names, "Legacy desktop file must be deleted: $legacyFile") }

        val source = productionFiles.joinToString("\n") { Files.readString(it) }
        listOf(
            "DesktopServiceComposition", "DesktopBuildWorker", "DesktopReleaseMp3Exporter",
            "WorkspaceSection", "WorkspaceDestination", "WorkspaceViewModel", "WorkspacePageRouter",
            "OperationKind", "OperationPhase",
            "Mix & Master", "Video Preview",
        ).forEach { legacySymbol ->
            assertFalse(Regex("\\b${Regex.escape(legacySymbol)}\\b").containsMatchIn(source), "Legacy desktop route must not remain: $legacySymbol")
        }
        listOf("\"wav\"", "\"mp3\"").forEach { legacyMediaToken ->
            assertFalse(source.contains(legacyMediaToken), "Legacy desktop media route must not remain: $legacyMediaToken")
        }
        assertTrue(source.contains("MidiCoreDesktopEntrypoint"))
        assertTrue(source.contains("MidiCoreWorkspaceDestination"))
    }

    private fun sourceFile(relativePath: String): Path = sequenceOf(
        Path.of(relativePath),
        Path.of("desktopApp").resolve(relativePath),
    ).first { Files.isRegularFile(it) }
}

private object NullMidiCoreDesktopFileDialogs : MidiCoreDesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = null
    override suspend fun chooseNewProjectDirectory(): Path? = null
    override suspend fun chooseMidiSource(): Path? = null
    override suspend fun chooseExportDirectory(): Path? = null
}

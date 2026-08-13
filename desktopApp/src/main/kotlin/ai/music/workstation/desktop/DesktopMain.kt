package ai.music.workstation.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ai.music.workstation.application.DefaultProjectApplicationService
import ai.music.workstation.application.LegacyPartAnalysisService
import ai.music.workstation.application.MidiPreparationService
import ai.music.workstation.application.ProjectApplicationService
import java.nio.file.Path

fun main() = application {
    val viewModel = WorkspaceViewModel(
        projectService = DesktopServiceComposition.projectService(),
        fileDialogs = SwingDesktopFileDialogs()
    )
    Window(
        onCloseRequest = {
            viewModel.close()
            exitApplication()
        },
        title = "Personal AI Music Arranger"
    ) {
        window.minimumSize = java.awt.Dimension(900, 620)
        window.size = java.awt.Dimension(1440, 900)
        MusicWorkstationTheme {
            WorkspaceApp(viewModel)
        }
    }
}

/**
 * The desktop adapter is deliberately composed from public application-service
 * boundaries. Import and analysis adapters are added with their UI workflows in
 * Task 025; the foundation shell only opens already-valid projects.
 */
object DesktopServiceComposition {
    fun projectService(): ProjectApplicationService = DefaultProjectApplicationService(
        midiPreparation = FoundationMidiPreparationService,
        legacyPartAnalysis = FoundationLegacyPartAnalysisService
    )

    private object FoundationMidiPreparationService : MidiPreparationService {
        override suspend fun transcribe(input: Path, output: Path): Nothing = unavailable()
        override suspend fun clean(input: Path, output: Path): Nothing = unavailable()
    }

    private object FoundationLegacyPartAnalysisService : LegacyPartAnalysisService {
        override suspend fun analyze(source: Path): Nothing = unavailable()
    }

    private fun unavailable(): Nothing = throw UnsupportedOperationException(
        "Part import and analysis are not available in the desktop foundation. Complete Task 025 first."
    )
}

package app.melotrail.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.melotrail.application.MidiCoreAcceptedSongAssembly
import app.melotrail.application.MidiCoreAuthoritativeHarmony
import app.melotrail.application.MidiCoreCandidateGeneration
import app.melotrail.application.MidiCoreCandidateLifecycle
import app.melotrail.application.MidiCoreArrangementStylePreview
import app.melotrail.application.MidiCoreArrangementDraftGeneration
import app.melotrail.application.MidiCoreArrangementDraftAcceptance
import app.melotrail.application.MidiCoreArrangementDraftAcceptanceUndo
import app.melotrail.application.MidiCoreCandidateReview
import app.melotrail.application.MidiCoreReviewAudition
import app.melotrail.application.MidiCoreMidiPackageExporter
import app.melotrail.application.MidiCoreMusicalAuthority
import app.melotrail.application.MidiCoreProjectLifecycle
import app.melotrail.application.MidiCoreSourceImport
import app.melotrail.application.MidiCoreSourceAudition
import app.melotrail.application.MidiCoreStructureTimeline
import app.melotrail.application.MidiCoreExportSnapshotLifecycle
import app.melotrail.audition.MidiAuditionController
import app.melotrail.audition.MidiAuditionPort
import app.melotrail.audition.adapter.JdkMidiAuditionOutput
import app.melotrail.project.adapter.MidiCoreArtifactStore
import java.awt.Desktop
import java.awt.KeyboardFocusManager
import java.nio.file.Path
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The complete target service graph owned by the focused Compose desktop. */
data class MidiCoreDesktopServices(
    val project: MidiCoreProjectLifecycle,
    val sourceImport: MidiCoreSourceImport,
    val authority: MidiCoreMusicalAuthority,
    val structure: MidiCoreStructureTimeline,
    val harmony: MidiCoreAuthoritativeHarmony,
    val generation: MidiCoreCandidateGeneration,
    val draftGeneration: MidiCoreArrangementDraftGeneration,
    val review: MidiCoreCandidateReview,
    val assembly: MidiCoreAcceptedSongAssembly,
    val audition: MidiAuditionPort,
    val export: MidiCoreMidiPackageExporter,
    val workspace: MidiCoreWorkspaceUseCases,
    val dialogs: MidiCoreDesktopFileDialogs,
    val preferences: MidiCoreDesktopPreferences,
    val logger: DesktopOperationLogger,
)

/** Builds the local MIDI-only desktop graph without network, worker, audio, or renderer construction. */
object MidiCoreDesktopComposition {
    /** Construct one target graph, with injectable local adapters for startup and unit tests. */
    fun create(
        artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
        dialogs: MidiCoreDesktopFileDialogs = SwingMidiCoreFileDialogs(),
        preferences: MidiCoreDesktopPreferences = JvmMidiCoreDesktopPreferences(),
        logger: DesktopOperationLogger = LocalDesktopOperationLogger(),
    ): MidiCoreDesktopServices {
        val project = MidiCoreProjectLifecycle(artifacts)
        val sourceImport = MidiCoreSourceImport(artifacts)
        val sourceAudition = MidiCoreSourceAudition(artifacts)
        val authority = MidiCoreMusicalAuthority(artifacts)
        val structure = MidiCoreStructureTimeline(artifacts)
        val harmony = MidiCoreAuthoritativeHarmony(artifacts)
        val candidateLifecycle = MidiCoreCandidateLifecycle(artifacts)
        val generation = MidiCoreCandidateGeneration(artifacts = artifacts, lifecycle = candidateLifecycle)
        val draftGeneration = MidiCoreArrangementDraftGeneration(artifacts = artifacts, candidates = generation)
        val stylePreview = MidiCoreArrangementStylePreview(artifacts = artifacts)
        val review = MidiCoreCandidateReview(artifacts = artifacts, lifecycle = candidateLifecycle, generation = generation)
        val assembly = MidiCoreAcceptedSongAssembly(artifacts)
        val reviewAudition = MidiCoreReviewAudition(review, assembly)
        val draftAcceptance = MidiCoreArrangementDraftAcceptance(artifacts)
        val draftAcceptanceUndo = MidiCoreArrangementDraftAcceptanceUndo(artifacts)
        val audition = MidiAuditionController(JdkMidiAuditionOutput())
        val snapshotLifecycle = MidiCoreExportSnapshotLifecycle(artifacts)
        val export = MidiCoreMidiPackageExporter(
            artifacts = artifacts,
            assembly = assembly,
            snapshotLifecycle = snapshotLifecycle,
        )
        val workspace = DefaultMidiCoreWorkspaceUseCases(
            project = project,
            sourceImport = sourceImport,
            authority = authority,
            structure = structure,
            harmony = harmony,
            generation = generation,
            draftGeneration = draftGeneration,
            review = review,
            exporter = export,
            audition = audition,
            sourceAudition = sourceAudition,
            reviewAudition = reviewAudition,
            stylePreview = stylePreview,
            draftAcceptance = draftAcceptance,
            draftAcceptanceUndo = draftAcceptanceUndo,
        )
        return MidiCoreDesktopServices(
            project = project,
            sourceImport = sourceImport,
            authority = authority,
            structure = structure,
            harmony = harmony,
            generation = generation,
            draftGeneration = draftGeneration,
            review = review,
            assembly = assembly,
            audition = audition,
            export = export,
            workspace = workspace,
            dialogs = dialogs,
            preferences = preferences,
            logger = logger,
        )
    }
}

/** Target application launcher used by the desktop module's default entrypoint. */
object MidiCoreDesktopEntrypoint {
    /** Start a minimal target window while the focused workflow surfaces are composed by later tasks. */
    fun run() {
        application {
            val services = remember { MidiCoreDesktopComposition.create() }
            val workspace = remember(services) {
                MidiCoreWorkspaceViewModel(services.workspace, services.preferences, services.logger)
            }
            val projectActions = remember(services) {
                MidiCoreProjectPageActions(
                    chooseProjectDirectory = { services.dialogs.chooseProjectDirectory() },
                    chooseNewProjectDirectory = { services.dialogs.chooseNewProjectDirectory() },
                )
            }
            val midiActions = remember(services) {
                MidiCoreMidiPageActions(
                    chooseMidiSource = { services.dialogs.chooseMidiSource() },
                )
            }
            val exportActions = remember(services) {
                MidiCoreExportPageActions { directory ->
                    runCatching {
                        if (Desktop.isDesktopSupported() && directory.toFile().isDirectory) {
                            Desktop.getDesktop().open(directory.toFile())
                        }
                    }
                }
            }
            val desktopWindowState = rememberWindowState(placement = WindowPlacement.Maximized)
            Window(
                state = desktopWindowState,
                onCloseRequest = {
                    workspace.close()
                    exitApplication()
                },
                title = "Melotrail",
            ) {
                window.minimumSize = java.awt.Dimension(900, 620)
                MelotrailTheme {
                    MidiCoreStartupSurface(workspace, projectActions, midiActions, exportActions)
                }
            }
        }
    }
}

/** Target shell with only the six MIDI Core workflow destinations. */
@Composable
private fun MidiCoreStartupSurface(
    workspace: MidiCoreWorkspaceViewModel,
    projectActions: MidiCoreProjectPageActions,
    midiActions: MidiCoreMidiPageActions,
    exportActions: MidiCoreExportPageActions,
) {
    MidiCoreWorkspaceShell(workspace, projectActions = projectActions, midiActions = midiActions, exportActions = exportActions)
}

/** Target-only file-dialog boundary; it exposes MIDI and project locations, never audio or sound-library settings. */
interface MidiCoreDesktopFileDialogs {
    suspend fun chooseProjectDirectory(): Path?
    suspend fun chooseNewProjectDirectory(): Path?
    suspend fun chooseMidiSource(): Path?
    suspend fun chooseExportDirectory(): Path?
}

/** Swing implementation of the target-only project, MIDI-source, and export dialogs. */
class SwingMidiCoreFileDialogs : MidiCoreDesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = chooseDirectory("Open MIDI Core project")

    override suspend fun chooseNewProjectDirectory(): Path? = chooseDirectory("Choose new MIDI Core project folder")

    override suspend fun chooseMidiSource(): Path? = chooseFile("Choose MIDI source") {
        isAcceptAllFileFilterUsed = false
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Standard MIDI files", "mid", "midi")
    }

    override suspend fun chooseExportDirectory(): Path? = chooseDirectory("Choose MIDI export folder")

    private suspend fun chooseDirectory(title: String): Path? = chooseFile(title) {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }

    private suspend fun chooseFile(title: String, configure: JFileChooser.() -> Unit): Path? = suspendCancellableCoroutine { continuation ->
        val open = Runnable {
            try {
                val chooser = JFileChooser().apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    configure()
                }
                val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                val selected = if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
                if (continuation.isActive) continuation.resume(selected)
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) open.run() else SwingUtilities.invokeLater(open)
    }
}

/** Target-only convenience preferences; project JSON remains the source of truth. */
interface MidiCoreDesktopPreferences {
    fun lastOpenedProject(): Path?
    fun saveLastOpenedProject(root: Path)
    fun clearLastOpenedProject()
}

/** JVM preference adapter that stores only the last successfully opened MIDI Core project. */
class JvmMidiCoreDesktopPreferences(
    private val preferences: Preferences = Preferences.userNodeForPackage(JvmMidiCoreDesktopPreferences::class.java),
) : MidiCoreDesktopPreferences {
    override fun lastOpenedProject(): Path? = preferences.get(LAST_PROJECT_KEY, null)?.let { raw ->
        runCatching { Path.of(raw).toAbsolutePath().normalize() }.getOrNull()
    }

    override fun saveLastOpenedProject(root: Path) {
        runCatching { preferences.put(LAST_PROJECT_KEY, root.toAbsolutePath().normalize().toString()) }
    }

    override fun clearLastOpenedProject() {
        runCatching { preferences.remove(LAST_PROJECT_KEY) }
    }

    private companion object {
        const val LAST_PROJECT_KEY = "last-successfully-opened-project"
    }
}

/** No-op target preferences for composition tests and non-persistent embedding. */
object NoOpMidiCoreDesktopPreferences : MidiCoreDesktopPreferences {
    override fun lastOpenedProject(): Path? = null
    override fun saveLastOpenedProject(root: Path) = Unit
    override fun clearLastOpenedProject() = Unit
}

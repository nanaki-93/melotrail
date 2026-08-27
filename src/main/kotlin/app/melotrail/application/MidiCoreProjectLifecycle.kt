package app.melotrail.application

import app.melotrail.project.MidiCoreProject
import app.melotrail.project.MidiCoreProjectDocument
import app.melotrail.project.MidiCoreProjectSchema
import app.melotrail.project.ProjectId
import app.melotrail.project.ProjectMetadata
import app.melotrail.project.UnsupportedMidiCoreProjectException
import app.melotrail.project.adapter.MidiCoreArtifactStore
import app.melotrail.project.adapter.MidiCoreProjectSaveException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Application boundary for target project creation, persistence, opening, and closing. */
class MidiCoreProjectLifecycle(
    private val artifacts: MidiCoreArtifactStore = MidiCoreArtifactStore(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "project-${UUID.randomUUID()}" },
) {
    fun create(request: CreateMidiCoreProject): MidiCoreProjectLifecycleResult {
        val root = request.root.toAbsolutePath().normalize()
        val project = try {
            MidiCoreProject(
                id = ProjectId(request.id ?: idFactory()),
                metadata = ProjectMetadata(request.name, Instant.now(clock).toString(), request.applicationVersion),
            )
        } catch (error: IllegalArgumentException) {
            return rejected(MidiCoreProjectProblemCode.INVALID_REQUEST, error.message ?: "Project details are invalid.", "Correct the project name or identifier and retry.")
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(root)) {
            return rejected(MidiCoreProjectProblemCode.INVALID_LOCATION, "The project location is not a directory.", "Choose a directory for the project.")
        }
        if (Files.exists(root.resolve(MidiCoreArtifactStore.PROJECT_FILE), LinkOption.NOFOLLOW_LINKS)) {
            return rejected(MidiCoreProjectProblemCode.PROJECT_ALREADY_EXISTS, "A project already exists in this folder.", "Open it or choose an empty folder.")
        }
        return try {
            artifacts.initialize(root)
            artifacts.saveProject(root, project)
            opened(root, project)
        } catch (error: MidiCoreProjectSaveException) {
            rejected(MidiCoreProjectProblemCode.SAVE_FAILED, "The project could not be saved safely.", "Retry the save; the recovery file remains available for inspection.")
        } catch (error: Exception) {
            rejected(MidiCoreProjectProblemCode.IO_FAILURE, "The project folder could not be created.", "Check the folder permissions and retry.")
        }
    }

    fun open(projectRoot: Path): MidiCoreProjectLifecycleResult {
        val root = projectRoot.toAbsolutePath().normalize()
        if (!Files.isDirectory(root) || !Files.isRegularFile(root.resolve(MidiCoreArtifactStore.PROJECT_FILE), LinkOption.NOFOLLOW_LINKS)) {
            return rejected(MidiCoreProjectProblemCode.PROJECT_NOT_FOUND, "No MIDI Core project was found in this folder.", "Choose a folder containing project.json.")
        }
        return try {
            opened(root, artifacts.openProject(root))
        } catch (error: UnsupportedMidiCoreProjectException) {
            rejected(MidiCoreProjectProblemCode.UNSUPPORTED_PROJECT, "This project uses an unsupported legacy or future schema.", "Create a MIDI Core project instead; legacy audio projects are not migrated.")
        } catch (error: IllegalArgumentException) {
            rejected(MidiCoreProjectProblemCode.INVALID_PROJECT, "The MIDI Core project document is invalid or its artifacts no longer match.", "Restore the project from a known-good copy or inspect the reported artifact.")
        } catch (error: Exception) {
            rejected(MidiCoreProjectProblemCode.IO_FAILURE, "The project could not be opened.", "Check that the folder remains available and retry.")
        }
    }

    fun save(session: MidiCoreProjectSession, project: MidiCoreProject): MidiCoreProjectLifecycleResult = try {
        artifacts.saveProject(session.root, project)
        opened(session.root, project)
    } catch (error: MidiCoreProjectSaveException) {
        rejected(MidiCoreProjectProblemCode.SAVE_FAILED, "The project could not be saved safely.", "Retry the save; the last known-good project remains available.")
    } catch (error: IllegalArgumentException) {
        rejected(MidiCoreProjectProblemCode.ARTIFACT_INTEGRITY, "The project references a missing, changed, or invalid artifact.", "Restore the artifact or correct the project state before saving.")
    } catch (error: Exception) {
        rejected(MidiCoreProjectProblemCode.IO_FAILURE, "The project could not be saved.", "Check the project folder permissions and retry.")
    }

    fun close(session: MidiCoreProjectSession): MidiCoreProjectCloseResult =
        MidiCoreProjectCloseResult.Closed(session.root, session.project.id)

    /** Classifies a project JSON document without writing or migrating it. */
    fun inspect(document: String): MidiCoreProjectDocument = MidiCoreProjectSchema.inspect(document)

    private fun opened(root: Path, project: MidiCoreProject): MidiCoreProjectLifecycleResult.Opened =
        MidiCoreProjectLifecycleResult.Opened(MidiCoreProjectSession(root, project))

    private fun rejected(
        code: MidiCoreProjectProblemCode,
        message: String,
        nextAction: String,
    ): MidiCoreProjectLifecycleResult.Rejected =
        MidiCoreProjectLifecycleResult.Rejected(MidiCoreProjectProblem(code, message, nextAction))
}

data class CreateMidiCoreProject(
    val root: Path,
    val name: String,
    val id: String? = null,
    val applicationVersion: String? = null,
)

data class MidiCoreProjectSession(val root: Path, val project: MidiCoreProject)

sealed interface MidiCoreProjectLifecycleResult {
    data class Opened(val session: MidiCoreProjectSession) : MidiCoreProjectLifecycleResult
    data class Rejected(val problem: MidiCoreProjectProblem) : MidiCoreProjectLifecycleResult
}

sealed interface MidiCoreProjectCloseResult {
    data class Closed(val root: Path, val projectId: ProjectId) : MidiCoreProjectCloseResult
}

data class MidiCoreProjectProblem(
    val code: MidiCoreProjectProblemCode,
    val message: String,
    val nextAction: String,
)

enum class MidiCoreProjectProblemCode {
    INVALID_REQUEST,
    INVALID_LOCATION,
    PROJECT_ALREADY_EXISTS,
    PROJECT_NOT_FOUND,
    UNSUPPORTED_PROJECT,
    INVALID_PROJECT,
    ARTIFACT_INTEGRITY,
    SAVE_FAILED,
    IO_FAILURE,
}

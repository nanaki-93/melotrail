package app.melotrail.video.application

import app.melotrail.video.domain.VideoProject
import java.io.IOException
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Persistence port used by the independent Video workspace. */
interface VideoProjectPersistence {
    fun create(projectRoot: Path, project: VideoProject): VideoProject
    fun open(projectRoot: Path): VideoProject
    fun save(projectRoot: Path, expectedRevision: Long, project: VideoProject): VideoProject
}

/** Coordinates project creation and revision-guarded persistence without MIDI state. */
class VideoProjectLifecycle(
    private val projects: VideoProjectPersistence,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "video-project-${UUID.randomUUID()}" },
) {
    fun create(request: CreateVideoProject): VideoProjectLifecycleResult {
        val project = try {
            VideoProject(
                id = request.id ?: idFactory(),
                name = request.name,
                createdAt = Instant.now(clock).toString(),
                applicationVersion = request.applicationVersion,
            )
        } catch (error: IllegalArgumentException) {
            return rejected(
                VideoProjectProblemCode.INVALID_REQUEST,
                error.message ?: "Video project details are invalid.",
                "Correct the project name or identifier and retry.",
            )
        }
        return try {
            opened(request.root, projects.create(request.root, project))
        } catch (error: VideoProjectAlreadyExistsException) {
            rejected(VideoProjectProblemCode.PROJECT_ALREADY_EXISTS, error.message ?: "A video project already exists.", "Open the existing video project or choose an empty folder.")
        } catch (error: UnsafeVideoProjectLocationException) {
            rejected(VideoProjectProblemCode.INVALID_LOCATION, error.message ?: "The video project location is unsafe.", "Choose video storage outside every MIDI project and export root.")
        } catch (error: VideoProjectSaveException) {
            rejected(VideoProjectProblemCode.SAVE_FAILED, error.message ?: "The video project could not be saved safely.", "Retry in the same folder; any last known-good project remains unchanged.")
        } catch (error: Exception) {
            rejected(VideoProjectProblemCode.IO_FAILURE, "The video project folder could not be created.", "Check the folder and permissions, then retry.")
        }
    }

    fun open(projectRoot: Path): VideoProjectLifecycleResult = try {
        opened(projectRoot, projects.open(projectRoot))
    } catch (error: VideoProjectNotFoundException) {
        rejected(VideoProjectProblemCode.PROJECT_NOT_FOUND, error.message ?: "No video project was found.", "Choose a folder containing a video-project.json file.")
    } catch (error: UnsafeVideoProjectLocationException) {
        rejected(VideoProjectProblemCode.INVALID_LOCATION, error.message ?: "The video project location is unsafe.", "Choose video storage outside every MIDI project and export root.")
    } catch (error: UnsupportedVideoProjectException) {
        rejected(VideoProjectProblemCode.UNSUPPORTED_PROJECT, error.message ?: "The video project schema is unsupported.", "Open it with a compatible Melotrail version; the file was not changed.")
    } catch (error: InvalidVideoProjectException) {
        rejected(VideoProjectProblemCode.INVALID_PROJECT, error.message ?: "The video project is invalid.", "Restore the video project document or referenced artifact from a known-good copy.")
    } catch (error: Exception) {
        rejected(VideoProjectProblemCode.IO_FAILURE, "The video project could not be opened.", "Check that the folder remains available and retry.")
    }

    fun save(session: VideoProjectSession, project: VideoProject): VideoProjectLifecycleResult {
        if (project.id != session.project.id) {
            return rejected(VideoProjectProblemCode.INVALID_REQUEST, "A save cannot replace the video project identity.", "Save changes to the opened video project.")
        }
        val nextRevision = runCatching { Math.addExact(session.project.revision, 1L) }.getOrNull()
        if (nextRevision == null || project.revision != nextRevision) {
            return rejected(
                VideoProjectProblemCode.INVALID_REQUEST,
                "The next video project revision must advance by exactly one.",
                "Reload the project and prepare one new revision.",
            )
        }
        return try {
            opened(session.root, projects.save(session.root, session.project.revision, project))
        } catch (error: VideoProjectConcurrencyException) {
            rejected(VideoProjectProblemCode.CONCURRENT_WRITE, error.message ?: "The video project changed before this save.", "Reopen the video project, reapply the intended selection, and save again.")
        } catch (error: UnsupportedVideoProjectException) {
            rejected(VideoProjectProblemCode.UNSUPPORTED_PROJECT, error.message ?: "The video project schema is unsupported.", "Open it with a compatible Melotrail version; the file was not changed.")
        } catch (error: InvalidVideoProjectException) {
            rejected(VideoProjectProblemCode.INVALID_PROJECT, error.message ?: "The video project is invalid.", "Restore the last known-good document or artifact before saving.")
        } catch (error: UnsafeVideoProjectLocationException) {
            rejected(VideoProjectProblemCode.INVALID_LOCATION, error.message ?: "The video project location is unsafe.", "Move the video project outside every MIDI project and export root.")
        } catch (error: VideoProjectSaveException) {
            rejected(VideoProjectProblemCode.SAVE_FAILED, error.message ?: "The video project could not be saved safely.", "Retry the save; the last known-good project remains available.")
        } catch (error: IllegalArgumentException) {
            rejected(VideoProjectProblemCode.IMMUTABLE_HISTORY, error.message ?: "Immutable video history changed.", "Append a new version and update its selected ID instead.")
        } catch (error: Exception) {
            rejected(VideoProjectProblemCode.IO_FAILURE, "The video project could not be saved.", "Check the folder permissions and retry.")
        }
    }

    fun close(session: VideoProjectSession): VideoProjectCloseResult =
        VideoProjectCloseResult.Closed(session.root, session.project.id)

    private fun opened(root: Path, project: VideoProject): VideoProjectLifecycleResult.Opened =
        // Keep symlink/parent traversal semantics for the persistence adapter on the next operation.
        VideoProjectLifecycleResult.Opened(VideoProjectSession(root.toAbsolutePath(), project))

    private fun rejected(code: VideoProjectProblemCode, message: String, nextAction: String) =
        VideoProjectLifecycleResult.Rejected(VideoProjectProblem(code, message, nextAction))
}

data class CreateVideoProject(
    val root: Path,
    val name: String,
    val id: String? = null,
    val applicationVersion: String? = null,
)

data class VideoProjectSession(val root: Path, val project: VideoProject)

sealed interface VideoProjectLifecycleResult {
    data class Opened(val session: VideoProjectSession) : VideoProjectLifecycleResult
    data class Rejected(val problem: VideoProjectProblem) : VideoProjectLifecycleResult
}

sealed interface VideoProjectCloseResult {
    data class Closed(val root: Path, val projectId: String) : VideoProjectCloseResult
}

data class VideoProjectProblem(val code: VideoProjectProblemCode, val message: String, val nextAction: String)

enum class VideoProjectProblemCode {
    INVALID_REQUEST,
    INVALID_LOCATION,
    PROJECT_ALREADY_EXISTS,
    PROJECT_NOT_FOUND,
    UNSUPPORTED_PROJECT,
    INVALID_PROJECT,
    IMMUTABLE_HISTORY,
    CONCURRENT_WRITE,
    SAVE_FAILED,
    IO_FAILURE,
}

class UnsafeVideoProjectLocationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class VideoProjectAlreadyExistsException(message: String) : IllegalStateException(message)
class VideoProjectNotFoundException(message: String) : IllegalStateException(message)
class UnsupportedVideoProjectException(message: String) : IllegalArgumentException(message)
class InvalidVideoProjectException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class VideoProjectConcurrencyException(message: String) : IllegalStateException(message)
class VideoProjectSaveException(
    message: String,
    val projectFile: Path,
    val recoveryEvidence: Path?,
    cause: Throwable,
) : IOException(message, cause)

package ai.music.workstation.desktop

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlin.coroutines.resume

interface DesktopFileDialogs {
    suspend fun chooseProjectDirectory(): Path?
    suspend fun chooseNewProjectDirectory(): Path?
    /** Filters are only selection hints; the application service validates the actual input. */
    suspend fun choosePartSource(): Path?
    suspend fun chooseSoundLibraryDirectory(): Path?
}

class SwingDesktopFileDialogs : DesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = suspendCancellableCoroutine { continuation ->
        chooseDirectory("Open arranger project", continuation)
    }

    override suspend fun chooseNewProjectDirectory(): Path? = suspendCancellableCoroutine { continuation ->
        chooseDirectory("Choose new project folder", continuation)
    }

    override suspend fun choosePartSource(): Path? = suspendCancellableCoroutine { continuation ->
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose MIDI, WAV, or MP3 source"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                "Supported sources (MIDI, WAV, MP3)", "mid", "midi", "wav", "wave", "mp3"
            )
        }
        val selected = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
        if (continuation.isActive) continuation.resume(selected)
    }

    override suspend fun chooseSoundLibraryDirectory(): Path? = suspendCancellableCoroutine { continuation ->
        chooseDirectory("Choose sound-library folder", continuation)
    }

    private fun chooseDirectory(
        title: String,
        continuation: kotlinx.coroutines.CancellableContinuation<Path?>
    ) {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val selected = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
        if (continuation.isActive) continuation.resume(selected)
    }
}

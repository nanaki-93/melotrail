package app.melotrail.desktop

import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.KeyboardFocusManager
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        showChooser(continuation) {
            JFileChooser().apply {
                dialogTitle = "Choose MIDI, WAV, or MP3 source"
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter(
                    "Supported sources (MIDI, WAV, MP3)", "mid", "midi", "wav", "wave", "mp3"
                )
            }
        }
    }

    override suspend fun chooseSoundLibraryDirectory(): Path? = suspendCancellableCoroutine { continuation ->
        chooseDirectory("Choose sound-library folder", continuation)
    }

    private fun chooseDirectory(
        title: String,
        continuation: kotlinx.coroutines.CancellableContinuation<Path?>
    ) {
        showChooser(continuation) {
            JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isAcceptAllFileFilterUsed = false
            }
        }
    }

    private fun showChooser(
        continuation: kotlinx.coroutines.CancellableContinuation<Path?>,
        chooser: () -> JFileChooser
    ) {
        val open = Runnable {
            try {
                val dialog = chooser()
                val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                val selected = if (dialog.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) dialog.selectedFile?.toPath() else null
                if (continuation.isActive) continuation.resume(selected)
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) open.run() else SwingUtilities.invokeLater(open)
    }
}

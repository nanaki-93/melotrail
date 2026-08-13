package ai.music.workstation.desktop

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlin.coroutines.resume

interface DesktopFileDialogs {
    suspend fun chooseProjectDirectory(): Path?
}

class SwingDesktopFileDialogs : DesktopFileDialogs {
    override suspend fun chooseProjectDirectory(): Path? = suspendCancellableCoroutine { continuation ->
        val chooser = JFileChooser().apply {
            dialogTitle = "Open arranger project"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val selected = if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.toPath() else null
        if (continuation.isActive) continuation.resume(selected)
    }
}

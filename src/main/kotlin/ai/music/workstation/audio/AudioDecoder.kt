package ai.music.workstation.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

interface AudioDecoder {
    val supportedFormats: Set<String>
    
    fun supports(format: AudioFormat): Boolean
    
    fun supportsExtension(extension: String): Boolean {
        return extension.lowercase() in supportedFormats
    }

    fun decode(path: Path): AudioBuffer

    suspend fun decodeAsync(
        path: Path,
        progress: (Double) -> Unit,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): AudioBuffer = withContext(dispatcher) {
        val buffer = decode(path)
        progress(1.0)
        buffer
    }
}

abstract class BaseDecoder(
    override val supportedFormats: Set<String>
) : AudioDecoder {
    override fun supports(format: AudioFormat): Boolean {
        return format.encoding.lowercase() in supportedFormats
    }
}

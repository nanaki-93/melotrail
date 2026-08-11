package ai.music.workstation.server.service

import ai.music.workstation.audio.*
import ai.music.workstation.model.ErrorReporter as ErrorReporterInterface
import ai.music.workstation.server.dto.AudioExportRequest
import ai.music.workstation.server.dto.UploadResult
import ai.music.workstation.server.dto.WaveformDTO
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.math.abs

class AudioService(storagePath: Path = Paths.get("data/audio")) {
    private val storagePath: Path = storagePath.also { Files.createDirectories(it) }

    fun saveUploadedFile(fileName: String, content: ByteArray): Result<UploadResult> = try {
        val projectId = UUID.randomUUID().toString()
        val trackId = UUID.randomUUID().toString()
        val dir = storagePath.resolve(projectId).resolve("tracks").resolve(trackId)
        Files.createDirectories(dir)
        val ext = fileName.substringAfterLast('.', "wav").lowercase()
        val fileNameWithExt = "$trackId.$ext"
        val filePath = dir.resolve(fileNameWithExt)
        Files.write(filePath, content)
        Result.success(UploadResult(projectId, trackId, fileNameWithExt, filePath.toString()))
    } catch (e: Exception) { Result.failure(e) }

    fun getAudioFile(projectId: String, trackId: String): File? =
        storagePath.resolve(projectId).resolve("tracks").resolve(trackId).toFile()
            .listFiles()?.firstOrNull { it.extension.lowercase() in listOf("wav", "mp3", "flac") }

    fun generateWaveformData(projectId: String, trackId: String): Result<WaveformDTO> = try {
        val audioFile = getAudioFile(projectId, trackId)
            ?: return Result.failure(Exception("Audio file not found"))
        val reporter = object : ErrorReporterInterface {
            override fun report(message: String) = Unit
            override fun report(message: String, cause: Throwable) = Unit
        }
        val buffer = when (audioFile.extension.lowercase()) {
            "wav" -> WAVDecoder(reporter).decode(audioFile.toPath())
            "mp3" -> MP3Decoder(reporter).decode(audioFile.toPath())
            "flac" -> FLACDecoder(reporter).decode(audioFile.toPath())
            else -> return Result.failure(Exception("Unsupported format"))
        }
        val samples = buffer.samples
        val numBuckets = 200
        val bucketSize = samples.size / numBuckets
        val amplitudes = (0 until numBuckets).map { i ->
            val start = i * bucketSize
            val end = (start + bucketSize).coerceAtMost(samples.size)
            var maxAmp = 0f
            for (j in start until end) maxAmp = maxOf(maxAmp, abs(samples[j]))
            maxAmp
        }
        Result.success(WaveformDTO(amplitudes, buffer.format.sampleRate, buffer.duration, buffer.format.channels))
    } catch (e: Exception) { Result.failure(e) }
}

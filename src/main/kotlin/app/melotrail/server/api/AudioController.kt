package app.melotrail.server.api

import app.melotrail.server.dto.AudioExportRequest
import app.melotrail.server.service.AudioService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.*
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/audio")
class AudioController(private val audioService: AudioService) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): ResponseEntity<Any> {
        val result = audioService.saveUploadedFile(file.originalFilename ?: "audio.wav", file.bytes)
        return result.fold(
            onSuccess = { ResponseEntity.status(HttpStatus.CREATED).body(it) },
            onFailure = { ResponseEntity.internalServerError().body<Any>(mapOf("error" to (it.message ?: "Upload failed"))) }
        )
    }

    @GetMapping("/{projectId}/{trackId}")
    fun getAudio(
        @PathVariable projectId: String,
        @PathVariable trackId: String,
        @RequestParam(required = false) format: String?
    ): ResponseEntity<Any> {
        if (format == "waveform") {
            return audioService.generateWaveformData(projectId, trackId).fold(
                onSuccess = { ResponseEntity.ok(it as Any) },
                onFailure = { ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to (it.message ?: "Not found"))) }
            )
        }
        val file = audioService.getAudioFile(projectId, trackId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(mapOf("error" to "Audio file not found"))
        val contentType = when (file.extension.lowercase()) {
            "wav" -> MediaType.parseMediaType("audio/x-wav")
            "mp3" -> MediaType.parseMediaType("audio/mpeg")
            "flac" -> MediaType.parseMediaType("audio/flac")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }
        val headers = HttpHeaders()
        headers.contentType = contentType
        return ResponseEntity.status(HttpStatus.OK).headers(headers).body(ByteArrayResource(file.readBytes()) as Any)
    }

    @PostMapping("/export")
    fun export(@RequestBody request: AudioExportRequest): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(mapOf(
            "message" to "Export queued for processing", "format" to request.format
        ))
}

package ai.music.workstation.application

import ai.music.workstation.arrangement.AnalysisKind
import ai.music.workstation.arrangement.InstrumentRenderer
import ai.music.workstation.arrangement.LogicalInstrument
import ai.music.workstation.arrangement.MidiAnalysis
import ai.music.workstation.arrangement.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToLong

/** Resolves a monitor-only preview without mutating registered source or release artifacts. */
interface PartPreviewApplicationService {
    suspend fun preview(root: Path, partId: String): Path
}

class DefaultPartPreviewApplicationService(private val renderer: InstrumentRenderer) : PartPreviewApplicationService {
    override suspend fun preview(root: Path, partId: String): Path = withContext(Dispatchers.IO) {
        val normalized = root.toAbsolutePath().normalize()
        val project = ProjectStore.read(normalized).also { it.requireValid(normalized) }
        val part = project.parts.find { it.id == partId } ?: throw IllegalArgumentException("Unknown part '$partId'")
        val source = normalized.resolve(part.file)
        if (part.midi == null) {
            require(Files.isRegularFile(source) && source.fileName.toString().substringAfterLast('.', "").lowercase() == "wav") {
                "Audio preview currently requires a decoded WAV source: $source"
            }
            return@withContext source
        }
        val format = requireNotNull(project.renderFormat) { "MIDI preview requires a MIDI-first render format" }
        val analysisRef = requireNotNull(part.analysis) { "Analyze $partId before previewing its clean MIDI" }
        require(analysisRef.kind == AnalysisKind.MIDI) { "MIDI preview requires MIDI analysis for $partId" }
        val analysis = json.decodeFromString(MidiAnalysis.serializer(), Files.readString(normalized.resolve(analysisRef.file), StandardCharsets.UTF_8))
        val clean = normalized.resolve(part.midi.clean)
        val fingerprint = digest(Files.readAllBytes(clean) + "|${format.sampleRate}|${format.channels}|${analysis.durationSeconds}".toByteArray())
        val target = normalized.resolve("previews/piano-$partId-$fingerprint.wav")
        if (Files.isRegularFile(target)) return@withContext target
        Files.createDirectories(checkNotNull(target.parent))
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp.wav")
        try {
            renderer.render(clean, LogicalInstrument.PIANO, temporary, format, (analysis.durationSeconds * format.sampleRate).roundToLong())
            require(Files.isRegularFile(temporary) && Files.size(temporary) > 44L) { "Piano preview renderer did not create audio" }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
        target
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object { val json = Json { ignoreUnknownKeys = false } }
}

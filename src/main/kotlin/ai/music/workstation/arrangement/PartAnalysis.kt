package ai.music.workstation.arrangement

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Deterministic audio metadata for one imported part. */
@Serializable
data class PartAnalysis(
    val duration: Double,
    val sampleRate: Int,
    val channels: Int,
    val frameCount: Long,
    val peak: Double,
    val rms: Double,
    val nearSilence: Boolean
)

/** Stores a part analysis locally and points the part at its JSON file. */
object PartAnalysisStore {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun write(
        projectRoot: Path,
        project: Project,
        partId: String,
        analysis: PartAnalysis
    ): Path {
        val root = projectRoot.toAbsolutePath().normalize()
        project.requireValid(root)
        require(PART_ID.matches(partId)) { "Invalid part ID for analysis: $partId" }
        require(project.parts.any { it.id == partId }) { "Part not found: $partId" }

        val relativeAnalysisPath = "analysis/$partId.json"
        val analysisPath = root.resolve(relativeAnalysisPath)
        Files.createDirectories(analysisPath.parent)
        Files.writeString(analysisPath, json.encodeToString(analysis), StandardCharsets.UTF_8)

        val updated = project.copy(
            parts = project.parts.map { part ->
                if (part.id == partId) {
                    part.copy(analysis = PartAnalysisReference(relativeAnalysisPath))
                } else {
                    part
                }
            }
        )
        updated.requireValid(root)
        Files.writeString(
            root.resolve("project.json"),
            json.encodeToString(updated),
            StandardCharsets.UTF_8
        )
        return analysisPath
    }

    private val PART_ID = Regex("[A-Za-z0-9_-]+")
}

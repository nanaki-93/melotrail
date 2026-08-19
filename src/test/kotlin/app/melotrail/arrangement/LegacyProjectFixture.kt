package app.melotrail.arrangement

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/** Test fixture writer only. Production saves are intentionally schema-v4 only. */
fun writeLegacyProjectFixture(root: Path, project: Project) {
    require(project.version in 1..3) { "Legacy fixture writer only supports v1-v3" }
    val json = Json { prettyPrint = true; encodeDefaults = true }
    fun part(part: Part): JsonObject = buildJsonObject {
        put("id", part.id)
        put("role", part.role)
        if (project.version == 1) {
            put("file", part.file)
            part.analysis?.let { put("analysis", json.encodeToJsonElement(it)) }
        } else {
            put("sourceFile", part.file)
            put("midi", json.encodeToJsonElement(requireNotNull(part.midi)))
            part.analysis?.let { put("analysis", json.encodeToJsonElement(it)) }
            part.sourceAttestation?.let { put("sourceAttestation", json.encodeToJsonElement(it)) }
            part.importEvidence?.let { put("importEvidence", json.encodeToJsonElement(it)) }
        }
    }
    val document = buildJsonObject {
        put("version", project.version)
        put("name", project.name)
        put("parts", JsonArray(project.parts.map(::part)))
        put("structure", JsonArray(project.structure.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        if (project.version >= 2) put("renderFormat", json.encodeToJsonElement(requireNotNull(project.renderFormat)))
        if (project.version == 3) put("workflow", json.encodeToJsonElement(project.workflow))
    }
    Files.createDirectories(root)
    Files.writeString(root.resolve(ProjectStore.FILE_NAME), json.encodeToString(document))
}

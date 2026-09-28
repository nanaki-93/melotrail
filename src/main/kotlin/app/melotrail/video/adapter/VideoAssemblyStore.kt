package app.melotrail.video.adapter

import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.domain.CURRENT_ASSEMBLY_PLANNER
import app.melotrail.video.domain.CURRENT_ASSEMBLY_SCHEMA
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAssembly
import app.melotrail.video.domain.VideoAssemblyRecord
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoVersionedId
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Persists proposals, not executable readiness, take selections or job checkpoints.
 * All publication goes through the existing Video project lock and revision CAS. */
class VideoAssemblyStore(
    private val projects: VideoProjectStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun save(projectRoot: Path, expectedRevision: Long, assembly: VideoAssembly): VideoProject {
        val project = projects.open(projectRoot)
        if (project.revision != expectedRevision) throw VideoProjectConcurrencyException(
            "The video project changed from revision $expectedRevision to ${project.revision}.")
        val bytes = ASSEMBLY_JSON.encodeToString(assembly).toByteArray(UTF_8)
        require(bytes.size.toLong() in 1..MAX_DESCRIPTOR_BYTES) { "Assembly descriptor exceeds its supported byte bound" }
        validateSources(projectRoot, project, assembly)
        val artifact = VideoArtifact("assemblies/${assembly.id.id}/v${assembly.id.version}/assembly.json", sha256(bytes))
        return projects.appendAssembly(projectRoot, expectedRevision,
            record(assembly, artifact, Instant.now(clock).toString()), bytes)
    }

    fun load(projectRoot: Path, id: VideoVersionedId): VideoAssembly {
        val project = projects.open(projectRoot)
        val saved = project.assemblyVersions.singleOrNull { it.id == id }
            ?: throw InvalidVideoProjectException("Assembly version ${id.id} v${id.version} is not in this project")
        val path = projects.resolveArtifact(projectRoot, saved.artifact)
        if (Files.size(path) !in 1..MAX_DESCRIPTOR_BYTES) {
            throw InvalidVideoProjectException("Assembly descriptor has an unsupported byte size")
        }
        val assembly = decode(Files.readString(path, UTF_8))
        if (record(assembly, saved.artifact, saved.createdAt) != saved) {
            throw InvalidVideoProjectException("Assembly descriptor does not match its immutable project record")
        }
        validateSources(projectRoot, project, assembly)
        return assembly
    }

    private fun validateSources(root: Path, project: VideoProject, assembly: VideoAssembly) {
        if (assembly.projectId != project.id) throw InvalidVideoProjectException("Assembly belongs to a different video project")
        val prepared = project.preparedSceneVersions.singleOrNull { it.id == assembly.preparedSceneId }
        if (prepared == null || prepared.artifact != assembly.preparedSceneArtifact) {
            throw InvalidVideoProjectException("Assembly prepared-scene identity or artifact pin does not match this project")
        }
        // Reuse decoded source/asset validation, rather than trusting matching filenames or IDs.
        val scene = VideoPreparedSceneStore(projects).load(root, prepared.id)
        val reference = scene.source.references.singleOrNull { it.id == assembly.finishedReferenceId }
        if (reference == null || reference.original.artifact != assembly.finishedReferenceArtifact) {
            throw InvalidVideoProjectException("Assembly finished-reference identity or original pin does not match its prepared scene")
        }
    }

    private fun record(assembly: VideoAssembly, artifact: VideoArtifact, createdAt: String) = VideoAssemblyRecord(
        assembly.id, artifact, assembly.preparedSceneId, assembly.preparedSceneArtifact,
        assembly.finishedReferenceId, assembly.finishedReferenceArtifact, assembly.provenanceFingerprint, createdAt,
    )

    private fun decode(document: String): VideoAssembly {
        val versions = try {
            val value = ASSEMBLY_JSON.parseToJsonElement(document).jsonObject
            value["schemaVersion"]?.jsonPrimitive?.intOrNull to value["plannerVersion"]?.jsonPrimitive?.intOrNull
        } catch (error: Exception) {
            throw InvalidVideoProjectException("Assembly descriptor is not valid JSON", error)
        }
        if (versions != (CURRENT_ASSEMBLY_SCHEMA to CURRENT_ASSEMBLY_PLANNER)) {
            throw UnsupportedVideoProjectException("Unsupported assembly schema/planner ${versions.first}/${versions.second}; create a new plan with the current planner.")
        }
        return try {
            ASSEMBLY_JSON.decodeFromString<VideoAssembly>(document)
        } catch (error: Exception) {
            throw InvalidVideoProjectException("Assembly descriptor is invalid", error)
        }
    }

    companion object {
        private const val MAX_DESCRIPTOR_BYTES = 1_048_576L
        private val ASSEMBLY_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

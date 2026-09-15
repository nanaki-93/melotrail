package app.melotrail.video.adapter

import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoAssetFiles
import app.melotrail.video.application.VideoProjectConcurrencyException
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoPreparedSceneRecord
import app.melotrail.video.domain.VideoProject
import app.melotrail.video.domain.VideoVersionedId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Prepared-scene persistence layered on the one guarded Video project store. */
class VideoPreparedSceneStore(
    private val projects: VideoProjectStore,
    private val images: VideoAssetFiles = VideoImageFiles(),
) {
    fun save(projectRoot: Path, expectedRevision: Long, scene: VideoPreparedScene): VideoProject {
        val project = projects.open(projectRoot)
        if (project.revision != expectedRevision) {
            throw VideoProjectConcurrencyException(
                "The video project changed from revision $expectedRevision to ${project.revision}.",
            )
        }
        validateAgainstProject(projectRoot, project, scene)
        val bytes = SCENE_JSON.encodeToString(scene).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_DESCRIPTOR_BYTES) { "Prepared-scene descriptor exceeds its supported byte bound" }
        val relativePath = "prepared-scenes/${scene.id.id}/v${scene.id.version}/scene.json"
        val record = VideoPreparedSceneRecord(
            id = scene.id,
            artifact = app.melotrail.video.domain.VideoArtifact(relativePath, sha256(bytes)),
            sourceLookId = scene.source.look?.id,
            sourceReferenceIds = scene.source.references.map { it.id },
            consumedArtifacts = scene.consumedArtifacts(),
            createdAt = scene.createdAt,
        )
        return projects.appendPreparedScene(projectRoot, expectedRevision, record, bytes)
    }

    fun load(projectRoot: Path, id: VideoVersionedId): VideoPreparedScene {
        val project = projects.open(projectRoot)
        val record = project.preparedSceneVersions.singleOrNull { it.id == id }
            ?: throw InvalidVideoProjectException("Prepared-scene version ${id.id} v${id.version} is not in this project")
        val descriptor = projects.resolveArtifact(projectRoot, record.artifact)
        val size = Files.size(descriptor)
        if (size <= 0L || size > MAX_DESCRIPTOR_BYTES) {
            throw InvalidVideoProjectException("Prepared-scene descriptor has an unsupported byte size")
        }
        val document = Files.readString(descriptor, StandardCharsets.UTF_8)
        val scene = decode(document)
        val expectedRecord = VideoPreparedSceneRecord(
            id = scene.id,
            artifact = record.artifact,
            sourceLookId = scene.source.look?.id,
            sourceReferenceIds = scene.source.references.map { it.id },
            consumedArtifacts = scene.consumedArtifacts(),
            createdAt = scene.createdAt,
        )
        if (expectedRecord != record) {
            throw InvalidVideoProjectException("Prepared-scene descriptor metadata does not match its immutable project record")
        }
        validateAgainstProject(projectRoot, project, scene)
        return scene
    }

    private fun validateAgainstProject(projectRoot: Path, project: VideoProject, scene: VideoPreparedScene) {
        scene.source.look?.let { pin ->
            val look = project.lookVersions.singleOrNull { it.id == pin.id }
                ?: throw InvalidVideoProjectException("Prepared-scene source look is missing from the project")
            if (look.artifact != pin.artifact) {
                throw InvalidVideoProjectException("Prepared-scene source look pin changed")
            }
            val sourceIds = scene.source.references.map { it.id }.toSet()
            if (!sourceIds.containsAll(look.referenceIds)) {
                throw InvalidVideoProjectException("Prepared-scene source pins omit a reference consumed by its look")
            }
        }
        scene.source.references.forEach { pin ->
            val record = project.referenceVersions.singleOrNull { it.id == pin.id }
                ?: throw InvalidVideoProjectException("Prepared-scene source reference is missing from the project")
            if (record.artifact != pin.descriptorArtifact) {
                throw InvalidVideoProjectException("Prepared-scene source reference descriptor pin changed")
            }
            val loaded = try {
                images.load(projectRoot, record)
            } catch (error: Exception) {
                throw InvalidVideoProjectException("Prepared-scene source reference could not be verified", error)
            }
            if (loaded.original != pin.original) {
                throw InvalidVideoProjectException("Prepared-scene source original pin changed")
            }
        }
        scene.layers.forEach { verifyImage(projectRoot, it.image, "layer '${it.id}'") }
        scene.poses.forEach { verifyImage(projectRoot, it.image, "pose '${it.id}'") }
        scene.masks.forEach { verifyImage(projectRoot, it.image, "mask '${it.id}'") }
        scene.dependencies.mapNotNull { it.artifact }.forEach { projects.resolveArtifact(projectRoot, it) }
    }

    private fun verifyImage(projectRoot: Path, expected: VideoAssetImage, label: String) {
        val path = projects.resolveArtifact(projectRoot, expected.artifact)
        val inspected = try {
            images.inspect(path)
        } catch (error: Exception) {
            throw InvalidVideoProjectException("Prepared-scene $label image could not be decoded and verified", error)
        }
        if (Files.size(path) != expected.encodedBytes || inspected.sha256 != expected.artifact.sha256 ||
            inspected.format != expected.format || inspected.width != expected.width || inspected.height != expected.height ||
            inspected.hasAlphaChannel != expected.hasAlphaChannel ||
            inspected.hasTransparentPixels != expected.hasTransparentPixels
        ) {
            throw InvalidVideoProjectException("Prepared-scene $label decoded image facts changed")
        }
    }

    private fun decode(document: String): VideoPreparedScene {
        val version = try {
            SCENE_JSON.parseToJsonElement(document).jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull
        } catch (error: Exception) {
            throw InvalidVideoProjectException("Prepared-scene descriptor is not valid JSON", error)
        }
        if (version != VideoPreparedScene.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedVideoProjectException("Unsupported prepared-scene descriptor schema '${version ?: "missing"}'.")
        }
        return try {
            SCENE_JSON.decodeFromString<VideoPreparedScene>(document)
        } catch (error: Exception) {
            throw InvalidVideoProjectException("Prepared-scene descriptor is invalid", error)
        }
    }

    companion object {
        private const val MAX_DESCRIPTOR_BYTES = 1_048_576L
        private val SCENE_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    }
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }

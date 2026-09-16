package app.melotrail.video

import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.ImportVideoAsset
import app.melotrail.video.application.InvalidVideoProjectException
import app.melotrail.video.application.UnsupportedVideoProjectException
import app.melotrail.video.application.VideoAssetImport
import app.melotrail.video.application.VideoAssetImportResult
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoComponentReviewStatus
import app.melotrail.video.domain.VideoCoordinateSpace
import app.melotrail.video.domain.VideoDepthRelation
import app.melotrail.video.domain.VideoEffectAnchor
import app.melotrail.video.domain.VideoLayerKind
import app.melotrail.video.domain.VideoLayerTransform
import app.melotrail.video.domain.VideoMeasuredAlpha
import app.melotrail.video.domain.VideoLookRecord
import app.melotrail.video.domain.VideoMotionCapability
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoMotionTargetType
import app.melotrail.video.domain.VideoMotionUnit
import app.melotrail.video.domain.VideoOcclusionRelation
import app.melotrail.video.domain.VideoPlacedPoint
import app.melotrail.video.domain.VideoPoint
import app.melotrail.video.domain.VideoPreparedDependencyPin
import app.melotrail.video.domain.VideoPreparedLayer
import app.melotrail.video.domain.VideoPreparedLookPin
import app.melotrail.video.domain.VideoPreparedMask
import app.melotrail.video.domain.VideoPreparedPose
import app.melotrail.video.domain.VideoPreparedReferencePin
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoPreparedSceneSource
import app.melotrail.video.domain.VideoRect
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoSceneryCoverage
import app.melotrail.video.domain.VideoSubjectLandmark
import app.melotrail.video.domain.VideoSubtleMotionReusePolicy
import app.melotrail.video.domain.VideoVersionedId
import app.melotrail.video.domain.VideoWholeFootageReusePolicy
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoPreparedSceneStoreTest {
    @TempDir lateinit var root: Path

    @Test
    fun `two structurally different scenes reopen with exact source pins geometry and capabilities`() {
        val fixture = fixture()
        val subjectImage = fixture.imagePin("prepared-assets/subject.png", 100, 100, 1)
        val poseImage = fixture.imagePin("prepared-assets/pose.png", 100, 100, 2)
        val first = subjectScene(fixture, subjectImage, poseImage)
        val afterFirst = fixture.scenes.save(fixture.projectRoot, fixture.revision, first)

        val sceneryImage = fixture.imagePin("prepared-assets/scenery.png", 200, 100, 3)
        val foregroundImage = fixture.imagePin("prepared-assets/foreground.png", 200, 100, 4)
        val maskImage = fixture.imagePin("prepared-assets/occlusion-mask.png", 200, 100, 5, transparent = true)
        val second = sceneryScene(fixture, sceneryImage, foregroundImage, maskImage)
        fixture.scenes.save(fixture.projectRoot, afterFirst.revision, second)

        val reopened = VideoPreparedSceneStore(projectStore(), VideoImageFiles())
        assertEquals(first, reopened.load(fixture.projectRoot, first.id))
        assertEquals(second, reopened.load(fixture.projectRoot, second.id))
        assertTrue(first.poses.all { it.reviewStatus == VideoComponentReviewStatus.UNREVIEWED })
        assertTrue(first.effectAnchors.all { it.reviewStatus == VideoComponentReviewStatus.UNREVIEWED })
        assertTrue(second.occlusionRelations.all { it.reviewStatus == VideoComponentReviewStatus.UNREVIEWED })
        assertEquals(VideoMotionControl.EFFECT_RATE, first.motionCapabilities.single { it.targetType == VideoMotionTargetType.EFFECT_ANCHOR }.control)
        assertEquals(VideoMotionControl.TRANSLATE_X, second.motionCapabilities.single().control)
        assertEquals(VideoSubtleMotionReusePolicy.ALLOWED, first.reusePolicy.subtleMotionPatterns)
        assertEquals(VideoWholeFootageReusePolicy.FORBIDDEN, second.reusePolicy.wholeFootage)

        val document = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(document)
        assertFailsWith<IllegalArgumentException> {
            reopened.save(
                fixture.projectRoot,
                expectedRevision = afterFirst.revision + 1L,
                first.copy(createdAt = "2026-09-15T00:09:00Z"),
            )
        }
        assertContentEquals(before, Files.readAllBytes(document))
    }

    @Test
    fun `changed missing and unsupported scene assets fail without rewriting the project record`() {
        val fixture = fixture()
        val subjectPath = "prepared-assets/subject.png"
        val posePath = "prepared-assets/pose.png"
        val scene = subjectScene(
            fixture,
            fixture.imagePin(subjectPath, 100, 100, 6),
            fixture.imagePin(posePath, 100, 100, 7),
        )
        fixture.scenes.save(fixture.projectRoot, fixture.revision, scene)
        val projectDocument = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(projectDocument)
        val subjectFile = fixture.projectRoot.resolve(subjectPath)
        val subjectBytes = Files.readAllBytes(subjectFile)

        Files.writeString(subjectFile, "changed")
        assertFailsWith<InvalidVideoProjectException> { fixture.scenes.load(fixture.projectRoot, scene.id) }
        assertContentEquals(before, Files.readAllBytes(projectDocument))
        Files.write(subjectFile, subjectBytes)

        Files.delete(fixture.projectRoot.resolve(posePath))
        assertFailsWith<InvalidVideoProjectException> { fixture.scenes.load(fixture.projectRoot, scene.id) }
        assertContentEquals(before, Files.readAllBytes(projectDocument))
        Files.write(fixture.projectRoot.resolve(posePath), fixture.poseBytes)

        val descriptor = fixture.projectRoot.resolve("prepared-scenes/${scene.id.id}/v${scene.id.version}/scene.json")
        val oldDigest = sha256(descriptor)
        val unsupported = Files.readString(descriptor).replaceFirst("\"schemaVersion\": 2", "\"schemaVersion\": 99")
        assertTrue(unsupported.contains("\"schemaVersion\": 99"))
        Files.writeString(descriptor, unsupported)
        val newDigest = sha256(descriptor)
        val currentDocument = Files.readString(projectDocument)
        Files.writeString(projectDocument, currentDocument.replace(oldDigest, newDigest))
        val unsupportedProjectBytes = Files.readAllBytes(projectDocument)

        assertFailsWith<UnsupportedVideoProjectException> { fixture.scenes.load(fixture.projectRoot, scene.id) }
        assertContentEquals(unsupportedProjectBytes, Files.readAllBytes(projectDocument))
    }

    @Test
    fun `invalid anchors geometry and unsupported control shapes are rejected before persistence`() {
        val fixture = fixture()
        val subject = fixture.imagePin("prepared-assets/subject.png", 100, 100, 8)
        val pose = fixture.imagePin("prepared-assets/pose.png", 100, 100, 9)
        val valid = subjectScene(fixture, subject, pose)
        val document = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(document)

        assertFailsWith<IllegalArgumentException> {
            valid.copy(effectAnchors = listOf(VideoEffectAnchor("effect-source", "subject", position = VideoPlacedPoint("canvas", VideoPoint(150.0, 20.0)))))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(motionCapabilities = listOf(
                VideoMotionCapability(
                    "invalid-opacity",
                    VideoMotionTargetType.LAYER,
                    "subject",
                    VideoMotionControl.OPACITY,
                    VideoMotionUnit.DEGREES,
                    0.0,
                    2.0,
                    1.0,
                ),
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                layers = listOf(
                    valid.layers.single().copy(
                        transform = VideoLayerTransform(3.0, 0.0, 1.0, 1.0),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                layers = listOf(
                    valid.layers.single().copy(
                        alpha = VideoMeasuredAlpha(9_999L, 0L, 0L),
                    ),
                ),
            )
        }
        assertContentEquals(before, Files.readAllBytes(document))
    }

    @Test
    fun `rotated placement accepts boundary rounding and rejects corners outside its coordinate space`() {
        val fixture = fixture()
        val subject = fixture.imagePin("prepared-assets/subject.png", 100, 100, 8)
        val pose = fixture.imagePin("prepared-assets/pose.png", 100, 100, 9)
        val base = subjectScene(fixture, subject, pose)
        val pivot = VideoPlacedPoint("canvas", VideoPoint(50.0, 50.0))
        val rotated = base.layers.single().copy(
            transform = VideoLayerTransform(0.0, 0.0, 1.0, 1.0, 90.0),
            pivot = pivot,
        )
        val valid = base.copy(
            layers = listOf(rotated),
            poses = emptyList(),
            motionCapabilities = base.motionCapabilities.filter { it.targetType != VideoMotionTargetType.POSE },
        )
        fixture.scenes.save(fixture.projectRoot, fixture.revision, valid)
        assertEquals(valid, fixture.scenes.load(fixture.projectRoot, valid.id))
        val document = fixture.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val before = Files.readAllBytes(document)

        val error = assertFailsWith<IllegalArgumentException> {
            valid.copy(layers = listOf(rotated.copy(transform = rotated.transform!!.copy(rotationDegrees = 45.0))))
        }
        assertTrue(error.message!!.contains("rotated geometry exceeds coordinate space"))
        assertFailsWith<IllegalArgumentException> {
            valid.copy(layers = listOf(rotated.copy(pivot = null)))
        }
        assertContentEquals(before, Files.readAllBytes(document))
    }

    private fun subjectScene(fixture: Fixture, subject: VideoAssetImage, pose: VideoAssetImage) = VideoPreparedScene(
        id = VideoVersionedId("calm-subject", 1),
        source = fixture.source(withLook = true),
        coordinateSpaces = listOf(VideoCoordinateSpace("canvas", 100, 100)),
        layers = listOf(VideoPreparedLayer("subject", VideoLayerKind.SUBJECT, subject, VideoRect("canvas", 0.0, 0.0, 100.0, 100.0))),
        poses = listOf(VideoPreparedPose("blink", "subject", pose, VideoRect("canvas", 0.0, 0.0, 100.0, 100.0))),
        subjectLandmarks = listOf(VideoSubjectLandmark("forehead", "subject", VideoPlacedPoint("canvas", VideoPoint(50.0, 25.0)))),
        effectAnchors = listOf(VideoEffectAnchor("effect-source", "subject", landmarkId = "forehead")),
        motionCapabilities = listOf(
            VideoMotionCapability("blink-blend", VideoMotionTargetType.POSE, "blink", VideoMotionControl.POSE_BLEND, VideoMotionUnit.RATIO, 0.0, 1.0, 0.0),
            VideoMotionCapability("effect-rate", VideoMotionTargetType.EFFECT_ANCHOR, "effect-source", VideoMotionControl.EFFECT_RATE, VideoMotionUnit.PER_SECOND, 0.0, 3.0, 0.4),
        ),
        dependencies = listOf(dependency("subject-preparation", "1.0")),
        createdAt = "2026-09-15T00:03:00Z",
    )

    private fun sceneryScene(
        fixture: Fixture,
        scenery: VideoAssetImage,
        foreground: VideoAssetImage,
        mask: VideoAssetImage,
    ) = VideoPreparedScene(
        id = VideoVersionedId("moving-landscape", 1),
        source = fixture.source(withLook = false),
        coordinateSpaces = listOf(VideoCoordinateSpace("wide-canvas", 200, 100)),
        layers = listOf(
            VideoPreparedLayer("landscape", VideoLayerKind.SCENERY, scenery, VideoRect("wide-canvas", 0.0, 0.0, 200.0, 100.0)),
            VideoPreparedLayer("foreground", VideoLayerKind.FOREGROUND, foreground, VideoRect("wide-canvas", 0.0, 0.0, 200.0, 100.0)),
        ),
        masks = listOf(VideoPreparedMask("foreground-mask", mask, VideoRect("wide-canvas", 0.0, 0.0, 200.0, 100.0), listOf("foreground", "landscape"))),
        depthRelations = listOf(VideoDepthRelation("foreground", "landscape")),
        occlusionRelations = listOf(VideoOcclusionRelation("foreground", "landscape", "foreground-mask")),
        sceneryCoverage = listOf(VideoSceneryCoverage("landscape-span", "landscape", VideoRect("wide-canvas", 0.0, 0.0, 200.0, 100.0))),
        motionCapabilities = listOf(
            VideoMotionCapability("landscape-travel", VideoMotionTargetType.SCENERY_COVERAGE, "landscape-span", VideoMotionControl.TRANSLATE_X, VideoMotionUnit.PIXELS, -200.0, 200.0, 0.0),
        ),
        dependencies = listOf(dependency("scenery-preparation", "2.1")),
        createdAt = "2026-09-15T00:04:00Z",
    )

    private fun fixture(): Fixture {
        val projectRoot = root.resolve("video-project")
        val projectStore = projectStore()
        val lifecycle = VideoProjectLifecycle(projectStore, CLOCK, idFactory = { "video-project" })
        val created = (lifecycle.create(CreateVideoProject(projectRoot, "Prepared scenes", "video-project")) as
            VideoProjectLifecycleResult.Opened).session
        val source = imageFile(root.resolve("source.png"), 40, 40, 0)
        val imported = VideoAssetImport(lifecycle, VideoImageFiles(), CLOCK, idFactory = { "reference" })
            .import(created, ImportVideoAsset(source, VideoReferenceRole.SUBJECT)) as VideoAssetImportResult.Imported
        val lookArtifact = artifact(projectRoot, "looks/reference-look.png", Files.readAllBytes(source))
        val look = VideoLookRecord(VideoVersionedId("look", 1), lookArtifact, listOf(imported.asset.id), "2026-09-15T00:02:00Z")
        val project = projectStore.save(
            projectRoot,
            imported.session.project.revision,
            imported.session.project.copy(lookVersions = listOf(look), revision = imported.session.project.revision + 1L),
        )
        return Fixture(
            projectRoot,
            project.revision,
            VideoPreparedSceneStore(projectStore, VideoImageFiles()),
            project.referenceVersions.single(),
            imported.asset.original,
            look,
        )
    }

    private fun projectStore() = VideoProjectStore(listOf(root.resolve("midi-projects"), root.resolve("midi-exports")))

    private inner class Fixture(
        val projectRoot: Path,
        val revision: Long,
        val scenes: VideoPreparedSceneStore,
        val reference: app.melotrail.video.domain.VideoReferenceRecord,
        val original: VideoAssetImage,
        val look: VideoLookRecord,
    ) {
        lateinit var poseBytes: ByteArray

        fun source(withLook: Boolean) = VideoPreparedSceneSource(
            look = if (withLook) VideoPreparedLookPin(look.id, look.artifact) else null,
            references = listOf(VideoPreparedReferencePin(reference.id, reference.artifact, original)),
        )

        fun imagePin(relativePath: String, width: Int, height: Int, seed: Int, transparent: Boolean = false): VideoAssetImage {
            val path = imageFile(projectRoot.resolve(relativePath), width, height, seed, transparent)
            val inspected = VideoImageFiles().inspect(path)
            if (relativePath.endsWith("pose.png")) poseBytes = Files.readAllBytes(path)
            return VideoAssetImage(
                artifact = app.melotrail.video.domain.VideoArtifact(relativePath, inspected.sha256),
                format = inspected.format,
                mediaType = inspected.format.mediaType,
                width = inspected.width,
                height = inspected.height,
                encodedBytes = Files.size(path),
                hasAlphaChannel = inspected.hasAlphaChannel,
                hasTransparentPixels = inspected.hasTransparentPixels,
            )
        }
    }

    private fun imageFile(path: Path, width: Int, height: Int, seed: Int, transparent: Boolean = false): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, if (transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(30 + seed * 7, 60 + seed * 5, 90 + seed * 3)
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        if (transparent) image.setRGB(0, 0, 0x00000000)
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun artifact(projectRoot: Path, relativePath: String, bytes: ByteArray): app.melotrail.video.domain.VideoArtifact {
        val path = projectRoot.resolve(relativePath)
        Files.createDirectories(requireNotNull(path.parent))
        Files.write(path, bytes)
        return app.melotrail.video.domain.VideoArtifact(relativePath, sha256(path))
    }

    private fun dependency(id: String, version: String) = VideoPreparedDependencyPin(id, version, sha256("$id-$version".toByteArray()))

    private fun sha256(path: Path): String = sha256(Files.readAllBytes(path))

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-15T00:01:00Z"), ZoneOffset.UTC)
    }
}

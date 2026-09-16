package app.melotrail.video

import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoPreparedSceneImport
import app.melotrail.video.adapter.VideoPreparedSceneImportResult
import app.melotrail.video.adapter.VideoPreparedSceneStore
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.ImportVideoAsset
import app.melotrail.video.application.PrepareVideoAnimationAssets
import app.melotrail.video.application.VideoAssetImport
import app.melotrail.video.application.VideoAssetImportResult
import app.melotrail.video.application.VideoMaskAnimationAsset
import app.melotrail.video.application.VideoPlacedAnimationAsset
import app.melotrail.video.application.VideoPoseAnimationAsset
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.domain.VideoDepthRelation
import app.melotrail.video.domain.VideoEffectAnchor
import app.melotrail.video.domain.VideoMaskPurpose
import app.melotrail.video.domain.VideoMotionControl
import app.melotrail.video.domain.VideoOcclusionRelation
import app.melotrail.video.domain.VideoPlacedPoint
import app.melotrail.video.domain.VideoPoint
import app.melotrail.video.domain.VideoPreparedScene
import app.melotrail.video.domain.VideoRect
import app.melotrail.video.domain.VideoReferenceRole
import app.melotrail.video.domain.VideoSubjectLandmark
import app.melotrail.video.domain.VideoVersionedId
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

/**
 * Emits the real V18a importer contract consumed by the external V19a compositor.
 * The focused Kotlin selector intentionally performs no Node launch or rendering.
 */
class VideoMotionDescriptorFixtureTest {
    @Test
    fun `emit deterministic production importer bundles for controlled motion pixel comparisons`() {
        val output = Path.of(System.getProperty("user.dir"), "build", "video-motion-fixtures").toAbsolutePath().normalize()
        require(output.endsWith(Path.of("build", "video-motion-fixtures"))) { "Fixture output must remain under owned build/video-motion-fixtures" }
        deleteOwnedOutput(output)
        Files.createDirectories(output)

        val specs = listOf(
            FixtureSpec(
                name = "unit-scale",
                width = 100,
                height = 80,
                subjectBounds = PixelRect(20, 20, 30, 40),
                subjectSourceWidth = 30,
                subjectSourceHeight = 40,
                foregroundBounds = PixelRect(29, 20, 8, 40),
                foregroundSourceWidth = 8,
                foregroundSourceHeight = 40,
                headBounds = PixelRect(20, 20, 30, 16),
                headSourceWidth = 30,
                headSourceHeight = 16,
                anchor = PixelPoint(33, 30),
            ),
            FixtureSpec(
                name = "nonunit-scale",
                width = 140,
                height = 110,
                subjectBounds = PixelRect(64, 42, 46, 54),
                subjectSourceWidth = 23,
                subjectSourceHeight = 27,
                foregroundBounds = PixelRect(78, 42, 8, 54),
                foregroundSourceWidth = 4,
                foregroundSourceHeight = 27,
                headBounds = PixelRect(64, 42, 46, 20),
                headSourceWidth = 23,
                headSourceHeight = 10,
                anchor = PixelPoint(82, 54),
            ),
            FixtureSpec(
                name = "effect-only-static",
                width = 100,
                height = 80,
                subjectBounds = PixelRect(20, 20, 30, 40),
                subjectSourceWidth = 30,
                subjectSourceHeight = 40,
                foregroundBounds = PixelRect(42, 15, 8, 50),
                foregroundSourceWidth = 8,
                foregroundSourceHeight = 50,
                headBounds = PixelRect(20, 20, 30, 16),
                headSourceWidth = 30,
                headSourceHeight = 16,
                anchor = PixelPoint(46, 42),
                effectOnly = true,
            ),
            FixtureSpec(
                name = "opaque-head-black-alpha",
                width = 140,
                height = 110,
                subjectBounds = PixelRect(64, 42, 46, 54),
                subjectSourceWidth = 23,
                subjectSourceHeight = 27,
                foregroundBounds = PixelRect(78, 42, 8, 54),
                foregroundSourceWidth = 4,
                foregroundSourceHeight = 27,
                headBounds = PixelRect(64, 42, 46, 20),
                headSourceWidth = 23,
                headSourceHeight = 10,
                anchor = PixelPoint(82, 54),
                opaqueGrayscaleHeadMask = true,
                blackAlphaOcclusionMask = true,
            ),
        )
        specs.forEach { emitFixture(output.resolve(it.name), it) }
        Files.writeString(
            output.resolve("fixture-set.json"),
            JSON.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("schema", "melotrail-motion-production-fixtures-v1")
                    put("fixtures", buildJsonArray { specs.forEach { add(JsonPrimitive(it.name)) } })
                    put("selectorEnvironment", "MELOTRAIL_MOTION_FIXTURE_ROOT")
                },
            ) + "\n",
        )
        assertEquals(specs.map(FixtureSpec::name).sorted(), Files.list(output).use { children ->
            children.filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }
                .map { it.fileName.toString() }.sorted().toList()
        })
    }

    private fun emitFixture(fixtureRoot: Path, spec: FixtureSpec) {
        val inputs = fixtureRoot.resolve("inputs")
        val projectRoot = fixtureRoot.resolve("project")
        Files.createDirectories(inputs)

        val subjectPixels = sourceInterior(spec.subjectBounds, spec.subjectSourceWidth, spec.subjectSourceHeight)
        val foregroundPixels = sourceInterior(spec.foregroundBounds, spec.foregroundSourceWidth, spec.foregroundSourceHeight)
        val headPixels = sourceInterior(
            spec.headBounds,
            spec.headSourceWidth,
            spec.headSourceHeight,
            insetSourcePixels = if (spec.opaqueGrayscaleHeadMask) 3 else 1,
        )
        val occlusionPixels = sourceInterior(spec.foregroundBounds, spec.foregroundSourceWidth, spec.foregroundSourceHeight)
        val finishedPath = finishedScene(inputs.resolve("finished-scene.png"), spec, subjectPixels, foregroundPixels)
        val cleanPath = rgbImage(inputs.resolve("clean-background.png"), spec.width, spec.height, BACKGROUND)
        val subjectPath = cutout(inputs.resolve("subject.png"), spec.subjectSourceWidth, spec.subjectSourceHeight, SUBJECT)
        val posePath = cutout(inputs.resolve("blink-pose.png"), spec.subjectSourceWidth, spec.subjectSourceHeight, POSE)
        val foregroundPath = cutout(inputs.resolve("foreground.png"), spec.foregroundSourceWidth, spec.foregroundSourceHeight, FOREGROUND)
        val headMaskPath = if (spec.opaqueGrayscaleHeadMask) {
            opaqueGrayscaleMask(inputs.resolve("head-mask.png"), spec.headSourceWidth, spec.headSourceHeight, inset = 3)
        } else {
            cutout(inputs.resolve("head-mask.png"), spec.headSourceWidth, spec.headSourceHeight, Color.WHITE)
        }
        val occlusionMaskColor = if (spec.blackAlphaOcclusionMask) Color.BLACK else Color.WHITE
        val occlusionMaskPath = cutout(
            inputs.resolve("occlusion-mask.png"),
            spec.foregroundSourceWidth,
            spec.foregroundSourceHeight,
            occlusionMaskColor,
        )

        val store = VideoProjectStore(listOf(fixtureRoot.resolve("protected-midi"), fixtureRoot.resolve("protected-exports")))
        val lifecycle = VideoProjectLifecycle(store, CLOCK, idFactory = { "motion-fixture-${spec.name}" })
        var session = assertIs<VideoProjectLifecycleResult.Opened>(
            lifecycle.create(CreateVideoProject(projectRoot, "V19a ${spec.name}", "motion-fixture-${spec.name}", "fixture-v1")),
        ).session
        val imageFiles = VideoImageFiles()
        fun imported(id: String, source: Path, role: VideoReferenceRole): VideoAssetImportResult.Imported {
            val result = VideoAssetImport(lifecycle, imageFiles, CLOCK, idFactory = { "${spec.name}-$id" })
                .import(session, ImportVideoAsset(source, role))
            return assertIs<VideoAssetImportResult.Imported>(result).also { session = it.session }
        }

        val finished = imported("finished", finishedPath, VideoReferenceRole.COMPLETE_SCENE)
        val subject = imported("subject", subjectPath, VideoReferenceRole.SUBJECT)
        val clean = imported("clean", cleanPath, VideoReferenceRole.ENVIRONMENT)
        val foreground = imported("foreground", foregroundPath, VideoReferenceRole.ENVIRONMENT)
        val pose = imported("pose", posePath, VideoReferenceRole.SUBJECT)
        val headMask = imported("head-mask", headMaskPath, VideoReferenceRole.ENVIRONMENT)
        val occlusionMask = imported("occlusion-mask", occlusionMaskPath, VideoReferenceRole.ENVIRONMENT)
        val headInspection = imageFiles.inspectOriginal(
            projectRoot,
            session.project.referenceVersions.single { it.id == headMask.asset.id },
        )
        val occlusionInspection = imageFiles.inspectOriginal(
            projectRoot,
            session.project.referenceVersions.single { it.id == occlusionMask.asset.id },
        )
        if (spec.opaqueGrayscaleHeadMask) {
            assertTrue(!headInspection.alpha.isUsableCutout)
            assertTrue(headInspection.hasVisibleContrast)
        } else {
            assertTrue(headInspection.alpha.isUsableCutout)
        }
        assertTrue(occlusionInspection.alpha.isUsableCutout)
        if (spec.blackAlphaOcclusionMask) assertTrue(!occlusionInspection.hasVisibleContrast)

        val sceneId = VideoVersionedId("controlled-${spec.name}", 1)
        val subjectBounds = spec.subjectBounds.videoRect()
        val subjectPivot = VideoPlacedPoint(SPACE, VideoPoint(
            spec.subjectBounds.x + spec.subjectBounds.width / 2.0,
            spec.subjectBounds.y + spec.headBounds.height * 0.75,
        ))
        val occludedId = if (spec.effectOnly) "finished-scene" else "subject"
        val request = PrepareVideoAnimationAssets(
            sceneId = sceneId,
            finishedSceneReferenceId = finished.asset.id,
            subjectLayers = listOf(VideoPlacedAnimationAsset("subject", subject.asset.id, subjectBounds, subjectPivot)),
            cleanBackground = VideoPlacedAnimationAsset("clean-background", clean.asset.id, PixelRect(0, 0, spec.width, spec.height).videoRect()),
            foregroundLayers = listOf(VideoPlacedAnimationAsset("foreground", foreground.asset.id, spec.foregroundBounds.videoRect())),
            poses = listOf(VideoPoseAnimationAsset(
                VideoPlacedAnimationAsset("blink-pose", pose.asset.id, subjectBounds, subjectPivot),
                "subject",
            )),
            masks = listOf(
                VideoMaskAnimationAsset(
                    VideoPlacedAnimationAsset("head-mask", headMask.asset.id, spec.headBounds.videoRect(), subjectPivot),
                    listOf("subject"),
                    VideoMaskPurpose.HEAD_REGION,
                ),
                VideoMaskAnimationAsset(
                    VideoPlacedAnimationAsset("foreground-occlusion", occlusionMask.asset.id, spec.foregroundBounds.videoRect()),
                    listOf("foreground", occludedId),
                    VideoMaskPurpose.OCCLUSION,
                ),
            ),
            subjectLandmarks = listOf(
                VideoSubjectLandmark("effect-point", "subject", VideoPlacedPoint(SPACE, spec.anchor.videoPoint())),
            ),
            effectAnchors = listOf(
                if (spec.effectOnly) {
                    VideoEffectAnchor("steam-source", "finished-scene", position = VideoPlacedPoint(SPACE, spec.anchor.videoPoint()))
                } else {
                    VideoEffectAnchor("steam-source", "subject", landmarkId = "effect-point")
                },
            ),
            depthRelations = listOf(VideoDepthRelation("foreground", occludedId)),
            occlusionRelations = listOf(VideoOcclusionRelation("foreground", occludedId, "foreground-occlusion")),
        )
        val scenes = VideoPreparedSceneStore(store, imageFiles)
        val importedScene = assertIs<VideoPreparedSceneImportResult.Saved>(
            VideoPreparedSceneImport(store, scenes, imageFiles, clock = CLOCK)
                .import(projectRoot, session.project.revision, request),
        )
        val scene = importedScene.scene
        assertEquals(scene, scenes.load(projectRoot, sceneId))
        assertCanonicalTransforms(
            scene,
            expectNonUnitScale = spec.subjectBounds.width != spec.subjectSourceWidth ||
                spec.subjectBounds.height != spec.subjectSourceHeight,
        )

        val sceneDocument = Files.readString(projectRoot.resolve("prepared-scenes/${sceneId.id}/v${sceneId.version}/scene.json"))
        val sceneJson = JSON.parseToJsonElement(sceneDocument)
        val controls = if (spec.effectOnly) {
            buildJsonArray { add(steamControl(scene)) }
        } else {
            buildJsonArray {
                add(control("blinking", "blink", capability(scene, VideoMotionControl.POSE_BLEND, "blink-pose"), "amount", 1.0))
                add(control("breathing", "breathing", capability(scene, VideoMotionControl.TRANSLATE_Y, "subject"), "amplitudePixels", 2.0))
                add(control("head", "headGesture", capability(scene, VideoMotionControl.ROTATE, "subject"), "amplitudeDegrees", 2.0))
                add(steamControl(scene))
            }
        }
        Files.writeString(
            fixtureRoot.resolve("request.json"),
            JSON.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("schema", "melotrail-controlled-motion-request-v1")
                put("preparedScene", sceneJson)
                put("seed", 73)
                put("fps", 30)
                put("canvas", buildJsonObject {
                    put("coordinateSpaceId", SPACE)
                    put("width", spec.width)
                    put("height", spec.height)
                })
                put("frameRange", buildJsonObject { put("startFrame", 90); put("frameCount", 300) })
                put("controls", controls)
            }) + "\n",
        )
        val componentBounds = linkedMapOf(
            "layer:finished-scene" to PixelRect(0, 0, spec.width, spec.height),
            "layer:subject" to subjectPixels,
            "layer:clean-background" to PixelRect(0, 0, spec.width, spec.height),
            "layer:foreground" to foregroundPixels,
            "pose:blink-pose" to subjectPixels,
            "mask:head-mask" to if (spec.opaqueGrayscaleHeadMask) spec.headBounds else headPixels,
            "mask:foreground-occlusion" to occlusionPixels,
        )
        Files.writeString(
            fixtureRoot.resolve("fixture-metadata.json"),
            JSON.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("schema", "melotrail-motion-production-fixture-metadata-v1")
                put("name", spec.name)
                put("projectRoot", "project")
                put("request", "request.json")
                put("effectOnly", spec.effectOnly)
                put("anchor", spec.anchor.json())
                put("subjectBounds", spec.subjectBounds.json())
                put("foregroundBounds", spec.foregroundBounds.json())
                put("componentPixelBounds", buildJsonObject { componentBounds.forEach { (id, bounds) -> put(id, bounds.json()) } })
                put("maskCoverageBounds", buildJsonObject {
                    put("head-mask", headPixels.json())
                    put("foreground-occlusion", occlusionPixels.json())
                })
                put("maskEncodings", buildJsonObject {
                    put("head-mask", if (spec.opaqueGrayscaleHeadMask) "opaque-grayscale" else "alpha-cutout")
                    put("foreground-occlusion", "alpha-cutout")
                })
                put("maskColors", buildJsonObject {
                    put("head-mask", colorJson(Color.WHITE))
                    put("foreground-occlusion", colorJson(occlusionMaskColor))
                })
                put("colors", buildJsonObject {
                    put("background", colorJson(BACKGROUND)); put("subject", colorJson(SUBJECT))
                    put("pose", colorJson(POSE)); put("foreground", colorJson(FOREGROUND))
                })
            }) + "\n",
        )
    }

    private fun capability(scene: VideoPreparedScene, control: VideoMotionControl, targetId: String): String =
        scene.motionCapabilities.single { it.control == control && it.targetId == targetId }.id

    private fun control(id: String, kind: String, capabilityId: String, amountName: String, amount: Double): JsonObject =
        buildJsonObject {
            put("id", id); put("kind", kind); put("capabilityId", capabilityId); put(amountName, amount)
        }

    private fun steamControl(scene: VideoPreparedScene): JsonObject = buildJsonObject {
        put("id", "steam"); put("kind", "steam")
        put("capabilityId", capability(scene, VideoMotionControl.EFFECT_RATE, "steam-source"))
        put("ratePerSecond", 3.0); put("risePixelsPerSecond", 18.0)
    }

    private fun assertCanonicalTransforms(scene: VideoPreparedScene, expectNonUnitScale: Boolean) {
        val components = scene.layers.map { Triple(it.image, it.bounds, it.transform) } +
            scene.poses.map { Triple(it.image, it.bounds, it.transform) } +
            scene.masks.map { Triple(it.image, it.bounds, it.transform) }
        components.forEach { (image, bounds, transform) ->
            requireNotNull(transform)
            assertEquals(bounds.x, transform.translateX)
            assertEquals(bounds.y, transform.translateY)
            assertEquals(bounds.width / image.width, transform.scaleX)
            assertEquals(bounds.height / image.height, transform.scaleY)
        }
        if (expectNonUnitScale) {
            assertTrue(components.any { (_, _, transform) -> requireNotNull(transform).scaleX != 1.0 })
        } else {
            assertTrue(components.all { (_, _, transform) ->
                val placed = requireNotNull(transform)
                placed.scaleX == 1.0 && placed.scaleY == 1.0
            })
        }
    }

    private fun finishedScene(path: Path, spec: FixtureSpec, subject: PixelRect, foreground: PixelRect): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(spec.width, spec.height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = BACKGROUND; graphics.fillRect(0, 0, spec.width, spec.height)
            graphics.color = SUBJECT; graphics.fillRect(subject.x, subject.y, subject.width, subject.height)
            graphics.color = FOREGROUND; graphics.fillRect(foreground.x, foreground.y, foreground.width, foreground.height)
        } finally { graphics.dispose() }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun rgbImage(path: Path, width: Int, height: Int, color: Color): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { graphics.color = color; graphics.fillRect(0, 0, width, height) } finally { graphics.dispose() }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun cutout(path: Path, width: Int, height: Int, color: Color): Path {
        Files.createDirectories(requireNotNull(path.parent))
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.composite = AlphaComposite.Clear; graphics.fillRect(0, 0, width, height)
            graphics.composite = AlphaComposite.Src; graphics.color = color
            graphics.fillRect(1, 1, width - 2, height - 2)
        } finally { graphics.dispose() }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun opaqueGrayscaleMask(path: Path, width: Int, height: Int, inset: Int): Path {
        Files.createDirectories(requireNotNull(path.parent))
        require(inset > 0 && width > inset * 2 && height > inset * 2)
        val image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color.BLACK; graphics.fillRect(0, 0, width, height)
            graphics.color = Color.WHITE; graphics.fillRect(inset, inset, width - inset * 2, height - inset * 2)
        } finally { graphics.dispose() }
        assertTrue(ImageIO.write(image, "png", path.toFile()))
        return path
    }

    private fun sourceInterior(
        bounds: PixelRect,
        sourceWidth: Int,
        sourceHeight: Int,
        insetSourcePixels: Int = 1,
    ): PixelRect {
        val scaleX = bounds.width / sourceWidth
        val scaleY = bounds.height / sourceHeight
        require(scaleX * sourceWidth == bounds.width && scaleY * sourceHeight == bounds.height && scaleX > 0 && scaleY > 0)
        require(insetSourcePixels > 0 && sourceWidth > insetSourcePixels * 2 && sourceHeight > insetSourcePixels * 2)
        return PixelRect(
            bounds.x + scaleX * insetSourcePixels,
            bounds.y + scaleY * insetSourcePixels,
            bounds.width - scaleX * insetSourcePixels * 2,
            bounds.height - scaleY * insetSourcePixels * 2,
        )
    }

    private fun deleteOwnedOutput(output: Path) {
        if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) return
        require(!Files.isSymbolicLink(output)) { "Refusing to replace a linked fixture output" }
        Files.walk(output).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private fun PixelRect.videoRect() = VideoRect(SPACE, x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble())
    private fun PixelPoint.videoPoint() = VideoPoint(x.toDouble(), y.toDouble())
    private fun PixelRect.json(): JsonElement = buildJsonObject { put("x", x); put("y", y); put("width", width); put("height", height) }
    private fun PixelPoint.json(): JsonElement = buildJsonObject { put("x", x); put("y", y) }
    private fun colorJson(color: Color): JsonArray = buildJsonArray { add(color.red); add(color.green); add(color.blue); add(255) }

    private data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int)
    private data class PixelPoint(val x: Int, val y: Int)
    private data class FixtureSpec(
        val name: String,
        val width: Int,
        val height: Int,
        val subjectBounds: PixelRect,
        val subjectSourceWidth: Int,
        val subjectSourceHeight: Int,
        val foregroundBounds: PixelRect,
        val foregroundSourceWidth: Int,
        val foregroundSourceHeight: Int,
        val headBounds: PixelRect,
        val headSourceWidth: Int,
        val headSourceHeight: Int,
        val anchor: PixelPoint,
        val effectOnly: Boolean = false,
        val opaqueGrayscaleHeadMask: Boolean = false,
        val blackAlphaOcclusionMask: Boolean = false,
    )

    private companion object {
        const val SPACE = "scene"
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneOffset.UTC)
        val JSON = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }
        val BACKGROUND = Color(64, 80, 96)
        val SUBJECT = Color(197, 106, 112)
        val POSE = Color(125, 83, 111)
        val FOREGROUND = Color(233, 210, 140)
    }
}

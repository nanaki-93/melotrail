package app.melotrail.video

import app.melotrail.video.adapter.VideoAtomicWriteObserver
import app.melotrail.video.adapter.VideoImageFiles
import app.melotrail.video.adapter.VideoImageLimits
import app.melotrail.video.adapter.VideoProjectStore
import app.melotrail.video.application.CreateVideoProject
import app.melotrail.video.application.ImportVideoAsset
import app.melotrail.video.application.VideoAssetFileException
import app.melotrail.video.application.VideoAssetImport
import app.melotrail.video.application.VideoAssetImportResult
import app.melotrail.video.application.VideoAssetLibraryResult
import app.melotrail.video.application.VideoAssetProblemCode
import app.melotrail.video.application.VideoProjectLifecycle
import app.melotrail.video.application.VideoProjectLifecycleResult
import app.melotrail.video.domain.VideoAssetCreationProvenance
import app.melotrail.video.domain.VideoAssetIdentityReview
import app.melotrail.video.domain.VideoAssetRights
import app.melotrail.video.domain.VideoAssetUsageIntent
import app.melotrail.video.domain.VideoBriefReference
import app.melotrail.video.domain.VideoImageFormat
import app.melotrail.video.domain.VideoMeasuredAlpha
import app.melotrail.video.domain.VideoReferenceRole
import java.awt.Color
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.IndexColorModel
import java.awt.image.Raster
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.name
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VideoAssetImportTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `PNG and JPEG imports preserve raw bytes and use decoded format instead of extension`() {
        val harness = harness()
        val pngNamedJpeg = imageFile("reference.jpg", "png", 640, 360, transparent = true)
        val jpegNamedPng = imageFile("scene.png", "jpeg", 900, 600)
        val pngBefore = Files.readAllBytes(pngNamedJpeg)
        val jpegBefore = Files.readAllBytes(jpegNamedPng)

        val first = harness.imported(
            harness.importer.import(
                harness.session,
                ImportVideoAsset(
                    source = pngNamedJpeg,
                    role = VideoReferenceRole.CHARACTER,
                    sourceDescription = "User-selected character reference",
                    rights = VideoAssetRights(
                        creator = "Fixture creator",
                        ownershipStatement = "Owned test fixture",
                        permittedUses = listOf("Local video generation"),
                    ),
                ),
            ),
        )
        val second = harness.imported(
            harness.importer.import(
                first.session,
                ImportVideoAsset(source = jpegNamedPng, role = VideoReferenceRole.COMPLETE_SCENE),
            ),
        )

        assertEquals(VideoImageFormat.PNG, first.asset.original.format)
        assertTrue(first.asset.original.artifact.relativePath.endsWith("original.png"))
        assertEquals(VideoImageFormat.JPEG, second.asset.original.format)
        assertTrue(second.asset.original.artifact.relativePath.endsWith("original.jpg"))
        assertContentEquals(pngBefore, Files.readAllBytes(harness.projectRoot.resolve(first.asset.original.artifact.relativePath)))
        assertContentEquals(jpegBefore, Files.readAllBytes(harness.projectRoot.resolve(second.asset.original.artifact.relativePath)))
        assertContentEquals(pngBefore, Files.readAllBytes(pngNamedJpeg))
        assertContentEquals(jpegBefore, Files.readAllBytes(jpegNamedPng))
        assertTrue(first.asset.original.hasAlphaChannel)
        assertTrue(first.asset.original.hasTransparentPixels)
        assertFalse(second.asset.original.hasAlphaChannel)
        assertTrue(first.asset.thumbnail.width <= 512 && first.asset.thumbnail.height <= 512)
        assertTrue(second.asset.thumbnail.width <= 512 && second.asset.thumbnail.height <= 512)
        assertEquals(VideoImageFormat.PNG, first.asset.thumbnail.format)
        assertEquals(VideoAssetIdentityReview.UNREVIEWED, first.asset.identityReview)
        assertEquals("Fixture creator", first.asset.rights?.creator)
        assertEquals(pngNamedJpeg.toAbsolutePath().normalize().toString(), first.asset.source.originalLocation)
        assertNull(second.asset.rights)
    }

    @Test
    fun `every reference role and no role work independently without a required combination`() {
        val harness = harness()
        var session = harness.session
        val requestedRoles = listOf(null) + VideoReferenceRole.entries
        val importedRoles = requestedRoles.mapIndexed { index, role ->
            val source = imageFile("role-$index.png", "png", 32 + index, 24 + index, colorSeed = index)
            val result = harness.imported(harness.importer.import(session, ImportVideoAsset(source, role)))
            session = result.session
            result.asset.role
        }

        assertEquals(requestedRoles, importedRoles)
        assertEquals(requestedRoles.size, session.project.referenceVersions.size)
        assertEquals(requestedRoles.size, session.project.selectedReferenceIds.size)
        assertEquals(requestedRoles, harness.loaded(harness.importer.open(harness.projectRoot)).assets.map { it.role })
    }

    @Test
    fun `original thumbnail and descriptor hashes bind their exact stored bytes`() {
        val harness = harness()
        val source = imageFile("hash-pins.png", "png", 600, 300)
        val imported = harness.imported(harness.importer.import(harness.session, ImportVideoAsset(source)))
        val artifacts = listOf(
            imported.asset.original.artifact,
            imported.asset.thumbnail.artifact,
            imported.session.project.referenceVersions.single().artifact,
        )

        artifacts.forEach { artifact ->
            val bytes = Files.readAllBytes(harness.projectRoot.resolve(artifact.relativePath))
            val expected = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
            assertEquals(expected, artifact.sha256)
        }
        assertEquals(listOf(imported.asset), harness.loaded(harness.importer.open(harness.projectRoot)).assets)
    }

    @Test
    fun `source paths preserve symlink parent traversal semantics`() {
        val harness = harness()
        Files.createDirectories(root.resolve("actual/child"))
        Files.createSymbolicLink(root.resolve("shortcut"), root.resolve("actual/child"))
        val selectedSource = imageFile("actual/source.png", "png", 40, 20)
        val lexicalDecoy = imageFile("source.png", "png", 25, 35, colorSeed = 1)
        val actualBytes = Files.readAllBytes(selectedSource)
        val decoyBytes = Files.readAllBytes(lexicalDecoy)
        val selection = root.resolve("shortcut/../source.png")

        val imported = harness.imported(harness.importer.import(harness.session, ImportVideoAsset(selection)))

        assertEquals(40, imported.asset.original.width)
        assertEquals(20, imported.asset.original.height)
        assertContentEquals(actualBytes, Files.readAllBytes(harness.projectRoot.resolve(imported.asset.original.artifact.relativePath)))
        assertContentEquals(actualBytes, Files.readAllBytes(selectedSource))
        assertContentEquals(decoyBytes, Files.readAllBytes(lexicalDecoy))
        assertEquals(selection.toAbsolutePath().toString(), imported.asset.source.originalLocation)
    }

    @Test
    fun `indexed PNG transparency is recorded and preserved after reopen`() {
        val harness = harness()
        val palette = IndexColorModel(
            8, 2,
            byteArrayOf(20, 100), byteArrayOf(40, 120), byteArrayOf(60, 140.toByte()),
            byteArrayOf(0, 255.toByte()),
        )
        val image = BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED, palette)
        image.raster.setSample(1, 1, 0, 1)
        val source = root.resolve("indexed-alpha.png")
        assertTrue(ImageIO.write(image, "png", source.toFile()))
        val decoded = ImageIO.read(source.toFile())
        assertTrue(decoded.colorModel is IndexColorModel)
        assertNull(decoded.alphaRaster)

        val imported = harness.imported(harness.importer.import(harness.session, ImportVideoAsset(source)))

        assertTrue(imported.asset.original.hasAlphaChannel)
        assertTrue(imported.asset.original.hasTransparentPixels)
        assertTrue(imported.asset.thumbnail.hasTransparentPixels)
        val inspected = harness.imageFiles.inspectOriginal(harness.projectRoot, imported.session.project.referenceVersions.single())
        assertEquals(imported.asset, inspected.asset)
        assertEquals(VideoMeasuredAlpha(1, 0, 255), inspected.alpha)
        assertFalse(inspected.hasVisibleContrast) // The other palette color is entirely invisible.
        assertEquals(listOf(imported.asset), harness.loaded(harness.importer.open(harness.projectRoot)).assets)
    }

    @Test
    fun `sixteen bit PNG alpha distinguishes partial transparency from full opacity`() {
        val harness = harness()
        var session = harness.session
        val model = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB), intArrayOf(16, 16, 16, 16),
            true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT,
        )
        listOf(32_768, 65_534, 65_535).forEach { alpha ->
            val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, 2, 2, 4, null)
            for (y in 0 until 2) for (x in 0 until 2) {
                raster.setPixel(x, y, intArrayOf(20_000, 30_000, 40_000, alpha))
            }
            val image = BufferedImage(model, raster, false, null)
            val source = root.resolve("alpha-$alpha.png")
            assertTrue(ImageIO.write(image, "png", source.toFile()))
            assertEquals(16, ImageIO.read(source.toFile()).alphaRaster.sampleModel.getSampleSize(0))

            val imported = harness.imported(harness.importer.import(session, ImportVideoAsset(source)))

            assertTrue(imported.asset.original.hasAlphaChannel)
            assertEquals(alpha < 65_535, imported.asset.original.hasTransparentPixels)
            val inspected = harness.imageFiles.inspectOriginal(harness.projectRoot, imported.session.project.referenceVersions.last())
            assertEquals(imported.asset, inspected.asset)
            assertEquals(if (alpha == 65_535) VideoMeasuredAlpha(4, 0, 0) else VideoMeasuredAlpha(0, 4, 0), inspected.alpha)
            assertFalse(inspected.hasVisibleContrast)
            session = imported.session
        }
        assertEquals(3, harness.loaded(harness.importer.open(harness.projectRoot)).assets.size)
    }

    @Test
    fun `original inspection measures native alpha extremes and visible mask contrast without modifying assets`() {
        val harness = harness()
        val model = ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_sRGB), intArrayOf(16, 16, 16, 16),
            true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT,
        )
        val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT, 4, 1, 4, null)
        listOf(0, 1, 65_534, 65_535).forEachIndexed { x, alpha ->
            val color = if (x == 3) 65_535 else 0
            raster.setPixel(x, 0, intArrayOf(color, color, color, alpha))
        }
        val source = root.resolve("mixed-native-alpha.png")
        assertTrue(ImageIO.write(BufferedImage(model, raster, false, null), "png", source.toFile()))
        val imported = harness.imported(harness.importer.import(harness.session, ImportVideoAsset(source)))
        val record = imported.session.project.referenceVersions.single()
        val protectedFiles = listOf(source, harness.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)) +
            listOf(record.artifact, imported.asset.original.artifact, imported.asset.thumbnail.artifact)
                .map { harness.projectRoot.resolve(it.relativePath) }
        val before = protectedFiles.associateWith(Files::readAllBytes)

        val inspected = harness.imageFiles.inspectOriginal(harness.projectRoot, record)

        assertEquals(imported.asset, inspected.asset)
        assertEquals(VideoMeasuredAlpha(1, 2, 1), inspected.alpha)
        assertTrue(inspected.hasVisibleContrast)
        before.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(path)) }
    }

    @Test
    fun `original inspection enforces original decode bounds and exact descriptor and media pins`() {
        val harness = harness()
        val source = imageFile("inspection-bounds.png", "png", 20, 20)
        val imported = harness.imported(harness.importer.import(harness.session, ImportVideoAsset(source)))
        val record = imported.session.project.referenceVersions.single()
        val originals = listOf(record.artifact, imported.asset.original.artifact, imported.asset.thumbnail.artifact)
            .associate { harness.projectRoot.resolve(it.relativePath) to Files.readAllBytes(harness.projectRoot.resolve(it.relativePath)) }
        val projectBefore = Files.readAllBytes(harness.projectRoot.resolve(VideoProjectStore.PROJECT_FILE))
        val sourceBefore = Files.readAllBytes(source)
        listOf(VideoImageLimits(maxPixels = 399), VideoImageLimits(maxDimension = 19), VideoImageLimits(maxEncodedBytes = 1)).forEach { limits ->
            val failure = assertFailsWith<VideoAssetFileException> {
                VideoImageFiles(limits).inspectOriginal(harness.projectRoot, record)
            }
            assertEquals(VideoAssetProblemCode.ASSET_CHANGED, failure.code)
        }
        originals.forEach { (path, bytes) ->
            val changed = bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            Files.write(path, changed)
            val failure = assertFailsWith<VideoAssetFileException> {
                harness.imageFiles.inspectOriginal(harness.projectRoot, record)
            }
            assertEquals(VideoAssetProblemCode.ASSET_CHANGED, failure.code)
            assertTrue(failure.message.orEmpty().contains("SHA-256"))
            assertContentEquals(changed, Files.readAllBytes(path))
            Files.write(path, bytes)
        }
        assertEquals(imported.asset, harness.imageFiles.inspectOriginal(harness.projectRoot, record).asset)
        assertContentEquals(projectBefore, Files.readAllBytes(harness.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)))
        assertContentEquals(sourceBefore, Files.readAllBytes(source))
        originals.forEach { (path, bytes) -> assertContentEquals(bytes, Files.readAllBytes(path)) }
    }

    @Test
    fun `duplicate bytes reuse the exact identity without overwrite or metadata promotion`() {
        val harness = harness()
        val source = imageFile("duplicate.png", "png", 96, 64)
        val first = harness.imported(
            harness.importer.import(
                harness.session,
                ImportVideoAsset(
                    source,
                    role = VideoReferenceRole.STYLE,
                    usageIntent = VideoAssetUsageIntent.INSPIRATION_ONLY,
                ),
            ),
        )
        val descriptor = first.session.project.referenceVersions.single().artifact
        val descriptorPath = harness.projectRoot.resolve(descriptor.relativePath)
        val descriptorBefore = Files.readAllBytes(descriptorPath)
        val originalBefore = Files.readAllBytes(harness.projectRoot.resolve(first.asset.original.artifact.relativePath))

        val duplicate = harness.reused(
            harness.importer.import(
                first.session,
                ImportVideoAsset(
                    source,
                    role = VideoReferenceRole.OUTFIT,
                    rights = VideoAssetRights(license = "A later claim must not overwrite the asset"),
                ),
            ),
        )

        assertEquals(first.asset.id, duplicate.asset.id)
        assertEquals(VideoReferenceRole.STYLE, duplicate.asset.role)
        assertEquals(VideoAssetUsageIntent.INSPIRATION_ONLY, duplicate.asset.usageIntent)
        assertNull(duplicate.asset.rights)
        assertEquals(VideoAssetIdentityReview.UNREVIEWED, duplicate.asset.identityReview)
        val briefBinding = VideoBriefReference.from(duplicate.asset, VideoReferenceRole.OUTFIT)
        assertEquals(VideoReferenceRole.OUTFIT, briefBinding.role)
        assertEquals(VideoReferenceRole.STYLE, duplicate.asset.role)
        assertEquals(first.session.project.revision, duplicate.session.project.revision)
        assertEquals(1, duplicate.session.project.referenceVersions.size)
        assertContentEquals(descriptorBefore, Files.readAllBytes(descriptorPath))
        assertContentEquals(originalBefore, Files.readAllBytes(harness.projectRoot.resolve(first.asset.original.artifact.relativePath)))
        assertEquals(VideoReferenceRole.STYLE, harness.loaded(harness.importer.open(harness.projectRoot)).assets.single().role)
    }

    @Test
    fun `reopen loads pinned descriptors and verifies raw and thumbnail media`() {
        val harness = harness()
        val source = imageFile("recoverable.png", "png", 700, 350)
        val imported = harness.imported(
            harness.importer.import(
                harness.session,
                ImportVideoAsset(
                    source = source,
                    role = VideoReferenceRole.ENVIRONMENT,
                    usageIntent = VideoAssetUsageIntent.PRODUCTION_REFERENCE,
                    creationProvenance = VideoAssetCreationProvenance(
                        provider = "Owned JUnit fixture",
                        prompt = "A synthetic two-color image",
                    ),
                ),
            ),
        )

        val reopened = harness.loaded(harness.importer.open(harness.projectRoot))

        assertEquals(imported.session.project, reopened.session.project)
        assertEquals(listOf(imported.asset), reopened.assets)
        val record = reopened.session.project.referenceVersions.single()
        val resolved = harness.imageFiles.resolveOriginal(harness.projectRoot, record)
        assertContentEquals(Files.readAllBytes(source), Files.readAllBytes(resolved))
        assertEquals("Owned JUnit fixture", reopened.assets.single().creationProvenance?.provider)
    }

    @Test
    fun `reopen reports missing and changed owned media with recovery guidance`() {
        val missingHarness = harness("missing-project")
        val missingImport = missingHarness.imported(
            missingHarness.importer.import(
                missingHarness.session,
                ImportVideoAsset(imageFile("missing-source.png", "png", 80, 60)),
            ),
        )
        Files.delete(missingHarness.projectRoot.resolve(missingImport.asset.thumbnail.artifact.relativePath))

        val missing = missingHarness.rejectedLibrary(missingHarness.importer.open(missingHarness.projectRoot))
        assertEquals(VideoAssetProblemCode.ASSET_MISSING, missing.code)
        assertTrue(missing.nextAction.contains("Restore", ignoreCase = true))

        val changedHarness = harness("changed-project")
        val changedImport = changedHarness.imported(
            changedHarness.importer.import(
                changedHarness.session,
                ImportVideoAsset(imageFile("changed-source.png", "png", 80, 60, colorSeed = 2)),
            ),
        )
        val original = changedHarness.projectRoot.resolve(changedImport.asset.original.artifact.relativePath)
        val changedBytes = Files.readAllBytes(original).also { bytes -> bytes[bytes.lastIndex] = (bytes.last() + 1).toByte() }
        Files.write(original, changedBytes)

        val changed = changedHarness.rejectedLibrary(changedHarness.importer.open(changedHarness.projectRoot))
        assertEquals(VideoAssetProblemCode.ASSET_CHANGED, changed.code)
        assertTrue(changed.message.contains("SHA-256"))
    }

    @Test
    fun `unsupported oversized and corrupt sources fail with specific corrective guidance`() {
        val unsupportedHarness = harness("unsupported-project")
        val unsupported = root.resolve("notes.gif")
        Files.writeString(unsupported, "not an image")
        val unsupportedProblem = unsupportedHarness.rejected(
            unsupportedHarness.importer.import(unsupportedHarness.session, ImportVideoAsset(unsupported)),
        )
        assertEquals(VideoAssetProblemCode.UNSUPPORTED_FORMAT, unsupportedProblem.code)
        assertTrue(unsupportedProblem.nextAction.contains("PNG or JPEG"))

        val oversizedHarness = harness(
            name = "oversized-project",
            limits = VideoImageLimits(maxEncodedBytes = 64),
        )
        val oversized = imageFile("oversized.png", "png", 128, 128)
        val oversizedProblem = oversizedHarness.rejected(
            oversizedHarness.importer.import(oversizedHarness.session, ImportVideoAsset(oversized)),
        )
        assertEquals(VideoAssetProblemCode.ENCODED_FILE_TOO_LARGE, oversizedProblem.code)
        assertTrue(oversizedProblem.message.contains("64 byte"))

        val dimensionsHarness = harness(
            name = "dimensions-project",
            limits = VideoImageLimits(maxPixels = 100, maxDimension = 20),
        )
        val tooWide = imageFile("too-wide.jpeg", "jpeg", 21, 4)
        val dimensionsProblem = dimensionsHarness.rejected(
            dimensionsHarness.importer.import(dimensionsHarness.session, ImportVideoAsset(tooWide)),
        )
        assertEquals(VideoAssetProblemCode.IMAGE_DIMENSIONS_TOO_LARGE, dimensionsProblem.code)
        assertTrue(dimensionsProblem.message.contains("21x4"))

        val corruptHarness = harness("corrupt-project")
        val corrupt = root.resolve("truncated.png")
        Files.write(corrupt, byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d))
        val corruptBefore = Files.readAllBytes(corrupt)
        val corruptProblem = corruptHarness.rejected(
            corruptHarness.importer.import(corruptHarness.session, ImportVideoAsset(corrupt)),
        )
        assertEquals(VideoAssetProblemCode.CORRUPT_IMAGE, corruptProblem.code)
        assertTrue(corruptProblem.nextAction.contains("Re-export"))
        assertContentEquals(corruptBefore, Files.readAllBytes(corrupt))
    }

    @Test
    fun `PNG truncation checksums and invalid chunk lengths reject before publication`() {
        val valid = Files.readAllBytes(imageFile("complete.png", "png", 24, 16))
        val chunks = pngChunks(valid)
        val ihdr = pngBytes(chunks.take(1))
        val badHeaderCrc = valid.copyOf().also { it[ihdr.lastIndex] = (it[ihdr.lastIndex].toInt() xor 1).toByte() }
        val idatEnd = pngBytes(chunks.takeWhile { it.first != "IEND" }).lastIndex
        val badDataCrc = valid.copyOf().also { it[idatEnd] = (it[idatEnd].toInt() xor 1).toByte() }
        val badAncillaryCrc = pngBytes(chunks.dropLast(1) + ("tEXt" to "Note\u0000fixture".toByteArray()) + chunks.last())
            .also { it[it.size - 13] = (it[it.size - 13].toInt() xor 1).toByte() }
        val cases = listOf(
            "missing-IEND" to valid.copyOf(valid.size - 12),
            "truncated-IEND-CRC" to valid.copyOf(valid.size - 1),
            "bad-IHDR-CRC" to badHeaderCrc,
            "bad-IDAT-CRC" to badDataCrc,
            "bad-ancillary-CRC" to badAncillaryCrc,
            "unsigned-length-overflow" to valid.copyOf().also { ByteBuffer.wrap(it).putInt(8, -1) },
            "length-past-end" to valid.copyOf().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) },
            "partial-chunk-header" to (valid.copyOf(valid.size - 12) + byteArrayOf(0, 0, 0)),
        )

        assertCorruptPngImports(cases)
    }

    @Test
    fun `PNG headers critical chunk ordering and terminal structure are required even with valid CRCs`() {
        val chunks = pngChunks(Files.readAllBytes(imageFile("structural-source.png", "png", 24, 16)))
        val header = chunks.first()
        val data = chunks.single { it.first == "IDAT" }
        val end = chunks.last()
        val text = "tEXt" to "Note\u0000fixture".toByteArray()
        fun changedHeader(index: Int, value: Int) =
            "IHDR" to header.second.copyOf().also { it[index] = value.toByte() }
        val cases = listOf(
            "missing-header" to pngBytes(listOf(data, end)),
            "header-not-first" to pngBytes(listOf(text, header, data, end)),
            "duplicate-header" to pngBytes(listOf(header, header, data, end)),
            "short-header" to pngBytes(listOf("IHDR" to header.second.copyOf(12), data, end)),
            "zero-width" to pngBytes(listOf(changedHeader(3, 0), data, end)),
            "invalid-depth" to pngBytes(listOf(changedHeader(8, 3), data, end)),
            "invalid-color-type" to pngBytes(listOf(changedHeader(9, 5), data, end)),
            "invalid-compression" to pngBytes(listOf(changedHeader(10, 1), data, end)),
            "invalid-filter-method" to pngBytes(listOf(changedHeader(11, 1), data, end)),
            "invalid-interlace" to pngBytes(listOf(changedHeader(12, 2), data, end)),
            "indexed-without-palette" to pngBytes(listOf(changedHeader(9, 3), data, end)),
            "late-palette" to pngBytes(listOf(header, data, "PLTE" to byteArrayOf(1, 2, 3), end)),
            "missing-image-data" to pngBytes(listOf(header, end)),
            "separated-image-data" to pngBytes(listOf(header, data, text, "IDAT" to byteArrayOf(), end)),
            "nonempty-IEND" to pngBytes(listOf(header, data, "IEND" to byteArrayOf(0))),
            "duplicate-IEND" to pngBytes(chunks + end),
            "trailing-chunk" to pngBytes(chunks + text),
            "trailing-byte" to (pngBytes(chunks) + byteArrayOf(0)),
        )

        assertCorruptPngImports(cases)
    }

    @Test
    fun `valid PNG keeps ancillary chunks optional RGB palette and consecutive split image data`() {
        val harness = harness()
        val original = Files.readAllBytes(imageFile("valid-chunks.png", "png", 24, 16))
        val chunks = pngChunks(original)
        val data = chunks.single { it.first == "IDAT" }.second
        val split = data.size / 2
        val modified = pngBytes(
            listOf(
                chunks.first(),
                "vpAg" to byteArrayOf(1, 2, 3), // Unknown ancillary content is permitted.
                "PLTE" to byteArrayOf(10, 20, 30), // Optional suggested palette for truecolor PNG.
                "IDAT" to byteArrayOf(),
                "IDAT" to data.copyOfRange(0, split),
                "IDAT" to data.copyOfRange(split, data.size),
                "IDAT" to byteArrayOf(),
                "tEXt" to "Note\u0000preserved after pixels".toByteArray(),
                chunks.last(),
            ),
        )
        val source = root.resolve("valid-split.png")
        Files.write(source, modified)

        val imported = harness.imported(harness.importer.import(harness.session, ImportVideoAsset(source)))

        assertEquals(24, imported.asset.original.width)
        assertEquals(16, imported.asset.original.height)
        assertContentEquals(modified, Files.readAllBytes(source))
        assertContentEquals(modified, Files.readAllBytes(harness.projectRoot.resolve(imported.asset.original.artifact.relativePath)))
        assertEquals(listOf(imported.asset), harness.loaded(harness.importer.open(harness.projectRoot)).assets)
        val expected = ImageIO.read(root.resolve("valid-chunks.png").toFile())
        val actual = ImageIO.read(source.toFile())
        for (y in 0 until expected.height) for (x in 0 until expected.width) {
            assertEquals(expected.getRGB(x, y), actual.getRGB(x, y))
        }
    }

    @Test
    fun `PNG transparency requires valid placement uniqueness color type and length`() {
        val rgb = pngSampleChunks(2, samples = byteArrayOf(10, 20, 30, 40, 50, 60))
        val gray = pngSampleChunks(0, samples = byteArrayOf(0, 1))
        val rgba = pngSampleChunks(6, samples = byteArrayOf(10, 20, 30, -1, 40, 50, 60, -1))
        val grayAlpha = pngSampleChunks(4, samples = byteArrayOf(0, -1, 1, -1))
        val palette = "PLTE" to byteArrayOf(10, 20, 30, 40, 50, 60)
        val indexed = pngSampleChunks(3, samples = byteArrayOf(0, 1), palette = palette.second)
        val transparency = "tRNS" to byteArrayOf(0, 10, 0, 20, 0, 30)
        val cases = mutableListOf(
            "late-RGB-transparency" to pngBytes(rgb.dropLast(1) + transparency + rgb.last()),
            "late-indexed-transparency" to pngBytes(indexed.dropLast(1) + ("tRNS" to byteArrayOf(0)) + indexed.last()),
            "duplicate-transparency" to pngBytes(listOf(rgb.first(), transparency, transparency) + rgb.drop(1)),
            "transparency-before-optional-palette" to pngBytes(listOf(rgb.first(), transparency, palette) + rgb.drop(1)),
            "indexed-transparency-before-palette" to pngBytes(listOf(indexed.first(), "tRNS" to byteArrayOf(0)) + indexed.drop(1)),
            "transparency-with-RGBA" to pngBytes(listOf(rgba.first(), transparency) + rgba.drop(1)),
            "transparency-with-gray-alpha" to pngBytes(listOf(grayAlpha.first(), "tRNS" to byteArrayOf(0, 0)) + grayAlpha.drop(1)),
        )
        listOf(0, 1, 3).forEach { size ->
            cases += "gray-transparency-length-$size" to pngBytes(listOf(gray.first(), "tRNS" to ByteArray(size)) + gray.drop(1))
        }
        listOf(0, 5, 7).forEach { size ->
            cases += "RGB-transparency-length-$size" to pngBytes(listOf(rgb.first(), "tRNS" to ByteArray(size)) + rgb.drop(1))
        }
        listOf(0, 3).forEach { size ->
            cases += "indexed-transparency-length-$size" to pngBytes(indexed.take(2) + ("tRNS" to ByteArray(size)) + indexed.drop(2))
        }

        assertCorruptPngImports(cases)
    }

    @Test
    fun `PNG chunk reserved bit must be uppercase even for unknown ancillary chunks`() {
        val chunks = pngSampleChunks(2, samples = byteArrayOf(10, 20, 30, 40, 50, 60))
        val invalid = "vpag" to byteArrayOf(1, 2, 3)

        assertCorruptPngImports(
            listOf(
                "reserved-bit-before-data" to pngBytes(listOf(chunks.first(), invalid) + chunks.drop(1)),
                "reserved-bit-after-data" to pngBytes(chunks.dropLast(1) + invalid + chunks.last()),
            ),
        )
    }

    @Test
    fun `PNG background and histogram must respect their palette and image data boundaries`() {
        val rgb = pngSampleChunks(2, samples = byteArrayOf(10, 20, 30, 40, 50, 60))
        val gray = pngSampleChunks(0, samples = byteArrayOf(0, 1))
        val palette = "PLTE" to byteArrayOf(10, 20, 30, 40, 50, 60)
        val indexed = pngSampleChunks(3, samples = byteArrayOf(0, 1), palette = palette.second)
        val background = "bKGD" to byteArrayOf(0, 10, 0, 20, 0, 30)
        val histogram = "hIST" to byteArrayOf(0, 1, 0, 1)

        assertCorruptPngImports(
            listOf(
                "late-background" to pngBytes(rgb.dropLast(1) + background + rgb.last()),
                "duplicate-background" to pngBytes(listOf(rgb.first(), background, background) + rgb.drop(1)),
                "background-before-optional-palette" to pngBytes(listOf(rgb.first(), background, palette) + rgb.drop(1)),
                "indexed-background-before-palette" to pngBytes(listOf(indexed.first(), "bKGD" to byteArrayOf(0)) + indexed.drop(1)),
                "indexed-background-outside-palette" to pngBytes(indexed.take(2) + ("bKGD" to byteArrayOf(2)) + indexed.drop(2)),
                "RGB-background-length" to pngBytes(listOf(rgb.first(), "bKGD" to byteArrayOf(0, 0)) + rgb.drop(1)),
                "gray-background-length" to pngBytes(listOf(gray.first(), background) + gray.drop(1)),
                "indexed-background-length" to pngBytes(indexed.take(2) + ("bKGD" to byteArrayOf(0, 0)) + indexed.drop(2)),
                "histogram-without-palette" to pngBytes(listOf(rgb.first(), histogram) + rgb.drop(1)),
                "histogram-before-palette" to pngBytes(listOf(indexed.first(), histogram) + indexed.drop(1)),
                "late-histogram" to pngBytes(indexed.dropLast(1) + histogram + indexed.last()),
                "duplicate-histogram" to pngBytes(indexed.take(2) + listOf(histogram, histogram) + indexed.drop(2)),
                "short-histogram" to pngBytes(indexed.take(2) + ("hIST" to byteArrayOf(0, 1)) + indexed.drop(2)),
                "long-histogram" to pngBytes(indexed.take(2) + ("hIST" to ByteArray(6)) + indexed.drop(2)),
            ),
        )
    }

    @Test
    fun `valid PNG transparency for grayscale RGB and partial palettes survives import and reopen`() {
        val harness = harness()
        var session = harness.session
        val gray = pngSampleChunks(0, samples = byteArrayOf(0, 1))
        val grayLowDepth = pngSampleChunks(0, bitDepth = 1, samples = byteArrayOf(0x40))
        val gray16 = pngSampleChunks(0, bitDepth = 16, samples = byteArrayOf(0, 1, 0, 2))
        val rgb = pngSampleChunks(2, samples = byteArrayOf(10, 20, 30, 40, 50, 60))
        val rgb16 = pngSampleChunks(2, bitDepth = 16, samples = byteArrayOf(0, 1, 0, 2, 0, 3, 0, 1, 0, 2, 0, 4))
        val palette = "PLTE" to byteArrayOf(10, 20, 30, 40, 50, 60)
        val indexed = pngSampleChunks(3, samples = byteArrayOf(0, 1), palette = palette.second)
        val rgbTransparency = "tRNS" to byteArrayOf(0, 10, 0, 20, 0, 30)
        val cases = listOf(
            "gray" to (listOf(gray.first(), "tRNS" to byteArrayOf(0, 0), "bKGD" to byteArrayOf(0, 1)) + gray.drop(1)),
            "gray-one-bit" to (listOf(grayLowDepth.first(), "tRNS" to byteArrayOf(0, 0)) + grayLowDepth.drop(1)),
            "gray-sixteen-bit" to (listOf(gray16.first(), "tRNS" to byteArrayOf(0, 1)) + gray16.drop(1)),
            "RGB" to (listOf(rgb.first(), rgbTransparency) + rgb.drop(1)),
            "RGB-sixteen-bit" to (listOf(rgb16.first(), "tRNS" to byteArrayOf(0, 1, 0, 2, 0, 3)) + rgb16.drop(1)),
            "RGB-suggested-palette" to (listOf(rgb.first(), palette, rgbTransparency, "hIST" to byteArrayOf(0, 1, 0, 1)) + rgb.drop(1)),
            "indexed-partial-alpha-table" to (indexed.take(2) + listOf(
                "bKGD" to byteArrayOf(1), "tRNS" to byteArrayOf(0), "hIST" to byteArrayOf(0, 1, 0, 1),
            ) + indexed.drop(2)),
        )
        val importedAssets = cases.map { (name, chunks) ->
            val bytes = pngBytes(chunks)
            val source = root.resolve("valid-transparency-$name.png")
            Files.write(source, bytes)

            val imported = harness.imported(harness.importer.import(session, ImportVideoAsset(source)))

            assertTrue(imported.asset.original.hasAlphaChannel, name)
            assertTrue(imported.asset.original.hasTransparentPixels, name)
            assertTrue(imported.asset.thumbnail.hasTransparentPixels, name)
            assertContentEquals(bytes, Files.readAllBytes(source), name)
            assertContentEquals(bytes, Files.readAllBytes(harness.projectRoot.resolve(imported.asset.original.artifact.relativePath)), name)
            val decoded = ImageIO.read(source.toFile())
            assertEquals(0, decoded.getRGB(0, 0) ushr 24, name)
            assertEquals(255, decoded.getRGB(1, 0) ushr 24, name)
            val thumbnail = ImageIO.read(harness.projectRoot.resolve(imported.asset.thumbnail.artifact.relativePath).toFile())
            assertEquals(0, thumbnail.getRGB(0, 0) ushr 24, name)
            assertEquals(255, thumbnail.getRGB(1, 0) ushr 24, name)
            session = imported.session
            imported.asset
        }
        assertEquals(importedAssets, harness.loaded(harness.importer.open(harness.projectRoot)).assets)
    }

    @Test
    fun `pixel count and thumbnail byte bounds reject without publishing or changing the source`() {
        val source = imageFile("bounded.png", "png", 20, 20)
        val sourceBefore = Files.readAllBytes(source)
        val pixelHarness = harness("pixel-bound", limits = VideoImageLimits(maxPixels = 399, maxDimension = 20))
        val pixelProblem = pixelHarness.rejected(
            pixelHarness.importer.import(pixelHarness.session, ImportVideoAsset(source)),
        )
        assertEquals(VideoAssetProblemCode.IMAGE_DIMENSIONS_TOO_LARGE, pixelProblem.code)
        val thumbnailHarness = harness("thumbnail-bound", limits = VideoImageLimits(maxThumbnailBytes = 8))
        val thumbnailProblem = thumbnailHarness.rejected(
            thumbnailHarness.importer.import(thumbnailHarness.session, ImportVideoAsset(source)),
        )
        assertEquals(VideoAssetProblemCode.IO_FAILURE, thumbnailProblem.code)
        assertTrue(thumbnailProblem.message.contains("thumbnail"))
        listOf(pixelHarness, thumbnailHarness).forEach { harness ->
            assertTrue(harness.loaded(harness.importer.open(harness.projectRoot)).assets.isEmpty())
            assertFalse(Files.exists(harness.projectRoot.resolve("references")))
        }
        assertContentEquals(sourceBefore, Files.readAllBytes(source))
    }

    @Test
    fun `symbolic source and owned storage redirects are rejected without touching their targets`() {
        val harness = harness()
        val source = imageFile("safe-source.png", "png", 48, 32)
        val sourceBefore = Files.readAllBytes(source)
        val sourceLink = Files.createSymbolicLink(root.resolve("linked-source.png"), source)
        val sourceProblem = harness.rejected(
            harness.importer.import(harness.session, ImportVideoAsset(sourceLink)),
        )
        assertEquals(VideoAssetProblemCode.UNSAFE_SOURCE, sourceProblem.code)
        val external = Files.createDirectory(root.resolve("external-assets"))
        Files.createSymbolicLink(harness.projectRoot.resolve("references"), external)
        val storageProblem = harness.rejected(
            harness.importer.import(harness.session, ImportVideoAsset(source)),
        )
        assertEquals(VideoAssetProblemCode.PROJECT_INVALID, storageProblem.code)
        assertEquals(0L, Files.list(external).use { it.count() })
        assertContentEquals(sourceBefore, Files.readAllBytes(source))
        assertTrue(harness.loaded(harness.importer.open(harness.projectRoot)).assets.isEmpty())
    }

    @Test
    fun `failed project publication leaves current project and selected source unchanged and retryable`() {
        var rejectPublication = false
        val observer = VideoAtomicWriteObserver { _, _ ->
            if (rejectPublication) throw IOException("injected project publication failure")
        }
        val harness = harness(
            "publication-project",
            observer = observer,
            ids = listOf("reference-retry", "reference-retry"),
        )
        val source = imageFile("publication.png", "png", 120, 90)
        val sourceBefore = Files.readAllBytes(source)
        rejectPublication = true

        val failed = harness.rejected(
            harness.importer.import(harness.session, ImportVideoAsset(source, VideoReferenceRole.SUBJECT)),
        )

        assertEquals(VideoAssetProblemCode.SAVE_FAILED, failed.code)
        assertTrue(VideoProjectStore(listOf(root.resolve("midi-projects"))).open(harness.projectRoot).referenceVersions.isEmpty())
        assertContentEquals(sourceBefore, Files.readAllBytes(source))

        rejectPublication = false
        val retried = harness.imported(
            harness.importer.import(harness.session, ImportVideoAsset(source, VideoReferenceRole.SUBJECT)),
        )
        assertEquals("reference-retry", retried.asset.id.id)
        assertEquals(1, retried.session.project.referenceVersions.size)
        assertContentEquals(sourceBefore, Files.readAllBytes(source))
    }

    @Test
    fun `stale concurrent session is rejected before publishing another asset`() {
        val harness = harness()
        val stale = harness.session
        val winnerSource = imageFile("winner.png", "png", 72, 72)
        val staleSource = imageFile("stale.png", "png", 73, 73, colorSeed = 3)
        val winner = harness.imported(
            harness.importer.import(harness.session, ImportVideoAsset(winnerSource)),
        )

        val rejected = harness.rejected(harness.importer.import(stale, ImportVideoAsset(staleSource)))

        assertEquals(VideoAssetProblemCode.PROJECT_CHANGED, rejected.code)
        assertTrue(rejected.nextAction.contains("Reopen"))
        assertEquals(winner.session.project, VideoProjectStore(listOf(root.resolve("midi-projects"))).open(harness.projectRoot))
        val versionDirectories = Files.walk(harness.projectRoot.resolve("references")).use { paths ->
            paths.filter { Files.isDirectory(it) && it.name == "v1" }.toList()
        }
        assertEquals(1, versionDirectories.size)
    }

    private fun assertCorruptPngImports(cases: List<Pair<String, ByteArray>>) {
        val harness = harness()
        val projectFile = harness.projectRoot.resolve(VideoProjectStore.PROJECT_FILE)
        val projectBefore = Files.readAllBytes(projectFile)
        cases.forEach { (name, bytes) ->
            val source = root.resolve("$name.png")
            Files.write(source, bytes)

            val result = harness.importer.import(harness.session, ImportVideoAsset(source))

            assertTrue(result is VideoAssetImportResult.Rejected, "$name was accepted: $result")
            assertEquals(VideoAssetProblemCode.CORRUPT_IMAGE, result.problem.code, name)
            assertTrue(result.problem.nextAction.contains("Re-export"), name)
            assertContentEquals(bytes, Files.readAllBytes(source), name)
            assertContentEquals(projectBefore, Files.readAllBytes(projectFile), name)
            assertFalse(Files.exists(harness.projectRoot.resolve("references")), name)
        }
        assertTrue(harness.loaded(harness.importer.open(harness.projectRoot)).assets.isEmpty())
    }

    /** Split only known-good ImageIO fixture bytes; mutated files go solely through the importer. */
    private fun pngChunks(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val chunks = mutableListOf<Pair<String, ByteArray>>()
        var offset = 8
        while (offset < bytes.size) {
            val size = ByteBuffer.wrap(bytes).getInt(offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            chunks += type to bytes.copyOfRange(offset + 8, offset + 8 + size)
            offset += size + 12
        }
        return chunks
    }

    private fun pngBytes(chunks: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        chunks.forEach { (type, data) ->
            val encodedType = type.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply {
                update(encodedType)
                update(data)
            }
            output.write(ByteBuffer.allocate(4).putInt(data.size).array())
            output.write(encodedType)
            output.write(data)
            output.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
        }
        return output.toByteArray()
    }

    /** Two pixels in one unfiltered row, encoded independently of ImageIO's metadata writer. */
    private fun pngSampleChunks(
        colorType: Int,
        bitDepth: Int = 8,
        samples: ByteArray,
        palette: ByteArray? = null,
    ): List<Pair<String, ByteArray>> {
        val header = ByteBuffer.allocate(13).putInt(2).putInt(1)
            .put(bitDepth.toByte()).put(colorType.toByte()).put(0).put(0).put(0).array()
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { output ->
            output.write(0) // Filter method None.
            output.write(samples)
        }
        return buildList {
            add("IHDR" to header)
            palette?.let { add("PLTE" to it) }
            add("IDAT" to compressed.toByteArray())
            add("IEND" to byteArrayOf())
        }
    }

    private fun harness(
        name: String = "video-project",
        limits: VideoImageLimits = VideoImageLimits(),
        observer: VideoAtomicWriteObserver = VideoAtomicWriteObserver.NONE,
        ids: List<String> = emptyList(),
    ): Harness {
        val projectRoot = root.resolve(name)
        val store = VideoProjectStore(listOf(root.resolve("midi-projects")), observer)
        val lifecycle = VideoProjectLifecycle(
            store,
            FIXED_CLOCK,
            idFactory = { "video-project-id" },
        )
        val session = (lifecycle.create(CreateVideoProject(projectRoot, "Video", "video-project-id")) as
            VideoProjectLifecycleResult.Opened).session
        val counter = AtomicInteger()
        val imageFiles = VideoImageFiles(limits)
        val importer = VideoAssetImport(
            lifecycle,
            imageFiles,
            FIXED_CLOCK,
            idFactory = {
                ids.getOrNull(counter.getAndIncrement()) ?: "reference-${counter.get()}"
            },
        )
        return Harness(projectRoot, session, importer, imageFiles)
    }

    private fun imageFile(
        filename: String,
        format: String,
        width: Int,
        height: Int,
        transparent: Boolean = false,
        colorSeed: Int = 0,
    ): Path {
        val type = if (format == "png" && transparent) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(width, height, type)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(30 + colorSeed * 11, 80 + colorSeed * 7, 140 + colorSeed * 5)
            graphics.fillRect(0, 0, width, height)
            graphics.color = Color(220, 150, 80)
            graphics.fillOval(width / 4, height / 4, maxOf(1, width / 2), maxOf(1, height / 2))
        } finally {
            graphics.dispose()
        }
        if (transparent) image.setRGB(0, 0, 0x00000000)
        val path = root.resolve(filename)
        assertTrue(ImageIO.write(image, format, path.toFile()))
        return path
    }

    private data class Harness(
        val projectRoot: Path,
        val session: app.melotrail.video.application.VideoProjectSession,
        val importer: VideoAssetImport,
        val imageFiles: VideoImageFiles,
    ) {
        fun imported(result: VideoAssetImportResult) = result as VideoAssetImportResult.Imported
        fun reused(result: VideoAssetImportResult) = result as VideoAssetImportResult.Reused
        fun loaded(result: VideoAssetLibraryResult) = result as VideoAssetLibraryResult.Loaded
        fun rejected(result: VideoAssetImportResult) = (result as VideoAssetImportResult.Rejected).problem
        fun rejectedLibrary(result: VideoAssetLibraryResult) = (result as VideoAssetLibraryResult.Rejected).problem
    }

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC)
    }
}

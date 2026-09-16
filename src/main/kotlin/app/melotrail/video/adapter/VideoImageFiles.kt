package app.melotrail.video.adapter

import app.melotrail.video.application.InspectedVideoAsset
import app.melotrail.video.application.PreparedVideoImage
import app.melotrail.video.application.PublishVideoAsset
import app.melotrail.video.application.PublishedVideoAsset
import app.melotrail.video.application.VideoAssetFileException
import app.melotrail.video.application.VideoAssetFiles
import app.melotrail.video.application.VideoAssetProblemCode
import app.melotrail.video.domain.VideoArtifact
import app.melotrail.video.domain.VideoAsset
import app.melotrail.video.domain.VideoAssetImage
import app.melotrail.video.domain.VideoImageFormat
import app.melotrail.video.domain.VideoMeasuredAlpha
import app.melotrail.video.domain.VideoReferenceRecord
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.UUID
import java.util.zip.CRC32
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** JDK image decoder and immutable filesystem owner for imported Video references. */
class VideoImageFiles(
    private val limits: VideoImageLimits = VideoImageLimits(),
) : VideoAssetFiles {
    override fun inspect(source: Path): PreparedVideoImage {
        // Preserve filesystem traversal order; normalizing symlink/.. can select a different file.
        val selected = source.toAbsolutePath()
        if (Files.isSymbolicLink(selected)) {
            fail(
                VideoAssetProblemCode.UNSAFE_SOURCE,
                "The selected reference is a symbolic link: $selected",
                "Choose the actual local PNG or JPEG file instead of a symbolic link.",
            )
        }
        if (!Files.isRegularFile(selected, LinkOption.NOFOLLOW_LINKS)) {
            fail(
                VideoAssetProblemCode.SOURCE_NOT_FOUND,
                "The selected reference is missing or is not a regular file: $selected",
                "Choose an available local PNG or JPEG file and retry.",
            )
        }
        try {
            if (Files.size(selected) == 0L) corrupt("The selected image file is empty.")
        } catch (error: VideoAssetFileException) {
            throw error
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.IO_FAILURE,
                "The selected reference size could not be read: $selected",
                "Check the file and folder permissions, then retry.",
                error,
            )
        }
        val bytes = readBounded(
            selected,
            limits.maxEncodedBytes,
            VideoAssetProblemCode.ENCODED_FILE_TOO_LARGE,
            "The selected reference exceeds the ${limits.maxEncodedBytes} byte import limit.",
            "Export a smaller PNG or JPEG while keeping the original file unchanged, then import that file.",
        )
        val decoded = decode(bytes, original = true)
        val thumbnail = makeThumbnail(decoded.image)
        return PreparedVideoImage(
            bytes = bytes,
            sha256 = sha256(bytes),
            format = decoded.format,
            width = decoded.image.width,
            height = decoded.image.height,
            hasAlphaChannel = decoded.hasAlphaChannel,
            hasTransparentPixels = decoded.hasTransparentPixels,
            thumbnailBytes = thumbnail.bytes,
            thumbnailSha256 = sha256(thumbnail.bytes),
            thumbnailWidth = thumbnail.width,
            thumbnailHeight = thumbnail.height,
            thumbnailHasAlphaChannel = thumbnail.hasAlphaChannel,
            thumbnailHasTransparentPixels = thumbnail.hasTransparentPixels,
            originalFileName = selected.fileName?.toString() ?: selected.toString(),
            originalLocation = selected.toString(),
        )
    }

    override fun publish(projectRoot: Path, request: PublishVideoAsset): PublishedVideoAsset {
        val root = requireProjectRoot(projectRoot)
        val bundleRelative = "references/${request.id.id}/v${request.id.version}"
        val originalRelative = "$bundleRelative/original.${request.image.format.fileExtension}"
        val thumbnailRelative = "$bundleRelative/thumbnail.png"
        val descriptorRelative = "$bundleRelative/asset.json"
        val asset = VideoAsset(
            id = request.id,
            original = VideoAssetImage(
                artifact = VideoArtifact(originalRelative, request.image.sha256),
                format = request.image.format,
                mediaType = request.image.format.mediaType,
                width = request.image.width,
                height = request.image.height,
                encodedBytes = request.image.bytes.size.toLong(),
                hasAlphaChannel = request.image.hasAlphaChannel,
                hasTransparentPixels = request.image.hasTransparentPixels,
            ),
            thumbnail = VideoAssetImage(
                artifact = VideoArtifact(thumbnailRelative, request.image.thumbnailSha256),
                format = VideoImageFormat.PNG,
                mediaType = VideoImageFormat.PNG.mediaType,
                width = request.image.thumbnailWidth,
                height = request.image.thumbnailHeight,
                encodedBytes = request.image.thumbnailBytes.size.toLong(),
                hasAlphaChannel = request.image.thumbnailHasAlphaChannel,
                hasTransparentPixels = request.image.thumbnailHasTransparentPixels,
            ),
            role = request.role,
            source = request.source,
            rights = request.rights,
            usageIntent = request.usageIntent,
            creationProvenance = request.creationProvenance,
            createdAt = request.createdAt,
        )
        val descriptorBytes = DESCRIPTOR_JSON.encodeToString(asset).toByteArray(Charsets.UTF_8)
        if (descriptorBytes.size.toLong() > limits.maxDescriptorBytes) {
            fail(
                VideoAssetProblemCode.INVALID_METADATA,
                "The reference metadata exceeds the ${limits.maxDescriptorBytes} byte descriptor limit.",
                "Shorten the optional source, rights, or creation-provenance text and retry.",
            )
        }
        val record = VideoReferenceRecord(
            id = request.id,
            artifact = VideoArtifact(descriptorRelative, sha256(descriptorBytes)),
            createdAt = request.createdAt,
        )
        val identityDirectory = ensureDirectory(root, root.resolve("references/${request.id.id}"))
        val destination = identityDirectory.resolve("v${request.id.version}")
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            val existing = load(projectRoot, record)
            if (existing != asset) {
                fail(
                    VideoAssetProblemCode.ASSET_CHANGED,
                    "The immutable destination for ${request.id.id} version ${request.id.version} already contains different bytes or metadata.",
                    "Reopen the project and retry with a new reference identity; the existing asset was not overwritten.",
                )
            }
            return PublishedVideoAsset(record, existing)
        }

        val staging = try {
            Files.createTempDirectory(identityDirectory, ".asset-stage-${UUID.randomUUID()}-")
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.IO_FAILURE,
                "The reference staging directory could not be created in the video project.",
                "Check free space and project-folder permissions, then retry.",
                error,
            )
        }
        var moved = false
        try {
            writeNew(staging.resolve("original.${request.image.format.fileExtension}"), request.image.bytes)
            writeNew(staging.resolve("thumbnail.png"), request.image.thumbnailBytes)
            writeNew(staging.resolve("asset.json"), descriptorBytes)
            // No replacement option: an existing immutable identity always wins the race.
            Files.move(staging, destination)
            moved = true
        } catch (error: FileAlreadyExistsException) {
            val existing = load(projectRoot, record)
            if (existing != asset) {
                fail(
                    VideoAssetProblemCode.ASSET_CHANGED,
                    "The immutable destination for ${request.id.id} version ${request.id.version} was claimed by different content.",
                    "Reopen the project and retry; no existing asset was overwritten.",
                    error,
                )
            }
            return PublishedVideoAsset(record, existing)
        } catch (error: VideoAssetFileException) {
            throw error
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.IO_FAILURE,
                "The reference could not be copied into immutable project storage.",
                "Check free space and project-folder permissions, then retry; the selected source was not changed.",
                error,
            )
        } finally {
            if (!moved) deleteOwnedTree(staging)
        }
        return PublishedVideoAsset(record, load(projectRoot, record))
    }

    override fun load(projectRoot: Path, record: VideoReferenceRecord): VideoAsset =
        loadVerified(projectRoot, record).asset

    override fun inspectOriginal(projectRoot: Path, record: VideoReferenceRecord): InspectedVideoAsset {
        val verified = loadVerified(projectRoot, record)
        return measurePixels(verified.asset, verified.original.image)
    }

    private fun loadVerified(projectRoot: Path, record: VideoReferenceRecord): VerifiedAsset {
        val root = requireProjectRoot(projectRoot)
        val expectedDescriptor = "references/${record.id.id}/v${record.id.version}/asset.json"
        if (record.artifact.relativePath != expectedDescriptor) {
            fail(
                VideoAssetProblemCode.PROJECT_INVALID,
                "Reference ${record.id.id} version ${record.id.version} does not point to its owned asset descriptor.",
                "Restore the reference record and its immutable asset bundle from a known-good copy.",
            )
        }
        val descriptorPath = resolveOwned(root, record.artifact.relativePath, "asset descriptor")
        val descriptorBytes = readBounded(
            descriptorPath,
            limits.maxDescriptorBytes,
            VideoAssetProblemCode.ASSET_CHANGED,
            "The asset descriptor is larger than its supported bound: ${record.artifact.relativePath}",
            "Restore the exact pinned descriptor or reimport the source as a new reference.",
        )
        requireDigest(descriptorBytes, record.artifact, "asset descriptor")
        val asset = try {
            DESCRIPTOR_JSON.decodeFromString<VideoAsset>(descriptorBytes.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.ASSET_CHANGED,
                "The pinned asset descriptor is corrupt or unsupported: ${record.artifact.relativePath}",
                "Restore the exact pinned descriptor or reimport the source as a new reference.",
                error,
            )
        }
        if (asset.id != record.id || asset.createdAt != record.createdAt) {
            fail(
                VideoAssetProblemCode.ASSET_CHANGED,
                "The pinned descriptor identity does not match reference ${record.id.id} version ${record.id.version}.",
                "Restore the matching descriptor and media bundle from a known-good copy.",
            )
        }
        val bundle = "references/${record.id.id}/v${record.id.version}"
        val expectedOriginal = "$bundle/original.${asset.original.format.fileExtension}"
        if (asset.original.artifact.relativePath != expectedOriginal ||
            asset.thumbnail.artifact.relativePath != "$bundle/thumbnail.png"
        ) {
            fail(
                VideoAssetProblemCode.ASSET_CHANGED,
                "The pinned descriptor contains media paths outside its immutable asset bundle.",
                "Restore the matching descriptor and media bundle from a known-good copy.",
            )
        }
        val original = verifyStoredImage(root, asset.original, original = true)
        verifyStoredImage(root, asset.thumbnail, original = false)
        return VerifiedAsset(asset, original)
    }

    override fun resolveOriginal(projectRoot: Path, record: VideoReferenceRecord): Path {
        val asset = load(projectRoot, record)
        return resolveOwned(requireProjectRoot(projectRoot), asset.original.artifact.relativePath, "original reference")
    }

    private fun verifyStoredImage(root: Path, expected: VideoAssetImage, original: Boolean): DecodedImage {
        val maximum = if (original) limits.maxEncodedBytes else limits.maxThumbnailBytes
        val path = resolveOwned(root, expected.artifact.relativePath, if (original) "original reference" else "thumbnail")
        val bytes = readBounded(
            path,
            maximum,
            VideoAssetProblemCode.ASSET_CHANGED,
            "The owned ${if (original) "reference" else "thumbnail"} exceeds its supported byte bound.",
            "Restore the exact pinned asset bundle or reimport the source as a new reference.",
        )
        if (bytes.size.toLong() != expected.encodedBytes) {
            changed(expected.artifact.relativePath, "byte size")
        }
        requireDigest(bytes, expected.artifact, if (original) "original reference" else "thumbnail")
        val actual = try {
            decode(bytes, original)
        } catch (error: VideoAssetFileException) {
            fail(
                VideoAssetProblemCode.ASSET_CHANGED,
                "The pinned image cannot be decoded as recorded: ${expected.artifact.relativePath}",
                "Restore the exact pinned asset bundle or import the changed source as a new reference.",
                error,
            )
        }
        if (actual.format != expected.format || actual.image.width != expected.width || actual.image.height != expected.height ||
            actual.hasAlphaChannel != expected.hasAlphaChannel || actual.hasTransparentPixels != expected.hasTransparentPixels
        ) {
            changed(expected.artifact.relativePath, "decoded image facts")
        }
        if (!original && (actual.image.width > limits.thumbnailMaxDimension || actual.image.height > limits.thumbnailMaxDimension)) {
            changed(expected.artifact.relativePath, "thumbnail dimensions")
        }
        return actual
    }

    private fun decode(bytes: ByteArray, original: Boolean): DecodedImage {
        val signatureFormat = signatureFormat(bytes)
        if (signatureFormat == null) {
            if (looksLikeSupportedPrefix(bytes)) {
                corrupt("The PNG or JPEG header is truncated or corrupt.")
            }
            fail(
                VideoAssetProblemCode.UNSUPPORTED_FORMAT,
                "The selected content is not a supported PNG or JPEG image.",
                "Export the image as PNG or JPEG and select that exported file.",
            )
        }
        if (signatureFormat == VideoImageFormat.PNG) validatePngStructure(bytes)
        val warnings = mutableListOf<String>()
        try {
            MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) corrupt("No image decoder accepted the ${signatureFormat.name} content.")
                val reader = readers.next()
                try {
                    reader.addIIOReadWarningListener { _, warning -> warnings += warning }
                    reader.setInput(input, false, true)
                    val decodedFormat = when (reader.formatName.lowercase()) {
                        "png" -> VideoImageFormat.PNG
                        "jpeg", "jpg" -> VideoImageFormat.JPEG
                        else -> null
                    }
                    if (decodedFormat != signatureFormat) corrupt("The image signature and decoded format disagree.")
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    validateDimensions(width, height, original)
                    val image = reader.read(0) ?: corrupt("The decoder returned no image pixels.")
                    if (warnings.isNotEmpty()) corrupt("The decoder reported incomplete or corrupt image data.")
                    if (image.width != width || image.height != height) corrupt("Decoded dimensions changed while reading the image.")
                    val hasAlpha = image.colorModel.hasAlpha()
                    val transparent = hasAlpha && hasTransparentPixel(image)
                    return DecodedImage(image, decodedFormat, hasAlpha, transparent)
                } finally {
                    reader.dispose()
                }
            }
        } catch (error: VideoAssetFileException) {
            throw error
        } catch (error: Exception) {
            corrupt("The ${signatureFormat.name} image could not be decoded completely.", error)
        }
    }

    /** ImageIO may return pixels without reading IEND or checking any chunk CRC. */
    private fun validatePngStructure(bytes: ByteArray) {
        var offset = PNG_SIGNATURE.size
        var hasHeader = false
        var hasPalette = false
        var paletteEntries = 0
        var hasTransparency = false
        var hasBackground = false
        var hasHistogram = false
        var hasData = false
        var dataEnded = false
        var colorType = -1
        var bitDepth = -1
        val crc = CRC32()
        while (offset < bytes.size) {
            // Check against remaining bytes before narrowing lengths or adding offsets.
            if (bytes.size - offset < 12) corrupt("The PNG ends inside a chunk header or checksum.")
            val length = pngUnsignedInt(bytes, offset)
            if (length > (bytes.size - offset - 12).toLong()) corrupt("A PNG chunk length exceeds the remaining file content.")
            val size = length.toInt()
            val data = offset + 8
            val end = data + size
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (type.any { it !in 'A'..'Z' && it !in 'a'..'z' }) corrupt("The PNG contains an invalid chunk type.")
            if (type[2] !in 'A'..'Z') corrupt("The PNG chunk type has an invalid reserved bit.")
            crc.reset()
            crc.update(bytes, offset + 4, size + 4)
            if (crc.value != pngUnsignedInt(bytes, end)) corrupt("The PNG $type chunk checksum does not match its content.")
            if (!hasHeader && type != "IHDR") corrupt("The PNG must start with its image header.")
            if (hasData && type != "IDAT") dataEnded = true
            when (type) {
                "IHDR" -> {
                    if (hasHeader || size != 13) corrupt("The PNG must contain exactly one complete image header.")
                    val width = pngUnsignedInt(bytes, data)
                    val height = pngUnsignedInt(bytes, data + 4)
                    bitDepth = bytes[data + 8].toInt() and 0xff
                    colorType = bytes[data + 9].toInt() and 0xff
                    val validDepth = when (colorType) {
                        0 -> bitDepth in setOf(1, 2, 4, 8, 16)
                        2, 4, 6 -> bitDepth == 8 || bitDepth == 16
                        3 -> bitDepth in setOf(1, 2, 4, 8)
                        else -> false
                    }
                    if (width !in 1..Int.MAX_VALUE.toLong() || height !in 1..Int.MAX_VALUE.toLong() ||
                        !validDepth || bytes[data + 10] != 0.toByte() || bytes[data + 11] != 0.toByte() ||
                        bytes[data + 12].toInt() !in 0..1
                    ) corrupt("The PNG image header contains invalid dimensions or encoding settings.")
                    hasHeader = true
                }
                "PLTE" -> {
                    if (hasPalette || hasData || hasTransparency || hasBackground || colorType == 0 || colorType == 4 ||
                        size == 0 || size % 3 != 0 || size > 768 ||
                        (colorType == 3 && size / 3 > (1 shl bitDepth))
                    ) corrupt("The PNG palette has invalid size, color type, or ordering.")
                    hasPalette = true
                    paletteEntries = size / 3
                }
                "tRNS" -> {
                    // ImageIO can silently ignore late or inapplicable transparency.
                    val validSize = when (colorType) {
                        0 -> size == 2
                        2 -> size == 6
                        3 -> hasPalette && size in 1..paletteEntries
                        else -> false // Color types 4 and 6 already contain alpha samples.
                    }
                    if (hasTransparency || hasData || !validSize) {
                        corrupt("The PNG transparency has invalid size, color type, or ordering.")
                    }
                    hasTransparency = true
                }
                "bKGD" -> {
                    val validSize = when (colorType) {
                        0, 4 -> size == 2
                        2, 6 -> size == 6
                        3 -> hasPalette && size == 1 && (bytes[data].toInt() and 0xff) < paletteEntries
                        else -> false
                    }
                    if (hasBackground || hasData || !validSize) {
                        corrupt("The PNG background has invalid size, palette index, or ordering.")
                    }
                    hasBackground = true
                }
                "hIST" -> {
                    if (hasHistogram || hasData || !hasPalette || size != paletteEntries * 2) {
                        corrupt("The PNG histogram has invalid size or palette ordering.")
                    }
                    hasHistogram = true
                }
                "IDAT" -> {
                    if (dataEnded || (colorType == 3 && !hasPalette)) {
                        corrupt("The PNG image data is out of order or lacks its required palette.")
                    }
                    hasData = true
                }
                "IEND" -> {
                    if (!hasData || size != 0 || end + 4 != bytes.size) {
                        corrupt("The PNG must end with one empty IEND chunk after its image data.")
                    }
                    return
                }
                else -> {
                    // Unknown ancillary chunks are allowed; unknown critical chunks cannot be decoded safely.
                    if (type[0] in 'A'..'Z') corrupt("The PNG contains an unsupported critical chunk: $type.")
                }
            }
            offset = end + 4
        }
        corrupt("The PNG is missing its complete final IEND chunk.")
    }

    private fun pngUnsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)

    private fun validateDimensions(width: Int, height: Int, original: Boolean) {
        val maximumDimension = if (original) limits.maxDimension else limits.thumbnailMaxDimension
        val maximumPixels = if (original) limits.maxPixels else limits.thumbnailMaxDimension.toLong() * limits.thumbnailMaxDimension
        val pixels = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || width > maximumDimension || height > maximumDimension || pixels > maximumPixels) {
            fail(
                VideoAssetProblemCode.IMAGE_DIMENSIONS_TOO_LARGE,
                "The image dimensions ${width}x$height exceed the supported ${maximumDimension}px edge and $maximumPixels pixel bounds.",
                "Export a smaller PNG or JPEG and import it as a separate reference; the original was not changed.",
            )
        }
    }

    private fun makeThumbnail(source: BufferedImage): Thumbnail {
        val scale = minOf(
            1.0,
            limits.thumbnailMaxDimension.toDouble() / source.width.toDouble(),
            limits.thumbnailMaxDimension.toDouble() / source.height.toDouble(),
        )
        val width = maxOf(1, (source.width * scale).toInt())
        val height = maxOf(1, (source.height * scale).toInt())
        val target = BufferedImage(width, height, if (source.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        target.createGraphics().use { graphics ->
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, width, height, null)
        }
        val output = BoundedByteArrayOutputStream(limits.maxThumbnailBytes)
        try {
            if (!ImageIO.write(target, "png", output)) throw IOException("No PNG writer is available")
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.IO_FAILURE,
                "A bounded PNG thumbnail could not be created.",
                "Export a smaller source image and retry; the original file was not changed.",
                error,
            )
        }
        return Thumbnail(
            bytes = output.toByteArray(),
            width = width,
            height = height,
            hasAlphaChannel = target.colorModel.hasAlpha(),
            hasTransparentPixels = target.colorModel.hasAlpha() && hasTransparentPixel(target),
        )
    }

    private fun requireProjectRoot(projectRoot: Path): Path {
        val absolute = projectRoot.toAbsolutePath()
        if (!Files.isDirectory(absolute)) {
            fail(
                VideoAssetProblemCode.PROJECT_INVALID,
                "The video project root is missing or is not a directory: $absolute",
                "Open or create the independent video project before importing references.",
            )
        }
        return try {
            absolute.toRealPath()
        } catch (error: IOException) {
            fail(
                VideoAssetProblemCode.PROJECT_INVALID,
                "The video project root could not be resolved safely: $absolute",
                "Restore access to the video project folder and retry.",
                error,
            )
        }
    }

    private fun ensureDirectory(root: Path, requested: Path): Path {
        val normalized = requested.normalize()
        if (!normalized.startsWith(root)) projectInvalid("Asset storage escapes the video project root.")
        try {
            var current = root
            root.relativize(normalized).forEach { segment ->
                current = current.resolve(segment)
                if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.createDirectory(current)
                    } catch (_: FileAlreadyExistsException) {
                        // Another importer created it; validate the exact entry below.
                    }
                }
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    projectInvalid("Asset storage contains a symbolic link or non-directory component: $current")
                }
            }
        } catch (error: VideoAssetFileException) {
            throw error
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.IO_FAILURE,
                "The immutable asset directory could not be prepared.",
                "Check project-folder permissions and free space, then retry.",
                error,
            )
        }
        return normalized
    }

    private fun resolveOwned(root: Path, relativePath: String, label: String): Path {
        val target = root.resolve(relativePath).normalize()
        if (!target.startsWith(root)) projectInvalid("The $label escapes the video project root.")
        var current = root
        root.relativize(target).forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) projectInvalid("The $label path contains a symbolic link: $current")
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            fail(
                VideoAssetProblemCode.ASSET_MISSING,
                "The owned $label is missing: $relativePath",
                "Restore the exact pinned asset bundle or reimport the source as a new reference.",
            )
        }
        val real = try {
            target.toRealPath()
        } catch (error: IOException) {
            fail(
                VideoAssetProblemCode.ASSET_MISSING,
                "The owned $label could not be resolved: $relativePath",
                "Restore access to the exact pinned asset bundle and retry.",
                error,
            )
        }
        if (!real.startsWith(root)) projectInvalid("The $label resolves outside the video project root.")
        return target
    }

    private fun readBounded(
        path: Path,
        maximumBytes: Long,
        code: VideoAssetProblemCode,
        message: String,
        nextAction: String,
    ): ByteArray {
        try {
            val declaredSize = Files.size(path)
            if (declaredSize <= 0L) fail(code, message, nextAction)
            if (declaredSize > maximumBytes || maximumBytes > Int.MAX_VALUE) fail(code, message, nextAction)
            Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream(minOf(declaredSize, 64L * 1024L).toInt())
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maximumBytes) fail(code, message, nextAction)
                    output.write(buffer, 0, read)
                }
                if (total <= 0L) fail(code, message, nextAction)
                return output.toByteArray()
            }
        } catch (error: VideoAssetFileException) {
            throw error
        } catch (error: Exception) {
            fail(
                VideoAssetProblemCode.IO_FAILURE,
                "The image file could not be read safely: $path",
                "Check the file and folder permissions, then retry.",
                error,
            )
        }
    }

    private fun writeNew(path: Path, bytes: ByteArray) {
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun requireDigest(bytes: ByteArray, artifact: VideoArtifact, label: String) {
        if (sha256(bytes) != artifact.sha256) changed(artifact.relativePath, "$label SHA-256")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun changed(relativePath: String, fact: String): Nothing = fail(
        VideoAssetProblemCode.ASSET_CHANGED,
        "The pinned $fact does not match for $relativePath.",
        "Restore the exact pinned asset bundle or import the changed source as a new reference.",
    )

    private fun projectInvalid(message: String): Nothing = fail(
        VideoAssetProblemCode.PROJECT_INVALID,
        message,
        "Restore the video project and immutable asset bundle from a known-good copy.",
    )

    private fun corrupt(message: String, cause: Throwable? = null): Nothing = fail(
        VideoAssetProblemCode.CORRUPT_IMAGE,
        message,
        "Re-export the complete image as PNG or JPEG and retry; the selected file was not changed.",
        cause,
    )

    private fun signatureFormat(bytes: ByteArray): VideoImageFormat? = when {
        bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] } -> VideoImageFormat.PNG
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> VideoImageFormat.JPEG
        else -> null
    }

    private fun looksLikeSupportedPrefix(bytes: ByteArray): Boolean =
        (bytes.size >= 4 && bytes[0] == PNG_SIGNATURE[0] && bytes[1] == PNG_SIGNATURE[1] &&
            bytes[2] == PNG_SIGNATURE[2] && bytes[3] == PNG_SIGNATURE[3]) ||
            (bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte())

    private fun measurePixels(asset: VideoAsset, decoded: BufferedImage): InspectedVideoAsset {
        var opaque = 0L
        var translucent = 0L
        var transparent = 0L
        var minimumLuminance = 255
        var maximumLuminance = 0
        val alphaRaster = decoded.alphaRaster
        val opaqueSample = alphaRaster?.let { (1L shl it.sampleModel.getSampleSize(0)) - 1L } ?: 255L
        val row = IntArray(decoded.width)
        for (y in 0 until decoded.height) {
            decoded.getRGB(0, y, decoded.width, 1, row, 0, decoded.width)
            row.forEachIndexed { x, pixel ->
                // Preserve 16-bit alpha distinctions that getRGB rounds to opaque 8-bit samples.
                val alpha = alphaRaster?.getSample(x, y, 0)?.toLong() ?: (pixel ushr 24).toLong()
                when (alpha) {
                    0L -> transparent++
                    opaqueSample -> opaque++
                    else -> translucent++
                }
                if (alpha > 0L) {
                    val red = pixel ushr 16 and 0xff
                    val green = pixel ushr 8 and 0xff
                    val blue = pixel and 0xff
                    val luminance = (red * 2126 + green * 7152 + blue * 722) / 10_000
                    minimumLuminance = minOf(minimumLuminance, luminance)
                    maximumLuminance = maxOf(maximumLuminance, luminance)
                }
            }
        }
        return InspectedVideoAsset(
            asset,
            VideoMeasuredAlpha(opaque, translucent, transparent),
            maximumLuminance > minimumLuminance,
        )
    }

    private fun hasTransparentPixel(image: BufferedImage): Boolean {
        if (!image.colorModel.hasAlpha()) return false
        val alpha = image.alphaRaster
        // Indexed PNGs store alpha in the palette, while component PNGs can use 16-bit alpha.
        val opaque = alpha?.let { (1L shl it.sampleModel.getSampleSize(0)) - 1L } ?: 255L
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (alpha != null) {
                    if (alpha.getSample(x, y, 0).toLong() < opaque) return true
                } else if (image.getRGB(x, y) ushr 24 < 255) {
                    return true
                }
            }
        }
        return false
    }

    private fun deleteOwnedTree(root: Path) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        runCatching {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private inline fun <T : java.awt.Graphics2D> T.use(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }

    private fun fail(
        code: VideoAssetProblemCode,
        message: String,
        nextAction: String,
        cause: Throwable? = null,
    ): Nothing = throw VideoAssetFileException(code, message, nextAction, cause)

    private data class VerifiedAsset(val asset: VideoAsset, val original: DecodedImage)

    private data class DecodedImage(
        val image: BufferedImage,
        val format: VideoImageFormat,
        val hasAlphaChannel: Boolean,
        val hasTransparentPixels: Boolean,
    )

    private data class Thumbnail(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val hasAlphaChannel: Boolean,
        val hasTransparentPixels: Boolean,
    )

    private class BoundedByteArrayOutputStream(private val maximumBytes: Long) : OutputStream() {
        private val delegate = ByteArrayOutputStream()

        override fun write(value: Int) {
            requireCapacity(1)
            delegate.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            requireCapacity(length)
            delegate.write(bytes, offset, length)
        }

        fun toByteArray(): ByteArray = delegate.toByteArray()

        private fun requireCapacity(additional: Int) {
            if (delegate.size().toLong() + additional.toLong() > maximumBytes) {
                throw IOException("Thumbnail exceeds the bounded output size")
            }
        }
    }

    private companion object {
        val DESCRIPTOR_JSON = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
        }
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
    }
}

data class VideoImageLimits(
    val maxEncodedBytes: Long = 32L * 1024L * 1024L,
    val maxPixels: Long = 25_000_000L,
    val maxDimension: Int = 12_000,
    val thumbnailMaxDimension: Int = VideoAsset.THUMBNAIL_MAX_DIMENSION,
    val maxThumbnailBytes: Long = 2L * 1024L * 1024L,
    val maxDescriptorBytes: Long = 128L * 1024L,
) {
    init {
        require(maxEncodedBytes in 1..Int.MAX_VALUE.toLong())
        require(maxPixels > 0L)
        require(maxDimension > 0)
        require(thumbnailMaxDimension in 1..VideoAsset.THUMBNAIL_MAX_DIMENSION)
        require(maxThumbnailBytes in 1..Int.MAX_VALUE.toLong())
        require(maxDescriptorBytes in 1..Int.MAX_VALUE.toLong())
    }
}

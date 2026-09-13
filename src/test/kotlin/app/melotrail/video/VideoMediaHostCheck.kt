package app.melotrail.video

import app.melotrail.video.adapter.VideoMediaProbe
import app.melotrail.video.adapter.VideoMediaProbeRequest
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.math.abs

/** Explicit real-host V12 proof. This class is test-classpath only and is never loaded at MIDI startup. */
object VideoMediaHostCheck {
    @JvmStatic
    fun main(arguments: Array<String>) {
        require(arguments.size in 1..2) {
            "Usage: videoMediaProbe <absolute-tools-directory> [absolute-new-output-directory]"
        }
        check(System.getProperty("os.name") == "Mac OS X" && System.getProperty("os.arch") in setOf("arm64", "aarch64")) {
            "videoMediaProbe requires macOS arm64; found ${System.getProperty("os.name")}/${System.getProperty("os.arch")}"
        }
        val tools = Path.of(arguments[0])
        require(tools.isAbsolute) { "Tools directory must be absolute: $tools" }
        val output = arguments.getOrNull(1)?.let { Path.of(it) } ?: defaultOutputDirectory()
        require(output.isAbsolute) { "Output directory must be absolute: $output" }

        val fixtureUrl = checkNotNull(VideoMediaHostCheck::class.java.getResource("/fixtures/video/owned-motion.mp4")) {
            "Missing owned V12 fixture: src/test/resources/fixtures/video/owned-motion.mp4"
        }
        check(fixtureUrl.protocol == "file") { "Owned V12 fixture must be a local file resource: $fixtureUrl" }
        val fixture = Path.of(fixtureUrl.toURI()).toRealPath()
        val originalBytes = Files.readAllBytes(fixture)
        check(sha256(fixture) == FIXTURE_SHA256) { "Owned V12 fixture bytes do not match the reviewed fixture pin" }

        val result = VideoMediaProbe().run(VideoMediaProbeRequest(tools, fixture, output))
        check(Files.readAllBytes(fixture).contentEquals(originalBytes)) { "Owned input fixture changed during probe" }
        check(result.inputMetadata.videoCodec == "h264") { "Fixture codec was ${result.inputMetadata.videoCodec}, expected h264" }
        check(result.inputMetadata.width == 320 && result.inputMetadata.height == 180) { "Fixture must be 320x180" }
        check(abs(result.inputMetadata.durationSeconds - 3.0) <= 0.05) { "Fixture duration was ${result.inputMetadata.durationSeconds}, expected 3 seconds" }
        check(abs(result.inputMetadata.frameRate - 24.0) <= 0.01) { "Fixture cadence was ${result.inputMetadata.frameRate}, expected 24 fps" }
        check(result.inputMetadata.decodedFrameCount == 72L) { "Fixture decoded ${result.inputMetadata.decodedFrameCount} frames, expected 72" }
        check(result.inputMetadata.audioStreamCount == 0) { "Fixture must be silent" }
        val firstImage = checkNotNull(ImageIO.read(result.firstFrame.toFile())) { "First frame is not a decodable PNG" }
        val seekImage = checkNotNull(ImageIO.read(result.seekFrame.toFile())) { "Seek frame is not a decodable PNG" }
        check(firstImage.width == 320 && firstImage.height == 180) { "First decoded frame must be 320x180" }
        check(seekImage.width == 320 && seekImage.height == 180) { "Seeked decoded frame must be 320x180" }
        val firstOrangeX = orangeCentroidX(firstImage)
        val seekOrangeX = orangeCentroidX(seekImage)
        check(abs(firstOrangeX - seekOrangeX) >= 20.0) {
            "Decoded seek did not expose the authored horizontal motion: first x=$firstOrangeX, seek x=$seekOrangeX"
        }
        check(result.encodedMetadata.videoCodec == "h264") { "Encoded codec was ${result.encodedMetadata.videoCodec}, expected h264" }
        check(result.encodedMetadata.width == 320 && result.encodedMetadata.height == 180) { "Encoded output must remain 320x180" }
        check(abs(result.encodedMetadata.durationSeconds - 3.0) <= 0.1) { "Encoded duration was ${result.encodedMetadata.durationSeconds}, expected 3 seconds" }
        check(abs(result.encodedMetadata.frameRate - 24.0) <= 0.01) { "Encoded cadence was ${result.encodedMetadata.frameRate}, expected 24 fps" }
        check(result.encodedMetadata.decodedFrameCount == 72L) { "Encoded output decoded ${result.encodedMetadata.decodedFrameCount} frames, expected 72" }
        check(result.encodedMetadata.audioStreamCount == 0) { "Encoded output must contain no audio stream" }

        println("VIDEO_MEDIA_PROBE_PASSED")
        println("report=${result.report}")
        println("firstFrame=${result.firstFrame}")
        println("seekFrame=${result.seekFrame}")
        println("encodedVideo=${result.encodedVideo}")
    }

    private fun defaultOutputDirectory(): Path {
        val parent = Path.of(System.getProperty("user.dir"), "build", "video", "media-probe").toAbsolutePath()
        Files.createDirectories(parent)
        val timestamp = Instant.now().toString().replace(':', '-').replace('.', '-')
        return parent.resolve("$timestamp-${UUID.randomUUID()}")
    }

    private fun orangeCentroidX(image: BufferedImage): Double {
        var count = 0L
        var sum = 0L
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val red = rgb ushr 16 and 0xff
                val green = rgb ushr 8 and 0xff
                val blue = rgb and 0xff
                if (red >= 160 && green >= 55 && blue <= 100 && red - blue >= 80) {
                    count += 1
                    sum += x
                }
            }
        }
        check(count >= 100) { "Decoded frame does not contain the authored orange motion block" }
        return sum.toDouble() / count
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val FIXTURE_SHA256 = "bc474eec8f0de89ed765aac902d0681b016ab9f59e8219e63c1404d42e1b3d35"
}

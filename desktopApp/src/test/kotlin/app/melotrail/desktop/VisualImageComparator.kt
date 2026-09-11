package app.melotrail.desktop

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Exact ARGB comparison under the pinned renderer, including text and transparent pixels.
 * No masks, alignment, resizing, percentage allowance or baseline-writing mode.
 */
internal object VisualImageComparator {
    fun compare(actual: BufferedImage, expected: BufferedImage, output: Path): Int {
        Files.createDirectories(output)
        write(actual, output.resolve("actual.png"))
        write(expected, output.resolve("expected.png"))
        val width = maxOf(actual.width, expected.width)
        val height = maxOf(actual.height, expected.height)
        val diff = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        var failures = 0
        for (y in 0 until height) for (x in 0 until width) {
            val outside = x >= actual.width || y >= actual.height || x >= expected.width || y >= expected.height
            val mismatch = outside || actual.getRGB(x, y) != expected.getRGB(x, y)
            if (mismatch) failures++
            diff.setRGB(x, y, if (mismatch) 0xffff00ff.toInt() else 0xff000000.toInt())
        }
        write(diff, output.resolve("diff.png"))
        Files.writeString(output.resolve("comparison.txt"),
            "actual=${actual.width}x${actual.height}\nexpected=${expected.width}x${expected.height}\nchangedPixels=$failures\n")
        return failures
    }

    fun readExpected(resource: String): BufferedImage =
        checkNotNull(javaClass.getResourceAsStream(resource)) {
            "Missing pinned baseline $resource; tests never create or approve goldens"
        }.use { checkNotNull(ImageIO.read(it)) { "Unreadable baseline $resource" } }

    fun assertMatches(actual: BufferedImage, resource: String, output: Path) {
        // Keep the actual capture even when a required baseline is missing or corrupt.
        Files.createDirectories(output)
        write(actual, output.resolve("actual.png"))
        val failures = compare(actual, readExpected(resource), output)
        check(failures == 0) { "$failures changed pixels; inspect $output/{actual,expected,diff}.png" }
    }

    private fun write(image: BufferedImage, path: Path) {
        check(ImageIO.write(image, "png", path.toFile())) { "No PNG writer for $path" }
    }
}

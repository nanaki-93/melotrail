package app.melotrail.desktop

import java.awt.Color
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VisualImageComparatorTest {
    private val output = Path.of("build/test-results/visual-comparator")

    @Test fun `comparator rejects shifted real panels wrong primary fill pill corners and removed real lanes`() {
        val shell = VisualImageComparator.readExpected("/visual/shell/1536-arrange-populated.png")
        val primary = VisualImageComparator.readExpected("/visual/primary-unfocused.png")
        assertEquals(0, VisualImageComparator.compare(copy(shell), shell, output.resolve("identical")))
        val shifted = copy(shell).apply {
            createGraphics().also { g ->
                try {
                    // Move the actual song-map panel twelve pixels, including its glyphs.
                    g.color = Color(0x0b131e)
                    g.fillRect(260, 532, 920, 96)
                    g.drawImage(shell.getSubimage(260, 532, 908, 96), 272, 532, null)
                } finally { g.dispose() }
            }
        }
        val wrongColor = copy(primary).apply {
            for (y in 0 until height) for (x in 0 until width) {
                if (getRGB(x, y) == 0xff594080.toInt()) setRGB(x, y, 0xffb18ade.toInt())
            }
        }
        val pill = copy(primary).apply {
            createGraphics().also { g ->
                try {
                    g.color = Color(0x0b131e)
                    g.fillRect(16, 16, 128, 48)
                    g.clip = RoundRectangle2D.Float(16f, 16f, 128f, 48f, 48f, 48f)
                    g.drawImage(primary, 0, 0, null)
                } finally { g.dispose() }
            }
        }
        val missingLane = copy(shell).apply {
            createGraphics().also { g ->
                try { g.color = Color(0x151e2a); g.fillRect(348, 452, 808, 52) }
                finally { g.dispose() }
            }
        }
        listOf(Triple("panel-shift-12", shifted, shell), Triple("primary-color", wrongColor, primary),
            Triple("pill-radius", pill, primary), Triple("missing-lane", missingLane, shell)).forEach { (name, actual, expected) ->
            assertTrue(VisualImageComparator.compare(actual, expected, output.resolve(name)) > 0, name)
            val saved = ImageIO.read(output.resolve("$name/diff.png").toFile())
            assertTrue((0 until saved.height).any { y ->
                (0 until saved.width).any { x -> saved.getRGB(x, y) == 0xffff00ff.toInt() }
            }, "The failure artifact must show changed pixels")
        }
    }

    @Test fun `single pixels alpha and dimension changes cannot hide in any font allowance`() {
        val expected = VisualImageComparator.readExpected("/visual/primary-unfocused.png")
        listOf(0xff594081.toInt(), 0x7f594080, 0).forEachIndexed { index, color ->
            val actual = copy(expected).apply { setRGB(20, 40, color) }
            assertEquals(1, VisualImageComparator.compare(actual, expected, output.resolve("one-pixel-$index")))
        }
        listOf(159, 161).forEach { width ->
            assertTrue(VisualImageComparator.compare(BufferedImage(width, 80, BufferedImage.TYPE_INT_ARGB),
                expected, output.resolve("width-$width")) > 0)
        }
        val transparent = BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB)
        assertTrue(VisualImageComparator.compare(expected, transparent, output.resolve("transparent")) > 0,
            "Transparent expected pixels are compared, not treated as exclusions")
    }

    @Test fun `missing baseline fails without creating an expected image`() {
        val folder = Files.createTempDirectory("u07a-missing-baseline-")
        try {
            assertFailsWith<IllegalStateException> {
                VisualImageComparator.assertMatches(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB),
                    "/visual/does-not-exist.png", folder)
            }
            assertTrue(Files.isRegularFile(folder.resolve("actual.png")))
            assertTrue(!Files.exists(folder.resolve("expected.png")))
        } finally {
            Files.deleteIfExists(folder.resolve("actual.png"))
            Files.delete(folder)
        }
    }

    private fun copy(source: BufferedImage) = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB).apply {
        setRGB(0, 0, width, height, source.getRGB(0, 0, width, height, null, 0, width), 0, width)
    }
}

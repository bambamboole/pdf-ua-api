package bambamboole.pdfua.image

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageOptimizerTest {
    @Test
    fun nonImageBytesPassThrough() {
        val css = "body { color: red; }".toByteArray()
        val result = ImageOptimizer.optimizeImage(css)
        assertTrue(css.contentEquals(result))
    }

    @Test
    fun emptyBytesPassThrough() {
        val empty = ByteArray(0)
        val result = ImageOptimizer.optimizeImage(empty)
        assertTrue(empty.contentEquals(result))
    }

    @Test
    fun smallJpegPassesThrough() {
        val jpeg = createJpeg(100, 100)
        val result = ImageOptimizer.optimizeImage(jpeg)
        val resultImage = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(100, resultImage.width)
        assertEquals(100, resultImage.height)
    }

    @Test
    fun largeJpegIsDownscaled() {
        val jpeg = createJpeg(3000, 2000)
        val result = ImageOptimizer.optimizeImage(jpeg)
        val resultImage = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(1240, resultImage.width)
        assertEquals(826, resultImage.height)
    }

    @Test
    fun largePngIsDownscaled() {
        val png = createPng(2000, 1500, hasAlpha = false)
        val result = ImageOptimizer.optimizeImage(png)
        val resultImage = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(1240, resultImage.width)
        assertEquals(930, resultImage.height)
    }

    @Test
    fun pngAlphaPreserved() {
        val png = createPng(2000, 1000, hasAlpha = true)
        val result = ImageOptimizer.optimizeImage(png)
        val resultImage = ImageIO.read(ByteArrayInputStream(result))
        assertTrue(resultImage.colorModel.hasAlpha())
    }

    @Test
    fun tallNarrowImagePassesThrough() {
        val jpeg = createJpeg(500, 3000)
        val result = ImageOptimizer.optimizeImage(jpeg)
        val resultImage = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(500, resultImage.width)
        assertEquals(3000, resultImage.height)
    }

    @Test
    fun jpegOutputIsSmallerThanInput() {
        val jpeg = createJpeg(4000, 3000)
        val result = ImageOptimizer.optimizeImage(jpeg)
        assertTrue(result.size < jpeg.size, "Optimized JPEG should be smaller")
    }

    @Test
    fun corruptBytesReturnedAsIs() {
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x01, 0x02)
        val result = ImageOptimizer.optimizeImage(corrupt)
        assertTrue(corrupt.contentEquals(result))
    }

    @Test
    fun detectFormatRecognizesJpeg() {
        val jpeg = createJpeg(10, 10)
        assertEquals("jpg", ImageOptimizer.detectFormat(jpeg))
    }

    @Test
    fun detectFormatRecognizesPng() {
        val png = createPng(10, 10, hasAlpha = false)
        assertEquals("png", ImageOptimizer.detectFormat(png))
    }

    @Test
    fun detectFormatReturnsNullForUnknown() {
        assertNull(ImageOptimizer.detectFormat("hello".toByteArray()))
    }

    @Test
    fun detectFormatRecognizesWebp() {
        val webp = loadFixture("opaque-100.webp")
        assertEquals("webp", ImageOptimizer.detectFormat(webp))
    }

    @Test
    fun webpWithoutAlphaIsTranscodedToJpeg() {
        val webp = loadFixture("opaque-100.webp")
        val result = ImageOptimizer.optimizeImage(webp)
        assertEquals(0xFF.toByte(), result[0])
        assertEquals(0xD8.toByte(), result[1])
        assertEquals(0xFF.toByte(), result[2])
    }

    @Test
    fun webpWithAlphaIsTranscodedToPng() {
        val webp = loadFixture("alpha-100.webp")
        val result = ImageOptimizer.optimizeImage(webp)
        val pngSignature =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            )
        assertTrue(
            result.copyOfRange(0, 8).contentEquals(pngSignature),
            "WebP with alpha should be transcoded to PNG",
        )
    }

    @Test
    fun wideWebpIsResizedAndTranscoded() {
        val webp = loadFixture("opaque-2000x1000.webp")
        val result = ImageOptimizer.optimizeImage(webp)
        val resultImage = ImageIO.read(ByteArrayInputStream(result))
        assertEquals(1240, resultImage.width)
        assertEquals(620, resultImage.height)
    }

    private fun loadFixture(name: String): ByteArray =
        ImageOptimizerTest::class.java
            .getResource("/fixtures/webp/$name")
            ?.readBytes()
            ?: error("Missing fixture: /fixtures/webp/$name")

    private fun createJpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.BLUE
        g.fillRect(0, 0, width, height)
        g.dispose()
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return output.toByteArray()
    }

    private fun createPng(
        width: Int,
        height: Int,
        hasAlpha: Boolean,
    ): ByteArray {
        val type = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val image = BufferedImage(width, height, type)
        val g = image.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, width, height)
        g.dispose()
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }
}

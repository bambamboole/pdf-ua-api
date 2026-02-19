package bambamboole.pdf.api.services

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

    private fun createJpeg(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.BLUE
        g.fillRect(0, 0, width, height)
        g.dispose()
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", output)
        return output.toByteArray()
    }

    private fun createPng(width: Int, height: Int, hasAlpha: Boolean): ByteArray {
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

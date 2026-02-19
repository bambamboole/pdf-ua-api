package bambamboole.pdf.api.services

import org.slf4j.LoggerFactory
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ImageOptimizer {

    private val logger = LoggerFactory.getLogger(ImageOptimizer::class.java)

    private const val MAX_WIDTH_PX = 1240
    private const val JPEG_QUALITY = 0.85f

    fun optimizeImage(bytes: ByteArray): ByteArray {
        val format = detectFormat(bytes) ?: return bytes

        return try {
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return bytes
            if (image.width <= MAX_WIDTH_PX) return bytes

            val resized = resize(image, MAX_WIDTH_PX)
            val encoded = encode(resized, format)

            logger.debug(
                "Optimized {} image: {}x{} -> {}x{}, {} -> {} bytes",
                format, image.width, image.height, resized.width, resized.height,
                bytes.size, encoded.size
            )
            encoded
        } catch (e: Exception) {
            logger.warn("Image optimization failed, using original: {}", e.message)
            bytes
        }
    }

    internal fun detectFormat(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "jpg"
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "png"
            else -> null
        }
    }

    private fun resize(image: BufferedImage, maxWidth: Int): BufferedImage {
        val ratio = maxWidth.toDouble() / image.width
        val newHeight = (image.height * ratio).toInt()

        val imageType = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val scaled = image.getScaledInstance(maxWidth, newHeight, Image.SCALE_SMOOTH)

        val result = BufferedImage(maxWidth, newHeight, imageType)
        val g = result.createGraphics()
        g.drawImage(scaled, 0, 0, null)
        g.dispose()
        return result
    }

    private fun encode(image: BufferedImage, format: String): ByteArray {
        val output = ByteArrayOutputStream(64 * 1024)

        if (format == "jpg") {
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = JPEG_QUALITY
            }
            writer.output = ImageIO.createImageOutputStream(output)
            writer.write(null, IIOImage(image, null, null), param)
            writer.dispose()
        } else {
            ImageIO.write(image, format, output)
        }

        return output.toByteArray()
    }
}

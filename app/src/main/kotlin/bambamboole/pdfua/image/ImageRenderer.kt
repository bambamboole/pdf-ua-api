package bambamboole.pdfua.image

import bambamboole.pdfua.fonts.BundledFonts
import com.openhtmltopdf.extend.FSStreamFactory
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.java2d.api.BufferedImagePageProcessor
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle as RendererFontStyle
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

object ImageRenderer {
    private val logger = LoggerFactory.getLogger(ImageRenderer::class.java)
    private val w3cDom = W3CDom()

    private fun parseAndInjectViewportWidth(html: String, width: Int): org.w3c.dom.Document {
        val jsoupDoc = Jsoup.parse(html)
        jsoupDoc.head().prependElement("style").attr("type", "text/css")
            .text("html { font-family: 'Liberation Sans'; }")
        val style = "@page { size: ${width}px 1px; margin: 0; }"
        jsoupDoc.head().appendElement("style").attr("type", "text/css").text(style)
        return w3cDom.fromJsoup(jsoupDoc)
    }

    fun warmup() {
        logger.info("Warming up ImageRenderer...")
        BundledFonts.fontBytes
        logger.info("ImageRenderer warmup complete")
    }

    fun renderHtmlToImage(
        html: String,
        format: String = "png",
        width: Int = 800,
        assetResolver: FSStreamFactory? = null,
        baseUrl: String = ""
    ): ByteArray {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }

        val imageType = if (format == "jpg") BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        val pageProcessor = BufferedImagePageProcessor(imageType, 2.0)

        val builder = Java2DRendererBuilder()
        builder.useFastMode()
        builder.useEnvironmentFonts(false)

        BundledFonts.fontBytesForHtml(html).forEach { (config, bytes) ->
            val fontSupplier = FSSupplier<InputStream> { ByteArrayInputStream(bytes) }
            builder.useFont(fontSupplier, config.family, config.weight, config.style.toRendererStyle(), true)
        }

        if (assetResolver != null) {
            builder.useHttpStreamImplementation(assetResolver)
        }

        val w3cDoc = parseAndInjectViewportWidth(html, width)
        builder.withW3cDocument(w3cDoc, baseUrl)
        builder.toSinglePage(pageProcessor)
        builder.runFirstPage()

        val pages = pageProcessor.pageImages
        if (pages.isEmpty()) throw IllegalStateException("No image was rendered")
        val image = pages.first()

        if (format == "jpg") {
            val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            val g = rgb.createGraphics()
            g.color = Color.WHITE
            g.fillRect(0, 0, rgb.width, rgb.height)
            g.drawImage(image, 0, 0, null)
            g.dispose()
            return ByteArrayOutputStream().use { out ->
                ImageIO.write(rgb, "jpg", out)
                out.toByteArray()
            }
        }

        return ByteArrayOutputStream().use { out ->
            ImageIO.write(image, format, out)
            out.toByteArray()
        }
    }

    private fun BundledFonts.FontStyle.toRendererStyle(): RendererFontStyle =
        when (this) {
            BundledFonts.FontStyle.Normal -> RendererFontStyle.NORMAL
            BundledFonts.FontStyle.Italic -> RendererFontStyle.ITALIC
        }
}

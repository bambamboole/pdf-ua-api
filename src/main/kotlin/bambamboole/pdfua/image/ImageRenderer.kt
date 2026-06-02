package bambamboole.pdfua.image

import bambamboole.pdfua.fonts.BundledFonts
import bambamboole.pdfua.fonts.useBundledFontsFor
import com.openhtmltopdf.extend.FSStreamFactory
import com.openhtmltopdf.java2d.api.BufferedImagePageProcessor
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageRenderer {
    private val logger = LoggerFactory.getLogger(ImageRenderer::class.java)
    private val w3cDom = W3CDom()

    private fun parseAndInjectViewportWidth(
        html: String,
        width: Int,
    ): org.w3c.dom.Document {
        val jsoupDoc = Jsoup.parse(html)
        jsoupDoc
            .head()
            .prependElement("style")
            .attr("type", "text/css")
            .text("html { font-family: 'Liberation Sans'; }")
        val style = "@page { size: ${width}px 1px; margin: 0; }"
        jsoupDoc
            .head()
            .appendElement("style")
            .attr("type", "text/css")
            .text(style)
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
        baseUrl: String = "",
    ): ByteArray {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }

        val imageType = if (format == "jpg") BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        val pageProcessor = BufferedImagePageProcessor(imageType, 2.0)

        val builder = Java2DRendererBuilder()
        @Suppress("DEPRECATION") // upstream OpenHTMLToPDF still recommends useFastMode for Java2D rendering
        builder.useFastMode()
        builder.useEnvironmentFonts(false)

        builder.useBundledFontsFor(html)

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

        // JPEG has no alpha channel, so flatten transparency onto white before encoding.
        val target = if (format == "jpg") flattenOntoWhite(image) else image
        return ByteArrayOutputStream().use { out ->
            ImageIO.write(target, format, out)
            out.toByteArray()
        }
    }

    private fun flattenOntoWhite(image: BufferedImage): BufferedImage {
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g = rgb.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, rgb.width, rgb.height)
        g.drawImage(image, 0, 0, null)
        g.dispose()
        return rgb
    }
}

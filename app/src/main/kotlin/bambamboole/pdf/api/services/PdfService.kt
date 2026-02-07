package bambamboole.pdf.api.services

import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

object PdfService {
    private val logger = LoggerFactory.getLogger(PdfService::class.java)

    private data class FontConfig(
        val path: String,
        val family: String,
        val weight: Int,
        val style: FontStyle
    )

    private val fontConfigs = listOf(
        FontConfig("/fonts/LiberationSans-Regular.ttf", "Liberation Sans", 400, FontStyle.NORMAL),
        FontConfig("/fonts/LiberationSans-Bold.ttf", "Liberation Sans", 700, FontStyle.NORMAL),
        FontConfig("/fonts/LiberationSans-Italic.ttf", "Liberation Sans", 400, FontStyle.ITALIC),
        FontConfig("/fonts/LiberationSans-BoldItalic.ttf", "Liberation Sans", 700, FontStyle.ITALIC),
        FontConfig("/fonts/LiberationSerif-Regular.ttf", "Liberation Serif", 400, FontStyle.NORMAL),
        FontConfig("/fonts/LiberationSerif-Bold.ttf", "Liberation Serif", 700, FontStyle.NORMAL),
        FontConfig("/fonts/LiberationSerif-Italic.ttf", "Liberation Serif", 400, FontStyle.ITALIC),
        FontConfig("/fonts/LiberationSerif-BoldItalic.ttf", "Liberation Serif", 700, FontStyle.ITALIC),
        FontConfig("/fonts/LiberationMono-Regular.ttf", "Liberation Mono", 400, FontStyle.NORMAL),
        FontConfig("/fonts/LiberationMono-Bold.ttf", "Liberation Mono", 700, FontStyle.NORMAL),
        FontConfig("/fonts/LiberationMono-Italic.ttf", "Liberation Mono", 400, FontStyle.ITALIC),
        FontConfig("/fonts/LiberationMono-BoldItalic.ttf", "Liberation Mono", 700, FontStyle.ITALIC)
    )

    private val colorProfileBytes: ByteArray by lazy {
        logger.info("Loading sRGB color profile")
        loadResource("/colorspaces/sRGB.icc")
            ?: throw IllegalStateException("sRGB.icc color profile not found in resources")
    }

    private val fontByteArrays: Map<FontConfig, ByteArray> by lazy {
        logger.info("Loading ${fontConfigs.size} font files")
        fontConfigs.associateWith { config ->
            loadResource(config.path)
                ?: throw IllegalStateException("Font not found: ${config.path}")
        }.also {
            val totalSize = it.values.sumOf { bytes -> bytes.size }
            logger.info("Successfully cached ${it.size} fonts (${totalSize / 1024} KB total)")
        }
    }

    private fun loadResource(path: String): ByteArray? =
        PdfService::class.java.getResourceAsStream(path)?.use { it.readBytes() }

    /**
     * Converts HTML string to PDF bytes with PDF/UA accessibility compliance
     * @param html Well-formed HTML string
     * @return PDF as byte array
     * @throws Exception if HTML is malformed or conversion fails
     */
    fun convertHtmlToPdf(html: String): ByteArray {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }

        val jsoupDoc = Jsoup.parse(html)
        injectTablePaginationStyles(jsoupDoc)
        val w3cDoc = W3CDom().fromJsoup(jsoupDoc)

        return ByteArrayOutputStream(512 * 1024).use { outputStream ->
            val builder = PdfRendererBuilder()
            configurePdfUA(builder)
            builder.withProducer("pdf-ua-api.com")
            builder.withW3cDocument(w3cDoc, "file:///")
            builder.toStream(outputStream)
            builder.run()
            outputStream.toByteArray()
        }
    }

    /**
     * Injects CSS styles for proper table pagination across pages.
     * This ensures tables are split correctly when spanning multiple pages.
     */
    private fun injectTablePaginationStyles(jsoupDoc: org.jsoup.nodes.Document) {
        val styles = """
            table {
                width: 100%;
                border-collapse: collapse;

                /* The magical table pagination property. */
                -fs-table-paginate: paginate;

                /* Recommended to avoid leaving thead on a page by itself. */
                -fs-page-break-min-height: 1.5cm;
            }

            tr, thead, tfoot {
                page-break-inside: avoid;
            }
        """.trimIndent()

        // Check if a style tag already exists, if not create one
        val head = jsoupDoc.head()
        val styleElement = head.appendElement("style")
        styleElement.attr("type", "text/css")
        styleElement.text(styles)

        logger.debug("Injected table pagination styles into HTML document")
    }

    private fun configurePdfUA(builder: PdfRendererBuilder) {
        builder.useColorProfile(colorProfileBytes)

        fontByteArrays.forEach { (config, bytes) ->
            val fontSupplier = FSSupplier<InputStream> { ByteArrayInputStream(bytes) }
            builder.useFont(
                fontSupplier,
                config.family,
                config.weight,
                config.style,
                true,
                EnumSet.of(FSFontUseCase.FALLBACK_FINAL)
            )
        }

        builder.usePdfUaAccessibility(true)
        builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)
    }
}

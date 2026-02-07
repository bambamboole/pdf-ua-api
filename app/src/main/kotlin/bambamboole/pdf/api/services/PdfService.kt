package bambamboole.pdf.api.services

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.EnumSet

object PdfService {
    private val logger = LoggerFactory.getLogger(PdfService::class.java)

    /**
     * Converts HTML string to PDF bytes with PDF/UA accessibility compliance
     * @param html Well-formed XHTML string
     * @return PDF as byte array
     * @throws Exception if HTML is malformed or conversion fails
     */
    fun convertHtmlToPdf(html: String): ByteArray {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }

        return ByteArrayOutputStream().use { outputStream ->
            val builder = PdfRendererBuilder()

            // Always configure PDF/UA accessibility compliance
            configurePdfUA(builder)

            // Set producer to pdf-ua-api.com
            builder.withProducer("pdf-ua-api.com")

            builder.withHtmlContent(html, "file:///")  // Base URL required for resolving relative resources
            builder.toStream(outputStream)
            builder.run()
            outputStream.toByteArray()
        }
    }

    /**
     * Configure PdfRendererBuilder for PDF/UA accessibility compliance
     */
    private fun configurePdfUA(builder: PdfRendererBuilder) {
        logger.info("Configuring PDF/UA accessibility compliance")

        // Add fonts for PDF/UA compliance with FALLBACK configuration
        // This ensures fonts are used even when HTML doesn't specify font-family
        addFallbackFonts(builder)

        // Enable PDF/UA accessibility
        builder.usePdfUaAccessibility(true)

        // Use PDF/A-3a for full accessibility compliance
        // PDF/A ensures long-term preservation and accessibility
        builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)

        // Set color profile for PDF/A compliance
        // Required to prevent DeviceRGB color space validation errors
        setColorProfile(builder)

        logger.info("PDF/UA configuration complete")
    }

    /**
     * Load and set sRGB color profile for PDF/A compliance
     */
    private fun setColorProfile(builder: PdfRendererBuilder) {
        try {
            val colorProfileStream = PdfService::class.java.getResourceAsStream("/colorspaces/sRGB.icc")
                ?: throw IllegalStateException("sRGB.icc color profile not found in resources")

            colorProfileStream.use { stream ->
                val colorProfileBytes = stream.readBytes()
                builder.useColorProfile(colorProfileBytes)
                logger.debug("Loaded sRGB color profile (${colorProfileBytes.size} bytes)")
            }
        } catch (e: Exception) {
            logger.error("Failed to load color profile: ${e.message}", e)
            throw IllegalStateException("PDF/A requires a color profile, but it could not be loaded", e)
        }
    }

    /**
     * Load and add fonts from resources as FALLBACK fonts for PDF/UA compliance.
     * FALLBACK fonts are used even when HTML doesn't specify font-family in CSS.
     * Built-in PDF fonts are prohibited in PDF/UA standard.
     */
    private fun addFallbackFonts(builder: PdfRendererBuilder) {
        data class FontConfig(
            val path: String,
            val family: String,
            val weight: Int,
            val style: FontStyle
        )

        // Liberation fonts bundled with the application
        // These are metrically compatible with Arial, Times New Roman, and Courier New
        // All variants (Regular, Bold, Italic, BoldItalic) for complete CSS font matching
        val fonts = listOf(
            // Liberation Sans (Arial replacement)
            FontConfig("/fonts/LiberationSans-Regular.ttf", "Liberation Sans", 400, FontStyle.NORMAL),
            FontConfig("/fonts/LiberationSans-Bold.ttf", "Liberation Sans", 700, FontStyle.NORMAL),
            FontConfig("/fonts/LiberationSans-Italic.ttf", "Liberation Sans", 400, FontStyle.ITALIC),
            FontConfig("/fonts/LiberationSans-BoldItalic.ttf", "Liberation Sans", 700, FontStyle.ITALIC),

            // Liberation Serif (Times New Roman replacement)
            FontConfig("/fonts/LiberationSerif-Regular.ttf", "Liberation Serif", 400, FontStyle.NORMAL),
            FontConfig("/fonts/LiberationSerif-Bold.ttf", "Liberation Serif", 700, FontStyle.NORMAL),
            FontConfig("/fonts/LiberationSerif-Italic.ttf", "Liberation Serif", 400, FontStyle.ITALIC),
            FontConfig("/fonts/LiberationSerif-BoldItalic.ttf", "Liberation Serif", 700, FontStyle.ITALIC),

            // Liberation Mono (Courier New replacement)
            FontConfig("/fonts/LiberationMono-Regular.ttf", "Liberation Mono", 400, FontStyle.NORMAL),
            FontConfig("/fonts/LiberationMono-Bold.ttf", "Liberation Mono", 700, FontStyle.NORMAL),
            FontConfig("/fonts/LiberationMono-Italic.ttf", "Liberation Mono", 400, FontStyle.ITALIC),
            FontConfig("/fonts/LiberationMono-BoldItalic.ttf", "Liberation Mono", 700, FontStyle.ITALIC)
        )

        val fontsAdded = mutableListOf<String>()

        fonts.forEach { font ->
            try {
                val fontStream = PdfService::class.java.getResourceAsStream(font.path)
                    ?: throw IllegalStateException("Font not found: ${font.path}")

                fontStream.use { stream ->
                    val fontBytes = stream.readBytes()

                    // Create font supplier that loads from byte array as InputStream
                    val fontSupplier = FSSupplier<InputStream> { ByteArrayInputStream(fontBytes) }

                    // Register font as FALLBACK_FINAL - will be used even without font-family in CSS
                    // PDF/A disables built-in fonts, so we need fallback fonts to render any text
                    builder.useFont(
                        fontSupplier,
                        font.family,
                        font.weight,
                        font.style,
                        true,
                        EnumSet.of(FSFontUseCase.FALLBACK_FINAL)
                    )

                    val styleDesc = when (font.style) {
                        FontStyle.NORMAL -> if (font.weight == 700) "Bold" else "Regular"
                        FontStyle.ITALIC -> if (font.weight == 700) "Bold Italic" else "Italic"
                        else -> "Unknown"
                    }
                    fontsAdded.add("${font.family} $styleDesc")
                    logger.debug("Loaded fallback font: ${font.family} $styleDesc (weight=${font.weight}, ${fontBytes.size} bytes)")
                }
            } catch (e: Exception) {
                logger.error("Failed to load font ${font.family} (${font.weight}, ${font.style}): ${e.message}", e)
                throw IllegalStateException("Failed to load required font: ${font.family}", e)
            }
        }

        logger.info("Successfully loaded ${fontsAdded.size} fallback font variants: ${fontsAdded.joinToString(", ")}")
    }
}

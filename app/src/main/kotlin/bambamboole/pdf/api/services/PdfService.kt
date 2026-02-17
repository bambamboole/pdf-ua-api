package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.FileAttachment
import com.openhtmltopdf.extend.FSStreamFactory
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*

object PdfService {
    private val logger = LoggerFactory.getLogger(PdfService::class.java)
    private val w3cDom = W3CDom()
    private val validRelationships = setOf("Source", "Data", "Alternative", "Supplement", "Unspecified")

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

    fun warmup() {
        logger.info("Warming up PdfService...")
        colorProfileBytes
        fontByteArrays
        logger.info("PdfService warmup complete")
    }

    fun convertHtmlToPdf(
        html: String,
        producer: String = "pdf-ua-api.com",
        assetResolver: FSStreamFactory? = null,
        baseUrl: String = "",
        attachments: List<FileAttachment>? = null
    ): ByteArray {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }
        if (!attachments.isNullOrEmpty()) {
            validateAttachments(attachments)
        }

        val jsoupDoc = Jsoup.parse(html)
        injectTablePaginationStyles(jsoupDoc)
        val w3cDoc = w3cDom.fromJsoup(jsoupDoc)

        val pdfBytes = ByteArrayOutputStream(512 * 1024).use { outputStream ->
            val builder = PdfRendererBuilder()
            configurePdfUA(builder)
            builder.withProducer(producer)
            if (assetResolver != null) {
                builder.useHttpStreamImplementation(assetResolver)
            }
            builder.withW3cDocument(w3cDoc, baseUrl)
            builder.toStream(outputStream)
            builder.run()
            outputStream.toByteArray()
        }

        return if (attachments.isNullOrEmpty()) pdfBytes else addAttachments(pdfBytes, attachments)
    }

    private fun validateAttachments(attachments: List<FileAttachment>) {
        require(attachments.size <= 10) { "Maximum 10 attachments allowed" }
        for (attachment in attachments) {
            require(attachment.name.isNotBlank()) { "Attachment name cannot be blank" }
            require(attachment.name.length <= 255) { "Attachment name too long: ${attachment.name}" }
            require(attachment.content.isNotBlank()) { "Attachment content cannot be blank" }
            require(attachment.relationship in validRelationships) {
                "Invalid relationship '${attachment.relationship}', must be one of: $validRelationships"
            }
            try {
                val decoded = Base64.getDecoder().decode(attachment.content)
                require(decoded.size <= 10 * 1024 * 1024) {
                    "Attachment '${attachment.name}' exceeds 10MB limit"
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("limit") == true) throw e
                throw IllegalArgumentException("Attachment '${attachment.name}' has invalid base64 content")
            }
        }
    }

    private fun addAttachments(pdfBytes: ByteArray, attachments: List<FileAttachment>): ByteArray {
        Loader.loadPDF(pdfBytes).use { document ->
            val embeddedFilesMap = mutableMapOf<String, PDComplexFileSpecification>()
            val afArray = COSArray()

            for (attachment in attachments) {
                val decodedBytes = Base64.getDecoder().decode(attachment.content)

                val embeddedFile = PDEmbeddedFile(document, ByteArrayInputStream(decodedBytes))
                embeddedFile.subtype = attachment.mimeType
                embeddedFile.size = decodedBytes.size
                embeddedFile.creationDate = Calendar.getInstance()
                embeddedFile.modDate = Calendar.getInstance()

                val fileSpec = PDComplexFileSpecification()
                fileSpec.file = attachment.name
                fileSpec.fileUnicode = attachment.name
                fileSpec.embeddedFile = embeddedFile
                fileSpec.embeddedFileUnicode = embeddedFile
                if (attachment.description != null) {
                    fileSpec.fileDescription = attachment.description
                }
                fileSpec.cosObject.setName(COSName.AF_RELATIONSHIP, attachment.relationship)

                embeddedFilesMap[attachment.name] = fileSpec
                afArray.add(fileSpec)
            }

            val efTree = PDEmbeddedFilesNameTreeNode()
            efTree.names = embeddedFilesMap

            val names = PDDocumentNameDictionary(document.documentCatalog)
            names.embeddedFiles = efTree
            document.documentCatalog.names = names
            document.documentCatalog.cosObject.setItem(COSName.AF, afArray)

            logger.info("Added ${attachments.size} attachment(s) to PDF")

            return ByteArrayOutputStream(pdfBytes.size + pdfBytes.size / 4).use { outputStream ->
                document.save(outputStream)
                outputStream.toByteArray()
            }
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

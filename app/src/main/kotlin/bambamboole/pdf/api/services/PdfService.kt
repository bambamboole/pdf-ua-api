package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.FileAttachment
import com.openhtmltopdf.extend.FSStreamFactory
import com.openhtmltopdf.extend.FSSupplier
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FSFontUseCase
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle as RendererFontStyle
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

    private val colorProfileBytes: ByteArray by lazy {
        logger.info("Loading sRGB color profile")
        loadResource("/colorspaces/sRGB.icc")
            ?: throw IllegalStateException("sRGB.icc color profile not found in resources")
    }

    private fun loadResource(path: String): ByteArray? =
        PdfService::class.java.getResourceAsStream(path)?.use { it.readBytes() }

    fun warmup() {
        logger.info("Warming up PdfService...")
        colorProfileBytes
        BundledFonts.fontBytes
        logger.info("PdfService warmup complete")
    }

    fun convertHtmlToPdf(
        html: String,
        producer: String = "pdf-ua-api.com",
        assetResolver: FSStreamFactory? = null,
        baseUrl: String = "",
        attachments: List<FileAttachment>? = null
    ): PdfResult {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }
        if (!attachments.isNullOrEmpty()) {
            validateAttachments(attachments)
        }

        val jsoupDoc = Jsoup.parse(html)
        val w3cDoc = w3cDom.fromJsoup(jsoupDoc)

        val pdfBytes = ByteArrayOutputStream(512 * 1024).use { outputStream ->
            val builder = PdfRendererBuilder()
            configurePdfUA(builder, html)
            builder.withProducer(producer)
            if (assetResolver != null) {
                builder.useHttpStreamImplementation(assetResolver)
            }
            builder.withW3cDocument(w3cDoc, baseUrl)
            builder.toStream(outputStream)
            builder.run()
            outputStream.toByteArray()
        }

        val finalBytes = if (attachments.isNullOrEmpty()) pdfBytes else addAttachments(pdfBytes, attachments)
        return embedDocumentId(finalBytes)
    }

    private fun embedDocumentId(pdfBytes: ByteArray): PdfResult {
        val documentId = UUID.randomUUID().toString()
        Loader.loadPDF(pdfBytes).use { document ->
            document.documentInformation.setCustomMetadataValue("X-Document-UUID", documentId)
            return ByteArrayOutputStream(pdfBytes.size + 256).use { outputStream ->
                document.save(outputStream)
                PdfResult(outputStream.toByteArray(), documentId)
            }
        }
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

    private fun configurePdfUA(builder: PdfRendererBuilder, html: String) {
        builder.useColorProfile(colorProfileBytes)

        BundledFonts.fontBytesForHtml(html).forEach { (config, bytes) ->
            val fontSupplier = FSSupplier<InputStream> { ByteArrayInputStream(bytes) }
            builder.useFont(
                fontSupplier,
                config.family,
                config.weight,
                config.style.toRendererStyle(),
                true,
                EnumSet.of(FSFontUseCase.FALLBACK_FINAL)
            )
        }

        builder.usePdfUaAccessibility(true)
        builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)
    }

    private fun BundledFonts.FontStyle.toRendererStyle(): RendererFontStyle =
        when (this) {
            BundledFonts.FontStyle.Normal -> RendererFontStyle.NORMAL
            BundledFonts.FontStyle.Italic -> RendererFontStyle.ITALIC
        }
}

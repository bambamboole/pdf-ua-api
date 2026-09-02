package bambamboole.pdfua.pdf

import bambamboole.pdfua.fonts.BundledFonts
import bambamboole.pdfua.fonts.useBundledFontsFor
import bambamboole.pdfua.hyphenation.LocaleAwareHyphenator
import bambamboole.pdfua.template.FileAttachment
import bambamboole.pdfua.template.XmpSchema
import com.openhtmltopdf.extend.FSStreamFactory
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.openhtmltopdf.render.DefaultObjectDrawerFactory
import com.openhtmltopdf.svgsupport.BatikSVGDrawer
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

data class PdfResult(
    val bytes: ByteArray,
    val documentId: String,
)

data class PdfRenderOptions(
    val embedColorProfile: Boolean = true,
)

object PdfRenderer {
    private val logger = LoggerFactory.getLogger(PdfRenderer::class.java)
    private val w3cDom = W3CDom()
    private val validRelationships = setOf("Source", "Data", "Alternative", "Supplement", "Unspecified")

    private const val UUID_METADATA_RESERVE_BYTES = 256
    private const val ATTACHMENT_NAME_MAX_LENGTH = 255
    private const val MAX_ATTACHMENTS = 10
    private const val ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024
    private const val ATTACHMENT_OUTPUT_RESERVE_DIVISOR = 4
    private const val MAX_XMP_SCHEMAS = 10
    private const val MAX_XMP_PROPERTIES = 50
    private val xmpNameFormat = Regex("[A-Za-z_][A-Za-z0-9_.-]*")
    private val reservedXmpPrefixes =
        setOf(
            "pdfaid",
            "pdfuaid",
            "pdfaExtension",
            "pdfaSchema",
            "pdfaProperty",
            "pdfaType",
            "pdfaField",
            "dc",
            "xmp",
            "xmpMM",
            "xmpRights",
            "pdf",
            "rdf",
            "x",
        )
    private val xmpValueTypes =
        setOf("Text", "Integer", "Real", "Boolean", "Date", "URI", "URL", "Lang Alt", "MIMEType", "AgentName", "ProperName")
    private val xmpCategories = setOf("internal", "external")

    private val colorProfileBytes: ByteArray by lazy {
        logger.info("Loading sRGB color profile")
        loadResource("/colorspaces/sRGB.icc")
            ?: throw IllegalStateException("sRGB.icc color profile not found in resources")
    }

    private fun loadResource(path: String): ByteArray? = PdfRenderer::class.java.getResourceAsStream(path)?.use { it.readBytes() }

    fun warmup() {
        logger.info("Warming up PdfRenderer...")
        colorProfileBytes
        BundledFonts.fontBytes
        logger.info("PdfRenderer warmup complete")
    }

    fun convertHtmlToPdf(
        html: String,
        producer: String = "pdf-ua-api.com",
        assetResolver: FSStreamFactory? = null,
        baseUrl: String = "",
        attachments: List<FileAttachment>? = null,
        xmpSchemas: List<XmpSchema>? = null,
        options: PdfRenderOptions = PdfRenderOptions(),
    ): PdfResult {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }
        if (!attachments.isNullOrEmpty()) {
            validateAttachments(attachments)
        }
        if (!xmpSchemas.isNullOrEmpty()) {
            validateXmpSchemas(xmpSchemas)
        }

        val jsoupDoc = Jsoup.parse(html)
        val w3cDoc = w3cDom.fromJsoup(jsoupDoc)
        val hyphenator =
            LocaleAwareHyphenator.forLang(jsoupDoc.selectFirst("html")?.attr("lang"))

        val pdfBytes =
            ByteArrayOutputStream(512 * 1024).use { outputStream ->
                val builder = PdfRendererBuilder()
                configurePdfUA(builder, html, options)
                builder.withProducer(producer)
                builder.useSVGDrawer(BatikSVGDrawer())
                if (assetResolver != null) {
                    builder.useHttpStreamImplementation(assetResolver)
                }
                if (hyphenator != null) {
                    builder.useHyphenation(hyphenator)
                }
                builder.withW3cDocument(w3cDoc, baseUrl)
                builder.toStream(outputStream)
                builder.run()
                outputStream.toByteArray()
            }

        val withAttachments = if (attachments.isNullOrEmpty()) pdfBytes else addAttachments(pdfBytes, attachments)
        val withXmpSchemas = if (xmpSchemas.isNullOrEmpty()) withAttachments else addXmpSchemas(withAttachments, xmpSchemas)
        return embedDocumentId(withXmpSchemas)
    }

    private fun validateXmpSchemas(schemas: List<XmpSchema>) {
        require(schemas.size <= MAX_XMP_SCHEMAS) { "Maximum $MAX_XMP_SCHEMAS XMP schemas allowed" }
        val prefixes = mutableSetOf<String>()
        for (schema in schemas) {
            require(xmpNameFormat.matches(schema.prefix)) { "Invalid XMP prefix '${schema.prefix}'" }
            require(schema.prefix !in reservedXmpPrefixes) { "XMP prefix '${schema.prefix}' is reserved" }
            require(prefixes.add(schema.prefix)) { "Duplicate XMP prefix '${schema.prefix}'" }
            require(schema.namespace.endsWith("#") || schema.namespace.endsWith("/")) {
                "XMP namespace '${schema.namespace}' must end with '#' or '/'"
            }
            require(schema.name.isNotBlank()) { "XMP schema name cannot be blank" }
            validateXmpProperties(schema)
        }
    }

    private fun validateXmpProperties(schema: XmpSchema) {
        require(schema.properties.isNotEmpty()) { "XMP schema '${schema.prefix}' needs at least one property" }
        require(schema.properties.size <= MAX_XMP_PROPERTIES) {
            "XMP schema '${schema.prefix}' exceeds $MAX_XMP_PROPERTIES properties"
        }
        val names = mutableSetOf<String>()
        for (property in schema.properties) {
            require(xmpNameFormat.matches(property.name)) { "Invalid XMP property name '${property.name}'" }
            require(names.add(property.name)) { "Duplicate XMP property '${schema.prefix}:${property.name}'" }
            require(property.valueType in xmpValueTypes) {
                "Invalid XMP value type '${property.valueType}', must be one of: $xmpValueTypes"
            }
            require(property.category in xmpCategories) {
                "Invalid XMP category '${property.category}', must be one of: $xmpCategories"
            }
        }
    }

    private fun addXmpSchemas(
        pdfBytes: ByteArray,
        schemas: List<XmpSchema>,
    ): ByteArray {
        Loader.loadPDF(pdfBytes).use { document ->
            XmpExtensionSchemas.apply(document, schemas)
            logger.info("Declared ${schemas.size} XMP extension schema(s) in PDF")
            return ByteArrayOutputStream(pdfBytes.size + pdfBytes.size / ATTACHMENT_OUTPUT_RESERVE_DIVISOR).use { outputStream ->
                document.save(outputStream)
                outputStream.toByteArray()
            }
        }
    }

    private fun embedDocumentId(pdfBytes: ByteArray): PdfResult {
        val documentId = UUID.randomUUID().toString()
        Loader.loadPDF(pdfBytes).use { document ->
            document.documentInformation.setCustomMetadataValue("X-Document-UUID", documentId)
            return ByteArrayOutputStream(pdfBytes.size + UUID_METADATA_RESERVE_BYTES).use { outputStream ->
                document.save(outputStream)
                PdfResult(outputStream.toByteArray(), documentId)
            }
        }
    }

    private fun validateAttachments(attachments: List<FileAttachment>) {
        require(attachments.size <= MAX_ATTACHMENTS) { "Maximum $MAX_ATTACHMENTS attachments allowed" }
        for (attachment in attachments) {
            require(attachment.name.isNotBlank()) { "Attachment name cannot be blank" }
            require(attachment.name.length <= ATTACHMENT_NAME_MAX_LENGTH) {
                "Attachment name too long: ${attachment.name}"
            }
            require(attachment.content.isNotBlank()) { "Attachment content cannot be blank" }
            require(attachment.relationship in validRelationships) {
                "Invalid relationship '${attachment.relationship}', must be one of: $validRelationships"
            }
            try {
                val decoded = Base64.getDecoder().decode(attachment.content)
                require(decoded.size <= ATTACHMENT_MAX_BYTES) {
                    "Attachment '${attachment.name}' exceeds 10MB limit"
                }
            } catch (e: IllegalArgumentException) {
                if (e.message?.contains("limit") == true) throw e
                throw IllegalArgumentException("Attachment '${attachment.name}' has invalid base64 content")
            }
        }
    }

    private fun addAttachments(
        pdfBytes: ByteArray,
        attachments: List<FileAttachment>,
    ): ByteArray {
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

            return ByteArrayOutputStream(pdfBytes.size + pdfBytes.size / ATTACHMENT_OUTPUT_RESERVE_DIVISOR).use { outputStream ->
                document.save(outputStream)
                outputStream.toByteArray()
            }
        }
    }

    private fun configurePdfUA(
        builder: PdfRendererBuilder,
        html: String,
        options: PdfRenderOptions,
    ) {
        if (options.embedColorProfile) {
            builder.useColorProfile(colorProfileBytes)
        }

        builder.useBundledFontsFor(html, fallbackFinal = true)

        builder.usePdfUaAccessibility(true)
        if (options.embedColorProfile) {
            builder.usePdfAConformance(PdfRendererBuilder.PdfAConformance.PDFA_3_A)
        }
        builder.useObjectDrawerFactory(backgroundObjectDrawerFactory())
    }

    private fun backgroundObjectDrawerFactory(): DefaultObjectDrawerFactory =
        DefaultObjectDrawerFactory().apply {
            registerDrawer(BackgroundObjectDrawer.OBJECT_TYPE, BackgroundObjectDrawer)
        }
}

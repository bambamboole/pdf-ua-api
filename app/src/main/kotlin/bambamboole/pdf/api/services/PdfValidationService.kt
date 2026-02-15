package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.*
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import org.verapdf.pdfa.results.ValidationResult
import org.verapdf.pdfa.validation.validators.ValidatorConfigBuilder
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.TimeZone

object PdfValidationService {
    private val logger = LoggerFactory.getLogger(PdfValidationService::class.java)

    private val validationFlavours = listOf(PDFAFlavour.PDFA_3_A, PDFAFlavour.PDFUA_1)

    private val initialized: Unit by lazy {
        try {
            VeraGreenfieldFoundryProvider.initialise()
            logger.info("veraPDF validation library initialized")
        } catch (e: Exception) {
            logger.error("Failed to initialize veraPDF: ${e.message}", e)
            throw IllegalStateException("Failed to initialize PDF validation library", e)
        }
    }

    fun warmup() {
        logger.info("Warming up PdfValidationService...")
        initialized
        logger.info("PdfValidationService warmup complete")
    }

    fun validatePdf(pdfBytes: ByteArray): ValidationResponse {
        initialized

        try {
            ByteArrayInputStream(pdfBytes).use { inputStream ->
                Foundries.defaultInstance().createParser(inputStream).use { parser ->
                    val config = ValidatorConfigBuilder.defaultBuilder()
                        .recordPasses(true)
                        .maxFails(-1)
                        .showErrorMessages(true)
                        .build()
                    val validator = Foundries.defaultInstance().createValidator(config, validationFlavours)
                    val results = validator.validateAll(parser)

                    val allFailures = mutableListOf<ValidationFailure>()
                    val profileResults = mutableListOf<ProfileResult>()
                    var totalPassed = 0
                    var totalFailed = 0

                    for (result in results) {
                        val profileName = formatProfileName(result.pdfaFlavour)
                        val specName = formatSpecificationName(result.pdfaFlavour)
                        var passed = 0
                        var failed = 0

                        for (assertion in result.testAssertions) {
                            when (assertion.status) {
                                TestAssertion.Status.FAILED -> {
                                    failed++
                                    allFailures.add(buildFailure(assertion, profileName))
                                }
                                TestAssertion.Status.PASSED -> passed++
                                else -> {}
                            }
                        }

                        profileResults.add(
                            ProfileResult(
                                profile = profileName,
                                specification = specName,
                                isCompliant = result.isCompliant,
                                totalChecks = passed + failed,
                                passedChecks = passed,
                                failedChecks = failed
                            )
                        )

                        totalPassed += passed
                        totalFailed += failed
                    }

                    val categories = buildCategorySummary(allFailures, totalPassed, results)
                    val (metadata, documentInfo) = extractDocumentDetails(pdfBytes)
                    val overallCompliant = profileResults.all { it.isCompliant }

                    logger.info(
                        "Validation complete: compliant=$overallCompliant, " +
                                "profiles=${profileResults.map { "${it.profile}=${it.isCompliant}" }}, " +
                                "total=${totalPassed + totalFailed}, passed=$totalPassed, failed=$totalFailed"
                    )

                    return ValidationResponse(
                        isCompliant = overallCompliant,
                        profiles = profileResults,
                        summary = ValidationSummary(
                            totalChecks = totalPassed + totalFailed,
                            passedChecks = totalPassed,
                            failedChecks = totalFailed,
                            categories = categories
                        ),
                        documentInfo = documentInfo,
                        failures = allFailures.take(100),
                        metadata = metadata
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("PDF validation failed: ${e.message}", e)
            throw IllegalStateException("Failed to validate PDF: ${e.message}", e)
        }
    }

    fun isPdfCompliant(pdfBytes: ByteArray): Boolean {
        return try {
            validatePdf(pdfBytes).isCompliant
        } catch (e: Exception) {
            logger.error("Validation check failed: ${e.message}")
            false
        }
    }

    private fun buildFailure(assertion: TestAssertion, profileName: String): ValidationFailure {
        val clause = assertion.ruleId?.clause ?: "Unknown"
        val errorDetails = buildErrorDetails(assertion)

        return ValidationFailure(
            profile = profileName,
            clause = clause,
            testNumber = assertion.ruleId?.testNumber ?: 0,
            category = categorizeClause(clause, profileName),
            message = assertion.message ?: "No message provided",
            location = assertion.location?.context,
            errorDetails = errorDetails
        )
    }

    private fun buildErrorDetails(assertion: TestAssertion): String? {
        val parts = mutableListOf<String>()

        val errorMsg = assertion.errorMessage
        if (!errorMsg.isNullOrBlank() && errorMsg != assertion.message) {
            parts.add(errorMsg)
        }

        val args = assertion.errorArguments
        if (!args.isNullOrEmpty()) {
            for (arg in args) {
                val name = arg.name
                val value = arg.argumentValue
                if (!name.isNullOrBlank() && !value.isNullOrBlank()) {
                    parts.add("$name=$value")
                }
            }
        }

        return parts.joinToString("; ").ifBlank { null }
    }

    private fun buildCategorySummary(
        failures: List<ValidationFailure>,
        totalPassed: Int,
        results: List<ValidationResult>
    ): List<CategoryResult> {
        val failedByCategory = failures.groupBy { it.category }
            .mapValues { it.value.size }

        if (failedByCategory.isEmpty()) return emptyList()

        return failedByCategory.map { (category, failedCount) ->
            CategoryResult(
                category = category,
                passedChecks = 0,
                failedChecks = failedCount
            )
        }.sortedByDescending { it.failedChecks }
    }

    private fun categorizeClause(clause: String, profileName: String): String {
        if (profileName == "PDF/A-3a") return categorizePdfAClause(clause)
        return categorizePdfUaClause(clause)
    }

    private fun categorizePdfAClause(clause: String): String {
        return when {
            clause.startsWith("6.1") -> "PDF/A File Structure"
            clause.startsWith("6.2") -> "PDF/A Graphics"
            clause.startsWith("6.3") -> "PDF/A Fonts"
            clause.startsWith("6.4") -> "PDF/A Transparency"
            clause.startsWith("6.5") -> "PDF/A Annotations"
            clause.startsWith("6.6") -> "PDF/A Actions"
            clause.startsWith("6.7") -> "PDF/A Metadata"
            clause.startsWith("6.8") -> "PDF/A Logical Structure"
            clause.startsWith("6.9") -> "PDF/A Embedded Files"
            else -> "PDF/A Compliance"
        }
    }

    private fun categorizePdfUaClause(clause: String): String {
        return when {
            clause.startsWith("5") -> "Metadata & Identity"
            clause.startsWith("6.1") || clause.startsWith("6.2") -> "File Structure"
            clause == "7.1-8" || clause == "7.1-9" || clause == "7.1-10" -> "Metadata & Identity"
            clause.startsWith("7.1") -> "Structure & Tagging"
            clause == "7.2-2" || clause.startsWith("7.2-2") -> "Natural Language"
            clause == "7.2-21" || clause == "7.2-22" || clause == "7.2-23" ||
                clause == "7.2-24" || clause == "7.2-25" || clause == "7.2-29" ||
                clause == "7.2-30" || clause == "7.2-31" || clause == "7.2-32" ||
                clause == "7.2-33" || clause == "7.2-34" -> "Natural Language"
            clause == "7.2-3" || clause == "7.2-4" || clause == "7.2-5" ||
                clause == "7.2-6" || clause == "7.2-7" || clause == "7.2-8" ||
                clause == "7.2-9" || clause == "7.2-10" || clause == "7.2-11" ||
                clause == "7.2-12" || clause == "7.2-13" || clause == "7.2-14" ||
                clause == "7.2-15" || clause == "7.2-16" || clause == "7.2-36" ||
                clause == "7.2-37" || clause == "7.2-38" || clause == "7.2-39" ||
                clause == "7.2-40" || clause == "7.2-41" || clause == "7.2-42" ||
                clause == "7.2-43" -> "Tables"
            clause == "7.2-17" || clause == "7.2-18" || clause == "7.2-19" ||
                clause == "7.2-20" -> "Lists"
            clause == "7.2-26" || clause == "7.2-27" || clause == "7.2-28" -> "Table of Contents"
            clause.startsWith("7.3") -> "Graphics & Figures"
            clause.startsWith("7.4") -> "Headings"
            clause.startsWith("7.5") -> "Table Headers"
            clause.startsWith("7.7") -> "Mathematical Expressions"
            clause.startsWith("7.9") -> "Notes & References"
            clause.startsWith("7.10") -> "Optional Content"
            clause.startsWith("7.11") -> "Embedded Files"
            clause.startsWith("7.15") -> "XFA Forms"
            clause.startsWith("7.16") -> "Security"
            clause.startsWith("7.18") -> "Annotations"
            clause.startsWith("7.20") -> "XObjects"
            clause.startsWith("7.21") -> "Fonts"
            else -> "Other"
        }
    }

    private fun formatProfileName(flavour: PDFAFlavour): String {
        return when (flavour) {
            PDFAFlavour.PDFA_1_A -> "PDF/A-1a"
            PDFAFlavour.PDFA_1_B -> "PDF/A-1b"
            PDFAFlavour.PDFA_2_A -> "PDF/A-2a"
            PDFAFlavour.PDFA_2_B -> "PDF/A-2b"
            PDFAFlavour.PDFA_2_U -> "PDF/A-2u"
            PDFAFlavour.PDFA_3_A -> "PDF/A-3a"
            PDFAFlavour.PDFA_3_B -> "PDF/A-3b"
            PDFAFlavour.PDFA_3_U -> "PDF/A-3u"
            PDFAFlavour.PDFUA_1 -> "PDF/UA-1"
            PDFAFlavour.PDFUA_2 -> "PDF/UA-2"
            else -> flavour.id
        }
    }

    private fun formatSpecificationName(flavour: PDFAFlavour): String {
        return when (flavour) {
            PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B -> "ISO 19005-1"
            PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U -> "ISO 19005-2"
            PDFAFlavour.PDFA_3_A, PDFAFlavour.PDFA_3_B, PDFAFlavour.PDFA_3_U -> "ISO 19005-3"
            PDFAFlavour.PDFUA_1 -> "ISO 14289-1"
            PDFAFlavour.PDFUA_2 -> "ISO 14289-2"
            else -> flavour.part.name
        }
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun extractDocumentDetails(pdfBytes: ByteArray): Pair<PdfMetadata?, DocumentInfo?> {
        return try {
            Loader.loadPDF(pdfBytes).use { document ->
                val info = document.documentInformation
                val catalog = document.documentCatalog
                val markInfo = catalog.markInfo

                val metadata = PdfMetadata(
                    title = info.title?.takeIf { it.isNotBlank() },
                    subject = info.subject?.takeIf { it.isNotBlank() },
                    author = info.author?.takeIf { it.isNotBlank() },
                    creator = info.creator?.takeIf { it.isNotBlank() },
                    producer = info.producer?.takeIf { it.isNotBlank() },
                    creationDate = info.creationDate?.let { isoDateFormat.format(it.time) }
                )

                val fonts = mutableSetOf<FontInfo>()
                var imageCount = 0
                for (page in document.pages) {
                    val resources = page.resources ?: continue
                    for (fontName in resources.fontNames) {
                        try {
                            val font = resources.getFont(fontName)
                            if (font != null) {
                                fonts.add(FontInfo(
                                    name = font.name ?: fontName.name,
                                    embedded = font.isEmbedded,
                                    type = font.subType ?: "Unknown"
                                ))
                            }
                        } catch (_: Exception) { }
                    }
                    for (xObjectName in resources.xObjectNames) {
                        if (resources.isImageXObject(xObjectName)) imageCount++
                    }
                }

                val structureElements = countStructureElements(catalog.structureTreeRoot)

                val documentInfo = DocumentInfo(
                    pages = document.numberOfPages,
                    tagged = markInfo?.isMarked == true,
                    language = catalog.language,
                    structureElements = structureElements,
                    fonts = fonts.sortedBy { it.name },
                    images = imageCount
                )

                Pair(metadata, documentInfo)
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract PDF details: ${e.message}")
            Pair(null, null)
        }
    }

    private fun countStructureElements(root: org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot?): Int {
        if (root == null) return 0
        var count = 0
        val stack = ArrayDeque<Any>()
        try {
            stack.addAll(root.kids)
        } catch (_: Exception) {
            return 0
        }
        while (stack.isNotEmpty()) {
            val item = stack.removeLast()
            if (item is org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement) {
                count++
                try {
                    stack.addAll(item.kids)
                } catch (_: Exception) { }
            }
        }
        return count
    }
}

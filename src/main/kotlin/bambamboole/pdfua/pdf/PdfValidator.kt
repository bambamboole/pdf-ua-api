package bambamboole.pdfua.pdf

import bambamboole.pdfua.http.*
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

// Ordered list of (predicate, category) pairs. First match wins, mirroring the
// original `when`'s branch ordering exactly — including the documented overlap
// where `startsWith("7.2-2")` shadows the more-specific 7.2-26..28 exact matches
// further down. Behaviour is preserved bit-for-bit.
private val PDF_UA_CLAUSE_MATCHERS: List<Pair<(String) -> Boolean, String>> =
    listOf(
        ({ s: String -> s.startsWith("5") }) to "Metadata & Identity",
        ({ s: String -> s.startsWith("6.1") || s.startsWith("6.2") }) to "File Structure",
        ({ s: String -> s == "7.1-8" || s == "7.1-9" || s == "7.1-10" }) to "Metadata & Identity",
        ({ s: String -> s.startsWith("7.1") }) to "Structure & Tagging",
        ({ s: String -> s == "7.2-2" || s.startsWith("7.2-2") }) to "Natural Language",
        (
            { s: String ->
                s in
                    setOf(
                        "7.2-21",
                        "7.2-22",
                        "7.2-23",
                        "7.2-24",
                        "7.2-25",
                        "7.2-29",
                        "7.2-30",
                        "7.2-31",
                        "7.2-32",
                        "7.2-33",
                        "7.2-34",
                    )
            }
        ) to "Natural Language",
        (
            { s: String ->
                s in
                    setOf(
                        "7.2-3",
                        "7.2-4",
                        "7.2-5",
                        "7.2-6",
                        "7.2-7",
                        "7.2-8",
                        "7.2-9",
                        "7.2-10",
                        "7.2-11",
                        "7.2-12",
                        "7.2-13",
                        "7.2-14",
                        "7.2-15",
                        "7.2-16",
                        "7.2-36",
                        "7.2-37",
                        "7.2-38",
                        "7.2-39",
                        "7.2-40",
                        "7.2-41",
                        "7.2-42",
                        "7.2-43",
                    )
            }
        ) to "Tables",
        ({ s: String -> s in setOf("7.2-17", "7.2-18", "7.2-19", "7.2-20") }) to "Lists",
        ({ s: String -> s in setOf("7.2-26", "7.2-27", "7.2-28") }) to "Table of Contents",
        ({ s: String -> s.startsWith("7.3") }) to "Graphics & Figures",
        ({ s: String -> s.startsWith("7.4") }) to "Headings",
        ({ s: String -> s.startsWith("7.5") }) to "Table Headers",
        ({ s: String -> s.startsWith("7.7") }) to "Mathematical Expressions",
        ({ s: String -> s.startsWith("7.9") }) to "Notes & References",
        ({ s: String -> s.startsWith("7.10") }) to "Optional Content",
        ({ s: String -> s.startsWith("7.11") }) to "Embedded Files",
        ({ s: String -> s.startsWith("7.15") }) to "XFA Forms",
        ({ s: String -> s.startsWith("7.16") }) to "Security",
        ({ s: String -> s.startsWith("7.18") }) to "Annotations",
        ({ s: String -> s.startsWith("7.20") }) to "XObjects",
        ({ s: String -> s.startsWith("7.21") }) to "Fonts",
    )

object PdfValidator {
    private val logger = LoggerFactory.getLogger(PdfValidator::class.java)

    private val validationFlavours = listOf(PDFAFlavour.PDFA_3_A, PDFAFlavour.PDFUA_1)

    @Suppress(
        "TooGenericExceptionCaught", // veraPDF init can throw arbitrary errors; we wrap them all as IllegalStateException
        "MaxLineLength",
    )
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
        logger.info("Warming up PdfValidator...")
        initialized
        logger.info("PdfValidator warmup complete")
    }

    @Suppress(
        "TooGenericExceptionCaught", // veraPDF/PDFBox throw varied runtime errors; all become IllegalStateException
        "NestedBlockDepth", // mirrors the PDF/A validation flow; flattening risks breaking compliance fixtures
    )
    fun validatePdf(pdfBytes: ByteArray): ValidationResponse {
        initialized

        try {
            ByteArrayInputStream(pdfBytes).use { inputStream ->
                Foundries.defaultInstance().createParser(inputStream).use { parser ->
                    val config =
                        ValidatorConfigBuilder
                            .defaultBuilder()
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

                                TestAssertion.Status.PASSED -> {
                                    passed++
                                }

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
                                failedChecks = failed,
                            ),
                        )

                        totalPassed += passed
                        totalFailed += failed
                    }

                    val categories = buildCategorySummary(allFailures)
                    val (metadata, documentInfo) = extractDocumentDetails(pdfBytes)
                    val overallCompliant = profileResults.all { it.isCompliant }

                    logger.info(
                        "Validation complete: compliant=$overallCompliant, " +
                            "profiles=${profileResults.map { "${it.profile}=${it.isCompliant}" }}, " +
                            "total=${totalPassed + totalFailed}, passed=$totalPassed, failed=$totalFailed",
                    )

                    return ValidationResponse(
                        isCompliant = overallCompliant,
                        profiles = profileResults,
                        summary =
                            ValidationSummary(
                                totalChecks = totalPassed + totalFailed,
                                passedChecks = totalPassed,
                                failedChecks = totalFailed,
                                categories = categories,
                            ),
                        documentInfo = documentInfo,
                        failures = allFailures.take(100),
                        metadata = metadata,
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("PDF validation failed: ${e.message}", e)
            throw IllegalStateException("Failed to validate PDF: ${e.message}", e)
        }
    }

    @Suppress("TooGenericExceptionCaught") // defensive boundary: any failure becomes `false`
    fun isPdfCompliant(pdfBytes: ByteArray): Boolean =
        try {
            validatePdf(pdfBytes).isCompliant
        } catch (e: Exception) {
            logger.error("Validation check failed: ${e.message}")
            false
        }

    private fun buildFailure(
        assertion: TestAssertion,
        profileName: String,
    ): ValidationFailure {
        val clause = assertion.ruleId?.clause ?: "Unknown"
        val errorDetails = buildErrorDetails(assertion)

        return ValidationFailure(
            profile = profileName,
            clause = clause,
            testNumber = assertion.ruleId?.testNumber ?: 0,
            category = categorizeClause(clause, profileName),
            message = assertion.message ?: "No message provided",
            location = assertion.location?.context,
            errorDetails = errorDetails,
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

    private fun buildCategorySummary(failures: List<ValidationFailure>): List<CategoryResult> {
        val failedByCategory =
            failures
                .groupBy { it.category }
                .mapValues { it.value.size }

        if (failedByCategory.isEmpty()) return emptyList()

        return failedByCategory
            .map { (category, failedCount) ->
                CategoryResult(
                    category = category,
                    passedChecks = 0,
                    failedChecks = failedCount,
                )
            }.sortedByDescending { it.failedChecks }
    }

    private fun categorizeClause(
        clause: String,
        profileName: String,
    ): String {
        if (profileName == "PDF/A-3a") return categorizePdfAClause(clause)
        return categorizePdfUaClause(clause)
    }

    private fun categorizePdfAClause(clause: String): String =
        when {
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

    @Suppress("MaxLineLength")
    private fun categorizePdfUaClause(clause: String): String = PDF_UA_CLAUSE_MATCHERS.firstOrNull { (matches, _) -> matches(clause) }?.second ?: "Other"

    private fun formatProfileName(flavour: PDFAFlavour): String =
        when (flavour) {
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

    private fun formatSpecificationName(flavour: PDFAFlavour): String =
        when (flavour) {
            PDFAFlavour.PDFA_1_A, PDFAFlavour.PDFA_1_B -> "ISO 19005-1"
            PDFAFlavour.PDFA_2_A, PDFAFlavour.PDFA_2_B, PDFAFlavour.PDFA_2_U -> "ISO 19005-2"
            PDFAFlavour.PDFA_3_A, PDFAFlavour.PDFA_3_B, PDFAFlavour.PDFA_3_U -> "ISO 19005-3"
            PDFAFlavour.PDFUA_1 -> "ISO 14289-1"
            PDFAFlavour.PDFUA_2 -> "ISO 14289-2"
            else -> flavour.part.name
        }

    private val isoDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    @Suppress(
        "TooGenericExceptionCaught", // defensive boundary: any failure returns Pair(null, null)
        "NestedBlockDepth", // mirrors PDFBox extraction structure; flattening risks breaking metadata extraction
    )
    private fun extractDocumentDetails(pdfBytes: ByteArray): Pair<PdfMetadata?, DocumentInfo?> =
        try {
            Loader.loadPDF(pdfBytes).use { document ->
                val info = document.documentInformation
                val catalog = document.documentCatalog
                val markInfo = catalog.markInfo

                val metadata =
                    PdfMetadata(
                        title = info.title?.takeIf { it.isNotBlank() },
                        subject = info.subject?.takeIf { it.isNotBlank() },
                        author = info.author?.takeIf { it.isNotBlank() },
                        creator = info.creator?.takeIf { it.isNotBlank() },
                        producer = info.producer?.takeIf { it.isNotBlank() },
                        creationDate = info.creationDate?.let { isoDateFormat.format(it.time) },
                    )

                val fonts = mutableSetOf<FontInfo>()
                var imageCount = 0
                for (page in document.pages) {
                    val resources = page.resources ?: continue
                    for (fontName in resources.fontNames) {
                        try {
                            val font = resources.getFont(fontName)
                            if (font != null) {
                                fonts.add(
                                    FontInfo(
                                        name = font.name ?: fontName.name,
                                        embedded = font.isEmbedded,
                                        type = font.subType ?: "Unknown",
                                    ),
                                )
                            }
                        } catch (_: Exception) {
                        }
                    }
                    for (xObjectName in resources.xObjectNames) {
                        if (resources.isImageXObject(xObjectName)) imageCount++
                    }
                }

                val structureElements = countStructureElements(catalog.structureTreeRoot)

                val documentInfo =
                    DocumentInfo(
                        pages = document.numberOfPages,
                        tagged = markInfo?.isMarked == true,
                        language = catalog.language,
                        structureElements = structureElements,
                        fonts = fonts.sortedBy { it.name },
                        images = imageCount,
                    )

                Pair(metadata, documentInfo)
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract PDF details: ${e.message}")
            Pair(null, null)
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
                } catch (_: Exception) {
                }
            }
        }
        return count
    }
}

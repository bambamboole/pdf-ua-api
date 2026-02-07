package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.PdfMetadata
import bambamboole.pdf.api.models.ValidationFailure
import bambamboole.pdf.api.models.ValidationResponse
import org.apache.pdfbox.Loader
import org.slf4j.LoggerFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import org.verapdf.pdfa.results.TestAssertion
import java.io.ByteArrayInputStream

object PdfValidationService {
    private val logger = LoggerFactory.getLogger(PdfValidationService::class.java)
    private var initialized = false

    /**
     * Initialize veraPDF library (should be called once)
     */
    private fun initialize() {
        if (!initialized) {
            try {
                VeraGreenfieldFoundryProvider.initialise()
                initialized = true
                logger.info("veraPDF validation library initialized")
            } catch (e: Exception) {
                logger.error("Failed to initialize veraPDF: ${e.message}", e)
                throw IllegalStateException("Failed to initialize PDF validation library", e)
            }
        }
    }

    /**
     * Validate PDF bytes for PDF/UA and PDF/A compliance
     * @param pdfBytes PDF document as byte array
     * @return ValidationResponse with compliance results and metadata
     */
    fun validatePdf(pdfBytes: ByteArray): ValidationResponse {
        initialize()

        try {
            ByteArrayInputStream(pdfBytes).use { inputStream ->
                Foundries.defaultInstance().createParser(inputStream).use { parser ->
                    // Auto-detect PDF flavour
                    val detectedFlavour = parser.flavour

                    // Use explicit PDF/A-3a flavour for thorough validation
                    val flavour = if (detectedFlavour == PDFAFlavour.NO_FLAVOUR) {
                        PDFAFlavour.PDFA_3_A
                    } else {
                        detectedFlavour
                    }

                    logger.info("Validating PDF with flavour: ${flavour.id} (detected: ${detectedFlavour.id})")

                    // Create validator with logging enabled (second parameter = true for max check count)
                    val validator = Foundries.defaultInstance()
                        .createValidator(flavour, false)

                    // Validate the PDF
                    val validationResult = validator.validate(parser)

                    // Extract test assertions
                    val failures = mutableListOf<ValidationFailure>()
                    var passedChecks = 0
                    var failedChecks = 0

                    validationResult.testAssertions.forEach { assertion ->
                        when (assertion.status) {
                            TestAssertion.Status.FAILED -> {
                                failedChecks++
                                failures.add(
                                    ValidationFailure(
                                        clause = assertion.ruleId?.clause ?: "Unknown",
                                        testNumber = assertion.ruleId?.testNumber ?: 0,
                                        status = "FAILED",
                                        message = assertion.message ?: "No message provided",
                                        location = assertion.location?.context ?: null
                                    )
                                )
                            }
                            TestAssertion.Status.PASSED -> passedChecks++
                            else -> {}
                        }
                    }

                    val totalChecks = passedChecks + failedChecks

                    // Extract metadata using PDFBox
                    val metadata = extractMetadata(pdfBytes)

                    logger.info("Validation complete: compliant=${validationResult.isCompliant}, " +
                            "total=$totalChecks, passed=$passedChecks, failed=$failedChecks")

                    if (metadata != null) {
                        logger.info("PDF metadata: title='${metadata.title}', subject='${metadata.subject}', " +
                                "author='${metadata.author}', creator='${metadata.creator}'")
                    }

                    return ValidationResponse(
                        isCompliant = validationResult.isCompliant,
                        flavour = flavour.id,
                        totalChecks = totalChecks,
                        failedChecks = failedChecks,
                        passedChecks = passedChecks,
                        failures = failures.take(20),  // Limit to first 20 failures
                        metadata = metadata
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("PDF validation failed: ${e.message}", e)
            throw IllegalStateException("Failed to validate PDF: ${e.message}", e)
        }
    }

    /**
     * Extract metadata from PDF using PDFBox
     */
    private fun extractMetadata(pdfBytes: ByteArray): PdfMetadata? {
        return try {
            Loader.loadPDF(pdfBytes).use { document ->
                val info = document.documentInformation

                PdfMetadata(
                    title = info.title?.takeIf { it.isNotBlank() },
                    subject = info.subject?.takeIf { it.isNotBlank() },
                    author = info.author?.takeIf { it.isNotBlank() },
                    creator = info.creator?.takeIf { it.isNotBlank() },
                    producer = info.producer?.takeIf { it.isNotBlank() },
                    creationDate = info.creationDate?.toString()
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract PDF metadata: ${e.message}")
            null
        }
    }

    /**
     * Validate PDF and return simplified boolean result
     */
    fun isPdfCompliant(pdfBytes: ByteArray): Boolean {
        return try {
            validatePdf(pdfBytes).isCompliant
        } catch (e: Exception) {
            logger.error("Validation check failed: ${e.message}")
            false
        }
    }
}

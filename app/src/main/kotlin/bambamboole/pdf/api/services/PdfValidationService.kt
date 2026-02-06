package bambamboole.pdf.api.services

import bambamboole.pdf.api.models.ValidationFailure
import bambamboole.pdf.api.models.ValidationResponse
import org.slf4j.LoggerFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
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
     * @return ValidationResponse with compliance results
     */
    fun validatePdf(pdfBytes: ByteArray): ValidationResponse {
        initialize()

        try {
            ByteArrayInputStream(pdfBytes).use { inputStream ->
                Foundries.defaultInstance().createParser(inputStream).use { parser ->
                    // Detect PDF flavour (PDF/A, PDF/UA, etc.)
                    val flavour = parser.flavour

                    logger.info("Validating PDF with detected flavour: ${flavour.id}")

                    // Create validator for detected flavour
                    val validator = Foundries.defaultInstance()
                        .createValidator(flavour, false)

                    // Validate the PDF
                    val validationResult = validator.validate(parser)

                    // Extract test assertions (failures)
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

                    logger.info("Validation complete: compliant=${validationResult.isCompliant}, " +
                            "total=$totalChecks, passed=$passedChecks, failed=$failedChecks")

                    return ValidationResponse(
                        isCompliant = validationResult.isCompliant,
                        flavour = flavour.id,
                        totalChecks = totalChecks,
                        failedChecks = failedChecks,
                        passedChecks = passedChecks,
                        failures = failures.take(20)  // Limit to first 20 failures
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("PDF validation failed: ${e.message}", e)
            throw IllegalStateException("Failed to validate PDF: ${e.message}", e)
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

package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.models.ValidationResponse
import bambamboole.pdf.api.services.PdfValidationService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ValidationRoutesTest {

    @Test
    fun testValidateEndpoint() = testApplication {
        application {
            module()
        }

        // First, generate a PDF with PDF/UA enabled
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Test Document</title>
                <meta name="subject" content="PDF Validation Test" />
                <meta name="description" content="A test document for validation" />
                <meta name="author" content="Test Author" />
            </head>
            <body>
                <h1>Validation Test</h1>
                <p>This document is used for testing PDF validation.</p>
            </body>
            </html>
        """.trimIndent()

        val convertResponse = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, convertResponse.status)
        val pdfBytes = convertResponse.readBytes()

        // Now validate the generated PDF via the API endpoint
        val validateResponse = client.post("/validate") {
            contentType(ContentType.Application.Pdf)
            setBody(pdfBytes)
        }

        assertEquals(HttpStatusCode.OK, validateResponse.status)

        // Parse validation response
        val validationResult = Json.decodeFromString<ValidationResponse>(validateResponse.bodyAsText())

        // Check that we got a validation result
        assertNotNull(validationResult.flavour, "Validation should detect PDF flavour")
        assertTrue(validationResult.flavour.isNotEmpty(), "Flavour should not be empty")
        assertEquals("3a", validationResult.flavour, "Should detect PDF/A-3a")
        assertTrue(validationResult.isCompliant, "Generated PDF/UA document should be compliant")

        // Note: totalChecks can be 0 for compliant documents in some PDF/A profiles
        // The important thing is that validation completed successfully
    }

    @Test
    fun testValidateEndpointWithIncompleteMetadata() = testApplication {
        application {
            module()
        }

        // Generate a PDF with minimal HTML (missing required metadata for full compliance)
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head><title>Basic Document</title></head>
            <body><h1>Test</h1></body>
            </html>
        """.trimIndent()

        val convertResponse = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, convertResponse.status)
        val pdfBytes = convertResponse.readBytes()

        // Validate the PDF
        val validationResult = PdfValidationService.validatePdf(pdfBytes)

        // Should still get a validation result, even if not fully compliant
        assertNotNull(validationResult, "Validation should complete for any PDF")
        assertNotNull(validationResult.flavour, "Should detect PDF/A flavour")

        // PDFs with incomplete metadata may not be fully compliant
        println("PDF validation with incomplete metadata: compliant=${validationResult.isCompliant}, flavour=${validationResult.flavour}")
    }
}

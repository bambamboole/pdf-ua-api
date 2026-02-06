package bambamboole.pdf.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.models.ValidationResponse
import bambamboole.pdf.api.services.PdfValidationService
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            module()
        }

        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("\"status\""))
            assertTrue(bodyAsText().contains("\"ok\""))
        }
    }

    @Test
    fun testConvertEndpointWithValidHTML() = testApplication {
        application {
            module()
        }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
        assertTrue(response.headers.contains(HttpHeaders.ContentDisposition))

        // Verify PDF content (PDF starts with %PDF-)
        val pdfBytes = response.readBytes()
        assertTrue(pdfBytes.isNotEmpty())
        val pdfHeader = pdfBytes.take(5).toByteArray().decodeToString()
        assertTrue(pdfHeader.startsWith("%PDF-"), "Response should be a valid PDF")

        // Validate PDF structure with veraPDF
        val validationResult = PdfValidationService.validatePdf(pdfBytes)
        assertNotNull(validationResult, "PDF validation should complete")
        assertNotNull(validationResult.flavour, "PDF should have a detected flavour")
    }

    @Test
    fun testConvertEndpointWithStyledHTML() = testApplication {
        application {
            module()
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                </style>
            </head>
            <body>
                <h1>Styled Document</h1>
                <p>This document has CSS styles.</p>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdfBytes = response.readBytes()
        assertTrue(pdfBytes.size > 500, "Styled PDF should be larger")

        // Validate PDF structure
        val validationResult = PdfValidationService.validatePdf(pdfBytes)
        assertNotNull(validationResult, "PDF validation should complete")
    }

    @Test
    fun testConvertEndpointWithEmptyHTML() = testApplication {
        application {
            module()
        }

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("error"))
    }

    @Test
    fun testConvertEndpointWithWhitespaceOnlyHTML() = testApplication {
        application {
            module()
        }

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("error"))
    }

    @Test
    fun testConvertEndpointWithInvalidJSON() = testApplication {
        application {
            module()
        }

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"invalid": "json structure"}""")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun testConvertEndpointWithComplexHTML() = testApplication {
        application {
            module()
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Complex Document</title>
                <style>
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid black; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
                <h1>Complex Document</h1>
                <table>
                    <tr><th>Name</th><th>Value</th></tr>
                    <tr><td>Item 1</td><td>100</td></tr>
                    <tr><td>Item 2</td><td>200</td></tr>
                </table>
                <ul>
                    <li>Point 1</li>
                    <li>Point 2</li>
                    <li>Point 3</li>
                </ul>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())

        // Validate PDF structure
        val pdfBytes = response.readBytes()
        val validationResult = PdfValidationService.validatePdf(pdfBytes)
        assertNotNull(validationResult, "PDF validation should complete")
        assertTrue(pdfBytes.size > 1000, "Complex PDF should be reasonably sized")
    }

    @Test
    fun testHealthEndpointReturnType() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun testConvertEndpointWithPdfUA() = testApplication {
        application {
            module()
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Accessible Document</title>
                <meta name="subject" content="PDF/UA Test Document" />
                <meta name="description" content="An accessible PDF document" />
                <meta name="author" content="Test Author" />
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                </style>
            </head>
            <body>
                <h1>Accessible PDF Document</h1>
                <p>This document is generated with PDF/UA compliance enabled.</p>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())

        val pdfBytes = response.readBytes()
        assertTrue(pdfBytes.isNotEmpty())
        assertTrue(pdfBytes.size > 1000, "PDF/UA document should be larger due to embedded fonts")

        // Validate PDF/A compliance with veraPDF
        val validationResult = PdfValidationService.validatePdf(pdfBytes)
        assertNotNull(validationResult, "PDF/UA validation should complete")
        assertEquals("3a", validationResult.flavour, "PDF should be PDF/A-3a compliant")
        assertTrue(validationResult.isCompliant, "PDF/UA document should be compliant")

        // Log validation details for debugging
        println("PDF/UA Validation: compliant=${validationResult.isCompliant}, " +
                "checks=${validationResult.totalChecks}, failed=${validationResult.failedChecks}")

        if (!validationResult.isCompliant && validationResult.failures.isNotEmpty()) {
            println("Validation failures:")
            validationResult.failures.take(5).forEach { failure ->
                println("  - ${failure.clause}: ${failure.message}")
            }
        }
    }

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

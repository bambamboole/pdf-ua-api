package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.services.PdfValidationService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import kotlin.test.*

class ConvertRoutesTest {

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
        val pdfBytes = response.readRawBytes()
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
        println(
            "PDF/UA Validation: compliant=${validationResult.isCompliant}, " +
                    "checks=${validationResult.totalChecks}, failed=${validationResult.failedChecks}"
        )

        if (!validationResult.isCompliant && validationResult.failures.isNotEmpty()) {
            println("Validation failures:")
            validationResult.failures.take(5).forEach { failure ->
                println("  - ${failure.clause}: ${failure.message}")
            }
        }
    }

    @Test
    fun testConvertEndpointSetsAuthorMetadata() = testApplication {
        application {
            module()
        }

        val expectedAuthor = "John Doe"
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Author Test Document</title>
                <meta name="author" content="$expectedAuthor" />
                <meta name="subject" content="Testing PDF Author Metadata" />
            </head>
            <body>
                <h1>Document Title</h1>
                <p>This document should have the author metadata set.</p>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val pdfBytes = response.readBytes()

        // Load PDF and extract metadata using PDFBox
        Loader.loadPDF(pdfBytes).use { document ->
            val info: PDDocumentInformation = document.documentInformation

            assertNotNull(info, "PDF should have document information")
            assertEquals(expectedAuthor, info.author, "PDF author should match HTML meta author tag")
            assertEquals("Author Test Document", info.title, "PDF title should match HTML title tag")
        }
    }

    @Test
    fun testConvertEndpointWithValidApiKey() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer test-api-key")
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())

        val pdfBytes = response.readBytes()
        assertTrue(pdfBytes.isNotEmpty())
        val pdfHeader = pdfBytes.take(5).toByteArray().decodeToString()
        assertTrue(pdfHeader.startsWith("%PDF-"), "Response should be a valid PDF")
    }

    @Test
    fun testConvertEndpointWithInvalidApiKey() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer wrong-key")
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testConvertEndpointWithoutApiKeyWhenAuthEnabled() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testConvertEndpointWithMalformedAuthorizationHeader() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "InvalidFormat test-api-key")
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}

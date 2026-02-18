package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.models.FileAttachment
import bambamboole.pdf.api.module
import bambamboole.pdf.api.services.PdfValidationService
import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for the /convert endpoint, including visual regression testing using fixtures.
 */
class ConvertRoutesTest {

    companion object {
        /**
         * Helper function to test a single fixture for visual regression.
         * This only tests PDF generation and visual comparison, NOT validation.
         */
        private suspend fun ApplicationTestBuilder.testFixtureConversion(fixtureName: String) {
            println("\n=== Testing fixture conversion: $fixtureName ===")

            val fixtureDir = File(getSourceFixturesDir(), fixtureName)
            assertTrue(
                fixtureDir.exists() && fixtureDir.isDirectory,
                "Fixture directory not found: ${fixtureDir.absolutePath}"
            )

            // Load fixture files
            val inputHtml = File(fixtureDir, "input.html").readText()
            val expectedPdfFile = File(fixtureDir, "expected.pdf")

            // Step 1: Convert HTML to PDF
            val convertResponse = client.post("/convert") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(inputHtml)))
            }

            assertEquals(
                HttpStatusCode.OK, convertResponse.status,
                "Fixture '$fixtureName': Convert endpoint should return 200 OK"
            )
            assertEquals(
                ContentType.Application.Pdf, convertResponse.contentType(),
                "Fixture '$fixtureName': Response should be a PDF"
            )

            val actualPdfBytes = convertResponse.readRawBytes()
            assertTrue(
                actualPdfBytes.isNotEmpty(),
                "Fixture '$fixtureName': PDF should not be empty"
            )

            // Save generated PDF for inspection
            val generatedPdfFile = File(fixtureDir, "generated.pdf")
            if (generatedPdfFile.exists()) {
                generatedPdfFile.delete()
            }
            generatedPdfFile.writeBytes(actualPdfBytes)
            println("Fixture '$fixtureName': Generated PDF saved to ${generatedPdfFile.absolutePath}")

            // Step 2: Visual regression testing
            if (expectedPdfFile.exists()) {
                val expectedPdfBytes = expectedPdfFile.readBytes()
                val compareResults = PdfVisualTester.comparePdfDocuments(
                    expectedPdfBytes,
                    actualPdfBytes,
                    fixtureName,
                    false // Don't keep images when they match
                )

                // Check for problems
                val hasProblems = compareResults.any {
                    it.type != PdfVisualTester.ProblemType.PAGE_GOOD
                }

                if (hasProblems) {
                    val problemPages = compareResults.filter {
                        it.type != PdfVisualTester.ProblemType.PAGE_GOOD
                    }
                    println("Fixture '$fixtureName': Visual differences detected on ${problemPages.size} page(s)")
                    problemPages.forEach { diff ->
                        println("  Page ${diff.pageNumber}: ${diff.type} - ${diff.logMessage}")

                        // Save diff images for visual differences
                        if (diff.type == PdfVisualTester.ProblemType.PAGE_VISUALLY_DIFFERENT) {
                            val testImage = diff.testImages
                            if (testImage.hasDifferences()) {
                                val diffImage = testImage.createDiff()
                                val diffFile = File(fixtureDir, "page-${diff.pageNumber}-diff.png")
                                javax.imageio.ImageIO.write(diffImage, "PNG", diffFile)
                                println("  Diff image saved: ${diffFile.name}")
                            }
                        }
                    }
                    fail("Fixture '$fixtureName': Visual regression test failed. See diff images in ${fixtureDir.absolutePath}")
                } else {
                    // PDFs are identical (no problems reported)
                    println("Fixture '$fixtureName': Visual regression test passed - PDFs are identical")
                }
            } else {
                println("Fixture '$fixtureName': No expected.pdf found, saving generated PDF as baseline")
                val baselinePdf = File(fixtureDir, "expected.pdf")
                baselinePdf.writeBytes(actualPdfBytes)
                println("Fixture '$fixtureName': Baseline PDF saved to ${baselinePdf.absolutePath}")
            }
        }

        private fun getSourceFixturesDir(): File {
            val fixturesUrl = ConvertRoutesTest::class.java.classLoader.getResource("fixtures")
                ?: fail("Fixtures directory not found in classpath")

            val buildFixturesDir = File(fixturesUrl.toURI())
            val projectRoot = buildFixturesDir.absolutePath.substringBefore("/app/build/")
            return File(projectRoot, "app/src/test/resources/fixtures")
        }
    }

    // ========================================
    // Basic Conversion Tests
    // ========================================

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

    // ========================================
    // Authentication Tests
    // ========================================

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

        val pdfBytes = response.readRawBytes()
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

    // ========================================
    // baseUrl Validation Tests
    // ========================================

    @Test
    fun testConvertEndpointWithBaseUrl() = testApplication {
        application { module() }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent, "https://example.com")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
    }

    @Test
    fun testConvertEndpointRejectsFileBaseUrl() = testApplication {
        application { module() }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"$htmlContent","baseUrl":"file:///etc/passwd"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("error"))
    }

    @Test
    fun testConvertEndpointRejectsJavascriptBaseUrl() = testApplication {
        application { module() }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"$htmlContent","baseUrl":"javascript:alert(1)"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testConvertEndpointWithoutBaseUrl() = testApplication {
        application { module() }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ========================================
    // Fixture-Based Visual Regression Tests
    // ========================================

    @Test
    fun testFixtureSimpleDocument() = testApplication {
        application { module() }
        testFixtureConversion("simple-document")
    }

    @Test
    fun testFixtureStyledTable() = testApplication {
        application { module() }
        testFixtureConversion("styled-table")
    }

    @Test
    fun testFixtureFontVariations() = testApplication {
        application { module() }
        testFixtureConversion("font-variations")
    }

    @Test
    fun testFixtureCustomCreator() = testApplication {
        application { module() }
        testFixtureConversion("custom-creator")
    }

    @Test
    fun testFixtureInvoiceExample1() = testApplication {
        application { module() }
        testFixtureConversion("invoice-example-1")
    }

    @Test
    fun testFixtureTablePagination() = testApplication {
        application { module() }
        testFixtureConversion("table-pagination")
    }

    @Test
    fun testFixtureExternalImage() = testApplication {
        application { module() }
        testFixtureConversion("external-image")
    }

    @Test
    fun testFixtureExternalFont() = testApplication {
        application { module() }
        testFixtureConversion("external-font")
    }

    // ========================================
    // External Font Tests
    // ========================================

    @Test
    fun testConvertWithFontFaceDeclaration() = testApplication {
        application { module() }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Font Face Test</title>
                <meta name="subject" content="Custom font test"/>
                <meta name="author" content="Test"/>
                <style>
                    @font-face {
                        font-family: 'Pacifico';
                        src: url('https://raw.githubusercontent.com/google/fonts/main/ofl/pacifico/Pacifico-Regular.ttf');
                        font-weight: 400;
                        font-style: normal;
                    }
                    .custom { font-family: 'Pacifico', serif; font-size: 20px; }
                </style>
            </head>
            <body>
                <h1>Custom Font</h1>
                <p class="custom">This text uses Pacifico font.</p>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(html)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdfBytes = response.readRawBytes()

        val validation = PdfValidationService.validatePdf(pdfBytes)
        assertTrue(validation.isCompliant, "PDF with @font-face should be PDF/A-3a compliant")

        assertNotNull(validation.documentInfo)
        val fontNames = validation.documentInfo!!.fonts.map { it.name }
        assertTrue(
            fontNames.any { it.contains("Pacifico", ignoreCase = true) },
            "Pacifico font should be embedded in PDF. Found fonts: $fontNames"
        )
        assertTrue(
            validation.documentInfo!!.fonts.all { it.embedded },
            "All fonts should be embedded for PDF/A compliance"
        )
    }

    @Test
    fun testConvertWithUnreachableFontUrl() = testApplication {
        application { module() }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Unreachable Font Test</title>
                <meta name="subject" content="Fallback font test"/>
                <meta name="author" content="Test"/>
                <style>
                    @font-face {
                        font-family: 'NonExistentFont';
                        src: url('https://invalid.example.com/nonexistent-font.ttf');
                    }
                    .custom { font-family: 'NonExistentFont', sans-serif; }
                </style>
            </head>
            <body>
                <h1>Fallback Font Test</h1>
                <p class="custom">This text should fall back to Liberation fonts.</p>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(html)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdfBytes = response.readRawBytes()
        assertTrue(pdfBytes.size > 1000, "PDF should not be blank (fallback fonts should render text)")

        val validation = PdfValidationService.validatePdf(pdfBytes)
        assertTrue(validation.isCompliant, "PDF with unreachable font URL should still be compliant via fallback fonts")
    }

    // ========================================
    // File Attachment Tests
    // ========================================

    @Test
    fun testConvertWithXmlAttachment() = testApplication {
        application { module() }

        val html = """<!DOCTYPE html><html lang="en"><head><title>Invoice</title>
            <meta name="subject" content="Invoice"/></head>
            <body><h1>Invoice</h1></body></html>"""

        val xmlContent = """<?xml version="1.0" encoding="UTF-8"?>
            <rsm:CrossIndustryInvoice xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100">
            <rsm:ExchangedDocument>
            <ram:ID xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100">INV-001</ram:ID>
            </rsm:ExchangedDocument>
            </rsm:CrossIndustryInvoice>"""

        val base64Xml = Base64.getEncoder().encodeToString(xmlContent.toByteArray())

        val request = ConvertRequest(
            html = html,
            attachments = listOf(
                FileAttachment(
                    name = "factur-x.xml",
                    content = base64Xml,
                    mimeType = "text/xml",
                    description = "Factur-X XML invoice",
                    relationship = "Alternative"
                )
            )
        )

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdfBytes = response.readRawBytes()

        Loader.loadPDF(pdfBytes).use { document ->
            val names = document.documentCatalog.names
            assertNotNull(names)
            val efTree = names.embeddedFiles
            assertNotNull(efTree)
            val embeddedFiles = efTree.names
            assertNotNull(embeddedFiles)
            assertTrue(embeddedFiles.containsKey("factur-x.xml"))

            val fileSpec = embeddedFiles["factur-x.xml"]!!
            assertEquals("factur-x.xml", fileSpec.file)
            assertEquals("Factur-X XML invoice", fileSpec.fileDescription)
            assertEquals("Alternative", fileSpec.cosObject.getNameAsString(COSName.AF_RELATIONSHIP))
        }

        val validation = PdfValidationService.validatePdf(pdfBytes)
        assertTrue(validation.isCompliant, "PDF with attachment must remain PDF/A-3a compliant")
    }

    @Test
    fun testConvertWithMultipleAttachments() = testApplication {
        application { module() }

        val html = """<!DOCTYPE html><html lang="en"><head><title>Doc</title>
            <meta name="subject" content="Multi"/></head><body><h1>Test</h1></body></html>"""

        val request = ConvertRequest(
            html = html,
            attachments = listOf(
                FileAttachment(
                    name = "factur-x.xml",
                    content = Base64.getEncoder().encodeToString("<invoice/>".toByteArray()),
                    mimeType = "text/xml",
                    relationship = "Alternative"
                ),
                FileAttachment(
                    name = "additional-data.csv",
                    content = Base64.getEncoder().encodeToString("col1,col2\nval1,val2".toByteArray()),
                    mimeType = "text/csv",
                    relationship = "Supplement"
                )
            )
        )

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdfBytes = response.readRawBytes()

        Loader.loadPDF(pdfBytes).use { document ->
            val embeddedFiles = document.documentCatalog.names.embeddedFiles.names
            assertEquals(2, embeddedFiles.size)
            assertTrue(embeddedFiles.containsKey("factur-x.xml"))
            assertTrue(embeddedFiles.containsKey("additional-data.csv"))
        }

        val validation = PdfValidationService.validatePdf(pdfBytes)
        assertTrue(validation.isCompliant)
    }

    @Test
    fun testConvertWithInvalidBase64Attachment() = testApplication {
        application { module() }

        val request = ConvertRequest(
            html = "<html><body><h1>Test</h1></body></html>",
            attachments = listOf(
                FileAttachment(name = "test.xml", content = "not-valid-base64!!!", mimeType = "text/xml")
            )
        )

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testConvertWithInvalidRelationship() = testApplication {
        application { module() }

        val request = ConvertRequest(
            html = "<html><body><h1>Test</h1></body></html>",
            attachments = listOf(
                FileAttachment(
                    name = "test.xml",
                    content = Base64.getEncoder().encodeToString("<xml/>".toByteArray()),
                    mimeType = "text/xml",
                    relationship = "InvalidValue"
                )
            )
        )

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), request))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

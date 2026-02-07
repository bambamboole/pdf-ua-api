package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.services.PdfValidationService
import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import java.io.File
import kotlin.test.*

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
            assertTrue(fixtureDir.exists() && fixtureDir.isDirectory,
                "Fixture directory not found: ${fixtureDir.absolutePath}")

            // Load fixture files
            val inputHtml = File(fixtureDir, "input.html").readText()
            val expectedPdfFile = File(fixtureDir, "expected.pdf")

            // Step 1: Convert HTML to PDF
            val convertResponse = client.post("/convert") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(inputHtml)))
            }

            assertEquals(HttpStatusCode.OK, convertResponse.status,
                "Fixture '$fixtureName': Convert endpoint should return 200 OK")
            assertEquals(ContentType.Application.Pdf, convertResponse.contentType(),
                "Fixture '$fixtureName': Response should be a PDF")

            val actualPdfBytes = convertResponse.readRawBytes()
            assertTrue(actualPdfBytes.isNotEmpty(),
                "Fixture '$fixtureName': PDF should not be empty")

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
}

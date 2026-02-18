package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.models.ValidationResponse
import bambamboole.pdf.api.module
import bambamboole.pdf.api.services.PdfValidationService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.*

/**
 * Tests for the /validate endpoint and fixture-based PDF/A validation.
 */
class ValidationRoutesTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Helper function to test PDF/A validation for a single fixture.
         * Validates the expected.pdf baseline directly (generation is tested in ConvertRoutesTest).
         */
        private suspend fun ApplicationTestBuilder.testFixtureValidation(fixtureName: String) {
            println("\n=== Testing fixture validation: $fixtureName ===")

            val fixtureDir = File(getSourceFixturesDir(), fixtureName)
            assertTrue(
                fixtureDir.exists() && fixtureDir.isDirectory,
                "Fixture directory not found: ${fixtureDir.absolutePath}"
            )

            val expectedPdfFile = File(fixtureDir, "expected.pdf")
            assertTrue(
                expectedPdfFile.exists(),
                "Fixture '$fixtureName': expected.pdf not found. Run ConvertRoutesTest to generate baselines."
            )

            val expectedValidation = json.decodeFromString(
                ValidationResponse.serializer(),
                File(fixtureDir, "expected-validation.json").readText()
            )

            val pdfBytes = expectedPdfFile.readBytes()
            assertTrue(
                pdfBytes.isNotEmpty(),
                "Fixture '$fixtureName': expected.pdf should not be empty"
            )

            val validateResponse = client.post("/validate") {
                contentType(ContentType.Application.Pdf)
                setBody(pdfBytes)
            }

            assertEquals(
                HttpStatusCode.OK, validateResponse.status,
                "Fixture '$fixtureName': Validate endpoint should return 200 OK"
            )

            val actualValidation = Json.decodeFromString(
                ValidationResponse.serializer(),
                validateResponse.bodyAsText()
            )

            assertEquals(
                expectedValidation.isCompliant, actualValidation.isCompliant,
                "Fixture '$fixtureName': Compliance status should match expected"
            )

            if (expectedValidation.isCompliant) {
                assertTrue(
                    actualValidation.isCompliant,
                    "Fixture '$fixtureName': PDF should be compliant"
                )
                assertEquals(
                    0, actualValidation.summary.failedChecks,
                    "Fixture '$fixtureName': Should have 0 failed checks"
                )
            }

            assertTrue(
                actualValidation.profiles.isNotEmpty(),
                "Fixture '$fixtureName': Should have profile results"
            )

            println(
                "Fixture '$fixtureName' validation: " +
                        "compliant=${actualValidation.isCompliant}, " +
                        "profiles=${actualValidation.profiles.map { "${it.profile}=${it.isCompliant}" }}, " +
                        "checks=${actualValidation.summary.totalChecks}, " +
                        "failed=${actualValidation.summary.failedChecks}"
            )

            if (actualValidation.metadata != null) {
                println(
                    "  Metadata: title='${actualValidation.metadata.title}', " +
                            "subject='${actualValidation.metadata.subject}', " +
                            "author='${actualValidation.metadata.author}', " +
                            "producer='${actualValidation.metadata.producer}'"
                )

                expectedValidation.metadata?.let { expected ->
                    val actual = actualValidation.metadata
                    expected.title?.let { assertEquals(it, actual.title, "Fixture '$fixtureName': Title should match") }
                    expected.subject?.let {
                        assertEquals(it, actual.subject, "Fixture '$fixtureName': Subject should match")
                    }
                    expected.author?.let {
                        assertEquals(it, actual.author, "Fixture '$fixtureName': Author should match")
                    }
                    expected.producer?.let {
                        assertEquals(it, actual.producer, "Fixture '$fixtureName': Producer should match")
                    }
                }
            }
        }

        private fun getSourceFixturesDir(): File {
            val fixturesUrl = ValidationRoutesTest::class.java.classLoader.getResource("fixtures")
                ?: fail("Fixtures directory not found in classpath")

            val buildFixturesDir = File(fixturesUrl.toURI())
            val projectRoot = buildFixturesDir.absolutePath.substringBefore("/app/build/")
            return File(projectRoot, "app/src/test/resources/fixtures")
        }
    }

    // ========================================
    // Basic Validation Tests
    // ========================================

    @Test
    fun testValidateEndpoint() = testApplication {
        application {
            module()
        }

        // Generate a PDF with PDF/UA metadata
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
        val pdfBytes = convertResponse.readRawBytes()

        // Validate the generated PDF via the API endpoint
        val validateResponse = client.post("/validate") {
            contentType(ContentType.Application.Pdf)
            setBody(pdfBytes)
        }

        assertEquals(HttpStatusCode.OK, validateResponse.status)

        // Parse validation response
        val validationResult = Json.decodeFromString<ValidationResponse>(validateResponse.bodyAsText())

        assertTrue(validationResult.isCompliant, "Generated PDF should be compliant")
        assertTrue(validationResult.profiles.isNotEmpty(), "Should have profile results")
        assertTrue(
            validationResult.profiles.any { it.profile == "PDF/A-3a" },
            "Should include PDF/A-3a profile"
        )
        assertTrue(
            validationResult.profiles.any { it.profile == "PDF/UA-1" },
            "Should include PDF/UA-1 profile"
        )
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
        val pdfBytes = convertResponse.readRawBytes()

        // Validate the PDF
        val validationResult = PdfValidationService.validatePdf(pdfBytes)

        assertNotNull(validationResult, "Validation should complete for any PDF")
        assertTrue(validationResult.profiles.isNotEmpty(), "Should have profile results")

        println("PDF validation with incomplete metadata: compliant=${validationResult.isCompliant}, profiles=${validationResult.profiles.map { it.profile }}")
    }

    // ========================================
    // Authentication Tests
    // ========================================

    @Test
    fun testValidateEndpointWithValidApiKey() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        // Generate a PDF first
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Test Document</title>
                <meta name="subject" content="Auth Test" />
            </head>
            <body><h1>Test</h1></body>
            </html>
        """.trimIndent()

        val convertResponse = client.post("/convert") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer test-api-key")
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, convertResponse.status)
        val pdfBytes = convertResponse.readRawBytes()

        // Validate with valid API key
        val validateResponse = client.post("/validate") {
            contentType(ContentType.Application.Pdf)
            header(HttpHeaders.Authorization, "Bearer test-api-key")
            setBody(pdfBytes)
        }

        assertEquals(HttpStatusCode.OK, validateResponse.status)
        val validationResult = Json.decodeFromString<ValidationResponse>(validateResponse.bodyAsText())
        assertTrue(validationResult.profiles.isNotEmpty())
    }

    @Test
    fun testValidateEndpointWithInvalidApiKey() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        val dummyPdfBytes = "%PDF-1.4\n".toByteArray()

        val response = client.post("/validate") {
            contentType(ContentType.Application.Pdf)
            header(HttpHeaders.Authorization, "Bearer wrong-key")
            setBody(dummyPdfBytes)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testValidateEndpointWithoutApiKeyWhenAuthEnabled() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application {
            module()
        }

        val dummyPdfBytes = "%PDF-1.4\n".toByteArray()

        val response = client.post("/validate") {
            contentType(ContentType.Application.Pdf)
            setBody(dummyPdfBytes)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ========================================
    // Fixture-Based Validation Tests
    // ========================================

    @Test
    fun testFixtureSimpleDocumentValidation() = testApplication {
        application { module() }
        testFixtureValidation("simple-document")
    }

    @Test
    fun testFixtureStyledTableValidation() = testApplication {
        application { module() }
        testFixtureValidation("styled-table")
    }

    @Test
    fun testFixtureFontVariationsValidation() = testApplication {
        application { module() }
        testFixtureValidation("font-variations")
    }

    @Test
    fun testFixtureCustomCreatorValidation() = testApplication {
        application { module() }
        testFixtureValidation("custom-creator")
    }

    @Test
    fun testFixtureInvoiceExample1Validation() = testApplication {
        application { module() }
        testFixtureValidation("invoice-example-1")
    }

    @Test
    fun testFixtureTablePaginationValidation() = testApplication {
        application { module() }
        testFixtureValidation("table-pagination")
    }

    @Test
    fun testFixtureExternalImageValidation() = testApplication {
        application { module() }
        testFixtureValidation("external-image")
    }

    @Test
    fun testFixtureExternalFontValidation() = testApplication {
        application { module() }
        testFixtureValidation("external-font")
    }
}

package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.models.ValidationResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.*

/**
 * Fixture-based tests for PDF conversion and validation.
 *
 * Each test fixture is a directory under `src/test/resources/fixtures/` containing:
 * - `input.html`: HTML content to convert to PDF
 * - `expected-validation.json`: Expected validation response from the /validate endpoint
 *
 * To add new test cases, simply create a new directory with these two files.
 */
class FixtureBasedConversionTest {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Discovers all fixture directories in the test resources.
         * Each fixture directory must contain input.html and expected-validation.json
         */
        fun getFixtures(): List<TestFixture> {
            // Get the fixtures directory from the classpath
            val fixturesUrl = FixtureBasedConversionTest::class.java.classLoader.getResource("fixtures")
                ?: fail("Fixtures directory not found in classpath")

            val fixturesDir = File(fixturesUrl.toURI())
            if (!fixturesDir.exists() || !fixturesDir.isDirectory) {
                fail("Fixtures directory not found or is not a directory: ${fixturesDir.absolutePath}")
            }

            return fixturesDir.listFiles { file -> file.isDirectory }
                ?.filter { dir ->
                    File(dir, "input.html").exists() &&
                    File(dir, "expected-validation.json").exists()
                }
                ?.map { dir ->
                    TestFixture(
                        name = dir.name,
                        htmlContent = File(dir, "input.html").readText(),
                        expectedValidation = json.decodeFromString(
                            ValidationResponse.serializer(),
                            File(dir, "expected-validation.json").readText()
                        )
                    )
                }
                ?: emptyList()
        }
    }

    data class TestFixture(
        val name: String,
        val htmlContent: String,
        val expectedValidation: ValidationResponse
    )

    @Test
    fun testAllFixtures() {
        val fixtures = getFixtures()
        assertTrue(fixtures.isNotEmpty(), "No test fixtures found in app/src/test/resources/fixtures/")

        println("Found ${fixtures.size} test fixture(s): ${fixtures.joinToString(", ") { it.name }}")

        testApplication {
            application {
                module()
            }

            fixtures.forEach { fixture ->
                println("\n=== Testing fixture: ${fixture.name} ===")

                // Step 1: Convert HTML to PDF
                val convertResponse = client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(fixture.htmlContent)))
                }

                assertEquals(HttpStatusCode.OK, convertResponse.status,
                    "Fixture '${fixture.name}': Convert endpoint should return 200 OK")
                assertEquals(ContentType.Application.Pdf, convertResponse.contentType(),
                    "Fixture '${fixture.name}': Response should be a PDF")

                val pdfBytes = convertResponse.readRawBytes()
                assertTrue(pdfBytes.isNotEmpty(),
                    "Fixture '${fixture.name}': PDF should not be empty")

                // Verify PDF header
                val pdfHeader = pdfBytes.take(5).toByteArray().decodeToString()
                assertTrue(pdfHeader.startsWith("%PDF-"),
                    "Fixture '${fixture.name}': Response should start with PDF magic bytes")

                // Step 2: Validate the PDF
                val validateResponse = client.post("/validate") {
                    contentType(ContentType.Application.Pdf)
                    setBody(pdfBytes)
                }

                assertEquals(HttpStatusCode.OK, validateResponse.status,
                    "Fixture '${fixture.name}': Validate endpoint should return 200 OK")

                val actualValidation = Json.decodeFromString(
                    ValidationResponse.serializer(),
                    validateResponse.bodyAsText()
                )

                // Step 3: Compare validation results
                assertEquals(fixture.expectedValidation.isCompliant, actualValidation.isCompliant,
                    "Fixture '${fixture.name}': Compliance status should match expected")

                assertEquals(fixture.expectedValidation.flavour, actualValidation.flavour,
                    "Fixture '${fixture.name}': PDF/A flavour should match expected")

                // If we expect it to be compliant, verify no failures
                if (fixture.expectedValidation.isCompliant) {
                    assertTrue(actualValidation.isCompliant,
                        "Fixture '${fixture.name}': PDF should be compliant")
                    assertEquals(0, actualValidation.failedChecks,
                        "Fixture '${fixture.name}': Should have 0 failed checks")
                }

                // Log validation summary
                println("Fixture '${fixture.name}' validation: " +
                    "compliant=${actualValidation.isCompliant}, " +
                    "flavour=${actualValidation.flavour}, " +
                    "checks=${actualValidation.totalChecks}, " +
                    "failed=${actualValidation.failedChecks}")

                if (!actualValidation.isCompliant && actualValidation.failures.isNotEmpty()) {
                    println("  Failures:")
                    actualValidation.failures.take(3).forEach { failure ->
                        println("    - ${failure.clause}: ${failure.message}")
                    }
                }
            }
        }
    }
}

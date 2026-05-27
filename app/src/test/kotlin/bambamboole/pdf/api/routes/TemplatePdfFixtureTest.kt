package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import bambamboole.pdf.api.services.PdfValidationService
import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Renders a template fixture through POST /render/template and visual-regresses the PDF
 * against a committed expected.pdf baseline.
 */
class TemplatePdfFixtureTest {

    companion object {
        private fun templateFixturesDir(): File {
            val url = TemplatePdfFixtureTest::class.java.classLoader.getResource("fixtures/template")
                ?: fail("fixtures/template directory not found in classpath")
            val projectRoot = File(url.toURI()).absolutePath.substringBefore("/app/build/")
            return File(projectRoot, "app/src/test/resources/fixtures/template")
        }

        private suspend fun ApplicationTestBuilder.assertTemplatePdfFixture(name: String, embeddedFont: String) {
            val dir = File(templateFixturesDir(), name)
            val body = File(dir, "input.json").readText()

            val response = client.post("/render/template") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status, "Fixture '$name': should return 200 OK")
            val pdf = response.readRawBytes()
            assertTrue(pdf.isNotEmpty(), "Fixture '$name': PDF should not be empty")

            val validation = PdfValidationService.validatePdf(pdf)
            assertTrue(validation.isCompliant, "Fixture '$name': PDF must be PDF/A-3a compliant")
            val fontNames = validation.documentInfo!!.fonts.map { it.name }
            assertTrue(
                fontNames.any { it.contains(embeddedFont, ignoreCase = true) },
                "Fixture '$name': '$embeddedFont' must be embedded. Found: $fontNames",
            )

            val generated = File(dir, "generated.pdf")
            if (generated.exists()) generated.delete()
            generated.writeBytes(pdf)

            val expected = File(dir, "expected.pdf")
            if (!expected.exists()) {
                expected.writeBytes(pdf)
                println("Fixture '$name': saved expected.pdf baseline")
                return
            }

            val results = PdfVisualTester.comparePdfDocuments(expected.readBytes(), pdf, name, false)
            val problems = results.filter { it.type != PdfVisualTester.ProblemType.PAGE_GOOD }
            if (problems.isNotEmpty()) {
                problems.forEach { diff ->
                    if (diff.type == PdfVisualTester.ProblemType.PAGE_VISUALLY_DIFFERENT && diff.testImages.hasDifferences()) {
                        ImageIO.write(diff.testImages.createDiff(), "PNG", File(dir, "page-${diff.pageNumber}-diff.png"))
                    }
                }
                fail("Fixture '$name': PDF visual regression detected. See diffs in ${dir.absolutePath}")
            }
        }
    }

    @Test
    fun externalFont() = testApplication {
        application { module() }
        assertTemplatePdfFixture("external-font", embeddedFont = "Lobster")
    }

    @Test
    fun pageBackground() = testApplication {
        application { module() }
        assertTemplatePdfFixture("page-background", embeddedFont = "LiberationSans")
    }

    @Test
    fun keyValueBasic() = testApplication {
        application { module() }
        assertTemplatePdfFixture("key-value-basic", embeddedFont = "Inter")
    }

    @Test
    fun keyValueRuntime() = testApplication {
        application { module() }
        assertTemplatePdfFixture("key-value-runtime", embeddedFont = "Inter")
    }
}

package bambamboole.pdfua.http.controller

import bambamboole.pdfua.module
import bambamboole.pdfua.pdf.PdfValidator
import com.openhtmltopdf.pdfboxout.visualtester.PdfVisualTester
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.image.BufferedImage
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
            val url =
                TemplatePdfFixtureTest::class.java.classLoader.getResource("fixtures/template")
                    ?: fail("fixtures/template directory not found in classpath")
            val projectRoot = File(url.toURI()).absolutePath.substringBefore("/build/")
            return File(projectRoot, "src/test/resources/fixtures/template")
        }

        private suspend fun ApplicationTestBuilder.assertTemplatePdfFixture(
            name: String,
            embeddedFont: String,
            pageTextAssertions: Map<Int, List<String>> = emptyMap(),
            keepTogetherGroups: List<List<String>> = emptyList(),
            visualTolerance: VisualTolerance = VisualTolerance.strict,
        ) {
            val dir = File(templateFixturesDir(), name)
            val body = File(dir, "input.json").readText()

            val response =
                client.post("/render/template") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            assertEquals(HttpStatusCode.OK, response.status, "Fixture '$name': should return 200 OK")
            val pdf = response.readRawBytes()
            assertTrue(pdf.isNotEmpty(), "Fixture '$name': PDF should not be empty")

            val validation = PdfValidator.validatePdf(pdf)
            assertTrue(validation.isCompliant, "Fixture '$name': PDF must be PDF/A-3a compliant")
            val fontNames = validation.documentInfo!!.fonts.map { it.name }
            assertTrue(
                fontNames.any { it.contains(embeddedFont, ignoreCase = true) },
                "Fixture '$name': '$embeddedFont' must be embedded. Found: $fontNames",
            )
            if (pageTextAssertions.isNotEmpty()) {
                assertPdfPageText(name, pdf, pageTextAssertions)
            }
            if (keepTogetherGroups.isNotEmpty()) {
                assertKeepsTogether(name, pdf, keepTogetherGroups)
            }

            val generated = File(dir, "generated.pdf")
            if (generated.exists()) generated.delete()
            generated.writeBytes(pdf)

            val expected = File(dir, "expected.pdf")
            if (!expected.exists()) {
                expected.writeBytes(pdf)
                println("Fixture '$name': saved expected.pdf baseline")
                return
            }

            val problems = comparePdfDocuments(expected.readBytes(), pdf, visualTolerance)
            if (problems.isNotEmpty()) {
                problems.forEach { diff ->
                    if (diff.expectedImage != null && diff.actualImage != null) {
                        ImageIO.write(
                            PdfVisualTester.createDiffImage(diff.expectedImage, diff.actualImage),
                            "PNG",
                            File(dir, "page-${diff.pageNumber}-diff.png"),
                        )
                    }
                }
                fail(
                    "Fixture '$name': PDF visual regression detected: ${
                        problems.joinToString { it.message }
                    }. See diffs in ${dir.absolutePath}",
                )
            }
        }

        private data class VisualTolerance(
            val maxChangedPixelRatio: Double,
        ) {
            companion object {
                val strict = VisualTolerance(maxChangedPixelRatio = 0.0)
                val svgPlatformDrift = VisualTolerance(maxChangedPixelRatio = 0.004)
            }
        }

        private data class PdfVisualProblem(
            val pageNumber: Int,
            val message: String,
            val expectedImage: BufferedImage? = null,
            val actualImage: BufferedImage? = null,
        )

        private fun comparePdfDocuments(
            expected: ByteArray,
            actual: ByteArray,
            tolerance: VisualTolerance,
        ): List<PdfVisualProblem> =
            Loader.loadPDF(expected).use { expectedDocument ->
                Loader.loadPDF(actual).use { actualDocument ->
                    val problems = mutableListOf<PdfVisualProblem>()
                    if (expectedDocument.numberOfPages != actualDocument.numberOfPages) {
                        problems +=
                            PdfVisualProblem(
                                pageNumber = -1,
                                message =
                                    "expected ${expectedDocument.numberOfPages} page(s), got ${actualDocument.numberOfPages}",
                            )
                    }

                    val expectedRenderer = PDFRenderer(expectedDocument)
                    val actualRenderer = PDFRenderer(actualDocument)
                    repeat(expectedDocument.numberOfPages) { pageIndex ->
                        val expectedImage = expectedRenderer.renderImageWithDPI(pageIndex, 96f, ImageType.RGB)
                        val actualImage =
                            actualDocument
                                .takeIf { pageIndex < it.numberOfPages }
                                ?.let { actualRenderer.renderImageWithDPI(pageIndex, 96f, ImageType.RGB) }

                        if (actualImage == null) {
                            problems +=
                                PdfVisualProblem(
                                    pageNumber = pageIndex,
                                    message = "page $pageIndex is missing",
                                    expectedImage = expectedImage,
                                )
                            return@repeat
                        }
                        if (expectedImage.width != actualImage.width || expectedImage.height != actualImage.height) {
                            problems +=
                                PdfVisualProblem(
                                    pageNumber = pageIndex,
                                    message =
                                        "page $pageIndex size changed from ${expectedImage.width}x${expectedImage.height} " +
                                            "to ${actualImage.width}x${actualImage.height}",
                                    expectedImage = expectedImage,
                                    actualImage = actualImage,
                                )
                            return@repeat
                        }

                        val changedPixelRatio = changedPixelRatio(expectedImage, actualImage)
                        if (changedPixelRatio > tolerance.maxChangedPixelRatio) {
                            problems +=
                                PdfVisualProblem(
                                    pageNumber = pageIndex,
                                    message =
                                        "page $pageIndex changed ${
                                            "%.5f".format(changedPixelRatio)
                                        } of pixels, allowed ${tolerance.maxChangedPixelRatio}",
                                    expectedImage = expectedImage,
                                    actualImage = actualImage,
                                )
                        }
                    }
                    problems
                }
            }

        private fun changedPixelRatio(
            expected: BufferedImage,
            actual: BufferedImage,
        ): Double {
            var changedPixels = 0
            for (y in 0 until expected.height) {
                for (x in 0 until expected.width) {
                    if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
                        changedPixels += 1
                    }
                }
            }
            return changedPixels.toDouble() / (expected.width * expected.height)
        }

        private fun assertPdfPageText(
            name: String,
            pdf: ByteArray,
            pageTextAssertions: Map<Int, List<String>>,
        ) {
            Loader.loadPDF(pdf).use { document ->
                val maxAssertedPage = pageTextAssertions.keys.maxOrNull() ?: 0
                assertTrue(
                    document.numberOfPages >= maxAssertedPage,
                    "Fixture '$name': needs at least $maxAssertedPage page(s); rendered ${document.numberOfPages}",
                )
                val stripper = PDFTextStripper()
                pageTextAssertions.forEach { (page, expectedTexts) ->
                    stripper.startPage = page
                    stripper.endPage = page
                    val pageText = stripper.getText(document).normalizedForMatch()
                    expectedTexts.forEach { expectedText ->
                        assertTrue(
                            pageText.contains(expectedText),
                            "Fixture '$name': page $page should contain '$expectedText'. Page text: $pageText",
                        )
                    }
                }
            }
        }

        private fun String.normalizedForMatch(): String = replace(Regex("\\s+"), " ")

        private fun assertKeepsTogether(
            name: String,
            pdf: ByteArray,
            groups: List<List<String>>,
        ) {
            Loader.loadPDF(pdf).use { document ->
                val stripper = PDFTextStripper()
                val pageTexts =
                    (1..document.numberOfPages).map { page ->
                        stripper.startPage = page
                        stripper.endPage = page
                        stripper.getText(document).normalizedForMatch()
                    }
                groups.forEach { group ->
                    val pages =
                        group.map { snippet ->
                            val match = pageTexts.indexOfFirst { it.contains(snippet) }
                            if (match < 0) {
                                fail(
                                    "Fixture '$name': snippet '$snippet' not found on any page; group $group cannot be evaluated",
                                )
                            }
                            match + 1
                        }
                    val distinct = pages.toSet()
                    assertTrue(
                        distinct.size == 1,
                        "Fixture '$name': block content split across pages $pages for group $group",
                    )
                }
            }
        }
    }

    @Test
    fun externalFont() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture("external-font", embeddedFont = "Lobster")
        }

    @Test
    fun pageBackground() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture("page-background", embeddedFont = "LiberationSans")
        }

    @Test
    fun keyValueBasic() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture("key-value-basic", embeddedFont = "Inter")
        }

    @Test
    fun keyValueRuntime() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture("key-value-runtime", embeddedFont = "Inter")
        }

    @Test
    fun repeatedFooter() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "repeated-footer",
                embeddedFont = "LiberationSans",
                pageTextAssertions = mapOf(2 to listOf("Runtime footer for every page")),
            )
        }

    @Test
    fun repeatedFooterRightPageNumbers() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "repeated-footer-right-page-numbers",
                embeddedFont = "LiberationSans",
                pageTextAssertions = mapOf(2 to listOf("Runtime footer plus right page numbers")),
            )
        }

    @Test
    fun footerWithLeftPageNumbers() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "footer-with-left-page-numbers",
                embeddedFont = "Inter",
                pageTextAssertions =
                    mapOf(
                        1 to listOf("Acme Industries GmbH"),
                        2 to listOf("Acme Industries GmbH"),
                    ),
            )
        }

    @Test
    fun invoice() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "invoice",
                embeddedFont = "Inter",
                pageTextAssertions =
                    mapOf(
                        1 to
                            listOf(
                                "RE-2026-001234",
                                "Issue date",
                                "Max Mustermann",
                                "Musterkunde AG",
                                "Currency EUR",
                            ),
                    ),
                keepTogetherGroups =
                    listOf(
                        listOf("Invoice no.", "RE-2026-001234", "Currency EUR"),
                        listOf("Seller", "Musterstraße 1", "VAT ID"),
                        listOf("Buyer", "Buyer reference"),
                        listOf("Subtotal", "VAT 19%", "Grand total", "7.282,80"),
                        listOf("Bank", "IBAN", "BIC"),
                    ),
                visualTolerance = VisualTolerance.svgPlatformDrift,
            )
        }

    @Test
    fun footerWithCenterPageNumbers() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "footer-with-center-page-numbers",
                embeddedFont = "Inter",
                pageTextAssertions =
                    mapOf(
                        1 to listOf("Acme Industries GmbH"),
                        2 to listOf("Acme Industries GmbH"),
                    ),
            )
        }

    @Test
    fun dunningNotice() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "dunning-notice",
                embeddedFont = "Inter",
                pageTextAssertions =
                    mapOf(
                        1 to
                            listOf(
                                "MA-2026-00018",
                                "Notice number",
                                "Musterkunde AG",
                                "04011000-12345-67",
                            ),
                    ),
                keepTogetherGroups =
                    listOf(
                        listOf("Notice number", "MA-2026-00018", "Payment due"),
                        listOf("Creditor", "Musterstraße 1", "VAT ID"),
                        listOf("Debtor", "Debtor reference"),
                        listOf("Amount due", "Bank", "IBAN", "BIC", "Payment reference"),
                    ),
            )
        }

    @Test
    fun footerWithRightPageNumbers() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "footer-with-right-page-numbers",
                embeddedFont = "Inter",
                pageTextAssertions =
                    mapOf(
                        1 to listOf("Acme Industries GmbH"),
                        2 to listOf("Acme Industries GmbH"),
                    ),
            )
        }

    @Test
    fun quote() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "quote",
                embeddedFont = "Inter",
                pageTextAssertions =
                    mapOf(
                        1 to
                            listOf(
                                "AN-2026-000087",
                                "Valid until",
                                "Maria Beispiel",
                                "Musterkunde AG",
                                "Quote / Proposal",
                                "Grand total",
                                "9.210,60 €",
                            ),
                    ),
                keepTogetherGroups =
                    listOf(
                        listOf("Quote no.", "AN-2026-000087", "Account manager"),
                        listOf("Provider", "Musterstraße 1"),
                        listOf("Customer", "Musterkunde AG"),
                        listOf("Subtotal", "VAT 19%", "Grand total", "9.210,60"),
                    ),
                visualTolerance = VisualTolerance.svgPlatformDrift,
            )
        }

    @Test
    fun barcodeLabel() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "barcode-label",
                embeddedFont = "Inter",
                visualTolerance = VisualTolerance.svgPlatformDrift,
            )
        }

    @Test
    fun swissQrBill() =
        testApplication {
            application { module() }
            assertTemplatePdfFixture(
                "swiss-qr-bill",
                embeddedFont = "Inter",
                visualTolerance = VisualTolerance.svgPlatformDrift,
            )
        }
}

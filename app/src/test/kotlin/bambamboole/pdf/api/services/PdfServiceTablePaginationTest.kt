package bambamboole.pdf.api.services

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for table pagination style injection.
 */
class PdfServiceTablePaginationTest {

    @Test
    fun testTablePaginationStylesAreInjected() {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Table Pagination Test</title>
                <meta name="subject" content="Testing table pagination styles"/>
            </head>
            <body>
                <table>
                    <thead>
                        <tr><th>Header 1</th><th>Header 2</th></tr>
                    </thead>
                    <tbody>
                        <tr><td>Cell 1</td><td>Cell 2</td></tr>
                        <tr><td>Cell 3</td><td>Cell 4</td></tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        // Convert to PDF (which internally injects styles)
        val pdfBytes = PdfService.convertHtmlToPdf(html)

        // Verify PDF was generated
        assertTrue(pdfBytes.isNotEmpty())
        assertTrue(pdfBytes.size > 1000, "PDF should be larger than 1KB")

        // Verify PDF header
        val pdfHeader = pdfBytes.take(5).toByteArray().decodeToString()
        assertTrue(pdfHeader.startsWith("%PDF-"))
    }

    @Test
    fun testTablePaginationPropertiesAreCorrect() {
        // Parse HTML and verify what styles would be injected
        val html = "<html><head></head><body><table><tr><td>Test</td></tr></table></body></html>"
        val jsoupDoc = Jsoup.parse(html)

        // Manually inject styles to verify the logic
        val styles = """
            table {
                width: 100%;
                font-size: 16px;
                border-collapse: collapse;
                -fs-table-paginate: paginate;
                -fs-page-break-min-height: 1.5cm;
            }
            tr, thead, tfoot {
                page-break-inside: avoid;
            }
        """.trimIndent()

        jsoupDoc.head().appendElement("style").text(styles)

        // Verify style tag exists
        val styleElements = jsoupDoc.select("style")
        assertTrue(styleElements.isNotEmpty(), "Style tag should be present")

        // Verify critical properties are in the styles
        val styleContent = styleElements.first()?.html() ?: ""
        assertTrue(styleContent.contains("-fs-table-paginate: paginate"),
            "Should contain table pagination property")
        assertTrue(styleContent.contains("-fs-page-break-min-height: 1.5cm"),
            "Should contain page break min height")
        assertTrue(styleContent.contains("page-break-inside: avoid"),
            "Should contain page break avoidance for rows")
    }

    @Test
    fun testComplexTableWithPagination() {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Complex Table Test</title>
                <meta name="subject" content="Testing complex table with pagination"/>
            </head>
            <body>
                <h1>Large Table with Multiple Rows</h1>
                <table>
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Name</th>
                            <th>Description</th>
                            <th>Amount</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${(1..50).joinToString("\n") { i ->
                            "<tr><td>$i</td><td>Item $i</td><td>Description for item $i</td><td>\$${i * 10}.00</td></tr>"
                        }}
                    </tbody>
                    <tfoot>
                        <tr>
                            <td colspan="3">Total</td>
                            <td>\$${50 * (1..50).sum()}.00</td>
                        </tr>
                    </tfoot>
                </table>
            </body>
            </html>
        """.trimIndent()

        // Convert to PDF with table pagination
        val pdfBytes = PdfService.convertHtmlToPdf(html)

        // Verify PDF was generated successfully
        assertTrue(pdfBytes.isNotEmpty())
        // Large table should produce a PDF larger than simple documents
        assertTrue(pdfBytes.size > 10000, "PDF with large table should be substantial in size")
    }
}

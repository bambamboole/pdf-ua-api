package bambamboole.pdfua.http.controller

import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.pdf.PdfValidator
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertTrue

class LargeDocumentTest {
    @Test
    @Disabled("Long-running performance coverage; run manually when changing large document rendering.")
    fun testConvert50kLineHtml() {
        val html = buildLargeHtml(tableRows = 8_000, paragraphs = 2_000)
        val lineCount = html.count { it == '\n' }
        println("Generated HTML: $lineCount lines, ${html.length / 1024} KB")

        val startTime = System.currentTimeMillis()
        val pdfBytes = PdfRenderer.convertHtmlToPdf(html).bytes
        val conversionTime = System.currentTimeMillis() - startTime

        println("Conversion time: ${conversionTime}ms, PDF size: ${pdfBytes.size / 1024} KB")
        assertTrue(pdfBytes.size > 1000, "PDF should not be blank")

        val validation = PdfValidator.validatePdf(pdfBytes)
        assertTrue(validation.isCompliant, "Large document must remain PDF/A-3a compliant: ${validation.failures}")
    }

    private fun buildLargeHtml(
        tableRows: Int,
        paragraphs: Int,
    ): String {
        val sb = StringBuilder(tableRows * 200 + paragraphs * 400)
        sb.appendLine("""<!DOCTYPE html>""")
        sb.appendLine("""<html lang="en">""")
        sb.appendLine("""<head>""")
        sb.appendLine("""  <title>Large Document Performance Test</title>""")
        sb.appendLine("""  <meta name="subject" content="Performance test"/>""")
        sb.appendLine("""  <meta name="author" content="Test"/>""")
        sb.appendLine("""  <style>""")
        sb.appendLine("""    body { font-size: 10px; margin: 10mm; }""")
        sb.appendLine("""    table { width: 100%; border-collapse: collapse; }""")
        sb.appendLine("""    th, td { border: 1px solid #333; padding: 2px 4px; text-align: left; }""")
        sb.appendLine("""    th { background-color: #ddd; }""")
        sb.appendLine("""    .even { background-color: #f9f9f9; }""")
        sb.appendLine("""    h2 { margin-top: 1em; }""")
        sb.appendLine("""  </style>""")
        sb.appendLine("""</head>""")
        sb.appendLine("""<body>""")

        sb.appendLine("""  <h1>Performance Test Report</h1>""")
        sb.appendLine("""  <p>This document contains $tableRows table rows and $paragraphs paragraphs.</p>""")

        sb.appendLine("""  <h2>Section 1: Data Table</h2>""")
        sb.appendLine("""  <table>""")
        sb.appendLine("""    <thead>""")
        sb.appendLine("""      <tr>""")
        sb.appendLine("""        <th>#</th>""")
        sb.appendLine("""        <th>Name</th>""")
        sb.appendLine("""        <th>Description</th>""")
        sb.appendLine("""        <th>Amount</th>""")
        sb.appendLine("""        <th>Status</th>""")
        sb.appendLine("""      </tr>""")
        sb.appendLine("""    </thead>""")
        sb.appendLine("""    <tbody>""")
        for (i in 1..tableRows) {
            val cssClass = if (i % 2 == 0) """ class="even"""" else ""
            sb.appendLine("""      <tr$cssClass>""")
            sb.appendLine("""        <td>$i</td>""")
            sb.appendLine("""        <td>Item-$i</td>""")
            sb.appendLine("""        <td>Description for row number $i in the large document performance test</td>""")
            sb.appendLine("""        <td>${"%.2f".format(i * 1.23)}</td>""")
            sb.appendLine("""        <td>${if (i % 3 == 0) "Pending" else "Done"}</td>""")
            sb.appendLine("""      </tr>""")
        }
        sb.appendLine("""    </tbody>""")
        sb.appendLine("""  </table>""")

        sb.appendLine("""  <h2>Section 2: Text Content</h2>""")
        for (i in 1..paragraphs) {
            sb.appendLine("""  <p>""")
            sb.appendLine("""    Paragraph $i: Lorem ipsum dolor sit amet, consectetur adipiscing elit.""")
            sb.appendLine("""    Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.""")
            sb.appendLine("""    Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.""")
            sb.appendLine("""  </p>""")
        }

        sb.appendLine("""</body>""")
        sb.appendLine("""</html>""")
        return sb.toString()
    }
}

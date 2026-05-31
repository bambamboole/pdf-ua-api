package bambamboole.pdfua.hyphenation

import bambamboole.pdfua.pdf.PdfRenderer
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertTrue

class HyphenatorPipelineTest {
    // Single very long word in a column so narrow it must break with a hyphen.
    private val veryNarrowGerman =
        """
        <!DOCTYPE html>
        <html lang="de-DE">
        <head><meta charset="utf-8"><title>t</title>
        <style>
        @page { size: 30mm 90mm; margin: 2mm; }
        body { font-size: 12pt; line-height: 14pt; hyphens: auto; }
        </style>
        </head>
        <body><p>Donaudampfschifffahrtsgesellschaft</p></body>
        </html>
        """.trimIndent()

    private val veryNarrowEnglish =
        veryNarrowGerman
            .replace("lang=\"de-DE\"", "lang=\"en-US\"")
            .replace("Donaudampfschifffahrtsgesellschaft", "Antidisestablishmentarianism")

    @Test
    fun germanLongWordsWrapWithHyphens() {
        val pdf = PdfRenderer.convertHtmlToPdf(html = veryNarrowGerman, baseUrl = "").bytes
        val text = extractText(pdf)
        val lines = text.lines().filter { it.isNotBlank() }
        // The visible signature of a wrap at a soft-hyphen point is a line that ends with '-'.
        val anyWraps = lines.any { it.trimEnd().endsWith("-") }
        assertTrue(anyWraps, "expected at least one hyphenated line break — content was: $lines")
    }

    @Test
    fun englishLongWordsWrapWithHyphens() {
        val pdf = PdfRenderer.convertHtmlToPdf(html = veryNarrowEnglish, baseUrl = "").bytes
        val text = extractText(pdf)
        val lines = text.lines().filter { it.isNotBlank() }
        val anyWraps = lines.any { it.trimEnd().endsWith("-") }
        assertTrue(anyWraps, "expected hyphenation in English narrow column — content was: $lines")
    }

    @Test
    fun renderWithoutLangDoesNotCrash() {
        val html = veryNarrowGerman.replace("lang=\"de-DE\"", "")
        val pdf = PdfRenderer.convertHtmlToPdf(html = html, baseUrl = "").bytes
        // Just exercise the path — no lang → no hyphenator → no hyphens, but rendering still works.
        val text = extractText(pdf).trim()
        assertTrue(text.isNotEmpty(), "expected some text in PDF")
    }

    private fun extractText(pdfBytes: ByteArray): String = Loader.loadPDF(pdfBytes).use { doc -> PDFTextStripper().getText(doc) }
}

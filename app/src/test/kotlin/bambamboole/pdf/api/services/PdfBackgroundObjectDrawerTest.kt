package bambamboole.pdf.api.services

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertTrue

class PdfBackgroundObjectDrawerTest {

    @Test
    fun rendersPdfBackgroundObject() {
        val background = Base64.getEncoder().encodeToString(blankA4Pdf())
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>Background Test</title>
              <style>
                @page { @top-left { content: element(stationary); } }
                .stationary { position: running(stationary); }
              </style>
            </head>
            <body>
              <div class="stationary">
                <object type="pdf/background" pdfsrc="data:application/pdf;base64,$background" style="width:1px;height:1px"></object>
              </div>
              <p>Hello</p>
            </body>
            </html>
        """.trimIndent()

        val result = PdfService.convertHtmlToPdf(html)

        assertTrue(result.bytes.take(5).toByteArray().decodeToString().startsWith("%PDF-"))
    }

    private fun blankA4Pdf(): ByteArray =
        PDDocument().use { document ->
            document.addPage(PDPage(PDRectangle.A4))
            ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
}

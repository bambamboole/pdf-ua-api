package bambamboole.pdfua.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class BackgroundObjectDrawerTest {
    private fun imageDataUrl(): String {
        val img = BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 300, 420)
        g.color = Color(0xd1, 0xd5, 0xdb)
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString("CONFIDENTIAL", 30, 210)
        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun pdfDataUrl(): String {
        val bytes =
            PDDocument().use { doc ->
                doc.addPage(PDPage(PDRectangle.A4))
                ByteArrayOutputStream().use { out ->
                    doc.save(out)
                    out.toByteArray()
                }
            }
        return "data:application/pdf;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    private fun htmlWith(
        dataUrl: String,
        kind: String,
    ): String =
        """
        <!DOCTYPE html><html lang="en"><head><meta charset="UTF-8"><title>t</title>
        <style>
        @page { size: A4; margin: 20mm; }
        .page-bg { position: running(pagebg); }
        @page { @top-left { content: element(pagebg); } }
        body { font-family: 'Liberation Sans'; }
        </style></head>
        <body>
        <div class="page-bg"><object type="${BackgroundObjectDrawer.OBJECT_TYPE}" data-src="$dataUrl" data-kind="$kind" style="width:1px;height:1px"></object></div>
        <p>Visible content over the background.</p>
        </body></html>
        """.trimIndent()

    @Test
    fun imageBackgroundIsStampedAndCompliant() {
        val pdf = PdfRenderer.convertHtmlToPdf(htmlWith(imageDataUrl(), "image")).bytes
        Loader.loadPDF(pdf).use { doc ->
            val res = doc.getPage(0).resources
            val hasImage = res.xObjectNames.any { res.getXObject(it) is PDImageXObject }
            assertTrue(hasImage, "background image must be stamped as an image XObject")
        }
        assertTrue(
            PdfValidator.validatePdf(pdf).isCompliant,
            "image background PDF must be PDF/A-3a compliant (background must be artifact-marked)",
        )
    }

    @Test
    fun pdfBackgroundProducesCompliantPdf() {
        val pdf = PdfRenderer.convertHtmlToPdf(htmlWith(pdfDataUrl(), "pdf")).bytes
        assertTrue(pdf.decodeToString(0, 5).startsWith("%PDF-"))
        org.apache.pdfbox.Loader.loadPDF(pdf).use { doc ->
            val res = doc.getPage(0).resources
            assertTrue(
                res.xObjectNames.any { res.getXObject(it) is PDFormXObject },
                "PDF background must be stamped as a form XObject",
            )
        }
        assertTrue(
            PdfValidator.validatePdf(pdf).isCompliant,
            "pdf background must be PDF/A-3a compliant",
        )
    }
}

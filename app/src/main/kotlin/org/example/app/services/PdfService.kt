package org.example.app.services

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayOutputStream

object PdfService {
    /**
     * Converts HTML string to PDF bytes
     * @param html Well-formed XHTML string
     * @return PDF as byte array
     * @throws Exception if HTML is malformed or conversion fails
     */
    fun convertHtmlToPdf(html: String): ByteArray {
        if (html.isBlank()) {
            throw IllegalArgumentException("HTML content cannot be empty")
        }

        return ByteArrayOutputStream().use { outputStream ->
            PdfRendererBuilder()
                .withHtmlContent(html, "file:///")  // Base URL required for resolving relative resources
                .toStream(outputStream)
                .run()
            outputStream.toByteArray()
        }
    }
}

package bambamboole.pdfua.services

import bambamboole.pdfua.http.controller.RenderRequest
import bambamboole.pdfua.template.Row
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.TextBlock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TemplatePdfRenderServiceTest {
    @Test
    fun rendersTemplateToPdf() {
        val service = TemplatePdfRenderService()
        val template = Template(version = 2, rows = listOf(Row(listOf(TextBlock(text = "Hello from MCP")))))

        val result = service.render(template)

        val success = assertIs<TemplatePdfRenderResult.Success>(result)
        assertTrue(
            success.pdf.bytes
                .take(5)
                .toByteArray()
                .decodeToString()
                .startsWith("%PDF-"),
        )
        assertTrue(success.pdf.documentId.isNotBlank())
    }

    @Test
    fun returnsValidationIssuesForInvalidTemplate() {
        val service = TemplatePdfRenderService()

        val result = service.render(Template(version = 1))

        val failure = assertIs<TemplatePdfRenderResult.ValidationFailed>(result)
        assertEquals(1, failure.issues.size)
    }

    @Test
    fun invalidBarcodeContentReturnsValidationFailedNotException() {
        val request =
            Json.decodeFromString(
                RenderRequest.serializer(),
                """{"template":{"version":2,"config":{"page":{"size":{"format":"A4"}}},"rows":[{"blocks":[{"type":"barcode","symbology":"ean13","content":{"type":"raw","value":"not-a-number"}}]}]}}""",
            )
        val result = TemplatePdfRenderService().render(request.template, request.data)
        assertIs<TemplatePdfRenderResult.ValidationFailed>(result)
    }

    @Test
    fun invalidBarcodeSurfacesAsValidationIssueWithBlockPath() {
        val request =
            Json.decodeFromString(
                RenderRequest.serializer(),
                """{"template":{"version":2,"config":{"page":{"size":{"format":"A4"}}},"rows":[{"blocks":[{"type":"barcode","symbology":"ean13","content":{"type":"raw","value":"not-a-number"}}]}]}}""",
            )
        val result = TemplatePdfRenderService().render(request.template, request.data)
        val failed = assertIs<TemplatePdfRenderResult.ValidationFailed>(result)
        // The issue must point at the offending block, not the generic root "$".
        assertEquals(true, failed.issues.any { it.path.startsWith("$.template.rows[0].blocks[0]") })
    }

    @Test
    fun runtimeDataCanFixOtherwiseInvalidBarcodeContent() {
        val request =
            Json.decodeFromString(
                RenderRequest.serializer(),
                """{"template":{"version":2,"config":{"page":{"size":{"format":"A4"}}},"rows":[{"blocks":[{"type":"barcode","id":"ean","symbology":"ean13","content":{"type":"raw","value":"not-a-number"}}]}]},"data":{"ean":{"value":"012345678905"}}}""",
            )
        val result = TemplatePdfRenderService().render(request.template, request.data)
        // Declared content is invalid for EAN-13, but the runtime data override is valid, so it must render.
        assertIs<TemplatePdfRenderResult.Success>(result)
    }
}

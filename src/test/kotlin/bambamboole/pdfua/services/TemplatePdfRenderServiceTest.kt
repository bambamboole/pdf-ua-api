package bambamboole.pdfua.services

import bambamboole.pdfua.template.Row
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.TextBlock
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
}

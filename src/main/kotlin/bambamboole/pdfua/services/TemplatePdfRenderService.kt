package bambamboole.pdfua.services

import bambamboole.pdfua.html.TemplateRenderer
import bambamboole.pdfua.pdf.PdfRenderOptions
import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.pdf.PdfResult
import bambamboole.pdfua.template.Template
import bambamboole.pdfua.template.ValidationIssue
import bambamboole.pdfua.template.validate
import com.openhtmltopdf.extend.FSStreamFactory
import kotlinx.serialization.json.JsonElement

class TemplatePdfRenderService(
    private val pdfProducer: String = "pdf-ua-api.com",
    private val assetResolver: FSStreamFactory? = null,
) {
    fun render(
        template: Template,
        data: Map<String, JsonElement> = emptyMap(),
    ): TemplatePdfRenderResult {
        val issues = template.validate(data)
        if (issues.isNotEmpty()) {
            return TemplatePdfRenderResult.ValidationFailed(issues)
        }

        val html = TemplateRenderer.render(template, data)

        val result =
            PdfRenderer.convertHtmlToPdf(
                html = html,
                producer = pdfProducer,
                assetResolver = assetResolver,
                attachments = template.attachments,
                options = PdfRenderOptions(embedColorProfile = template.config.embedColorProfile),
            )

        return TemplatePdfRenderResult.Success(result)
    }
}

sealed interface TemplatePdfRenderResult {
    data class Success(
        val pdf: PdfResult,
    ) : TemplatePdfRenderResult

    data class ValidationFailed(
        val issues: List<ValidationIssue>,
    ) : TemplatePdfRenderResult
}

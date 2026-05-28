package bambamboole.pdfua.routes

import bambamboole.pdfua.models.RenderRequest
import bambamboole.pdfua.services.PdfService
import bambamboole.pdfua.services.TemplateRenderer
import com.openhtmltopdf.extend.FSStreamFactory
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@GenerateOpenApi
@Tag(["Rendering"])
fun Route.renderRoutes(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null,
) {
    @KtorDescription(
        summary = "Render a template to PDF",
        description = "Renders a JSON template (with optional per-block data overrides) to a PDF/A-3a document.",
    )
    @KtorResponds([
        ResponseEntry("200", ByteArray::class, description = "PDF document"),
        ResponseEntry("400", Nothing::class, description = "Invalid template or request"),
        ResponseEntry("500", Nothing::class, description = "Rendering failed"),
    ])
    post("/render/template") {
        val request = try {
            call.receive<RenderRequest>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid render request: ${e.message}"))
            return@post
        }

        try {
            request.options.baseUrl.takeIf { it.isNotEmpty() }?.let { validateBaseUrl(it) }
            val html = TemplateRenderer.render(request.template, request.data, request.options)

            val result = PdfService.convertHtmlToPdf(
                html = html,
                producer = pdfProducer,
                assetResolver = assetResolver,
                baseUrl = request.options.baseUrl,
                attachments = request.template.attachments,
            )

            call.response.header("X-Document-UUID", result.documentId)
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "output.pdf",
                ).toString(),
            )
            call.respondBytes(result.bytes, ContentType.Application.Pdf, HttpStatusCode.OK)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to render template: ${e.message}"),
            )
        }
    }
}

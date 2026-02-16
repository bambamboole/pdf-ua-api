package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.services.PdfService
import com.openhtmltopdf.extend.FSStreamFactory
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI

fun Route.convertRoutes(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null
) {
    post("/convert") {
        try {
            val request = call.receive<ConvertRequest>()

            if (request.html.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "HTML content cannot be empty")
                )
                return@post
            }

            val baseUrl = request.baseUrl?.also { validateBaseUrl(it) } ?: ""

            val pdfBytes = PdfService.convertHtmlToPdf(
                html = request.html,
                producer = pdfProducer,
                assetResolver = assetResolver,
                baseUrl = baseUrl
            )

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName,
                    "output.pdf"
                ).toString()
            )

            call.respondBytes(
                bytes = pdfBytes,
                contentType = ContentType.Application.Pdf,
                status = HttpStatusCode.OK
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (e.message ?: "Invalid request"))
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to convert HTML to PDF: ${e.message}")
            )
        }
    }
}

internal fun validateBaseUrl(baseUrl: String) {
    val uri = URI.create(baseUrl)
    val scheme = uri.scheme?.lowercase()
    require(scheme == "http" || scheme == "https") {
        "baseUrl must use http or https scheme, got: $scheme"
    }
}

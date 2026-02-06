package bambamboole.pdf.api.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.services.PdfService

fun Route.convertRoutes() {
    post("/convert") {
        try {
            val request = call.receive<ConvertRequest>()

            // Validate request
            if (request.html.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "HTML content cannot be empty")
                )
                return@post
            }

            // Convert HTML to PDF
            val pdfBytes = PdfService.convertHtmlToPdf(request.html)

            // Return PDF with proper headers
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

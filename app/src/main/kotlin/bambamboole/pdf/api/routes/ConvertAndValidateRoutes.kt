package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ConvertAndValidateResponse
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.services.PdfService
import bambamboole.pdf.api.services.PdfValidationService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.Base64

fun Route.convertAndValidateRoutes(pdfProducer: String = "pdf-ua-api.com") {
    post("/convert-and-validate") {
        try {
            val request = call.receive<ConvertRequest>()

            if (request.html.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "HTML content cannot be empty")
                )
                return@post
            }

            val pdfBytes = PdfService.convertHtmlToPdf(request.html, pdfProducer)
            val validation = PdfValidationService.validatePdf(pdfBytes)
            val pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes)

            call.respond(
                HttpStatusCode.OK,
                ConvertAndValidateResponse(
                    validation = validation,
                    pdf = pdfBase64
                )
            )
        } catch (e: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (e.message ?: "Invalid request"))
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to convert and validate PDF: ${e.message}")
            )
        }
    }
}

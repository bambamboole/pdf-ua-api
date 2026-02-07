package bambamboole.pdf.api.routes

import bambamboole.pdf.api.services.PdfValidationService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.validationRoutes() {
    post("/validate") {
        try {
            // Receive PDF bytes from request body
            val pdfBytes = call.receive<ByteArray>()

            if (pdfBytes.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "PDF content cannot be empty")
                )
                return@post
            }

            // Validate PDF
            val validationResult = PdfValidationService.validatePdf(pdfBytes)

            // Return validation result as JSON
            call.respond(HttpStatusCode.OK, validationResult)

        } catch (e: IllegalStateException) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Validation service error"))
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to validate PDF: ${e.message}")
            )
        }
    }
}

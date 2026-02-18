package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ValidationResponse
import bambamboole.pdf.api.services.PdfValidationService
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
@Tag(["Validation"])
fun Route.validationRoutes() {
    @KtorDescription(
        summary = "Validate PDF",
        description = "Validates a PDF against PDF/A-3a and PDF/UA-1 standards using veraPDF. Send PDF binary as request body."
    )
    @KtorResponds([
        ResponseEntry("200", ValidationResponse::class, description = "Validation result"),
        ResponseEntry("400", Nothing::class, description = "PDF content is empty"),
        ResponseEntry("500", Nothing::class, description = "Validation service error")
    ])
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

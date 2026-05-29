package bambamboole.pdfua.http.controller

import bambamboole.pdfua.http.ValidationResponse
import bambamboole.pdfua.pdf.PdfValidator
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
        description = "Validates a PDF against PDF/A-3a and PDF/UA-1 standards using veraPDF. Send PDF binary as request body.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", ValidationResponse::class, description = "Validation result"),
            ResponseEntry("400", Nothing::class, description = "PDF content is empty"),
            ResponseEntry("500", Nothing::class, description = "Validation service error"),
        ],
    )
    post("/validate") {
        try {
            val pdfBytes = call.receive<ByteArray>()

            if (pdfBytes.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "PDF content cannot be empty"),
                )
                return@post
            }

            val validationResult = PdfValidator.validatePdf(pdfBytes)
            call.respond(HttpStatusCode.OK, validationResult)
        } catch (e: IllegalStateException) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (e.message ?: "Validation service error")),
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Failed to validate PDF: ${e.message}"),
            )
        }
    }
}

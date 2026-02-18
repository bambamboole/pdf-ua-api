package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ConvertAndValidateResponse
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.services.PdfService
import bambamboole.pdf.api.services.PdfValidationService
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
import java.util.Base64

@GenerateOpenApi
@Tag(["Conversion"])
fun Route.convertAndValidateRoutes(
    pdfProducer: String = "pdf-ua-api.com",
    assetResolver: FSStreamFactory? = null
) {
    @KtorDescription(
        summary = "Convert and validate HTML to PDF",
        description = "Converts HTML to PDF/A-3a and validates the result. Returns validation results and the base64-encoded PDF."
    )
    @KtorResponds([
        ResponseEntry("200", ConvertAndValidateResponse::class, description = "Conversion and validation result with base64-encoded PDF"),
        ResponseEntry("400", Nothing::class, description = "Invalid request"),
        ResponseEntry("500", Nothing::class, description = "Conversion or validation failed")
    ])
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

            val baseUrl = request.baseUrl?.also { validateBaseUrl(it) } ?: ""

            val pdfBytes = PdfService.convertHtmlToPdf(
                html = request.html,
                producer = pdfProducer,
                assetResolver = assetResolver,
                baseUrl = baseUrl,
                attachments = request.attachments
            )
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

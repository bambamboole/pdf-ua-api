package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import bambamboole.pdfua.http.ValidationResponse
import bambamboole.pdfua.http.binarySchema
import bambamboole.pdfua.pdf.PdfValidator
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

fun Application.validation() {
    val config: AppConfig by dependencies
    routing {
        expensiveRoute(config) { validationRoutes() }
    }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.validationRoutes() {
    post("/validate") {
        val pdfBytes = call.receive<ByteArray>()
        require(pdfBytes.isNotEmpty()) { "PDF content cannot be empty" }

        val validationResult = PdfValidator.validatePdf(pdfBytes)
        call.respond(HttpStatusCode.OK, validationResult)
    }.describe {
        tag("Validation")
        summary = "Validate PDF"
        description =
            "Validates a PDF against PDF/A-3a and PDF/UA-1 standards using veraPDF. Send PDF binary as request body."
        requestBody {
            required = true
            ContentType.Application.Pdf { schema = binarySchema() }
        }
        responses {
            HttpStatusCode.OK {
                description = "Validation result"
                schema = jsonSchema<ValidationResponse>()
            }
            HttpStatusCode.BadRequest { description = "PDF content is empty" }
            HttpStatusCode.InternalServerError { description = "Validation service error" }
        }
    }
}

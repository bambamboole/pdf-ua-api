package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.IdentifyResponse
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.pdfbox.Loader

@GenerateOpenApi
@Tag(["Identification"])
fun Route.identifyRoutes() {
    @KtorDescription(
        summary = "Identify PDF",
        description = "Checks whether a PDF was produced by this API and returns its document UUID if found."
    )
    @KtorResponds([
        ResponseEntry("200", IdentifyResponse::class, description = "Identification result"),
        ResponseEntry("400", Nothing::class, description = "PDF content is empty or invalid"),
        ResponseEntry("500", Nothing::class, description = "Failed to read PDF")
    ])
    post("/identify") {
        try {
            val pdfBytes = call.receive<ByteArray>()

            if (pdfBytes.isEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "PDF content cannot be empty")
                )
                return@post
            }

            val documentId = Loader.loadPDF(pdfBytes).use { document ->
                document.documentInformation.getCustomMetadataValue("X-Document-UUID")
            }

            call.respond(HttpStatusCode.OK, IdentifyResponse(documentId))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Failed to read PDF: ${e.message}")
            )
        }
    }
}

package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.expensiveRoute
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.apache.pdfbox.Loader

@Serializable
data class IdentifyResponse(
    val documentId: String?,
)

fun Application.identify() {
    val config: AppConfig by dependencies
    routing {
        expensiveRoute(config) { identifyRoutes() }
    }
}

@GenerateOpenApi
@Tag(["Identification"])
fun Route.identifyRoutes() {
    @KtorDescription(
        summary = "Identify PDF",
        description = "Checks whether a PDF was produced by this API and returns its document UUID if found.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", IdentifyResponse::class, description = "Identification result"),
            ResponseEntry("400", Nothing::class, description = "PDF content is empty or invalid"),
            ResponseEntry("500", Nothing::class, description = "Failed to read PDF"),
        ],
    )
    post("/identify") {
        val pdfBytes = call.receive<ByteArray>()
        require(pdfBytes.isNotEmpty()) { "PDF content cannot be empty" }

        val documentId =
            try {
                Loader.loadPDF(pdfBytes).use { document ->
                    document.documentInformation.getCustomMetadataValue("X-Document-UUID")
                }
            } catch (e: java.io.IOException) {
                throw IllegalArgumentException("Failed to read PDF: ${e.message}", e)
            }

        call.respond(HttpStatusCode.OK, IdentifyResponse(documentId))
    }
}

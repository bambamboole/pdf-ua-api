package bambamboole.pdfua.http.controller

import bambamboole.pdfua.config.AppConfig
import bambamboole.pdfua.http.binarySchema
import bambamboole.pdfua.protectedRoute
import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import org.apache.pdfbox.Loader

@Serializable
data class IdentifyResponse(
    val documentId: String?,
)

fun Application.identify() {
    val config: AppConfig by dependencies
    routing {
        protectedRoute(config) { identifyRoutes() }
    }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.identifyRoutes() {
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
    }.describe {
        tag("Identification")
        summary = "Identify PDF"
        description = "Checks whether a PDF was produced by this API and returns its document UUID if found."
        requestBody {
            required = true
            ContentType.Application.Pdf { schema = binarySchema() }
        }
        responses {
            HttpStatusCode.OK {
                description = "Identification result"
                schema = jsonSchema<IdentifyResponse>()
            }
            HttpStatusCode.BadRequest { description = "PDF content is empty or invalid" }
            HttpStatusCode.InternalServerError { description = "Failed to read PDF" }
        }
    }
}

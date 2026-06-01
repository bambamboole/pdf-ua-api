package bambamboole.pdfua.http.controller

import io.ktor.http.*
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
)

fun Application.health() {
    routing { healthRoutes() }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok"))
    }.describe {
        tag("Health")
        summary = "Health check"
        description = "Returns the health status of the API. Always accessible without authentication."
        responses {
            HttpStatusCode.OK {
                description = "Service is healthy"
                schema = jsonSchema<HealthResponse>()
            }
        }
    }
}

package bambamboole.pdfua.http.controller

import io.ktor.http.*
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

fun Application.health() {
    routing { healthRoutes() }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.healthRoutes() {
    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }.describe {
        tag("Health")
        summary = "Health check"
        description = "Returns the health status of the API. Always accessible without authentication."
        responses {
            HttpStatusCode.OK {
                description = "Service is healthy"
                schema =
                    JsonSchema(
                        type = JsonType.OBJECT,
                        properties =
                            mapOf(
                                "status" to
                                    io.ktor.openapi.ReferenceOr
                                        .Value(JsonSchema(type = JsonType.STRING)),
                            ),
                    )
            }
        }
    }
}

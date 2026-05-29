package bambamboole.pdfua.http.controller

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.health() {
    routing { healthRoutes() }
}

@GenerateOpenApi
@Tag(["Health"])
fun Route.healthRoutes() {
    @KtorDescription(
        summary = "Health check",
        description = "Returns the health status of the API. Always accessible without authentication.",
    )
    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }
}

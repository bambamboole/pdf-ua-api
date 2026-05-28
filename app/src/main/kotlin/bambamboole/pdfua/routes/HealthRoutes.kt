package bambamboole.pdfua.routes

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

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

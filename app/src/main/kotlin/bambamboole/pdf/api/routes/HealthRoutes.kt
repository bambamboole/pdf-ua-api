package bambamboole.pdf.api.routes

import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.server.response.*
import io.ktor.server.routing.*

@GenerateOpenApi
@Tag(["Health"])
fun Route.healthRoutes() {
    @KtorDescription(
        summary = "Health check",
        description = "Returns the health status of the API. Always accessible without authentication."
    )
    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }
}

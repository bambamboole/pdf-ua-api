package bambamboole.pdfua.http.controller

import bambamboole.pdfua.template.TemplateJsonSchema
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject

@GenerateOpenApi
@Tag(["Rendering"])
fun Route.templateSchemaRoutes() {
    @KtorDescription(
        summary = "Get the template schema",
        description = "Returns the canonical JSON Schema for template rendering, including builder metadata under x-pdfUa.",
    )
    @KtorResponds(
        [
            ResponseEntry("200", JsonObject::class, description = "Template JSON Schema"),
        ],
    )
    get("/schema") {
        call.respond(TemplateJsonSchema.current())
    }
}

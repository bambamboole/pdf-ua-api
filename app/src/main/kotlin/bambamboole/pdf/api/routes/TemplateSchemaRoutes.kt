package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.template.TemplateSchema
import bambamboole.pdf.api.models.template.TemplateSchemaResponse
import io.github.tabilzad.ktor.annotations.GenerateOpenApi
import io.github.tabilzad.ktor.annotations.KtorDescription
import io.github.tabilzad.ktor.annotations.KtorResponds
import io.github.tabilzad.ktor.annotations.ResponseEntry
import io.github.tabilzad.ktor.annotations.Tag
import io.ktor.server.response.*
import io.ktor.server.routing.*

@GenerateOpenApi
@Tag(["Rendering"])
fun Route.templateSchemaRoutes() {
    @KtorDescription(
        summary = "Get the template schema",
        description = "Returns the supported template version, page formats, bundled fonts, external font fields, and block definitions.",
    )
    @KtorResponds([
        ResponseEntry("200", TemplateSchemaResponse::class, description = "Template schema"),
    ])
    get("/schema") {
        call.respond(TemplateSchema.current())
    }
}

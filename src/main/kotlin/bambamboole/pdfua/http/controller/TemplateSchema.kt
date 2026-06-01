package bambamboole.pdfua.http.controller

import bambamboole.pdfua.template.TemplateJsonSchema
import io.ktor.http.*
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

fun Application.templateSchema() {
    routing { templateSchemaRoutes() }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.templateSchemaRoutes() {
    get("/schema") {
        call.respond(TemplateJsonSchema.current())
    }.describe {
        tag("Rendering")
        summary = "Get the template schema"
        description =
            "Canonical JSON Schema (Draft 2020-12) for the template rendering payload. The response is a JSON Schema " +
            "document describing the Template type accepted by /render/template, with builder metadata under x-pdfUa."
        responses {
            HttpStatusCode.OK {
                description = "Template JSON Schema"
                ContentType.Application.Json { schema = JsonSchema(type = JsonType.OBJECT) }
            }
        }
    }
}

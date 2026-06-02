package bambamboole.pdfua.http

import bambamboole.pdfua.template.TemplateJsonSchema
import io.ktor.http.*
import io.ktor.openapi.HttpSecurityScheme
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.findSecuritySchemes
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.openapi.registerSecurityScheme
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val BEARER_SECURITY_SCHEME = "bearerAuth"

/** Name and `$ref` of the shared template schema component referenced by `/render/template`. */
const val TEMPLATE_SCHEMA_COMPONENT = "Template"
const val TEMPLATE_SCHEMA_REF = "#/components/schemas/$TEMPLATE_SCHEMA_COMPONENT"

/** Schema for a raw binary response or request body (for example `application/pdf`). */
fun binarySchema(): JsonSchema = JsonSchema(type = JsonType.STRING, format = "binary")

private val specJson =
    Json {
        prettyPrint = true
        encodeDefaults = false
    }

/**
 * Assembles the OpenAPI document at runtime from the routing tree. Route metadata is attached via
 * the `describe {}` DSL on each route; security schemes are inferred from the registered schemes.
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.buildOpenApiDocument(version: String): OpenApiDoc {
    val defaults =
        OpenApiDoc.build {
            info =
                OpenApiInfo(
                    title = "PDF/UA API",
                    version = version,
                    description =
                        "HTML to PDF/A-3a conversion API with PDF/UA accessibility support and " +
                            "veraPDF validation",
                )
            servers { server("http://localhost:8080") }
            security { requirement(BEARER_SECURITY_SCHEME) }
        }
    return defaults + routingRoot.descendants() + findSecuritySchemes()
}

/**
 * Serializes an assembled document to JSON, injecting [TemplateJsonSchema] as the shared `Template`
 * component. The schema is injected as raw JSON because it is a self-contained JSON Schema document
 * (`$schema`/`$id`/`$defs`) that Ktor's typed [JsonSchema] model cannot represent without losing its
 * `$defs`. The `/render/template` request body `$ref`s this component.
 */
fun serializeOpenApiDoc(doc: OpenApiDoc): String {
    val root = specJson.parseToJsonElement(specJson.encodeToString(doc)).jsonObject
    val components = root["components"]?.jsonObject ?: JsonObject(emptyMap())
    val schemas = components["schemas"]?.jsonObject ?: JsonObject(emptyMap())
    val templateSchema = TemplateJsonSchema.current()
    val templateDefinitions = templateSchema["\$defs"]?.jsonObject ?: JsonObject(emptyMap())
    val openApiTemplateDefinitions =
        JsonObject(templateDefinitions.mapValues { (_, value) -> rewriteTemplateSchemaRefsForOpenApi(value) })
    val mergedSchemas =
        JsonObject(
            schemas +
                openApiTemplateDefinitions +
                (TEMPLATE_SCHEMA_COMPONENT to rewriteTemplateSchemaRefsForOpenApi(templateSchema)),
        )
    val mergedComponents = JsonObject(components + ("schemas" to mergedSchemas))
    return specJson.encodeToString(JsonObject(root + ("components" to mergedComponents)))
}

private fun rewriteTemplateSchemaRefsForOpenApi(element: JsonElement): JsonElement =
    when (element) {
        is JsonObject -> {
            JsonObject(
                element.mapValues { (key, value) ->
                    if (key == "\$ref") rewriteTemplateRefForOpenApi(value) else rewriteTemplateSchemaRefsForOpenApi(value)
                },
            )
        }

        is JsonArray -> {
            JsonArray(element.map(::rewriteTemplateSchemaRefsForOpenApi))
        }

        else -> {
            element
        }
    }

private fun rewriteTemplateRefForOpenApi(value: JsonElement): JsonElement {
    val ref = value.jsonPrimitive.content
    return if (ref.startsWith("#/\$defs/")) {
        JsonPrimitive("#/components/schemas/${ref.removePrefix("#/\$defs/")}")
    } else {
        value
    }
}

fun Application.serializeOpenApiDocument(version: String): String = serializeOpenApiDoc(buildOpenApiDocument(version))

/**
 * Registers the bearer security scheme and serves the assembled specification as JSON. The endpoint
 * itself is hidden from the document.
 */
@OptIn(ExperimentalKtorApi::class)
fun Application.openApiSpec(version: String) {
    registerSecurityScheme(BEARER_SECURITY_SCHEME, HttpSecurityScheme(scheme = "bearer"))
    routing {
        get("/openapi.json") {
            call.respondText(serializeOpenApiDocument(version), ContentType.Application.Json)
        }.hide()
    }
}

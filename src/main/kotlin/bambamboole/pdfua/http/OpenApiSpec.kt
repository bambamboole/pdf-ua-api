package bambamboole.pdfua.http

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

const val BEARER_SECURITY_SCHEME = "bearerAuth"

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
                    title = "PDF API",
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

fun Application.serializeOpenApiDocument(version: String): String = specJson.encodeToString(buildOpenApiDocument(version))

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

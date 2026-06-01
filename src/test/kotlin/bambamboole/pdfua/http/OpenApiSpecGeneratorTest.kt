package bambamboole.pdfua.http

import bambamboole.pdfua.http.controller.healthRoutes
import bambamboole.pdfua.http.controller.identifyRoutes
import bambamboole.pdfua.http.controller.renderImageRoutes
import bambamboole.pdfua.http.controller.renderRoutes
import bambamboole.pdfua.http.controller.templateSchemaRoutes
import bambamboole.pdfua.http.controller.validationRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Assembles the OpenAPI document from the routing tree and compares it against the committed
 * `docs/openapi/openapi.json`. Run `./gradlew updateOpenApi` to regenerate the committed file after
 * intentionally changing any route's `describe {}` metadata.
 */
class OpenApiSpecGeneratorTest {
    private fun Application.specModule() {
        install(ContentNegotiation) { json() }
        openApiSpec(SPEC_VERSION)
        routing {
            healthRoutes()
            templateSchemaRoutes()
            renderRoutes()
            validationRoutes()
            renderImageRoutes()
            identifyRoutes()
        }
    }

    @Test
    fun committedSpecIsUpToDate() =
        testApplication {
            application { specModule() }

            val generated = client.get("/openapi.json").bodyAsText().trim() + "\n"
            val committed = File(COMMITTED_SPEC_PATH)

            if (System.getProperty("updateOpenApi") != null) {
                committed.parentFile.mkdirs()
                committed.writeText(generated)
                return@testApplication
            }

            assertEquals(
                committed.readText(),
                generated,
                "OpenAPI spec is out of date. Run `./gradlew updateOpenApi` and commit the result.",
            )
        }

    private companion object {
        const val SPEC_VERSION = "dev"
        const val COMMITTED_SPEC_PATH = "docs/openapi/openapi.json"
    }
}

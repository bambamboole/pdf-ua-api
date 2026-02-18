package bambamboole.pdf.api.routes

import bambamboole.pdf.api.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiRoutesTest {

    @Test
    fun testSwaggerUIEndpoint() = testApplication {
        application {
            module()
        }

        client.get("/docs").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertTrue(body.contains("Swagger UI"), "Response should contain 'Swagger UI'")
            assertTrue(body.contains("swagger-ui"), "Response should contain 'swagger-ui'")
        }
    }

    @Test
    fun testSwaggerUIReturnsHTML() = testApplication {
        application {
            module()
        }

        val response = client.get("/docs")
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
    }

    @Test
    fun testOpenAPISpecEndpoint() = testApplication {
        application {
            module()
        }

        client.get("/docs/documentation.yaml").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertTrue(body.contains("openapi: 3.0.3"), "Response should contain OpenAPI version")
            assertTrue(body.contains("PDF UA API"), "Response should contain API title")
            assertTrue(body.contains("/health"), "Response should contain /health endpoint")
            assertTrue(body.contains("/convert"), "Response should contain /convert endpoint")
            assertTrue(body.contains("/validate"), "Response should contain /validate endpoint")
        }
    }

    @Test
    fun testOpenAPISpecReturnsYAML() = testApplication {
        application {
            module()
        }

        val response = client.get("/docs/documentation.yaml")
        // YAML files are typically served as text/yaml or text/plain
        val contentType = response.contentType()?.withoutParameters()
        assertTrue(
            contentType == ContentType.Text.Plain || contentType?.toString()?.contains("yaml") == true,
            "Response content type should be text/plain or contain 'yaml', got: $contentType"
        )
    }
}

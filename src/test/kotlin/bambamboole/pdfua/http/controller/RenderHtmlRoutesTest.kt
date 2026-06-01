package bambamboole.pdfua.http.controller

import bambamboole.pdfua.http.RenderHtmlRequest
import bambamboole.pdfua.statusPages
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun Application.renderHtmlModule() {
    install(ContentNegotiation) { json() }
    statusPages()
    routing { renderRoutes() }
}

class RenderHtmlRoutesTest {
    private val validHtml =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <title>Render HTML Test</title>
            <meta name="subject" content="Render HTML Test"/>
        </head>
        <body><h1>Render HTML Test</h1></body>
        </html>
        """.trimIndent()

    @Test
    fun renderHtmlReturnsPdfByDefault() =
        testApplication {
            application { renderHtmlModule() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertNotNull(response.headers["X-Document-UUID"])

            val bytes = response.readRawBytes()
            assertTrue(bytes.size > 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        }

    @Test
    fun renderHtmlWithJsonAcceptReturnsValidationAndPdf() =
        testApplication {
            application { renderHtmlModule() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertNotNull(response.headers["X-Document-UUID"])

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue("validation" in body)
            assertTrue("pdf" in body)

            val pdfBytes = Base64.getDecoder().decode(body.getValue("pdf").jsonPrimitive.content)
            assertTrue(pdfBytes.size > 5 && String(pdfBytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        }

    @Test
    fun renderHtmlRejectsEmptyHtml() =
        testApplication {
            application { renderHtmlModule() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest("")))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("HTML content cannot be empty"))
        }

    @Test
    fun renderHtmlRejectsJsonAcceptWithUploadUrl() =
        testApplication {
            application { renderHtmlModule() }

            val response =
                client.post("/render/html") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    header("X-Upload-Url", "https://bucket.example.com/out.pdf")
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("cannot be combined"))
        }

    @Test
    fun oldConvertEndpointReturnsNotFound() =
        testApplication {
            application { renderHtmlModule() }

            val response =
                client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun oldConvertAndValidateEndpointReturnsNotFound() =
        testApplication {
            application { renderHtmlModule() }

            val response =
                client.post("/convert-and-validate") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(RenderHtmlRequest.serializer(), RenderHtmlRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }
}

package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.ConvertAndValidateResponse
import bambamboole.pdf.api.models.ConvertRequest
import bambamboole.pdf.api.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvertAndValidateRoutesTest {

    @Test
    fun testConvertAndValidateWithValidHTML() = testApplication {
        application { module() }

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Test Document</title>
                <meta name="subject" content="PDF Test" />
                <meta name="author" content="Test Author" />
            </head>
            <body><h1>Hello</h1><p>Test content</p></body>
            </html>
        """.trimIndent()

        val response = client.post("/convert-and-validate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val result = Json.decodeFromString<ConvertAndValidateResponse>(response.bodyAsText())

        assertTrue(result.validation.isCompliant)
        assertTrue(result.validation.profiles.isNotEmpty())
        assertTrue(result.validation.profiles.any { it.profile == "PDF/A-3a" })
        assertTrue(result.validation.profiles.any { it.profile == "PDF/UA-1" })
        assertTrue(result.pdf.isNotEmpty())

        val pdfBytes = Base64.getDecoder().decode(result.pdf)
        assertTrue(String(pdfBytes, 0, 5, Charsets.US_ASCII).startsWith("%PDF-"))
    }

    @Test
    fun testConvertAndValidateWithEmptyHTML() = testApplication {
        application { module() }

        val response = client.post("/convert-and-validate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest("")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun testConvertAndValidateWithValidApiKey() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application { module() }

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Test</title>
                <meta name="subject" content="Auth Test" />
            </head>
            <body><h1>Test</h1></body>
            </html>
        """.trimIndent()

        val response = client.post("/convert-and-validate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer test-api-key")
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val result = Json.decodeFromString<ConvertAndValidateResponse>(response.bodyAsText())
        assertTrue(result.validation.isCompliant)
        assertTrue(result.pdf.isNotEmpty())
    }

    @Test
    fun testConvertAndValidateWithInvalidApiKey() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application { module() }

        val response = client.post("/convert-and-validate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer wrong-key")
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest("<html><body>Test</body></html>")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testConvertAndValidateWithoutApiKeyWhenAuthEnabled() = testApplication {
        environment {
            config = MapApplicationConfig("api.key" to "test-api-key")
        }
        application { module() }

        val response = client.post("/convert-and-validate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest("<html><body>Test</body></html>")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}

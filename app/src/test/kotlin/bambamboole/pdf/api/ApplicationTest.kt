package bambamboole.pdf.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import bambamboole.pdf.api.models.ConvertRequest
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testHealthEndpoint() = testApplication {
        application {
            module()
        }

        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("\"status\""))
            assertTrue(bodyAsText().contains("\"ok\""))
        }
    }

    @Test
    fun testConvertEndpointWithValidHTML() = testApplication {
        application {
            module()
        }

        val htmlContent = "<html><body><h1>Test</h1></body></html>"
        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"$htmlContent"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
        assertTrue(response.headers.contains(HttpHeaders.ContentDisposition))

        // Verify PDF content (PDF starts with %PDF-)
        val pdfBytes = response.readBytes()
        assertTrue(pdfBytes.isNotEmpty())
        val pdfHeader = pdfBytes.take(5).toByteArray().decodeToString()
        assertTrue(pdfHeader.startsWith("%PDF-"), "Response should be a valid PDF")
    }

    @Test
    fun testConvertEndpointWithStyledHTML() = testApplication {
        application {
            module()
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                </style>
            </head>
            <body>
                <h1>Styled Document</h1>
                <p>This document has CSS styles.</p>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val pdfBytes = response.readBytes()
        assertTrue(pdfBytes.size > 500, "Styled PDF should be larger")
    }

    @Test
    fun testConvertEndpointWithEmptyHTML() = testApplication {
        application {
            module()
        }

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":""}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("error"))
    }

    @Test
    fun testConvertEndpointWithWhitespaceOnlyHTML() = testApplication {
        application {
            module()
        }

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"   "}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("error"))
    }

    @Test
    fun testConvertEndpointWithInvalidJSON() = testApplication {
        application {
            module()
        }

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"invalid": "json structure"}""")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun testConvertEndpointWithComplexHTML() = testApplication {
        application {
            module()
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Complex Document</title>
                <style>
                    table { border-collapse: collapse; width: 100%; }
                    th, td { border: 1px solid black; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; }
                </style>
            </head>
            <body>
                <h1>Complex Document</h1>
                <table>
                    <tr><th>Name</th><th>Value</th></tr>
                    <tr><td>Item 1</td><td>100</td></tr>
                    <tr><td>Item 2</td><td>200</td></tr>
                </table>
                <ul>
                    <li>Point 1</li>
                    <li>Point 2</li>
                    <li>Point 3</li>
                </ul>
            </body>
            </html>
        """.trimIndent()

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(htmlContent)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
    }

    @Test
    fun testHealthEndpointReturnType() = testApplication {
        application {
            module()
        }

        val response = client.get("/health")
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }
}

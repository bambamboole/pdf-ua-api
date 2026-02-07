package bambamboole.pdf.api

import bambamboole.pdf.api.models.ConvertRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import kotlin.test.Test
import kotlin.test.assertEquals


class ApplicationConfigTest {

    @Test
    fun testUIEnabledByDefault() = testApplication {
        application {
            module()
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testUIDisabledReturns404() = testApplication {
        environment {
            config = MapApplicationConfig("ui.enabled" to "false")
        }
        application {
            module()
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testCustomPdfProducer() = testApplication {
        environment {
            config = MapApplicationConfig("pdf.producer" to "custom-producer-v1.0")
        }
        application {
            module()
        }

        val html =
            """<!DOCTYPE html><html lang="en"><head><title>Test</title><meta name="subject" content="Test"/></head><body><h1>Test</h1></body></html>"""

        val requestBody = Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(html))

        val response = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())

        Loader.loadPDF(response.bodyAsBytes()).use { document ->
            assertEquals("custom-producer-v1.0", document.documentInformation.producer)
        }
    }
}

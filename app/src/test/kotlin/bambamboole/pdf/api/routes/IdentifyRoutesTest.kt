package bambamboole.pdf.api.routes

import bambamboole.pdf.api.models.IdentifyResponse
import bambamboole.pdf.api.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IdentifyRoutesTest {

    @Test
    fun testRoundTripIdentification() = testApplication {
        application { module() }

        val convertResponse = client.post("/convert") {
            contentType(ContentType.Application.Json)
            setBody("""{"html":"<html><body><h1>Test</h1></body></html>"}""")
        }

        assertEquals(HttpStatusCode.OK, convertResponse.status)
        val headerUuid = convertResponse.headers["X-Document-UUID"]
        assertNotNull(headerUuid, "X-Document-UUID header should be present")

        val pdfBytes = convertResponse.readRawBytes()

        val identifyResponse = client.post("/identify") {
            contentType(ContentType.Application.Pdf)
            setBody(pdfBytes)
        }

        assertEquals(HttpStatusCode.OK, identifyResponse.status)
        val result = Json.decodeFromString<IdentifyResponse>(identifyResponse.bodyAsText())
        assertEquals(headerUuid, result.documentId, "Identified UUID should match the convert header")
    }

    @Test
    fun testUnknownPdfReturnsNullDocumentId() = testApplication {
        application { module() }

        val pdfBytes = PDDocument().use { doc ->
            doc.addPage(org.apache.pdfbox.pdmodel.PDPage())
            ByteArrayOutputStream().use { out ->
                doc.save(out)
                out.toByteArray()
            }
        }

        val response = client.post("/identify") {
            contentType(ContentType.Application.Pdf)
            setBody(pdfBytes)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val result = Json.decodeFromString<IdentifyResponse>(response.bodyAsText())
        assertNull(result.documentId, "Non-API PDF should have null documentId")
    }

    @Test
    fun testEmptyBodyReturnsBadRequest() = testApplication {
        application { module() }

        val response = client.post("/identify") {
            contentType(ContentType.Application.Pdf)
            setBody(ByteArray(0))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}

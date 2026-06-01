package bambamboole.pdfua.http.controller

import bambamboole.pdfua.pdf.PdfRenderer
import bambamboole.pdfua.services.DocumentUploader
import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun Application.pdfNegotiationModule(uploader: DocumentUploader? = null) {
    install(ContentNegotiation) { json() }
    routing {
        get("/test/pdf") {
            val result =
                PdfRenderer.convertHtmlToPdf(
                    html =
                        """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <title>Negotiation Test</title>
                            <meta name="subject" content="Negotiation Test"/>
                        </head>
                        <body><h1>Negotiation Test</h1></body>
                        </html>
                        """.trimIndent(),
                )

            respondPdfOrUpload(result, uploader)
        }
    }
}

class PdfResponseNegotiationTest {
    private fun httpClient() =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    private fun permissiveUploader() = DocumentUploader(httpClient = httpClient(), timeoutMs = 5000, validateUrl = { _, _ -> })

    private class CapturingServer {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests: Int = 0
        var body: ByteArray = ByteArray(0)

        fun start(path: String) {
            server.createContext(path) { exchange ->
                requests += 1
                body = exchange.requestBody.readBytes()
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            server.start()
        }

        val port: Int get() = server.address.port

        fun stop() = server.stop(0)
    }

    @Test
    fun missingAcceptReturnsPdf() =
        testApplication {
            application { pdfNegotiationModule() }

            val response = client.get("/test/pdf")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
            assertNotNull(response.headers["X-Document-UUID"])
            val bytes = response.readRawBytes()
            assertTrue(bytes.size > 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        }

    @Test
    fun wildcardAcceptReturnsPdf() =
        testApplication {
            application { pdfNegotiationModule() }

            val response =
                client.get("/test/pdf") {
                    accept(ContentType.Any)
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
        }

    @Test
    fun jsonAcceptReturnsValidationAndPdf() =
        testApplication {
            application { pdfNegotiationModule() }

            val response =
                client.get("/test/pdf") {
                    accept(ContentType.Application.Json)
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
    fun zeroQualityJsonAcceptReturnsPdf() =
        testApplication {
            application { pdfNegotiationModule() }

            val response =
                client.get("/test/pdf") {
                    header(HttpHeaders.Accept, "application/json;q=0")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
        }

    @Test
    fun pdfPreferredMixedAcceptReturnsPdf() =
        testApplication {
            application { pdfNegotiationModule() }

            val response =
                client.get("/test/pdf") {
                    header(HttpHeaders.Accept, "application/pdf, application/json;q=0.1")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
        }

    @Test
    fun jsonPreferredMixedAcceptReturnsJson() =
        testApplication {
            application { pdfNegotiationModule() }

            val response =
                client.get("/test/pdf") {
                    header(HttpHeaders.Accept, "application/json, application/pdf;q=0.1")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        }

    @Test
    fun jsonAcceptWithUploadUrlReturns400WithoutUploading() =
        testApplication {
            val target = CapturingServer().apply { start("/upload") }
            application { pdfNegotiationModule(permissiveUploader()) }

            try {
                val response =
                    client.get("/test/pdf") {
                        accept(ContentType.Application.Json)
                        header(UPLOAD_URL_HEADER, "http://127.0.0.1:${target.port}/upload")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("cannot be combined"))
                assertEquals(0, target.requests)
                assertTrue(target.body.isEmpty())
            } finally {
                target.stop()
            }
        }
}

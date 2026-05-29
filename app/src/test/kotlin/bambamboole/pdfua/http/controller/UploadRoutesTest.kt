package bambamboole.pdfua.http.controller

import bambamboole.pdfua.http.ConvertRequest
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
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun Application.convertModule(uploader: DocumentUploader?) {
    install(ContentNegotiation) { json() }
    routing { convertRoutes(uploader = uploader) }
}

private fun Application.renderImageModule(uploader: DocumentUploader?) {
    install(ContentNegotiation) { json() }
    routing { renderImageRoutes(uploader = uploader) }
}

class UploadRoutesTest {
    private val validHtml =
        """<!DOCTYPE html><html lang="en"><head><title>Test</title><meta name="subject" content="Test"/></head><body><h1>Test</h1></body></html>"""

    private fun httpClient() =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    private fun permissiveUploader() = DocumentUploader(httpClient = httpClient(), timeoutMs = 5000, validateUrl = { _, _ -> })

    private class CapturingServer {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var contentType: String? = null
        var body: ByteArray = ByteArray(0)

        fun start(path: String) {
            server.createContext(path) { exchange ->
                contentType = exchange.requestHeaders.getFirst("Content-Type")
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
    fun convertWithUploadUrlReturns204AndUploadsPdf() =
        testApplication {
            val target = CapturingServer().apply { start("/upload") }
            application { convertModule(permissiveUploader()) }
            try {
                val response =
                    client.post("/convert") {
                        contentType(ContentType.Application.Json)
                        header("X-Upload-Url", "http://127.0.0.1:${target.port}/upload")
                        setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(validHtml)))
                    }

                assertEquals(HttpStatusCode.NoContent, response.status)
                assertEquals("application/pdf", target.contentType)
                assertTrue(target.body.size > 4 && String(target.body, 0, 4) == "%PDF", "uploaded body should be a PDF")
            } finally {
                target.stop()
            }
        }

    @Test
    fun renderImageWithUploadUrlReturns204AndUploadsPng() =
        testApplication {
            val target = CapturingServer().apply { start("/upload") }
            application { renderImageModule(permissiveUploader()) }
            try {
                val response =
                    client.post("/render") {
                        contentType(ContentType.Application.Json)
                        header("X-Upload-Url", "http://127.0.0.1:${target.port}/upload")
                        setBody("""{"html":${Json.encodeToString(validHtml)},"format":"png","width":200}""")
                    }

                assertEquals(HttpStatusCode.NoContent, response.status)
                assertEquals("image/png", target.contentType)
                assertTrue(target.body.isNotEmpty())
            } finally {
                target.stop()
            }
        }

    @Test
    fun blockedUploadUrlReturns400() =
        testApplication {
            application { convertModule(DocumentUploader(httpClient = httpClient(), timeoutMs = 5000)) }

            val response =
                client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    header("X-Upload-Url", "http://10.0.0.1/upload")
                    setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun uploadUrlWhenFeatureDisabledReturns400() =
        testApplication {
            application { convertModule(null) }

            val response =
                client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    header("X-Upload-Url", "https://bucket.example.com/upload")
                    setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("not enabled"))
        }

    @Test
    fun convertWithoutUploadUrlReturnsPdfInline() =
        testApplication {
            application { convertModule(permissiveUploader()) }

            val response =
                client.post("/convert") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(ConvertRequest.serializer(), ConvertRequest(validHtml)))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Pdf, response.contentType())
        }
}

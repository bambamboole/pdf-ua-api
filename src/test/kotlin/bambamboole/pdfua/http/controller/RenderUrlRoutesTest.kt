package bambamboole.pdfua.http.controller

import bambamboole.pdfua.services.HtmlSourceFetcher
import bambamboole.pdfua.statusPages
import com.sun.net.httpserver.HttpExchange
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
import kotlin.test.assertTrue

private fun Application.renderUrlModule(fetcher: HtmlSourceFetcher?) {
    install(ContentNegotiation) { json() }
    statusPages()
    routing { renderRoutes(htmlSourceFetcher = fetcher) }
}

class RenderUrlRoutesTest {
    private val sampleHtml =
        """<!DOCTYPE html><html lang="en"><head><title>T</title>""" +
            """<meta name="subject" content="T"/></head><body><h1>Hello</h1></body></html>"""

    private fun httpClient() =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    private fun permissiveFetcher() = HtmlSourceFetcher(httpClient = httpClient(), timeoutMs = 5000, validateUrl = { _, _ -> })

    private class CapturingHtmlServer(
        private val statusCode: Int,
        private val contentType: String?,
        private val body: ByteArray,
    ) {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests: Int = 0

        fun start() {
            server.createContext("/page") { exchange: HttpExchange ->
                requests += 1
                contentType?.let { exchange.responseHeaders.add("Content-Type", it) }
                if (body.isEmpty()) {
                    exchange.sendResponseHeaders(statusCode, -1)
                } else {
                    exchange.sendResponseHeaders(statusCode, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            server.start()
        }

        val port: Int get() = server.address.port

        fun stop() = server.stop(0)
    }

    @Test
    fun renderUrlFetchesAndReturnsPdf() =
        testApplication {
            val target = CapturingHtmlServer(200, "text/html; charset=UTF-8", sampleHtml.toByteArray())
            target.start()
            application { renderUrlModule(permissiveFetcher()) }
            try {
                val response =
                    client.post("/render/url") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"url":"http://127.0.0.1:${target.port}/page"}""")
                    }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(ContentType.Application.Pdf, response.contentType())
                val bytes = response.readRawBytes()
                assertTrue(bytes.size > 4 && String(bytes, 0, 4) == "%PDF")
            } finally {
                target.stop()
            }
        }

    @Test
    fun renderUrlWithJsonAcceptReturnsValidationAndPdf() =
        testApplication {
            val target = CapturingHtmlServer(200, "text/html; charset=UTF-8", sampleHtml.toByteArray())
            target.start()
            application { renderUrlModule(permissiveFetcher()) }
            try {
                val response =
                    client.post("/render/url") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody("""{"url":"http://127.0.0.1:${target.port}/page"}""")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertTrue("validation" in body)
                assertTrue("pdf" in body)

                val pdfBytes = Base64.getDecoder().decode(body.getValue("pdf").jsonPrimitive.content)
                assertTrue(pdfBytes.size > 5 && String(pdfBytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
            } finally {
                target.stop()
            }
        }

    @Test
    fun renderUrlRejectsJsonAcceptWithUploadUrlBeforeFetching() =
        testApplication {
            val target = CapturingHtmlServer(200, "text/html; charset=UTF-8", sampleHtml.toByteArray())
            target.start()
            application { renderUrlModule(permissiveFetcher()) }
            try {
                val response =
                    client.post("/render/url") {
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        header("X-Upload-Url", "https://bucket.example.com/out.pdf")
                        setBody("""{"url":"http://127.0.0.1:${target.port}/page"}""")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("cannot be combined"))
                assertEquals(0, target.requests)
            } finally {
                target.stop()
            }
        }

    @Test
    fun renderUrlReturns400ForEmptyUrl() =
        testApplication {
            application { renderUrlModule(permissiveFetcher()) }
            val response =
                client.post("/render/url") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"url":""}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun renderUrlReturns400ForLoopbackWithRealValidator() =
        testApplication {
            application {
                renderUrlModule(HtmlSourceFetcher(httpClient = httpClient(), timeoutMs = 5000))
            }
            val response =
                client.post("/render/url") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"url":"http://127.0.0.1/page"}""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun renderUrlReturns400WhenSourceReturnsJson() =
        testApplication {
            val target = CapturingHtmlServer(200, "application/json", """{"x":1}""".toByteArray())
            target.start()
            application { renderUrlModule(permissiveFetcher()) }
            try {
                val response =
                    client.post("/render/url") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"url":"http://127.0.0.1:${target.port}/page"}""")
                    }
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("URL did not return HTML"))
            } finally {
                target.stop()
            }
        }

    @Test
    fun renderUrlReturns400OnNon2xx() =
        testApplication {
            val target = CapturingHtmlServer(500, null, ByteArray(0))
            target.start()
            application { renderUrlModule(permissiveFetcher()) }
            try {
                val response =
                    client.post("/render/url") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"url":"http://127.0.0.1:${target.port}/page"}""")
                    }
                assertEquals(HttpStatusCode.BadRequest, response.status)
            } finally {
                target.stop()
            }
        }
}

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
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
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

        fun start() {
            server.createContext("/page") { exchange: HttpExchange ->
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

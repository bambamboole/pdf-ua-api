package bambamboole.pdfua.services

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HtmlSourceFetcherTest {
    private fun client() =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    // Loopback hosts are blocked by the real SSRF guard, so round-trip tests pass a no-op
    // validator. The guard itself is exercised in the two rejection tests at the bottom.
    private fun permissiveFetcher(maxSizeBytes: Long = 5 * 1024 * 1024) =
        HtmlSourceFetcher(
            httpClient = client(),
            timeoutMs = 2000,
            maxSizeBytes = maxSizeBytes,
            validateUrl = { _, _ -> },
        )

    private fun startServer(
        path: String,
        handler: (HttpExchange) -> Unit,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path, handler)
        server.start()
        return server
    }

    @Test
    fun fetchReturnsHtmlOn200() {
        val server =
            startServer("/page") { exchange ->
                val body = "<html><body><h1>Hi</h1></body></html>".toByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=UTF-8")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val result = permissiveFetcher().fetch("http://127.0.0.1:${server.address.port}/page")
            val success = assertIs<FetchResult.Success>(result)
            assertTrue(success.html.contains("<h1>Hi</h1>"))
            assertEquals("http://127.0.0.1:${server.address.port}/page", success.finalUrl)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchUsesCharsetFromContentType() {
        val body = "<html><body>café</body></html>".toByteArray(Charsets.ISO_8859_1)
        val server =
            startServer("/page") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html; charset=ISO-8859-1")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val result = permissiveFetcher().fetch("http://127.0.0.1:${server.address.port}/page")
            val success = assertIs<FetchResult.Success>(result)
            assertTrue(success.html.contains("café"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRejectsNonHtmlContentType() {
        val server =
            startServer("/page") { exchange ->
                val body = """{"x":1}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val result = permissiveFetcher().fetch("http://127.0.0.1:${server.address.port}/page")
            val failed = assertIs<FetchResult.Failed>(result)
            assertTrue(failed.message.contains("URL did not return HTML"))
            assertTrue(failed.message.contains("application/json"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRejectsMissingContentType() {
        val server =
            startServer("/page") { exchange ->
                val body = "<html></html>".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val result = permissiveFetcher().fetch("http://127.0.0.1:${server.address.port}/page")
            val failed = assertIs<FetchResult.Failed>(result)
            assertTrue(failed.message.contains("URL did not return HTML"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchFailsOnNon2xx() {
        val server =
            startServer("/page") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        try {
            val result = permissiveFetcher().fetch("http://127.0.0.1:${server.address.port}/page")
            val failed = assertIs<FetchResult.Failed>(result)
            assertTrue(failed.message.contains("404"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchFailsWhenContentLengthExceedsCap() {
        val body = ByteArray(150) { 'x'.code.toByte() }
        val server =
            startServer("/page") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val result =
                permissiveFetcher(maxSizeBytes = 100)
                    .fetch("http://127.0.0.1:${server.address.port}/page")
            val failed = assertIs<FetchResult.Failed>(result)
            assertTrue(failed.message.contains("too large"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchFailsWhenStreamedBodyExceedsCapWithoutContentLength() {
        // Server uses chunked encoding (sendResponseHeaders with responseLength=0),
        // so no Content-Length header is sent. The cap must be enforced by the bounded read.
        val body = ByteArray(150) { 'x'.code.toByte() }
        val server =
            startServer("/page") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { it.write(body) }
            }
        try {
            val result =
                permissiveFetcher(maxSizeBytes = 100)
                    .fetch("http://127.0.0.1:${server.address.port}/page")
            val failed = assertIs<FetchResult.Failed>(result)
            assertTrue(failed.message.contains("too large"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchDoesNotFollowRedirects() {
        val server =
            startServer("/start") { exchange ->
                exchange.responseHeaders.add("Location", "/final")
                exchange.sendResponseHeaders(301, -1)
                exchange.close()
            }
        try {
            val result = permissiveFetcher().fetch("http://127.0.0.1:${server.address.port}/start")
            val failed = assertIs<FetchResult.Failed>(result)
            assertTrue(failed.message.contains("301"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun fetchRejectsLoopbackByDefault() {
        val fetcher = HtmlSourceFetcher(httpClient = client(), timeoutMs = 2000)
        val result = fetcher.fetch("http://127.0.0.1/page")
        assertIs<FetchResult.Failed>(result)
    }

    @Test
    fun fetchRejectsNonHttpScheme() {
        val fetcher = HtmlSourceFetcher(httpClient = client(), timeoutMs = 2000)
        val result = fetcher.fetch("ftp://example.com/page")
        assertIs<FetchResult.Failed>(result)
    }
}

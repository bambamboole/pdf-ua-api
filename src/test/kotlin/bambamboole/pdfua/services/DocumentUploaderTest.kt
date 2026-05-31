package bambamboole.pdfua.services

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentUploaderTest {
    private fun client() =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    // The SSRF guard blocks loopback, so the round-trip tests use a no-op validator
    // to reach the embedded test server; the guard itself is covered separately below.
    private fun permissiveUploader() = DocumentUploader(httpClient = client(), timeoutMs = 2000, validateUrl = { _, _ -> })

    @Test
    fun uploadPutsBytesAndReturnsSuccess() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val received = ArrayList<Byte>()
        var method: String? = null
        var contentType: String? = null
        server.createContext("/upload") { exchange ->
            method = exchange.requestMethod
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            received.addAll(exchange.requestBody.readBytes().toList())
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val port = server.address.port
            val body = byteArrayOf(1, 2, 3, 4)

            val result =
                permissiveUploader()
                    .upload("http://127.0.0.1:$port/upload", body, "application/pdf")

            assertEquals(UploadResult.Success, result)
            assertEquals("PUT", method)
            assertEquals("application/pdf", contentType)
            assertEquals(body.toList(), received)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun uploadReturnsFailedOnNon2xx() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/upload") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val port = server.address.port
            val result =
                permissiveUploader()
                    .upload("http://127.0.0.1:$port/upload", byteArrayOf(1), "application/pdf")

            val failed = assertIs<UploadResult.Failed>(result)
            assertTrue(failed.message.contains("500"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun uploadRejectsBlockedUrlWithoutConnecting() {
        // Default (real) validator: loopback must be rejected as InvalidUrl, not attempted.
        val uploader = DocumentUploader(httpClient = client(), timeoutMs = 2000)
        val result = uploader.upload("http://127.0.0.1/upload", byteArrayOf(1), "application/pdf")
        assertIs<UploadResult.InvalidUrl>(result)
    }

    @Test
    fun guardBlocksLoopbackPrivateLinkLocalAndNonHttp() {
        assertFailsWith<IllegalArgumentException> { validatePublicHttpUrl(URI.create("https://127.0.0.1/x"), emptySet()) }
        assertFailsWith<IllegalArgumentException> { validatePublicHttpUrl(URI.create("https://10.0.0.1/x"), emptySet()) }
        assertFailsWith<IllegalArgumentException> { validatePublicHttpUrl(URI.create("https://169.254.1.1/x"), emptySet()) }
        assertFailsWith<IllegalArgumentException> { validatePublicHttpUrl(URI.create("ftp://example.com/x"), emptySet()) }
    }

    @Test
    fun guardEnforcesAllowlist() {
        assertFailsWith<IllegalArgumentException> {
            validatePublicHttpUrl(URI.create("https://evil.com/x"), setOf("bucket.example.com"))
        }
    }

    @Test
    fun guardWithTrustPrivateHostsAllowsLoopback() {
        validatePublicHttpUrl(URI.create("http://127.0.0.1/x"), emptySet(), trustPrivateHosts = true)
    }

    @Test
    fun guardWithTrustPrivateHostsAllowsSiteLocal() {
        validatePublicHttpUrl(URI.create("http://10.0.0.1/x"), emptySet(), trustPrivateHosts = true)
        validatePublicHttpUrl(URI.create("http://192.168.1.1/x"), emptySet(), trustPrivateHosts = true)
    }

    @Test
    fun guardWithTrustPrivateHostsAllowsLinkLocal() {
        validatePublicHttpUrl(URI.create("http://169.254.169.254/x"), emptySet(), trustPrivateHosts = true)
    }

    @Test
    fun guardWithTrustPrivateHostsStillRejectsNonHttpScheme() {
        assertFailsWith<IllegalArgumentException> {
            validatePublicHttpUrl(URI.create("ftp://127.0.0.1/x"), emptySet(), trustPrivateHosts = true)
        }
    }

    @Test
    fun guardWithTrustPrivateHostsStillEnforcesAllowlist() {
        assertFailsWith<IllegalArgumentException> {
            validatePublicHttpUrl(
                URI.create("http://127.0.0.1/x"),
                setOf("bucket.example.com"),
                trustPrivateHosts = true,
            )
        }
    }
}

package bambamboole.pdfua.services

import bambamboole.pdfua.image.ImageOptimizer
import com.openhtmltopdf.extend.FSStream
import com.openhtmltopdf.extend.FSStreamFactory
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class AssetResolver(
    private val httpClient: HttpClient,
    private val timeoutMs: Long = 5000,
    private val maxSizeBytes: Long = 5 * 1024 * 1024,
    private val allowedDomains: Set<String> = emptySet(),
) : FSStreamFactory {
    private val logger = LoggerFactory.getLogger(AssetResolver::class.java)

    override fun getUrl(url: String): FSStream =
        try {
            val uri = URI.create(url)
            validateUrl(uri)
            fetchUrl(uri)
        } catch (e: Exception) {
            logger.warn("Failed to fetch asset: {} - {}", url, e.message)
            EmptyStream
        }

    internal fun validateUrl(uri: URI) = validatePublicHttpUrl(uri, allowedDomains)

    private fun fetchUrl(uri: URI): FSStream {
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() !in 200..299) {
            throw java.io.IOException("HTTP ${response.statusCode()} for $uri")
        }

        val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1)
        if (contentLength > maxSizeBytes) {
            throw java.io.IOException("Content-Length $contentLength exceeds max size $maxSizeBytes")
        }

        val bytes = response.body().use { it.readNBytes(maxSizeBytes.toInt()) }
        val optimized = ImageOptimizer.optimizeImage(bytes)
        if (optimized.size < bytes.size) {
            logger.debug("Fetched and optimized asset: {} ({} -> {} bytes)", uri, bytes.size, optimized.size)
        } else {
            logger.debug("Fetched asset: {} ({} bytes)", uri, bytes.size)
        }
        return AssetStream(optimized)
    }

    private class AssetStream(
        private val bytes: ByteArray,
    ) : FSStream {
        override fun getStream(): InputStream = ByteArrayInputStream(bytes)

        override fun getReader(): Reader = InputStreamReader(getStream())
    }

    private object EmptyStream : FSStream {
        private val empty = ByteArray(0)

        override fun getStream(): InputStream = ByteArrayInputStream(empty)

        override fun getReader(): Reader = InputStreamReader(getStream())
    }

    companion object {
        fun createHttpClient(connectTimeoutMs: Long): HttpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
    }
}

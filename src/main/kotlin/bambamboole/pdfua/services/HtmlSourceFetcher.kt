package bambamboole.pdfua.services

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.UnsupportedCharsetException
import java.time.Duration

sealed interface FetchResult {
    data class Success(
        val html: String,
        val finalUrl: String,
    ) : FetchResult

    data class InvalidUrl(
        val message: String,
    ) : FetchResult

    data class Failed(
        val message: String,
    ) : FetchResult
}

private val HTML_MIME_TYPES = setOf("text/html", "application/xhtml+xml")

class HtmlSourceFetcher(
    private val httpClient: HttpClient,
    private val timeoutMs: Long = 5_000,
    private val maxSizeBytes: Long = 5 * 1024 * 1024,
    private val allowedDomains: Set<String> = emptySet(),
    private val validateUrl: (URI, Set<String>) -> Unit = ::validatePublicHttpUrl,
) {
    private val logger = LoggerFactory.getLogger(HtmlSourceFetcher::class.java)

    fun fetch(url: String): FetchResult {
        val uri =
            try {
                URI.create(url).also { validateUrl(it, allowedDomains) }
            } catch (e: IllegalArgumentException) {
                return FetchResult.InvalidUrl(e.message ?: "Invalid URL")
            }

        return try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())

            if (response.statusCode() !in 200..299) {
                return FetchResult.Failed("Failed to fetch URL: HTTP ${response.statusCode()}")
            }

            val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1)
            if (contentLength > maxSizeBytes) {
                return FetchResult.Failed("Failed to fetch URL: response too large ($contentLength bytes)")
            }

            val rawContentType = response.headers().firstValue("Content-Type").orElse(null)
            val (mimeType, charset) = parseContentType(rawContentType)
            if (mimeType == null || mimeType !in HTML_MIME_TYPES) {
                return FetchResult.Failed(
                    "URL did not return HTML (Content-Type: ${rawContentType ?: "<none>"})",
                )
            }

            val body = response.body()
            if (body.size > maxSizeBytes) {
                return FetchResult.Failed("Failed to fetch URL: response too large (${body.size} bytes)")
            }

            val html = String(body, charset)
            val finalUrl = response.uri().toString()
            logger.debug("Fetched HTML source: {} ({} bytes)", finalUrl, body.size)
            FetchResult.Success(html, finalUrl)
        } catch (e: Exception) {
            logger.warn("Failed to fetch URL {}: {}", uri, e.message)
            FetchResult.Failed("Failed to fetch URL: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun parseContentType(raw: String?): Pair<String?, Charset> {
        if (raw.isNullOrBlank()) return Pair(null, StandardCharsets.UTF_8)
        val parts = raw.split(";").map { it.trim() }
        val mime = parts.firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() }
        val charset =
            parts
                .drop(1)
                .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                ?.substringAfter("=")
                ?.trim('"', ' ')
                ?.let {
                    try {
                        Charset.forName(it)
                    } catch (_: UnsupportedCharsetException) {
                        StandardCharsets.UTF_8
                    } catch (_: IllegalArgumentException) {
                        StandardCharsets.UTF_8
                    }
                }
                ?: StandardCharsets.UTF_8
        return Pair(mime, charset)
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

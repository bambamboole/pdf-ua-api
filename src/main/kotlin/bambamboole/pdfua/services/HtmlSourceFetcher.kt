package bambamboole.pdfua.services

import org.slf4j.LoggerFactory
import java.io.InputStream
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

    data class Failed(
        val message: String,
    ) : FetchResult
}

private val HTML_MIME_TYPES = setOf("text/html", "application/xhtml+xml")

/**
 * Fetches an HTML document from a user-supplied URL. Like [DocumentUploader] it shares the
 * SSRF guard with [AssetResolver] and follows the same defensive posture: no redirect
 * following (the post-validation hole on a redirected Location header is closed by
 * never sending the follow-up request), and the response body is read through a bounded
 * stream so an unbounded server cannot exhaust memory.
 */
class HtmlSourceFetcher(
    private val httpClient: HttpClient,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    private val allowedDomains: Set<String> = emptySet(),
    private val validateUrl: (URI, Set<String>) -> Unit = ::validatePublicHttpUrl,
) {
    private val logger = LoggerFactory.getLogger(HtmlSourceFetcher::class.java)

    @Suppress("TooGenericExceptionCaught") // defensive boundary: any fetch failure becomes FetchResult.Failed
    fun fetch(url: String): FetchResult {
        val uri =
            try {
                URI.create(url).also { validateUrl(it, allowedDomains) }
            } catch (e: IllegalArgumentException) {
                return FetchResult.Failed(e.message ?: "Invalid URL")
            }

        return try {
            val response = httpClient.send(buildRequest(uri), HttpResponse.BodyHandlers.ofInputStream())
            readResponse(response)
        } catch (e: Exception) {
            logger.warn("Failed to fetch URL {}: {}", uri, e.message)
            FetchResult.Failed("Failed to fetch URL: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun buildRequest(uri: URI): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(uri)
            .timeout(Duration.ofMillis(timeoutMs))
            .GET()
            .build()

    private fun readResponse(response: HttpResponse<InputStream>): FetchResult {
        if (response.statusCode() !in HTTP_SUCCESS_RANGE) {
            return FetchResult.Failed("Failed to fetch URL: HTTP ${response.statusCode()}")
        }
        val rawContentType = response.headers().firstValue("Content-Type").orElse(null)
        val (mimeType, charset) = parseContentType(rawContentType)
        if (mimeType == null || mimeType !in HTML_MIME_TYPES) {
            return FetchResult.Failed("URL did not return HTML (Content-Type: ${rawContentType ?: "<none>"})")
        }
        val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1)
        if (contentLength > maxSizeBytes) {
            return FetchResult.Failed("Failed to fetch URL: response too large ($contentLength bytes)")
        }
        val readLimit = (maxSizeBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val body = response.body().use { it.readNBytes(readLimit) }
        if (body.size.toLong() > maxSizeBytes) {
            return FetchResult.Failed("Failed to fetch URL: response too large (>$maxSizeBytes bytes)")
        }
        val html = String(body, charset)
        val finalUrl = response.uri().toString()
        logger.debug("Fetched HTML source: {} ({} bytes)", finalUrl, body.size)
        return FetchResult.Success(html, finalUrl)
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
        private const val DEFAULT_TIMEOUT_MS: Long = 5_000
        private const val DEFAULT_MAX_SIZE_BYTES: Long = 5L * 1024 * 1024

        fun createHttpClient(connectTimeoutMs: Long): HttpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
    }
}

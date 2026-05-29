package bambamboole.pdfua.services

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

sealed interface UploadResult {
    data object Success : UploadResult

    data class InvalidUrl(
        val message: String,
    ) : UploadResult

    data class Failed(
        val message: String,
    ) : UploadResult
}

/**
 * Uploads a generated document to a user-supplied (e.g. presigned S3) URL via HTTP PUT.
 * The URL is validated with the same SSRF guard as asset fetching before any connection,
 * and redirects are never followed so a redirect cannot bypass that guard.
 */
class DocumentUploader(
    private val httpClient: HttpClient,
    private val timeoutMs: Long = 30_000,
    private val allowedDomains: Set<String> = emptySet(),
    private val validateUrl: (URI, Set<String>) -> Unit = ::validatePublicHttpUrl,
) {
    private val logger = LoggerFactory.getLogger(DocumentUploader::class.java)

    fun upload(
        url: String,
        bytes: ByteArray,
        contentType: String,
    ): UploadResult {
        val uri =
            try {
                URI.create(url).also { validateUrl(it, allowedDomains) }
            } catch (e: IllegalArgumentException) {
                return UploadResult.InvalidUrl(e.message ?: "Invalid upload URL")
            }

        return try {
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", contentType)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..299) {
                logger.debug("Uploaded document to {} ({} bytes)", uri, bytes.size)
                UploadResult.Success
            } else {
                UploadResult.Failed("Upload target returned HTTP ${response.statusCode()}")
            }
        } catch (e: Exception) {
            logger.warn("Failed to upload document to {}: {}", uri, e.message)
            UploadResult.Failed("Upload failed: ${e.message}")
        }
    }

    companion object {
        fun createHttpClient(connectTimeoutMs: Long): HttpClient =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
    }
}

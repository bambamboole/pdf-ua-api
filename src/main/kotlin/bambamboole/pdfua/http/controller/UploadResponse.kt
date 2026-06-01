package bambamboole.pdfua.http.controller

import bambamboole.pdfua.http.ValidationResponse
import bambamboole.pdfua.pdf.PdfResult
import bambamboole.pdfua.pdf.PdfValidator
import bambamboole.pdfua.services.DocumentUploader
import bambamboole.pdfua.services.UploadResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.Base64

const val UPLOAD_URL_HEADER = "X-Upload-Url"

@Serializable
data class RenderPdfResponse(
    val validation: ValidationResponse,
    val pdf: String,
)

/**
 * Responds with a generated document. When the request carries an [UPLOAD_URL_HEADER], the
 * bytes are PUT to that URL and the endpoint replies 204; otherwise the bytes are returned
 * inline as an attachment.
 */
suspend fun RoutingContext.respondDocumentOrUpload(
    bytes: ByteArray,
    contentType: ContentType,
    fileName: String,
    documentId: String?,
    uploader: DocumentUploader?,
) {
    val uploadUrl = call.request.header(UPLOAD_URL_HEADER)?.takeIf { it.isNotBlank() }

    if (uploadUrl == null) {
        documentId?.let { call.response.header("X-Document-UUID", it) }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(
                    ContentDisposition.Parameters.FileName,
                    fileName,
                ).toString(),
        )
        call.respondBytes(bytes, contentType, HttpStatusCode.OK)
        return
    }

    if (uploader == null) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Document upload is not enabled"))
        return
    }

    when (val result = uploader.upload(uploadUrl, bytes, contentType.toString())) {
        is UploadResult.Success -> {
            documentId?.let { call.response.header("X-Document-UUID", it) }
            call.respond(HttpStatusCode.NoContent)
        }

        is UploadResult.InvalidUrl -> {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
        }

        is UploadResult.Failed -> {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to result.message))
        }
    }
}

suspend fun RoutingContext.respondPdfOrUpload(
    result: PdfResult,
    uploader: DocumentUploader?,
) {
    val uploadUrl = call.request.header(UPLOAD_URL_HEADER)?.takeIf { it.isNotBlank() }
    val wantsJson = call.request.acceptItems().prefersExplicitJson()

    if (wantsJson && uploadUrl != null) {
        call.respond(
            HttpStatusCode.BadRequest,
            mapOf("error" to "Accept: application/json cannot be combined with X-Upload-Url"),
        )
        return
    }

    if (wantsJson) {
        call.response.header("X-Document-UUID", result.documentId)
        call.respond(
            HttpStatusCode.OK,
            RenderPdfResponse(
                validation = PdfValidator.validatePdf(result.bytes),
                pdf = Base64.getEncoder().encodeToString(result.bytes),
            ),
        )
        return
    }

    respondDocumentOrUpload(
        bytes = result.bytes,
        contentType = ContentType.Application.Pdf,
        fileName = "output.pdf",
        documentId = result.documentId,
        uploader = uploader,
    )
}

private fun List<HeaderValue>.prefersExplicitJson(): Boolean {
    val explicitJsonQuality =
        filter { it.value.equals(ContentType.Application.Json.toString(), ignoreCase = true) }
            .maxOfOrNull { it.quality }
            ?: return false

    if (explicitJsonQuality <= 0.0) return false

    val pdfQuality =
        filter {
            it.value.equals(ContentType.Application.Pdf.toString(), ignoreCase = true) ||
                it.value == "*/*" ||
                it.value.equals("application/*", ignoreCase = true)
        }.maxOfOrNull { it.quality }
            ?: 0.0

    return explicitJsonQuality >= pdfQuality
}

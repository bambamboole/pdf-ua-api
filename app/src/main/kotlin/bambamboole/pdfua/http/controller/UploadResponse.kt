package bambamboole.pdfua.http.controller

import bambamboole.pdfua.services.DocumentUploader
import bambamboole.pdfua.services.UploadResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val UPLOAD_URL_HEADER = "X-Upload-Url"

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

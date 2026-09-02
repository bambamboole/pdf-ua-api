package bambamboole.pdfua.http

import bambamboole.pdfua.template.FileAttachment
import bambamboole.pdfua.template.XmpSchema
import kotlinx.serialization.Serializable

@Serializable
data class RenderHtmlRequest(
    val html: String,
    val baseUrl: String? = null,
    val attachments: List<FileAttachment>? = null,
    val xmpSchemas: List<XmpSchema>? = null,
    val embedColorProfile: Boolean = true,
)

package bambamboole.pdfua.http

import bambamboole.pdfua.template.FileAttachment
import kotlinx.serialization.Serializable

@Serializable
data class ConvertRequest(
    val html: String,
    val baseUrl: String? = null,
    val attachments: List<FileAttachment>? = null,
)

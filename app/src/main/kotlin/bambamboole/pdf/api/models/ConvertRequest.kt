package bambamboole.pdf.api.models

import kotlinx.serialization.Serializable

@Serializable
data class FileAttachment(
    val name: String,
    val content: String,
    val mimeType: String = "application/octet-stream",
    val description: String? = null,
    val relationship: String = "Alternative"
)

@Serializable
data class ConvertRequest(
    val html: String,
    val baseUrl: String? = null,
    val attachments: List<FileAttachment>? = null
)

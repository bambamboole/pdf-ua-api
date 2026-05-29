package bambamboole.pdfua.template

import kotlinx.serialization.Serializable

@Serializable
data class FileAttachment(
    val name: String,
    val content: String,
    val mimeType: String = "application/octet-stream",
    val description: String? = null,
    val relationship: String = "Alternative",
)

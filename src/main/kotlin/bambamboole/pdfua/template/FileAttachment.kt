package bambamboole.pdfua.template

import kotlinx.serialization.Serializable

@Serializable
data class FileAttachment(
    val name: String,
    @SchemaDescription("Base64-encoded file content.")
    val content: String,
    @SchemaStringDefault("application/octet-stream")
    val mimeType: String = "application/octet-stream",
    val description: String? = null,
    @SchemaStringDefault("Alternative")
    val relationship: String = "Alternative",
)

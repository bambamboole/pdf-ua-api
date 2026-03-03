package bambamboole.pdf.api.models

import kotlinx.serialization.Serializable

@Serializable
data class RenderImageRequest(
    val html: String,
    val baseUrl: String? = null,
    val format: String = "png",
    val width: Int = 800
)

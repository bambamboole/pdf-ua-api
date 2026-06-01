package bambamboole.pdfua.services

import kotlinx.serialization.Serializable

@Serializable
data class RenderOptions(
    val title: String = "Document",
    val baseUrl: String = "",
    val embedColorProfile: Boolean = true,
)
